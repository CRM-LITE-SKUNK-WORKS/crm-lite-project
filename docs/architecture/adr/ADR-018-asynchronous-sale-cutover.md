# ADR-018: The Asynchronous SALE Cutover — Draft Orders, Saga Orchestration and the 202 Contract

## Status

**Accepted (2026-08-10).** §1 records analyst decisions supplied on 2026-08-10 and is not
open to technical revision; everything else is the design that follows from them plus the
team's explicit decision to make the asynchronous SALE flow **live and demonstrable**
rather than another dormant foundation.

Companions: **ADR-017** (the messaging foundation this uses and, in one place,
supersedes), **ADR-016** (the synchronous orchestration this replaces as the live route),
**ADR-015** (product write slice), **ADR-013** (account involvement), **ADR-002** (no
cross-database access), **ADR-005/012** (unrelated but adjacent contracts).

**What is superseded, explicitly:**

| Superseded | By | Why |
|---|---|---|
| ADR-016 §8.3 — "the Order Number exists only after submission" | §1.1 below | the analyst clarified that the records exist before Submit |
| ADR-017 §5.1 — "`sagaId` is the client's `Idempotency-Key`" | §3 below | the order now has an identity before Submit, so the sale no longer borrows one |
| ADR-016 §4 — "KR-12 is a project-proposed rule **awaiting analyst sign-off**" | §2 below | the analyst set the requirement (uniqueness); the format is a technical choice satisfying it |
| ADR-016 §5 as the LIVE sale | §5 below | it remains in the build as the documented rollback route (§10), not as the live one |

Nothing in ADR-016 or ADR-017 is deleted. Both remain the record of what was decided when.

## Context

ADR-017 built the whole reliable-messaging foundation — envelope, per-service Outbox and
Inbox, Debezium and in-process relays, dead-lettering, metrics — and then deliberately
stopped: "**This branch introduces the foundation and does not cut the SALE flow over.**"
Every switch shipped `false`. Its own "Not done here" section names the gap precisely:
"The asynchronous SALE cutover. `POST /api/orders` is untouched, no consumer creates a
product, and no end-to-end asynchronous sale has been run or is claimed."

Meanwhile the durability gap ADR-017 §Context described is still open in the live route: a
sale that fails at the involvement write is unwound by best-effort compensation, and a
compensation that itself fails is, in `OrderServiceImpl#compensate`'s own words, "logged
and swallowed". Everything needed to close it now exists and is tested. What was missing
was the decision to use it, plus one analyst clarification about when an order comes into
existence.

Both arrived on 2026-08-10.

## Decision

### 1. Analyst decisions of 2026-08-10, recorded verbatim in effect

**1.1 An order exists BEFORE Submit; the Order Number is visible on the Submit screen.**
The analyst clarified: *"When the user starts the process, records should already be
created in the relevant tables. The records exist, but because the order has not yet been
completed/approved their status is not MIDLWARE. With order approval the status moves to
MIDLWARE. Therefore the Order ID may be displayed at this point."*

Consequences, and they are the reason this ADR exists at all:

- an order MUST exist before the final Submit confirmation;
- the Order Number MUST be available on the Submit screen (AC-SALE-01-12);
- the workbook's existing `GNL_ST WAIT (id 3, domain ORDER)` is that pre-submit status.
  **No new GNL_ST row is invented** — the catalog already had the state the analyst
  described, and adding a `DRAFT` row would have been a project invention where a
  requirement already had an answer;
- ADR-016 §8.3's "Order Number only after submission" is superseded.

Lifecycle:

```
Start New Sale  ─► order + bsn_inter created, status WAIT, KR-12 number allocated
                   (Order Number displayable from here on)
Confirm Submit  ─► WAIT ─► MIDLWARE, saga starts
terminal failure ─► CANCELLED (compensation, or an abandoned/expired draft)
```

No `COMPLETED`/`FAILED` row is added to GNL_ST. A completed sale stays **MIDLWARE**,
because that is what the analyst defined at approval and no accepted requirement defines
another terminal ORDER status. §6 explains where "completed" is expressed instead.

