# Contract registry

Every message CRM Lite defines, who produces it, and who reads it. Kept here rather than
inferred from code, because "who consumes this event?" is the question you need answered
before changing it — and the code can only tell you who consumes it *today* in the module
you happen to be looking at.

**Status as of 2026-08-10 (ADR-018): the SALE saga is LIVE under the `async-sale` Spring
profile.** Without that profile every messaging switch is still `false` and nothing is on
the wire, exactly as the ADR-017 foundation branch left it — the synchronous
`POST /api/orders` remains the only route and needs no broker. With it, order-service
orchestrates the sale over the commands and events below.

## The SALE saga at a glance

```
  Submit  ──► sale_saga: AWAITING_ACCOUNT_CHECK        ──► cmd check-sale-account
              AWAITING_PRODUCT_PREPARATION  ◄── evt sale-account-checked
                                            ──► cmd prepare-sale-products
              AWAITING_INVOLVEMENT          ◄── evt sale-products-prepared
                                            ──► cmd link-sale-products
              AWAITING_ACTIVATION           ◄── evt sale-products-linked
                                            ──► cmd activate-sale-products
              COMPLETED                     ◄── evt sale-products-activated
                                            ──► evt sale-completed        (choreography)

  compensation
              COMPENSATING_INVOLVEMENT      ──► cmd compensate-sale-involvements
              COMPENSATING_PRODUCTS         ──► cmd compensate-sale-products
              FAILED / MANUAL_INTERVENTION
```

`sagaId = orderNumber` for every one of them (ADR-018 §3), which is also the envelope's
`aggregateId` and its partition key — so one sale's messages are ordered with respect to
each other and no sale is serialized behind another.

## Commands

A command is imperative and has exactly one intended handler. A command nobody handles is
a **stuck sale** and shows up as a rising `crm_saga_stuck` gauge — which is why they are
listed separately from events rather than folded in.

| Message type | v | Destination | Producer | Consumer | Idempotent at the receiver because |
|---|---|---|---|---|---|
| `crm.account.check-sale-account` | 1 | `crm.account.cmd.check-sale-account.v1` | order-service | account-service | it is a read |
| `crm.product.prepare-sale-products` | 1 | `crm.product.cmd.prepare-sale-products.v1` | order-service | product-service | `saleOperationId` = order number replays the first response (ADR-015 idempotency addendum) |
| `crm.account.link-sale-products` | 1 | `crm.account.cmd.link-sale-products.v1` | order-service | account-service | involvement is unique per (account, product) (ADR-013 §8) |
| `crm.product.activate-sale-products` | 1 | `crm.product.cmd.activate-sale-products.v1` | order-service | product-service | products that are no longer PNDG are skipped |
| `crm.product.compensate-sale-products` | 1 | `crm.product.cmd.compensate-sale-products.v1` | order-service | product-service | already-passivated products are a no-op success |
| `crm.account.compensate-sale-involvements` | 1 | `crm.account.cmd.compensate-sale-involvements.v1` | order-service | account-service | it matches non-deleted rows only, so a repeat finds none |

That last column is not decoration. It is why the recovery job may reissue any of these
without checking whether the original was already applied (ADR-018 §8).

## Events

| Message type | v | Destination | Producer | Recorded in the same transaction as | Consumers |
|---|---|---|---|---|---|
| `crm.account.sale-account-checked` | 1 | `crm.account.evt.sale-account-checked.v1` | account-service | the Inbox claim of the command that caused it | order-service (saga) |
| `crm.product.sale-products-prepared` | 1 | `crm.product.evt.sale-products-prepared.v1` | product-service | the Inbox claim of the command that caused it | order-service (saga) |
| `crm.account.sale-products-linked` | 1 | `crm.account.evt.sale-products-linked.v1` | account-service | the Inbox claim of the command that caused it | order-service (saga) |
| `crm.product.sale-products-activated` | 1 | `crm.product.evt.sale-products-activated.v1` | product-service | the Inbox claim of the command that caused it | order-service (saga) |
| `crm.product.sale-products-compensated` | 1 | `crm.product.evt.sale-products-compensated.v1` | product-service | the Inbox claim of the command that caused it | order-service (saga) |
| `crm.account.sale-involvements-compensated` | 1 | `crm.account.evt.sale-involvements-compensated.v1` | account-service | the Inbox claim of the command that caused it | order-service (saga) |
| `crm.sale.sale-completed` | 1 | `crm.sale.evt.sale-completed.v1` | order-service | the saga's COMPLETED transition | none yet — **the choreography seam** (ADR-017 §2) |

