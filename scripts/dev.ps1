# Start the whole dev environment with ONE command:   .\scripts\dev.ps1
#
# The backend and frontend are two long-running servers, so they can't share a single
# terminal. This opens each in its own PowerShell window (each prints its own logs /
# readiness banner). Close a window or press Ctrl+C in it to stop that server.

$backend  = Join-Path $PSScriptRoot 'dev-backend.ps1'
$frontend = Join-Path $PSScriptRoot 'dev-frontend.ps1'

Start-Process powershell -ArgumentList '-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $backend
Start-Process powershell -ArgumentList '-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $frontend

Write-Host ""
Write-Host "Launched two windows:" -ForegroundColor Green
Write-Host "  backend  -> http://localhost:8080  (ready when you see 'MARKETINGHUB BACKEND READY')"
Write-Host "  frontend -> http://localhost:5173"
