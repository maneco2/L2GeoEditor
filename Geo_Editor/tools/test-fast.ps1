[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $root 'build\classes-test'
$sources = Join-Path $root 'java'
$tests = Join-Path $root 'tests'

if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}
try {
    New-Item -ItemType Directory -Path $classes -Force | Out-Null

    $mainSources = @(Get-ChildItem $sources -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
    $testSources = @(Get-ChildItem $tests -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
    & javac -encoding UTF-8 --release 25 -cp (Join-Path $root 'libs\*') -d $classes ($mainSources + $testSources)
    if ($LASTEXITCODE -ne 0) { throw "Compilação dos testes falhou com código $LASTEXITCODE." }

    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.geo.GeoEngineTest
    if ($LASTEXITCODE -ne 0) { throw "Testes falharam com código $LASTEXITCODE." }
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.geo.GeoBlockFormatTest
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.geo.UnrHeaderTest
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.geo.MapSelectorStateTest
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.geo.WorldMapPanelTest
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.geo.GeoDataOverviewTest
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.geo.RegionLoadManagerTest
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.util.PerformanceMetricsTest
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.jogl.CellGridMaskTest
    & java -cp "$classes;$(Join-Path $root 'libs\*')" com.util.TaskExecutorTest
    if ($LASTEXITCODE -ne 0) { throw "Testes de formato falharam com código $LASTEXITCODE." }
}
finally {
    if (Test-Path -LiteralPath $classes) {
        Remove-Item -LiteralPath $classes -Recurse -Force
    }
}

Write-Host 'Testes concluídos e classes temporárias removidas.' -ForegroundColor Green
