[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Reference,
    [Parameter(Mandatory = $true, Position = 1)]
    [string]$Candidate
)

$ErrorActionPreference = 'Stop'

function Read-PerformanceReport([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line) -or -not $line.Contains('=')) {
            continue
        }
        $pair = $line.Split('=', 2)
        $values[$pair[0]] = $pair[1]
    }
    return $values
}

function Format-Delta([double]$ReferenceValue, [double]$CandidateValue) {
    if ($ReferenceValue -eq 0) {
        return 'n/a'
    }
    return ('{0:+0.0%;-0.0%;0.0%}' -f (($CandidateValue / $ReferenceValue) - 1))
}

$referencePath = (Resolve-Path -LiteralPath $Reference).Path
$candidatePath = (Resolve-Path -LiteralPath $Candidate).Path
$baseline = Read-PerformanceReport $referencePath
$current = Read-PerformanceReport $candidatePath

Write-Host "Reference: $referencePath"
Write-Host "Candidate: $candidatePath"

$scenarioKeys = @(
    'scenario.grid',
    'scenario.fullMap',
    'scenario.fullTexturedTerrain',
    'scenario.visibleGridRange',
    'scenario.cameraGeoPosition',
    'scenario.cameraRotation'
)
$mismatches = @($scenarioKeys | Where-Object {
    $baseline.ContainsKey($_) -and $current.ContainsKey($_) -and $baseline[$_] -ne $current[$_]
})
if ($mismatches.Count -gt 0) {
    Write-Warning ('Scenario mismatch: ' + ($mismatches -join ', '))
} elseif (($baseline.ContainsKey('scenario.cameraGeoPosition') -and $current.ContainsKey('scenario.cameraGeoPosition'))) {
    Write-Host 'Scenario: compatible camera and rendering settings.' -ForegroundColor Green
} else {
    Write-Warning 'Camera metadata is missing in one or both reports; compare FPS only with caution.'
}

$metrics = @(
    @{ Key = 'averageFps'; Label = 'Average FPS' },
    @{ Key = 'averageFrameMicros'; Label = 'Average frame us' },
    @{ Key = 'worstFrameMicros'; Label = 'Worst frame us' },
    @{ Key = 'peakMemoryBytes'; Label = 'Peak memory bytes' },
    @{ Key = 'heapUsedBytes'; Label = 'Current heap used bytes' },
    @{ Key = 'processResidentBytes'; Label = 'Current process resident bytes' },
    @{ Key = 'lastGeodataParseMicros'; Label = 'Last parse us' },
    @{ Key = 'peakAuxiliaryVboPreparationMicros'; Label = 'Peak auxiliary VBO prep us' },
    @{ Key = 'totalAuxiliaryVboPreparationMicros'; Label = 'Total auxiliary VBO prep us' },
    @{ Key = 'peakAuxiliaryVboUploadMicros'; Label = 'Peak auxiliary VBO upload us' },
    @{ Key = 'totalAuxiliaryVboUploadMicros'; Label = 'Total auxiliary VBO upload us' },
    @{ Key = 'visibleNeighbors'; Label = 'Visible neighbors' },
    @{ Key = 'loadedRegions'; Label = 'Loaded regions' }
)

$rows = foreach ($metric in $metrics) {
    $key = $metric.Key
    if (-not $baseline.ContainsKey($key) -or -not $current.ContainsKey($key)) {
        continue
    }
    $referenceValue = [double]$baseline[$key]
    $candidateValue = [double]$current[$key]
    [PSCustomObject]@{
        Metric = $metric.Label
        Reference = $baseline[$key]
        Candidate = $current[$key]
        Delta = Format-Delta $referenceValue $candidateValue
    }
}

$rows | Format-Table -AutoSize
