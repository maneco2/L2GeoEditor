@echo off
setlocal

cd /d "%~dp0"
title GeoEditor Build

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build.ps1"
set "BUILD_EXIT=%ERRORLEVEL%"

echo.
if not "%BUILD_EXIT%"=="0" echo BUILD FALHOU - consulte o log.
echo Pressione qualquer tecla para sair...
pause >nul
exit /b %BUILD_EXIT%
