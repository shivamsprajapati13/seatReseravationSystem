# Local API test report

**Date:** 2026-10-03  
**Fixed build tested on:** `http://localhost:8601` (restarted with latest code)  
**Your process on `http://localhost:8600`:** still running **old** bytecode — `POST .../reserve` returns **500** until you restart after pulling the `SeatRepository` fix.

## Critical finding (reserve 500 on old server)

Root cause: Hibernate generated `FOR UPDATE OF s1_0`, which **MariaDB/MySQL rejects** → uncaught SQL exception → **500**.

**Fix applied:** native query with `FOR UPDATE` (no `OF`) in `SeatRepository.findSeatsForUpdate`.

**Action for you:** stop the app on 8600 and run again (`mvnw spring-boot:run` or IDE restart).

---

## Summary (8601 — after fix)

| # | Scenario | HTTP | Result |
|---|----------|------|--------|
| 1 | Liveness | 200 | UP |
| 2 | Readiness | 200 | UP |
| 3 | Create show | 201 | 5 seats available |
| 4 | GET show (initial) | 200 | counts: 5 available |
| 5 | Alice reserves A1 | 201 | confirmed |
| 6 | GET show (after) | 200 | A1 confirmed, 4 available |
| 7 | Bob reserves A1 | 409 | SEAT_TAKEN |
| 8 | Alice idempotent replay | 200 | same reservation_id |
| 9 | Alice same key, different seats | 409 | IDEMPOTENCY_KEY_REUSED |
| 10 | Reserve without auth | 401 | UNAUTHORIZED |
| 11 | Create show without admin | 403 | FORBIDDEN |

**Reconciliation after test 6:** `available(4) + held(0) + confirmed(1) = total_seats(5)` ✓

**Show ID used:** `ffb17bed-4f3b-4696-b980-aff8bd072e88`  
**Reservation ID:** `f65475de-0185-42ce-bc0c-0262f8ed8d95`

Raw JSON files: `test-artifacts/responses/*.json`

Re-run: `powershell -File test-artifacts/run-smoke-tests.ps1 -Base http://localhost:8600` (after restart)

---

## Actual curls (copy-paste)

Set base URL (use **8600** after you restart with the fix):

```bash
BASE=http://localhost:8600
ADMIN=admin-secret
SHOW=ffb17bed-4f3b-4696-b980-aff8bd072e88
```

### Health

```bash
curl -s -w "\nHTTP %{http_code}\n" "$BASE/actuator/health/liveness"
```

**Response (8601):**
```json
{"status":"UP"}
```
HTTP **200**

```bash
curl -s -w "\nHTTP %{http_code}\n" "$BASE/actuator/health/readiness"
```

**Response (8601):**
```json
{"status":"UP"}
```
HTTP **200**

---

### Create show

**Request:**
```http
POST /shows HTTP/1.1
Host: localhost:8600
Content-Type: application/json
X-Admin-Token: admin-secret

{"name":"test-friday-night","seats":["A1","A2","A3","A4","A5"],"price_paise":25000}
```

**curl:**
```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE/shows" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: $ADMIN" \
  --data-binary @test-artifacts/create-show.json
```

**Response (8601):**
```json
{
  "id": "ffb17bed-4f3b-4696-b980-aff8bd072e88",
  "name": "test-friday-night",
  "price_paise": 25000,
  "per_user_limit": 4,
  "seats": [
    {"seat_number": "A1", "status": "available"},
    {"seat_number": "A2", "status": "available"},
    {"seat_number": "A3", "status": "available"},
    {"seat_number": "A4", "status": "available"},
    {"seat_number": "A5", "status": "available"}
  ]
}
```
HTTP **201**

---

### GET show

```bash
curl -s -w "\nHTTP %{http_code}\n" "$BASE/shows/$SHOW"
```

**Response after create (8601):**
```json
{
  "id": "ffb17bed-4f3b-4696-b980-aff8bd072e88",
  "name": "test-friday-night",
  "price_paise": 25000,
  "per_user_limit": 4,
  "seats": [ ... all "available" ... ],
  "counts": {"available": 5, "held": 0, "confirmed": 0, "total_seats": 5}
}
```
HTTP **200**

---

### Reserve (Alice → A1)

**Request:**
```http
POST /shows/{showId}/reserve HTTP/1.1
Content-Type: application/json
Authorization: Bearer alice
Idempotency-Key: alice-a1-v1

{"seats":["A1"]}
```

**curl:**
```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE/shows/$SHOW/reserve" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer alice" \
  -H "Idempotency-Key: alice-a1-v1" \
  --data-binary @test-artifacts/reserve-a1.json
```

**Response (8601):**
```json
{
  "reservation_id": "f65475de-0185-42ce-bc0c-0262f8ed8d95",
  "show_id": "ffb17bed-4f3b-4696-b980-aff8bd072e88",
  "user_id": "alice",
  "seats": ["A1"],
  "amount_paise": 25000,
  "status": "confirmed"
}
```
HTTP **201**

**Response on old 8600 (before restart):**
```json
{"timestamp":"2026-10-03T07:42:37.401Z","status":500,"error":"Internal Server Error","path":"/shows/.../reserve"}
```
HTTP **500**

---

### Seat taken (Bob → A1)

```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE/shows/$SHOW/reserve" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer bob" \
  -H "Idempotency-Key: bob-a1-v1" \
  --data-binary @test-artifacts/reserve-a1.json
```

**Response (8601):**
```json
{"message":"Seat already taken: A1","error":"SEAT_TAKEN"}
```
HTTP **409**

---

### Idempotent replay (Alice, same key + body)

Same curl as Alice reserve.

**Response (8601):**
```json
{
  "reservation_id": "f65475de-0185-42ce-bc0c-0262f8ed8d95",
  "show_id": "ffb17bed-4f3b-4696-b980-aff8bd072e88",
  "user_id": "alice",
  "seats": ["A1"],
  "amount_paise": 25000,
  "status": "confirmed"
}
```
HTTP **200** (replay; same `reservation_id`)

---

### Idempotency conflict (same key, different seats)

```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE/shows/$SHOW/reserve" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer alice" \
  -H "Idempotency-Key: alice-a1-v1" \
  -d '{"seats":["A2"]}'
```

**Response (8601):**
```json
{"error":"IDEMPOTENCY_KEY_REUSED"}
```
HTTP **409**

---

### Auth / admin errors

```bash
# 401 — no Authorization
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE/shows/$SHOW/reserve" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: no-auth" \
  --data-binary @test-artifacts/reserve-a1.json
```

**Response:** `{"message":"Missing Authorization header","error":"UNAUTHORIZED"}` — HTTP **401**

```bash
# 403 — no admin token
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE/shows" \
  -H "Content-Type: application/json" \
  --data-binary @test-artifacts/create-show.json
```

**Response:** `{"message":"Admin access required","error":"FORBIDDEN"}` — HTTP **403**

---

### Prometheus metrics (snapshot after smoke run)

```bash
curl -s "$BASE/actuator/prometheus" | grep -E '^(seats_|reservations_)'
```

See `test-artifacts/responses/12-prometheus-metrics.txt` for the scraped lines from 8601.

---

## Windows note

Use `--data-binary @file.json` for POST bodies (PowerShell mangles inline JSON). The smoke script does this automatically.
