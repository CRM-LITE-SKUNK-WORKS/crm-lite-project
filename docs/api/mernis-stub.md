# mernis-stub API (fake KPS/MERNIS simulation)

**This is a FAKE external identity-verification simulation** standing in for the
Turkish KPS/MERNIS service (KR-10 / AC-CUST-03-06). It is **not** a real identity
service, performs no real verification, and **uses no real personal data** —
behaviour is fully deterministic and driven by configuration.

- Direct local port: **8084** (no database).
- Normal consumer: **customer-service** (`com.crm.customer.mernis.MernisClient`)
  during atomic customer create — never the frontend.
- **Not exposed through the gateway**: no approved `/api/mernis/**` route exists in
  `config-repo/api-gateway.yml`, and none should be added without an architecture
  decision — a verification stub has no business being public.

## Behaviour (deterministic)

Any syntactically valid 11-digit `nationalityId` verifies **unless it is on the deny
list**. Default deny list: `99999999999` (config `mernis.stub.denied-ids` in
`config-repo/mernis-stub.yml`) — the stable local "rejected" fixture. The other
request fields are accepted for contract realism but not evaluated.

## Endpoint

| Method | Path | Description |
|---|---|---|
| POST | `/api/mernis/verify` | Verify a nationality id against the deterministic rules |

Request (`MernisVerifyRequest` — all fields required):

```json
{
  "nationalityId": "12345678901",
  "firstName": "Ali",
  "lastName": "Yildiz",
  "birthDate": "1990-05-14"
}
```

Response (`MernisVerifyResponse`): `{"verified": true}` or `{"verified": false}`.

## curl catalog (each request prints its HTTP status)

### 1. Health

```bash
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8084/actuator/health
```

### 2. Verification accepted

```bash
curl -sS -X POST "http://localhost:8084/api/mernis/verify" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"nationalityId":"12345678901","firstName":"Ali","lastName":"Yildiz","birthDate":"1990-05-14"}
JSON
# expect: {"verified":true}, HTTP Status: 200
```

### 3. Deny-listed Nationality ID rejected

```bash
curl -sS -X POST "http://localhost:8084/api/mernis/verify" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"nationalityId":"99999999999","firstName":"Red","lastName":"Deneme","birthDate":"1991-01-01"}
JSON
# expect: {"verified":false}, HTTP Status: 200 (the stub answered; the ANSWER is "rejected")
```

### 4. Invalid request/body validation

```bash
# missing fields -> 400 (bean validation)
curl -sS -X POST "http://localhost:8084/api/mernis/verify" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"nationalityId":"12345678901"}
JSON
# expect: HTTP Status: 400

# non-11-digit id -> answered but not verified
curl -sS -X POST "http://localhost:8084/api/mernis/verify" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"nationalityId":"123","firstName":"A","lastName":"B","birthDate":"1990-01-01"}
JSON
# expect: {"verified":false}, HTTP Status: 200
```

### 5. Customer-service aggregate create with ACCEPTED verification

```bash
curl -sS -X POST "http://localhost:8080/api/customers" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{
  "demographic": {"firstName":"Kabul","lastName":"Edilen","birthDate":"1990-01-01",
                  "gender":"Male","nationalityId":"10000000021"},
  "addresses": [{"cityId":1,"districtId":1,"street":"Sok.","houseFlatNumber":"5",
                 "addressDescription":"Ev","primary":true}],
  "contactMedium": {"email":"kabul@example.com","mobilePhone":"05320007788"}
}
JSON
# expect: HTTP Status: 201 (customer created)
```

### 6. Customer-service aggregate create with REJECTED verification

```bash
curl -sS -X POST "http://localhost:8080/api/customers" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{
  "demographic": {"firstName":"Red","lastName":"Edilen","birthDate":"1990-01-01",
                  "gender":"Female","nationalityId":"99999999999"},
  "addresses": [{"cityId":1,"districtId":1,"street":"Sok.","houseFlatNumber":"6",
                 "addressDescription":"Ev","primary":true}],
  "contactMedium": {"email":"red2@example.com","mobilePhone":"05320009900"}
}
JSON
# expect: HTTP Status: 400, messageKey MSG-CUST-NATID-VERIFICATION-FAILED, ZERO rows persisted
```

### 7. Unavailable-service test procedure (documented)

1. Stop mernis-stub (Ctrl-C in its terminal, or `docker compose -f infra/docker-compose.yml stop mernis-stub`).
2. Repeat the create from step 5 with a fresh `nationalityId`.
3. Expect: **HTTP Status: 503**, `messageKey` **`MSG-MERNIS-UNAVAILABLE`**, and no
   partial customer rows (fail closed, KR-10).
4. Restart mernis-stub and repeat — the same request now returns 201.