**1.2 The Order Number must be unique; the format is ours.** The analyst required
uniqueness and stated that a simple increasing sequence would be sufficient, leaving any
rule to the technical team. The existing KR-12 generator (`[T][YY][SSSSSS][C]`, Luhn-
checked, ADR-016 §4) already guarantees uniqueness, is implemented, unit-tested against
fixed vectors, and allocates inside the transaction that creates the order — including the
draft transaction, which is the only new demand this ADR places on it.

It is therefore **kept unchanged**, and its status is corrected: the format is a
**project technical choice that satisfies an analyst requirement**, not an
analyst-mandated format and no longer an open question. Redesigning a working, tested
identifier scheme to answer a requirement it already answers would be change for its own
sake.

**1.3 Offer prices are non-normative demo fixtures.** The analyst confirmed the exact
values are demo data and not a blocker. `PROD_OFR.product_offer_total_price` is empty in
the workbook; the repository's fixture prices stay exactly as they are, and derived
documentation records them as **non-normative demo fixtures** rather than as an unresolved
blocking question. No "real" price list is invented.

**1.4 A general failure message is approved.** For unavailable or failed SALE processing
the analyst approved a general message. This does **not** flatten the existing specific
outcomes:

- product-service's own `MSG-SALE-*` / `MSG-VAL-CHAR-*` keys are relayed unchanged, so a
  rejected basket still tells the user what is actually wrong;
- `MSG-SERVICE-UNAVAILABLE` remains the key for concrete unavailability, including a
  Submit attempted where asynchronous processing is switched off;
- `MSG-SALE-FAILED` is added as the generic terminal fallback, used **only** when no
  existing key truthfully describes the failure.

Infrastructure exception text is never exposed (§8).

**1.5 PNDG for an in-flight sale is analyst-supported.** The analyst confirmed PNDG may be
used for products belonging to an order that is currently being processed. The existing
semantic — *prepared/reserved for an in-flight sale, not yet committed* (ADR-015 §5.5) — is
now backed by an explicit decision rather than by project interpretation, and §5's
ordering (products stay PNDG until the involvement exists) depends on it.

**1.6 Transfer and Service Address Change are OUT OF SCOPE.** Not implemented. The
`GNL_TP TRANSFER`/`CANCEL` rows remain populated-but-unused columns exactly as ADR-016
§6/§8.2 left them, and are not treated as licence to build anything.

### 2. What this branch delivers, stated plainly

The real asynchronous SALE flow, live and demonstrable end to end: a draft order with a
visible number, an HTTP 202 submit, an orchestrated saga across three services over Kafka,
compensation in both directions, bounded recovery, and a status resource to poll. It is
switched on by one Spring profile (§10) so that a developer who does not want Kafka still
gets today's behaviour unchanged.

### 3. `sagaId = orderNumber`, and command idempotency is a separate concern

ADR-017 §5.1 made the sagaId the client's `Idempotency-Key` for one honest reason: before
this ADR, the sale had no identity of its own until it was submitted, and minting a second
id would have given one sale two identifiers nothing correlated.

§1.1 removes that constraint. The order — and its KR-12 number — now exists from Start New
Sale, so:

- **`sagaId = orderNumber`**, for every command and event in the flow, and it is also the
  envelope `aggregateId` and the partition key (ADR-017 §7.6 asked for exactly
  "orderNumber or sagaId"; they are now the same value);
- **`Idempotency-Key` identifies one HTTP command**, not one sale. Draft creation and
  submission are two independent commands and take two independent keys.

This separation fixes a real defect in the old conflation: a client that retried a lost
submit with a *new* key would previously have produced a *new* saga id for the same sale.
It cannot now — the identity is the order number, and the retry is either a replay or a
409, decided by the `idempotency_key` table.

The two new endpoints hash the request **path** along with the body, so reusing one key
across them is a `MSG-IDEMPOTENCY-KEY-CONFLICT` rather than a replay of the wrong
command's response. The legacy endpoint keeps its original body-only hash: rehashing it
would make every key already recorded in a live table mismatch after a deploy, turning an
in-flight retry into a spurious 409.

### 4. The saga is a persisted, recoverable process manager owned by order-service

New table `sale_saga` (order-service, Flyway `V5`), keyed by `saga_id` = the order number,
with a `CHECK (saga_id = order_number)` because the equality is the identity decision and a
constraint is the only documentation a future migration cannot silently contradict.

