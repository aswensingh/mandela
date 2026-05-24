-- Post-MVP: allow tenants.status = 'DELETED' (soft-delete state). Hard purge then removes
-- the row entirely; this status is the intermediate "hidden from UI but recoverable" state.
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_status_check;
ALTER TABLE tenants ADD CONSTRAINT tenants_status_check
  CHECK (status IN ('ACTIVE','SUSPENDED','DELETED'));
