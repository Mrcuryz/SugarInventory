$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Push-Location $root
try {
    & 'agent-service\.venv\Scripts\python.exe' -m pytest `
        agent-service/tests/test_agent_service.py `
        agent-service/tests/test_state_infrastructure.py -q
    if ($LASTEXITCODE -ne 0) { throw 'Python Runtime deterministic integration tests failed.' }

    mvn -q '-Dtest=InternalAgentToolGatewayServiceTest,HttpPythonAgentClientTest,StdioMcpSessionTest,StdioMcpSessionManagerTest,RuntimeRoutingAgentGatewayServiceTest' test
    if ($LASTEXITCODE -ne 0) { throw 'Java Gateway deterministic integration tests failed.' }

    Push-Location 'warehouse-mcp'
    try {
        mvn -q '-Dtest=WarehouseMcpApplicationTest,WarehouseToolsTest' test
        if ($LASTEXITCODE -ne 0) { throw 'warehouse-mcp deterministic integration tests failed.' }
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}

Write-Host 'Fixed-data Agent integration contract suite passed.'
