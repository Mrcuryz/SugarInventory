param(
    [ValidateSet('Capture', 'Verify')]
    [string]$Mode = 'Capture',
    [string]$EnvFile = '',
    [string]$JarPath = '',
    [string]$LogDirectory = ''
)

$ErrorActionPreference = 'Stop'

function New-ProcessSecret {
    param([ValidateRange(16, 256)][int]$ByteCount = 64)
    $buffer = New-Object byte[] $ByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($buffer)
        return [Convert]::ToBase64String($buffer)
    } finally {
        $generator.Dispose()
    }
}

$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    (Get-Location).Path
} else {
    $PSScriptRoot
}
$rootCandidate = if (Test-Path -LiteralPath (Join-Path $scriptRoot 'pom.xml')) {
    $scriptRoot
} else {
    Join-Path $scriptRoot '..'
}
$root = (Resolve-Path -LiteralPath $rootCandidate).Path
if (-not (Test-Path -LiteralPath (Join-Path $root 'pom.xml'))) {
    throw "Unable to resolve project root from script or current directory: $scriptRoot"
}

if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Join-Path $root 'target\SugarInventory-1.0-SNAPSHOT.jar'
}
if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
    $LogDirectory = Join-Path $root 'logs\inventory-history'
}

if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
    if (-not (Test-Path -LiteralPath $EnvFile)) {
        throw "Environment file was not found: $EnvFile"
    }
    $EnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
    Get-Content -LiteralPath $EnvFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $name = $matches[1]
            $value = $matches[2].Trim()
            if ($value.Length -ge 2 -and (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))
            )) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "Backend executable jar was not found: $JarPath"
}
$JarPath = (Resolve-Path -LiteralPath $JarPath).Path

# The one-shot inventory job does not use JWT, WeChat or model capabilities, but the
# full Spring context still resolves their required settings. Generate process-only
# placeholders for local/disaster-recovery jobs and never write them back to the env file.
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    $env:JWT_SECRET = New-ProcessSecret -ByteCount 64
}
if ([string]::IsNullOrWhiteSpace($env:WECHAT_APP_SECRET)) {
    $env:WECHAT_APP_SECRET = 'inventory-history-job-not-used'
}
if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
    $env:OPENAI_API_KEY = 'inventory-history-job-not-used'
}
if ([string]::IsNullOrWhiteSpace($env:AGENT_ENTITY_REF_SECRET)) {
    $env:AGENT_ENTITY_REF_SECRET = New-ProcessSecret -ByteCount 32
}

New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$modeValue = $Mode.ToLowerInvariant()
$logFile = Join-Path $LogDirectory "$modeValue-$timestamp.log"
$arguments = @(
    '-jar',
    $JarPath,
    '--spring.main.web-application-type=none',
    "--inventory.history.one-shot-mode=$modeValue",
    '--agent.mcp.warmup-enabled=false',
    '--spring.task.scheduling.enabled=false',
    '--knife4j.enable=false',
    '--springdoc.api-docs.enabled=false',
    '--springdoc.swagger-ui.enabled=false'
)

& java @arguments 2>&1 | Tee-Object -FilePath $logFile
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    Write-Host "Inventory history $Mode completed. Log: $logFile"
} elseif ($exitCode -eq 3) {
    Write-Warning "Inventory history $Mode ran, but the data-quality gate is blocked. Log: $logFile"
} else {
    Write-Warning "Inventory history $Mode failed with exit code $exitCode. Log: $logFile"
}

exit $exitCode
