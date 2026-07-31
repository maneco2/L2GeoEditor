[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$version = '25.0.4'
$root = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $root 'tools\openjfx'
$sdkRoot = Join-Path $toolRoot "javafx-sdk-$version"
$archive = Join-Path $toolRoot "openjfx-$version`_windows-x64_bin-sdk.zip"
$download = "https://download2.gluonhq.com/openjfx/$version/openjfx-$version`_windows-x64_bin-sdk.zip"

if (Test-Path -LiteralPath (Join-Path $sdkRoot 'lib\javafx.controls.jar')) {
    Write-Host "OpenJFX $version is already available at $sdkRoot" -ForegroundColor Green
    exit 0
}

New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null
Invoke-WebRequest -UseBasicParsing -Uri $download -OutFile $archive
Expand-Archive -LiteralPath $archive -DestinationPath $toolRoot -Force
Remove-Item -LiteralPath $archive -Force

if (-not (Test-Path -LiteralPath (Join-Path $sdkRoot 'lib\javafx.controls.jar'))) {
    throw "OpenJFX SDK extraction did not produce the expected modules."
}

Write-Host "OpenJFX $version installed locally for GeoEditor." -ForegroundColor Green
