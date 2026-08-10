# Runbook — eventing, Outbox/Inbox and the broker

Operational companion to **ADR-017**. Contracts are in `docs/contracts/events/`;
broker configuration is in `infra/eventing/` and the `eventing` Compose profile.

> **Read this first.** As of 2026-08-07 the SALE flow is **not** on messaging.
> `POST /api/orders` runs the synchronous ADR-016 §5 orchestration, and all three
> messaging switches ship `false`. Everything below describes machinery that is in place
> and tested, not a running pipeline.

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
`InboxGuard`), alongside the `correlationId` the whole platform already has. To follow one
sale end to end in Grafana/Loki, query on `sagaId` — it is the client's `Idempotency-Key`,
so it is the same value the client used on the original HTTP request.

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

## 6. Cutover procedure (not performed in this branch)

Three steps, each observable before the next. Do not compress them.

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

**Step 3 — consume.** Enable consumers and move the business work into the handlers. This
is the actual SALE cutover and is where `POST /api/orders` changes; it needs its own ADR
amendment, its own tests, and its own rollback plan. It is **not** part of the eventing
foundation branch.

---

## 7. Running the tests

```bash
# Contract-level, no Docker needed.
mvn -pl backend/crm-messaging-starter test

# Needs a running Docker daemon (Testcontainers).
mvn -pl backend/order-service   test -Dtest='Outbox*IntegrationTest'
mvn -pl backend/product-service test -Dtest='InboxDeduplicationIntegrationTest'

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
| `NoBrokerTypesInDomainOrApplicationTest` | no broker type outside an adapter package |
