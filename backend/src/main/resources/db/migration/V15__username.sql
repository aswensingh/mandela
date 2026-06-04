-- Login identifier switched from email to username. The column keeps its citext type
-- (case-insensitive uniqueness, which suits usernames just as well as emails). Postgres
-- automatically rewrites the dependent partial-unique index definitions on rename; we then
-- rename the indexes themselves so their names stay meaningful.
ALTER TABLE users RENAME COLUMN email TO username;

ALTER INDEX users_tenant_email_uniq RENAME TO users_tenant_username_uniq;
ALTER INDEX users_platform_email_uniq RENAME TO users_platform_username_uniq;
