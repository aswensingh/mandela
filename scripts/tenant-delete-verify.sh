#!/usr/bin/env bash
# Smoke-test for soft delete + hard purge tenant flow. Uses a per-run unique tenant
# name + admin username so it can be re-run without colliding with prior runs.
set -euo pipefail

BASE=http://localhost:8080
RUN_ID="$(date +%s)"
TENANT_NAME="PurgeMe-${RUN_ID}"
USERNAME="purge-${RUN_ID}"
PASSWORD="purge12345"

step() { echo; echo "================ $* ================"; }

ADMIN_TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

step "1. Create disposable tenant '$TENANT_NAME'"
CREATE=$(curl -fsS -X POST "$BASE/api/platform/tenants" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"$TENANT_NAME\",\"industry\":\"qa\",\"initialAdminUsername\":\"$USERNAME\",\"initialAdminPassword\":\"$PASSWORD\"}")
echo "$CREATE"
TENANT_ID=$(echo "$CREATE" | sed -E 's/.*"tenant":\{"id":"([0-9a-f-]{36})".*/\1/')
echo "TENANT_ID=$TENANT_ID"

step "2. Verify it appears in the default list"
curl -fsS "$BASE/api/platform/tenants?includeDeleted=false&size=200" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | grep -oE "\"name\":\"$TENANT_NAME\"|\"id\":\"$TENANT_ID\",\"name\":\"$TENANT_NAME\",\"industry\":\"qa\",\"status\":\"[A-Z_]+\"" | tail -2

step "3. Confirm its admin can log in"
LOGIN_OK=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
echo "login HTTP $LOGIN_OK (expect 200)"

step "4. Soft-delete it (DELETE /api/platform/tenants/{id})"
curl -fsS -X DELETE "$BASE/api/platform/tenants/$TENANT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | head -c 400; echo

step "5. Verify it disappears from default list"
HAS_NAME=$(curl -fsS "$BASE/api/platform/tenants?includeDeleted=false&size=200" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | grep -c "\"id\":\"$TENANT_ID\"" || true)
echo "occurrences of TENANT_ID in default list: $HAS_NAME (expect 0)"

step "6. Verify it reappears when includeDeleted=true with status DELETED"
curl -fsS "$BASE/api/platform/tenants?includeDeleted=true&size=200" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | grep -oE "\"id\":\"$TENANT_ID\",\"name\":\"$TENANT_NAME\",\"industry\":\"qa\",\"status\":\"[A-Z_]+\"" | head -1

step "7. Verify the admin can NO LONGER log in"
LOGIN_BLOCKED=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
echo "login HTTP $LOGIN_BLOCKED (expect 401)"

step "8. Purge with WRONG name -> should be 400"
WRONG=$(curl -sS -o /tmp/p.json -w "%{http_code}" -X DELETE "$BASE/api/platform/tenants/$TENANT_ID/purge" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"confirmName":"NopeWrong"}')
echo "HTTP $WRONG (expect 400)"; cat /tmp/p.json; echo

step "9. Purge with CORRECT name -> should be 204"
RIGHT=$(curl -sS -o /tmp/p.json -w "%{http_code}" -X DELETE "$BASE/api/platform/tenants/$TENANT_ID/purge" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"confirmName\":\"$TENANT_NAME\"}")
echo "HTTP $RIGHT (expect 204)"

step "10. Verify it is GONE from all listings (even includeDeleted)"
ALL_HITS=$(curl -fsS "$BASE/api/platform/tenants?includeDeleted=true&size=200" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | grep -c "\"id\":\"$TENANT_ID\"" || true)
echo "occurrences after purge: $ALL_HITS (expect 0)"

step "11. Verify the admin row is also gone"
GHOST=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
echo "purged-admin login HTTP $GHOST (expect 401)"

echo
echo "================ DONE — tenant-delete flow verified ================"
