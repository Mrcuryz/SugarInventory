param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [string]$BaseUrl = "http://127.0.0.1:38082",

    [string]$LoginPassword = "lbsp-isolated-uat",

    [string]$CleanupManifest,

    [switch]$VerifyCompletedManifest
)

$ErrorActionPreference = "Stop"
$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    (Get-Location).Path
} else {
    $PSScriptRoot
}
$projectRoot = if (Test-Path -LiteralPath (Join-Path $scriptRoot 'pom.xml')) {
    $scriptRoot
} else {
    (Resolve-Path -LiteralPath (Join-Path $scriptRoot '..')).Path
}
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'pom.xml'))) {
    throw "Unable to resolve project root from script or current directory: $scriptRoot"
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "ASSERTION FAILED: $Message" }
}

function Escape-SqlLiteral {
    param([string]$Value)
    if ($null -eq $Value) { return '' }
    return $Value.Replace("'", "''")
}

function Convert-ToBase36 {
    param([long]$Value)
    $chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    $encoded = ''
    do {
        $encoded = $chars[$Value % 36] + $encoded
        $Value = [Math]::Floor($Value / 36)
    } while ($Value -gt 0)
    return $encoded
}

function New-PalletCode {
    param([long]$Id)
    $chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    $mixed = (($Id * 13L) + 5L) % 60466176L
    $withoutCheck = "BT$((Convert-ToBase36 $mixed).PadLeft(5, '0'))"
    $sum = 0
    foreach ($char in $withoutCheck.ToCharArray()) {
        $value = $chars.IndexOf([string]$char)
        if ($value -lt 0) { throw "Invalid Base36 character while generating pallet code" }
        $sum += $value
    }
    return $withoutCheck + $chars[$sum % 36]
}

$config = @{}
foreach ($line in [System.IO.File]::ReadAllLines($EnvFile)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $config[$Matches[1]] = $Matches[2]
    }
}
foreach ($required in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    Assert-True ($config.ContainsKey($required)) "Missing $required in env file"
}
Assert-True ($config['DB_URL'] -match '^jdbc:mysql://([^/:]+)(?::(\d+))?/([^?]+)') "Unsupported DB_URL"
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$dbName = $Matches[3]
Assert-True ($dbHost -in @('127.0.0.1', 'localhost', '::1')) "Refusing non-local database host: $dbHost"

$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
Assert-True (Test-Path -LiteralPath $mysql) "mysql.exe not found"

