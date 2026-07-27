[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Reference,
    [Parameter(Mandatory = $true, Position = 1)][string]$Candidate
)
$ErrorActionPreference = 'Stop'
function Compare-GeoStream([IO.BinaryReader]$A, [IO.BinaryReader]$B) {
    $stats = [ordered]@{ Flat = 0; Complex = 0; Multilayer = 0; Layers = 0 }
    for ($block = 0; $block -lt 65536; $block++) {
        try { $ta = $A.ReadByte(); $tb = $B.ReadByte() } catch { throw "EOF no bloco $block." }
        if ($ta -ne $tb) { return [PSCustomObject]@{ Difference=$block; Reason="tipo $ta/$tb"; Stats=$stats } }
        switch ($ta) { 0 { $stats.Flat++; $cells=1 }; 1 { $stats.Complex++; $cells=64 }; 2 { $stats.Multilayer++; $cells=64 }; default { throw "Tipo inválido ($ta) no bloco $block." } }
        for ($cell=0; $cell -lt $cells; $cell++) {
            if ($ta -eq 2) { $la=$A.ReadByte(); $lb=$B.ReadByte(); if ($la -lt 1 -or $la -gt 125 -or $lb -lt 1 -or $lb -gt 125) { throw "Camadas inválidas no bloco $block." }; $stats.Layers += $la; if ($la -ne $lb) { return [PSCustomObject]@{ Difference=$block; Reason="camadas na célula $cell"; Stats=$stats } }; $layers=$la } else { $layers=1 }
            for ($layer=0; $layer -lt $layers; $layer++) { if ($A.ReadUInt16() -ne $B.ReadUInt16()) { return [PSCustomObject]@{ Difference=$block; Reason="altura/NSWE na célula $cell"; Stats=$stats } } }
        }
    }
    if ($A.BaseStream.Position -ne $A.BaseStream.Length -or $B.BaseStream.Position -ne $B.BaseStream.Length) { throw 'Dados extras após os 65536 blocos.' }
    [PSCustomObject]@{ Difference=$null; Reason=''; Stats=$stats }
}
$refPath=(Resolve-Path -LiteralPath $Reference).Path; $candPath=(Resolve-Path -LiteralPath $Candidate).Path
$ra=$null; $rb=$null
try { $ra=[IO.BinaryReader]::new([IO.File]::OpenRead($refPath)); $rb=[IO.BinaryReader]::new([IO.File]::OpenRead($candPath)); $result=Compare-GeoStream $ra $rb } finally { if($ra){$ra.Dispose()}; if($rb){$rb.Dispose()} }
Write-Host "Reference: $refPath"; Write-Host "Candidate: $candPath"; Write-Host "Tipos: flat=$($result.Stats.Flat), complex=$($result.Stats.Complex), multilayer=$($result.Stats.Multilayer), layers=$($result.Stats.Layers)"
if($null -eq $result.Difference){Write-Host 'RESULT: SEMANTICALLY IDENTICAL' -ForegroundColor Green; exit 0}; Write-Host "First semantic difference at block $($result.Difference): $($result.Reason)" -ForegroundColor Yellow; Write-Host 'RESULT: SEMANTICALLY DIFFERENT' -ForegroundColor Red; exit 2
