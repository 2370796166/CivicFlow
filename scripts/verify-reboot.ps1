param([Parameter(Mandatory=$true)][ValidateSet('before','after')][string]$Phase)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$evidencePath = Join-Path $projectRoot '.local\validation\reboot-baseline.json'
$keyPath = Join-Path $projectRoot '.local\dev\keys.json'
$boot = (Get-CimInstance Win32_OperatingSystem).LastBootUpTime.ToUniversalTime().ToString('o')
$sha = [Security.Cryptography.SHA256]::Create()
try { $digest = [BitConverter]::ToString($sha.ComputeHash([IO.File]::ReadAllBytes($keyPath))).Replace('-', '') }
finally { $sha.Dispose() }
if ($Phase -eq 'before') {
    New-Item -ItemType Directory -Force -Path (Split-Path $evidencePath -Parent) | Out-Null
    @{ bootTime = $boot; keyDigest = $digest; capturedAt = [DateTime]::UtcNow.ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath $evidencePath -Encoding UTF8
    Write-Host 'Baseline saved. Save your work and reboot Windows manually when ready.'
    exit 0
}
$baseline = Get-Content -LiteralPath $evidencePath -Raw | ConvertFrom-Json
if ($baseline.bootTime -eq $boot) { throw 'Windows has not rebooted since the baseline. No reboot pass can be recorded.' }
if ($baseline.keyDigest -ne $digest) { throw 'Persistent development keys changed across reboot.' }
& powershell.exe -NoProfile -File (Join-Path $PSScriptRoot 'dev.ps1') up
if ($LASTEXITCODE -ne 0) { throw 'Startup after reboot failed.' }
& (Join-Path $projectRoot '.local\venv\Scripts\python.exe') (Join-Path $PSScriptRoot 'smoke_local.py')
if ($LASTEXITCODE -ne 0) { throw 'HTTP smoke after reboot failed.' }
@{ previousBoot = $baseline.bootTime; currentBoot = $boot; keysUnchanged = $true; smoke = 'PASS'; verifiedAt = [DateTime]::UtcNow.ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectRoot '.local\validation\reboot-result.json') -Encoding UTF8
Write-Host 'PASS: real Windows reboot, unchanged keys, complete startup and authenticated HTTP smoke.'