It carries the state, an optimistic-lock `version`, retry budget and due time, the failure
code and the **safe** failure message key, the causing message id, the timestamps, and two
JSON columns: the submitted basket snapshot and the product ids.

Two of those need justifying:

- **`request_snapshot`.** `cust_ord_item` records the offer, the product and the amount —
  but not the characteristic VALUES the user typed, which are product-service's data,
  forwarded verbatim (ADR-015 §6) and stored nowhere in `order_db`. Without the snapshot a
  crash between Submit and preparation would leave a sale nothing could resume.
- **`product_ids`.** The same ids are in `cust_ord_item`, but reading them back through the
  order aggregate to build a compensation command would make compensation depend on the
  very write that may have been what failed.

There is **no cross-service saga database**. product-service and account-service learn what
to do from commands and answer with events; neither reads this table, and there is no
cross-database foreign key (ADR-002).

Internal states are richer than the public contract on purpose:

```
STARTED → AWAITING_ACCOUNT_CHECK → AWAITING_PRODUCT_PREPARATION
        → AWAITING_INVOLVEMENT → AWAITING_ACTIVATION → COMPLETED
compensation: COMPENSATING_INVOLVEMENT → COMPENSATING_PRODUCTS → FAILED
escalation:   MANUAL_INTERVENTION
```

`COMPENSATING_*` and `MANUAL_INTERVENTION` are operational states. **None of them is a
GNL_ST value and none may become one** — GNL_ST is the analyst-owned shared catalog, and
putting a process-manager state into a catalog three services read would be exactly the
kind of local invention ADR-002 forbids.

### 5. Orchestration for the core flow, choreography for the terminal fact

ADR-017 §1/§2 already decided this; ADR-018 instantiates it.

| # | Command (order-service →) | Reply (→ order-service) | Handled by |
|---|---|---|---|
| 1 | `check-sale-account` | `sale-account-checked` | account-service |
| 2 | `prepare-sale-products` | `sale-products-prepared` | product-service |
| 3 | `link-sale-products` | `sale-products-linked` | account-service |
| 4 | `activate-sale-products` | `sale-products-activated` | product-service |
| C1 | `compensate-sale-involvements` | `sale-involvements-compensated` | account-service |
| C2 | `compensate-sale-products` | `sale-products-compensated` | product-service |

and then, from `COMPLETED` only, the terminal event `crm.sale.sale-completed` — the
choreography seam. By the time it exists the products are ACTV and linked, so a reaction
cannot fail the sale.

**Each reply carries a `result` of SUCCEEDED/FAILED rather than being two message types.**
Both are facts about the same step and the orchestrator's next move is chosen by the same
state guard either way; two destinations per step would double the binding table and make
"what is this saga waiting for?" two questions.

**5.1 The ordering is deliberately the reverse of ADR-016 §5.** The synchronous flow
activates products and *then* writes the involvement, so that its only compensation acts on
never-committed rows. The saga links **first** and activates **last**, because a product
must not be committed while no account claims it. The price is that an activation failure
has an involvement to undo — which is the situation §7 exists for, and which is affordable
now precisely because a saga can carry out a two-step compensation reliably and a
synchronous request could not.

**5.2 No new business logic is created in any handler.** Every command routes into the
same service method the HTTP endpoint calls: `ProductService#create/confirm/compensate`,
`AccountService#addProductInvolvements`, and the one internal method §7 adds. Basket
composition, characteristic validation, the PNDG lifecycle and the sale-scoped ownership
guard exist in exactly one place each.

**5.3 No broker type appears in domain or application code.** Handlers are plain classes
taking an `EventEnvelope`; the adapters are `Consumer<Message<byte[]>>` beans whose whole
body is one delegation. No `KafkaTemplate`, no `@KafkaListener`, no Kafka Streams, no
`KStream`/`KTable`. The existing per-service `NoBrokerTypesInDomainOrApplicationTest`
scans compiled bytecode and now covers the new packages too.

### 6. The public contract: draft, 202, and two status axes

```
POST   /api/orders/drafts                  201  Idempotency-Key required
DELETE /api/orders/{orderNumber}/draft     204  WAIT only, idempotent
POST   /api/orders/{orderNumber}/submit    202  Idempotency-Key required, Location: .../status
GET    /api/orders/{orderNumber}/status    200
```