Each reply carries a `result` of `SUCCEEDED` or `FAILED` rather than being split into two
message types. Both are facts about the same step, the orchestrator decides with the same
state guard either way, and one destination per step keeps "what is this saga waiting
for?" a single question.

### Pre-ADR-018 events, still defined

| Message type | v | Destination | Producer | Status |
|---|---|---|---|---|
| `crm.order.order-submitted` | 1 | `crm.order.evt.order-submitted.v1` | order-service | written by the LEGACY synchronous `POST /api/orders`; product-service's consumer observes only |
| `crm.product.products-created` | 1 | `crm.product.evt.products-created.v1` | product-service | written by `ProductServiceImpl#doCreate`; no consumer |
| `crm.account.products-linked` | 1 | `crm.account.evt.products-linked.v1` | account-service | written by `addProductInvolvements`; no consumer |

They are not deleted, because the synchronous route still exists as the documented
rollback path (ADR-018 §10). The saga does **not** use them: it uses the `sale-*` replies
above, which carry an outcome the terminal facts cannot express.

## Consumer groups

`<consumer-service>.<destination>` — the consuming service first, because the group
identifies *who is reading*, and it is also the value written to the Inbox
`consumer_group` column (ADR-017 §8.2).

| Consumer | Destination | Group |
|---|---|---|
| product-service | `crm.order.evt.order-submitted.v1` | `product-service.crm.order.evt.order-submitted.v1` |
| product-service | `crm.product.cmd.prepare-sale-products.v1` | `product-service.crm.product.cmd.prepare-sale-products.v1` |
| product-service | `crm.product.cmd.activate-sale-products.v1` | `product-service.crm.product.cmd.activate-sale-products.v1` |
| product-service | `crm.product.cmd.compensate-sale-products.v1` | `product-service.crm.product.cmd.compensate-sale-products.v1` |
| account-service | `crm.account.cmd.check-sale-account.v1` | `account-service.crm.account.cmd.check-sale-account.v1` |
| account-service | `crm.account.cmd.link-sale-products.v1` | `account-service.crm.account.cmd.link-sale-products.v1` |
| account-service | `crm.account.cmd.compensate-sale-involvements.v1` | `account-service.crm.account.cmd.compensate-sale-involvements.v1` |
| order-service | `crm.account.evt.sale-account-checked.v1` | `order-service.crm.account.evt.sale-account-checked.v1` |
| order-service | `crm.product.evt.sale-products-prepared.v1` | `order-service.crm.product.evt.sale-products-prepared.v1` |
| order-service | `crm.account.evt.sale-products-linked.v1` | `order-service.crm.account.evt.sale-products-linked.v1` |
| order-service | `crm.product.evt.sale-products-activated.v1` | `order-service.crm.product.evt.sale-products-activated.v1` |
| order-service | `crm.product.evt.sale-products-compensated.v1` | `order-service.crm.product.evt.sale-products-compensated.v1` |
| order-service | `crm.account.evt.sale-involvements-compensated.v1` | `order-service.crm.account.evt.sale-involvements-compensated.v1` |

## Dead-letter destinations

One per consumed destination, `<destination>.dlq`. A message lands there only for terminal
reasons — undecodable bytes, or a `schemaVersion` this build cannot read. A handler that
merely *failed* does not go to the DLQ; it is left unacknowledged and redelivered
(ADR-017 §8.3). Every destination in the consumer-group table above has one, named by the
same rule.

## Envelope fields every message carries

See `envelope.v1.schema.json`. Three of them are conventions, not just fields:

- **`sagaId` is the KR-12 order number** for everything in the SALE flow (ADR-018 §3).
  This **supersedes ADR-017 §5.1**, where the sagaId was the client's `Idempotency-Key` —
  the order now exists before Submit, so the sale has an identity of its own and no longer
  has to borrow one. Command idempotency and saga identity are separate concerns.
- **`causationId` is the message that caused this one.** Every saga reply carries the id
  of the command it answers, and every command carries the id of the reply that triggered
  it, so one sale is a single chain rather than seven unrelated messages.
- **`correlationId` is the `X-Correlation-Id`** already in every service's MDC — so the log
  line about the Submit request and the log lines about the messages it produced are found
  by the same search.
