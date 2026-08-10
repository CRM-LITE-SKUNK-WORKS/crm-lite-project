# infra/eventing — broker and relay configuration

Everything Kafka-, Connect- and Debezium-specific in this repository lives here and in
the `eventing` profile of `infra/docker-compose.yml`. **No application module references
any of it** — that isolation is the point of ADR-017 §3, and it is what makes the choice
of broker a deployment decision rather than a code decision.

Start it (never started by a plain `docker compose up`):

```
docker compose --profile eventing up -d
```

Starting the profile does **not** move the SALE flow onto messaging. Every service ships
with `crm.messaging.broker.enabled=false`, so they neither publish nor consume until that
is changed deliberately. See `docs/runbooks/eventing.md` for the cutover order.

## connectors/

One Debezium PostgreSQL connector per service database, each configured with the **Outbox
Event Router** SMT.

Three connectors and not one, because there are three databases and each service owns
exactly one. A connector reads only the `outbox_message` table of the database whose
service wrote it (`table.include.list`), so this is not a back door into another
service's data: it is the same one-writer rule ADR-002 states, applied to the relay.

The column mapping is the part worth understanding:

| Router setting | Column | Why |
|---|---|---|
| `table.field.event.id` | `message_id` | the envelope's messageId — what the receiving Inbox deduplicates on |
| `table.field.event.key` | `partition_key` | `sagaId` else `aggregateId`; per-sale ordering (ADR-017 §7.6) |
| `table.field.event.payload` | `envelope` | the **whole** envelope, passed through byte-for-byte |
| `route.by.field` | `destination` | the row names its own destination, so a new contract needs no connector change |

`transforms.outbox.table.expand.json.payload` is **false** and both converters are
`StringConverter`. Together they mean Connect republishes exactly the bytes the
application wrote. Expanding the JSON would have Connect infer a schema and re-serialize —
the consumer would then be decoding something Connect composed rather than something the
producer wrote, and the contract in `docs/contracts/events/` would stop describing what is
actually on the wire.

`snapshot.mode` is `no_data`: a fresh connector must not republish the entire outbox
history as if it had just happened. It starts from the current WAL position and streams
forward.

## Running Debezium and the in-process relay at the same time

Don't. They read the same rows and would publish every message twice (ADR-017 §9.3).
With this profile up, `crm.messaging.outbox.relay.enabled` must stay `false`.
