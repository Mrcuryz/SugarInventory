param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [ValidateRange(1024, 65535)]
    [int]$JavaPort = 38082,

    [ValidateRange(1024, 65535)]
    [int]$PythonPort = 38091,

    [string]$LoginPassword = "lbsp-rag-uat",

    [string]$PythonExecutable = "C:\Users\18124\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe",

    [string]$PythonPath = "C:\Users\18124\AppData\Local\Temp\laibin-agent-test-deps-20260718",

    [string]$RagRoot,

    [string]$RagModelPath,

    [switch]$RagRequired
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$javaJar = Join-Path $projectRoot 'target\SugarInventory-1.0-SNAPSHOT.jar'
$mcpJar = Join-Path $projectRoot 'warehouse-mcp\target\warehouse-mcp-0.1.0-exec.jar'
$defaultRagRoot = Join-Path $projectRoot 'deploy\simple\artifacts\rag'
$defaultModelPath = Join-Path $projectRoot 'deploy\simple\artifacts\rag-model'
$effectiveRagRoot = if ([string]::IsNullOrWhiteSpace($RagRoot)) { $defaultRagRoot } else { $RagRoot }
$effectiveModelPath = if ([string]::IsNullOrWhiteSpace($RagModelPath)) { $defaultModelPath } else { $RagModelPath }

foreach ($requiredPath in @($EnvFile, $PythonExecutable, $javaJar, $mcpJar)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required file was not found: $requiredPath"
    }
}
foreach ($port in @($JavaPort, $PythonPort)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is already in use"
    }
}

foreach ($line in [System.IO.File]::ReadAllLines($EnvFile)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$env:PYTHONPATH = "$PythonPath;$projectRoot\agent-service"
$env:WEB_LOGIN_PASSWORD = $LoginPassword
if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY) -and -not [string]::IsNullOrWhiteSpace($env:AGENT_MODEL_API_KEY)) {
    $env:OPENAI_API_KEY = $env:AGENT_MODEL_API_KEY
}

$jwtMaterial = "rag-uat-jwt-$([Guid]::NewGuid().ToString('N'))-$([Guid]::NewGuid().ToString('N'))"
$env:JWT_SECRET = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($jwtMaterial))
$env:AGENT_PYTHON_SERVICE_KEY = "rag-python-$([Guid]::NewGuid().ToString('N'))"
$env:AGENT_INTERNAL_TOOL_SERVICE_KEY = "rag-gateway-$([Guid]::NewGuid().ToString('N'))"
$env:AGENT_ENTITY_REF_SECRET = "rag-entity-$([Guid]::NewGuid().ToString('N'))-$([Guid]::NewGuid().ToString('N'))"
$env:WECHAT_APP_SECRET = "rag-wechat-disabled-$([Guid]::NewGuid().ToString('N'))"
$env:AGENT_TOOL_MODE = 'java_gateway'
$env:AGENT_PYTHON_MODEL_MODE = 'openai_compatible'
$env:AGENT_PLANNING_MODE = 'llm'
$env:AGENT_ENV = 'test'
$env:AGENT_ALLOW_INSECURE_MOCK_AUTH = 'false'
$env:AGENT_RAG_ENABLED = 'true'
$env:AGENT_RAG_REQUIRED = if ($RagRequired) { 'true' } else { 'false' }
$env:AGENT_RAG_ROOT = $effectiveRagRoot
$env:AGENT_RAG_MODEL_PATH = $effectiveModelPath

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
$pythonOut = Join-Path $logDir "rag-python-$stamp.out.log"
$pythonErr = Join-Path $logDir "rag-python-$stamp.err.log"
$javaOut = Join-Path $logDir "rag-java-$stamp.out.log"
$javaErr = Join-Path $logDir "rag-java-$stamp.err.log"

$pythonProcess = Start-Process -FilePath $PythonExecutable `
    -ArgumentList @('-m', 'uvicorn', 'app.main:app', '--host', '127.0.0.1', '--port', [string]$PythonPort) `
    -WorkingDirectory (Join-Path $projectRoot 'agent-service') -WindowStyle Hidden `
    -RedirectStandardOutput $pythonOut -RedirectStandardError $pythonErr -PassThru

if ($RagRequired) {
    Start-Sleep -Seconds 3
    if (-not $pythonProcess.HasExited) {
        Stop-Process -Id $pythonProcess.Id -Force
        throw 'RAG required startup unexpectedly stayed alive'
    }
    $pythonProcess.WaitForExit()
    [pscustomobject]@{
        Mode = 'REQUIRED_FAIL_CLOSED'
        PythonPid = $pythonProcess.Id
        ExitCode = $pythonProcess.ExitCode
        PythonErrorLog = $pythonErr
    } | ConvertTo-Json -Compress
    exit 0
}

$javaProcess = Start-Process -FilePath 'java' -ArgumentList @('-jar', $javaJar) `
    -WorkingDirectory $projectRoot -WindowStyle Hidden `
    -RedirectStandardOutput $javaOut -RedirectStandardError $javaErr -PassThru

[pscustomobject]@{
    Mode = 'OPTIONAL_UNAVAILABLE'
    JavaPid = $javaProcess.Id
    PythonPid = $pythonProcess.Id
    JavaBaseUrl = $javaBaseUrl
    PythonBaseUrl = $pythonBaseUrl
    JavaLog = $javaOut
    JavaErrorLog = $javaErr
    PythonLog = $pythonOut
    PythonErrorLog = $pythonErr
} | ConvertTo-Json -Compress
