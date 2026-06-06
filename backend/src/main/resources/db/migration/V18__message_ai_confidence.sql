-- Dev/debug aid: store the AI bot's self-reported confidence (0..1) for each generated
-- reply so it can be surfaced per-message in the conversation UI. Nullable — only BOT
-- messages carry a value; customer/agent/system rows leave it null. Safe to drop later.
ALTER TABLE messages ADD COLUMN ai_confidence double precision;
