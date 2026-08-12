param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [string]$BaseUrl = 'http://127.0.0.1:38082',

    [Parameter(Mandatory = $true)]
    [string]$LoginPassword,

    [string]$CleanupManifest,

    [switch]$VerifyCreatedManifest
)

$ErrorActionPreference = 'Stop'

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
        if ($value -lt 0) { throw 'Invalid Base36 character while generating pallet code' }
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
Assert-True ($config['DB_URL'] -match '^jdbc:mysql://([^/:]+)(?::(\d+))?/([^?]+)') 'Unsupported DB_URL'
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$dbName = $Matches[3]
Assert-True ($dbHost -in @('127.0.0.1', 'localhost', '::1')) "Refusing non-local database host: $dbHost"

$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
Assert-True (Test-Path -LiteralPath $mysql) 'mysql.exe not found'

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
    $userId = [int]$Fixture.operatorId
    $warehouseId = [int]$Fixture.warehouseId
    $employeeRosterId = [int]$Fixture.employeeRosterId
    $palletIds = @($Fixture.palletIds | ForEach-Object { [int]$_ } | Where-Object { $_ -gt 0 })
    if ($userId -gt 0) {
        $sessionRows = @(Invoke-LocalMySql "SELECT id FROM agent_session WHERE user_id=$userId;")
        if ($sessionRows.Count -gt 0) {
            $sessionList = ($sessionRows | ForEach-Object { "'$(Escape-SqlLiteral ([string]$_))'" }) -join ','
            Invoke-LocalMySql "DELETE FROM agent_message_review_evidence WHERE review_id IN (SELECT id FROM agent_message_review WHERE agent_session_id IN ($sessionList)); DELETE FROM agent_message_review WHERE agent_session_id IN ($sessionList); DELETE FROM agent_interrupt_state WHERE agent_session_id IN ($sessionList); DELETE FROM agent_api_audit_log WHERE agent_session_id IN ($sessionList); DELETE FROM agent_tool_audit_log WHERE agent_session_id IN ($sessionList);" | Out-Null
        }
        Invoke-LocalMySql "DELETE FROM agent_finish_inbound_execution_audit WHERE owner_user_id=$userId; DELETE FROM agent_finish_inbound_execution_request WHERE owner_user_id=$userId; DELETE FROM agent_finish_inbound_execution_confirmation WHERE owner_user_id=$userId; DELETE FROM agent_finish_inbound_execution_preview WHERE owner_user_id=$userId; DELETE FROM agent_task_transition_preview WHERE owner_user_id=$userId; DELETE FROM agent_session WHERE user_id=$userId;" | Out-Null
    }
    if ($palletIds.Count -gt 0) {
        $idList = $palletIds -join ','
        Invoke-LocalMySql "DELETE FROM pallet_task_semi_item WHERE pallet_task_id IN (SELECT id FROM pallet_task WHERE pallet_code_id IN ($idList)); DELETE FROM stock_movement_event WHERE pallet_code_id IN ($idList); DELETE FROM pallet_flow_record WHERE pallet_code_id IN ($idList); DELETE FROM inventory WHERE pallet_code_id IN ($idList); DELETE FROM pallet_task WHERE pallet_code_id IN ($idList); DELETE FROM pallet_code WHERE id IN ($idList);" | Out-Null
    }
    if ($warehouseId -gt 0) {
        Invoke-LocalMySql "DELETE FROM inventory WHERE warehouse_id=$warehouseId; DELETE FROM in_stock WHERE warehouse_id=$warehouseId; DELETE FROM warehouse WHERE id=$warehouseId;" | Out-Null
    }
    if ($userId -gt 0) { Invoke-LocalMySql "DELETE FROM user WHERE id=$userId;" | Out-Null }
    if ($employeeRosterId -gt 0) { Invoke-LocalMySql "DELETE FROM employee_roster WHERE id=$employeeRosterId;" | Out-Null }
    Invoke-LocalMySql "DELETE FROM operation_log WHERE operator='$(Escape-SqlLiteral ([string]$Fixture.testName))' AND table_name='pallet_code';" | Out-Null
}

