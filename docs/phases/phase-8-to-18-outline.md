# Phase 8 — CSV Import

**Goal:** Tenant Admin can upload a CSV file of customers and have them imported in the background.

**Prerequisite:** Phase 7 verified.

---

## Scope

- `csv_import_jobs` table: id, tenant_id, status (`PENDING|PROCESSING|COMPLETED|FAILED`), total_rows, processed_rows, failed_rows, error_log_jsonb, file_name, created_by_user_id, created_at, completed_at
- `POST /api/customers/import` (multipart) — saves file metadata, returns 202 with job id
- For Phase 8 we'll keep it **simple synchronous-in-a-background-Thread** rather than introducing RabbitMQ. Use Spring's `@Async` with a dedicated `ThreadPoolTaskExecutor`. RabbitMQ comes when blast scale demands it (Phase 13).
- `GET /api/customers/import/{jobId}` — returns job status + progress
- React: drag-and-drop CSV upload, polling progress bar

### Format

CSV columns: `phone_e164,full_name,tags,opt_in_status` (tags semicolon-separated). Header required.

### Idempotency
- Upsert by `(tenant_id, phone_e164)` — if customer exists, update `full_name` / `tags` / `opt_in_status` rather than insert. Document this behavior in `decisions.md`.

---

## Verification

- Import a CSV of 1000 rows. Job completes, all 1000 customers visible. Re-import same CSV → no duplicates, all rows "processed" but no new inserts (updates only).
- Import a CSV with 5 invalid phone rows → job status COMPLETED with `failed_rows=5`, error log details each.

---

# Phase 9 — WhatsApp Configuration per Tenant

**Goal:** Each tenant can save their Meta Cloud API credentials (phone number ID + access token), and the system encrypts the token at rest.

**Prerequisite:** Phase 8 verified.

---

## Scope

- `tenants` gains: `whatsapp_phone_number_id text`, `whatsapp_access_token_encrypted bytea`
- AES-GCM encryption helper class, master key from env `ENCRYPTION_MASTER_KEY` (32 bytes base64)
- `GET /api/tenants/me/whatsapp` — returns config status (does NOT echo the token)
- `PUT /api/tenants/me/whatsapp` — sets phone_number_id + access_token, encrypts before storing
- React `WhatsAppSettingsPage` — form, password-style input for token, never shows the token after save (only shows "Configured" + last 4 chars)

---

## Verification

- Save WhatsApp credentials as tenant admin
- Read back via GET → shows configured: true, but no token
- Inspect DB → `whatsapp_access_token_encrypted` is a binary blob, not the plaintext token
- Try to call /api/tenants/me/whatsapp without tenant admin role → 403

---

# Phase 10 — WhatsApp Send (Single Message)

**Goal:** From the UI, send a single WhatsApp text message to a phone number using the tenant's Meta Cloud API credentials.

**Prerequisite:** Phase 9 verified.

---

## Scope

- `messages` table: id, tenant_id, customer_id (nullable for test sends), direction (`OUT|IN`), sender_type (`SYSTEM|AGENT|BOT|CUSTOMER`), body, whatsapp_message_id (unique), status (`QUEUED|SENT|DELIVERED|READ|FAILED`), error_message, created_at, updated_at
- `WhatsAppClient` Spring service wrapping Meta Cloud API:
  - Method `sendText(tenantId, toE164, body)`
  - Loads encrypted token from tenant, decrypts, calls Meta API with `RestClient`
  - Stores `messages` row, sets `whatsapp_message_id` from Meta response
- `POST /api/whatsapp/send-test` — body `{toE164, body}` — Tenant Admin only, sends a test message, no Customer record needed
- React: "Send Test Message" form on the WhatsApp Settings page

---

## Verification

- Configure your real WhatsApp Business test number in Meta dashboard, paste credentials, send a real message to your own phone, receive it
- DB shows messages row with `status=SENT` and a real `whatsapp_message_id`

**This is the first phase that requires real Meta API credentials. Without them, the verification is incomplete. Set them up before starting Phase 10.**

---

# Phase 11 — Message Templates

**Goal:** Tenant Admin can store reusable message templates that reference Meta-approved WhatsApp templates.

**Prerequisite:** Phase 10 verified.

---

## Scope

