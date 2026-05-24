# Phase 7 — Customers

**Goal:** Tenant Admin / Agent can add and manage customers (the recipients of WhatsApp campaigns).

**Prerequisite:** Phase 6 verified.

---

## Scope

In:
- `customers` table: id, tenant_id, phone_e164, full_name, tags text[], opt_in_status (`UNKNOWN|OPTED_IN|OPTED_OUT`), custom_attributes jsonb, created_at, updated_at
- `POST /api/customers`, `GET /api/customers` (paginated, filterable by tag/opt-in), `GET /api/customers/{id}`, `PATCH /api/customers/{id}`, `DELETE /api/customers/{id}` (soft delete or hard — decide in implementation; lean hard delete for now since no compliance needs)
- React `CustomersPage` with table, search, filters, add/edit modal

Out:
- CSV import (Phase 8)
- Segments (deferred — could be Phase 9 or later)

---

## Deliverables outline

### Flyway `V4__customers.sql`

```sql
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
```

### Backend
- `Customer` entity, `CustomerRepository`, `CustomerService`, `CustomerController`
- Validation: `phone_e164` matches `^\+[1-9]\d{1,14}$` (E.164 format — strict, reject anything else)
- Service queries always filter by `TenantContext.getTenantId()`

### Frontend
- `customerApi.ts` (RTK Query)
- `CustomersPage.tsx`: AntD `Table` with search input, tag filter, opt-in filter; "Add Customer" modal

---

## Verification gate

- Create 5 customers as Acme tenant admin
- List them — see exactly those 5
- Log in as Beta tenant admin (from Phase 6) — list customers — see 0 (isolation works)
- Try to create a customer with invalid phone (`123`) — 400
- Try to create duplicate phone in same tenant — 409 or 400 with clear message

---

## Output expected

1. Files added
2. Verification outputs
3. "Phase 7 done — ready for Phase 8?"
