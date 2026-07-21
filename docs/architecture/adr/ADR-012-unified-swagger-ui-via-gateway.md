# ADR-012: Unified Swagger UI Hosted by the Gateway

## Status
Accepted (2026-07-20) — implemented via `springdoc-openapi-starter-webmvc-ui` in
`api-gateway`, `customer-service`, `lookup-service`.

## Context
The project needed browsable/interactive API documentation (Swagger/OpenAPI) for
customer-service and lookup-service. Two shapes were considered:

1. **Per-service Swagger UI**, each domain service hosting its own `/swagger-ui.html`
   and using springdoc's built-in OAuth2 Authorization Code + PKCE helper to fetch a
   Keycloak access token directly and call itself as a Bearer resource server. This
   exercises `crm-security-starter`'s actual zero-trust JWT model most faithfully, but
   requires publishing a host port for customer-service (8082) and lookup-service
   (8083) — both currently have **no published host port** (ADR-009: "all client
   traffic enters through the gateway BFF"). It also means two separate,
   simultaneously-valid auth mechanisms in the platform: BFF session cookies for
   normal traffic, and direct Bearer tokens for Swagger, with no shared login step.
2. **Gateway-hosted unified Swagger UI**, proxying each service's `/v3/api-docs` JSON
   through the existing WebMVC gateway and rendering one Swagger UI page at the
   gateway's own origin. "Try it out" then reuses whatever the gateway already knows
   how to do for `/api/**` — nothing new to authenticate.

## Decision
1. **Only `api-gateway` renders a Swagger UI.** `customer-service` and
   `lookup-service` add `springdoc-openapi-starter-webmvc-ui` purely to generate their
   `/v3/api-docs` OpenAPI JSON from existing `@RestController`s; both set
   `springdoc.swagger-ui.enabled: false` in their config-repo YAML — they never serve
   an HTML docs page themselves.
2. **The gateway proxies, not redirects.** New WebMVC gateway routes
   (`customer-service-docs`, `lookup-service-docs`) rewrite
   `/v3/api-docs/{service}` to the downstream service's `/v3/api-docs`, following the
   same `id`/`uri: lb://…`/`predicates`/`filters` shape as the existing `/api/**`
   routes. No `TokenRelay` filter is attached — the docs JSON is schema metadata, not
   business data. `springdoc.swagger-ui.urls` on the gateway lists both proxied paths,
   giving one page (`/swagger-ui.html`) with a service-switcher dropdown.
3. **No new host port.** Neither customer-service nor lookup-service publishes 8082 or
   8083 to the Docker/Podman host, in any profile — ADR-009 is not relaxed, not even
   for local development.
4. **Auth for the docs themselves vs. auth for "Try it out" are different layers.**
   `/swagger-ui/**`, `/swagger-ui.html` and `/v3/api-docs/**` are `permitAll()` in the
   gateway's `SecurityConfig` and in each service's `crm.security.permit-paths`
   (the starter's existing externally-settable extension point, no code change) — the
   docs page itself needs no login. Executing a request from "Try it out" still goes
   through the real `/api/**` routes, which still require an authenticated
   `ROLE_crm-user` session exactly as before. There is no separate OAuth2 flow
   configured in springdoc; the browser's existing BFF session cookie (from a normal
   `http://localhost:8080/` login) is what authenticates the call, because Swagger UI
   and the API share the same origin.
5. **Known gap, tracked not blocking:** Swagger UI's default `requestInterceptor` does
   not attach the `X-XSRF-TOKEN` header, so mutating "Try it out" calls
   (POST/PUT/DELETE/PATCH) currently require the user to copy the `XSRF-TOKEN` cookie
   value into the header manually. A small custom JS interceptor to automate this is
   listed as a follow-up (PROJECTBRAIN §9.2), not a blocker for read documentation.

## Consequences
- One URL to remember (`http://localhost:8080/swagger-ui.html`), one auth model
  (the BFF session everyone already uses to test the app manually).
- Adding OpenAPI docs to a future domain service is: add the starter dependency,
  set `springdoc.swagger-ui.enabled: false`, add `/v3/api-docs/**` to its
  `permit-paths`, add one proxy route + one `springdoc.swagger-ui.urls` entry on the
  gateway. No security code changes anywhere.
- Trade-off accepted: this does **not** exercise `crm-security-starter`'s direct
  Bearer-token resource-server path (option 2 above would have). Real zero-trust
  bearer-token verification is already covered by existing service-level integration
  tests (ADR-009) and the manual Postman/curl flows in `docs/postman/`; Swagger's job
  here is discoverability and casual "try it out" ergonomics, not security testing.
- If a future requirement needs Swagger UI to work with no BFF session at all (e.g. a
  fully external API consumer), that reopens option 1 and ADR-009's port policy
  deliberately, not silently.
