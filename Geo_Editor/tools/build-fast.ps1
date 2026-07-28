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
$manifest = Join-Path $sourceRoot 'MANIFEST.MF'

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
    throw 'No Java source files were found.'
}

Write-Host "Compiling $($sources.Count) Java files..." -ForegroundColor Yellow
& $javac.Source -encoding UTF-8 --release 25 -cp (Join-Path $libs '*') -d $classes $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE."
}

$jarFile = Join-Path $build 'GeoEditor.jar'
if (Test-Path -LiteralPath $jarFile) {
    try {
        Remove-Item -LiteralPath $jarFile -Force -ErrorAction Stop
    } catch {
        throw "Unable to replace build\GeoEditor.jar. Close GeoEditor and run the build again."
    }
}
Write-Host 'Creating GeoEditor.jar...' -ForegroundColor Yellow
& $jar.Source --create --file $jarFile --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE."
}

Remove-Item -LiteralPath $classes -Recurse -Force

Copy-Item -LiteralPath $libs -Destination $build -Recurse -Force
New-Item -ItemType Directory -Path $assets -Force | Out-Null
$buildData = Join-Path $build 'data'
New-Item -ItemType Directory -Path $buildData -Force | Out-Null
Get-ChildItem -LiteralPath $buildData -Force | Where-Object {
    $_.Name -ne 'geodata'
} | ForEach-Object {
    $resourcePath = [System.IO.Path]::GetFullPath($_.FullName)
    $expectedRoot = [System.IO.Path]::GetFullPath($buildData) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resourcePath.StartsWith($expectedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean an unexpected runtime resource path: $resourcePath"
    }
    Remove-Item -LiteralPath $resourcePath -Recurse -Force
}
Get-ChildItem -LiteralPath $assets -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $buildData -Recurse -Force
}

Write-Host 'Build completed without ZIP packaging.' -ForegroundColor Green
Write-Host "Output: $build"
Write-Host 'The existing build\data\geodata directory was preserved.'