if ($CleanupManifest) {
    $manifestPath = (Resolve-Path -LiteralPath $CleanupManifest).Path
    $fixture = Get-Content -LiteralPath $manifestPath -Encoding UTF8 -Raw | ConvertFrom-Json
    if ($VerifyCreatedManifest) {
        $idList = (@($fixture.palletIds) | ForEach-Object { [int]$_ }) -join ','
        $business = (Invoke-LocalMySql "SELECT CONCAT((SELECT COUNT(*) FROM pallet_code WHERE id IN ($idList) AND status='PENDING' AND fixed_mode_enabled=1),(SELECT CONCAT('|',COUNT(*)) FROM pallet_task WHERE pallet_code_id IN ($idList) AND task_type='FINISH_IN' AND status='PENDING'),(SELECT CONCAT('|',COUNT(*)) FROM inventory WHERE pallet_code_id IN ($idList)),(SELECT CONCAT('|',COUNT(*)) FROM pallet_flow_record WHERE pallet_code_id IN ($idList)),(SELECT CONCAT('|',COUNT(*)) FROM pallet_flow_record f JOIN pallet_task t ON t.id=f.task_id AND t.pallet_code_id=f.pallet_code_id WHERE f.pallet_code_id IN ($idList) AND f.operation_type='FINISH_BIND' AND f.remark='固定产品二维码打印并启用' AND t.task_type='FINISH_IN'),(SELECT CONCAT('|',COUNT(*)) FROM stock_movement_event WHERE pallet_code_id IN ($idList))); ")[0].Split('|')
        Assert-True ([int]$business[0] -eq 2 -and [int]$business[1] -eq 2) 'Fixed QR task creation did not produce two pending finish-inbound tasks'
        Assert-True ([int]$business[3] -eq 2 -and [int]$business[4] -eq 2) 'Fixed QR task creation did not produce exactly two expected FINISH_BIND traceability facts'
        Assert-True ([int]$business[2] -eq 0 -and [int]$business[5] -eq 0) 'Fixed QR task creation unexpectedly wrote inventory or stock movement facts'
        $previews = (Invoke-LocalMySql "SELECT CONCAT((SELECT COUNT(*) FROM agent_task_transition_preview WHERE owner_user_id=$($fixture.operatorId)), '|', (SELECT COUNT(*) FROM agent_finish_inbound_execution_preview WHERE owner_user_id=$($fixture.operatorId)), '|', (SELECT COUNT(*) FROM agent_tool_audit_log WHERE user_id=$($fixture.operatorId) AND tool_name LIKE 'execute_%'), '|', (SELECT COUNT(*) FROM agent_finish_inbound_execution_request WHERE owner_user_id=$($fixture.operatorId))); ")[0].Split('|')
        Assert-True ([int]$previews[0] -ge 1) 'Task transition preview was not archived'
        Assert-True ([int]$previews[1] -ge 1) 'Exact finish-inbound preview was not archived'
        Assert-True ([int]$previews[2] -eq 0 -and [int]$previews[3] -eq 0) 'Browser journey unexpectedly reached an execute tool or execution request'
        [pscustomobject]@{
            Result = 'PASS'
            FixedQrPendingTasks = [int]$business[1]
            InventoryWrites = [int]$business[2]
            BindingAuditFlows = [int]$business[4]
            UnexpectedFlowWrites = [int]$business[3] - [int]$business[4]
            StockMovementWrites = [int]$business[5]
            TaskTransitionPreviews = [int]$previews[0]
            ExactExecutionPreviews = [int]$previews[1]
            ExecuteToolCalls = [int]$previews[2]
            ExecutionRequests = [int]$previews[3]
            DatabaseHost = $dbHost
        } | ConvertTo-Json
        exit 0
    }
    Remove-BrowserFixture $fixture
    $leftovers = [int](Invoke-LocalMySql "SELECT (SELECT COUNT(*) FROM employee_roster WHERE employee_id='$(Escape-SqlLiteral $fixture.employeeId)') + (SELECT COUNT(*) FROM user WHERE employee_id='$(Escape-SqlLiteral $fixture.employeeId)') + (SELECT COUNT(*) FROM warehouse WHERE id=$($fixture.warehouseId)) + (SELECT COUNT(*) FROM pallet_code WHERE id IN ($((@($fixture.palletIds) | ForEach-Object { [int]$_ }) -join ','))); ")[0]
    Assert-True ($leftovers -eq 0) "Guided browser fixture cleanup left $leftovers rows"
    Remove-Item -LiteralPath $manifestPath
    [pscustomobject]@{ Result = 'PASS'; Cleanup = 'COMPLETE'; DatabaseHost = $dbHost } | ConvertTo-Json
    exit 0
}

$requiredPermissions = @('system:access', 'dashboard:view', 'product:view', 'warehouse:view', 'inventory:view', 'task:view', 'task:confirm', 'qrcode:view', 'qrcode:pool_view', 'qrcode:activate')
$permissionList = ($requiredPermissions | ForEach-Object { "'$(Escape-SqlLiteral $_)'" }) -join ','
$adminPermissionCount = [int](Invoke-LocalMySql "SELECT COUNT(DISTINCT p.perm_code) FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='ADMIN' AND p.perm_code IN ($permissionList);")[0]
Assert-True ($adminPermissionCount -eq $requiredPermissions.Count) 'ADMIN is missing a required guided-browser permission'
Assert-True ([int](Invoke-LocalMySql "SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';")[0] -eq 0) 'Dedicated Agent execute permission must remain unassigned'
Assert-True ([int](Invoke-LocalMySql "SELECT COUNT(*) FROM warehouse WHERE warehouse_name='3号库位';")[0] -eq 0) 'Temporary 3号库位 conflicts with existing data'

