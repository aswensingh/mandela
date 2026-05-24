# Phase 4 — Multi-Tenancy

**Goal:** Introduce tenants. Every user belongs to a tenant (except Platform Admins). JWT carries `tenant_id`. Tenant-scoped tables enforce isolation.

**Prerequisite:** Phase 3 verified.

---

## Scope

In:
- `tenants` table
- `users` gets `tenant_id` column + `role` column with values `PLATFORM_ADMIN | TENANT_ADMIN | AGENT | VIEWER`
- JWT claims include `tenantId` and `role`
- A `TenantContext` ThreadLocal + filter
- A `@TenantScoped` query helper (services that query tenant data filter by `TenantContext.getTenantId()`)
- Platform Admin user seeded at startup from env vars
- API endpoint changes: register endpoint REMOVED (Platform Admin creates tenants and tenant admins; tenant admins create their own users — that's Phases 5 and 6)

Out:
- Tenant CRUD UI (Phase 5)
- User CRUD within a tenant (Phase 6)
- Postgres Row-Level Security (decision: skip for now to keep complexity down — application-layer filtering enforced through a shared base repository method or a Hibernate filter. Document the trade-off in `decisions.md`.)

---

## Deliverables

### Flyway migration `V3__tenancy.sql`

```sql
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

DROP INDEX IF EXISTS users_email_key;
CREATE UNIQUE INDEX users_tenant_email_uniq ON users (tenant_id, email) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX users_platform_email_uniq ON users (email) WHERE tenant_id IS NULL;
```

### Backend changes

- New package `com.marketinghub.tenant` with `Tenant` entity, `TenantRepository`
- `User` entity gains `tenantId`, `role` fields
- `JwtService` issues access tokens with claims: `sub`, `email`, `tenantId` (UUID string or null), `role`
- `JwtAuthenticationFilter` populates `AuthenticatedPrincipal(userId, email, tenantId, role)` and pushes `tenantId` into a new `TenantContext` ThreadLocal
- `TenantContextFilter` runs after the JWT filter, sets and clears the context
- `AuthService.login` updates user lookup; password check still by email; existing accounts (test@example.com from Phase 3) need wiping — Phase 4 is an opportunity to reset
- `AuthService.register` REMOVED from the public API (users are created by admins in Phases 5/6)
- `ApplicationRunner` `PlatformAdminBootstrap`: at startup, if no PLATFORM_ADMIN user exists, create one using `PLATFORM_ADMIN_EMAIL` + `PLATFORM_ADMIN_PASSWORD` from env (log a warning if env missing)

### `.env.example` — add

```
PLATFORM_ADMIN_EMAIL=admin@marketinghub.local
PLATFORM_ADMIN_PASSWORD=change-me-locally
```

### `application.yml`

```yaml
platform-admin:
  email: ${PLATFORM_ADMIN_EMAIL}
  password: ${PLATFORM_ADMIN_PASSWORD}
```

### Frontend

- `User` type updated: add `tenantId`, `role`
- `authSlice` stores role
- Remove `/register` page (and route)
- Login page only

(UI for managing tenants/users comes in Phases 5 and 6.)

---

## Hard rules

- A `PLATFORM_ADMIN` has `tenant_id = NULL`. Enforced by DB check constraint.
- Every other role has a non-null `tenant_id`.
- `TenantContext` is set per request and cleared in a finally block.
- For now, services that query tenant-scoped tables must include `tenant_id = :ctx` in their queries. We'll harden this later (Phase 6 introduces a base repository pattern; Phase 7+ can revisit Postgres RLS).

---

## Verification gate

```bash
# 1. Reset DB (we changed users schema)
docker compose down -v
docker compose up -d --build
sleep 60

# 2. Log in as platform admin (created by bootstrap)
curl -sf -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@marketinghub.local","password":"change-me-locally"}'
# Expect accessToken whose payload contains role:"PLATFORM_ADMIN", tenantId:null

# 3. Decode JWT payload to verify claims
# (paste accessToken at jwt.io, or use: echo $ACCESS | cut -d. -f2 | base64 -d | jq)

# 4. /api/auth/me works
curl -sf -H "Authorization: Bearer $ACCESS" http://localhost:8080/api/auth/me
# Expect user with role PLATFORM_ADMIN, tenantId null

# 5. /register endpoint should no longer exist
curl -i -X POST http://localhost:8080/api/auth/register -d '{}' | head -1
# Expect 404 or 405
```

---

## Output expected

1. Files modified
2. Verification outputs (especially: JWT contains `role` and `tenantId` claims)
3. "Phase 4 done — ready for Phase 5?"
