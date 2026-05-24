CREATE TABLE tenants (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  industry text,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE users
  ADD COLUMN tenant_id uuid REFERENCES tenants(id),
  ADD COLUMN role text NOT NULL DEFAULT 'TENANT_ADMIN'
    CHECK (role IN ('PLATFORM_ADMIN','TENANT_ADMIN','AGENT','VIEWER'));

-- A platform admin has tenant_id NULL. Everyone else has a tenant.
ALTER TABLE users
  ADD CONSTRAINT users_platform_admin_no_tenant
    CHECK ((role = 'PLATFORM_ADMIN' AND tenant_id IS NULL) OR (role <> 'PLATFORM_ADMIN' AND tenant_id IS NOT NULL));

-- The original global UNIQUE on email is owned by a constraint, not a bare index — drop the
-- constraint (which also removes its backing index) and replace with two partial uniques.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
CREATE UNIQUE INDEX users_tenant_email_uniq ON users (tenant_id, email) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX users_platform_email_uniq ON users (email) WHERE tenant_id IS NULL;
