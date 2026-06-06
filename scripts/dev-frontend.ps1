# Run the Vite dev server on the host — instant HMR, no rebuilds. It proxies /api to
# http://localhost:8080 (see frontend/vite.config.ts), i.e. the host backend from dev-backend.ps1.
#
# Prereq: ./scripts/dev-infra.ps1 has been run. Uses Corepack pnpm (host npm is broken here).
# Free port 5173 from any Dockerized frontend (no-op if it isn't running).
Push-Location "$PSScriptRoot\.."
docker compose stop frontend | Out-Null
Pop-Location

Set-Location "$PSScriptRoot\..\frontend"

corepack pnpm install
corepack pnpm dev
