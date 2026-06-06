-- Debug/observability: record WHY a conversation is in human mode and at what AI
-- confidence, so an auto-handoff can be explained in the UI header. Set by the AI worker
-- on auto-handoff (MODEL_REQUESTED / LOW_CONFIDENCE), and on manual takeover
-- (MANUAL_TAKEOVER); cleared when released back to the bot. Nullable.
ALTER TABLE conversations ADD COLUMN handoff_reason text;
ALTER TABLE conversations ADD COLUMN handoff_confidence double precision;
