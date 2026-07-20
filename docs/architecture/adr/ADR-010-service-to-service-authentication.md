# ADR-010: Service-to-Service Authentication Strategy (Per Dependency)

## Status
Accepted (2026-07-17) — implemented. Deliberately NOT one blanket rule.

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
