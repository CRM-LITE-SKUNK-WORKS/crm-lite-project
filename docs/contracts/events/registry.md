# Contract registry

Every message CRM Lite defines, who produces it, and who reads it. Kept here rather than
inferred from code, because "who consumes this event?" is the question you need answered
before changing it — and the code can only tell you who consumes it *today* in the module
you happen to be looking at.

**Status as of 2026-08-07 (ADR-017 foundation branch): nothing is on the wire yet.**
Every producer records to its Outbox only when `crm.messaging.outbox.enabled` is true, and
it ships `false`. The synchronous SALE flow (ADR-016 §5) is still the only live route.

## Events

| Message type | v | Destination | Producer | Recorded in the same transaction as | Consumers |
|---|---|---|---|---|---|
| `crm.order.order-submitted` | 1 | `crm.order.evt.order-submitted.v1` | order-service | `bsn_inter` + `cust_ord` + `cust_ord_item` (`OrderPersistence#persistOrder`) | product-service (adapter wired, handler observes only until cutover) |
| `crm.product.products-created` | 1 | `crm.product.evt.products-created.v1` | product-service | `prod` + `prod_char_val` (`ProductServiceImpl#doCreate`) | none yet |
| `crm.account.products-linked` | 1 | `crm.account.evt.products-linked.v1` | account-service | `cust_acct_prod_invl` (`AccountServiceImpl#addProductInvolvements`) | none yet — **the choreography seam** (ADR-017 §2) |

## Commands

None yet. Commands appear at the SALE cutover, when order-service's saga starts *telling*
product-service and account-service to act rather than calling them synchronously. The
naming is already fixed (`crm.<domain>.cmd.<action>.v<n>`) and
`Destinations.command(...)` already produces it.

## Consumer groups

| Consumer | Destination | Group |
|---|---|---|
| product-service | `crm.order.evt.order-submitted.v1` | `product-service.crm.order.evt.order-submitted.v1` |

## Dead-letter destinations

One per consumed destination, `<destination>.dlq`. A message lands there only for terminal
reasons — undecodable bytes, or a `schemaVersion` this build cannot read. A handler that
merely *failed* does not go to the DLQ; it is left unacknowledged and redelivered
(ADR-017 §8.3).

| Destination | DLQ |
|---|---|
| `crm.order.evt.order-submitted.v1` | `crm.order.evt.order-submitted.v1.dlq` |

## Envelope fields every message carries

See `envelope.v1.schema.json`. Worth repeating here because two of them are conventions,
not just fields:

- **`sagaId` is the client's `Idempotency-Key`** for anything in the SALE flow. One sale,
  one id, whichever route it takes (ADR-017 §5.1).
- **`correlationId` is the `X-Correlation-Id`** already in every service's MDC — so the log
  line about the HTTP request and the log line about the message it produced are found by
  the same search.
