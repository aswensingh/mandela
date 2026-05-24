CREATE TABLE csv_import_jobs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  created_by_user_id uuid NOT NULL REFERENCES users(id),
  file_name text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
  total_rows integer NOT NULL DEFAULT 0,
  processed_rows integer NOT NULL DEFAULT 0,
  failed_rows integer NOT NULL DEFAULT 0,
  error_log jsonb NOT NULL DEFAULT '[]',
  created_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);
CREATE INDEX idx_csv_import_jobs_tenant ON csv_import_jobs(tenant_id);
CREATE INDEX idx_csv_import_jobs_status ON csv_import_jobs(status);
