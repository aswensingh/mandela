CREATE TABLE customers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  phone_e164 text NOT NULL,
  full_name text,
  tags text[] NOT NULL DEFAULT '{}',
  opt_in_status text NOT NULL DEFAULT 'UNKNOWN' CHECK (opt_in_status IN ('UNKNOWN','OPTED_IN','OPTED_OUT')),
  custom_attributes jsonb NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, phone_e164)
);
CREATE INDEX idx_customers_tenant ON customers(tenant_id);
CREATE INDEX idx_customers_tags ON customers USING GIN(tags);
