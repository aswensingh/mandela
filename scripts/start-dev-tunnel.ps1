param(
    [switch]$SkipDocker,
    [switch]$RestartNgrok
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path $Path)) {
        throw "Missing .env file at $Path"
    }

    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }

        $idx = $trimmed.IndexOf("=")
        if ($idx -le 0) {
            continue
        }

        $key = $trimmed.Substring(0, $idx).Trim()
        $value = $trimmed.Substring($idx + 1).Trim()
        $values[$key] = $value.Trim('"').Trim("'")
    }

    return $values
}

function Wait-ForBackend {
    $healthUrl = "http://localhost:8080/api/health"
    Write-Host "Waiting for backend health at $healthUrl ..."

    for ($i = 0; $i -lt 60; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                Write-Host "Backend is healthy."
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "Backend did not become healthy within 2 minutes."
}

function Get-NgrokTunnel {
    try {
        $state = Invoke-RestMethod -Uri "http://127.0.0.1:4040/api/tunnels" -TimeoutSec 2
        return $state.tunnels
    } catch {
        return @()
    }
}

function Wait-ForNgrokTunnel {
    param([string]$ExpectedUrl)

    Write-Host "Waiting for ngrok tunnel at $ExpectedUrl ..."
    for ($i = 0; $i -lt 30; $i++) {
        $match = Get-NgrokTunnel | Where-Object { $_.public_url -eq $ExpectedUrl } | Select-Object -First 1
        if ($null -ne $match) {
            Write-Host "ngrok tunnel is online."
            return
        }
        Start-Sleep -Seconds 1
    }

    $outLog = Join-Path $repoRoot "tmp\ngrok.out.log"
    $errLog = Join-Path $repoRoot "tmp\ngrok.err.log"
    Write-Host "ngrok did not report the expected URL. Recent logs:"
    if (Test-Path $outLog) { Get-Content $outLog -Tail 20 }
    if (Test-Path $errLog) { Get-Content $errLog -Tail 20 }
    throw "ngrok failed to start with $ExpectedUrl"
}

$envPath = Join-Path $repoRoot ".env"
$config = Read-DotEnv $envPath

$ngrokUrl = $config["NGROK_STATIC_URL"]
if ([string]::IsNullOrWhiteSpace($ngrokUrl)) {
    throw "Set NGROK_STATIC_URL in .env first."
}
$ngrokUrl = $ngrokUrl.TrimEnd("/")

$verifyToken = $config["WHATSAPP_VERIFY_TOKEN"]
if ([string]::IsNullOrWhiteSpace($verifyToken)) {
    throw "Set WHATSAPP_VERIFY_TOKEN in .env first."
}

$ngrok = Get-Command "ngrok" -ErrorAction SilentlyContinue
if ($null -eq $ngrok) {
    throw "ngrok is not on PATH. Reopen PowerShell after installing ngrok, or add ngrok.exe to PATH."
}

if (-not $SkipDocker) {
    Write-Host "Starting MarketingHub Docker stack ..."
    docker compose up -d
}

Wait-ForBackend

$existingMatch = Get-NgrokTunnel | Where-Object { $_.public_url -eq $ngrokUrl } | Select-Object -First 1
if ($null -ne $existingMatch -and -not $RestartNgrok) {
    Write-Host "ngrok is already serving $ngrokUrl"
} else {
    $existingNgrok = Get-Process -Name "ngrok" -ErrorAction SilentlyContinue
    if ($null -ne $existingNgrok) {
        if ($RestartNgrok) {
            Write-Host "Stopping existing ngrok process ..."
            $existingNgrok | Stop-Process -Force
            Start-Sleep -Seconds 2
        } else {
            throw "ngrok is already running but not on $ngrokUrl. Close it, or rerun this script with -RestartNgrok."
        }
    }

    $tmpDir = Join-Path $repoRoot "tmp"
    New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
    $outLog = Join-Path $tmpDir "ngrok.out.log"
    $errLog = Join-Path $tmpDir "ngrok.err.log"
    Remove-Item -Force -ErrorAction SilentlyContinue $outLog, $errLog

    Write-Host "Starting ngrok: $ngrokUrl -> http://localhost:8080"
    Start-Process `
        -FilePath $ngrok.Source `
        -ArgumentList @("http", "8080", "--url", $ngrokUrl) `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -WindowStyle Hidden

    Wait-ForNgrokTunnel $ngrokUrl
}

$challenge = "marketinghub_" + [Guid]::NewGuid().ToString("N")
$handshakeUrl = $ngrokUrl `
    + "/api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=" `
    + [Uri]::EscapeDataString($verifyToken) `
    + "&hub.challenge=" `
    + [Uri]::EscapeDataString($challenge)

Write-Host "Checking public webhook handshake ..."
$handshake = Invoke-WebRequest `
    -Uri $handshakeUrl `
    -UseBasicParsing `
    -TimeoutSec 20 `
    -Headers @{
        "User-Agent" = "facebookexternalhit/1.1"
        "ngrok-skip-browser-warning" = "true"
    }
if ($handshake.StatusCode -ne 200 -or $handshake.Content -ne $challenge) {
    throw "Webhook handshake failed. HTTP $($handshake.StatusCode), body: $($handshake.Content)"
}

Write-Host ""
Write-Host "Ready."
Write-Host "Meta callback URL:"
Write-Host "$ngrokUrl/api/webhooks/whatsapp"
Write-Host ""
Write-Host "Keep this PC awake, Docker Desktop running, and ngrok running while testing."
