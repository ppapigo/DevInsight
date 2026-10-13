param([string]$Path = (Join-Path $PSScriptRoot '..\.env'))
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Path)) { throw 'Copy .env.example to .env and fill the required values first.' }
foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $pair = $trimmed -split '=', 2
    if ($pair.Count -ne 2 -or $pair[0] -notmatch '^[A-Z][A-Z0-9_]*$') { throw 'Invalid .env line.' }
    [Environment]::SetEnvironmentVariable($pair[0], $pair[1].Trim().Trim('"').Trim("'"), 'Process')
}
Write-Output 'Environment loaded into this PowerShell process. Values are not printed.'
