# Runbook — eventing, Outbox/Inbox and the broker

Operational companion to **ADR-017**. Contracts are in `docs/contracts/events/`;
broker configuration is in `infra/eventing/` and the `eventing` Compose profile.

> **Read this first (updated 2026-08-10, ADR-018).** The SALE flow **is** on messaging —
> under one Spring profile.
>
> - **Default (no profile):** all three switches are `false`, no binder is instantiated,
>   the stack runs with **no Kafka at all**, and the deprecated synchronous
>   `POST /api/orders` is the only working sale route. This is what CI runs and what a
>   developer who is not working on the sale gets.
> - **`SPRING_PROFILES_ACTIVE=async-sale`:** order/product/account-service pick up
>   `<service>-async-sale.yml`, which turns on the broker, the Outbox and one relay. The
>   draft → submit → poll contract goes live and the saga orchestrates the sale
>   (ADR-018).
>
> With the profile off, `POST /api/orders/{n}/submit` answers **503
> `MSG-SERVICE-UNAVAILABLE`** rather than accepting a sale nothing can process. That is
> deliberate: without it the Outbox recorder would silently write nothing, the transaction
> would commit, and the client would poll a MIDLWARE order forever.

---

## 1. The three switches

| Property | Default | Effect when true |
|---|---|---|
| `crm.messaging.outbox.enabled` | `false` | business transactions write `outbox_message` rows |
| `crm.messaging.outbox.relay.enabled` | `false` | the in-process relay polls and publishes |
| `crm.messaging.broker.enabled` | `false` | Spring Cloud Stream bindings are created |

Set per service in `backend/config-server/src/main/resources/config-repo/<service>.yml`,
or overridden by environment variable
(`CRM_MESSAGING_OUTBOX_ENABLED=true`, and so on).

**The one mistake to avoid:** running the in-process relay *and* the Debezium connectors
at the same time. Both read the same rows, so every message is published twice. With the
`eventing` profile up, `crm.messaging.outbox.relay.enabled` stays `false`.

---

## 2. Starting the stack

```bash
cd infra

# Application stack, no broker — this is the normal case and it is what CI runs.
docker compose up -d

# Add Kafka + Kafka Connect + Debezium (opt-in).
docker compose --profile eventing up -d

# Connectors are registered automatically by the one-shot kafka-connect-init container.
curl -s http://localhost:8083/connectors | jq
curl -s http://localhost:8083/connectors/order-outbox-connector/status | jq '.connector.state, .tasks[].state'
```

Ports: Kafka `localhost:29092` from the host, `kafka:9092` inside `crm-net`; Kafka Connect
`localhost:8083`.

`postgres` runs with `wal_level=logical` unconditionally (it cannot be changed at runtime,
and requiring a volume recreation to try the profile would be hostile). Without a
connector running, no replication slot exists and the only cost is a slightly larger WAL.

---

## 3. Watching it work

### Metrics (`/actuator/prometheus` on each service)

| Metric | Read it as |
|---|---|
| `crm_outbox_backlog_messages` | how much is owed to the broker |
| `crm_outbox_oldest_unpublished_age_seconds` | **whether anything is moving — alert on this** |
| `crm_outbox_published_total` | throughput |
| `crm_outbox_publish_failures_total` | the broker is rejecting or unreachable |
| `crm_outbox_retention_deleted_total` | cleanup is running |
| `crm_inbox_duplicates_total` | redeliveries absorbed — **nonzero is normal and healthy** |
| `crm_inbox_processed_total` | consumer throughput |
| `crm_inbox_consumer_failures_total` | handlers throwing; messages will be retried |
| `crm_inbox_dead_letter_total` | **messages given up on — this is the one that pages** |

Backlog and age are not interchangeable. A relay stuck on a single poison message shows a
small, flat backlog while the age climbs without limit; a busy healthy system shows the
opposite. **Alert on the age**, use the backlog for capacity.

A rising `crm_inbox_duplicates_total` is the design working. A rising
`crm_inbox_dead_letter_total` means a producer shipped a contract version this consumer
cannot read, or something is emitting malformed bytes.

### Logs

Every message-handling log line carries `sagaId` and `eventId` in the MDC (populated by
`InboxGuard`), alongside the `correlationId` the whole platform already has.

To follow one sale end to end in Grafana/Loki, query on **`sagaId`, which since ADR-018 is
the KR-12 order number** — the same value the Submit response returned, the same value in
`cust_ord.order_number` and `sale_saga.saga_id`, and the same value on every command and
reply of that sale. (Before ADR-018 it was the client's `Idempotency-Key`; that identity
is now per-HTTP-command only, because the order exists before Submit and the sale no longer
has to borrow one — ADR-018 §3.)

`causationId` chains the messages: every reply carries the id of the command it answers and
every command the id of the reply that triggered it, so one sale is one chain rather than
seven unrelated messages.

---

## 4. Diagnosing

### The backlog is growing and the age is climbing

