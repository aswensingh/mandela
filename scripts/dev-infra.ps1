# Enter "dev mode": run ONLY the infrastructure (Postgres, RabbitMQ, Redis) in Docker,
# and stop the Dockerized backend/frontend so their ports (8080 / 5173) are free for the
# host processes that hot-reload. Run this once, then dev-backend.ps1 + dev-frontend.ps1.
Set-Location "$PSScriptRoot\.."

docker compose up -d postgres rabbitmq redis
docker compose stop backend frontend

Write-Host ""
Write-Host "Infra is up (Postgres/RabbitMQ/Redis). Dockerized backend + frontend stopped." -ForegroundColor Green
Write-Host "Now, in two terminals:" -ForegroundColor Green
Write-Host "  Terminal 1:  ./scripts/dev-backend.ps1   -> backend on :8080 (hot reload)"
Write-Host "  Terminal 2:  ./scripts/dev-frontend.ps1  -> frontend on :5173 (HMR)"
Write-Host ""
Write-Host "To go back to the full Docker stack:  docker compose up -d --build" -ForegroundColor DarkGray
