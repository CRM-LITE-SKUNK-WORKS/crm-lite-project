# ADR-010: Service-to-Service Authentication Strategy (Per Dependency)

## Status
Accepted (2026-07-17) — implemented. Deliberately NOT one blanket rule.
Updated 2026-07-23: added the account-service → customer-service dependency
(same propagation pattern as customer-service → lookup-service; see below).
Updated 2026-08-13: added the client-credentials service account this ADR's
lookup-service entry named as future work — the async SALE saga (ADR-018) is
the "genuine background/batch job" that entry anticipated.

## Decision

### customer-service → lookup-service: propagate the end-user token
The catalog call happens inside a user request, the user's `crm-user` role
legitimately covers catalog reads, and the shared `crm-api` audience already
includes lookup-service. The `BearerTokenPropagationInterceptor` (from
crm-security-starter, opt-in per RestClient) copies the inbound bearer token
onto the outbound call, so the **subject (`sub`) and audience are preserved
end-to-end** and lookup-service applies the exact same zero-trust validation
(ADR-009).

**Client credentials were rejected for this hop** because the call is strictly
request-bound: a service account would erase the user identity from the hop for
zero operational gain and add a second client + secret to manage. Consequence
(accepted and documented): the lookup client only works in a user-request
context. If a genuine background/batch job ever needs the catalog, THAT flow
gets a client-credentials service account with its own ADR note — as future
work, not now.