**Draft creation** validates that the billing account exists and is Active, creates
`bsn_inter` + `cust_ord` with status WAIT, allocates the KR-12 number and returns it. It
creates **no product**, starts **no saga**, and writes **no message**. The customer number
is resolved from the account and is never accepted from the browser.

A WAIT draft is **inert by construction**: nothing consumes it, no command names it, and
the recovery job looks only at saga rows — of which a draft has none. That is what keeps
AC-SALE-01-16 ("an abandoned sale must never be processed later") true for a browser that
simply vanishes, without depending on the browser to say it went. The explicit
`DELETE .../draft` is a courtesy, not the guarantee. A configurable cleanup cancels expired
WAIT drafts; it **only ever cancels** — there is no code path anywhere that can submit or
fulfil a draft.

`DELETE .../draft` refuses a MIDLWARE order with 409 `MSG-ORDER-NOT-DRAFT`, which is what
stops it from quietly becoming the post-submit order cancellation KR-7 keeps out of phase.

**Submit** re-validates the Active precondition, checks that the request still belongs to
this draft's account, and then in **one `order_db` transaction**: persists the order-item
snapshot, moves WAIT → MIDLWARE, creates the saga, and records the first Outbox command.
The 202 is returned **after** that transaction commits and **never** waits for
product-service or account-service.

**Why 202 and not 201.** A 201 promises a created resource whose state is what the body
says; after Submit the products do not exist yet, so a 201 carrying the order would
describe a sale that has not happened. 202 says what is true — accepted, durable, being
worked on — and `Location` says where to find out how it went.

**Two status axes, and they are not redundant:**

| | values | owner |
|---|---|---|
| `orderStatus` | WAIT / MIDLWARE / CANCELLED | the analyst's GNL_ST ORDER domain |
| `processingStatus` | DRAFT / PROCESSING / COMPLETED / FAILED | this service's API contract |

A completed sale is **MIDLWARE + COMPLETED**. Collapsing the two fields would have forced
this project to invent the terminal ORDER status the analyst did not define — which is
precisely the invention §1.1 avoids. `processingStatus` is **not** added to GNL_ST.

The status response also carries `failureMessageKey` (only on a terminal failure) and
`updatedAt` (what lets a poll loop tell "still working" from "stuck").

**AC-SALE-01-15 and the processing screen.** How the frontend renders "Sipariş Alındı,
İşleniyor…" against a polled status resource — a dedicated processing screen, an inline
state, or something else — is a **PROJECT INTERPRETATION PENDING the analyst's final UX
clarification**. It is not recorded here as an analyst-approved requirement. The backend
contract above is deliberately UX-neutral: it exposes state and lets the frontend choose.

### 7. Compensation

| Failure point | What exists | Compensation |
|---|---|---|
| account check rejected | nothing | none — cancel the order |
| product preparation rejected | nothing (creation is one transaction, ADR-015 §5.1) | none — cancel the order |
| involvement write failed | PNDG products, no involvement | compensate products, cancel the order |
| activation failed | PNDG products **and** involvement rows | compensate involvement **first**, then products, cancel the order |
| a compensation itself failed | partial residue | `MANUAL_INTERVENTION` — see below |

**7.1 Involvement compensation is internal and saga-scoped.** ADR-013 §8.6 declined to
create an involvement-delete command, and that still stands for every user-facing purpose.
What §5.1's ordering requires is narrower: a way for **one saga** to undo **its own** rows.
A nullable `sale_operation_id` is added to `cust_acct_prod_invl` (account-service Flyway
`V6`) holding the order number of the saga that wrote the row.

- Existing rows — the V3 seed and everything the synchronous route wrote — keep NULL, and
  are therefore **uncompensatable by any saga**. That is the safe default, and a backfill
  would have had to invent an owner, which is exactly what would make the guard unsafe.
- The compensation matches on `(customer_account_id, sale_operation_id, deleted_date IS
  NULL)`. Pairing the account with the operation id makes it structurally incapable of
  reaching another account's rows.
- It is idempotent (already-passivated rows are outside the query), soft (PASV + deleted
  metadata, never a physical delete), and **exposed by no endpoint**.

