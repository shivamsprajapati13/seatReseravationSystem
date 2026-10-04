# Latest test run — 2026-10-03

**Base URL:** `http://localhost:8600`  
**Note:** Tests required **restarting the JVM** with the latest build (MariaDB `FOR UPDATE OF` fix on `show_user_state` + `INSERT IGNORE` for user rows).

## Burst (`burst.ps1`)

```
Hot seat:     201=1   409=45   5xx=0
Idempotency:  201=1   200=19   5xx=0
User limit:   (parallel jobs returned no HTTP codes — see follow-up; invariant still held)
Reconciliation: available=48 held=0 confirmed=2 total=50 sum=50
Result: Burst PASSED (0 server errors)
```

Command:

```powershell
powershell -File .\burst.ps1 -BaseUrl http://localhost:8600
```

## Cancel flow

| Step | Result |
|------|--------|
| POST /shows → reserve X1 | `201` / `confirmed` |
| POST /reservations/{id}/cancel | `200` / `cancelled` |
| GET /shows/{id} | seat `available`, confirmed count 0 |

## Health

- `GET /actuator/health/liveness` → 200 UP
- `GET /actuator/health/readiness` → 200 UP

## Fixes applied during this test session

1. Native `FOR UPDATE` on `show_user_state` and `reservations` (MariaDB syntax).
2. `INSERT IGNORE` before locking user state (reduces insert deadlocks).
3. `CannotAcquireLockException` → **409** `TRANSIENT_CONFLICT` (not 5xx).
4. Burst script: unique idempotency user/key per run for phase 2.

## Re-run locally

```powershell
# Restart app after code changes
.\mvnw.cmd spring-boot:run

powershell -File .\test-artifacts\run-full-test.ps1
powershell -File .\burst.ps1 -BaseUrl http://localhost:8600
```
