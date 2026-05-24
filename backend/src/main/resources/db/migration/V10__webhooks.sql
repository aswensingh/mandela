CREATE TABLE conversations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  customer_id uuid NOT NULL REFERENCES customers(id),
  status text NOT NULL DEFAULT 'OPEN'
    CHECK (status IN ('OPEN','CLOSED')),
  assigned_agent_id uuid REFERENCES users(id),
  last_message_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, customer_id)
);
CREATE INDEX idx_conversations_tenant_last_message ON conversations(tenant_id, last_message_at DESC);
CREATE INDEX idx_conversations_assigned_agent ON conversations(assigned_agent_id);

CREATE TABLE webhook_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  whatsapp_message_id text NOT NULL UNIQUE,
  processed_at timestamptz NOT NULL DEFAULT now()
);