**7.2 `MANUAL_INTERVENTION` is not a synonym for FAILED.** A saga whose compensation
failed does **not** cancel its order: CANCELLED asserts the sale was undone, and the whole
point of this state is that it was not. Leaving the order MIDLWARE while the saga says
MANUAL_INTERVENTION is the honest description of "something is left behind", and it is
what makes the one case an operator must find distinguishable from a clean rollback —
by a state, a query and a gauge, rather than a log line nobody counted.

**7.3 A compensation never touches another saga's work.** product-service refuses any
product whose `saleOperationId` differs (409 `MSG-SALE-OPERATION-MISMATCH`, ADR-015 §5.7),
and account-service matches only rows carrying this saga's id.

### 8. Correctness under at-least-once delivery, and recovery

**Exact duplicates** never reach business code: `InboxGuard`'s `(message_id,
consumer_group)` constraint absorbs them, in the same transaction as the work
(ADR-017 §8.2).

**Out-of-order or impossible transitions** are guarded by state: every reply names the
state it can apply to, and one that does not match is counted
(`crm_saga_unexpected_messages_total`) and ignored. Counted rather than dropped — a
persistent nonzero rate here means two messages disagree about where a sale is, which is a
contract defect, not the transport working normally.

**Two deliveries racing one saga** are settled by the `@Version` column: the loser's whole
transaction — Inbox claim, saga mutation and outgoing command — rolls back and is
redelivered, to find a world that has moved on.

**Atomicity, stated precisely, including where it is relaxed.**

- order-service: a saga transition and the command it produces are always one commit.
  Nothing here throws a business exception, so nothing relaxes it.
- product-service and account-service: the Inbox claim and the outgoing reply are always
  one commit. The **business mutation** runs in its own `REQUIRES_NEW` transaction, and
  this is deliberate: a saga step must be able to fail as a business outcome, but a
  `REQUIRED` transactional method that throws marks the caller's transaction rollback-only
  — so the failure reply could never be committed at all, and a rejected basket would be
  redelivered until the DLQ took it. On the **failure** path nothing is written, so
  atomicity is intact. On the **success** path the mutation commits an instant before the
  claim, and every command is idempotent at its receiver (registry table), so a redelivery
  converges on the same reply rather than duplicating the sale.

**Retry and recovery** are application-level and broker-independent. No Kafka retry API
appears in business code; binder-level retry stays at `max-attempts: 1`, because a second
in-memory retry loop would only re-run a handler whose transaction already rolled back
while holding the partition.

What the transport cannot see is a command that was **published successfully and never
answered** — the consumer was down, or the reply was dead-lettered. `SaleSagaRecoveryJob`
covers exactly that: a saga waiting past `reply-timeout` has its outstanding command
reissued (idempotent by the table above), with exponential backoff and a bounded budget;
when the budget is spent it escalates to `MANUAL_INTERVENTION` rather than generating load
forever. Each saga is reissued in its own transaction, so one optimistic-lock loss — the
expected outcome when the real reply lands at the same moment — costs that saga's nudge and
nothing else.

A broker restart, a consumer restart and an order-service restart all lose nothing: the
work to be done is a row in `sale_saga` and a row in `outbox_message`, both committed
before the client was told anything.

**Nothing infrastructure-shaped reaches a client.** `failureMessageKey` must match
`MSG-[A-Z0-9-]+`; anything else is replaced with `MSG-SALE-FAILED` before it can be
returned. No stack trace, exception message or connection string can travel this path.

### 9. Observability

On the already-merged Micrometer/Prometheus/Grafana/Loki pipeline. `correlationId`,
`sagaId` (= orderNumber), `eventId`, `causationId` and `orderNumber` are populated and
propagated — `InboxGuard` already puts `sagaId`/`eventId` into the MDC for the duration of
a handler, filling the two keys the observability work reserved.

| Metric | Type | Notes |
|---|---|---|
| `crm_saga_state{state}` | gauge | one series per state, so a state nobody is in reports 0 |
| `crm_saga_completed_total` | counter | |
| `crm_saga_failed_total` (+ `_by_state`) | counter | |
| `crm_saga_compensation_attempts_total` | counter | |
| `crm_saga_compensation_failures_total` | counter | **the one to alert on** |
| `crm_saga_stuck` | gauge | non-terminal sagas that have not moved recently |
| `crm_saga_unexpected_messages_total{messageType,state}` | counter | out-of-order/impossible |
| `crm_saga_command_reissues_total` | counter | recovery activity |
| `crm_sale_async_duration_seconds` | timer | submit → terminal |

Duplicates, consumer failures and dead-letter activity are **not** re-counted here: they
are Inbox properties already counted for every consumer in every service by `InboxMetrics`
(ADR-017 §12). Two numbers for one event would disagree the first time a message was
dead-lettered before a saga ever saw it.

Never logged: JWTs, passwords, cookies, full Nationality IDs, personal-data request bodies,
credentials. Unchanged from the observability work; the saga adds no new sink for any of
them.

### 10. Rollout, and the legacy route

**One profile.** `SPRING_PROFILES_ACTIVE=async-sale` layers
`{order,product,account}-service-async-sale.yml` on top of the base configuration and turns
on `crm.messaging.broker.enabled`, `crm.messaging.outbox.enabled` and one relay. Without
it every switch stays `false`, no binder is instantiated, and the stack runs exactly as it
does today with no Kafka present.

That default matters: making Kafka a hard dependency of every local run would break
everyone who is not working on the sale flow, to demonstrate a flow they are not working
on.

**Submit fails closed when the profile is off** — 503 `MSG-SERVICE-UNAVAILABLE` — rather
than issuing a 202 for work that can never start. Without that check the recorder would
silently write nothing (its documented disabled behaviour, ADR-017 §11), the transaction
would commit, and the client would poll a MIDLWARE order waiting forever for a reply to a
command that was never sent.

**The legacy synchronous `POST /api/orders` stays**, deprecated in code and docs, for the
currently merged frontend and as the rollback route. It is removed by the frontend PR that
moves the browser to the asynchronous contract.

**One sale can never enter both routes** — by construction, not convention: the legacy
endpoint always creates a NEW order, already MIDLWARE, and `POST .../submit` accepts only a
WAIT draft. There is no order both can act on and no request that reaches both. A test
asserts it.

## Consequences

**Good**

- The durability gap ADR-016 §5 documented and ADR-017 built for is closed on the live
  route: compensation is a persisted, retried, observable process rather than a
  best-effort call whose failure was logged and swallowed.
- A user is no longer made to wait for three services; the Submit response is one local
  commit away.
- "Which sales are stuck, and at which step?" is a single indexed query and a gauge.
- The Order Number is available where the analyst says it should be, using a status the
  workbook already defined.
- A broker outage, a consumer restart and a service restart are survivable states, each
  with a test that produces it on demand.

**Costs, stated plainly**

- Two live SALE routes until the frontend PR lands. Mitigated by the WAIT-only submit
  guard and by the deprecation being explicit rather than implied.
- Thirteen new message contracts and twelve new bindings. That is the price of orchestration
  with an explicit catalog; the alternative — one shared reply channel — would have made an
  incompatible change to any single reply force a new destination version on all of them.
- `sale_operation_id` on `cust_acct_prod_invl` is a column that exists only to make a
  compensation safe. Nullable, indexed partially, read by exactly one method, exposed by no
  endpoint.
- On the consumer side the business mutation is not in the same commit as the Inbox claim
  (§8). Idempotent receivers close the window; the trade-off is documented on
  `SaleCommandExecutor` in both services rather than left to be discovered.
- The pre-existing product-creation snapshot window (ADR-015's `SaleOperationCoordinator`
  commits its response snapshot in a `REQUIRES_NEW` transaction before the products'
  transaction commits) is **carried over unchanged**. It is not introduced here and not
  fixed here; the saga's reissue path makes it self-correcting in practice, because a
  replayed snapshot for products that were rolled back leads to a link/activate failure and
  a clean compensation rather than a silent success.

**Not done here**

- The frontend cutover. The browser still calls `POST /api/orders`; the next PR moves it,
  and only then may the legacy endpoint be deleted.
- The AC-SALE-01-15 processing-screen UX, which is a project interpretation pending analyst
  clarification (§6).
- Transfer and Service Address Change, which are out of scope by analyst decision (§1.6).
- Any user-facing order cancellation. KR-7 keeps it out of phase; the draft abandon is
  WAIT-only and refuses a submitted order.
