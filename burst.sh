#!/usr/bin/env bash
# Burst load test — default 20,000 reserve attempts (chunked parallel workers).
set -euo pipefail

BASE_URL="${1:?Usage: ./burst.sh <BASE_URL>}"
BASE_URL="${BASE_URL%/}"
ADMIN_TOKEN="${ADMIN_TOKEN:-admin-secret}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-20000}"
PARALLEL="${PARALLEL:-64}"

reserve() {
  local show_id="$1" user="$2" key="$3" seat="$4"
  curl -s -o /dev/null -w "%{http_code}" -X POST \
    "$BASE_URL/shows/$show_id/reserve" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $user" \
    -H "Idempotency-Key: $key" \
    -d "{\"seats\":[\"$seat\"]}" \
    --max-time 180 2>/dev/null || echo 0
}

echo "=== Burst: $TOTAL_REQUESTS requests against $BASE_URL (parallel=$PARALLEL) ==="

SEAT_COUNT=$(( TOTAL_REQUESTS / 20 ))
[[ "$SEAT_COUNT" -lt 500 ]] && SEAT_COUNT=500
[[ "$SEAT_COUNT" -gt 2000 ]] && SEAT_COUNT=2000
SEATS=$(printf '"B%d",' $(seq 1 "$SEAT_COUNT") | sed 's/,$//')

CREATE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/shows" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: $ADMIN_TOKEN" \
  -d "{\"name\":\"burst-$(date +%s)\",\"seats\":[$SEATS],\"price_paise\":1000,\"per_user_limit\":4}")
CREATE_CODE=$(echo "$CREATE" | tail -n1)
CREATE_BODY=$(echo "$CREATE" | sed '$d')
[[ "$CREATE_CODE" == "201" ]] || { echo "Create failed: $CREATE_CODE"; exit 1; }
SHOW_ID=$(echo "$CREATE_BODY" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
RUN_ID=$(date +%s)
echo "ShowId: $SHOW_ID seats=$SEAT_COUNT"

HOT=$(( TOTAL_REQUESTS * 60 / 100 ))
IDEM=$(( TOTAL_REQUESTS * 25 / 100 ))
LIMIT=$(( TOTAL_REQUESTS * 10 / 100 ))
SCATTER=$(( TOTAL_REQUESTS - HOT - IDEM - LIMIT ))

WORK=$(mktemp)
RESULTS=$(mktemp)
trap 'rm -f "$WORK" "$RESULTS"' EXIT

i=1; while [[ $i -le $HOT ]]; do echo "hot-$i hot-$RUN_ID-$i B1" >> "$WORK"; i=$((i+1)); done
i=1; while [[ $i -le $IDEM ]]; do echo "idem-$RUN_ID idem-$RUN_ID B2" >> "$WORK"; i=$((i+1)); done
i=1; while [[ $i -le $LIMIT ]]; do echo "limit-$RUN_ID limit-$RUN_ID-$i B$(( (i % 40) + 3 ))" >> "$WORK"; i=$((i+1)); done
i=1; while [[ $i -le $SCATTER ]]; do echo "scatter-$i scatter-$RUN_ID-$i B$(( (i % (SEAT_COUNT - 2)) + 3 ))" >> "$WORK"; i=$((i+1)); done

TOTAL_LINES=$(wc -l < "$WORK")
CHUNK=$(( (TOTAL_LINES + PARALLEL - 1) / PARALLEL ))

worker() {
  local start="$1" count="$2"
  local line=0
  while IFS=' ' read -r user key seat; do
    line=$((line + 1))
    [[ "$line" -lt "$start" ]] && continue
    [[ "$line" -ge $((start + count)) ]] && break
    reserve "$SHOW_ID" "$user" "$key" "$seat"
  done < "$WORK"
}

export -f reserve
export BASE_URL SHOW_ID WORK

start=1
while [[ "$start" -le "$TOTAL_LINES" ]]; do
  while [[ $(jobs -r | wc -l) -ge $PARALLEL ]]; do wait -n 2>/dev/null || wait; done
  (
    worker "$start" "$CHUNK"
  ) | while read -r code; do echo "$code" >> "$RESULTS"; done &
  start=$((start + CHUNK))
done
wait

echo "--- HTTP code distribution ---"
sort "$RESULTS" | uniq -c | sort -rn

echo "--- Reconciliation (GET /shows/$SHOW_ID) ---"
curl -s "$BASE_URL/shows/$SHOW_ID" | python3 -m json.tool 2>/dev/null || curl -s "$BASE_URL/shows/$SHOW_ID"
echo ""
echo "Done."
