-- Deleting a customer (single or bulk) previously failed with a foreign-key violation
-- whenever the customer had any conversation, message, or campaign-recipient row, since
-- those FKs had no ON DELETE action. A "delete customer" should remove the customer and
-- everything tied to them, so switch all three references to ON DELETE CASCADE.

ALTER TABLE conversations DROP CONSTRAINT conversations_customer_id_fkey;
ALTER TABLE conversations ADD CONSTRAINT conversations_customer_id_fkey
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE;

ALTER TABLE messages DROP CONSTRAINT messages_customer_id_fkey;
ALTER TABLE messages ADD CONSTRAINT messages_customer_id_fkey
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE;

ALTER TABLE campaign_recipients DROP CONSTRAINT campaign_recipients_customer_id_fkey;
ALTER TABLE campaign_recipients ADD CONSTRAINT campaign_recipients_customer_id_fkey
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE;
