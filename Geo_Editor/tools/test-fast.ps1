[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $root 'Build\classes-test'
. (Join-Path $PSScriptRoot 'core-source-files.ps1')

if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}

try {
    New-Item -ItemType Directory -Path $classes -Force | Out-Null

	$mainSources = @(Get-GeoEditorCoreSourceFiles -Root $root)
	$testSources = @(
		Get-ChildItem -LiteralPath (Join-Path $root 'tests\com\geo\core') -Filter '*.java' -File
	)
	$testSources += @(
		'GeoBlockFormatTest.java',
		'GeoEngineTest.java',
		'RealGeodataFixtureTest.java',
		'RegionLoadManagerTest.java',
		'RegionStreamingPolicyTest.java',
		'RegionWindowPlanTest.java',
		'SelectionNavigationPolicyTest.java'
	) | ForEach-Object { Join-Path $root "tests\com\geo\$_" }
	$testSources += @(
		(Join-Path $root 'tests\com\util\PerformanceMetricsTest.java'),
		(Join-Path $root 'tests\com\util\WindowsProcessMemoryTest.java')
	)
	$testSources = @($testSources | ForEach-Object {
		if ($_ -is [System.IO.FileInfo]) { $_.FullName } else { $_ }
	})
	& javac -encoding UTF-8 --release 25 -d $classes ($mainSources + $testSources)
    if ($LASTEXITCODE -ne 0) {
        throw "Test compilation failed with exit code $LASTEXITCODE."
    }

	$testClasses = @(
        'com.geo.GeoEngineTest',
        'com.geo.core.GeoCoordinatesTest',
        'com.geo.core.CameraStateTest',
        'com.geo.core.RegionSnapshotLoaderTest',
        'com.geo.core.EditableRegionSessionTest',
        'com.geo.GeoBlockFormatTest',
		'com.geo.RealGeodataFixtureTest',
        'com.geo.RegionLoadManagerTest',
	'com.geo.SelectionNavigationPolicyTest',
		'com.geo.RegionStreamingPolicyTest',
	'com.geo.RegionWindowPlanTest',
	'com.util.PerformanceMetricsTest',
	'com.util.WindowsProcessMemoryTest'
    )

    foreach ($testClass in $testClasses) {
		& java '--enable-native-access=ALL-UNNAMED' -cp $classes $testClass
        if ($LASTEXITCODE -ne 0) {
            throw "$testClass failed with exit code $LASTEXITCODE."
        }
    }
}
finally {
    if (Test-Path -LiteralPath $classes) {
        Remove-Item -LiteralPath $classes -Recurse -Force
    }
}

Write-Host 'Tests completed and temporary classes removed.' -ForegroundColor Green
