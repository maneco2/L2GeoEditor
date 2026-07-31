[CmdletBinding()]
param(
    [string]$FlatRegion,
    [string]$MultilayerRegion
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$geodata = Join-Path $root 'Build\data\geodata'
$classes = Join-Path $root 'Build\classes-benchmark'
. (Join-Path $PSScriptRoot 'core-source-files.ps1')

if (-not (Test-Path -LiteralPath $geodata)) {
    throw 'Build\data\geodata was not found.'
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
	$sources = @(Get-GeoEditorCoreSourceFiles -Root $root)
	$benchmark = Join-Path $root 'tests\com\geo\RealRegionBenchmark.java'
	& javac -encoding UTF-8 --release 25 -d $classes ($sources + $benchmark)
    if ($LASTEXITCODE -ne 0) {
        throw "Benchmark compilation failed with exit code $LASTEXITCODE."
    }
	& java -cp $classes com.geo.RealRegionBenchmark $FlatRegion $MultilayerRegion
    if ($LASTEXITCODE -ne 0) {
        throw "Region benchmark failed with exit code $LASTEXITCODE."
    }
}
finally {
    if (Test-Path -LiteralPath $classes) {
        Remove-Item -LiteralPath $classes -Recurse -Force
    }
}
