[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Reference,
    [Parameter(Mandatory = $true, Position = 1)]
    [string]$Candidate
)

$ErrorActionPreference = 'Stop'
$referencePath = (Resolve-Path -LiteralPath $Reference).Path
$candidatePath = (Resolve-Path -LiteralPath $Candidate).Path
$referenceBytes = [IO.File]::ReadAllBytes($referencePath)
$candidateBytes = [IO.File]::ReadAllBytes($candidatePath)
$referenceHash = (Get-FileHash -LiteralPath $referencePath -Algorithm SHA256).Hash
$candidateHash = (Get-FileHash -LiteralPath $candidatePath -Algorithm SHA256).Hash

Write-Host "Reference: $referencePath"
Write-Host "Candidate: $candidatePath"
Write-Host "Reference size: $($referenceBytes.Length) bytes"
Write-Host "Candidate size: $($candidateBytes.Length) bytes"
Write-Host "Reference SHA-256: $referenceHash"
Write-Host "Candidate SHA-256: $candidateHash"

$limit = [Math]::Min($referenceBytes.Length, $candidateBytes.Length)
$firstDifference = -1
for ($i = 0; $i -lt $limit; $i++) {
    if ($referenceBytes[$i] -ne $candidateBytes[$i]) {
        $firstDifference = $i
        break
    }
}
if (($firstDifference -lt 0) -and ($referenceBytes.Length -ne $candidateBytes.Length)) {
    $firstDifference = $limit
}

if ($firstDifference -lt 0) {
    Write-Host 'RESULT: IDENTICAL' -ForegroundColor Green
    exit 0
}

$referenceValue = if ($firstDifference -lt $referenceBytes.Length) { $referenceBytes[$firstDifference] } else { '<EOF>' }
$candidateValue = if ($firstDifference -lt $candidateBytes.Length) { $candidateBytes[$firstDifference] } else { '<EOF>' }
Write-Host "First difference at byte offset $firstDifference" -ForegroundColor Yellow
Write-Host "Reference byte: $referenceValue"
Write-Host "Candidate byte: $candidateValue"
Write-Host 'RESULT: DIFFERENT' -ForegroundColor Red
exit 2
