$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$legacyBuild = Join-Path $root 'agent-service\build\lib'
if (Test-Path -LiteralPath $legacyBuild) {
    throw "Stale agent-service/build/lib exists. Run a clean source build before packaging."
}

$python = Join-Path $root 'agent-service\.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    throw 'agent-service virtual environment is missing.'
}

$env:PYTHONPATH = Join-Path $root 'agent-service'
$source = & $python -c 'from pathlib import Path; import app; print(Path(app.__file__).resolve())'
if ($LASTEXITCODE -ne 0 -or $source -notlike "$(Join-Path $root 'agent-service\app')*") {
    throw "Agent runtime imported code outside agent-service/app: $source"
}
Write-Host "Agent source check passed: $source"
