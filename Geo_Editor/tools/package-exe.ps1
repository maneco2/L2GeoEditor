[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'build'
$jar = Join-Path $build 'GeoEditor.jar'
$exe = Join-Path $build 'GeoEditor.exe'
$logs = Join-Path $build 'logs'
$config = Join-Path $PSScriptRoot 'launch4j.xml'
$launch4jHome = Join-Path $PSScriptRoot 'launch4j'
$launch4jJar = Join-Path $launch4jHome 'launch4j-3.50.jar'
$launch4jLib = Join-Path $launch4jHome 'lib\*'
$buildScript = Join-Path $PSScriptRoot 'build-fast.ps1'

Write-Host '== GeoEditor portable EXE build ==' -ForegroundColor Cyan
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $buildScript
if ($LASTEXITCODE -ne 0) {
    throw "GeoEditor build failed with exit code $LASTEXITCODE."
}
if (-not (Test-Path -LiteralPath $jar)) {
    throw 'GeoEditor.jar was not generated.'
}
if (-not (Test-Path -LiteralPath $launch4jJar)) {
    throw 'Launch4j 3.50 is required. Extract it locally to tools\launch4j (this toolchain is intentionally ignored by Git).'
}
if (-not (Test-Path -LiteralPath (Join-Path $launch4jHome 'bin\windres.exe'))) {
    throw 'The local tools\launch4j directory is incomplete: bin\windres.exe was not found.'
}

if (Test-Path -LiteralPath $exe) {
    Remove-Item -LiteralPath $exe -Force
}

$classPath = "$launch4jJar;$launch4jLib"
& java -cp $classPath net.sf.launch4j.Main $config
if ($LASTEXITCODE -ne 0) {
    throw "Launch4j failed with exit code $LASTEXITCODE."
}
if (-not (Test-Path -LiteralPath $exe)) {
    throw 'GeoEditor.exe was not generated.'
}

# The temporary JAR is embedded in the EXE. Runtime data, libraries and native DLLs remain external.
Remove-Item -LiteralPath $jar -Force
if (Test-Path -LiteralPath $logs) {
    Remove-Item -LiteralPath $logs -Recurse -Force
}

$versionInfo = (Get-Item -LiteralPath $exe).VersionInfo
Write-Host 'Portable application generated successfully.' -ForegroundColor Green
Write-Host "Output: $exe"
Write-Host "Version: $($versionInfo.ProductVersion)"
Write-Host 'External runtime folders preserved: build\libs and build\data'
Write-Host 'Runtime logs removed from the final portable build'
