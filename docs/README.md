# CRM Lite Documentation

| Area | Location | Contents |
|---|---|---|
| Source documents (authoritative) | [source/](source/) | FR/AC v8 Final, use cases, entity/seed workbook, draw.io diagrams. **Files named `Final` override older material.** |
| Requirements | [requirements/](requirements/) | Functional requirements summary, traceability matrix |
| Data model | [data-model/](data-model/) | Entity catalog, customer_db schema reference |
| Architecture | [architecture/](architecture/) | Service boundaries + ADRs (ADR-001..004) |
| APIs | [api/](api/) | customer-service, shared lookup-service |
| Runbooks | [runbooks/](runbooks/) | Local development, database operations |

Start with [PROJECTBRAIN.md](../PROJECTBRAIN.md) (repo root) for the current state of the
whole system, then the ADRs for the binding architecture decisions:

- **ADR-001** — customer/address/contact form ONE customer-service aggregate (atomic create).
- **ADR-002** — GNL_ST/GNL_TP are centrally owned by lookup-service; no local copies, no cross-database FKs.
- **ADR-003** — Nationality ID is globally and permanently unique (soft delete does not release it).
- **ADR-004** — Keycloak direction for authentication (proposed; not implemented).
