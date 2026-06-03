@echo off
setlocal

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-dev-tunnel.ps1" %*

echo.
pause
