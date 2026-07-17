$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$deploy = Join-Path $root 'deploy\simple'
$artifacts = Join-Path $deploy 'artifacts'
$wheelOutput = Join-Path $env:TEMP 'laibin-agent-wheel'

Push-Location $root
try {
    mvn -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' }

    Push-Location 'warehouse-mcp'
    try {
        mvn -q package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'warehouse-mcp package failed.' }
    } finally {
        Pop-Location
    }

    Push-Location 'webpage'
    try {
        npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally {
        Pop-Location
    }

    if (Test-Path -LiteralPath $wheelOutput) {
        Remove-Item -LiteralPath $wheelOutput -Recurse -Force
    }
    New-Item -ItemType Directory -Path $wheelOutput | Out-Null
    & 'agent-service\.venv\Scripts\python.exe' -m pip wheel --no-cache-dir --no-build-isolation --no-deps --wheel-dir $wheelOutput '.\agent-service'
    if ($LASTEXITCODE -ne 0) { throw 'Agent wheel build failed.' }

    New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
    Copy-Item -LiteralPath 'target\SugarInventory-1.0-SNAPSHOT.jar' -Destination (Join-Path $artifacts 'app.jar') -Force
    Copy-Item -LiteralPath 'warehouse-mcp\target\warehouse-mcp-0.1.0-exec.jar' -Destination (Join-Path $artifacts 'warehouse-mcp.jar') -Force
    $wheel = Get-ChildItem -LiteralPath $wheelOutput -Filter '*.whl' | Select-Object -First 1
    if ($null -eq $wheel) { throw 'Agent wheel was not produced.' }
    Copy-Item -LiteralPath $wheel.FullName -Destination (Join-Path $artifacts 'agent-service.whl') -Force

    foreach ($generated in @('agent-service\build', 'agent-service\warehouse_agent_service.egg-info')) {
        if (Test-Path -LiteralPath $generated) {
            Remove-Item -LiteralPath $generated -Recurse -Force
        }
    }

    $distTarget = Join-Path $artifacts 'dist'
    if (Test-Path -LiteralPath $distTarget) {
        Remove-Item -LiteralPath $distTarget -Recurse -Force
    }
    Copy-Item -LiteralPath 'webpage\dist' -Destination $distTarget -Recurse
} finally {
    Pop-Location
}

Write-Host "部署产物已生成：$artifacts"
