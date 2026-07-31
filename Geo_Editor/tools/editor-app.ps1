[CmdletBinding()]
param(
	[switch]$Run,
	[switch]$SmokeTest,
	[switch]$StartupSmokeTest,
	[switch]$ConfigSmokeTest,
	[switch]$ConfigApplySmokeTest,
	[switch]$AboutSmokeTest,
	[switch]$MapSelectorSmokeTest,
	[switch]$SkyStyleSmoke,
	[switch]$SkyMotionSmoke,
	[switch]$NeighborStyleSmoke,
	[switch]$NeighborResizeSmoke,
	[switch]$LightThemeSmoke,
	[string]$RegionSmoke = '',
	[string]$GeodataRoot = '',
	[ValidateSet(0, 8, 24, 48, 80, 120)]
	[int]$StreamingRegions = 0
)

$ErrorActionPreference = 'Stop'
$javaFxVersion = '25.0.4'
$lwjglVersion = '3.4.1'
$root = Split-Path -Parent $PSScriptRoot
$appBuild = Join-Path $root 'Build'
$staging = Join-Path $appBuild '.staging'
$coreClasses = Join-Path $staging 'core-classes'
$appClasses = Join-Path $staging 'app-classes'
$coreJar = Join-Path $staging 'GeoEditorCore.jar'
$modulePath = Join-Path $root "tools\openjfx\javafx-sdk-$javaFxVersion\lib"
$lwjglPath = Join-Path $root "tools\lwjgl\$lwjglVersion"
. (Join-Path $PSScriptRoot 'core-source-files.ps1')
$appSourceRoot = Join-Path $root 'src\main\java\com\geo\editor'
$dataRoot = Join-Path $appBuild 'data'
$activeGeodataRoot = if ($GeodataRoot) {
	[System.IO.Path]::GetFullPath($GeodataRoot)
} else {
	Join-Path $dataRoot 'geodata'
}
$region = Join-Path $activeGeodataRoot '23_18.l2j'
$mainClass = 'com.geo.editor.GeoEditorApplication'
$executeApplication = $Run -or $SmokeTest -or $StartupSmokeTest -or $ConfigSmokeTest `
	-or $ConfigApplySmokeTest -or $AboutSmokeTest -or $MapSelectorSmokeTest `
	-or $SkyStyleSmoke -or $SkyMotionSmoke -or $NeighborStyleSmoke `
	-or $NeighborResizeSmoke -or $LightThemeSmoke -or [bool]$RegionSmoke `
	-or $StreamingRegions -gt 0

if (-not (Test-Path -LiteralPath (Join-Path $modulePath 'javafx.controls.jar'))) {
    throw 'OpenJFX dependencies are missing. Run tools\setup-javafx.ps1.'
}
if (-not (Test-Path -LiteralPath (Join-Path $lwjglPath "lwjgl-$lwjglVersion.jar"))) {
    throw 'LWJGL dependencies are missing. Run tools\setup-lwjgl.ps1.'
}
if ($RegionSmoke) {
	$region = Join-Path $activeGeodataRoot "$RegionSmoke.l2j"
}
if (($SmokeTest -or $RegionSmoke) -and -not (Test-Path -LiteralPath $region)) {
    throw "Required test geodata is missing: $region"
}

if (Test-Path -LiteralPath $staging) {
    Remove-Item -LiteralPath $staging -Recurse -Force
}
New-Item -ItemType Directory -Path $coreClasses -Force | Out-Null
New-Item -ItemType Directory -Path $appClasses -Force | Out-Null

