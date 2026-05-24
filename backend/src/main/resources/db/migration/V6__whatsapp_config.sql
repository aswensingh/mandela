ALTER TABLE tenants
  ADD COLUMN whatsapp_phone_number_id text,
  ADD COLUMN whatsapp_access_token_encrypted bytea;
