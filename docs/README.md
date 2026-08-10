# CRM Lite Documentation

| Area | Location | Contents |
|---|---|---|
| Source documents (authoritative) | [source/](source/) | FR/AC v8-2 (03.08.2026 revision), use cases, entity/seed workbook, draw.io diagrams. **Files named `Final` override older material.** |
| Requirements | [requirements/](requirements/) | Functional requirements summary, traceability matrix, [document-delta](requirements/document-delta.md) (03.08.2026 source-change reconciliation) |
| Data model | [data-model/](data-model/) | Entity catalog, customer_db schema reference |
| Architecture | [architecture/](architecture/) | Service boundaries + roadmap, ADRs (ADR-001..017) |
| APIs | [api/](api/) | customer-service, shared lookup-service, mernis-stub — full curl catalogs; **[authentication](api/authentication.md)** (login/session/CSRF/logout browser contract) |
| Message contracts | [contracts/events/](contracts/events/) | The authoritative, language-neutral JSON Schema for every event/command, plus the [registry](contracts/events/registry.md) of who produces and reads each one. **Not a build dependency** — deliberately (ADR-017 §6). |
| Postman | [postman/](postman/) | Importable collection + local environment + usage guide |
| Runbooks | [runbooks/](runbooks/) | Local development, **[authentication testing](runbooks/auth-testing.md)** (browser + Postman verification walkthrough), database operations, **[eventing](runbooks/eventing.md)** (Outbox/Inbox, broker, cutover), **Git workflow** |

Start with [PROJECTBRAIN.md](../PROJECTBRAIN.md) (repo root) for the current state of the
whole system, [CONTRIBUTING.md](../CONTRIBUTING.md) for contributor rules, then the
ADRs for the binding architecture decisions:

- **ADR-001** — customer/address/contact form ONE customer-service aggregate (atomic create).
- **ADR-002** — GNL_ST/GNL_TP are centrally owned by lookup-service; no local copies, no cross-database FKs.
- **ADR-003** — Nationality ID is globally and permanently unique (soft delete does not release it).
- **ADR-004** — Keycloak direction for authentication (implemented; partially superseded by ADR-006/007).
- **ADR-005** — GET /api/customers browse+filter contract: no-criteria mode lists all
  active customers; rows carry the full detail contract (`Page<CustomerDetailResponse>`).
- **ADR-006** — Keycloak is the SOLE identity/token authority; Authorization Code + PKCE; ROPC forbidden.
- **ADR-007** — the api-gateway is the BFF (server-side tokens, session cookie); auth-service removed.
- **ADR-008** — cookie/session/CSRF policy (HttpOnly session, XSRF-TOKEN/X-XSRF-TOKEN, SameSite=Lax).
- **ADR-009** — zero-trust resource servers (sig/iss/aud/`crm-user` role) via crm-security-starter.
- **ADR-010** — service-to-service tokens: user-token to lookup-service, NO token to mernis-stub.
- **ADR-011** — no application USERS/password table; Keycloak owns credentials (workbook superseded).
- **ADR-012** — one Swagger UI, hosted by the gateway; services serve `/v3/api-docs` only.
- **ADR-013 / ADR-014** — account-service boundary; KR-11 account numbers (Luhn, immutable, never reused).
- **ADR-015 / ADR-016** — product write slice; order-service boundary, KR-12 order numbers
  and the synchronous SALE orchestration (+ §10 `Idempotency-Key`).
- **ADR-017** — eventing: saga orchestration for the core SALE, choreography for
  non-critical reactions, per-service transactional Outbox/Inbox, the versioned envelope,
  and a broker boundary enforced by tests. **Foundation only — the SALE flow is not cut
  over**; see [runbooks/eventing.md](runbooks/eventing.md).
