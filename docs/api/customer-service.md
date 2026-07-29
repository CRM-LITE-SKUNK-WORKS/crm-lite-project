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
| GET | `/api/customers` | **Canonical list + filter endpoint (ADR-005).** No parameters ⇒ browse all active customers; with parameters ⇒ filter. The old `/api/customers/search` alias stays removed. |
| GET | `/api/customers/nationality-id-availability?nationalityId={id}` | **Availability probe** — is this Nationality ID still free for a create? Reports the ADR-003 rule in full (**soft-deleted holders included**), unlike every other read here |
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

## List + filter (`GET /api/customers`) — ADR-005 / KR-01 semantics

Query parameters (all optional): `firstName`, `lastName`, `nationalityId`,
`customerId`, `gsmNumber`, `accountNumber`, `orderNumber`, `page` (default 0),
`size` (default 20).

- **No parameters ⇒ browse mode (AC-CUST-01-00):** returns all ACTIVE, non-deleted
  customers, server-side paginated — the post-login main-page list. The old
  400 `MSG-SEARCH-CRITERIA-REQUIRED` rejection is **gone** (rule removed by the
  16.07.2026 FR revision).
- `firstName` matches **word-start, case-insensitive, over First + Middle Name
  combined**: `Kemal` finds "Ali Kemal"; `Nur` finds "Zeynep Nur"; `li` does NOT find "Ali".
- `lastName` matches word-start over Last Name only. Both present ⇒ AND-ed.
- `gsmNumber` is a prefix match on the mobile phone.
- `nationalityId` and `customerId` match exactly.
- Filled criterion groups are **OR-ed**; results are always distinct customers.
- Only **active** customers return (soft-deleted are invisible) — browse and filter alike.
- Sorted firstName → lastName → customerNumber (stable A-Z, KR-04/AC-CUST-01-00).
- `accountNumber`/`orderNumber` ⇒ **501 `MSG-FEATURE-NOT-IMPLEMENTED`** until the
  account/order domains exist.
- Numeric fields reject non-numeric input with 400.

**Result rows use the full detail contract** (`Page<CustomerDetailResponse>`) —
exactly the same fields as `GET /api/customers/{customerNumber}`:

```json
{
  "customerNumber": 1001, "firstName": "Ali", "middleName": null, "lastName": "Yildiz",
  "fatherName": "Hasan", "motherName": "Ayse", "birthDate": "1990-05-14",
  "gender": "Male", "nationalityId": "12345678901", "role": "Customer", "status": "ACTV"
}
```

> Contract change (ADR-005): the old slim `CustomerSearchResponse` row (with its
> `customerId` field) was **deleted**. List rows now carry `customerNumber` and the
> complete demographic set. The `customerId` **query parameter** keeps its name.

> KR-04 note: the UI default Per Page is **15** (options 15/30/50) — the API default
> is 20 and the frontend passes `size` explicitly. Recorded as an open item in ADR-005.

## Nationality-ID availability (`GET /api/customers/nationality-id-availability`)

Added 2026-07-29 — **ADR-005 §Addendum**. Exists for one reason: ADR-003 uniqueness is
global and permanent, so a **soft-deleted** customer still reserves its Nationality ID,
but every other read endpoint here is active-only by design (`GET /api/customers`
filters `status_id = ACTV AND deleted_date IS NULL`). Without this probe the create
screen could not tell the user before the POST answered 409.

```bash
curl -sS "http://localhost:8080/api/customers/nationality-id-availability?nationalityId=34567890123"
# {"available": false}   <- held by soft-deleted customer 1003; the list endpoint shows nothing
curl -sS "http://localhost:8080/api/customers/nationality-id-availability?nationalityId=99988877766"
# {"available": true}
```

- Answers **exactly one field**, `available`. It never says *who* holds the ID — a name
  or customer number here would turn a yes/no check into a way of mining deleted people.
