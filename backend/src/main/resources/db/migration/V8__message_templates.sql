CREATE TABLE message_templates (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  name text NOT NULL,
  whatsapp_template_name text NOT NULL,
  language text NOT NULL,
  body_preview text,
  status text NOT NULL DEFAULT 'APPROVED'
    CHECK (status IN ('APPROVED','PENDING','REJECTED','PAUSED','DISABLED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, whatsapp_template_name, language)
);
CREATE INDEX idx_message_templates_tenant ON message_templates(tenant_id);