$product = (Invoke-LocalMySql "SELECT CONCAT(id,'|',screen_mesh_id,'|',weight_per_piece,'|',pieces_per_pallet) FROM product WHERE product_name='黄冰糖（袋）' AND status='成品' AND screen_mesh_id IS NOT NULL AND pieces_per_pallet IS NOT NULL ORDER BY id LIMIT 1;")[0].Split('|')
Assert-True ($product.Count -eq 4) 'Required finished yellow rock sugar product was not found'
$productId = [int]$product[0]
$suffix = [Guid]::NewGuid().ToString('N')
$fixture = [ordered]@{
    prefix = "GUIDED_$((Get-Date).ToString('MMddHHmm'))$($suffix.Substring(0, 6))"
    employeeId = "GD$($suffix.Substring(0, 12))"
    testName = "引导验收$($suffix.Substring(0, 8))"
    mobile = "16$([Math]::Abs($suffix.GetHashCode()).ToString().PadLeft(9, '0').Substring(0, 9))"
    employeeRosterId = 0
    operatorId = 0
    warehouseId = 0
    palletIds = @()
    palletCodes = @()
    productId = $productId
    productLabel = '黄冰糖（袋） 25kg/件 40件/板'
}

try {
    $fixture.employeeRosterId = [int](Invoke-LocalMySql "INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$(Escape-SqlLiteral $fixture.employeeId)','$(Escape-SqlLiteral $fixture.testName)','$(Escape-SqlLiteral $fixture.mobile)','本地验收','固定码引导验收','在职','ADMIN'); SELECT LAST_INSERT_ID();")[0]
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/web-login" -ContentType 'application/json; charset=utf-8' -Body (@{ name = $fixture.testName; password = $LoginPassword } | ConvertTo-Json -Compress)
    Assert-True ($login.code -eq 200) 'Guided browser test account login failed'
    foreach ($permission in $requiredPermissions) {
        Assert-True (@($login.data.permissionCodes) -contains $permission) "Guided browser test account lacks $permission"
    }
    Assert-True (@($login.data.permissionCodes) -notcontains 'agent:finish-inbound:execute') 'Guided browser account unexpectedly has Agent execute permission'
    $fixture.operatorId = [int](Invoke-LocalMySql "SELECT id FROM user WHERE employee_id='$(Escape-SqlLiteral $fixture.employeeId)' LIMIT 1;")[0]
    $fixture.warehouseId = [int](Invoke-LocalMySql "INSERT INTO warehouse(status,max_capacity,cur_capacity,max_rows,warehouse_name) VALUES('空置',4,0,1,'3号库位'); SELECT LAST_INSERT_ID();")[0]
    for ($index = 0; $index -lt 2; $index++) {
        $palletId = [int](Invoke-LocalMySql "INSERT INTO pallet_code(code,status,product_id,fixed_product_id,fixed_mode_enabled,product_status,production_date,screen_mesh_id,created_by,current_cycle_no) VALUES(NULL,'FREE',NULL,$productId,1,NULL,NULL,NULL,$($fixture.operatorId),0); SELECT LAST_INSERT_ID();")[0]
        $palletCode = New-PalletCode $palletId
        Invoke-LocalMySql "UPDATE pallet_code SET code='$(Escape-SqlLiteral $palletCode)' WHERE id=$palletId;" | Out-Null
        $fixture.palletIds += $palletId
        $fixture.palletCodes += $palletCode
    }

    $workspaceRoot = if ($PSScriptRoot) {
        (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    }
    else {
        (Get-Location).Path
    }
    Assert-True (Test-Path -LiteralPath (Join-Path $workspaceRoot 'pom.xml')) 'Run the fixture script from the project root'
    $manifestDir = Join-Path $workspaceRoot 'output\guided-inbound-uat'
    [System.IO.Directory]::CreateDirectory($manifestDir) | Out-Null
    $manifestPath = Join-Path $manifestDir "$($fixture.prefix).json"
    [System.IO.File]::WriteAllText($manifestPath, ($fixture | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
    [pscustomobject]@{
        Result = 'PASS'
        TestName = $fixture.testName
        ProductLabel = $fixture.productLabel
        WarehouseName = '3号库位'
        PalletCodes = @($fixture.palletCodes)
        ManifestPath = $manifestPath
        DatabaseHost = $dbHost
    } | ConvertTo-Json
}
catch {
    Remove-BrowserFixture ([pscustomobject]$fixture)
    throw
}
