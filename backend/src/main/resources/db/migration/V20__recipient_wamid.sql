-- Link each campaign recipient to the WhatsApp message id (wamid) we got from Meta on send,
-- so Meta's asynchronous delivery-status webhooks (sent/delivered/read/FAILED) can update the
-- recipient's status + capture the failure reason — previously a delivery failure only updated
-- the messages row, leaving the recipient stuck on a misleading SENT.
ALTER TABLE campaign_recipients ADD COLUMN whatsapp_message_id text;
CREATE INDEX idx_campaign_recipients_wamid ON campaign_recipients(whatsapp_message_id);
