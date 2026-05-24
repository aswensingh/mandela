# Phase 5 — Tenant Management UI

**Goal:** Platform Admin can create tenants and view a list of tenants in the React UI.

**Prerequisite:** Phase 4 verified.

---

## Scope

In:
- Backend: `POST /api/platform/tenants` (creates tenant + initial Tenant Admin in one transaction), `GET /api/platform/tenants` (paginated list), `PATCH /api/platform/tenants/{id}` (rename, change industry), `POST /api/platform/tenants/{id}/suspend` and `/unsuspend`
- All `/api/platform/**` endpoints require `role == PLATFORM_ADMIN` (return 403 otherwise)
- Frontend: `TenantsPage` with AntD `Table` and "Create Tenant" modal

Out:
- Tenant Admin editing their own tenant (could be Phase 6 or later, keep it out of here)
- Deleting tenants (soft-delete only — `SUSPENDED` status is the only "off" state)

---

## Deliverables

### Backend

- Package `com.marketinghub.tenant`:
  - `TenantService.createTenant(CreateTenantRequest)` — in one transaction:
    1. Insert into `tenants`
    2. Insert initial `TENANT_ADMIN` user (email + bcrypt(password) from request)
    3. Return `TenantDto` with the new tenant + initial admin info
  - `TenantService.listTenants(Pageable)` → page of TenantDto
  - `TenantService.suspend(id)` / `unsuspend(id)` — toggle status
  - `TenantService.update(id, UpdateTenantRequest)` — change name/industry
- `TenantController` at `/api/platform/tenants`
- `CreateTenantRequest`: `name`, `industry`, `initialAdminEmail`, `initialAdminPassword`
- DTOs do NOT leak password hashes
- Authorization: `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` on controller methods (configure `SecurityFilterChain` to use authorities `ROLE_PLATFORM_ADMIN` etc.; set them in JWT filter)

### Frontend

- `src/features/tenants/tenantApi.ts`:
  - `listTenants`, `createTenant`, `updateTenant`, `suspendTenant`, `unsuspendTenant`
  - Tag `'Tenant'`, mutations invalidate
- `src/features/tenants/TenantsPage.tsx`:
  - AntD `Table` columns: Name, Industry, Status (Tag colored by status), Created At, Actions
  - "Create Tenant" button opens a `Modal` with a `Form`:
    - name (required), industry (optional), initialAdminEmail (required + email), initialAdminPassword (required + min 8)
  - On submit, call `useCreateTenantMutation`, show success message, close modal, table auto-refreshes via tag invalidation
- Routing:
  - `/app/tenants` → `TenantsPage`, wrapped in a new `<RoleRoute roles={['PLATFORM_ADMIN']}>` guard
  - `AppLayout` Sider menu shows "Tenants" only if user is `PLATFORM_ADMIN`

---

## Hard rules

- Only Platform Admins can access these endpoints. Verified by integration test.
- Tenant creation is atomic — if the initial admin insert fails, the tenant insert rolls back.
- Initial admin password is never echoed back in the response.

---

## Verification gate

```bash
# Stack already up from previous phases

# 1. Log in as platform admin
ACCESS=$(curl -sf -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@marketinghub.local","password":"change-me-locally"}' | jq -r .accessToken)

# 2. Create a tenant
curl -sf -X POST http://localhost:8080/api/platform/tenants \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{"name":"Acme Dental","industry":"dental","initialAdminEmail":"acme@acme.com","initialAdminPassword":"acme12345"}'

# 3. List tenants
curl -sf -H "Authorization: Bearer $ACCESS" http://localhost:8080/api/platform/tenants | jq

# 4. Log in as the new tenant admin
curl -sf -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"acme@acme.com","password":"acme12345"}'

# 5. Try to access /api/platform/tenants as tenant admin → 403
NON_PLATFORM=$(curl -sf -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"acme@acme.com","password":"acme12345"}' | jq -r .accessToken)
curl -i -H "Authorization: Bearer $NON_PLATFORM" http://localhost:8080/api/platform/tenants | head -1
# Expect 403
```

Manual:
- Log in as platform admin in browser → see "Tenants" in sider → create a tenant → see it in the table
- Log out → log in as the new tenant admin → "Tenants" menu item NOT visible

---

## Output expected

1. Files added
2. Verification outputs (including the 403)
3. "Phase 5 done — ready for Phase 6?"
