-- Per-conversation "reset bot memory" timestamp. The AI worker only feeds messages
-- with created_at > bot_context_reset_at into the LLM, so an admin can wipe the
-- bot's running context (e.g. after the bot got stuck on a wrong assumption)
-- without deleting the visible message history in the UI.
ALTER TABLE conversations ADD COLUMN bot_context_reset_at timestamptz;
