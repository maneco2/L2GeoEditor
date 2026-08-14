[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$appBuild = Join-Path $root 'Build'
$staging = Join-Path $appBuild '.staging'
$appClasses = Join-Path $staging 'app-classes'
$stagedCoreJar = Join-Path $staging 'GeoEditorCore.jar'
$appJar = Join-Path $appBuild 'GeoEditor.jar'
$appExe = Join-Path $appBuild 'GeoEditor.exe'
$appLibs = Join-Path $appBuild 'libs'
$coreLibs = Join-Path $appLibs 'core'
$javaFxRuntime = Join-Path $appLibs 'javafx'
$lwjglRuntime = Join-Path $appLibs 'lwjgl'
$runtimeJavaFxJars = @('javafx.base.jar', 'javafx.controls.jar', 'javafx.graphics.jar')
$excludedJavaFxDlls = @('fxplugins.dll', 'glib-lite.dll', 'gstreamer-lite.dll',
    'jfxmedia.dll', 'jfxwebkit.dll')
$appData = Join-Path $appBuild 'data'
$appConfigs = Join-Path $appBuild 'configs'
$obsoleteVisualMinimap = Join-Path $appBuild '.visual-minimap.xml'
$manifest = Join-Path $root 'src\main\resources\META-INF\MANIFEST.MF'
$assets = Join-Path $root 'src\main\resources'
$javaFxLibs = Join-Path $root 'tools\openjfx\javafx-sdk-25.0.4\lib'
$javaFxBin = Join-Path $root 'tools\openjfx\javafx-sdk-25.0.4\bin'
$lwjglLibs = Join-Path $root 'tools\lwjgl\3.4.1'
$launch4jHome = Join-Path $PSScriptRoot 'launch4j'
$launch4jJar = Join-Path $launch4jHome 'launch4j-3.50.jar'
$launch4jLib = Join-Path $launch4jHome 'lib\*'
$launch4jConfig = Join-Path $PSScriptRoot 'launch4j.xml'

function Remove-ItemWithRetry([string]$Path, [switch]$Recurse) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    for ($attempt = 1; $attempt -le 6; $attempt++) {
        try {
            Remove-Item -LiteralPath $Path -Recurse:$Recurse -Force -ErrorAction Stop
            return
        } catch {
            if ($attempt -eq 6) {
                throw
            }
            Start-Sleep -Milliseconds (250 * $attempt)
        }
    }
}

