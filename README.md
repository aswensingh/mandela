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

## Getting started (new developer)

Everything runs in Docker — you don't need Java, Node, or Maven installed locally just to run the app. (You only need those if you want to run the backend/frontend outside containers.)

**Prerequisites:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) (with Docker Compose v2). Make sure it's running.

**1. Clone:**

```bash
git clone <this repo>
cd marketinghub
```

The repo ships a ready-to-run `.env` with safe local defaults — runs in mock mode, no API keys needed. (See [Going live](#going-live) to switch to real services.)

**2. Start the whole stack:**

```bash
docker compose up -d --build   # postgres, redis, rabbitmq, backend, frontend
```

First run takes a couple of minutes (it builds the images and runs DB migrations). Watch progress with `docker compose logs -f backend`.

**3. Open the app:** once the backend is healthy, go to **http://localhost:5173** and log in (see accounts below).

**Useful commands:**

```bash
docker compose ps              # container health / ports
docker compose logs -f backend # follow backend logs
docker compose down            # stop containers, keep the database
docker compose down -v         # stop AND wipe the database
```

### Default accounts

Login is by **username** (not email). On a fresh database the platform admin is created from
`PLATFORM_ADMIN_USERNAME` / `PLATFORM_ADMIN_PASSWORD` in your `.env`. The current local database
also has a demo tenant (**Acme Dental**) and its accounts already seeded:

| Role | Username | Password | Tenant |
|---|---|---|---|
| Platform Admin | `admin` | `admin` | — |
| Tenant Admin | `acme` | `acme12345` | Acme Dental |
| Agent | `agent1` | `agent12345` | Acme Dental |

Sign in as the platform admin to create and manage tenants, or as `acme` to manage Acme Dental's
customers, templates, and campaigns.

> **Note:** the Acme Dental accounts are demo data already present in your local database. The
> seeder that created them has been removed, so `docker compose down -v` (which wipes the DB) will
> **not** bring them back — only the platform admin is re-created on a fresh database.

### Mock mode (default)

Out of the box, the app runs with **mocked WhatsApp + mocked OpenAI** — no external API keys needed. You can:

- Send campaigns (they go to a fake Meta endpoint and complete instantly)
- Simulate inbound customer messages from the WhatsApp settings page (enable `WHATSAPP_TEST_TOOLS_ENABLED`)
- Watch the AI chatbot reply (it's a deterministic echo bot in mock mode)

To switch to real services, see [Going live](#going-live) below.

---

## How to use it

### As Platform Admin

1. Log in with the `admin` account.
2. **Tenants** menu → see all client workspaces. Each row shows the tenant's admins.
3. Click **Create Tenant** to onboard a new client — provide a name, industry, and the first admin's username + password.
4. Hand those credentials to the client. They take it from there.
5. If a client forgets their password, click the 🔑 icon next to their username — you can generate a random password or set one, then convey it to them.
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
7. **Campaigns** → choose a **message type**, filter recipients (by tag, opt-in status) → click **Launch**:
   - **📣 Marketing template** — reaches any customer, but needs a Meta-**approved** template (use **Sync from Meta** on the Templates page to see real approval status).
   - **💬 Free text** — no approval needed, but Meta only delivers it to customers who messaged you in the last 24h (others fail).

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

For a stable local ngrok URL, set `NGROK_STATIC_URL` in `.env`. The repo ships npm wrappers (Windows/PowerShell) that bring up the Docker stack **and** the tunnel together:

```powershell
npm run dev          # start Docker stack + ngrok tunnel + verify webhook handshake
npm run dev:restart  # restart ngrok if the tunnel process gets confused
npm run dev:status   # show container health/ports
npm run dev:logs     # follow backend logs
npm run dev:stop     # stop ngrok + Docker containers, keeping DB volumes
```

`npm run dev` is just a convenience wrapper around `scripts\start-dev-tunnel.ps1` (on Windows you can also double-click `scripts\start-dev-tunnel.cmd`): it starts the Docker stack, starts ngrok with the configured static URL, and verifies the public Meta webhook handshake. Make sure Docker Desktop is running first. The webhook callback Meta should point at is `https://<your-ngrok-domain>/api/webhooks/whatsapp`.

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
├── .env                            # local config — mock-mode defaults (tracked)
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
└── scripts/                        # dev workflow + ngrok tunnel helpers
```

---

## Backend tests

```bash
cd backend
./mvnw test
```

Currently **136 tests** covering auth, multi-tenancy, customers, CSV import, encryption, WhatsApp send, templates, campaigns (template + free-text send modes), the blast worker, the rate limiter, webhooks, conversations, the AI worker, the knowledge base RAG, tenant deletion, and password reset.

---

## License

Private project — not currently published.
