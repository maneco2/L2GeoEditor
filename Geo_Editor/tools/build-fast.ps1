[CmdletBinding()]
param(
    [switch]$CleanClasses
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes-fast'
$libs = Join-Path $root 'libs'
$sourceRoot = Join-Path $root 'src'
$assets = Join-Path $sourceRoot 'assets'

Write-Host '== Geo Editor fast build ==' -ForegroundColor Cyan
$javac = Get-Command javac -ErrorAction Stop
$jar = Get-Command jar -ErrorAction Stop
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
$ErrorActionPreference = $previousErrorActionPreference
Write-Host "Java: $javaVersion"

if ($CleanClasses -and (Test-Path -LiteralPath $classes)) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}

New-Item -ItemType Directory -Path $build -Force | Out-Null
New-Item -ItemType Directory -Path $classes -Force | Out-Null

$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'java') -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
if ($sources.Count -eq 0) {
    throw 'Nenhum arquivo Java encontrado.'
}

Write-Host "Compilando $($sources.Count) arquivos..." -ForegroundColor Yellow
& $javac.Source -encoding UTF-8 --release 25 -cp (Join-Path $libs '*') -d $classes $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac falhou com codigo $LASTEXITCODE."
}

$legacyJar = Join-Path $build 'G3DEditor.jar'
if (Test-Path -LiteralPath $legacyJar) {
    Remove-Item -LiteralPath $legacyJar -Force
}
$jarFile = Join-Path $build 'GeoEditor.jar'
if (Test-Path -LiteralPath $jarFile) {
    try {
        Remove-Item -LiteralPath $jarFile -Force -ErrorAction Stop
    } catch {
        throw "Nao foi possivel substituir build\GeoEditor.jar. Feche o Geo Editor e execute o build novamente."
    }
}
Write-Host 'Gerando GeoEditor.jar...' -ForegroundColor Yellow
& $jar.Source --create --file $jarFile --main-class com.GeoEditor -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar falhou com codigo $LASTEXITCODE."
}

Remove-Item -LiteralPath $classes -Recurse -Force

$legacyLauncher = Join-Path $build 'G3DEditor.bat'
if (Test-Path -LiteralPath $legacyLauncher) {
    Remove-Item -LiteralPath $legacyLauncher -Force
}
Copy-Item -LiteralPath (Join-Path $sourceRoot 'GeoEditor.bat') -Destination (Join-Path $build 'GeoEditor.bat') -Force
$legacyConfig = Join-Path $build 'G3DEditor.ini'
$currentConfig = Join-Path $build 'GeoEditor.ini'
if ((Test-Path -LiteralPath $legacyConfig) -and -not (Test-Path -LiteralPath $currentConfig)) {
    Move-Item -LiteralPath $legacyConfig -Destination $currentConfig
} elseif (Test-Path -LiteralPath $legacyConfig) {
    Remove-Item -LiteralPath $legacyConfig -Force
}
Copy-Item -LiteralPath $libs -Destination $build -Recurse -Force
New-Item -ItemType Directory -Path $assets -Force | Out-Null
$buildData = Join-Path $build 'data'
New-Item -ItemType Directory -Path $buildData -Force | Out-Null
foreach ($resourceFolder in @('icon', 'shader', 'textures', 'MAPS')) {
    $buildResource = Join-Path $buildData $resourceFolder
    if (Test-Path -LiteralPath $buildResource) {
        Remove-Item -LiteralPath $buildResource -Recurse -Force
    }
}
Get-ChildItem -LiteralPath $assets -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $buildData -Recurse -Force
}

$oldZip = Join-Path $build 'G3DEditor.zip'
if (Test-Path -LiteralPath $oldZip) {
    Remove-Item -LiteralPath $oldZip -Force
}

Write-Host 'Build concluido sem ZIP.' -ForegroundColor Green
Write-Host "Saida: $build"
Write-Host 'A pasta build\data\geodata existente foi preservada.'
