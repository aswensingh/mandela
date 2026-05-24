# Phase 6 — User Management Within a Tenant

**Goal:** Tenant Admin can create and manage users within their own tenant. Roles: `TENANT_ADMIN`, `AGENT`, `VIEWER`.

**Prerequisite:** Phase 5 verified.

---

## Scope

In:
- `GET /api/users` — list users in current tenant (Tenant Admin sees all; Agent/Viewer only see themselves)
- `POST /api/users` — create a user in the current tenant (Tenant Admin only)
- `PATCH /api/users/{id}` — update fullName, role, status (Tenant Admin only)
- `DELETE /api/users/{id}` — soft delete (set status=DISABLED) (Tenant Admin only)
- `UsersPage` in React with table + create/edit modal

Out:
- Cross-tenant user moves (out of scope)
- Bulk user import

---

## Deliverables

### Backend

- Package `com.marketinghub.user`:
  - `UserService` methods using `TenantContext` to scope queries
  - `listUsers(Pageable)` queries with `WHERE tenant_id = :ctx`
  - `createUser(CreateUserRequest)` enforces:
    - Role can only be `TENANT_ADMIN`, `AGENT`, or `VIEWER` (never `PLATFORM_ADMIN`)
    - Email must be unique within the tenant
    - Sets `tenant_id = TenantContext.getTenantId()`
  - `updateUser(id, UpdateUserRequest)`:
    - Verify the user being updated belongs to the same tenant (else 404)
    - Self-edit allowed (any user can update their own `fullName`)
    - Role change restricted to `TENANT_ADMIN`
  - `disableUser(id)`: set status=DISABLED, also revoke any active refresh tokens for that user
- `UserController` at `/api/users`
- Authorization via `@PreAuthorize`:
  - Create/delete: `hasRole('TENANT_ADMIN')`
  - List: any authenticated user (filtering applied in service based on role)
  - Update: any authenticated user (service enforces fine-grained rules)

### Frontend

- `src/features/users/userApi.ts`: `listUsers`, `createUser`, `updateUser`, `disableUser`. Tag `'User'`
- `src/features/users/UsersPage.tsx`:
  - AntD `Table`: columns Email, Full Name, Role (Tag), Status (Tag), Last Login, Actions
  - "Add User" button (visible only to Tenant Admin) → Modal with Form (email, fullName, password, role select)
  - Row actions: Edit (modal), Disable (with confirm)
- Routing: `/app/users` for `TENANT_ADMIN`, `AGENT`, `VIEWER`. Agents/Viewers see only themselves.

---

## Hard rules

- Tenant Admin can never create or escalate to `PLATFORM_ADMIN`. Enforced server-side.
- Disabling a user revokes their refresh tokens.
- All user queries filter by `TenantContext.getTenantId()`. No exceptions.

---

## Verification gate

```bash
# 1. Log in as tenant admin (from Phase 5)
ACCESS=$(curl -sf -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"acme@acme.com","password":"acme12345"}' | jq -r .accessToken)

# 2. Create an agent
curl -sf -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{"email":"agent1@acme.com","password":"agent12345","fullName":"Agent One","role":"AGENT"}'

# 3. List users — should see 2 (tenant admin + agent)
curl -sf -H "Authorization: Bearer $ACCESS" http://localhost:8080/api/users | jq

# 4. Try to create a PLATFORM_ADMIN → 400 or 403
curl -i -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{"email":"hack@acme.com","password":"hackme123","fullName":"Hack","role":"PLATFORM_ADMIN"}' | head -1

# 5. Tenant isolation: create another tenant via platform admin, then verify the first tenant admin can't see its users
PLATFORM=$(curl -sf -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@marketinghub.local","password":"change-me-locally"}' | jq -r .accessToken)
curl -sf -X POST http://localhost:8080/api/platform/tenants \
  -H "Authorization: Bearer $PLATFORM" -H 'Content-Type: application/json' \
  -d '{"name":"Beta Resto","industry":"restaurant","initialAdminEmail":"beta@beta.com","initialAdminPassword":"beta12345"}'

# Acme tenant admin lists users — should still see only Acme's 2 users
curl -sf -H "Authorization: Bearer $ACCESS" http://localhost:8080/api/users | jq '. | length'
# Expect 2
```

Manual:
- As tenant admin, see Users page, create an agent, see both rows
- Log in as the agent → Users menu visible, but only shows the agent's own row (or hides Users entirely — your call, document in implementation)

---

## Output expected

1. Files added
2. Verification outputs
3. "Phase 6 done — ready for Phase 7?"
