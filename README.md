# MarketingHub

Multi-tenant web app for marketing agents to send WhatsApp promotional messages, with an AI chatbot that handles customer replies.

> Built phase-by-phase with Claude Code. Start with `CLAUDE.md` and `docs/phases/phase-1-hello-world.md`.

---

## How to use this repo with Claude Code

1. **Verify your tooling** (you've already done this):
   ```powershell
   java -version        # should show 25.x
   node --version       # should show v24.x
   mvn --version        # should show 3.9.16 AND "Java version: 25.x"
   docker --version
   docker compose version
   ```

2. **Open this folder in VS Code** with the Claude Code extension.

3. **Start Phase 1.** Open a Claude Code chat and paste:
   ```
   Read CLAUDE.md and docs/phases/phase-1-hello-world.md, then execute Phase 1.
   Run every verification command. Do not do any work outside this phase.
   Report back with verification outputs.
   ```

4. **Verify it yourself.** Run the verification commands from `phase-1-hello-world.md` in your own terminal. Don't trust Claude Code's word — run them.

5. **Move on only after Phase 1 is green.** Then:
   ```
   Read CLAUDE.md and docs/phases/phase-2-database.md, then execute Phase 2.
   Run every verification command. Report back.
   ```

6. **Repeat.** Phases 1 through 7 each have their own file. Phases 8 through 18 are outlined in `phase-8-to-18-outline.md` — you (or Claude Code) will expand each into a full doc when its turn comes.

---

## What's in each phase

| # | Phase | What works after |
|---|---|---|
| 1 | Hello world | Frontend says "Hello", backend returns `/api/health` |
| 2 | Database | Postgres in compose, Flyway runs, `/api/db-info` works |
| 3 | Auth | Register, login, JWT, refresh, /me endpoint, React login flow |
| 4 | Multi-tenancy | Users belong to tenants, Platform Admin seeded |
| 5 | Tenant management | Platform Admin creates tenants in UI |
| 6 | User management | Tenant Admin manages users in their tenant |
| 7 | Customers | CRUD for customer records |
| 8 | CSV import | Upload customer list as CSV |
| 9 | WhatsApp config | Save WhatsApp Business credentials per tenant |
| 10 | WhatsApp send | Send a single message via Meta Cloud API |
| 11 | Templates | CRUD for message templates |
| 12 | Campaigns | Define a campaign (template + recipients) |
| 13 | Blast worker | Launch a campaign, send to all recipients with rate limiting |
| 14 | Inbound webhooks | Receive customer replies |
| 15 | Conversations UI | View incoming conversations in the React app |
| 16 | AI chatbot | Bot replies to incoming messages |
| 17 | Knowledge base + RAG | Bot uses tenant-specific knowledge docs |
| 18 | Agent takeover | Agent takes over from bot |

After Phase 18, the MVP is functionally complete. Hardening, observability, and production deploy are later work.

---

## Tech stack at a glance

- **Backend:** Java 25 + Spring Boot 3.5 + PostgreSQL 16 + (later) RabbitMQ + Redis + Spring AI
- **Frontend:** React 18 + Vite + TypeScript + Ant Design 5 + Redux Toolkit + RTK Query
- **WhatsApp:** Meta Cloud API
- **AI:** OpenAI (gpt-4o-mini for chat, text-embedding-3-small for embeddings)
- **Infra:** Docker Compose, local development first

See `CLAUDE.md` for the locked versions and conventions.

---

## Why so many small phases?

A previous attempt with bigger phases failed — Claude Code couldn't scaffold large multi-service phases without something subtly breaking. With 18 small phases, each one is debuggable on its own, and you have a working app after every phase (just with fewer features).
