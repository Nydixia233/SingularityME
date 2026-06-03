@echo off
setlocal
set "ROOT=%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\start-test-env.ps1" %*

exit /b %ERRORLEVEL%
