CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_info (
  key text PRIMARY KEY,
  value text NOT NULL
);

INSERT INTO app_info (key, value) VALUES ('schema_version', '1');
