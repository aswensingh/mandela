# Phase 2 — Database

**Goal:** Add Postgres to Docker Compose, add Spring Data JPA + Flyway to the backend, prove the backend can connect to the DB and run a migration.

**Prerequisite:** Phase 1 verified.

---

## Scope

In:
- Postgres 16 service in docker-compose (with pgvector extension preinstalled — used later, no harm now)
- Spring Boot adds: `spring-boot-starter-data-jpa`, `postgresql` (runtime), `flyway-core`, `flyway-database-postgresql`
- One Flyway migration creating a placeholder `app_info` table with one row
- New endpoint `GET /api/db-info` returns `{"db":"postgres","version":"<from SQL>"}`

Out:
- Any business tables (users, tenants, customers — those come in later phases)
- Spring Security
- Connection pooling tweaks beyond defaults

---

## Deliverables

### `docker-compose.yml` — add postgres service

```yaml
postgres:
  image: pgvector/pgvector:pg16
  environment:
    POSTGRES_USER: ${POSTGRES_USER}
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    POSTGRES_DB: ${POSTGRES_DB}
  ports:
    - "5432:5432"          # expose for local DB tools (Phase 2 only; remove later)
  volumes:
    - postgres_data:/var/lib/postgresql/data
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER}"]
    interval: 5s
    timeout: 5s
    retries: 10
  networks:
    - marketinghub-net
```

Add `volumes: { postgres_data: }` at the file root.

Update `backend` service:
- Add `depends_on: { postgres: { condition: service_healthy } }`
- Add env passthrough for `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

### `.env.example` — add

```
POSTGRES_USER=marketinghub
POSTGRES_PASSWORD=change-me-locally
POSTGRES_DB=marketinghub

SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/marketinghub
SPRING_DATASOURCE_USERNAME=marketinghub
SPRING_DATASOURCE_PASSWORD=change-me-locally
```

### `backend/pom.xml` — add dependencies

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

### `backend/src/main/resources/application.yml` — add

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

### Flyway migration `V1__init.sql`

`backend/src/main/resources/db/migration/V1__init.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_info (
  key text PRIMARY KEY,
  value text NOT NULL
);

INSERT INTO app_info (key, value) VALUES ('schema_version', '1');
```

### New code

`backend/src/main/java/com/marketinghub/health/DbInfoController.java`:
- `@RestController`
- Injects a `JdbcTemplate` (auto-configured by Spring Data JPA)
- `GET /api/db-info` runs `SELECT version()` and returns `{"db":"postgres","version":"<that string>"}`

(No JPA entity yet. Pure JDBC for now.)

---

## Hard rules

- **`hibernate.ddl-auto: validate` always.** Flyway owns the schema.
- **No users/tenants/customers tables yet.** Just `app_info`.
- **No Spring Security yet** — the new endpoint is open.

---

## Verification gate

```bash
# 1. Clean rebuild
docker compose down -v
docker compose up -d --build
sleep 45
docker compose ps           # postgres healthy, backend healthy

# 2. Backend connected and migration ran
docker compose logs backend | grep -i flyway
# expected: "Successfully applied 1 migration to schema 'public'" (or similar)

# 3. DB info endpoint works
curl -sf http://localhost:8080/api/db-info | jq
# expected: version string starting with "PostgreSQL 16"

# 4. The Phase 1 endpoint still works (no regression)
curl -sf http://localhost:8080/api/health

# 5. Frontend still loads
curl -sfI http://localhost:5173 | head -n 1
```

Manual:
- Open http://localhost:5173 — the "Hello MarketingHub" page should still render fine.

---

## Output expected at end of phase

1. Files added/modified
2. Output of every verification command (especially the Flyway log line and the version string)
3. "Phase 2 done — ready for Phase 3?"
