# CLAUDE.md — MarketingHub

> Auto-loaded by Claude Code at the start of every session. Source of truth for project conventions. When training data and this file conflict, this file wins.

## What we're building

**MarketingHub** is a multi-tenant web app for marketing agents. Agents send WhatsApp promotional messages to their customers and an AI chatbot handles customer replies in a human-like way. Goal: reduce manual follow-up work.

- Used by multiple client companies (dental clinics, restaurants, casinos, universities, etc.) — each is a separate **tenant** with isolated data.
- Each tenant has its own users with roles: **Tenant Admin**, **Agent**, **Viewer**.
- Above all tenants: **Platform Admin** (the developer/owner of MarketingHub), who creates and manages tenants.
- Must support blasts to several thousand customers per tenant.
- Agents can take over conversations from the bot at any time.
- Customers are imported via CSV (later phase).
- Local development first. Production deployment deferred.

## Tech stack (LOCKED)

| Layer | Choice | Version | Notes |
|---|---|---|---|
| JDK | Eclipse Temurin | **25 LTS** | already installed |
| Build | Maven | **3.9.16** | already installed |
| Backend | Spring Boot | **3.5.x** (latest 3.5) | NOT 4.x — keep ecosystem maturity |
| Database | PostgreSQL | **16** | with `pgvector` extension for AI RAG (used in later phase) |
| Frontend | React | **18.3** | NOT 19 — keep AntD 5 compatible |
| Build (FE) | Vite | **5.x** | |
| Language (FE) | TypeScript | **5.x** | |
| UI library | Ant Design | **5.x** | NOT v6 — keep ecosystem maturity |
| State management | Redux Toolkit + RTK Query | **2.x** | not classic Redux, not redux-saga |
| Routing | React Router | **6.x** | |
| Node | Node.js | **24 LTS** | already installed |
| WhatsApp | Meta Cloud API | webhook-based | the de facto standard for new projects |
| AI chatbot | OpenAI (chat + embeddings) | gpt-4o-mini for chat, text-embedding-3-small | single vendor, one API key |
| Local infra | Docker Compose | v2 | |

**Why not the bleeding edge?** Spring Boot 4 and AntD 6 just shipped. Claude Code's training data and the wider ecosystem still lean toward Spring Boot 3.x and AntD 5. Last build attempt failed partly because of this. We trade "newest" for "runs on first try."

**Java 25 with Spring Boot 3.5** is fully supported and gives us virtual threads, pattern matching, etc.

**OpenAI as primary AI** — single API key, simple. Switching to Claude later is straightforward because we'll use Spring AI's abstractions.

## Hard rules — NEVER violate

1. **One phase at a time.** Phase N+1 cannot start until Phase N's verification gate is green. Don't anticipate later phases.
2. **NO Kubernetes** in this project — manifests, Helm, anything. Production runs on Docker Compose. (Production is many phases away; not yet.)
3. **NO GraphQL.** REST only.
4. **NO redux-saga, NO TanStack Query, NO Zustand.** Redux Toolkit + RTK Query handles all state.
5. **NO mirroring server state into Redux slices.** Server data lives in RTK Query cache. Slices are for client-only state (auth, UI).
6. **NO secrets in code or config files committed to git.** Use `.env` (gitignored) + `.env.example` (committed).
7. **NO `hibernate.ddl-auto: create` or `update`.** Always `validate`. Flyway owns the schema.
8. **NO Spring Boot 4 or AntD 6 patterns.** Use Spring Boot 3.5 + AntD 5 documented APIs only.
9. **Every tenant-scoped DB query must filter by `tenant_id`.** Multi-tenancy enforcement details added in the auth phase.

## Repository layout