### account-service → customer-service: propagate the end-user token (2026-07-23)
account-service validates customers and their addresses through
customer-service's existing public API (`GET /api/customers/{customerNumber}`,
`GET /api/customers/{customerNumber}/addresses` — ADR-013) via Eureka
(`lb://customer-service`), NOT through the gateway: the gateway is the browser
edge (ADR-007), not an internal hop. The call is strictly request-bound (it
happens inside a user's create/update request), the user's `crm-user` role
legitimately covers those reads, and the shared `crm-api` audience already
includes customer-service — exactly the reasoning that selected token
propagation for the lookup hop above. The same
`BearerTokenPropagationInterceptor` is applied to account-service's
customer-service RestClient, so the subject (`sub`) and audience are preserved
end-to-end and customer-service applies its normal zero-trust validation
(ADR-009). Client credentials are rejected for this hop for the same reasons
recorded above; if a genuine background/batch account flow ever appears, that
flow gets a client-credentials service account with its own ADR note.

### account-service/product-service SALE saga → customer-service/lookup-service: client-credentials service account (2026-08-13)
The async SALE saga's command handlers (ADR-018 §5) — account-service's
`check`/`link`/`compensate` and product-service's `prepare`/`activate`/
`compensate` — run on a **Kafka listener thread**, not inside an HTTP request.
`doCreate()`/`addProductInvolvements()` make the exact same customer-service
(address ownership) and lookup-service (status id) calls the synchronous route
makes, but there is no end-user token there for `BearerTokenPropagationInterceptor`
to find: `SecurityContextHolder` holds no `JwtAuthenticationToken` on that
thread. Before this addendum that meant an empty `Authorization` header and a
401 from the resource server, silently stalling every sale one step past the
account check.

This is precisely the case the lookup-service entry above named as future
work: a **client-credentials service account**, `crm-saga-worker` (Keycloak,
`infra/keycloak/realm/crm-lite-realm.json`), granted the same `crm-user` realm
role and `crm-api` audience a real user token carries — so customer-service
and lookup-service apply the exact same zero-trust check either way, no
resource-server change needed. `ServiceAccountTokenProvider`
(crm-security-starter) fetches and caches its token; `BearerTokenPropagationInterceptor`
reaches for it **only when no user `Authentication` is present** — every
request-bound call documented above is unaffected. Registered only when
`crm.security.service-account.client-id` is set, which is exactly the three
services that run saga code under the `async-sale` profile
(`config-repo/{account,product,order}-service-async-sale.yml`); no service
needs it outside that profile (the deprecated synchronous route never runs
saga code).

> **Correction (2026-08-14) — order-service needs it too.** As first written,
> this addendum granted the service account to account-service and
> product-service only, on the reasoning that "order-service never needs it
> (its saga transitions are local, ADR-018 §5)". That reasoning was wrong, and
> the omission was a live defect: order-service's saga transitions do write only
> to `order_db`, but resolving the GNL_ST status id for each transition is a
> lookup-service call (`HttpLookupCatalogClient`), and it happens on exactly the
> threads this addendum exists for — the six saga-reply Kafka consumers and
> `SaleSagaScheduler` (draft cleanup, saga recovery). With no service account
> configured there, every one of those calls left an empty `Authorization`
> header and was rejected, so `SaleSagaOrchestrator` could never record a reply.
> The observable symptom was a submitted sale stuck at `PROCESSING` forever:
> the recovery job reissued the outstanding command until its budget was spent
> and then escalated to `MANUAL_INTERVENTION`, which surfaced to the client as
> `FAILED` + the generic `MSG-SALE-FAILED` — never the real
> `MSG-SALE-*`/`MSG-VAL-CHAR-*` key product-service had actually returned.
> Fixed by adding the same `crm.security.service-account` block the other two
> profiles already carried to `order-service-async-sale.yml`; no code changed.
>
> **Why the 2026-08-13 live verification did not catch it.** Two things hid it,
> and both are worth knowing before trusting a green run here. First,
> `LookupCatalogService` serves resolved entries from a 15-minute TTL cache, so a
> saga step running shortly after an HTTP request that already resolved the same
> short code never makes the call at all. Second — and decisively — the statuses
> the **happy path** needs (`NEWSALE`, `WAIT` at draft creation; `MIDLWARE`,
> `ACTV` at submit) are all resolved by the HTTP request itself, on a thread that
> *does* carry a user token. `CANCELLED` is resolved in exactly one place,
> `OrderPersistence#cancel`, which only the **failure** path reaches. That is why
> three consecutive successful sales reached `COMPLETED` while every rejected
> basket stalled: the three sales never asked for a status that was not already
> cached by their own submit. A verification that exercises only successful sales
> cannot detect this class of defect.
>
> **The same day exposed a second half of the same defect: the client existed in
> the realm JSON but not in anyone's running Keycloak.** `--import-realm` imports
> **only when the realm is absent** from `keycloak_db`, so `crm-saga-worker` —
> added to `crm-lite-realm.json` on 2026-08-13 — never reached any developer
> whose database predated it. Configuring the service account correctly would
> still have produced `invalid_client`, and (once the client was created by hand
> without its role) a `403` instead of the `401`. Fixed the way this repository
> already fixes exactly this class of drift: a fourth reconciliation step in the
> `keycloak-init` one-shot (`infra/docker-compose.yml`), which creates the client
> and its `crm-api` audience mapper when absent and grants the service-account
> user the `crm-user` realm role on every `up`. Verified both ways on the running
> stack (2026-08-14): with the client already present it changes nothing, and
> with the client deleted it recreates it complete — role and audience confirmed
> by decoding the resulting token. Same mechanism, same reasoning as the login
> theme and the `crm-bff` redirect URIs (FE-ADR-004 §3); no second mechanism
> invented.

The secret shipped in `crm-lite-realm.json` is local-dev seed data — same
status as this realm's other committed dev credentials (test user passwords)
— never a real one.

### customer-service → mernis-stub: NO CRM token
mernis-stub simulates an **external** KPS system (KR-10). A real KPS would never
accept a CRM-realm JWT, so forcing realm awareness onto the stub would make the
simulation less realistic. The mernis RestClient deliberately has no propagation
interceptor — requests carry **no Authorization header** (asserted by
`OutboundBearerPropagationTest`). The stub is reachable only inside the compose
network (no published host port, no gateway route). User attribution for
verification attempts lives in CRM-side audit/log context, never in a forwarded
token. If a service-auth simulation is ever wanted, an API-key header is the
realistic shape — future work.

## Consequences
- One interceptor, applied to exactly one client; adding a future internal
  dependency means opting its RestClient into the same interceptor.
- lookup-service sees the real user (`sub`) in its security context, keeping
  audit/log attribution intact across the hop, for every request-bound call.
- A second OAuth2 client (`crm-saga-worker`) and a service-account secret now
  exist in the repository (2026-08-13) — local-dev seed data only, scoped to
  the async SALE saga's three services under one profile (order-service added
  2026-08-14, see the correction above). customer-service and
  lookup-service audit/log attribution shows the saga worker, not a user
  `sub`, for exactly those calls — the saga already has its own identity
  (`sagaId` = order number) in every message-handling log line, so this does
  not lose traceability, it moves it to where ADR-018 already put it.