function Sync-Directory([string]$Source, [string]$Destination) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    $sourcePrefix = [System.IO.Path]::GetFullPath($Source).TrimEnd('\') + '\'
    $destinationPrefix = [System.IO.Path]::GetFullPath($Destination).TrimEnd('\') + '\'
    $expected = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase)

    foreach ($file in Get-ChildItem -LiteralPath $Source -File -Recurse) {
        $relative = $file.FullName.Substring($sourcePrefix.Length)
        [void]$expected.Add($relative)
        $target = Join-Path $Destination $relative
        $targetDirectory = Split-Path -Parent $target
        New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
        $copy = -not (Test-Path -LiteralPath $target)
        if (-not $copy) {
            $current = Get-Item -LiteralPath $target
            $copy = $current.Length -ne $file.Length `
                -or $current.LastWriteTimeUtc -ne $file.LastWriteTimeUtc
        }
        if ($copy) {
            Copy-Item -LiteralPath $file.FullName -Destination $target -Force
        }
    }

    foreach ($file in Get-ChildItem -LiteralPath $Destination -File -Recurse) {
        $relative = $file.FullName.Substring($destinationPrefix.Length)
        if (-not $expected.Contains($relative)) {
            Remove-ItemWithRetry -Path $file.FullName
        }
    }
    Get-ChildItem -LiteralPath $Destination -Directory -Recurse |
        Sort-Object FullName -Descending | ForEach-Object {
            if (@(Get-ChildItem -LiteralPath $_.FullName -Force).Count -eq 0) {
                Remove-ItemWithRetry -Path $_.FullName
            }
        }
}

Write-Host '== GeoEditor build ==' -ForegroundColor Cyan

try {
if (Test-Path -LiteralPath $appLibs) {
    Remove-ItemWithRetry -Path $appLibs -Recurse
}
if (Test-Path -LiteralPath $appExe) {
    Remove-ItemWithRetry -Path $appExe
}
if (Test-Path -LiteralPath $appJar) {
    Remove-ItemWithRetry -Path $appJar
}
if (Test-Path -LiteralPath $obsoleteVisualMinimap) {
    Remove-Item -LiteralPath $obsoleteVisualMinimap -Force
}

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
    (Join-Path $PSScriptRoot 'editor-app.ps1') -KeepStaging
if ($LASTEXITCODE -ne 0) {
    throw "GeoEditor compilation failed with exit code $LASTEXITCODE."
}

New-Item -ItemType Directory -Path $coreLibs -Force | Out-Null
New-Item -ItemType Directory -Path $javaFxRuntime -Force | Out-Null
New-Item -ItemType Directory -Path $lwjglRuntime -Force | Out-Null
New-Item -ItemType Directory -Path $appConfigs -Force | Out-Null
New-Item -ItemType Directory -Path $appData -Force | Out-Null

foreach ($jarName in $runtimeJavaFxJars) {
    Copy-Item -LiteralPath (Join-Path $javaFxLibs $jarName) -Destination $javaFxRuntime -Force
}
Get-ChildItem -LiteralPath $javaFxBin -Filter '*.dll' | Where-Object {
    $_.Name -notin $excludedJavaFxDlls
} | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $javaFxRuntime -Force
}
Copy-Item -Path (Join-Path $lwjglLibs '*') -Destination $lwjglRuntime -Recurse -Force
Copy-Item -LiteralPath $stagedCoreJar `
    -Destination (Join-Path $coreLibs 'GeoEditorCore.jar') -Force

$preservedData = @('MAPS', 'geodata')
$assetEntries = @(Get-ChildItem -LiteralPath $assets -Force | Where-Object {
    $_.Name -ne 'META-INF'
})
$assetEntryNames = @($assetEntries | Select-Object -ExpandProperty Name)
Get-ChildItem -LiteralPath $appData -Force | Where-Object {
    $_.Name -notin $preservedData -and $_.Name -notin $assetEntryNames
} | ForEach-Object {
    Remove-ItemWithRetry -Path $_.FullName -Recurse
}
foreach ($entry in $assetEntries) {
    $destination = Join-Path $appData $entry.Name
    if ($entry.PSIsContainer) {
        Sync-Directory -Source $entry.FullName -Destination $destination
    } else {
        Copy-Item -LiteralPath $entry.FullName -Destination $destination -Force
    }
}

& jar --create --file $appJar --manifest $manifest -C $appClasses .
if ($LASTEXITCODE -ne 0) {
    throw "GeoEditor JAR creation failed with exit code $LASTEXITCODE."
}
$launch4jStdout = Join-Path $staging 'launch4j.stdout.log'
$launch4jStderr = Join-Path $staging 'launch4j.stderr.log'
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    & java -cp "$launch4jJar;$launch4jLib" net.sf.launch4j.Main $launch4jConfig `
        1> $launch4jStdout 2> $launch4jStderr
    $launch4jExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorAction
}
if (Test-Path -LiteralPath $launch4jStdout) {
    Get-Content -LiteralPath $launch4jStdout | Write-Output
}
if (Test-Path -LiteralPath $launch4jStderr) {
    Get-Content -LiteralPath $launch4jStderr | Write-Output
}
if ($launch4jExitCode -ne 0 -or -not (Test-Path -LiteralPath $appExe)) {
    throw "GeoEditor Launch4j packaging failed with exit code $launch4jExitCode."
}

$version = (Get-Item -LiteralPath $appExe).VersionInfo.ProductVersion
Write-Host 'Build generated successfully.' -ForegroundColor Green
Write-Host "Output: $appExe"
Write-Host "Version: $version"
Write-Host 'Runtime package contains only JavaFX, LWJGL and GeoEditor core libraries.'
}
finally {
    if (Test-Path -LiteralPath $appJar) {
        Remove-ItemWithRetry -Path $appJar
    }
    if (Test-Path -LiteralPath $staging) {
        Remove-ItemWithRetry -Path $staging -Recurse
    }
}
