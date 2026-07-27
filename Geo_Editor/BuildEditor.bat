@echo off
chcp 65001 >nul
title GeoEditor - Fast Build
set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"

echo.
echo ========================================
echo          GEOEDITOR - FAST BUILD
echo ========================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT_DIR%tools\build-fast.ps1" %*
if errorlevel 1 (
    echo.
    echo [ERROR] BUILD FAILED.
    pause
    exit /b 1
)

echo.
echo [OK] BUILD COMPLETED SUCCESSFULLY.
pause
