# AGENTS.md — MarketingHub

> Auto-loaded by Codex (and other AGENTS.md-aware tools) each session. Project conventions; this file overrides training data. (Kept in sync with `CLAUDE.md`.)

## What it is

Multi-tenant web app: marketing agents blast WhatsApp promos to their customers, and an AI chatbot handles the replies (with agent takeover). Each client company is an isolated **tenant**; roles are **Tenant Admin / Agent / Viewer**, with a **Platform Admin** (the owner) above all tenants. Sized for blasts to several thousand customers per tenant.

**Status:** feature-complete — all 18 original phases are built (auth, multi-tenancy, customers, CSV import, WhatsApp config/send, templates, campaigns, blast worker, inbound webhooks, conversations UI, AI replies, knowledge base/RAG, agent takeover). Treat this as a stable app, **not** phase-gated work.

## Tech stack (locked — don't cross these majors)

- **Backend:** Java 25 (Temurin), Spring Boot 3.5.x (NOT 4.x), Maven 3.9
- **DB:** PostgreSQL 16 + pgvector; Flyway owns the schema
- **Frontend:** React 19, Vite 5, TypeScript 5, Ant Design 5.24+ (NOT v6), Redux Toolkit + RTK Query 2.x, React Router 6
- **Integrations:** WhatsApp Meta Cloud API (webhooks); Spring AI chatbot — **OpenAI** (`gpt-4o-mini` chat + `text-embedding-3-small` for RAG). `OPENAI_MOCK=true` keeps it mocked; set false + a real `OPENAI_API_KEY` to go live.
- **Infra:** Docker Compose (Postgres, RabbitMQ, Redis, backend, frontend)

## Hard rules

- REST only — no GraphQL.
- State = Redux Toolkit + RTK Query only (no redux-saga / TanStack Query / Zustand). Server data stays in the RTK Query cache — never mirror it into slices (slices = client-only state: auth, UI).
- Flyway owns the schema: `hibernate.ddl-auto: validate` only — never `create`/`update`.
- Every tenant-scoped DB query filters by `tenant_id`.
- No Kubernetes — runs on Docker Compose.
- Stay on Spring Boot 3.5 + AntD 5 documented APIs (React 19 patterns are fine).
- Secrets live in `.env` (intentionally tracked in this private, solo repo) — never hardcode them in source.

## Conventions

**Backend** — one package per feature (`com.marketinghub.<feature>`); DTOs at the controller boundary, never expose JPA entities; `@Transactional` on mutating service methods, not controllers; constructor injection (Lombok `@RequiredArgsConstructor`); `jakarta.validation` on DTOs; error shape `{"error":{"code","message","traceId"}}`.

**Frontend** — one folder per feature under `src/features/` (slice + api + pages); single `createApi` in `src/services/baseApi.ts`, extended via `injectEndpoints`; base query in `src/services/baseQuery.ts`; store in `src/app/store.ts`, typed hooks in `src/app/hooks.ts`; AntD `ConfigProvider` wraps the app.

If something is genuinely ambiguous, ask before guessing.
