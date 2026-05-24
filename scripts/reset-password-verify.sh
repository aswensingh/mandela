#!/usr/bin/env bash
# Verify admin-assisted password reset + admin email visibility.
set -euo pipefail

BASE=http://localhost:8080
step() { echo; echo "================ $* ================"; }

PLATFORM_TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@marketinghub.local","password":"change-me-locally"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

step "1. Platform-admin tenants list shows admin emails per tenant"
curl -fsS "$BASE/api/platform/tenants?size=10" -H "Authorization: Bearer $PLATFORM_TOKEN" \
  | grep -oE '"name":"[^"]+","industry":[^,]*,"status":"[A-Z_]+","createdAt":"[^"]+","updatedAt":"[^"]+","admins":\[[^]]*\]' \
  | head -5

step "2. Platform admin resets acme@acme.com (generate)"
ACME_USER_ID=$(curl -fsS "$BASE/api/platform/tenants?size=10" -H "Authorization: Bearer $PLATFORM_TOKEN" \
  | grep -oE '"admins":\[\{"id":"[0-9a-f-]+","email":"acme@acme.com"' \
  | sed -E 's/.*"id":"([0-9a-f-]{36})".*/\1/' | head -1)
echo "Resolved Acme admin userId: $ACME_USER_ID"

RESET_RESP=$(curl -fsS -X POST "$BASE/api/users/$ACME_USER_ID/reset-password" \
  -H "Authorization: Bearer $PLATFORM_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"newPassword": null}')
echo "$RESET_RESP"
NEW_PASS=$(echo "$RESET_RESP" | sed -E 's/.*"newPassword":"([^"]+)".*/\1/')
echo "Generated new password: $NEW_PASS"

step "3. OLD password should now FAIL"
OLD_STATUS=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"acme@acme.com","password":"acme12345"}')
echo "old-password login HTTP $OLD_STATUS (expect 401)"

step "4. NEW password should WORK"
NEW_STATUS=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"acme@acme.com\",\"password\":\"$NEW_PASS\"}")
echo "new-password login HTTP $NEW_STATUS (expect 200)"

step "5. Restore acme12345 as the password so subsequent tests work"
curl -fsS -X POST "$BASE/api/users/$ACME_USER_ID/reset-password" \
  -H "Authorization: Bearer $PLATFORM_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"acme12345"}' > /dev/null
RESTORE=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"acme@acme.com","password":"acme12345"}')
echo "acme12345 login after restore HTTP $RESTORE (expect 200)"

step "6. Tenant admin (Acme) resets their AGENT's password"
ACME_ADMIN_TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"acme@acme.com","password":"acme12345"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
# Find agent1@acme.com via /api/users
AGENT_ID=$(curl -fsS "$BASE/api/users?size=50" -H "Authorization: Bearer $ACME_ADMIN_TOKEN" \
  | grep -oE '"id":"[0-9a-f-]+","tenantId":"[0-9a-f-]+","email":"agent1@acme.com"' \
  | sed -E 's/.*"id":"([0-9a-f-]{36})".*/\1/')
echo "Resolved agent userId: $AGENT_ID"

AGENT_RESET=$(curl -fsS -X POST "$BASE/api/users/$AGENT_ID/reset-password" \
  -H "Authorization: Bearer $ACME_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}')
echo "$AGENT_RESET"
AGENT_NEW=$(echo "$AGENT_RESET" | sed -E 's/.*"newPassword":"([^"]+)".*/\1/')
echo "Agent's new password: $AGENT_NEW"

AGENT_LOGIN=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"agent1@acme.com\",\"password\":\"$AGENT_NEW\"}")
echo "agent login with new password HTTP $AGENT_LOGIN (expect 200)"

step "7. Restore agent12345"
curl -fsS -X POST "$BASE/api/users/$AGENT_ID/reset-password" \
  -H "Authorization: Bearer $ACME_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"agent12345"}' > /dev/null
echo "agent12345 restored"

step "8. CROSS-TENANT: Acme admin tries to reset Beta's admin -> 403"
BETA_USER_ID=$(curl -fsS "$BASE/api/platform/tenants?size=10" -H "Authorization: Bearer $PLATFORM_TOKEN" \
  | grep -oE '"admins":\[\{"id":"[0-9a-f-]+","email":"beta@beta.com"' \
  | sed -E 's/.*"id":"([0-9a-f-]{36})".*/\1/')
echo "Beta admin userId: $BETA_USER_ID"
CROSS=$(curl -sS -o /tmp/cross.json -w "%{http_code}" -X POST "$BASE/api/users/$BETA_USER_ID/reset-password" \
  -H "Authorization: Bearer $ACME_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}')
echo "HTTP $CROSS (expect 403)"; cat /tmp/cross.json; echo

step "9. AGENT tries to reset another user's password -> 403"
AGENT_TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"agent1@acme.com","password":"agent12345"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
AGENT_DENY=$(curl -sS -o /tmp/deny.json -w "%{http_code}" -X POST "$BASE/api/users/$ACME_USER_ID/reset-password" \
  -H "Authorization: Bearer $AGENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}')
echo "HTTP $AGENT_DENY (expect 403)"; cat /tmp/deny.json; echo

echo
echo "================ DONE ================"
