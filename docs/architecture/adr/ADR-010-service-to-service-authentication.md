# ADR-010: Service-to-Service Authentication Strategy (Per Dependency)

## Status
Accepted (2026-07-17) — implemented. Deliberately NOT one blanket rule.
Updated 2026-07-23: added the account-service → customer-service dependency
(same propagation pattern as customer-service → lookup-service; see below).

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
  audit/log attribution intact across the hop.
- No second OAuth2 client, no service-account secret exists in the repository.