```
marketinghub/
├── CLAUDE.md                       # this file, auto-loaded
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml              # added Phase 1
├── backend/                        # added Phase 1
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/marketinghub/
└── frontend/                       # added Phase 1
    ├── package.json
    ├── Dockerfile
    └── src/
└── docs/
    ├── architecture.md             # grows over time
    ├── decisions.md                # ADRs
    └── phases/
        ├── phase-1-hello-world.md
        ├── phase-2-database.md
        ├── phase-3-auth.md
        ├── phase-4-multi-tenancy.md
        ├── phase-5-tenant-management.md
        ├── phase-6-user-management.md
        ├── phase-7-customers.md
        ├── phase-8-csv-import.md
        ├── phase-9-whatsapp-config.md
        ├── phase-10-whatsapp-send.md
        ├── phase-11-templates.md
        ├── phase-12-campaigns.md
        ├── phase-13-blast-worker.md
        ├── phase-14-inbound-webhooks.md
        ├── phase-15-conversations-ui.md
        ├── phase-16-ai-chatbot.md
        ├── phase-17-knowledge-base.md
        └── phase-18-agent-takeover.md
```

## Phase plan — VERY SMALL phases

Each phase is **deliberately tiny** so that if it breaks, it's obvious where. After each phase, every verification command must pass before moving on.

| # | Phase | Outcome |
|---|---|---|
| 1 | Hello world | Backend says `{"status":"ok"}`, frontend shows "Hello MarketingHub" |
| 2 | Database + Flyway | Postgres in compose, backend connects and reports DB version |
| 3 | Auth (Spring Security + JWT) | Users table, register/login endpoints, JWT issued |
| 4 | Multi-tenancy | Tenants table, every user belongs to a tenant, JWT carries `tenant_id` |
| 5 | Tenant management UI | Platform Admin creates/lists tenants in the React app |
| 6 | User management UI | Tenant Admin creates/lists users in their tenant |
| 7 | Customers entity | Customer CRUD endpoints + UI |
| 8 | CSV import | Upload CSV, parse, save customers in background job |
| 9 | WhatsApp config per tenant | Each tenant saves their Meta Cloud API credentials |
| 10 | WhatsApp send (single) | Send one message via the API, store it |
| 11 | Message templates | CRUD for templates a tenant can reuse |
| 12 | Campaigns entity | A campaign = template + recipient list |
| 13 | Blast worker | Send a campaign to all recipients with rate limiting |
| 14 | Inbound webhooks | Receive incoming WhatsApp messages, store them |
| 15 | Conversations UI | Two-pane chat view showing conversations |
| 16 | AI chatbot replies | Bot replies to inbound messages using OpenAI |
| 17 | Knowledge base + RAG | Upload docs per tenant; bot uses them when answering |
| 18 | Agent takeover | Agent can take over a conversation, bot stops replying |

Even with 18 phases, each is small and verifiable. Better to do 18 small phases that all work than 8 big ones that each fail halfway.

## Conventions

### Backend
- Package: `com.marketinghub.<feature>` (one package per feature, not per layer)
- DTOs at the controller boundary. Never expose JPA entities directly via REST.
- Use `@Transactional` on service methods that mutate, not on controllers.
- Use constructor injection (Lombok `@RequiredArgsConstructor`).
- Validation via `jakarta.validation` annotations on DTOs.
- Error responses: `{"error":{"code":"STRING_CODE","message":"...","traceId":"..."}}`.

### Frontend
- One folder per feature under `src/features/`, containing the slice, the API, and the pages.
- `src/services/baseApi.ts` is the single `createApi` instance. Features extend it via `injectEndpoints`.
- `src/services/baseQuery.ts` holds the fetch base query and (later) the reauth wrapper.
- `src/app/store.ts` configures the store. `src/app/hooks.ts` exports typed `useAppDispatch`/`useAppSelector`.
- AntD `ConfigProvider` wraps the app. Theme tokens go through `ConfigProvider`.

## When in doubt

- Read `docs/architecture.md` (filled in over time) for context.
- Read `docs/decisions.md` for prior decisions.
- Read the current phase's doc under `docs/phases/`.
- If something is ambiguous, ASK before guessing. One focused question is cheap; a rebuild is not.
