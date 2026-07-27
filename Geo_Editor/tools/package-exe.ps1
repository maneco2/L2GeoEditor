[CmdletBinding()]
param([switch]$Installer)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'build'
$inputDir = Join-Path $build '_jpackage-input'
$outputDir = Join-Path $build 'package'
$jar = Join-Path $build 'GeoEditor.jar'

if (-not (Test-Path -LiteralPath $jar)) { throw 'GeoEditor.jar não encontrado. Execute BuildEditor.bat primeiro.' }
if (Test-Path -LiteralPath $inputDir) { Remove-Item -LiteralPath $inputDir -Recurse -Force }
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination $inputDir -Force
Copy-Item -LiteralPath (Join-Path $build 'libs') -Destination $inputDir -Recurse -Force
Copy-Item -LiteralPath (Join-Path $build 'data') -Destination $inputDir -Recurse -Force

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$args = @('--type', 'app-image', '--name', 'GeoEditor', '--input', $inputDir,
    '--main-jar', 'GeoEditor.jar', '--main-class', 'com.GeoEditor', '--dest', $outputDir,
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--java-options', '--add-exports=java.base/java.lang=ALL-UNNAMED',
    '--java-options', '--add-exports=java.desktop/sun.awt=ALL-UNNAMED',
    '--java-options', '--add-exports=java.desktop/sun.java2d=ALL-UNNAMED')
if ($Installer) { $args[1] = 'exe' }
& jpackage @args
$exitCode = $LASTEXITCODE
Remove-Item -LiteralPath $inputDir -Recurse -Force
if ($exitCode -ne 0) { throw "jpackage falhou com código $exitCode." }
Write-Host "Pacote gerado em: $outputDir" -ForegroundColor Green
