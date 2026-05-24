CREATE TABLE messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  customer_id uuid REFERENCES customers(id),
  direction text NOT NULL CHECK (direction IN ('OUT','IN')),
  sender_type text NOT NULL CHECK (sender_type IN ('SYSTEM','AGENT','BOT','CUSTOMER')),
  body text NOT NULL,
  whatsapp_message_id text UNIQUE,
  status text NOT NULL CHECK (status IN ('QUEUED','SENT','DELIVERED','READ','FAILED')),
  error_message text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_tenant ON messages(tenant_id);
CREATE INDEX idx_messages_customer ON messages(customer_id);
CREATE INDEX idx_messages_tenant_created ON messages(tenant_id, created_at DESC);
