# Event and command contracts

The **authoritative** definition of every message CRM Lite puts on the wire. Neutral by
design: JSON Schema, not Java, and **not a build dependency of anything** (ADR-017 §6).

That is the point. A shared Java contracts jar would recreate the shared domain model this
project deliberately does not have — every producer field addition would become a
lock-step release across three services. Here, a producer declares its own
`*EventContracts` class, a consumer declares its own DTO with only the fields it reads, and
this directory is what the two agree with rather than with each other.

## Layout

```
docs/contracts/events/
  README.md                                   this file — conventions and version policy
  registry.md                                 every contract, its producer, and its readers
  envelope.v1.schema.json                     the envelope every message is wrapped in
  crm.order.order-submitted.v1.schema.json    payload schemas, one file per type+version
  crm.product.products-created.v1.schema.json
  crm.account.products-linked.v1.schema.json
```

A file is named `<messageType>.v<schemaVersion>.schema.json`. Both parts are in the name
because both can change independently: a new type is a new file, and a new incompatible
version of an existing type is *also* a new file — the old one stays, because consumers
still reading it have not been redeployed.

## Version policy (ADR-017 §5.3)

| Change | Compatible? | What to do |
|---|---|---|
| add an optional field | yes | edit the schema in place, keep the version |
| add a required field | **no** | new version |
| remove or rename a field | **no** | new version |
| narrow a type or an enum | **no** | new version |
| widen a type, relax an enum | yes | edit in place |

An incompatible change means all three of: a new schema file here, a bump in
`MessageTypes.currentVersion`, **and** a new `.v<n>` destination
(`Destinations`) — because a consumer that has not been redeployed is still subscribed to
the old destination and would otherwise receive bytes it cannot read.

A consumer rejects a `schemaVersion` above what its build produces rather than parsing it
optimistically. Silently dropping the field a producer bumped the version *for* produces a
wrong answer; a loud `SchemaVersionNotSupportedException` and a dead-lettered message
produce a page. The second is the better failure.

## The envelope

Every payload here travels inside `envelope.v1.schema.json`. The envelope is versioned
separately from the payloads and evolves far more slowly; unknown envelope fields are
ignored by consumers by design, so adding one is always compatible.

## Destination naming

See ADR-017 §7. In short:

```
crm.<domain>.evt.<fact>.v<n>       events (facts; any number of readers)
crm.<domain>.cmd.<action>.v<n>     commands (one intended handler)
<destination>.retry.<attempt>      retry
<destination>.dlq                  dead letter
<consumer-service>.<destination>   consumer group
```

## Adding a contract

1. Write the schema file here and add a row to `registry.md`.
2. Add the type name and version to `com.crm.messaging.contract.MessageTypes`.
3. Add the destination constant and the producer-side payload record to the producing
   service's `*EventContracts`.
4. In each consumer, declare a DTO with **only the fields that consumer reads**.
5. Record the message with `OutboxRecorder` inside the transaction that makes the fact
   true. Never outside it — `Propagation.MANDATORY` will not let you anyway.
