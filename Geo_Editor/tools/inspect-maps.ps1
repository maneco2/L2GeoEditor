[CmdletBinding()]
param(
    [string]$MapsPath,
    [string]$MapFile,
    [int]$MaxFiles = 0
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($MapsPath)) {
    $MapsPath = Join-Path (Split-Path -Parent (Split-Path -Parent $PSCommandPath)) 'build\data\MAPS'
}
$path = (Resolve-Path -LiteralPath $MapsPath).Path
$files = @(Get-ChildItem -LiteralPath $path -File -Filter '*.unr' -Recurse)
if (-not [string]::IsNullOrWhiteSpace($MapFile)) { $files = @($files | Where-Object Name -EQ $MapFile) }
if ($MaxFiles -gt 0) { $files = @($files | Select-Object -First $MaxFiles) }

if ($files.Count -eq 0) {
    Write-Host "Nenhum arquivo .unr encontrado em $path" -ForegroundColor Yellow
    exit 0
}

foreach ($file in $files) {
    $bytes = [IO.File]::ReadAllBytes($file.FullName)
    $previewLength = [Math]::Min(32, $bytes.Length)
    $hex = (($bytes[0..($previewLength - 1)] | ForEach-Object { $_.ToString('X2') }) -join ' ')
    $ascii = -join ($bytes[0..([Math]::Min(255, $bytes.Length - 1))] | ForEach-Object {
        if ($_ -ge 32 -and $_ -le 126) { [char]$_ } else { '.' }
    })

    Write-Host "--- $($file.Name) ---" -ForegroundColor Cyan
    Write-Host "Path: $($file.FullName)"
    Write-Host "Size: $($bytes.Length) bytes"
    Write-Host "SHA-256: $((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash)"
    Write-Host "Header: $hex"
    Write-Host "ASCII preview: $ascii"
}
