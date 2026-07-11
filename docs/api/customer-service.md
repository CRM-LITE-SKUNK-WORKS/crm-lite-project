# customer-service API

Customer aggregate service (FR-CUST, FR-ADDR, FR-CNTC). Port **8082**, database
`customer_db`. Public path through the gateway: `http://localhost:8080/api/...`.
All examples below go through the gateway.

**Identifiers:** every public `{customerNumber}` path variable and the `customerId`
search parameter are the **business customer number** (`CUST.customer_number`, seeds
start at 1001). The internal database id is never exposed.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/customers` | **Canonical (and only) search endpoint.** The old `/api/customers/search` alias was removed. |
| GET | `/api/customers/{customerNumber}` | Detail (active customers only; else 404 `MSG-CUST-NOT-FOUND`) |
| POST | `/api/customers` | Atomic create (demographic + addresses + contact) |
| PUT | `/api/customers/{customerNumber}` | Demographic update |
| DELETE | `/api/customers/{customerNumber}` | Soft delete of the whole aggregate (204) |
| GET | `/api/customers/{customerNumber}/addresses` | Active addresses |
| POST | `/api/customers/{customerNumber}/addresses` | Add address (first one becomes primary automatically) |
| PUT | `/api/customers/{customerNumber}/addresses/{addressId}` | Update address fields |
| DELETE | `/api/customers/{customerNumber}/addresses/{addressId}` | Soft delete (guards: last address 409 `MSG-ADDR-LAST-DELETE`, primary 409 `MSG-ADDR-PRIMARY-DELETE`) |
| PATCH | `/api/customers/{customerNumber}/addresses/{addressId}/primary` | Make primary (demotes the previous one) |
| GET | `/api/customers/{customerNumber}/contact-medium` | Contact info |
| PUT | `/api/customers/{customerNumber}/contact-medium` | Update contact info |
| GET | `/api/cities` · `/api/cities/{cityId}/districts` | Cascading address dropdown data |

## Search (`GET /api/customers`) — KR-01 semantics

Query parameters: `firstName`, `lastName`, `nationalityId`, `customerId`,
`gsmNumber`, `accountNumber`, `orderNumber`, `page` (default 0), `size` (default 20).

- At least one criterion required, else 400 `MSG-SEARCH-CRITERIA-REQUIRED`.
- `firstName` matches **word-start, case-insensitive, over First + Middle Name
  combined**: `Kemal` finds "Ali Kemal"; `Nur` finds "Zeynep Nur"; `li` does NOT find "Ali".
- `lastName` matches word-start over Last Name only. Both present ⇒ AND-ed.
- `gsmNumber` is a prefix match on the mobile phone (CNTC_MEDIUM is local now — no 501).
- `nationalityId` and `customerId` match exactly.
- Filled criterion groups are **OR-ed**; results are always distinct customers.
- Only **active** customers return (soft-deleted are invisible).
- Sorted firstName → lastName; 20 per page, server-side.
- `accountNumber`/`orderNumber` ⇒ **501 `MSG-FEATURE-NOT-IMPLEMENTED`** until the
  account/order domains exist.
- Numeric fields reject non-numeric input with 400.

Result row: `{customerId, firstName, middleName, lastName, role, nationalityId}` —
`role` is the display name `"Customer"` (ROLE.role_name), `customerId` is the business number.

## Atomic create (`POST /api/customers`)

```json
{
  "demographic": {
    "firstName": "Velihan", "middleName": null, "lastName": "Gözek",
    "fatherName": null, "motherName": null,
    "birthDate": "1992-03-15", "gender": "Male", "nationalityId": "10000000004"
  },
  "addresses": [
    { "cityId": 1, "districtId": 1, "street": "Example Street",
      "houseFlatNumber": "10/2", "addressDescription": "Home", "primary": true }
  ],
  "contactMedium": {
    "email": "velihan@example.com", "homePhone": null,
    "mobilePhone": "05321112233", "fax": null
  }
}
```

Pipeline (single transaction — any failure persists **nothing**):

1. Bean validation: VR-NAME (Turkish letters ÇĞİÖŞÜçğıöşü supported, 1–50, trimmed
   first), VR-NATID, VR-EMAIL, VR-PHONE/VR-MOBILE; ≥1 address; email+mobile required.
2. Business rules: birth date not future (`MSG-VAL-BIRTHDATE`), age ≥ 18
   (`MSG-VAL-AGE-MIN`), Nationality ID **globally & permanently** unique — soft-deleted
   customers still block it (409 `MSG-CUST-DUP-NATID`, ADR-003); exactly one primary
   address after normalization; district must belong to the selected city.
3. Shared catalog resolution (ADR-002): `ACTV`, `INDV`, gender `MALE`/`FEMALE`
   resolved and domain-validated through lookup-service. Unknown code ⇒ 400 with field
   detail; catalog unreachable ⇒ **503 `MSG-SERVICE-UNAVAILABLE`** (fail closed).
4. MERNIS verification (KR-10) via mernis-stub: rejected ⇒ 400
   `MSG-NATID-VERIFY-FAILED`; unreachable ⇒ 503. Customer is NOT created in either case.
5. Persist PARTY → IND → PARTY_ROLE → CUST (sequence-assigned `customerNumber`)
   → ADDR rows → CNTC_MEDIUM. Returns **201** with the detail payload.

Detail payload: `{customerNumber, firstName, middleName, lastName, fatherName,
motherName, birthDate, gender, nationalityId, role, status}` — `gender` is
`"Male"/"Female"`, `status` is the GNL_ST short code (`ACTV`).

## Error response shape (all endpoints)

```json
{
  "timestamp": "…", "status": 409, "error": "Conflict",
  "messageKey": "MSG-CUST-DUP-NATID",
  "message": "A customer already exists with this Nationality ID",
  "path": "/api/customers", "validationErrors": null
}
```

DB constraint races (e.g. duplicate NAT ID) map to **409**, never 500. Stack traces
never leak; root causes go to server logs.

## curl test sequence (dependency order)

> Windows/Git Bash note: send JSON via `--data-binary @-` heredocs (as below), not
> `-d '...'` — native curl mangles Turkish characters passed as command-line arguments.

```bash
# A) health (start order: postgres, config-server, discovery, lookup, mernis, gateway, customer)
curl http://localhost:8888/actuator/health
curl http://localhost:8761/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8080/actuator/health
curl http://localhost:8082/actuator/health

