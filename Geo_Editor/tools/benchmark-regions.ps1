[CmdletBinding()]
param(
    [string]$FlatRegion,
    [string]$MultilayerRegion
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$geodata = Join-Path $root 'build\data\geodata'
$classes = Join-Path $root 'build\classes-benchmark'

if (-not (Test-Path -LiteralPath $geodata)) {
    throw 'build\data\geodata was not found.'
}

$regions = @(Get-ChildItem -LiteralPath $geodata -Filter '*.l2j' -File | Sort-Object Length)
if ($regions.Count -lt 2) {
    throw 'At least two .l2j regions are required.'
}
if ([string]::IsNullOrWhiteSpace($FlatRegion)) {
    $FlatRegion = $regions[0].FullName
}
if ([string]::IsNullOrWhiteSpace($MultilayerRegion)) {
    $MultilayerRegion = $regions[-1].FullName
}

if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}

try {
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'java') -Filter '*.java' -Recurse |
        Select-Object -ExpandProperty FullName)
    $benchmark = Join-Path $root 'tests\com\geo\RealRegionBenchmark.java'
    & javac -encoding UTF-8 --release 25 -cp (Join-Path $root 'libs\*') -d $classes ($sources + $benchmark)
    if ($LASTEXITCODE -ne 0) {
        throw "Benchmark compilation failed with exit code $LASTEXITCODE."
    }
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.geo.RealRegionBenchmark $FlatRegion $MultilayerRegion
    if ($LASTEXITCODE -ne 0) {
        throw "Region benchmark failed with exit code $LASTEXITCODE."
    }
}
finally {
    if (Test-Path -LiteralPath $classes) {
        Remove-Item -LiteralPath $classes -Recurse -Force
    }
}
