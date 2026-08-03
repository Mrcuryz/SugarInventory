param(
    [ValidateSet('Capture', 'Verify')]
    [string]$Mode = 'Capture',
    [string]$EnvFile = '',
    [string]$JarPath = '',
    [string]$LogDirectory = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Join-Path $root 'target\SugarInventory-1.0-SNAPSHOT.jar'
}
if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
    $LogDirectory = Join-Path $root 'logs\inventory-history'
}

if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
    if (-not (Test-Path -LiteralPath $EnvFile)) {
        throw "环境配置文件不存在：$EnvFile"
    }
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
    throw "后端可执行包不存在：$JarPath"
}

# 一次性库存任务不使用 JWT、微信或模型能力，但完整 Spring 上下文仍会解析这些必填配置。
# 本地/灾备任务缺少这些变量时只生成当前进程有效的占位值，不写回 env 文件。
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    $env:JWT_SECRET = [Convert]::ToBase64String(
        [Security.Cryptography.RandomNumberGenerator]::GetBytes(64)
    )
}
if ([string]::IsNullOrWhiteSpace($env:WECHAT_APP_SECRET)) {
    $env:WECHAT_APP_SECRET = 'inventory-history-job-not-used'
}
if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
    $env:OPENAI_API_KEY = 'inventory-history-job-not-used'
}
if ([string]::IsNullOrWhiteSpace($env:AGENT_ENTITY_REF_SECRET)) {
    $env:AGENT_ENTITY_REF_SECRET = [Convert]::ToHexString(
        [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
    )
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
    Write-Host "库存历史 $Mode 任务完成，日志：$logFile"
} elseif ($exitCode -eq 3) {
    Write-Warning "库存历史 $Mode 任务已执行，但数据质量门禁未通过。日志：$logFile"
} else {
    Write-Warning "库存历史 $Mode 任务失败，退出码 $exitCode。日志：$logFile"
}

exit $exitCode
