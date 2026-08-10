param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [ValidateRange(1024, 65535)]
    [int]$JavaPort = 38082,

    [ValidateRange(1024, 65535)]
    [int]$PythonPort = 38091,

    [switch]$EnableFinishInboundS2Noop,

    [switch]$EnableFinishInboundS3Execute
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pythonExe = Join-Path $projectRoot 'agent-service\.venv\Scripts\python.exe'
$javaJar = Join-Path $projectRoot 'target\SugarInventory-1.0-SNAPSHOT.jar'
$mcpJar = Join-Path $projectRoot 'warehouse-mcp\target\warehouse-mcp-0.1.0-exec.jar'

foreach ($requiredPath in @($EnvFile, $pythonExe, $javaJar, $mcpJar)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required file was not found: $requiredPath"
    }
}

foreach ($port in @($JavaPort, $PythonPort)) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($listener) {
        throw "Port $port is already in use"
    }
}

foreach ($line in [System.IO.File]::ReadAllLines($EnvFile)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}

# Python Agent and the Java fallback client use different environment names for
# the same model credential. Keep the mapping process-local so the source env
# file remains unchanged.
if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY) -and
    -not [string]::IsNullOrWhiteSpace($env:AGENT_MODEL_API_KEY)) {
    $env:OPENAI_API_KEY = $env:AGENT_MODEL_API_KEY
}

# The local shadow-model file intentionally carries only model/database values.
# Generate isolated process-only security material instead of persisting test secrets.
$jwtMaterial = "s1-uat-jwt-$([Guid]::NewGuid().ToString('N'))-$([Guid]::NewGuid().ToString('N'))"
$env:JWT_SECRET = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($jwtMaterial))
$env:AGENT_PYTHON_SERVICE_KEY = "s1-python-$([Guid]::NewGuid().ToString('N'))"
$env:AGENT_INTERNAL_TOOL_SERVICE_KEY = "s1-gateway-$([Guid]::NewGuid().ToString('N'))"
$env:AGENT_ENTITY_REF_SECRET = "s1-entity-ref-$([Guid]::NewGuid().ToString('N'))-$([Guid]::NewGuid().ToString('N'))"
$env:WECHAT_APP_SECRET = "s1-wechat-disabled-$([Guid]::NewGuid().ToString('N'))"
$env:AGENT_TOOL_MODE = 'java_gateway'
$env:AGENT_PYTHON_MODEL_MODE = 'openai_compatible'
$env:AGENT_PLANNING_MODE = 'llm'
$env:AGENT_ENV = 'test'
$env:AGENT_ALLOW_INSECURE_MOCK_AUTH = 'false'
if ($EnableFinishInboundS2Noop) {
    # 仅为 S2 隔离验收暴露零业务写入消费端点；正常启动默认不设置。
    $env:AGENT_FINISH_INBOUND_S2_NOOP_ENABLED = 'true'
} else {
    Remove-Item Env:AGENT_FINISH_INBOUND_S2_NOOP_ENABLED -ErrorAction SilentlyContinue
}
if ($EnableFinishInboundS3Execute) {
    # 仅为 S3 隔离验收暴露真实成品入库受控端点；正常启动默认不设置。
    $env:AGENT_FINISH_INBOUND_S3_EXECUTE_ENABLED = 'true'
} else {
    Remove-Item Env:AGENT_FINISH_INBOUND_S3_EXECUTE_ENABLED -ErrorAction SilentlyContinue
}

$javaBaseUrl = "http://127.0.0.1:$JavaPort"
$pythonBaseUrl = "http://127.0.0.1:$PythonPort"
$env:SERVER_PORT = [string]$JavaPort
$env:AGENT_RUNTIME_MODE = 'python'
$env:AGENT_PYTHON_BASE_URL = $pythonBaseUrl
$env:AGENT_MCP_API_BASE_URL = $javaBaseUrl
$env:AGENT_MCP_JAR_PATH = $mcpJar
$env:JAVA_TOOL_GATEWAY_BASE_URL = $javaBaseUrl

$logDir = Join-Path $projectRoot 'output\s1-uat'
[System.IO.Directory]::CreateDirectory($logDir) | Out-Null
$stamp = [DateTime]::Now.ToString('yyyyMMdd-HHmmss')
$pythonOut = Join-Path $logDir "python-$stamp.out.log"
$pythonErr = Join-Path $logDir "python-$stamp.err.log"
$javaOut = Join-Path $logDir "java-$stamp.out.log"
$javaErr = Join-Path $logDir "java-$stamp.err.log"

$pythonProcess = Start-Process `
    -FilePath $pythonExe `
    -ArgumentList @('-m', 'uvicorn', 'app.main:app', '--host', '127.0.0.1', '--port', [string]$PythonPort) `
    -WorkingDirectory (Join-Path $projectRoot 'agent-service') `
    -WindowStyle Hidden `
    -RedirectStandardOutput $pythonOut `
    -RedirectStandardError $pythonErr `
    -PassThru

$javaProcess = Start-Process `
    -FilePath 'java' `
    -ArgumentList @('-jar', $javaJar) `
    -WorkingDirectory $projectRoot `
    -WindowStyle Hidden `
    -RedirectStandardOutput $javaOut `
    -RedirectStandardError $javaErr `
    -PassThru

[pscustomobject]@{
    JavaPid = $javaProcess.Id
    PythonPid = $pythonProcess.Id
    JavaBaseUrl = $javaBaseUrl
    PythonBaseUrl = $pythonBaseUrl
    JavaLog = $javaOut
    JavaErrorLog = $javaErr
    PythonLog = $pythonOut
    PythonErrorLog = $pythonErr
} | ConvertTo-Json -Compress