- `message_templates` table: id, tenant_id, name (internal label), whatsapp_template_name (the Meta-approved name), language, body_preview, status, created_at
- Note: actual templates are approved on the Meta side. Our DB just stores metadata so users can pick from a list.
- CRUD endpoints
- React `TemplatesPage`

---

## Verification

- Pre-approve a template on Meta dashboard, add it in MarketingHub, see it in the list

---

# Phase 12 — Campaigns Entity

**Goal:** A campaign = a template + a recipient list + a schedule. CRUD only, no sending yet.

**Prerequisite:** Phase 11 verified.

---

## Scope

- `campaigns` table: id, tenant_id, name, status (`DRAFT|SCHEDULED|SENDING|SENT|FAILED|CANCELLED`), template_id, scheduled_at, created_by_user_id, started_at, completed_at, created_at, updated_at
- `campaign_recipients` table: id, campaign_id, customer_id, status (`PENDING|SENT|DELIVERED|READ|FAILED`), error_message, sent_at — populated from the customers selected at campaign creation
- Endpoints to create a campaign with a list of customer IDs (in Phase 12, no segments — just pass an array of IDs)
- React `CampaignsPage` and create wizard (pick template, pick customers via the customers table with checkboxes, schedule)

---

## Verification

- Create a campaign with 3 customers, see it in DRAFT status with 3 recipients
- Don't launch yet — that's Phase 13

---

# Phase 13 — Blast Worker

**Goal:** Actually send a campaign to all its recipients, asynchronously, with rate limiting.

**Prerequisite:** Phase 12 verified.

---

## Scope

- Add RabbitMQ to docker-compose (`rabbitmq:3.13-management-alpine`)
- Add Redis (`redis:7-alpine`) for rate limiting state
- Backend: `spring-boot-starter-amqp`, `spring-boot-starter-data-redis`, `bucket4j-redis`
- `POST /api/campaigns/{id}/launch` — transitions to `SENDING`, fans out one RabbitMQ message per recipient
- `CampaignSendWorker` (`@RabbitListener`) — for each message: acquire token from Redis rate limiter (default 80/sec per tenant), call WhatsAppClient, update recipient status
- On Meta 429 (rate limit) → nack + requeue with delay
- React: campaign detail page shows live progress (poll every 2 seconds)

---

## Verification

- Launch a 100-customer test campaign to test numbers (Meta gives 5 free test numbers)
- All recipients reach SENT
- Time check: 100 messages at 80/sec ≥ 1.25 sec; observed time > that
- Rate limit honored — no Meta 429 errors

---

# Phase 14 — Inbound Webhooks

**Goal:** Receive incoming WhatsApp messages from customers and store them.

**Prerequisite:** Phase 13 verified.

---

## Scope

- `conversations` table: id, tenant_id, customer_id, status (`OPEN|CLOSED`), assigned_agent_id (nullable), last_message_at, created_at
- `webhook_events` table for dedup: id, whatsapp_message_id, processed_at — unique on whatsapp_message_id
- `GET /api/webhooks/whatsapp` — Meta's verification handshake (returns `hub.challenge` if `hub.verify_token` matches env var)
- `POST /api/webhooks/whatsapp` — receives events:
  - Verify `X-Hub-Signature-256` HMAC (constant-time comparison)
  - Dedup by `whatsapp_message_id`
  - On inbound message: find/create conversation, insert message row with direction=IN, sender_type=CUSTOMER
  - On delivery status update: update existing messages row's status

---

## Verification

- Configure Meta webhook to point to your machine via ngrok (or similar tunnel)
- Send a WhatsApp message from your phone to the test number
- See conversation appear in DB, message stored, signature verified
- Send same webhook twice (via curl replay) → only one DB row (idempotency works)

---

# Phase 15 — Conversations UI

**Goal:** A two-pane chat interface so agents can see incoming conversations.

**Prerequisite:** Phase 14 verified.

---

## Scope

- Endpoints: `GET /api/conversations`, `GET /api/conversations/{id}/messages`
- React `ConversationsPage`:
  - Left pane: list of conversations (most recent first), filter tabs (All, Mine, Open, Closed)
  - Right pane: message thread, message bubbles, customer info header
- Polling every 5 seconds for new messages (WebSockets can come later)