- Same rule, same rows as create: both go through
  `CustomerBusinessRules.isNationalityIdAvailableForCreate`, so the probe and the
  create-time authority cannot drift apart.
- **Advisory, not a reservation.** `available: true` is a snapshot; a create landing in
  between still gets 409 `MSG-CUST-DUP-NATID` (and, in a race, the DB UNIQUE
  constraint). Clients must keep handling that 409.
- `nationalityId` is **required** and numeric-only: missing ⇒ 400 `MSG-VALIDATION-ERROR`
  (`validationErrors: {nationalityId: "is required"}`), non-numeric ⇒ 400 with
  `"must contain digits only"`.
- Not a list/filter alias (ADR-005 §1 stands): it returns no customer data and takes no
  paging/sorting parameters. The literal path segment takes precedence over
  `/{customerNumber}`, which is unchanged.

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
4. MERNIS verification (KR-10 / AC-CUST-03-06) via mernis-stub: rejected ⇒ **400
   `MSG-CUST-NATID-VERIFICATION-FAILED`**; unreachable ⇒ **503
   `MSG-MERNIS-UNAVAILABLE`**. Customer is NOT created in either case.
   (These are the v8 Final analyst catalog keys — they replaced the older
   project-specific `MSG-NATID-VERIFY-FAILED` and, for MERNIS outages, the generic
   `MSG-SERVICE-UNAVAILABLE`.)
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
> Every request prints its HTTP status via `-w "\nHTTP Status: %{http_code}\n"`.

### A) Infrastructure health (start order: postgres, config, discovery, lookup, mernis, gateway, customer)

```bash
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8888/actuator/health
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8761/actuator/health
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8083/actuator/health
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8084/actuator/health
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/actuator/health
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8082/actuator/health
```

### B) Shared catalog through the gateway (ADR-002)

```bash
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/lookups/statuses/ACTV
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/lookups/types?domain=GENDER"
```

### C) Reference data (cities / cascading districts)

```bash
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/cities
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/cities/1/districts
```

### D) Browse + filter semantics (seed: 1001 Ali Yildiz, 1002 Zeynep Nur Demir, 1003 soft-deleted)

```bash
# ADR-005 browse mode: NO parameters -> 200, all active customers, A-Z, paginated
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers"

# pagination
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?page=0&size=1"
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?page=1&size=1"

# filters (KR-01)
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?firstName=Ali"        # 1001
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?firstName=Nur"        # 1002 via middle name
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?firstName=li"         # empty (word-start!)
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?lastName=De"          # 1002
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?firstName=Zeynep&lastName=Demir"  # AND within name group
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?nationalityId=12345678901"        # 1001 exact
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?customerId=1001"      # 1001 exact
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?gsmNumber=0532"       # 1001 prefix
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?firstName=Ali&gsmNumber=0533"     # OR across groups: 1001 + 1002
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?customerId=1003"      # empty (soft-deleted invisible)

# negative cases
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?nationalityId=abc"            # 400 numeric-only
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?accountNumber=0101112900"     # 501 future domain
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?orderNumber=5001"             # 501 future domain
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers/search?firstName=Ali"         # alias removed (400/404)
```

### E) Detail

```bash
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/customers/1001
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/customers/9999   # 404 MSG-CUST-NOT-FOUND
```

### F) Atomic create with Turkish characters

```bash
curl -sS -X POST "http://localhost:8080/api/customers" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{
  "demographic": {"firstName":"Velihan","lastName":"Gözek","birthDate":"1992-03-15",
                  "gender":"Male","nationalityId":"10000000004"},
  "addresses": [{"cityId":1,"districtId":1,"street":"Example Street",
                 "houseFlatNumber":"10/2","addressDescription":"Home","primary":true}],
  "contactMedium": {"email":"velihan@example.com","mobilePhone":"05321112233"}
}
JSON
# expect: HTTP Status: 201, customerNumber >= 1004, role "Customer", status "ACTV"
```

### G) Duplicate Nationality ID (ADR-003)

