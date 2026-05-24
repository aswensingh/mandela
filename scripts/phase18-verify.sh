#!/usr/bin/env bash
# Phase 18 verification — end-to-end Agent Takeover flow against running stack.
# Requires WHATSAPP_MOCK=true and OPENAI_MOCK=true (current .env defaults).
set -euo pipefail

BASE=http://localhost:8080
PHONE_E164='+19998883333'
PHONE_LOCAL='19998883333'
ACME_PHONE_NUMBER_ID='PHN-987654321'
APP_SECRET='local-app-secret-67890'  # matches .env WHATSAPP_APP_SECRET

step() { echo; echo "================ $* ================"; }

login() {
  local email="$1" password="$2"
  curl -fsS -X POST "$BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
    | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'
}

# Send a Meta-shaped inbound webhook with valid HMAC signature. Each call uses a
# fresh whatsapp_message_id so dedup doesn't suppress the second inbound.
send_inbound() {
  local wamid="$1" body="$2"
  local ts
  ts=$(date +%s)
  # Single-line JSON so HMAC matches exactly what we POST.
  local payload
  payload='{"object":"whatsapp_business_account","entry":[{"id":"WABA-1","changes":[{"field":"messages","value":{"messaging_product":"whatsapp","metadata":{"display_phone_number":"+14155551234","phone_number_id":"'"$ACME_PHONE_NUMBER_ID"'"},"contacts":[{"profile":{"name":"Verifier"},"wa_id":"'"$PHONE_LOCAL"'"}],"messages":[{"from":"'"$PHONE_LOCAL"'","id":"'"$wamid"'","timestamp":"'"$ts"'","type":"text","text":{"body":"'"$body"'"}}]}}]}]}'
  local sig
  sig=$(printf '%s' "$payload" | openssl dgst -sha256 -hmac "$APP_SECRET" | awk '{print $NF}')
  curl -fsS -X POST "$BASE/api/webhooks/whatsapp" \
    -H "Content-Type: application/json" \
    -H "X-Hub-Signature-256: sha256=$sig" \
    --data-binary "$payload"
}

step "1. Login as Acme TENANT_ADMIN + as Acme AGENT"
ACME_ADMIN_TOKEN=$(login acme@acme.com acme12345)
ACME_AGENT_TOKEN=$(login agent1@acme.com agent12345)
echo "admin token len=${#ACME_ADMIN_TOKEN}, agent token len=${#ACME_AGENT_TOKEN}"

step "2. Ensure verifier customer exists (ignore duplicate)"
CREATE_RESP=$(curl -sS -X POST "$BASE/api/customers" \
  -H "Authorization: Bearer $ACME_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"phoneE164\":\"$PHONE_E164\",\"fullName\":\"P18 Verifier\"}" 2>&1 || true)
echo "create response: $CREATE_RESP"

step "3. First inbound — customer says 'hi please reply'. Bot SHOULD reply."
WAMID1="wamid.P18-IN-$(date +%s)-1"
send_inbound "$WAMID1" "hi please reply"
echo
echo "Waiting 4s for AI worker to consume RabbitMQ + persist bot reply..."
sleep 4

step "4. Find conversation for this customer"
LIST_BEFORE=$(curl -fsS "$BASE/api/conversations?filter=ALL&page=0&size=200" \
  -H "Authorization: Bearer $ACME_AGENT_TOKEN")
# Look for our phone — emit just the conversation id immediately preceding it
CONV_ID=$(echo "$LIST_BEFORE" \
  | grep -oE '\{"id":"[0-9a-f-]{36}","status":"[A-Z_]+","assignedAgentId":[^,]+,"customerId":"[0-9a-f-]+","customerPhone":"[^"]*"' \
  | grep "\"customerPhone\":\"$PHONE_E164\"" \
  | head -1 \
  | sed -E 's/.*"id":"([0-9a-f-]{36})".*/\1/')
echo "CONV_ID=$CONV_ID"
if [[ -z "$CONV_ID" ]]; then echo "ERROR: could not find conversation for $PHONE_E164"; exit 1; fi

step "5. Conversation + messages BEFORE takeover"
echo "--- conversation ---"
curl -fsS "$BASE/api/conversations/$CONV_ID" -H "Authorization: Bearer $ACME_AGENT_TOKEN"; echo
echo "--- messages ---"
curl -fsS "$BASE/api/conversations/$CONV_ID/messages?size=50" -H "Authorization: Bearer $ACME_AGENT_TOKEN"; echo

step "6. AGENT takes over"
TAKE_RESP=$(curl -fsS -X POST "$BASE/api/conversations/$CONV_ID/take-over" \
  -H "Authorization: Bearer $ACME_AGENT_TOKEN")
echo "$TAKE_RESP"

step "7. Second inbound — customer says 'bot should stay silent'. Bot must NOT reply."
WAMID2="wamid.P18-IN-$(date +%s)-2"
send_inbound "$WAMID2" "bot should stay silent"
echo
echo "Waiting 4s for AI worker (which should detect HUMAN_ACTIVE and skip)..."
sleep 4

step "8. Message log AFTER second inbound — should have +1 CUSTOMER row, NO new BOT row"
curl -fsS "$BASE/api/conversations/$CONV_ID/messages?size=50" -H "Authorization: Bearer $ACME_AGENT_TOKEN"; echo

step "9. AGENT sends manual reply"
SEND_RESP=$(curl -fsS -X POST "$BASE/api/conversations/$CONV_ID/messages" \
  -H "Authorization: Bearer $ACME_AGENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"body":"Thanks for reaching out -- agent here. What can I help with?"}')
echo "$SEND_RESP"

step "10. AGENT releases conversation"
REL_RESP=$(curl -fsS -X POST "$BASE/api/conversations/$CONV_ID/release" \
  -H "Authorization: Bearer $ACME_AGENT_TOKEN")
echo "$REL_RESP"

step "11. Final message log"
curl -fsS "$BASE/api/conversations/$CONV_ID/messages?size=50" -H "Authorization: Bearer $ACME_AGENT_TOKEN"; echo

step "12. Negative tests — error codes"
echo "12a. sendMessage when BOT_ACTIVE should be 409 INVALID_CONVERSATION_STATE:"
curl -sS -o /tmp/p18_neg.json -w "HTTP %{http_code}\n" -X POST "$BASE/api/conversations/$CONV_ID/messages" \
  -H "Authorization: Bearer $ACME_AGENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"body":"should fail — convo is BOT_ACTIVE"}'
cat /tmp/p18_neg.json; echo
echo "12b. takeOver as VIEWER would be 403, but Acme has no VIEWER seeded — skipping."

echo
echo "================ DONE — Phase 18 scenario complete ================"
