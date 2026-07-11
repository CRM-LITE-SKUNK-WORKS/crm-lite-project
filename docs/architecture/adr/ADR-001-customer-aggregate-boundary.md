# ADR-001: Customer Aggregate Boundary

## Status
Accepted

## Decision
Customer, address and contact capabilities are implemented in one
customer-service and one customer_db.

## Reason
Customer creation must persist demographic, address and contact data
atomically in one ACID transaction.

## Consequences
- Address and contact are internal modules, not separate deployables.
- Future extraction requires changing the consistency requirement.