---

## Verification

- Send a message via WhatsApp → appears in the conversations list within 5 seconds
- Click conversation → see message thread

---

# Phase 16 — AI Chatbot Replies

**Goal:** When a customer sends a message, the bot replies automatically using OpenAI.

**Prerequisite:** Phase 15 verified.

---

## Scope

- Add Spring AI: `spring-ai-starter-model-openai`
- `tenants` gains `ai_system_prompt text` and `chat_config jsonb` (handoff rules, knobs)
- `conversations` status enum gets new values: `BOT_ACTIVE`, `HUMAN_ACTIVE`, `CLOSED`
- New default for new conversations: `BOT_ACTIVE`
- Inbound webhook: if conversation is `BOT_ACTIVE`, publish a job to `ai.reply` queue
- `AIReplyWorker`:
  - Receive `{tenantId, conversationId}` — load tenant's system prompt, last 10 messages, customer info
  - Call OpenAI chat (`gpt-4o-mini` default, configurable)
  - Structured output schema: `{reply, confidence (0-1), request_handoff (bool)}`
  - If handoff requested or low confidence → transition to `HUMAN_ACTIVE`, don't reply
  - Otherwise → send reply via `WhatsAppClient`, store as message with `sender_type=BOT`
- React: in `ConversationsPage`, show conversation status badge (Bot / Human / Closed)
- Tenant Admin can edit `ai_system_prompt` in WhatsApp/Chatbot settings page

---

## Verification

- Send a real WhatsApp message to the test number
- Bot replies coherently within seconds, reply visible in the conversations UI
- Send "I want to talk to a human" → conversation flips to `HUMAN_ACTIVE`, no further bot reply

---

# Phase 17 — Knowledge Base + RAG

**Goal:** Tenants upload promo documents and FAQs. The bot uses them when answering.

**Prerequisite:** Phase 16 verified.

---

## Scope

- `knowledge_documents` table: id, tenant_id, title, file_name, status, created_by_user_id, created_at
- Spring AI: `spring-ai-starter-vector-store-pgvector`, `spring-ai-tika-document-reader`
- Upload PDF/TXT/DOCX → extract text → split into chunks → embed (OpenAI `text-embedding-3-small`) → store in `vector_store` table with metadata `{tenant_id, document_id}`
- `AIReplyWorker` updated: use Spring AI's `QuestionAnswerAdvisor` with a metadata filter `tenant_id == :ctx`
- React: `KnowledgeBasePage` — upload form, list of docs with status

---

## Verification

- Upload a FAQ doc for the tenant (e.g., "Our clinic offers teeth whitening at $150")
- Send a relevant question via WhatsApp ("how much for whitening?")
- Bot's reply includes the figure from the doc
- Upload a different doc to a second tenant — that tenant's bot can't see the first tenant's doc (RAG isolation)

---

# Phase 18 — Agent Takeover

**Goal:** An agent can click "Take Over" on a conversation. Bot stops replying. Agent types replies and they go out via WhatsApp.

**Prerequisite:** Phase 17 verified.

---

## Scope

- `POST /api/conversations/{id}/take-over` — sets status=`HUMAN_ACTIVE`, sets `assigned_agent_id` = current user
- `POST /api/conversations/{id}/release` — back to `BOT_ACTIVE`, clears `assigned_agent_id`
- `POST /api/conversations/{id}/messages` — agent sends a reply (calls `WhatsAppClient.sendText`)
- React: "Take Over" button in conversation header; when taken over, message input becomes enabled

---

## Verification

- Send inbound message → bot replies
- Click "Take Over" → status changes, message input enabled
- Send another inbound message → bot does NOT reply (still HUMAN_ACTIVE)
- Type a reply as agent → goes to customer via WhatsApp, stored in DB with `sender_type=AGENT`
- Click "Release" → back to bot mode

---

## At this point, the MVP is functionally complete.

What comes after (not in this plan, write more phase docs later):
- Audit logs
- Per-tenant rate limit overrides
- Observability (Prometheus, structured logs, traceId propagation)
- Production deployment (Caddy + TLS + backups on a VPS)
- e2e tests with Playwright
- GitHub Actions CI

**These are post-MVP — get the functional app working first, then harden.**
