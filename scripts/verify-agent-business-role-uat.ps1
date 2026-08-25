param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [string]$BaseUrl = 'http://127.0.0.1:38082',

    [string]$LoginPassword = 'lbsp-isolated-uat'
)

$ErrorActionPreference = 'Stop'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Escape-SqlLiteral {
    param([string]$Value)
    return $Value.Replace("'", "''")
}

$config = @{}
foreach ($line in [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile).Path)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $config[$Matches[1]] = $Matches[2]
    }
}
foreach ($required in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    Assert-True ($config.ContainsKey($required)) "Missing $required in env file"
}
Assert-True ($config.DB_URL -match '^jdbc:mysql://([^/:]+)(?::(\d+))?/([^?]+)') 'Unsupported DB_URL'
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$dbName = $Matches[3]
Assert-True ($dbHost -in @('127.0.0.1', 'localhost', '::1')) 'Only a local shadow database is allowed'

$mysqlCandidates = @(
    'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    'mysql.exe',
    'mysql'
)
$mysql = $mysqlCandidates | Where-Object {
    if ([System.IO.Path]::IsPathRooted($_)) { Test-Path -LiteralPath $_ }
    else { $null -ne (Get-Command $_ -ErrorAction SilentlyContinue) }
} | Select-Object -First 1
Assert-True (-not [string]::IsNullOrWhiteSpace($mysql)) 'MySQL client was not found'

$mysqlArgs = @(
    "--host=$dbHost",
    "--port=$dbPort",
    "--user=$($config.DB_USERNAME)",
    "--database=$dbName",
    '--default-character-set=utf8mb4',
    '--batch',
    '--skip-column-names'
)

function Invoke-LocalMySql {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $env:MYSQL_PWD = $config.DB_PASSWORD
    try {
        $output = @($Sql | & $mysql @mysqlArgs 2>&1)
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL command failed: $((@($output | Select-Object -Last 8) -join [Environment]::NewLine))"
        }
        return @($output | ForEach-Object { [string]$_ })
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body,
        [string]$Token
    )
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }
    $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        SkipHttpErrorCheck = $true
        StatusCodeVariable = 'statusCode'
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    $response = Invoke-RestMethod @params
    return [pscustomobject]@{ StatusCode = [int]$statusCode; Body = $response }
}

function Test-ApiSuccess {
    param([object]$Response)
    return $Response.StatusCode -ge 200 -and $Response.StatusCode -lt 300 -and
        ($null -eq $Response.Body.code -or [int]$Response.Body.code -eq 200)
}

function Test-ApiForbidden {
    param([object]$Response)
    return $Response.StatusCode -eq 403 -or
        ($null -ne $Response.Body.code -and [int]$Response.Body.code -eq 403)
}

$suffix = [DateTime]::Now.ToString('MMddHHmmss')
$fixtures = @(
    [pscustomobject]@{
        roleCode = 'WAREHOUSE_MANAGER'
        employeeId = "ARBW$suffix"
        name = "仓管验收$suffix"
        mobile = "17$($suffix.Substring(0, 9))"
        department = '本地验收'
        position = '仓管'
        allowedPath = '/api/inventory/agent-read/ledger/query'
        allowedBody = @{}
        deniedPath = '/api/production/agent-read/boiling-batches/query'
        deniedBody = @{ limit = 1 }
    },
    [pscustomobject]@{
        roleCode = 'QC'
        employeeId = "ARBQ$suffix"
        name = "质检验收$suffix"
        mobile = "18$($suffix.Substring(0, 9))"
        department = '本地验收'
        position = '质检'
        allowedPath = '/api/assay/records/query'
        allowedBody = @{
            productScope = @{ type = 'ALL' }
            judgeStatus = 'ANY'
            sortBy = 'sampleDate'
            sortDirection = 'DESC'
            page = 1
            size = 1
        }
        deniedPath = '/api/production/agent-read/boiling-batches/query'
        deniedBody = @{ limit = 1 }
    },
    [pscustomobject]@{
        roleCode = 'PROD_SUPERVISOR'
        employeeId = "ARBP$suffix"
        name = "生产验收$suffix"
        mobile = "19$($suffix.Substring(0, 9))"
        department = '本地验收'
        position = '生产主管'
        allowedPath = '/api/production/agent-read/boiling-batches/query'
        allowedBody = @{ limit = 1 }
        deniedPath = '/api/audit/agent-read/operation-logs/query'
        deniedBody = @{}
    }
)

$evidence = [System.Collections.Generic.List[object]]::new()
$sessionIds = [System.Collections.Generic.List[string]]::new()

