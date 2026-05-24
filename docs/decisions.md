# Architectural Decision Records

Living document. New decisions are appended; accepted ones are not edited (mark as `Superseded by ADR-NNN` if needed).

---

## ADR-001 — REST over GraphQL

**Status:** Accepted (2026-05)

**Context:** Single SPA frontend, CRUD-heavy backend, small team (one person + Claude Code).

**Decision:** REST + OpenAPI. No GraphQL.

**Consequences:**
- (+) Simpler debugging, standard HTTP caching, AntD Table maps cleanly to REST.
- (+) RTK Query's `fetchBaseQuery` is built for REST.
- (–) Multiple consumers with very different shapes would need an extension layer later; acceptable trade-off.

---

## ADR-002 — Spring Boot 3.5 (not 4.x)

**Status:** Accepted (2026-05)

**Context:** Spring Boot 4 GA in Nov 2025. Spring Boot 3.5 still actively maintained, far more examples in the wild.

**Decision:** Use Spring Boot 3.5.x on Java 25 LTS.

**Consequences:**
- (+) Mature ecosystem, more Stack Overflow / GitHub examples Claude Code can lean on.
- (+) Still gets Java 25 features (virtual threads, pattern matching).
- (–) Spring Boot 4 has some niceties we forgo. Acceptable; can migrate later.

---

## ADR-003 — Ant Design 5 (not 6)

**Status:** Accepted (2026-05)

**Context:** AntD 6 is current major. AntD 5 has the wider ecosystem and more component tutorials.

**Decision:** Use AntD 5.x on React 18.3 (NOT React 19 — keep AntD 5's React 18 compatibility happy).

**Consequences:**
- (+) Solid ecosystem.
- (+) Compatible with React 18 — no migration risk.
- (–) Forgoes AntD 6's theming improvements. Acceptable.

---

## ADR-004 — Redux Toolkit + RTK Query

**Status:** Accepted (2026-05)

**Context:** Need state management for the React app. User asked for Redux specifically.

**Decision:** Redux Toolkit 2.x for client state. RTK Query for ALL server state. No redux-saga, no TanStack Query, no Zustand. `redux-persist` whitelists `auth` only.

**Consequences:**
- (+) One mental model, one DevTools panel.
- (+) Auto-caching, tag-based invalidation, built-in mutex via async-mutex wrapper for refresh.
- (–) Learning curve, but worth it.

---

## ADR-005 — Meta Cloud API for WhatsApp

**Status:** Accepted (2026-05)

**Context:** Three WhatsApp options: Business app (manual), Cloud API (Meta-hosted), On-Premise/BSP (third-party hosted).

**Decision:** Meta Cloud API. Webhook-based, REST.

**Consequences:**
- (+) Free tier, official, simplest setup.
- (+) Most documentation and library support in 2026.
- (–) Volume limits at lower tiers, but plenty for this use case.

---

## ADR-006 — OpenAI for chat + embeddings (Claude option later)

**Status:** Accepted (2026-05)

**Context:** Anthropic doesn't offer embeddings, so a Claude-only stack needs a second vendor anyway. OpenAI provides both.

**Decision:** OpenAI for v1 — `gpt-4o-mini` for chat, `text-embedding-3-small` for embeddings. Use Spring AI's abstractions so we can swap to Claude later.

**Consequences:**
- (+) One vendor, one API key, one bill.
- (+) Spring AI handles vector store + RAG plumbing.
- (–) Locked to OpenAI quality and pricing for now. Migration path documented in Phase 16 notes.

---

## ADR-007 — Application-layer tenant filtering (not Postgres RLS)

**Status:** Accepted (2026-05)

**Context:** Two options for tenant isolation: (a) every query manually filters by tenant_id, (b) PostgreSQL Row-Level Security policies. RLS is stronger but adds complexity (separate DB role, aspect to set session var, harder to debug).

**Decision:** Application-layer filtering for v1 — services read `TenantContext.getTenantId()` and include it in queries. Document as a known limitation. Revisit RLS in a hardening phase.

**Consequences:**
- (+) Simpler to set up and debug.
- (+) Easier for Claude Code to generate correctly.
- (–) Bugs in queries can leak data across tenants. Mitigated by code review + integration tests asserting isolation per phase.
- This is a deliberate trade-off; if the app reaches production scale, ADR-NNN will supersede this by adopting RLS.

---

## ADR-008 — Docker Compose throughout (no Kubernetes)

**Status:** Accepted (2026-05)

**Context:** Last build attempt failed; one cause was over-engineering with K8s. Expected scale is "thousands of messages/day per tenant" — well within single-VPS Docker Compose territory.

**Decision:** Docker Compose for local development AND production (when we get there). No K8s.

**Consequences:**
- (+) Simpler, cheaper.
- (–) Single point of failure on the production VPS — mitigated by backups + a "spin up replacement" runbook (post-MVP work).

---

## ADR-009 — 18 small phases over 8 big phases

**Status:** Accepted (2026-05)

**Context:** Previous attempt with 8 phases failed. Each phase was too large for Claude Code to scaffold correctly.

**Decision:** Break into 18 small phases, each verifiable in isolation. Better to do 18 small things that all work than 8 big things that each fail.

**Consequences:**
- (+) Easier to debug failures.
- (+) Each phase produces something working.
- (–) More repetitive setup. Worth it.

---

## ADR-010 — CSV import is upsert-on-(tenant_id, phone_e164)

**Status:** Accepted (2026-05, Phase 8)

**Context:** When a tenant re-uploads the same customer CSV (refreshed names, new tags), we don't want duplicate rows — the DB has `UNIQUE (tenant_id, phone_e164)` anyway, so a straight INSERT would 409 on every existing phone. Two options: (a) insert-only and report dupes as failed rows, (b) upsert: existing rows get their `full_name`/`tags`/`opt_in_status` overwritten by the CSV.

**Decision:** Upsert. A re-uploaded row updates the existing customer rather than failing. Inserts and updates both count toward `processed_rows`; only true validation failures (bad phone, bad opt-in value) count toward `failed_rows`.

**Consequences:**
- (+) Re-running an import after fixing a typo doesn't require manual cleanup.
- (+) Marketing teams can treat the CSV as the source of truth for the columns it carries.
- (–) The CSV silently overwrites edits made through the UI — if an agent adjusted tags in the app, the next CSV import will clobber them. Document this in the UI when CSV import lands in the user-facing changelog.
- (–) No "dry run" mode in v1. Add later if the clobber risk bites someone.
