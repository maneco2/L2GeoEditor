@echo off
setlocal

cd /d "%~dp0"

echo ============================================================
echo GeoEditor 2.0.4.31 - Build completa
echo ============================================================

where java >nul 2>&1
if errorlevel 1 goto :java_missing

where javac >nul 2>&1
if errorlevel 1 goto :java_missing

echo.
echo [1/2] Executando testes...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\test-fast.ps1"
if errorlevel 1 goto :failed

echo.
echo [2/2] Gerando Build\GeoEditor.exe...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\package-exe.ps1"
if errorlevel 1 goto :failed

echo.
echo ============================================================
echo BUILD CONCLUIDA: %~dp0Build\GeoEditor.exe
echo ============================================================
exit /b 0

:java_missing
echo.
echo ERRO: Java JDK 25 nao foi encontrado no PATH.
echo Instale ou configure Java, javac e jar antes de continuar.
exit /b 1

:failed
echo.
echo ERRO: a build foi interrompida. Verifique as mensagens acima.
exit /b 1
