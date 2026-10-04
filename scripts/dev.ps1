param(
    [ValidateSet('up', 'prepare', 'stop', 'status')][string]$Action = 'up',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$launcherJar = Join-Path $projectRoot '.local\bootstrap\civicflow-local-launcher.jar'
$launcherSource = Join-Path $PSScriptRoot 'java\LocalLauncher.java'
if (-not (Test-Path -LiteralPath $launcherJar)) {
    $launcherClasses = Join-Path $projectRoot '.local\bootstrap\classes'
    New-Item -ItemType Directory -Force -Path $launcherClasses | Out-Null
    & javac --release 17 -d $launcherClasses $launcherSource
    if ($LASTEXITCODE -ne 0) { throw 'JDK 17+ javac is required to initialize IDEA launcher.' }
    & jar --create --file $launcherJar --main-class com.civicflow.dev.LocalLauncher -C $launcherClasses .
    if ($LASTEXITCODE -ne 0) { throw 'Could not build IDEA launcher.' }
}
$pythonExe = Join-Path $projectRoot '.local\venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $pythonExe)) {
    if ($Action -in @('stop', 'status')) { Write-Host 'Local launcher has not been initialized.'; exit 0 }
    # Reuse already-installed pinned E2E dependencies; pip still checks exact versions below.
    & python -m venv --system-site-packages (Join-Path $projectRoot '.local\venv')
    if ($LASTEXITCODE -ne 0) { throw 'Python 3.10+ is required.' }
}
$dependencyStamp = Join-Path $projectRoot '.local\venv\requirements.sha256'
$requirements = Join-Path $PSScriptRoot 'requirements-e2e.txt'
$hasher = [Security.Cryptography.SHA256]::Create()
try {
    $expectedHash = [BitConverter]::ToString($hasher.ComputeHash([IO.File]::ReadAllBytes($requirements))).Replace('-', '')
} finally { $hasher.Dispose() }
if ($Action -in @('up', 'prepare') -and ((-not (Test-Path $dependencyStamp)) -or (Get-Content $dependencyStamp -Raw).Trim() -ne $expectedHash)) {
    & $pythonExe -m pip install --retries 1 --timeout 20 -r $requirements
    if ($LASTEXITCODE -ne 0) { throw 'Local launcher dependencies could not be installed.' }
    Set-Content -LiteralPath $dependencyStamp -Value $expectedHash
}
$launcherArguments = @((Join-Path $PSScriptRoot 'dev.py'), $Action)
if ($SkipBuild) { $launcherArguments += '--skip-build' }
& $pythonExe @launcherArguments
exit $LASTEXITCODE