```bash
# repeat F verbatim -> HTTP Status: 409 MSG-CUST-DUP-NATID
# 34567890123 belongs to SOFT-DELETED seed customer 1003 — still reserved:
curl -sS -X POST "http://localhost:8080/api/customers" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{
  "demographic": {"firstName":"Yeni","lastName":"Kisi","birthDate":"1990-01-01",
                  "gender":"Male","nationalityId":"34567890123"},
  "addresses": [{"cityId":1,"districtId":1,"street":"Sok.","houseFlatNumber":"1",
                 "addressDescription":"Ev","primary":true}],
  "contactMedium": {"email":"yeni@example.com","mobilePhone":"05320001122"}
}
JSON
# expect: HTTP Status: 409 MSG-CUST-DUP-NATID (soft delete never releases the id)
```

### H) MERNIS rejection through customer create (KR-10)

```bash
# nationalityId 99999999999 is deny-listed in mernis-stub:
curl -sS -X POST "http://localhost:8080/api/customers" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{
  "demographic": {"firstName":"Red","lastName":"Deneme","birthDate":"1991-01-01",
                  "gender":"Female","nationalityId":"99999999999"},
  "addresses": [{"cityId":1,"districtId":1,"street":"Sok.","houseFlatNumber":"2",
                 "addressDescription":"Ev","primary":true}],
  "contactMedium": {"email":"red@example.com","mobilePhone":"05320003344"}
}
JSON
# expect: HTTP Status: 400 MSG-CUST-NATID-VERIFICATION-FAILED, nothing persisted
```

### I) Invalid validation cases

```bash
# under 18 -> 400 MSG-VAL-AGE-MIN
curl -sS -X POST "http://localhost:8080/api/customers" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{
  "demographic": {"firstName":"Genc","lastName":"Kisi","birthDate":"2015-01-01",
                  "gender":"Male","nationalityId":"10000000099"},
  "addresses": [{"cityId":1,"districtId":1,"street":"Sok.","houseFlatNumber":"3",
                 "addressDescription":"Ev","primary":true}],
  "contactMedium": {"email":"genc@example.com","mobilePhone":"05320005566"}
}
JSON
# expect: HTTP Status: 400 MSG-VAL-AGE-MIN

# invalid email + invalid mobile in one request -> 400 with validationErrors map
curl -sS -X POST "http://localhost:8080/api/customers" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{
  "demographic": {"firstName":"Bozuk","lastName":"Veri","birthDate":"1990-01-01",
                  "gender":"Male","nationalityId":"10000000098"},
  "addresses": [{"cityId":1,"districtId":1,"street":"Sok.","houseFlatNumber":"4",
                 "addressDescription":"Ev","primary":true}],
  "contactMedium": {"email":"not-an-email","mobilePhone":"02121112233"}
}
JSON
# expect: HTTP Status: 400, validationErrors with contactMedium.email / contactMedium.mobilePhone
```

### J) Demographic update + soft delete

```bash
curl -sS -X PUT "http://localhost:8080/api/customers/1001" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"firstName":"Ali","middleName":"Kemal","lastName":"Yildiz",
 "birthDate":"1990-05-14","gender":"Male","nationalityId":"12345678901"}
JSON
# expect: HTTP Status: 200, middleName "Kemal"

# soft delete a customer created in step F (adjust the number):
curl -sS -X DELETE -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/customers/1004   # 204
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/customers/1004             # 404
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?customerId=1004" # empty page
```

### K) Addresses (list, create, PUT update, set primary, delete + guards)