```sql
-- Per service database. What is stuck, and why.
SELECT message_id, message_type, destination, publish_attempts, last_error, occurred_at
FROM outbox_message
WHERE published_at IS NULL
ORDER BY occurred_at
LIMIT 20;
```

`last_error` is the publisher's own exception text. Then check, in order:

1. Is any relay running at all? (`crm.messaging.outbox.relay.enabled`, or the Debezium
   connector's state.)
2. Is the broker up? `docker compose --profile eventing ps kafka`
3. Is the connector failed?
   `curl -s localhost:8083/connectors/order-outbox-connector/status | jq`

Nothing is lost while this is happening. The rows stay, retention will not touch them
(retention deletes **published** rows only), and the next successful poll drains them.

### A message keeps being redelivered

The handler is throwing. `crm_inbox_consumer_failures_total` confirms it and the WARN log
carries the exception. This is by design — the claim rolled back with the handler, so the
message is genuinely unprocessed. Fix the cause; the redelivery then succeeds with no
manual replay.

### A message was dead-lettered

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic crm.order.evt.order-submitted.v1.dlq --from-beginning --max-messages 5
```

Two causes, both terminal for *this* build:

- **`reason=schema-version`** — a producer is ahead of this consumer. Deploy the consumer;
  the DLQ contents can then be replayed and will be a clean first delivery, because a
  dead-lettered message is never claimed in the inbox.
- **`reason=undecodable`** — malformed bytes. Something is publishing outside the codec.

### Was this message already processed?

```sql
SELECT * FROM inbox_message
WHERE message_id = '<the messageId>' AND consumer_group = '<group>';
```

A row means it was processed and committed. No row means it was not — regardless of how
many times it was delivered.

---

## 5. Retention

`OutboxRetentionJob` runs hourly and deletes rows where
`published_at IS NOT NULL AND published_at < now() - keep_published_for` (default 7 days).

**Unpublished rows are never deleted, at any age.** That is deliberate: "old" is the
symptom of a message that still needs to go out, so a time-only cleanup would delete
exactly the backlog the metrics exist to surface.

Tuning: `crm.messaging.outbox.retention.keep-published-for` /
`.run-interval` / `.enabled`.

---

## 5b. The SALE saga (ADR-018)

`sale_saga` in `order_db` is the process manager, keyed by the KR-12 order number
(`saga_id = order_number`). Everything operational about a sale is one query:

```bash
docker exec -it postgres psql -U crmlite -d order_db -c \
  "SELECT saga_id, current_state, retry_count, next_retry_at, failure_code,
          failure_message_key, updated_at
     FROM sale_saga ORDER BY updated_at DESC LIMIT 20;"
```

| Internal state | Public `processingStatus` | What it means |
|---|---|---|
| `AWAITING_ACCOUNT_CHECK` … `AWAITING_ACTIVATION` | `PROCESSING` | waiting for a reply from product/account-service |
| `COMPLETED` | `COMPLETED` | products ACTV and linked; order stays `MIDLWARE` |
| `COMPENSATING_INVOLVEMENT` / `COMPENSATING_PRODUCTS` | `PROCESSING` | undoing; still moving |
| `FAILED` | `FAILED` | fully unwound; order `CANCELLED` |
| `MANUAL_INTERVENTION` | `FAILED` | **a compensation itself failed** — order left `MIDLWARE`, because `CANCELLED` would claim an undo that did not happen |

`MANUAL_INTERVENTION` is the one that needs a person. It is never reached silently:
`crm_saga_compensation_failures_total` increments, `crm_saga_state{state="MANUAL_INTERVENTION"}`
goes up, and an ERROR log line names the saga.

**Saga metrics** (`/actuator/prometheus`, order-service):

| Metric | Alert on it? |
|---|---|
| `crm_saga_state{state}` | dashboard |
| `crm_saga_stuck` | **yes** — non-terminal sagas that have stopped moving |
| `crm_saga_compensation_failures_total` | **yes** — the one that needs a human |
| `crm_saga_unexpected_messages_total{messageType,state}` | yes if it is persistently nonzero — two messages disagree about where a sale is |
| `crm_saga_command_reissues_total` | dashboard; a rising rate means replies are not coming back |
| `crm_saga_completed_total` / `crm_saga_failed_total` | dashboard |
| `crm_sale_async_duration_seconds` | dashboard |

Duplicates and dead-lettering are **not** re-counted here — they are Inbox properties and
are already in `crm_inbox_duplicates_total` / `crm_inbox_dead_letter_total` for every
consumer in every service (§3).

**A saga is stuck.** It is waiting for a reply that never came. The recovery job reissues
the outstanding command after `crm.order.saga.reply-timeout` with exponential backoff, up
to `max-retries`, then escalates to `MANUAL_INTERVENTION`. Every command is idempotent at
its receiver, so a reissue that races a late reply produces work the receiver recognises
and skips. If reissues are not helping, look at the consumer side: is the destination's
DLQ filling (§4), is the consumer group lagging, is the service up?

**Do not "fix" a saga by editing `sale_saga` by hand.** The row is one half of an
invariant whose other half is the Outbox; changing the state without the matching command
produces a sale that nothing will ever act on. Reissue by moving `next_retry_at` into the
past and letting the job do it.

---

## 6. Cutover procedure

**Performed for the SALE flow on 2026-08-10 (ADR-018).** The three steps below remain the
procedure for any *new* flow, and for enabling this one in an environment where it is off.
Each is observable before the next. Do not compress them.

**Step 1 — record only.** `crm.messaging.outbox.enabled=true`, everything else false.
Business transactions start writing outbox rows; nothing publishes. Verify: rows appear in
`outbox_message` with `published_at IS NULL`, `crm_outbox_backlog_messages` climbs, and
**every API response is byte-identical to before**. This step proves atomicity in the real
environment while touching no broker. Roll back by setting the flag false — the rows are
inert.

**Step 2 — publish.** Start `docker compose --profile eventing up -d`, set
`crm.messaging.broker.enabled=true`, and enable **exactly one** relay (Debezium is the
default; use the in-process relay only where Connect is unavailable). Verify: the backlog
drains, `crm_outbox_published_total` climbs, and messages are visible on the destinations
with `kafka-console-consumer.sh`. Still no consumer acts on them.

**Step 3 — consume.** Enable consumers and let the handlers do the business work. For the
SALE flow this is `SPRING_PROFILES_ACTIVE=async-sale`, which does all three steps at once
for order/product/account-service:

```bash
cd infra
docker compose --profile eventing up -d          # Kafka (+ Connect/Debezium)

# The three sale services read SPRING_PROFILES_ACTIVE from the environment
# (it defaults to empty in docker-compose.yml, so a plain `up` is unchanged):
SPRING_PROFILES_ACTIVE=async-sale docker compose up -d --force-recreate \
    account-service product-service order-service
```

The profile enables the **in-process relay**, so Kafka alone is enough and Kafka Connect
is optional. If you register the Debezium connectors instead, set
`crm.messaging.outbox.relay.enabled=false` in all three services first — running both
publishes every message twice (§1).

**Rollback.** Remove the profile and restart. The three switches go back to `false`, the
asynchronous submit answers 503, and the deprecated synchronous `POST /api/orders` — which
is still in the build for exactly this reason — is the working route again. Drafts already
in `WAIT` are inert and are cleaned up by the stale-draft job; sagas already in flight stop
where they are and are visible in `sale_saga` (§5b), so nothing is lost, but they will not
progress until the profile is back on.

---

## 7. Running the tests

```bash
# Contract-level, no Docker needed.
mvn -pl backend/crm-messaging-starter test

# Needs a running Docker daemon (Testcontainers).
mvn -pl backend/order-service   test -Dtest='Outbox*IntegrationTest'
mvn -pl backend/product-service test -Dtest='InboxDeduplicationIntegrationTest'

# The SALE saga end to end — draft, submit, every failure branch, compensation,
# escalation and recovery. Needs Docker; needs no broker.
mvn -pl backend/order-service -am test -Dtest='AsyncSaleFlowIntegrationTest'

# The saga command handlers, with no Spring context and no Docker at all.
mvn -pl backend/product-service,backend/account-service -am test \
    -Dtest='SaleCommandHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false

# The architecture guard — no Docker, but needs target/classes, so compile first.
mvn -pl backend/order-service,backend/product-service,backend/account-service test \
    -Dtest='NoBrokerTypesInDomainOrApplicationTest'
```

What each proves:

| Test | Property |
|---|---|
| `OutboxAtomicityIntegrationTest` | business state + outbox commit or roll back **together**; recording outside a transaction is impossible |
| `OutboxRelayIntegrationTest` | broker outage loses nothing; no double publish; backlog/age gauges; retention never deletes an unpublished row |
| `InboxDeduplicationIntegrationTest` | duplicate delivery, consumer restart, handler failure, unsupported version, undecodable bytes, group independence |
| `EventEnvelopeSerializationTest` | field names, round trip, forward tolerance |
| `SchemaVersionCompatibilityTest` | a future version is rejected, not guessed |
| `NoBrokerTypesInDomainOrApplicationTest` | no broker type outside an adapter package, and the exemption is load-bearing |
| `AsyncSaleFlowIntegrationTest` | draft lifecycle; WAIT → MIDLWARE + saga + first command in one commit; 202 without waiting; duplicate/out-of-order replies; every failure branch; both compensation orders; escalation on a failed compensation; unsafe failure keys replaced; stuck-saga reissue; exhausted retry budget; a terminal saga never reissued; legacy and async cannot both act on one sale |
| `SaleCommandHandlerTest` (product, account) | business failure → published `FAILED` reply; infrastructure failure → rethrown, nothing published; the order number is the sale-operation id; compensation is sale-scoped and idempotent |
