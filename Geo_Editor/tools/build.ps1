[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'Build'
$logDirectory = Join-Path $root 'Artifacts\Logs'
$appExe = Join-Path $build 'GeoEditor.exe'
$javaFxVersion = '25.0.4'
$lwjglVersion = '3.4.1'
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$log = Join-Path $logDirectory "Build_$stamp.log"

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

function Write-LogLine([string]$Text) {
    Add-Content -LiteralPath $log -Value $Text -Encoding UTF8
}

function Get-RelativePath([string]$Path) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $rootPrefix = $root.TrimEnd('\') + '\'
    if ($fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $fullPath.Substring($rootPrefix.Length)
    }
    return $fullPath
}

function Format-Size([long]$Bytes) {
    if ($Bytes -ge 1MB) {
        return ('{0:0.0} MB' -f ($Bytes / 1MB))
    }
    return ('{0:0} KB' -f ($Bytes / 1KB))
}

function Invoke-Stage([string]$Title, [string]$ScriptPath) {
    Write-LogLine "[$Title]"
    $started = Get-Date
    $output = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ScriptPath *>&1)
    $exitCode = $LASTEXITCODE
    if ($output.Count -gt 0) {
        $output | Out-File -LiteralPath $log -Append -Encoding UTF8
    }
    $elapsed = ((Get-Date) - $started).TotalSeconds.ToString('0.0')
    if ($exitCode -ne 0) {
        Write-Host "[FAIL] $Title (exit $exitCode)" -ForegroundColor Red
        throw "$Title failed with exit code $exitCode."
    }
    return [pscustomobject]@{
        Output = @($output)
        Elapsed = $elapsed
    }
}

function Get-OutputLines([object[]]$Output) {
    return @($Output | ForEach-Object {
        if ($null -ne $_) {
            $_.ToString().Trim()
        }
    } | Where-Object { $_ })
}

function Show-TestSummary([object[]]$Output) {
    $lines = @(Get-OutputLines $Output | Where-Object {
        $_ -match '(?i)(Test OK|Test passed\.|Test OK -)'
    })
    foreach ($line in $lines) {
        Write-Host " - $line" -ForegroundColor Gray
    }
    return $lines.Count
}

function Show-BuildSummary([object[]]$Output) {
    foreach ($line in @(Get-OutputLines $Output)) {
        if ($line -match '^GeoEditor compiled in ') {
            Write-Host " - Compilacao: JavaFX $javaFxVersion + LWJGL $lwjglVersion" -ForegroundColor Gray
        } elseif ($line -match '^launch4j: (Compiling resources|Linking|Wrapping)$') {
            Write-Host " - $line" -ForegroundColor Gray
        } elseif ($line -match 'Successfully created ') {
            Write-Host ' - EXE: Build\GeoEditor.exe gerado' -ForegroundColor Gray
        } elseif ($line -match '^Build generated successfully\.') {
            Write-Host ' - Pacote runtime: JavaFX + LWJGL + core' -ForegroundColor Gray
        }
    }
}

try {
    Write-Host '========================================' -ForegroundColor DarkCyan
    Write-Host 'GeoEditor 2.0.4.31' -ForegroundColor White
    Write-Host '========================================' -ForegroundColor DarkCyan
    Write-LogLine "GeoEditor 2.0.4.31 - $stamp"

    foreach ($command in @('java', 'javac', 'jar')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "Required command not found: $command"
        }
    }
    $javaVersion = (& java --version 2>&1 | Select-Object -First 1).ToString()
    Write-LogLine "Java: $javaVersion"

    Write-Host '[1/5] Ambiente' -ForegroundColor Cyan
    Write-Host " - Raiz: Geo_Editor" -ForegroundColor Gray
    Write-Host " - $javaVersion" -ForegroundColor Gray
    Write-Host " - JavaFX: $javaFxVersion" -ForegroundColor Gray
    Write-Host " - LWJGL: $lwjglVersion" -ForegroundColor Gray
    Write-Host ' - Saida: Build\GeoEditor.exe' -ForegroundColor Gray
    Write-Host " - Log: $((Get-RelativePath $log))" -ForegroundColor Gray
    Write-Host '[OK] Ambiente' -ForegroundColor Green

    Write-Host '[2/5] Testes' -ForegroundColor Cyan
    $testStage = Invoke-Stage 'Testes' (Join-Path $PSScriptRoot 'test-fast.ps1')
    $testCount = Show-TestSummary $testStage.Output
    Write-Host "[OK] Testes: $testCount ($($testStage.Elapsed) s)" -ForegroundColor Green

    Write-Host '[3/5] Build' -ForegroundColor Cyan
    $buildStage = Invoke-Stage 'Build' (Join-Path $PSScriptRoot 'package-exe.ps1')
    Show-BuildSummary $buildStage.Output | Out-Null
    Write-Host "[OK] Build ($($buildStage.Elapsed) s)" -ForegroundColor Green

    if (-not (Test-Path -LiteralPath $appExe)) {
        throw "Output not found: $appExe"
    }
    $warningCount = @(Select-String -LiteralPath $log -Pattern '(?i)^\s*(WARNING:|Note:)').Count
    $appFile = Get-Item -LiteralPath $appExe
    $version = $appFile.VersionInfo.ProductVersion

    Write-Host '[4/5] Verificacao' -ForegroundColor Cyan
    Write-Host " - Arquivo: Build\GeoEditor.exe" -ForegroundColor Gray
    Write-Host " - Tamanho: $(Format-Size $appFile.Length)" -ForegroundColor Gray
    Write-Host " - Versao: $version" -ForegroundColor Gray
    if ($warningCount -eq 0) {
        Write-Host '[OK] Avisos: 0' -ForegroundColor Green
    } else {
        Write-Host "[WARN] Avisos externos: $warningCount (log)" -ForegroundColor Yellow
    }
    Write-Host '[OK] EXE verificado' -ForegroundColor Green

    Write-Host '[5/5] Final' -ForegroundColor Cyan
    Write-Host "[OK] Pronto: $version" -ForegroundColor Green
    Write-Host "Log: $((Get-RelativePath $log))" -ForegroundColor Gray
    Write-Host '========================================' -ForegroundColor DarkCyan
    Write-Host 'BUILD FINALIZADA COM SUCESSO' -ForegroundColor Green
    Write-LogLine "Result: OK; tests=$testCount; warnings=$warningCount; app=$appExe"
    exit 0
} catch {
    Write-Host '[FAIL] Build interrompida.' -ForegroundColor Red
    Write-Host "Log: $((Get-RelativePath $log))" -ForegroundColor Gray
    Write-LogLine "Result: FAIL; $($_.Exception.Message)"
    exit 1
}
