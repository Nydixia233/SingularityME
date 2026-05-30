@echo off
setlocal
set "ROOT=%~dp0"

if "%~1"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\deploy-built-mod.ps1" -Watch
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\deploy-built-mod.ps1" %*
)

exit /b %ERRORLEVEL%
