[CmdletBinding()]
param([switch]$Installer)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'build'
$inputDir = Join-Path $build '_jpackage-input'
$outputDir = Join-Path $build 'package'
$jar = Join-Path $build 'GeoEditor.jar'

if (-not (Test-Path -LiteralPath $jar)) { throw 'GeoEditor.jar was not found. Run BuildEditor.bat first.' }
if (Test-Path -LiteralPath $inputDir) { Remove-Item -LiteralPath $inputDir -Recurse -Force }
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination $inputDir -Force
Copy-Item -LiteralPath (Join-Path $build 'libs') -Destination $inputDir -Recurse -Force
Copy-Item -LiteralPath (Join-Path $build 'data') -Destination $inputDir -Recurse -Force

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$args = @('--type', 'app-image', '--name', 'GeoEditor', '--input', $inputDir,
    '--main-jar', 'GeoEditor.jar', '--main-class', 'com.GeoEditor', '--dest', $outputDir,
    '--app-version', '2.0.3.41',
    '--description', 'Lineage II Geodata Editor',
    '--vendor', 'Maneco2',
	'--copyright', 'GNU General Public License v3 or later',
	'--java-options', '-XX:+UseG1GC',
	'--java-options', '-XX:+ExplicitGCInvokesConcurrent',
	'--java-options', '-XX:MinHeapFreeRatio=10',
	'--java-options', '-XX:MaxHeapFreeRatio=20',
	'--java-options', '-XX:MaxRAMPercentage=75.0',
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--java-options', '--add-exports=java.base/java.lang=ALL-UNNAMED',
    '--java-options', '--add-exports=java.desktop/sun.awt=ALL-UNNAMED',
    '--java-options', '--add-exports=java.desktop/sun.java2d=ALL-UNNAMED')
if ($Installer) { $args[1] = 'exe' }
& jpackage @args
$exitCode = $LASTEXITCODE
Remove-Item -LiteralPath $inputDir -Recurse -Force
if ($exitCode -ne 0) { throw "jpackage failed with exit code $exitCode." }
Write-Host "Windows application generated at: $outputDir" -ForegroundColor Green