try {
$coreSources = Get-GeoEditorCoreSourceFiles -Root $root
& javac -encoding UTF-8 --release 25 -d $coreClasses $coreSources
if ($LASTEXITCODE -ne 0) {
    throw "Core compilation failed with exit code $LASTEXITCODE."
}
& jar --create --file $coreJar -C $coreClasses .
if ($LASTEXITCODE -ne 0) {
    throw "Core JAR creation failed with exit code $LASTEXITCODE."
}

$appSources = @(Get-ChildItem -LiteralPath $appSourceRoot -Filter '*.java' -Recurse |
    Select-Object -ExpandProperty FullName)
$compileClassPath = "$coreJar;$(Join-Path $lwjglPath '*')"
& javac -encoding UTF-8 --release 25 --module-path $modulePath `
    --add-modules javafx.controls -cp $compileClassPath `
    -d $appClasses $appSources
if ($LASTEXITCODE -ne 0) {
    throw "GeoEditor application compilation failed with exit code $LASTEXITCODE."
}

Write-Host "GeoEditor compiled in Build with JavaFX $javaFxVersion and LWJGL $lwjglVersion." -ForegroundColor Green

if ($executeApplication) {
    $arguments = @('--module-path', $modulePath, '--add-modules', 'javafx.controls',
        '--enable-native-access=javafx.graphics,ALL-UNNAMED')
    if ($SmokeTest) {
        $arguments += '-Dgeoeditor.autocloseMillis=8000'
        $arguments += "-Dgeoeditor.config=$(Join-Path $staging 'smoke-GeoEditor.xml')"
        $arguments += '-Dgeoeditor.cameraSmoke=true'
		$arguments += '-Dgeoeditor.selectionSmoke=true'
		$arguments += '-Dgeoeditor.cellLayersThemeSmoke=true'
		$arguments += '-Dgeoeditor.inputSmoke=true'
        $arguments += '-Dgeoeditor.metricsSmoke=true'
        $arguments += '-Dgeoeditor.streamingSmoke=true'
        $arguments += '-Dgeoeditor.circularGrid=true'
		$arguments += '-Dgeoeditor.typePickingSmoke=true'
		$arguments += '-Dgeoeditor.visibilityPlanSmoke=true'
		$arguments += '-Dgeoeditor.visibleGridRange=96'
		$arguments += '-Dgeoeditor.sharedGridRenderer=true'
		$arguments += '-Dgeoeditor.sharedGridSmoke=true'
		$arguments += '-Dgeoeditor.range96BenchmarkSmoke=true'
		$arguments += '-Dgeoeditor.mapSelectorSmoke=true'
		$arguments += '-Dgeoeditor.graphicsSmoke=true'
		$arguments += '-Dgeoeditor.graphicsComparisonSmoke=true'
		$arguments += '-Dgeoeditor.gridModesSmoke=true'
		$arguments += '-Dgeoeditor.transparencySmoke=true'
		$arguments += '-Dgeoeditor.outlineSmoke=true'
		$arguments += "-Dgeoeditor.frameOutput=$(Join-Path $staging 'shared-grid-smoke.png')"
    }
	if ($StartupSmokeTest) {
		$arguments += '-Dgeoeditor.autocloseMillis=4000'
		$arguments += '-Dgeoeditor.startupSmoke=true'
		$arguments += '-Dgeoeditor.startupSplashSmoke=true'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'startup-smoke-GeoEditor.xml')"
	}
	if ($AboutSmokeTest) {
		$arguments += '-Dgeoeditor.aboutSmoke=true'
		$arguments += '-Dgeoeditor.autocloseMillis=4000'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'about-smoke-GeoEditor.xml')"
	}
	if ($MapSelectorSmokeTest) {
		$arguments += '-Dgeoeditor.mapSelectorSmoke=true'
		$arguments += '-Dgeoeditor.autocloseMillis=2500'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'map-selector-smoke-GeoEditor.xml')"
	}
	if (-not $Run) {
		$arguments += '-Dgeoeditor.lifecycleSmoke=true'
	}
	if ($SkyStyleSmoke) {
		$arguments += '-Dgeoeditor.skyStyleSmoke=true'
		$arguments += '-Dgeoeditor.autocloseMillis=30000'
		$arguments += '-Dgeoeditor.visibleGridRange=96'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'sky-style-GeoEditor.xml')"
		$arguments += "-Dgeoeditor.region=$region"
		$arguments += "-Dgeoeditor.skyStyleOutput=$(Join-Path $staging 'sky-comparison')"
	}
	if ($SkyMotionSmoke) {
		$arguments += '-Dgeoeditor.skyMotionSmoke=true'
		$arguments += '-Dgeoeditor.autocloseMillis=12000'
		$arguments += '-Dgeoeditor.visibleGridRange=96'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'sky-motion-GeoEditor.xml')"
		$arguments += "-Dgeoeditor.region=$region"
	}
	if ($StreamingRegions -gt 0) {
		$streamingConfig = Join-Path $staging "streaming-$StreamingRegions-GeoEditor.xml"
		$streamingTimeout = if ($StreamingRegions -le 24) { 30000 } else { 90000 }
		$arguments += "-Dgeoeditor.autocloseMillis=$streamingTimeout"
		$arguments += '-Dgeoeditor.streamingSmoke=true'
		if ($StreamingRegions -eq 120) {
			$arguments += '-Dgeoeditor.longTraversalSmoke=true'
		}
		$arguments += "-Dgeoeditor.streamingRegions=$StreamingRegions"
		$arguments += '-Dgeoeditor.visibleGridRange=96'
		$arguments += "-Dgeoeditor.config=$streamingConfig"
		$arguments += "-Dgeoeditor.region=$region"
	}
	if ($ConfigSmokeTest) {
		$arguments += '-Dgeoeditor.autocloseMillis=2500'
		$arguments += '-Dgeoeditor.configSmoke=true'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'config-smoke-GeoEditor.xml')"
	}
	if ($ConfigApplySmokeTest) {
		$arguments += '-Dgeoeditor.autocloseMillis=12000'
		$arguments += '-Dgeoeditor.configApplySmoke=true'
		$arguments += '-Dgeoeditor.visibleGridRange=96'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'config-apply-smoke-GeoEditor.xml')"
		$arguments += "-Dgeoeditor.region=$region"
		$arguments += "-Dgeoeditor.configApplyOutput=$(Join-Path $staging 'config-apply-smoke.png')"
	}
	if ($NeighborStyleSmoke) {
		$arguments += '-Dgeoeditor.autocloseMillis=30000'
		$arguments += '-Dgeoeditor.neighborStyleSmoke=true'
		$arguments += '-Dgeoeditor.visibleGridRange=96'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'neighbor-style-GeoEditor.xml')"
		$arguments += "-Dgeoeditor.region=$region"
		$arguments += "-Dgeoeditor.neighborStyleOutput=$(Join-Path $staging 'neighbor-styles')"
	}
	if ($NeighborResizeSmoke) {
		$arguments += '-Dgeoeditor.autocloseMillis=45000'
		$arguments += '-Dgeoeditor.neighborResizeSmoke=true'
		$arguments += '-Dgeoeditor.visibleGridRange=96'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'neighbor-resize-GeoEditor.xml')"
		$arguments += "-Dgeoeditor.region=$region"
	}
	if ($LightThemeSmoke) {
		$arguments += '-Dgeoeditor.autocloseMillis=5000'
		$arguments += '-Dgeoeditor.theme=LIGHT'
		$arguments += '-Dgeoeditor.visibleGridRange=96'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging 'light-theme-GeoEditor.xml')"
		$arguments += "-Dgeoeditor.region=$region"
		$arguments += "-Dgeoeditor.frameOutput=$(Join-Path $staging 'light-theme-smoke.png')"
		$arguments += '-Dgeoeditor.regionVisualSmoke=true'
	}
	if ($RegionSmoke) {
		$arguments += '-Dgeoeditor.autocloseMillis=5000'
		$arguments += '-Dgeoeditor.regionVisualSmoke=true'
		$arguments += '-Dgeoeditor.visibleGridRange=96'
		$arguments += "-Dgeoeditor.config=$(Join-Path $staging "region-$RegionSmoke-GeoEditor.xml")"
		$arguments += "-Dgeoeditor.region=$region"
		$arguments += "-Dgeoeditor.frameOutput=$(Join-Path $staging "region-$RegionSmoke.png")"
	}
	if ($SmokeTest) {
		$arguments += "-Dgeoeditor.region=$region"
	}
	$arguments += "-Dgeoeditor.geodata=$activeGeodataRoot"
    $arguments += "-Dgeoeditor.data=$dataRoot"
    $arguments += @('-cp', "$appClasses;$compileClassPath", $mainClass)
    & java @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "GeoEditor application failed with exit code $LASTEXITCODE."
    }
}
}
finally {
	if ($executeApplication -and (Test-Path -LiteralPath $staging)) {
		Remove-Item -LiteralPath $staging -Recurse -Force
	}
}
