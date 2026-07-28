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

    $mainSources = @(Get-ChildItem -LiteralPath $sources -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
    $testSources = @(Get-ChildItem -LiteralPath $tests -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
    & javac -encoding UTF-8 --release 25 -cp (Join-Path $root 'libs\*') -d $classes ($mainSources + $testSources)
    if ($LASTEXITCODE -ne 0) {
        throw "Test compilation failed with exit code $LASTEXITCODE."
    }

	$testClasses = @(
		'com.ConfigTest',
        'com.geo.GeoEngineTest',
        'com.geo.GeoBlockFormatTest',
        'com.geo.UnrHeaderTest',
        'com.geo.MapSelectorStateTest',
        'com.geo.WorldMapPanelTest',
        'com.geo.RegionLoadManagerTest',
    'com.geo.RegionDiscardPolicyTest',
	'com.geo.SelectionNavigationPolicyTest',
		'com.geo.RegionStreamingPolicyTest',
		'com.geo.RegionWindowPlanTest',
		'com.util.PerformanceMetricsTest',
		'com.util.WindowsProcessMemoryTest',
        'com.jogl.CellGridMaskTest',
    'com.jogl.TerrainBuilderXDetailTest',
    'com.jogl.RendererBatchPolicyTest',
    'com.jogl.MiniMapLayoutTest',
        'com.jogl.AWTInputTest',
        'com.util.TaskExecutorTest',
		'com.swing.SplashTest',
		'com.swing.NeighborLoadingOverlayTest',
		'com.jogl.shader.uniform.GLUniformVec2ivTest'
    )

    foreach ($testClass in $testClasses) {
		& java '--enable-native-access=ALL-UNNAMED' -cp "$classes;$(Join-Path $root 'libs\*')" $testClass
        if ($LASTEXITCODE -ne 0) {
            throw "$testClass failed with exit code $LASTEXITCODE."
        }
    }

	& java '--enable-native-access=ALL-UNNAMED' '-Djava.awt.headless=true' -cp "$classes;$(Join-Path $root 'libs\*')" com.swing.ThemeManagerTest
    if ($LASTEXITCODE -ne 0) {
        throw "com.swing.ThemeManagerTest failed with exit code $LASTEXITCODE."
    }
}
finally {
    if (Test-Path -LiteralPath $classes) {
        Remove-Item -LiteralPath $classes -Recurse -Force
    }
}

Write-Host 'Tests completed and temporary classes removed.' -ForegroundColor Green
