# ADR-017: Eventing, Reliable Messaging and the Broker Boundary

## Status

**Accepted (2026-08-07).** The decisions in §1–§4 (orchestration style, messaging
framework, per-service Outbox/Inbox, Debezium as the relay) were **supplied as approved**
by the team before implementation began; this ADR records them and works out their
consequences. The remaining sections are the design that follows from them.

Companions: **ADR-016** (the synchronous SALE orchestration this prepares to replace, and
its §10 `Idempotency-Key`), **ADR-015** (product write slice), **ADR-013** (account
involvement — the SALE's commit point), **ADR-002** (no cross-database access),
**ADR-009/010** (zero trust, service-to-service auth).

**This branch introduces the foundation and does not cut the SALE flow over.**
`POST /api/orders` is unchanged. Every switch (`crm.messaging.outbox.enabled`,
`.relay.enabled`, `crm.messaging.broker.enabled`) ships `false`, and the Kafka/Connect/
Debezium containers are behind an opt-in Compose profile. Nothing in this document should
be read as a claim that an end-to-end asynchronous sale has run.

> ### Addendum 2026-08-10 — the cutover happened (ADR-018)
>
> **Everything above and below is preserved as written on 2026-08-07 and is still the
> record of what was decided then.** Two things about it are no longer current, and both
> are changed by **ADR-018**, not by editing this document:
>
> 1. **The "not done here" paragraph and the paragraph above it.** The asynchronous SALE
>    cutover *has* been performed. `POST /api/orders` is now the deprecated route; the live
>    flow is draft → 202 submit → poll, orchestrated by a persisted saga in order-service.
>    The three switches still ship `false` **by default** — they are turned on by the
>    `async-sale` Spring profile — so the sentence "every switch ships false" remains
>    literally true of the base configuration.
> 2. **§5.1 (`sagaId` is the client's `Idempotency-Key`) is SUPERSEDED by ADR-018 §3.**
>    Its reasoning was sound for its time and is worth keeping: before ADR-018 a sale had
>    no identity of its own until it was submitted. The analyst's decision that the order
>    exists *before* Submit removed that constraint, so `sagaId = orderNumber` and the
>    `Idempotency-Key` now identifies one HTTP command rather than one sale.
>
> Everything else here — the envelope, the naming conventions, the service-local
> Outbox/Inbox, the two relays and their mutual exclusion, the dead-letter policy, the
> package boundary and its bytecode guard, the metrics — is unchanged and is what ADR-018
> is built on.

## Context

ADR-016 §5 states the problem plainly: the sale writes to three databases with no
distributed transaction and no broker, so it is held together by ordering and
compensation. Two later addenda narrowed the failure surface without removing it —
§5.3b disabled transport-level retries after an incident where httpclient5 silently
re-executed a 503'd `POST /api/products` and produced two orders from one request, and §10
added an `Idempotency-Key` so a client that never sees a response can ask again safely.

What remains is the part neither addendum can fix: when step 5 (the account involvement
write, ADR-013 §8) fails, the sale is unwound by best-effort compensation, and when
compensation itself fails, ADR-015 §8.4 records the residue as an operational follow-up.
The design is honest about it — a comment in `OrderServiceImpl#compensate` says so — but
"a failing compensation is logged and swallowed" is a durability gap, not a design
feature.

The observability work (2026-08-06) reserved `sagaId` and `eventId` in `MdcKeys` for
exactly this, and the resilience work deliberately added **no** circuit breaker or retry
to the order→product/account write boundary, with a test
(`NoResilienceOnSaleWriteClientsTest`) asserting their absence — because that boundary was
already scheduled to move here rather than be patched in place.

## Decision

### 1. Core SALE uses saga orchestration, owned by order-service

The sale has a business-meaningful order of steps, needs compensation, and needs a single
place that knows how far it got. That is an orchestrator, and the orchestrator is
order-service — the service that already owns the order aggregate and the KR-12 number.

Choreography for the core flow was rejected for a specific reason, not a stylistic one:
with each service reacting to the previous one's event, no single component knows the
state of a sale, so "which sales are stuck at step 3?" becomes a query nobody can answer
without reassembling it from three logs.

### 2. Non-critical reactions to terminal SALE events use choreography

The counterpart. Once `crm.account.products-linked` exists, the sale is real and visible
(ADR-016 §5, step 5). A reaction after that point — a notification, a projection, a
report — must not be able to fail the sale, and making it a saga step would give it
exactly that power. Such reactions subscribe to the terminal event and own their own
failure.

The test for which one a new requirement is: *if this step fails, must the sale be undone?*
Yes → saga step. No → choreography.

### 3. Spring Cloud Stream, functional model, regular Kafka binder

- **Spring Cloud Stream functional bindings** (`Consumer<Message<byte[]>>`,
  `StreamBridge`), never `@KafkaListener` or `KafkaTemplate`.
- **Kafka Streams, KStream, KTable, topologies and the Kafka Streams binder are not used**
  for command/event transport. The transport need here is point-to-point delivery with a
  partition key; a stream-processing runtime with its own state stores, changelog topics
  and rebalancing semantics would be a second distributed system to operate, for a
  capability nothing in the SALE flow asks for.
- **Debezium and Kafka Connect stay in infrastructure configuration** — `infra/eventing/`
  and the `eventing` Compose profile. No module depends on them.

### 4. The broker boundary is a package boundary, and it is enforced by tests

| Layer | May reference | Package |
|---|---|---|
| Domain / application | nothing broker-shaped | `com.crm.<svc>.**` |
| Ports | plain Java only | `OutboxPublisher`, `MessageHandler` |
| Adapter | Spring Cloud Stream, Spring messaging | `com.crm.**.messaging.adapter`, `com.crm.messaging.adapter.stream` |

Rules:

1. Domain and application code must not import `org.apache.kafka.*`.
2. `KafkaTemplate`, `@KafkaListener`, `KStream`, `KTable`, Kafka Streams topologies and
   the Kafka Streams binder are not used anywhere.
3. Spring Cloud Stream appears only in adapter packages.
4. **Business handlers are plain Java classes, callable with no broker.** `MessageHandler`
   takes an `EventEnvelope` and returns `void`; `new OrderSubmittedHandler(codec).handle(e)`
   is a valid call from a unit test.
5. Broker-specific topic names, serializers and headers stay out of domain/application
   packages.

`NoBrokerTypesInDomainOrApplicationTest` (one per service) scans **compiled class files**
for the forbidden package prefixes. Bytecode rather than source, because an import grep
misses an inline fully-qualified reference and a type reached only through a generic
signature — both compile fine and both would break class loading without the broker
present. In product-service the guard also asserts the exemption is *load-bearing* (the
adapter really does reference the transport), so deleting the adapter cannot make the rule
pass vacuously.

### 5. The versioned envelope

`com.crm.messaging.contract.EventEnvelope` — one wire format for every command and event:

| Field | Meaning |
|---|---|
| `messageId` | globally unique; doubles as the eventId and is what the Inbox deduplicates on |
| `messageType` | contract name (`crm.order.order-submitted`), never a Java class name |
| `schemaVersion` | version of `payload` |
| `aggregateType` / `aggregateId` | owning aggregate; `aggregateType` is also Debezium's routing field |
| `sagaId` | sale-scoped orchestration id |
| `correlationId` | the end-to-end `X-Correlation-Id` already in every service's MDC |
| `causationId` | the message that caused this one; null when a request did |
| `occurredAt` | when the fact happened — not when it was published |
| `payload` | the versioned contract body, as JSON text |

**5.1 `sagaId` is the client's `Idempotency-Key`.** The sale already has an identity the
client chose and can retry with (ADR-016 §10). Minting a second id would give one sale two
identifiers that nothing correlates afterwards.

**5.2 The payload is text, not a typed generic.** A shared payload class would recreate
the shared domain model this project does not have: every producer field addition would
become a lock-step release across three services. Consumers decode into their own DTOs
(§6).

**5.3 Version policy.** Adding an optional field keeps the version. Removing, renaming or
narrowing one is incompatible: bump `MessageTypes.currentVersion` **and** publish to a new
`.v<n>` destination, because consumers that have not been redeployed are still reading the
old one. A consumer rejects a version above what its build produces
(`SchemaVersionNotSupportedException`) rather than parsing optimistically — silently
dropping the field a producer bumped the version *for* is a wrong answer, and a wrong
answer is worse than a loud failure. Rejection is terminal, not retryable: the same bytes
will fail identically forever, so it goes to the DLQ.

**5.4 The codec owns its own `ObjectMapper`.** The web mapper is tuned for the HTTP API
(customer-service, for one, installs a string-trimming deserializer for form input); the
wire format must stay byte-stable across services and releases.

### 6. Contracts are neutral; DTOs are consumer-owned

- The authoritative definitions live in **`docs/contracts/events/`** as JSON Schema —
  language-neutral, versioned, reviewable, and not a build dependency.
- `crm-messaging-starter` contains the envelope and the mechanics. **It contains no
  business payload type from any service**, so depending on it is not depending on
  another service's model.
- Each producer declares its own `*EventContracts` class; each consumer declares its own
  DTO with only the fields it reads. `OrderSubmittedContract.OrderSubmitted` in
  product-service is deliberately a partial duplicate of order-service's payload — the
  duplication is what keeps the two independently deployable.

### 7. Naming conventions

```
command   crm.<domain>.cmd.<action>.v<n>     crm.product.cmd.create-products.v1
event     crm.<domain>.evt.<fact>.v<n>       crm.order.evt.order-submitted.v1
retry     <destination>.retry.<attempt>      crm.order.evt.order-submitted.v1.retry.1
DLQ       <destination>.dlq                  crm.order.evt.order-submitted.v1.dlq
group     <consumer-service>.<destination>   product-service.crm.order.evt.order-submitted.v1
```

Encoded in `com.crm.messaging.contract.Destinations`, which is in `contract` and not in an
adapter package: the naming *policy* is a project decision, the transport is not. A
"destination" is a topic under the Kafka binder and would be an exchange under another.

**7.6 The partition key is `sagaId`, else `aggregateId`.** Ordering is only ever needed
*within* one sale. A broader key — the customer number, say — would serialize unrelated
sales behind each other for no ordering benefit. `orderNumber` is the aggregateId of the
order aggregate, so the approved "orderNumber or sagaId" is exactly what this expresses.

**7.7 Headers duplicate envelope fields and are advisory only.** They exist so an operator
at a broker console can filter without deserializing. **Nothing may make a business
decision from a header** — the envelope is the contract.

**7.8 The version is in both the destination name and the envelope.** They answer
different questions: `schemaVersion` lets one consumer accept a compatible range; a new
destination version is how an *incompatible* change ships without breaking consumers still
reading the old one.

### 8. Transactional Outbox and Inbox, service-local

`order_db`, `product_db` and `account_db` each get their own `outbox_message` and
`inbox_message` (new Flyway migrations: order V4, product V6, account V5). No service
reads another's, and there is no cross-database foreign key — ADR-002, applied to
messaging.

**8.1 Outbox — the write is in the business transaction, by construction.**
`OutboxRecorder` is `@Transactional(propagation = MANDATORY)`. Calling it outside a
transaction is an `IllegalTransactionStateException`, not a code-review finding. "State
changed but message lost" and "message published but state rolled back" are therefore
unreachable, with no distributed transaction and with the broker allowed to be down.

Rows are **not** deleted on publish; they are marked `published_at`. Delete-on-publish
would give up two things: a short audit trail of what was actually sent, and a meaningful
backlog metric — a table that empties itself cannot tell you it is behind.

**8.2 Inbox — claim and business change in one transaction.** `InboxGuard.claimAndHandle`
inserts the claim and runs the handler in the *same* transaction:

- first delivery → claim + work commit together;
- redelivery → `uq_inbox_message_id_group` rejects the insert; nothing was applied;
- handler throws → the claim rolls back with it, so the next delivery is a genuine first
  delivery, not a duplicate.

The third case is why the claim is never committed separately "to be safe": a separately
committed claim would swallow every message whose processing failed.

**The uniqueness constraint is the guard, not a lookup.** The `exists` check in
`InboxGuard` is an optimization that two concurrent deliveries can both pass; the
constraint is what settles it. The key is `(message_id, consumer_group)` and not
`message_id` alone, because two different services must each process a message once while
one service must never process it twice.

**8.3 `InboxDispatcher` separates transient from terminal.** A handler that threw is
transient — the exception is **rethrown** so the transport does not acknowledge a message
nobody applied. An undecodable or unsupported-version message is terminal and goes to the
DLQ, because retrying it only blocks the messages behind it.

### 9. The relay

**9.1 Primary: the Debezium Outbox Event Router** (`infra/eventing/connectors/`), one
connector per service database, reading the WAL. No application thread, no polling.
`transforms.outbox.route.by.field=destination` means the row names its own destination, so
a new contract needs no connector change. `expand.json.payload=false` with
`StringConverter` on both sides means Connect republishes exactly the bytes the
application wrote — expanding would have Connect infer a schema and re-serialize, and the
contract in `docs/contracts/events/` would stop describing what is actually on the wire.

**9.2 Alternative: the in-process repository relay** (`OutboxRelay`), for environments
without Kafka Connect and — more importantly — for tests. A relay that requires Connect
running cannot be exercised, and "the broker was down for thirty seconds and came back" is
a behaviour that has to be proven, not asserted.

**9.3 Exactly one of them runs at a time.** They read the same rows; both running
publishes everything twice. With the `eventing` profile up,
`crm.messaging.outbox.relay.enabled` stays `false`.

**9.4 Three connectors is not cross-database access.** Each is bound to the
`outbox_message` table of the database whose service wrote it. It is the same one-writer
rule ADR-002 states, applied to the relay — and it is why the relay lives in infrastructure
rather than inside a service that would then be reading someone else's database.

**9.5 At-least-once, deliberately.** The row is marked published only *after* the publisher
returns, so a crash between send and mark redelivers. The Inbox makes a duplicate free; a
silently dropped message is unrecoverable. Marking first would trade a solved problem for
an unsolved one. `required-acks: all` for the same reason: the relay treats "the send
returned" as "durable", so anything weaker would mark a losable message as published.

### 10. Retention

`OutboxRetentionJob` deletes rows where `published_at IS NOT NULL AND published_at <
cutoff` (default: 7 days). **Unpublished rows are never eligible, at any age** — "old" is
precisely the symptom of a message that still needs to go out, so a time-only cleanup
would delete the backlog it was supposed to alert on. It runs in the application, not as a
database job, so it needs no privilege the service lacks and appears in the same metrics
and structured logs as everything else.

### 11. Rollout — three switches, off

| Property | Default | What turning it on does |
|---|---|---|
| `crm.messaging.outbox.enabled` | `false` | business transactions start writing outbox rows |
| `crm.messaging.outbox.relay.enabled` | `false` | the in-process relay starts publishing |
| `crm.messaging.broker.enabled` | `false` | Spring Cloud Stream bindings are created at all |

Three and not one, because the intermediate state — *record but do not publish* — is a real
and useful step: it proves atomicity in a live environment while still touching no broker.
While `broker.enabled` is false no binder is instantiated and every service starts with no
broker present, exactly as today. The binder health contributor is disabled for the same
reason: reporting DOWN for a component nothing uses is how a health check stops meaning
anything.

Cutover order (`docs/runbooks/eventing.md`): `outbox.enabled` → `broker.enabled` + exactly
one relay → consumers. Each step is observable on its own before the next.

### 12. Metrics

On the existing Micrometer/Prometheus pipeline (`/actuator/prometheus`):

| Metric | Type | Notes |
|---|---|---|
| `crm_outbox_backlog_messages` | gauge | rows written but not accepted by the broker |
| `crm_outbox_oldest_unpublished_age_seconds` | gauge | **the one to alert on** |
| `crm_outbox_published_total` / `crm_outbox_publish_failures_total` | counter | |
| `crm_outbox_retention_deleted_total` | counter | |
| `crm_inbox_duplicates_total` | counter | tagged by `messageType`; **nonzero is normal** |
| `crm_inbox_processed_total` | counter | |
| `crm_inbox_consumer_failures_total` | counter | handler threw; message will be retried |
| `crm_inbox_dead_letter_total` | counter | tagged `messageType` + `reason` |

Backlog and age answer different questions. A relay stuck on one poison message keeps a
small, flat backlog while the age climbs without limit — so the alert has to be on the age.
A nonzero duplicate rate is the transport working as designed, absorbed for free.

`sagaId` and `eventId` are populated in the MDC by `InboxGuard` for the duration of a
handler, filling the two keys `MdcKeys` reserved in the observability work.

## Consequences

**Good**

- The durability gap in ADR-016 §5 has a mechanism to close it; the branch after this one
  changes flags and moves a method body, not an architecture.
- A broker swap is a dependency and configuration change; the guard tests make that
  claim checkable rather than aspirational.
- Handlers are unit-testable with no infrastructure at all.
- Every failure mode of the design — outage, duplicate, restart, version skew — has a test
  that produces it on demand.

**Costs, stated plainly**

- Three new switches, and an operator who turns on the wrong two gets either a growing
  backlog or double publishing. §11 and the runbook exist because of this.
- Two relay implementations, permanently, with a documented mutual exclusion. The
  alternative was an untestable relay.
- The consumer-owned-DTO rule means a payload shape is written twice. Deliberate (§6).
- `wal_level=logical` on the shared Postgres, set unconditionally: it cannot be changed at
  runtime, and a developer must not have to recreate their volume to try the profile. The
  cost when Debezium is not running is a slightly larger WAL; no replication slot exists
  until a connector asks for one.
- `spring-cloud-stream-binder-kafka` is on the runtime classpath of three services even
  while unused. Rule 1 constrains *imports in domain/application code*, not the classpath;
  no binder is created without a binding.

**Not done here**

- The asynchronous SALE cutover. `POST /api/orders` is untouched, no consumer creates a
  product, and no end-to-end asynchronous sale has been run or is claimed.
- Retry destinations are named and specified (§7) but no automatic retry-topic escalation
  ladder is implemented; binder-level retry is off (`max-attempts: 1`) so that redelivery
  plus the Inbox is the only retry path until the cutover defines what needs more.