try {
    $roleFacts = @{}
    foreach ($roleCode in @('WAREHOUSE_MANAGER', 'QC', 'PROD_SUPERVISOR')) {
        $safeRole = Escape-SqlLiteral $roleCode
        $codes = @((Invoke-LocalMySql "SELECT p.perm_code FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='$safeRole' ORDER BY p.perm_code;") | Where-Object { $_ })
        Assert-True ($codes -contains 'agent:use') "$roleCode is missing agent:use"
        $agentForbidden = @('agent:review:view', 'agent:review:update', 'agent:audit:view')
        if ($roleCode -ne 'WAREHOUSE_MANAGER') {
            $agentForbidden += 'agent:finish-inbound:execute'
        }
        $readOnlyRoleForbidden = @(
            'inventory:inbound', 'inventory:outbound', 'inventory:transfer',
            'task:create', 'task:confirm', 'task:cancel',
            'production:order:create', 'production:order:update', 'production:order:cancel',
            'production:material:pick', 'production:output:create',
            'production:boiling:create', 'production:boiling:update', 'production:boiling:cancel'
        )
        $forbiddenCodes = if ($roleCode -eq 'QC') {
            $agentForbidden
        } else {
            @($agentForbidden + $readOnlyRoleForbidden)
        }
        $forbidden = @($codes | Where-Object { $_ -in $forbiddenCodes })
        Assert-True ($forbidden.Count -eq 0) "$roleCode has forbidden permissions: $($forbidden -join ',')"
        if ($roleCode -eq 'WAREHOUSE_MANAGER') {
            Assert-True ($codes -contains 'agent:finish-inbound:execute') 'WAREHOUSE_MANAGER is missing controlled finish inbound execution'
            Assert-True ($codes -notcontains 'task:confirm') 'WAREHOUSE_MANAGER must not receive generic task confirmation'
        }
        $roleFacts[$roleCode] = $codes
    }

    foreach ($fixture in $fixtures) {
        $employeeId = Escape-SqlLiteral $fixture.employeeId
        $name = Escape-SqlLiteral $fixture.name
        $mobile = Escape-SqlLiteral $fixture.mobile
        $roleCode = Escape-SqlLiteral $fixture.roleCode
        Invoke-LocalMySql "INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$employeeId','$name','$mobile','本地验收','$(Escape-SqlLiteral $fixture.position)','在职','$roleCode');" | Out-Null

        $login = Invoke-Api 'Post' '/api/auth/web-login' @{ name = $fixture.name; password = $LoginPassword } ''
        Assert-True ((Test-ApiSuccess $login) -and $login.Body.data.token) "$($fixture.roleCode) login failed"
        $token = [string]$login.Body.data.token

        $info = Invoke-Api 'Get' '/api/user/info' $null $token
        Assert-True (Test-ApiSuccess $info) "$($fixture.roleCode) user info failed"
        Assert-True ($info.Body.data.roleCode -eq $fixture.roleCode) "$($fixture.roleCode) role mismatch"
        $permissionCodes = @($info.Body.data.permissionCodes)
        Assert-True ($permissionCodes -contains 'agent:use') "$($fixture.roleCode) login token is missing agent:use"

        $session = Invoke-Api 'Post' '/api/agent/sessions' @{ clientType = 'WEB'; requestedScopes = @('mcp:warehouse:read') } $token
        Assert-True ((Test-ApiSuccess $session) -and $session.Body.data.agentSessionId) "$($fixture.roleCode) Agent session failed"
        $sessionId = [string]$session.Body.data.agentSessionId
        $sessionIds.Add($sessionId)

        $allowed = Invoke-Api 'Post' $fixture.allowedPath $fixture.allowedBody $token
        Assert-True (Test-ApiSuccess $allowed) "$($fixture.roleCode) allowed read returned HTTP $($allowed.StatusCode), code $($allowed.Body.code)"

        $denied = Invoke-Api 'Post' $fixture.deniedPath $fixture.deniedBody $token
        Assert-True (Test-ApiForbidden $denied) "$($fixture.roleCode) cross-role read expected 403 but got HTTP $($denied.StatusCode), code $($denied.Body.code)"

        $outboundPreviewDenied = 'NOT_APPLICABLE'
        if ($fixture.roleCode -eq 'WAREHOUSE_MANAGER') {
            $outboundPreview = Invoke-Api 'Post' '/api/logistics/agent-read/pallet-tasks/transition/preview' @{
                previewVersion = 1
                transition = 'CONFIRM_FINISH_OUTBOUND'
                palletCodes = @('UAT-DENIED')
            } $token
            Assert-True (Test-ApiForbidden $outboundPreview) "WAREHOUSE_MANAGER outbound preview expected 403 but got HTTP $($outboundPreview.StatusCode), code $($outboundPreview.Body.code)"
            $outboundPreviewDenied = 'PASS'
        }

        $evidence.Add([pscustomobject]@{
            RoleCode = $fixture.roleCode
            PermissionCount = $permissionCodes.Count
            AgentSession = 'PASS'
            AllowedRead = 'PASS'
            CrossRoleDenied = 'PASS'
            OutboundPreviewDenied = $outboundPreviewDenied
            AgentWritePermissions = if ($fixture.roleCode -eq 'WAREHOUSE_MANAGER') { 'CONTROLLED_FINISH_INBOUND_ONLY' } else { 0 }
            ExistingBusinessWritePermissions = if ($fixture.roleCode -eq 'QC') { 'PRESERVED' } else { 0 }
        })

        $revoke = Invoke-Api 'Delete' "/api/agent/sessions/$sessionId" @{ revokedReason = 'LOCAL_ROLE_UAT_COMPLETE' } $token
        Assert-True (Test-ApiSuccess $revoke) "$($fixture.roleCode) session cleanup failed"
    }

    [pscustomobject]@{
        Result = 'PASS'
        DatabaseHost = $dbHost
        DatabaseName = $dbName
        Evidence = $evidence
    } | ConvertTo-Json -Depth 8
} finally {
    $employeeIds = @($fixtures | ForEach-Object { "'$(Escape-SqlLiteral $_.employeeId)'" }) -join ','
    if ($employeeIds) {
        Invoke-LocalMySql "DELETE FROM agent_session WHERE user_id IN (SELECT id FROM user WHERE employee_id IN ($employeeIds)); DELETE FROM user WHERE employee_id IN ($employeeIds); DELETE FROM employee_roster WHERE employee_id IN ($employeeIds);" | Out-Null
    }
}