# B) shared catalog through the gateway (ADR-002)
curl http://localhost:8080/api/lookups/statuses/ACTV
curl "http://localhost:8080/api/lookups/types?domain=GENDER"

# C) reference data
curl http://localhost:8080/api/cities
curl http://localhost:8080/api/cities/1/districts

# D) search semantics (seed: 1001 Ali Yildiz, 1002 Zeynep Nur Demir, 1003 soft-deleted)
curl "http://localhost:8080/api/customers?firstName=Ali"        # 1001
curl "http://localhost:8080/api/customers?firstName=Nur"        # 1002 via middle name
curl "http://localhost:8080/api/customers?firstName=li"         # empty (word-start!)
curl "http://localhost:8080/api/customers?lastName=De"          # 1002
curl "http://localhost:8080/api/customers?nationalityId=12345678901"   # 1001
curl "http://localhost:8080/api/customers?customerId=1001"      # 1001
curl "http://localhost:8080/api/customers?gsmNumber=0532"       # 1001
curl "http://localhost:8080/api/customers?customerId=1003"      # empty (soft-deleted)
curl -i "http://localhost:8080/api/customers?nationalityId=abc" # 400 numeric-only
curl -i "http://localhost:8080/api/customers?accountNumber=0101112900"  # 501
curl -i "http://localhost:8080/api/customers/search?firstName=Ali"      # alias removed (400/404)

# E) detail
curl http://localhost:8080/api/customers/1001

# F) atomic create with Turkish characters
curl -X POST "http://localhost:8080/api/customers" \
  -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'JSON'
{
  "demographic": {"firstName":"Velihan","lastName":"Gözek","birthDate":"1992-03-15",
                  "gender":"Male","nationalityId":"10000000004"},
  "addresses": [{"cityId":1,"districtId":1,"street":"Example Street",
                 "houseFlatNumber":"10/2","addressDescription":"Home","primary":true}],
  "contactMedium": {"email":"velihan@example.com","mobilePhone":"05321112233"}
}
JSON
# expect: 201, customerNumber 1004, role "Customer", status "ACTV"

# G) duplicate Nationality ID (ADR-003) — repeat F (409); then try 34567890123,
#    which belongs to SOFT-DELETED customer 1003: still 409 MSG-CUST-DUP-NATID.

# H) MERNIS rejection fixture: nationalityId 99999999999 is deny-listed in
#    mernis-stub -> 400 MSG-NATID-VERIFY-FAILED, nothing persisted.

# I) addresses
curl http://localhost:8080/api/customers/1001/addresses
curl -X POST http://localhost:8080/api/customers/1001/addresses \
  -H "Content-Type: application/json" --data-binary @- <<'JSON'
{"cityId":2,"districtId":3,"street":"Yeni Cad.","houseFlatNumber":"7","addressDescription":"Is"}
JSON
# PATCH .../addresses/{id}/primary  -> switch primary
# DELETE the primary while others exist -> 409 MSG-ADDR-PRIMARY-DELETE
# DELETE the only remaining address    -> 409 MSG-ADDR-LAST-DELETE

# J) contact medium
curl http://localhost:8080/api/customers/1001/contact-medium
curl -X PUT http://localhost:8080/api/customers/1001/contact-medium \
  -H "Content-Type: application/json" --data-binary @- <<'JSON'
{"email":"ali.yeni@example.com","mobilePhone":"05327778899","homePhone":"02161112233"}
JSON

# K) soft delete + invisibility
curl -i -X DELETE http://localhost:8080/api/customers/1004     # 204
curl -i http://localhost:8080/api/customers/1004               # 404 MSG-CUST-NOT-FOUND
curl "http://localhost:8080/api/customers?customerId=1004"     # empty

# L) catalog-down behaviour (stop lookup-service first)
#    POST /api/customers -> 503 MSG-SERVICE-UNAVAILABLE, nothing persisted;
#    GET  /api/customers?firstName=Ali still works (reads filter locally, ADR-002).
```

## Known limitations (intentional, documented)

- `accountNumber`/`orderNumber` search → 501 until account/order domains exist.
- Active-product check before delete and billing-account passivation → future
  account/product domains (TODO, no-op today).
- Address in-use check (`MSG-ADDR-IN-USE`) → no-op until account/service addresses exist.
- Gateway security is `permitAll` until authentication lands (ADR-004).