function Invoke-LocalMySql {
    param([string]$Sql)
    $env:MYSQL_PWD = $config['DB_PASSWORD']
    try {
        $output = & $mysql `
            "--host=$dbHost" `
            "--port=$dbPort" `
            "--user=$($config['DB_USERNAME'])" `
            "--database=$dbName" `
            '--default-character-set=utf8mb4' `
            '--batch' `
            '--skip-column-names' `
            "--execute=$Sql"
        if ($LASTEXITCODE -ne 0) { throw "mysql exited with code $LASTEXITCODE" }
        Write-Output -NoEnumerate @($output)
    }
    finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Remove-BrowserFixture {
    param([object]$Fixture)
    $userId = if ($null -eq $Fixture.operatorId) { 0 } else { [int]$Fixture.operatorId }
    $palletId = if ($null -eq $Fixture.palletId) { 0 } else { [int]$Fixture.palletId }
    $taskId = if ($null -eq $Fixture.taskId) { 0 } else { [int]$Fixture.taskId }
    $warehouseId = if ($null -eq $Fixture.warehouseId) { 0 } else { [int]$Fixture.warehouseId }
    $employeeRosterId = if ($null -eq $Fixture.employeeRosterId) { 0 } else { [int]$Fixture.employeeRosterId }
    if ($userId -gt 0) {
        $sessionRows = @(Invoke-LocalMySql "SELECT id FROM agent_session WHERE user_id=$userId;")
        if ($sessionRows.Count -gt 0) {
            $sessionList = ($sessionRows | ForEach-Object { "'$(Escape-SqlLiteral ([string]$_))'" }) -join ','
            Invoke-LocalMySql "DELETE FROM agent_message_review_evidence WHERE review_id IN (SELECT id FROM agent_message_review WHERE agent_session_id IN ($sessionList)); DELETE FROM agent_message_review WHERE agent_session_id IN ($sessionList); DELETE FROM agent_interrupt_state WHERE agent_session_id IN ($sessionList); DELETE FROM agent_api_audit_log WHERE agent_session_id IN ($sessionList); DELETE FROM agent_tool_audit_log WHERE agent_session_id IN ($sessionList);" | Out-Null
        }
        Invoke-LocalMySql "DELETE FROM agent_finish_inbound_execution_audit WHERE owner_user_id=$userId; DELETE FROM agent_finish_inbound_execution_request WHERE owner_user_id=$userId; DELETE FROM agent_finish_inbound_execution_confirmation WHERE owner_user_id=$userId; DELETE FROM agent_finish_inbound_execution_preview WHERE owner_user_id=$userId; DELETE FROM agent_task_transition_preview WHERE owner_user_id=$userId; DELETE FROM agent_session WHERE user_id=$userId;" | Out-Null
    }
    if ($palletId -gt 0) {
        Invoke-LocalMySql "DELETE FROM stock_movement_event WHERE pallet_code_id=$palletId; DELETE FROM pallet_flow_record WHERE pallet_code_id=$palletId; DELETE FROM inventory WHERE pallet_code_id=$palletId;" | Out-Null
    }
    if ($taskId -gt 0) {
        Invoke-LocalMySql "DELETE FROM pallet_task_semi_item WHERE pallet_task_id=$taskId; DELETE FROM pallet_task WHERE id=$taskId;" | Out-Null
    }
    if ($palletId -gt 0) { Invoke-LocalMySql "DELETE FROM pallet_code WHERE id=$palletId;" | Out-Null }
    if ($warehouseId -gt 0) {
        Invoke-LocalMySql "DELETE FROM inventory WHERE warehouse_id=$warehouseId; DELETE FROM in_stock WHERE warehouse_id=$warehouseId; DELETE FROM warehouse WHERE id=$warehouseId;" | Out-Null
    }
    if ($userId -gt 0) { Invoke-LocalMySql "DELETE FROM user WHERE id=$userId;" | Out-Null }
    if ($employeeRosterId -gt 0) { Invoke-LocalMySql "DELETE FROM employee_roster WHERE id=$employeeRosterId;" | Out-Null }
    if ([bool]$Fixture.permissionLinkInserted) {
        Invoke-LocalMySql "DELETE FROM role_permission WHERE role_id=(SELECT id FROM role WHERE role_code='ADMIN' LIMIT 1) AND permission_id=(SELECT id FROM permission WHERE perm_code='agent:finish-inbound:execute' LIMIT 1);" | Out-Null
    }
}

if ($CleanupManifest) {
    $manifestPath = (Resolve-Path -LiteralPath $CleanupManifest).Path
    $fixture = Get-Content -LiteralPath $manifestPath -Encoding UTF8 -Raw | ConvertFrom-Json
    if ($VerifyCompletedManifest) {
        $state = (Invoke-LocalMySql "SELECT CONCAT(pc.status,'|',pt.status,'|',(SELECT COUNT(*) FROM inventory i WHERE i.pallet_code_id=pc.id),'|',(SELECT COUNT(*) FROM pallet_flow_record pfr WHERE pfr.pallet_code_id=pc.id),'|',(SELECT COUNT(*) FROM stock_movement_event sme WHERE sme.pallet_code_id=pc.id),'|',(SELECT cur_capacity FROM warehouse WHERE id=$($fixture.warehouseId))) FROM pallet_code pc JOIN pallet_task pt ON pt.id=$($fixture.taskId) WHERE pc.id=$($fixture.palletId);")[0].Split('|')
        Assert-True ($state[0] -eq 'INSTOCK' -and $state[1] -eq 'CONFIRMED') "S4 browser execution did not commit pallet and task state"
        Assert-True ([int]$state[2] -eq 1 -and [int]$state[3] -eq 1 -and [int]$state[4] -ge 1 -and [int]$state[5] -eq 1) "S4 browser execution business facts are incomplete"
        $control = (Invoke-LocalMySql "SELECT CONCAT(status,'|',attempt_count,'|',result_code) FROM agent_finish_inbound_execution_request WHERE owner_user_id=$($fixture.operatorId) ORDER BY id DESC LIMIT 1;")[0].Split('|')
        Assert-True ($control[0] -eq 'SUCCEEDED' -and [int]$control[1] -eq 1 -and $control[2] -eq 'FINISH_INBOUND_COMPLETED') "S4 browser execution control result is invalid"
        $auditCount = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_finish_inbound_execution_audit WHERE owner_user_id=$($fixture.operatorId) AND event_type IN ('USER_CONFIRMED','EXECUTION_ACCEPTED','EXECUTION_SUCCEEDED');")[0]
        Assert-True ($auditCount -eq 3) "S4 browser execution audit chain is incomplete"
        $executeToolCalls = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_tool_audit_log WHERE user_id=$($fixture.operatorId) AND tool_name LIKE 'execute_%';")[0]
        Assert-True ($executeToolCalls -eq 0) "S4 browser execution unexpectedly used an MCP execute tool"
        [pscustomobject]@{
            Result = 'PASS'
            BrowserDomainExecution = 'PASS'
            AuditEvents = $auditCount
            AttemptCount = [int]$control[1]
            ExecuteToolCalls = $executeToolCalls
            DatabaseHost = $dbHost
        } | ConvertTo-Json
        exit 0
    }
    Remove-BrowserFixture $fixture
    $leftovers = [int](Invoke-LocalMySql "SELECT (SELECT COUNT(*) FROM employee_roster WHERE employee_id='$(Escape-SqlLiteral $fixture.employeeId)') + (SELECT COUNT(*) FROM user WHERE employee_id='$(Escape-SqlLiteral $fixture.employeeId)') + (SELECT COUNT(*) FROM warehouse WHERE warehouse_name='$(Escape-SqlLiteral $fixture.warehouseName)');")[0]
    Assert-True ($leftovers -eq 0) "S4 browser fixture cleanup left $leftovers rows"
    Remove-Item -LiteralPath $manifestPath
    [pscustomobject]@{ Result = 'PASS'; Cleanup = 'COMPLETE'; DatabaseHost = $dbHost } | ConvertTo-Json
    exit 0
}

$migrationFiles = @(
    '2026-08-09-add-agent-finish-inbound-execution-preview.sql',
    '2026-08-10-add-agent-finish-inbound-execution-control-plane.sql',
    '2026-08-10-add-agent-finish-inbound-execute-permission.sql'
)
foreach ($migrationFile in $migrationFiles) {
    $sql = [System.IO.File]::ReadAllText((Join-Path $projectRoot "migrations\$migrationFile"))
    Invoke-LocalMySql $sql | Out-Null
}
$existingPermissionLink = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';")[0]
Assert-True ($existingPermissionLink -eq 0) "Dedicated Agent execute permission must be unassigned before S4 browser UAT"
Invoke-LocalMySql "INSERT INTO role_permission(role_id,permission_id) SELECT r.id,p.id FROM role r JOIN permission p WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';" | Out-Null

$suffix = [Guid]::NewGuid().ToString('N')
$prefix = "S4WEB_$((Get-Date).ToString('MMddHHmm'))$($suffix.Substring(0, 6))"
$fixture = [ordered]@{
    prefix = $prefix
    employeeId = "S4$($suffix.Substring(0, 12))"
    testName = "S4浏览器$($suffix.Substring(0, 8))"
    mobile = "18$([Math]::Abs($suffix.GetHashCode()).ToString().PadLeft(9, '0').Substring(0, 9))"
    warehouseName = $prefix
    employeeRosterId = 0
    operatorId = 0
    palletId = 0
    taskId = 0
    warehouseId = 0
    palletCode = ''
    permissionLinkInserted = $true
}

try {
    $fixture.employeeRosterId = [int](Invoke-LocalMySql "INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$(Escape-SqlLiteral $fixture.employeeId)','$(Escape-SqlLiteral $fixture.testName)','$(Escape-SqlLiteral $fixture.mobile)','本地验收','Agent S4 浏览器','在职','ADMIN'); SELECT LAST_INSERT_ID();")[0]
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/web-login" -ContentType 'application/json; charset=utf-8' -Body (@{ name = $fixture.testName; password = $LoginPassword } | ConvertTo-Json -Compress)
    Assert-True ($login.code -eq 200) "S4 browser test account login failed"
    Assert-True (@($login.data.permissionCodes) -contains 'agent:finish-inbound:execute') "S4 browser test account lacks explicit execute permission"
    $fixture.operatorId = [int](Invoke-LocalMySql "SELECT id FROM user WHERE employee_id='$(Escape-SqlLiteral $fixture.employeeId)' LIMIT 1;")[0]

    $product = (Invoke-LocalMySql "SELECT CONCAT(id,'|',screen_mesh_id) FROM product WHERE product_name='黄冰糖（袋）' AND screen_mesh_id IS NOT NULL AND pieces_per_pallet IS NOT NULL ORDER BY id LIMIT 1;")[0].Split('|')
    $productId = [int]$product[0]
    $screenMeshId = [int]$product[1]
    $fixture.warehouseId = [int](Invoke-LocalMySql "INSERT INTO warehouse(status,max_capacity,cur_capacity,max_rows,warehouse_name) VALUES('空置',4,0,1,'$(Escape-SqlLiteral $fixture.warehouseName)'); SELECT LAST_INSERT_ID();")[0]
    $fixture.palletId = [int](Invoke-LocalMySql "INSERT INTO pallet_code(code,status,product_id,fixed_mode_enabled,product_status,production_date,screen_mesh_id,created_by,current_cycle_no) VALUES(NULL,'PENDING',$productId,0,'成品',CURDATE(),$screenMeshId,$($fixture.operatorId),1); SELECT LAST_INSERT_ID();")[0]
    $fixture.palletCode = New-PalletCode $fixture.palletId
    Invoke-LocalMySql "UPDATE pallet_code SET code='$(Escape-SqlLiteral $fixture.palletCode)' WHERE id=$($fixture.palletId);" | Out-Null
    $fixture.taskId = [int](Invoke-LocalMySql "INSERT INTO pallet_task(pallet_code_id,task_type,status,product_id,product_status,production_date,screen_mesh_id,created_by,remark,cycle_no) VALUES($($fixture.palletId),'FINISH_IN','PENDING',$productId,'成品',CURDATE(),$screenMeshId,$($fixture.operatorId),'$prefix',1); SELECT LAST_INSERT_ID();")[0]

    $manifestDir = Join-Path $projectRoot 'output\s4-uat'
    [System.IO.Directory]::CreateDirectory($manifestDir) | Out-Null
    $manifestPath = Join-Path $manifestDir "$prefix.json"
    $manifestJson = $fixture | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($manifestPath, $manifestJson, [System.Text.UTF8Encoding]::new($false))
    [pscustomobject]@{
        Result = 'PASS'
        TestName = $fixture.testName
        PalletCode = $fixture.palletCode
        WarehouseName = $fixture.warehouseName
        ManifestPath = $manifestPath
        DatabaseHost = $dbHost
    } | ConvertTo-Json
}
catch {
    Remove-BrowserFixture ([pscustomobject]$fixture)
    throw
}
