$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

Write-Host "Stopping ngrok if it is running ..."
$ngrokProcesses = Get-Process -Name "ngrok" -ErrorAction SilentlyContinue
if ($null -ne $ngrokProcesses) {
    $ngrokProcesses | Stop-Process -Force
    Write-Host "ngrok stopped."
} else {
    Write-Host "ngrok was not running."
}

Write-Host "Stopping MarketingHub Docker containers ..."
docker compose stop

Write-Host ""
Write-Host "Stopped. Data volumes were kept."
