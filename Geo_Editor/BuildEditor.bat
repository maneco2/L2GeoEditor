@echo off
chcp 65001 >nul
title Geo Editor - Build Rapido
set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"

echo.
echo ========================================
echo        GEO EDITOR - BUILD RAPIDO
echo ========================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT_DIR%tools\build-fast.ps1" %*
if errorlevel 1 (
    echo.
    echo [ERRO] BUILD FALHOU.
    pause
    exit /b 1
)

echo.
echo [OK] BUILD CONCLUIDO COM SUCESSO.
pause
