# Write-up — Seat Reservation at Scale

## Atomic decision (race-free mechanism)

**Goal:** Exactly one winner per seat under concurrent `POST /reserve`, with clean **409** declines (no double-sell, no 5xx for domain races).

**Mechanism (layered):**

1. **Single database transaction** (`@Transactional` on `ReservationService.reserve`) — commit or rollback as a unit (all-or-nothing multi-seat).

2. **Per-user limit — row lock on `show_user_state`**  
   `ShowUserStateService.lockForUpdate(showId, userId)` runs `SELECT … FROM show_user_state … FOR UPDATE` (native SQL for MariaDB/MySQL).  
   `reserved_count` is checked and incremented in the same transaction **before** seats are confirmed.  
   **Why race-free:** Two transactions for the same user serialize on the `show_user_state` row; one passes the limit check, the other sees updated count and gets **409 PER_USER_LIMIT**.

3. **Seat contention — pessimistic lock + conditional update**  
   - `SeatRepository.findSeatsForUpdate`: native `SELECT * FROM seats … ORDER BY seat_number FOR UPDATE` locks requested rows in **sorted seat order** (deadlock avoidance for multi-seat).  
   - In-memory check: all seats must be `AVAILABLE`.  
   - `confirmSeatIfAvailable`:  
     `UPDATE seats SET status='CONFIRMED', … WHERE id=? AND status='AVAILABLE'`  
     **Why race-free:** Even if two transactions passed the read check, only one `UPDATE` affects a row; the loser gets `updated==0` → **409 SEAT_TAKEN**.

4. **Idempotency — unique constraint**  
   `UNIQUE (user_id, idempotency_key)` on `reservations`. Parallel duplicate inserts hit the constraint; we catch `DataIntegrityViolationException` and **replay** the existing row (no second reservation).

**Multi-seat deadlock avoidance:** Lock order is fixed: **`show_user_state` (user row) → seats in ascending `seat_number`**. All code paths acquire locks in that order, so circular wait on seat rows alone is avoided.

**Partial multi-seat:** All-or-nothing — if any seat is taken or missing, the transaction aborts with **409**; no partial confirm.

---

## Idempotency

| Topic | Implementation |
|--------|----------------|
| **Storage** | `reservations.idempotency_key` + `reservations.request_hash` (SHA-256 of sorted seat list) |
| **Constraint** | `UNIQUE (user_id, idempotency_key)` |
| **Exactly-once effect** | Fast path: `findByUserIdAndIdempotencyKey` before work. Slow path: duplicate insert → catch unique violation → reload and return same reservation |
| **Same key, same body** | **200** replay (`ReservationResult.replayed=true`), metric `idempotent_replay` |
| **Same key, different seats** | Hash mismatch → **409** `IDEMPOTENCY_KEY_REUSED` |
| **Client identity** | `Authorization: Bearer <user_id>` only; body cannot spoof user |

Seat list for a replay is read from `reservation_seats` (not inferred from the request alone).

---

## Holds and expiry

**Model chosen:** **Immediate confirm** (no timed `HELD` TTL). Seats go `AVAILABLE` → `CONFIRMED` in one transaction.

**Release:** `POST /reservations/{id}/cancel` (owner only):

- Lock reservation row `FOR UPDATE`
- Lock seat rows for that reservation
- `releaseSeatIfOwned`: `UPDATE seats … WHERE reservation_id=? AND status='CONFIRMED'`
- Reservation → `CANCELLED`; `show_user_state.reserved_count` decremented

Released seats are bookable again; cancel cannot resurrect a seat already owned by another reservation (conditional update returns 0 → **409**).

**Not implemented:** auto-expiry scheduler for `HELD` + `hold_expires_at` (schema supports `HELD` for future work).

---

## Consistency vs availability (partition)

**CP bias:** We use **one MySQL** as system of record with **strong consistency** inside a transaction (locks + conditional updates). Under network partition:

- If app **cannot reach DB**, readiness fails (**503**), load balancers should not route traffic — **availability** of the API drops, but we do not accept reservations we cannot persist (**no double-sell**).
- If DB is up but a client is isolated, they get errors/timeouts; no “best effort” confirm without commit.

**Trade-off:** Correctness and inventory invariant over accepting writes during DB failure.

---

## Observability (2am paging)

| Signal | Alert idea |
|--------|------------|
| `readiness` **DOWN** / DB probe fail | Page — service cannot serve correct reserves |
| Spike in **5xx** rate (ingress or logs) | Page — regression or DB overload |
| `reservations_declined_total{reason=seat_taken}` flat while traffic high | Investigate — possible storm or fraud |
| `seats_*` gauges vs API reconciliation drift | Page — invariant broken |
| Hikari pool exhaustion / connection timeouts | Page — DB connectivity or pool sizing |
| Burst script / external monitor: **>1 confirm on same seat** | Critical — correctness bug |

**Logs:** Railway deployment logs; startup logs JDBC host (no password). Correlate with `Idempotency-Key` / user in access logs if request logging is added.

**Metrics URL:** `GET /actuator/prometheus` on the live service.

---

## AI usage (honest)

| Area | AI role | Author ownership |
|------|---------|----------------|
| Boilerplate (DTOs, controllers, Flyway sketch) | Generated scaffolding | Reviewed and adjusted to assignment API |
| Concurrency design (`FOR UPDATE`, conditional `UPDATE`, idempotency replay) | Suggested patterns | Chose lock ordering, all-or-nothing, and where constraints live |
| Railway / Docker / `MYSQL_URL` wiring | Troubleshooting connection errors | Final variable-reference setup on Railway |
| Burst script (20k `Parallel.ForEach`) | Drafted structure | Tuned phases, counts, pass/fail checks |
| WRITEUP | Draft from actual code | Verified against implementation |

AI accelerated exploration; **grading-relevant choices** (transaction boundaries, lock order, idempotency on unique constraint, cancel semantics) were validated by local + production smoke/burst tests.

---

## What I would do next

1. **Integration / burst tests in CI** against a test DB; gate merges on zero 5xx and reconciliation.
2. **Time-boxed holds** + expiry worker for `HELD` seats (schema already has `hold_expires_at`).
3. **Structured request logging** (correlation id) and export to Railway/log drain.
4. **Rate limiting** at edge for create-show / reserve.
5. **Read replicas** for `GET /shows/{id}` with sticky writer for reserves (if scale demands).
6. **Rotate secrets** and remove any credentials ever committed to git history.

---

## Live service

- **URL:** https://seatreseravationsystem-production.up.railway.app  
- **Burst:** `powershell -File burst.ps1 -BaseUrl https://seatreseravationsystem-production.up.railway.app -TotalRequests 20000`