```bash
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/customers/1001/addresses

# create a second address
curl -sS -X POST "http://localhost:8080/api/customers/1001/addresses" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"cityId":2,"districtId":3,"street":"Yeni Cad.","houseFlatNumber":"7","addressDescription":"Is"}
JSON
# expect: HTTP Status: 201 (note the returned addressId for the next steps)

# UPDATE ADDRESS with PUT (use the addressId returned above, e.g. 4)
curl -sS -X PUT "http://localhost:8080/api/customers/1001/addresses/4" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"cityId":2,"districtId":3,"street":"Guncel Cad.","houseFlatNumber":"7/2","addressDescription":"Is guncel"}
JSON
# expect: HTTP Status: 200

# set primary (demotes the previous primary)
curl -sS -X PATCH -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:8080/api/customers/1001/addresses/4/primary        # 200

# guards (seed 1001 starts with addresses 1=primary and 2; the create above added a third):
curl -sS -X DELETE -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:8080/api/customers/1001/addresses/4                # 409 MSG-ADDR-PRIMARY-DELETE (it is primary now)
# delete the non-primary ones, then try the last remaining address:
curl -sS -X DELETE -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:8080/api/customers/1001/addresses/1                # 204 (non-primary, not last)
curl -sS -X DELETE -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:8080/api/customers/1001/addresses/2                # 204 (non-primary, not last)
curl -sS -X DELETE -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:8080/api/customers/1001/addresses/4                # 409 MSG-ADDR-LAST-DELETE (only one left)

# invalid city/district relation (district 3 = Cankaya belongs to Ankara, not Istanbul):
curl -sS -X POST "http://localhost:8080/api/customers/1001/addresses" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"cityId":1,"districtId":3,"street":"Ters","houseFlatNumber":"9","addressDescription":"Hatali"}
JSON
# expect: HTTP Status: 400
```

> **Address delete confirmation (AC-ADDR-04-03, `MSG-ADDR-DELETE-CONFIRM`):** the
> confirmation modal is a **frontend** interaction — the backend has no
> "confirm" endpoint. The UI shows MSG-ADDR-DELETE-CONFIRM with Yes/No and only calls
> `DELETE .../addresses/{id}` after Yes. The backend then answers 204 (deleted),
> 409 `MSG-ADDR-LAST-DELETE` / `MSG-ADDR-PRIMARY-DELETE` (guards the UI normally
> prevents), or — once account/service addresses exist — `MSG-ADDR-IN-USE`.

### L) Contact medium

```bash
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/customers/1001/contact-medium

curl -sS -X PUT "http://localhost:8080/api/customers/1001/contact-medium" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"email":"ali.yeni@example.com","mobilePhone":"05327778899","homePhone":"02161112233"}
JSON
# expect: HTTP Status: 200

# invalid contact data (VR-MOBILE): mobile must start with 05
curl -sS -X PUT "http://localhost:8080/api/customers/1001/contact-medium" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"email":"ali.yeni@example.com","mobilePhone":"02121112233"}
JSON
# expect: HTTP Status: 400 MSG-VAL-PHONE (field-level)
```

### M) Failure behaviour of upstream dependencies

```bash
# lookup-service DOWN (stop it first):
#   POST /api/customers -> HTTP Status: 503 MSG-SERVICE-UNAVAILABLE, nothing persisted
#   GET  /api/customers?firstName=Ali still 200 (reads filter locally, ADR-002)
# mernis-stub DOWN (stop it first):
#   POST /api/customers -> HTTP Status: 503 MSG-MERNIS-UNAVAILABLE, nothing persisted
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?firstName=Ali"
```

## Known limitations (intentional, documented)

- `accountNumber`/`orderNumber` search → 501 until account/order domains exist.
- Active-product check before delete and billing-account passivation → future
  account/product domains (TODO, no-op today).
- Address in-use check (`MSG-ADDR-IN-USE`) → no-op until account/service addresses exist.
- All endpoints require a Keycloak-authenticated caller with the `crm-user` role
  (ADR-006..009): via the gateway that means a logged-in BFF session (browser /
  `docs/postman/README.md` §Authentication); direct calls need a valid
  `Authorization: Bearer` JWT. Anonymous requests get 401 `MSG-AUTH-UNAUTHORIZED`.
  See `docs/api/authentication.md`.
