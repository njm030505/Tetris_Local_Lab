@echo off
cd /d "%~dp0"
if exist "build\TFSE-Phase3-Local\TFSE-Phase3-Local.exe" (
 start "" "build\TFSE-Phase3-Local\TFSE-Phase3-Local.exe"
) else (
 powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-local.ps1"
)
