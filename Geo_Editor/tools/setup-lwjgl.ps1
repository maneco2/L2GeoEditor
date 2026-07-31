[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$version = '3.4.1'
$root = Split-Path -Parent $PSScriptRoot
$target = Join-Path $root "tools\lwjgl\$version"
$repository = 'https://repo.maven.apache.org/maven2/org/lwjgl'
$modules = @('lwjgl', 'lwjgl-glfw', 'lwjgl-opengl')

New-Item -ItemType Directory -Path $target -Force | Out-Null

function Receive-Artifact {
    param(
        [string]$Module,
        [string]$FileName
    )

    $uri = "$repository/$Module/$version/$FileName"
    $path = Join-Path $target $FileName
    if (-not (Test-Path -LiteralPath $path)) {
        Invoke-WebRequest -UseBasicParsing -Uri $uri -OutFile $path
    }
    $expected = (Invoke-WebRequest -UseBasicParsing -Uri "$uri.sha256").Content.Trim().ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        throw "SHA-256 mismatch for $FileName."
    }
}

foreach ($module in $modules) {
    Receive-Artifact -Module $module -FileName "$module-$version.jar"
    Receive-Artifact -Module $module -FileName "$module-$version-natives-windows.jar"
}

$files = @(Get-ChildItem -LiteralPath $target -Filter '*.jar' -File)
if ($files.Count -ne 6) {
    throw "Expected 6 LWJGL artifacts, found $($files.Count)."
}

$bytes = ($files | Measure-Object Length -Sum).Sum
Write-Host "LWJGL $version installed locally for GeoEditor." -ForegroundColor Green
Write-Host "Artifacts: $($files.Count), bytes: $bytes"
