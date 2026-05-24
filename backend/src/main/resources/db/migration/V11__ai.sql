-- Phase 16: chatbot replies.

-- 1. Conversation status enum gains BOT_ACTIVE + HUMAN_ACTIVE. We must drop the existing
--    CHECK before re-labelling rows, otherwise the UPDATE violates it.
ALTER TABLE conversations DROP CONSTRAINT conversations_status_check;

UPDATE conversations SET status = 'BOT_ACTIVE' WHERE status = 'OPEN';

ALTER TABLE conversations ADD CONSTRAINT conversations_status_check
  CHECK (status IN ('BOT_ACTIVE','HUMAN_ACTIVE','CLOSED'));

ALTER TABLE conversations ALTER COLUMN status SET DEFAULT 'BOT_ACTIVE';

-- 2. Tenant gets the chatbot's system prompt + a free-form chat_config jsonb (handoff
--    threshold, knobs we'll add later without further migrations).
ALTER TABLE tenants
  ADD COLUMN ai_system_prompt text,
  ADD COLUMN chat_config jsonb NOT NULL DEFAULT '{}';
