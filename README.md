# MarketingHub

**A multi-tenant SaaS that lets marketing agents blast WhatsApp messages to their customers and have an AI chatbot handle the replies — so a small team can run promotional campaigns at scale without drowning in DMs.**

Each client company (a "tenant") gets their own isolated workspace: customers, templates, campaigns, conversations, and an AI chatbot trained on their own knowledge base. Human agents can take over any conversation at any time.

---

## Who is this for?

| Persona | What they do |
|---|---|
| **Platform Admin** (you, the operator) | Run the whole platform. Create new tenant workspaces, manage their lifecycle. |
| **Tenant Admin** (your client) | Manage their company's WhatsApp credentials, customers, templates, campaigns, AI chatbot, and team. |
| **Agent** (your client's staff) | Watch incoming conversations, take over from the bot when needed, reply manually. |
| **Viewer** (read-only) | See everything for compliance / oversight, can't change anything. |

Typical clients are small-to-mid businesses doing direct customer engagement — dental clinics, restaurants, gyms, casinos, universities, real-estate agencies, etc.

---

## Quick start (local development)

You need: **Docker Desktop**, **Java 25**, **Node 24**, **Maven 3.9+**.

```bash
git clone <this repo>
cd marketinghub
cp .env.example .env          # already includes safe local defaults
docker compose up -d          # starts postgres, redis, rabbitmq, backend, frontend
```

Wait ~15 seconds for the backend to become healthy, then open **http://localhost:5173**.

Daily shortcut commands:

```powershell
npm run dev          # start Docker stack + ngrok tunnel + verify webhook handshake
npm run dev:restart  # restart ngrok if the tunnel process is confused
npm run dev:status   # show container health/ports
npm run dev:logs     # follow backend logs
npm run dev:stop     # stop ngrok + Docker containers, keeping DB volumes
```

The WhatsApp webhook callback for local testing is:

```text
https://turkey-storage-private.ngrok-free.dev/api/webhooks/whatsapp
```

Make sure Docker Desktop is running before `npm run dev`.

### Default seeded accounts

| Role | Email | Password |
|---|---|---|
| Platform Admin | `admin@marketinghub.local` | `change-me-locally` |
| Acme Tenant Admin | `acme@acme.com` | `acme12345` |
| Acme Agent | `agent1@acme.com` | `agent12345` |
| Beta Tenant Admin | `beta@beta.com` | `beta12345` |

The seed data also includes ~2 000 fake customers under each tenant so you can immediately try the Customers page, template creation, and campaign launches.

### Mock mode (default)

Out of the box, the app runs with **mocked WhatsApp + mocked OpenAI** — no external API keys needed. You can:

- Send campaigns (they go to a fake Meta endpoint and complete instantly)
- Receive simulated inbound messages via the verification scripts in `scripts/`
- Watch the AI chatbot reply (it's a deterministic echo bot in mock mode)

To switch to real services, see [Going live](#going-live) below.

---

## How to use it

### As Platform Admin

1. Log in with `admin@marketinghub.local`.
2. **Tenants** menu → see all client workspaces. Each row shows the tenant's admins.
3. Click **Create Tenant** to onboard a new client — provide a name, industry, and the first admin's email + password.
4. Hand those credentials to the client. They take it from there.
5. If a client forgets their password, click the 🔑 icon next to their email — you can generate a random password or set one, then convey it to them.
6. Lifecycle actions per tenant:
   - **Suspend** — they can't log in but data stays.
   - **Delete** — soft delete; tenant disappears from the default list. Toggle "Show deleted" to see them.
   - **Purge permanently** — irreversible. Only available on already-deleted tenants. You must type the tenant name to confirm.

### As Tenant Admin

After your Platform Admin creates your workspace and gives you the credentials, log in. You'll see a sidebar menu with everything you can configure.

**First-run checklist:**

1. **WhatsApp** → paste your Meta Cloud API `phone_number_id` and `access_token`. Both are stored AES-256 encrypted.
2. **Knowledge Base** → upload PDFs / Markdown / text files of your FAQs, price list, hours of operation, etc. The AI bot will use these to answer customers.
3. **Chatbot Settings** (under WhatsApp) → write your system prompt: "You are a friendly receptionist at Acme Dental. Be polite, helpful, and never make up appointment times."
4. **Users** → invite agents (Role: AGENT) and viewers (Role: VIEWER).
5. **Customers** → upload a CSV (`phone_e164, full_name, tags, opt_in_status`) or add them one by one.
6. **Templates** → create reusable message bodies with `{name}`-style placeholders.
7. **Campaigns** → pick a template + filter recipients (by tag, opt-in status) → click **Launch**.

Conversations land in the **Conversations** menu in real time. The bot answers automatically; flip to **HUMAN** at any time to take over.

### As Agent

You spend almost all your time in **Conversations**.

1. The list auto-refreshes every 5 seconds. New inbound messages appear at the top.
2. Each conversation has a status tag: `BOT` (chatbot is answering), `HUMAN` (an agent has taken over), or `CLOSED`.
3. Open a chat. If the bot is doing fine, just watch. If you want to step in:
   - Click **Take Over** → status flips to HUMAN, bot stops replying.
   - Type into the composer at the bottom. **Cmd/Ctrl+Enter** to send.
   - When you're done, click **Release to Bot** → bot resumes on the next inbound.

The bot can also auto-hand off if its confidence drops (look for status flipping to HUMAN without your action).

### As Viewer

Read-only across the whole tenant. Useful for compliance/audit accounts.

---

## Going live

Mock mode is great for development. For real customers, you need:

| Service | Required for | Cost |
|---|---|---|
| **Meta WhatsApp Business Platform** | Sending/receiving real WhatsApp messages | Free first 1000 conversations/month, then $0.005-0.07 each |
| **OpenAI** | AI chatbot replies | ~$0.0003 per reply (gpt-4o-mini) |
| **A public HTTPS URL** | Receiving Meta webhooks | Free via ngrok / Cloudflare Tunnel; ~$5/month for a real VPS |

### Switching from mock to real

Edit `.env`:

```bash
WHATSAPP_MOCK=false
OPENAI_MOCK=false
OPENAI_API_KEY=sk-<your-real-key>
OPENAI_EMBEDDING_AUTOCONFIG=openai
WHATSAPP_VERIFY_TOKEN=<your-random-string>
WHATSAPP_APP_SECRET=<from-meta-app-settings>

# For production: regenerate these
JWT_SECRET=<openssl rand -base64 32>
ENCRYPTION_MASTER_KEY=<openssl rand -base64 32>
```

Then `docker compose restart backend` and each tenant pastes their own Meta credentials in **WhatsApp Settings**.

For the webhook to work, Meta needs to be able to reach `https://<your-domain>/api/webhooks/whatsapp`. Easiest path:

- **Local dev / first test:** `ngrok http 8080` → put the ngrok URL into Meta's webhook config.
- **Production:** deploy to a VPS (Hetzner/DigitalOcean/Linode), put **Caddy** in front for automatic Let's Encrypt TLS.

For a stable local ngrok URL, set `NGROK_STATIC_URL` in `.env` and run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-dev-tunnel.ps1
```

On Windows you can also double-click `scripts\start-dev-tunnel.cmd`. It starts the Docker stack, starts ngrok with the configured static URL, and verifies the public Meta webhook handshake.

Detailed credentials checklist is in `docs/decisions.md` and there's a step-by-step in the Phase 9 doc.

---

## Tech stack

- **Backend:** Java 25 + Spring Boot 3.5 + PostgreSQL 16 (with `pgvector` for RAG) + RabbitMQ + Redis + Spring AI
- **Frontend:** React 19 + Vite 5 + TypeScript 5 + Ant Design 5 + Redux Toolkit + RTK Query + React Router 6
- **WhatsApp:** Meta Cloud API (webhook-based)
- **AI:** OpenAI (`gpt-4o-mini` for chat, `text-embedding-3-small` for embeddings) — pluggable via Spring AI's abstractions
- **Infra:** Docker Compose, local development first

See `CLAUDE.md` for the locked versions and conventions.

---

## Architecture (one-paragraph)

Every request comes in via the Spring Boot REST API at `/api/*`. A `JwtAuthenticationFilter` resolves the bearer token to an `AuthenticatedPrincipal`, then a `TenantContextFilter` pushes the tenant id into a `ThreadLocal` so every tenant-scoped query knows what tenant it's serving. Outbound WhatsApp messages flow through RabbitMQ (`campaign.send` queue) with a Redis-backed token-bucket rate limiter (80 msg/sec by default). Inbound messages from Meta hit `/api/webhooks/whatsapp`, get HMAC-verified, then enqueue onto `ai.reply` for the AI worker to generate a response (using pgvector-stored RAG chunks). Conversations carry one of three statuses (`BOT_ACTIVE`, `HUMAN_ACTIVE`, `CLOSED`) — the AI worker silently skips anything not `BOT_ACTIVE`, which is how agent takeover silences the bot for free.

---

## Project structure

```
marketinghub/
├── CLAUDE.md                       # project rules for AI assistants
├── README.md                       # this file
├── docker-compose.yml              # 5-service local stack
├── .env.example                    # template — copy to .env
├── backend/                        # Spring Boot
│   └── src/main/java/com/marketinghub/
│       ├── auth/                   # JWT, login, users
│       ├── tenant/                 # multi-tenancy, WhatsApp config
│       ├── customer/               # CRUD + CSV import
│       ├── template/               # message templates
│       ├── campaign/               # campaign blast + worker
│       ├── webhook/                # Meta inbound webhook
│       ├── conversation/           # chat threading + take-over
│       ├── ai/                     # chatbot + RAG
│       ├── knowledge/              # document upload + embedding
│       ├── whatsapp/               # Meta API client
│       └── common/                 # encryption, error handling, rate limiting
├── frontend/                       # React + Vite
│   └── src/
│       ├── app/                    # store + hooks
│       ├── services/               # baseApi (RTK Query), reauth
│       ├── features/               # one folder per feature (auth, tenants, ...)
│       └── shared/                 # AppLayout
├── docs/
│   ├── architecture.md
│   ├── decisions.md
│   └── phases/                     # development phase docs
└── scripts/                        # smoke-test scripts you can run against a running stack
```

---

## Development phases

The app was built in 18 small phases, each independently verifiable. See `docs/phases/` for the details.

| # | Phase | What works after |
|---|---|---|
| 1 | Hello world | Frontend says "Hello", backend returns `/api/health` |
| 2 | Database | Postgres in compose, Flyway runs, `/api/db-info` works |
| 3 | Auth | Register, login, JWT, refresh, /me endpoint |
| 4 | Multi-tenancy | Users belong to tenants, Platform Admin seeded |
| 5 | Tenant management | Platform Admin creates tenants in UI |
| 6 | User management | Tenant Admin manages users in their tenant |
| 7 | Customers | CRUD for customer records |
| 8 | CSV import | Upload customer list as CSV (async worker) |
| 9 | WhatsApp config | Save WhatsApp Business credentials per tenant (encrypted) |
| 10 | WhatsApp send | Send a single message via Meta Cloud API |
| 11 | Templates | CRUD for message templates |
| 12 | Campaigns | Define a campaign (template + recipients) |
| 13 | Blast worker | Launch a campaign, send to all with rate limiting |
| 14 | Inbound webhooks | Receive customer replies (HMAC-verified) |
| 15 | Conversations UI | Two-pane chat view |
| 16 | AI chatbot | Bot replies to incoming messages |
| 17 | Knowledge base + RAG | Bot uses tenant-specific knowledge docs |
| 18 | Agent takeover | Agent takes over from bot, sends manual replies |

Post-MVP additions (still going in):
- Soft + hard tenant deletion with cascade
- Admin-assisted password reset
- React 19 upgrade

---

## Running the verification scripts

The `scripts/` folder has end-to-end smoke tests that exercise full user journeys against a running stack:

```bash
bash scripts/phase18-verify.sh         # inbound → bot → take over → agent reply → release
bash scripts/tenant-delete-verify.sh   # create → soft delete → restore filter → hard purge
bash scripts/reset-password-verify.sh  # platform admin + tenant admin reset flows
```

Each script is self-contained, uses the seeded accounts, and prints pass/fail per step.

---

## Backend tests

```bash
cd backend
./mvnw test
```

Currently **126 tests** covering auth, multi-tenancy, customers, CSV import, encryption, WhatsApp send, templates, campaigns, the blast worker, the rate limiter, webhooks, conversations, the AI worker, the knowledge base RAG, tenant deletion, and password reset.

---

## Why so many small phases?

A previous attempt with bigger phases failed — agentic coding assistants couldn't scaffold large multi-service phases without something subtly breaking. With 18 small phases, each one is debuggable on its own, and you have a working app after every phase (just with fewer features).

---

## License

Private project — not currently published.
