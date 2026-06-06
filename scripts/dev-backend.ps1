# Run the backend on the host with hot reload (Spring Boot DevTools restarts on recompile).
# Reuses your .env for all config, but repoints the service hostnames at localhost — inside
# Docker they're "postgres"/"rabbitmq"/"redis"; on the host everything is on localhost.
#
# Prereq: ./scripts/dev-infra.ps1 has been run (infra up, Docker backend stopped).
# Hot reload: edit + let your IDE recompile (IntelliJ "Build", or VS Code save) and DevTools
# restarts in ~1-2s. From a plain editor, run `mvn -f backend/pom.xml compile` to trigger it.
$root = (Resolve-Path "$PSScriptRoot\..").Path

# Make sure infra is up and free port 8080 from any Dockerized backend (both no-op if already so).
Push-Location $root
docker compose up -d postgres rabbitmq redis | Out-Null
docker compose stop backend | Out-Null
Pop-Location

# Load every KEY=VALUE line from .env into this process's environment.
Get-Content "$root\.env" | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
}

# Containers reach each other by service name; on the host it's all localhost.
$env:SPRING_DATASOURCE_URL = $env:SPRING_DATASOURCE_URL -replace '//postgres:', '//localhost:'
$env:SPRING_RABBITMQ_HOST  = 'localhost'
$env:SPRING_DATA_REDIS_HOST = 'localhost'

# docker-compose derives Spring's RabbitMQ creds from RABBITMQ_USER/PASSWORD. Replicate that:
# the broker was created with those as its user, so the default 'guest' login is refused.
$env:SPRING_RABBITMQ_USERNAME = $env:RABBITMQ_USER
$env:SPRING_RABBITMQ_PASSWORD = $env:RABBITMQ_PASSWORD

Write-Host "Starting backend on http://localhost:8080 (DevTools hot reload)..." -ForegroundColor Green
# -q quiets Maven's own preamble; the Spring app logs (and any build errors) still show.
& mvn -q -f "$root\backend\pom.xml" spring-boot:run
