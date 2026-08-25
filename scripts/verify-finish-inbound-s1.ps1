param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [string]$BaseUrl = "http://127.0.0.1:38082",

    [string]$LoginPassword = "lbsp",

    [ValidateSet('ADMIN', 'WAREHOUSE_MANAGER')]
    [string]$ExecutionRoleCode = 'ADMIN',

    [switch]$VerifyS2ControlPlane,

    [switch]$VerifyS3DomainExecution,

    [switch]$VerifyS4AuditRollback,

    [switch]$VerifyS4StateInvalidation
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
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw "ASSERTION FAILED: $Message"
    }
}

function Escape-SqlLiteral {
    param([string]$Value)
    return $Value.Replace("'", "''")
}

function Convert-ToBase36 {
    param([long]$Value)
    $chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    if ($Value -eq 0) {
        return "0"
    }
    $result = ""
    while ($Value -gt 0) {
        $remainder = [int]($Value % 36)
        $result = $chars[$remainder] + $result
        $Value = [math]::Floor($Value / 36)
    }
    return $result
}

function New-PalletCode {
    param([long]$Id)
    $chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    $mixed = (($Id * 13L) + 5L) % 60466176L
    $withoutCheck = "BT$((Convert-ToBase36 $mixed).PadLeft(5, '0'))"
    $sum = 0
    foreach ($char in $withoutCheck.ToCharArray()) {
        $value = $chars.IndexOf([string]$char)
        if ($value -lt 0) {
            throw "Invalid Base36 character while generating pallet code"
        }
        $sum += $value
    }
    return $withoutCheck + $chars[$sum % 36]
}

$baseUri = [Uri]$BaseUrl
Assert-True ($baseUri.Host -in @('127.0.0.1', 'localhost', '::1')) "Refusing non-local application host: $($baseUri.Host)"

$config = @{}
foreach ($line in [System.IO.File]::ReadAllLines($EnvFile)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $config[$Matches[1]] = $Matches[2]
    }
}
foreach ($requiredKey in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    Assert-True ($config.ContainsKey($requiredKey)) "Missing $requiredKey in env file"
}

$jdbcUrl = $config['DB_URL']
Assert-True ($jdbcUrl -match '^jdbc:mysql://([^/:?]+)(?::([0-9]+))?/([^?]+)') "Unsupported DB_URL"
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
            '--raw' `
            '--skip-column-names' `
            "--execute=$Sql"
        if ($LASTEXITCODE -ne 0) {
            throw "mysql exited with code $LASTEXITCODE"
        }
        Write-Output -NoEnumerate @($output)
        return
    }
    finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Invoke-AgentPost {
    param(
        [string]$Path,
        [object]$Body,
        [string]$Token
    )
    $headers = @{}
    if ($Token) {
        $headers['Authorization'] = "Bearer $Token"
    }
    return Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl$Path" `
        -Headers $headers `
        -ContentType 'application/json; charset=utf-8' `
        -Body ($Body | ConvertTo-Json -Depth 12 -Compress) `
        -TimeoutSec 150
}

$suffix = "$([DateTime]::Now.ToString('MMddHHmmss'))$([Guid]::NewGuid().ToString('N').Substring(0, 4))"
$prefix = "S1UAT_$suffix"
$testName = "S1验收$($suffix.Substring(0, 8))"
$employeeId = "S1$($suffix.Substring(0, 12))"
$mobile = "19$([Math]::Abs($suffix.GetHashCode()).ToString().PadLeft(9, '0').Substring(0, 9))"
$warehouseName = $prefix
$operatorId = $null
$employeeRosterId = $null
$palletId = $null
$taskId = $null
$warehouseId = $null
$agentSessionId = $null
$s3RolePermissionInserted = $false
$s4AuditTriggerName = $null
$s4StateDelayTriggerName = $null
$s4StateJob = $null
$requiresS3Permission = $VerifyS3DomainExecution -or $VerifyS4AuditRollback -or $VerifyS4StateInvalidation
$isWarehouseRoleUat = $ExecutionRoleCode -eq 'WAREHOUSE_MANAGER'
$s4PermissionDeniedBeforeGrant = $false

try {
    $selectedExecutionModes = @(
        [bool]$VerifyS2ControlPlane,
        [bool]$VerifyS3DomainExecution,
        [bool]$VerifyS4AuditRollback,
        [bool]$VerifyS4StateInvalidation
    ) | Where-Object { $_ }
    Assert-True ($selectedExecutionModes.Count -le 1) "S2, S3 and S4 verification modes must use separate fixtures"
    Assert-True (-not $isWarehouseRoleUat -or $VerifyS3DomainExecution) "WAREHOUSE_MANAGER is only supported for the S3 domain execution UAT"
    $migration = [System.IO.File]::ReadAllText(
        (Join-Path $projectRoot 'migrations\2026-08-09-add-agent-finish-inbound-execution-preview.sql'))
    Invoke-LocalMySql $migration | Out-Null
    if ($VerifyS2ControlPlane -or $requiresS3Permission) {
        $controlMigration = [System.IO.File]::ReadAllText(
            (Join-Path $projectRoot 'migrations\2026-08-10-add-agent-finish-inbound-execution-control-plane.sql'))
        Invoke-LocalMySql $controlMigration | Out-Null
    }
    if ($requiresS3Permission) {
        $permissionMigration = [System.IO.File]::ReadAllText(
            (Join-Path $projectRoot 'migrations\2026-08-10-add-agent-finish-inbound-execute-permission.sql'))
        Invoke-LocalMySql $permissionMigration | Out-Null
        $existingPermissionLink = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';")[0]
        Assert-True ($existingPermissionLink -eq 0) "Dedicated Agent execute permission is already assigned to ADMIN; S4 default-deny proof requires zero baseline assignments"
        if ($isWarehouseRoleUat) {
            $warehousePermissionMigration = [System.IO.File]::ReadAllText(
                (Join-Path $projectRoot 'migrations\2026-08-13-grant-warehouse-manager-controlled-finish-inbound.sql'))
            Invoke-LocalMySql $warehousePermissionMigration | Out-Null
        }
    }

    $escapedName = Escape-SqlLiteral $testName
    $escapedEmployeeId = Escape-SqlLiteral $employeeId
    $escapedMobile = Escape-SqlLiteral $mobile
    $employeeRosterId = [int](Invoke-LocalMySql "INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$escapedEmployeeId','$escapedName','$escapedMobile','本地验收','Agent S1','在职','$(Escape-SqlLiteral $ExecutionRoleCode)'); SELECT LAST_INSERT_ID();")[0]

    $login = Invoke-AgentPost '/api/auth/web-login' @{ name = $testName; password = $LoginPassword } ''
    Assert-True ($login.code -eq 200) "Web login failed"
    Assert-True ([bool]$login.data.token) "Web login returned no token"
    Assert-True (@($login.data.permissionCodes) -contains 'task:view') "Temporary ADMIN lacks task:view"
    if ($isWarehouseRoleUat) {
        Assert-True (@($login.data.permissionCodes) -notcontains 'task:confirm') "WAREHOUSE_MANAGER unexpectedly received generic task:confirm"
        Assert-True (@($login.data.permissionCodes) -contains 'agent:finish-inbound:execute') "WAREHOUSE_MANAGER lacks controlled finish inbound execution"
    } else {
        Assert-True (@($login.data.permissionCodes) -contains 'task:confirm') "Temporary ADMIN lacks task:confirm"
    }
    if ($requiresS3Permission) {
        Assert-True ($isWarehouseRoleUat -or -not (@($login.data.permissionCodes) -contains 'agent:finish-inbound:execute')) "Temporary ADMIN unexpectedly received the default-deny Agent execute permission"
    }
    $token = $login.data.token

    $userRows = Invoke-LocalMySql "SELECT id FROM user WHERE employee_id='$escapedEmployeeId' LIMIT 1;"
    Assert-True ($userRows.Count -eq 1) "Temporary ADMIN user was not created"
    $operatorId = [int]$userRows[0]

    $session = Invoke-AgentPost '/api/agent/sessions' @{
        clientType = 'S1_UAT'
        requestedScopes = @('mcp:warehouse:read')
        mcpTransport = 'STDIO'
    } $token
    Assert-True ($session.code -eq 200) "Agent session creation failed"
    $agentSessionId = [string]$session.data.agentSessionId
    Assert-True ([bool]$agentSessionId) "Agent session id is missing"

    $productRows = Invoke-LocalMySql "SELECT CONCAT(id,'|',screen_mesh_id) FROM product WHERE product_name='黄冰糖（袋）' AND screen_mesh_id IS NOT NULL AND pieces_per_pallet IS NOT NULL ORDER BY id LIMIT 1;"
    Assert-True ($productRows.Count -eq 1) "Suitable finished product fixture was not found"
    $productParts = $productRows[0].Split('|')
    $productId = [int]$productParts[0]
    $screenMeshId = [int]$productParts[1]

    $escapedWarehouse = Escape-SqlLiteral $warehouseName
    $warehouseId = [int](Invoke-LocalMySql "INSERT INTO warehouse(status,max_capacity,cur_capacity,max_rows,warehouse_name) VALUES('空置',4,0,1,'$escapedWarehouse'); SELECT LAST_INSERT_ID();")[0]
    $palletId = [int](Invoke-LocalMySql "INSERT INTO pallet_code(code,status,product_id,fixed_mode_enabled,product_status,production_date,screen_mesh_id,created_by,current_cycle_no) VALUES(NULL,'PENDING',$productId,0,'成品',CURDATE(),$screenMeshId,$operatorId,1); SELECT LAST_INSERT_ID();")[0]
    $palletCode = New-PalletCode $palletId
    $escapedPalletCode = Escape-SqlLiteral $palletCode
    Invoke-LocalMySql "UPDATE pallet_code SET code='$escapedPalletCode' WHERE id=$palletId;" | Out-Null
    $taskId = [int](Invoke-LocalMySql "INSERT INTO pallet_task(pallet_code_id,task_type,status,product_id,product_status,production_date,screen_mesh_id,created_by,remark,cycle_no) VALUES($palletId,'FINISH_IN','PENDING',$productId,'成品',CURDATE(),$screenMeshId,$operatorId,'$prefix',1); SELECT LAST_INSERT_ID();")[0]

    $payload = @{
        previewVersion = 1
        items = @(
            @{
                code = $palletCode
                warehouseName = $warehouseName
                entryDate = [DateTime]::Now.ToString('yyyy-MM-dd')
                side = '左'
                quantity = 1
                unit = '0'
                remark = 'S1 精确预览验收'
            }
        )
    }
    $payloadJson = $payload | ConvertTo-Json -Depth 8
    $message = "请生成成品入库精确执行预览（只预览，不执行）。以下是我已经填写的表单：`n`n``````json`n$payloadJson`n``````"
    $response = Invoke-AgentPost "/api/agent/sessions/$agentSessionId/messages" @{
        message = $message
        pageContext = @{}
    } $token

    Assert-True ($response.code -eq 200) "Agent message failed"
    Assert-True ($null -eq $response.data.error) "Agent returned an error"
    Assert-True ([bool]$response.data.answer) "Agent returned no answer"
    $previewCards = @($response.data.cards | Where-Object { $_.cardType -eq 'finish_inbound_execution_preview' })
    if ($previewCards.Count -ne 1) {
        $cardTypes = @($response.data.cards | ForEach-Object { [string]$_.cardType }) -join ','
        $safeAnswer = ([string]$response.data.answer).Replace("`r", ' ').Replace("`n", ' ')
        $runtimeAudit = @((Invoke-LocalMySql "SELECT CONCAT(IFNULL(result_code,''),'|',IFNULL(error_code,'')) FROM agent_tool_audit_log WHERE agent_session_id='$(Escape-SqlLiteral $agentSessionId)' AND tool_name='agent_runtime' ORDER BY id DESC LIMIT 1;") | Select-Object -First 1)
        $runtimeAuditText = if ($runtimeAudit.Count) { [string]$runtimeAudit[0] } else { 'missing' }
        throw "ASSERTION FAILED: Expected one exact inbound preview card; cardTypes=[$cardTypes]; runtimeAudit=$runtimeAuditText; answer=$safeAnswer"
    }
    $visibleJson = $response.data | ConvertTo-Json -Depth 16 -Compress
    Assert-True ($visibleJson.Contains($palletCode)) "Visible response does not contain the pallet code"
    Assert-True ($visibleJson.Contains($warehouseName)) "Visible response does not contain the target warehouse"
    foreach ($forbidden in @('fip1_', 'previewRef', 'stateDigest', 'serverSnapshot', 'taskId', 'warehouseId', 'rowNumber', 'layer', 'execute_finish_inbound_task')) {
        Assert-True (-not $visibleJson.Contains($forbidden)) "Visible response exposed forbidden field: $forbidden"
    }

    $archiveCount = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_finish_inbound_execution_preview WHERE agent_session_id='$agentSessionId';")[0]
    Assert-True ($archiveCount -eq 1) "Expected exactly one persisted exact preview"
    $archive = (Invoke-LocalMySql "SELECT CONCAT(preview_ref,'|',status,'|',required_permissions,'|',owner_user_id) FROM agent_finish_inbound_execution_preview WHERE agent_session_id='$agentSessionId' LIMIT 1;")[0].Split('|')
    Assert-True ($archive[0] -match '^fip1_[A-Za-z0-9_-]{43}$') "Stored preview ref is invalid"
    Assert-True ($archive[1] -eq 'READY') "Stored preview is not READY"
    $expectedPreviewPermission = if ($isWarehouseRoleUat) { 'agent:finish-inbound:execute,task:view' } else { 'task:confirm,task:view' }
    Assert-True ($archive[2] -eq $expectedPreviewPermission) "Stored permission snapshot is invalid"
    Assert-True ([int]$archive[3] -eq $operatorId) "Stored preview owner is invalid"

    if ($requiresS3Permission -and -not $isWarehouseRoleUat) {
        $denied = Invoke-AgentPost "/api/agent/sessions/$agentSessionId/finish-inbound-execution/s3/pending-preview" @{
            palletCodes = @($palletCode)
        } $token
        Assert-True ($denied.code -eq 403) "S3 controlled endpoint did not reject the default-deny account"
        Assert-True ([string]$denied.msg -notmatch 'agent:finish-inbound:execute|task:confirm|task:view') "Permission denial exposed internal authority names"
        $s4PermissionDeniedBeforeGrant = $true

        Invoke-LocalMySql "INSERT INTO role_permission(role_id,permission_id) SELECT r.id,p.id FROM role r JOIN permission p WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';" | Out-Null
        $s3RolePermissionInserted = $true
        $login = Invoke-AgentPost '/api/auth/web-login' @{ name = $testName; password = $LoginPassword } ''
        Assert-True ($login.code -eq 200) "Web re-login after temporary permission grant failed"
        Assert-True (@($login.data.permissionCodes) -contains 'agent:finish-inbound:execute') "Temporary ADMIN did not receive the explicit Agent execute permission"
        $token = $login.data.token
    }

    $s2ExecutionRef = $null
    $s2AuditCount = 0
    if ($VerifyS2ControlPlane) {
        $confirmation = Invoke-AgentPost "/api/agent/sessions/$agentSessionId/finish-inbound-execution/confirmations" @{
            previewRef = $archive[0]
        } $token
        Assert-True ($confirmation.code -eq 200) "S2 confirmation failed"
        Assert-True ([string]$confirmation.data.confirmationRef -match '^fic1_[A-Za-z0-9_-]{43}$') "S2 confirmation ref is invalid"
        Assert-True ([string]$confirmation.data.executionToken -match '^fiet1_[A-Za-z0-9_-]{43}$') "S2 execution token is invalid"
        Assert-True ([string]$confirmation.data.idempotencyKey -match '^fii1_[A-Za-z0-9_-]{43}$') "S2 idempotency key is invalid"

        $confirmationRetry = Invoke-AgentPost "/api/agent/sessions/$agentSessionId/finish-inbound-execution/confirmations" @{
            previewRef = $archive[0]
        } $token
        Assert-True ($confirmationRetry.code -eq 200) "S2 repeated confirmation failed"
        Assert-True ([bool]$confirmationRetry.data.replayed) "S2 repeated confirmation was not marked as replayed"
        Assert-True ($confirmationRetry.data.executionToken -eq $confirmation.data.executionToken) "S2 repeated confirmation returned a different token"
        Assert-True ($confirmationRetry.data.idempotencyKey -eq $confirmation.data.idempotencyKey) "S2 repeated confirmation returned a different idempotency key"

        $consumePath = "/api/agent/sessions/$agentSessionId/finish-inbound-execution/s2-noop/confirmations/$($confirmation.data.confirmationRef)/consume"
        $consumeBody = @{
            executionToken = $confirmation.data.executionToken
            idempotencyKey = $confirmation.data.idempotencyKey
        }
        $consumed = Invoke-AgentPost $consumePath $consumeBody $token
        Assert-True ($consumed.code -eq 200) "S2 no-op consume failed"
        Assert-True ($consumed.data.executionStatus -eq 'SUCCEEDED') "S2 no-op consume did not succeed"
        Assert-True ([int]$consumed.data.businessWrites -eq 0) "S2 no-op adapter reported a business write"
        $s2ExecutionRef = [string]$consumed.data.executionRef

        $consumeRetry = Invoke-AgentPost $consumePath $consumeBody $token
        Assert-True ($consumeRetry.code -eq 200) "S2 idempotent retry failed"
        Assert-True ([bool]$consumeRetry.data.replayed) "S2 idempotent retry was not marked as replayed"
        Assert-True ($consumeRetry.data.executionRef -eq $s2ExecutionRef) "S2 idempotent retry returned a different execution ref"
        Assert-True ([int]$consumeRetry.data.businessWrites -eq 0) "S2 retry reported a business write"

        $confirmationRow = (Invoke-LocalMySql "SELECT CONCAT(status,'|',LENGTH(token_sha256),'|',LENGTH(idempotency_key_sha256)) FROM agent_finish_inbound_execution_confirmation WHERE confirmation_ref='$(Escape-SqlLiteral $confirmation.data.confirmationRef)' LIMIT 1;")[0].Split('|')
        Assert-True ($confirmationRow[0] -eq 'CONSUMED') "S2 confirmation was not consumed"
        Assert-True ([int]$confirmationRow[1] -eq 64 -and [int]$confirmationRow[2] -eq 64) "S2 credential hashes are invalid"
        $requestRow = (Invoke-LocalMySql "SELECT CONCAT(status,'|',attempt_count,'|',result_code) FROM agent_finish_inbound_execution_request WHERE execution_ref='$(Escape-SqlLiteral $s2ExecutionRef)' LIMIT 1;")[0].Split('|')
        Assert-True ($requestRow[0] -eq 'SUCCEEDED') "S2 execution request did not persist success"
        Assert-True ([int]$requestRow[1] -eq 1) "S2 idempotent retry unexpectedly created another attempt"
        Assert-True ($requestRow[2] -eq 'S2_NOOP_VALIDATED') "S2 result code is invalid"
        $s2AuditCount = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_finish_inbound_execution_audit WHERE confirmation_ref='$(Escape-SqlLiteral $confirmation.data.confirmationRef)' AND event_type IN ('USER_CONFIRMED','EXECUTION_ACCEPTED','EXECUTION_SUCCEEDED');")[0]
        Assert-True ($s2AuditCount -eq 3) "S2 append-only audit chain is incomplete"
    }

    $s3ExecutionRef = $null
    $s3AuditCount = 0
    $s3Replayed = $false
    $s4AuditRollbackPassed = $false
    $s4AuditRecoveryPassed = $false
    $s4StateInvalidationPassed = $false
    if ($VerifyS3DomainExecution) {
        $pending = Invoke-AgentPost "/api/agent/sessions/$agentSessionId/finish-inbound-execution/s3/pending-preview" @{
            palletCodes = @($palletCode)
        } $token
        Assert-True ($pending.code -eq 200) "S3 pending controlled preview failed"
        Assert-True ([string]$pending.data.previewRef -eq $archive[0]) "S3 pending preview did not resolve the current immutable preview"
        Assert-True (@($pending.data.items).Count -eq 1) "S3 pending preview item count is invalid"
        $pendingJson = $pending.data | ConvertTo-Json -Depth 10 -Compress
        Assert-True (-not $pendingJson.Contains('executionToken') -and -not $pendingJson.Contains('idempotencyKey')) "S3 pending UI response exposed execution credentials"

        $executePath = "/api/agent/sessions/$agentSessionId/finish-inbound-execution/s3/previews/$($archive[0])/confirm-and-execute"
        $executed = Invoke-AgentPost $executePath @{} $token
        Assert-True ($executed.code -eq 200) "S3 controlled execution failed"
        Assert-True ($executed.data.statusLabel -eq '成品入库已完成') "S3 returned an unexpected user status"
        Assert-True ([int]$executed.data.affectedPalletCount -eq 1) "S3 affected pallet count is invalid"
        Assert-True (@($executed.data.palletCodes) -contains $palletCode) "S3 result is missing the business pallet code"
        Assert-True (-not [bool]$executed.data.replayed) "First S3 execution was unexpectedly replayed"

        $executeRetry = Invoke-AgentPost $executePath @{} $token
        Assert-True ($executeRetry.code -eq 200) "S3 committed result replay failed"
        Assert-True ([bool]$executeRetry.data.replayed) "S3 retry did not replay the committed result"
        Assert-True ([int]$executeRetry.data.affectedPalletCount -eq 1) "S3 replay returned a different result"
        $s3Replayed = [bool]$executeRetry.data.replayed

        $confirmationRow = (Invoke-LocalMySql "SELECT CONCAT(confirmation_ref,'|',status,'|',required_permissions) FROM agent_finish_inbound_execution_confirmation WHERE agent_session_id='$agentSessionId' LIMIT 1;")[0].Split('|')
        Assert-True ($confirmationRow[1] -eq 'CONSUMED') "S3 confirmation was not consumed"
        Assert-True ($confirmationRow[2] -eq 'agent:finish-inbound:execute,task:view') "S3 dedicated permission snapshot is invalid"
        $requestRow = (Invoke-LocalMySql "SELECT CONCAT(execution_ref,'|',status,'|',attempt_count,'|',result_code) FROM agent_finish_inbound_execution_request WHERE agent_session_id='$agentSessionId' LIMIT 1;")[0].Split('|')
        $s3ExecutionRef = $requestRow[0]
        Assert-True ($requestRow[1] -eq 'SUCCEEDED') "S3 execution request did not persist success"
        Assert-True ([int]$requestRow[2] -eq 1) "S3 replay unexpectedly created another attempt"
        Assert-True ($requestRow[3] -eq 'FINISH_INBOUND_COMPLETED') "S3 result code is invalid"
        $s3AuditCount = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_finish_inbound_execution_audit WHERE confirmation_ref='$(Escape-SqlLiteral $confirmationRow[0])' AND event_type IN ('USER_CONFIRMED','EXECUTION_ACCEPTED','EXECUTION_SUCCEEDED');")[0]
        Assert-True ($s3AuditCount -eq 3) "S3 append-only audit chain is incomplete"
    }

    if ($VerifyS4AuditRollback) {
        $pending = Invoke-AgentPost "/api/agent/sessions/$agentSessionId/finish-inbound-execution/s3/pending-preview" @{
            palletCodes = @($palletCode)
        } $token
        Assert-True ($pending.code -eq 200) "S4 audit rollback pending preview failed"
        Assert-True ([string]$pending.data.previewRef -eq $archive[0]) "S4 pending preview did not resolve the immutable preview"

        $executePath = "/api/agent/sessions/$agentSessionId/finish-inbound-execution/s3/previews/$($archive[0])/confirm-and-execute"
        $s4AuditTriggerName = "s4_fi_audit_$($suffix.Substring(0, 12))"
        Invoke-LocalMySql "DROP TRIGGER IF EXISTS $s4AuditTriggerName; CREATE TRIGGER $s4AuditTriggerName BEFORE INSERT ON agent_finish_inbound_execution_audit FOR EACH ROW SET NEW.event_type = IF(NEW.event_type='EXECUTION_SUCCEEDED', NULL, NEW.event_type);" | Out-Null

        $failed = Invoke-AgentPost $executePath @{} $token
        Assert-True ($failed.code -eq 500) "S4 injected audit failure did not fail the controlled execution"
        Assert-True ([string]$failed.msg -eq '本次成品入库未完成，可使用同一确认安全重试') "S4 audit failure returned an unsafe or unstable user message"

        $rolledBackState = (Invoke-LocalMySql "SELECT CONCAT(pc.status,'|',pt.status,'|',(SELECT COUNT(*) FROM inventory i WHERE i.pallet_code_id=pc.id),'|',(SELECT COUNT(*) FROM pallet_flow_record pfr WHERE pfr.pallet_code_id=pc.id),'|',(SELECT COUNT(*) FROM stock_movement_event sme WHERE sme.pallet_code_id=pc.id),'|',(SELECT cur_capacity FROM warehouse WHERE id=$warehouseId)) FROM pallet_code pc JOIN pallet_task pt ON pt.id=$taskId WHERE pc.id=$palletId;")[0].Split('|')
        Assert-True ($rolledBackState[0] -eq 'PENDING' -and $rolledBackState[1] -eq 'PENDING') "S4 audit failure did not roll back pallet and task state"
        Assert-True ([int]$rolledBackState[2] -eq 0 -and [int]$rolledBackState[3] -eq 0 -and [int]$rolledBackState[4] -eq 0 -and [int]$rolledBackState[5] -eq 0) "S4 audit failure left partial inventory, flow, event or capacity writes"
        $failedControl = (Invoke-LocalMySql "SELECT CONCAT(r.status,'|',r.attempt_count,'|',c.status,'|',p.status) FROM agent_finish_inbound_execution_request r JOIN agent_finish_inbound_execution_confirmation c ON c.confirmation_ref=r.confirmation_ref JOIN agent_finish_inbound_execution_preview p ON p.id=c.preview_id WHERE r.agent_session_id='$agentSessionId' LIMIT 1;")[0].Split('|')
        Assert-True ($failedControl[0] -eq 'FAILED_RETRYABLE' -and [int]$failedControl[1] -eq 1) "S4 audit failure did not persist a single retryable attempt"
        Assert-True ($failedControl[2] -eq 'CONFIRMED' -and $failedControl[3] -eq 'CONFIRMED') "S4 audit failure incorrectly consumed the confirmation or preview"
        $s4AuditRollbackPassed = $true

        Invoke-LocalMySql "DROP TRIGGER IF EXISTS $s4AuditTriggerName;" | Out-Null
        $s4AuditTriggerName = $null
        $recovered = Invoke-AgentPost $executePath @{} $token
        Assert-True ($recovered.code -eq 200) "S4 retry after audit recovery failed"
        Assert-True ($recovered.data.statusLabel -eq '成品入库已完成') "S4 retry returned an unexpected user status"
        $recoveredControl = (Invoke-LocalMySql "SELECT CONCAT(r.status,'|',r.attempt_count,'|',c.status,'|',p.status) FROM agent_finish_inbound_execution_request r JOIN agent_finish_inbound_execution_confirmation c ON c.confirmation_ref=r.confirmation_ref JOIN agent_finish_inbound_execution_preview p ON p.id=c.preview_id WHERE r.agent_session_id='$agentSessionId' LIMIT 1;")[0].Split('|')
        Assert-True ($recoveredControl[0] -eq 'SUCCEEDED' -and [int]$recoveredControl[1] -eq 2) "S4 recovery did not reuse the same execution request exactly once"
        Assert-True ($recoveredControl[2] -eq 'CONSUMED' -and $recoveredControl[3] -eq 'CONSUMED') "S4 recovery did not consume the confirmation and preview"
        $s4AuditEvents = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_finish_inbound_execution_audit WHERE agent_session_id='$agentSessionId' AND event_type IN ('USER_CONFIRMED','EXECUTION_ACCEPTED','EXECUTION_FAILED','EXECUTION_SUCCEEDED');")[0]
        Assert-True ($s4AuditEvents -eq 5) "S4 audit recovery chain is incomplete"
        $s4AuditRecoveryPassed = $true
    }

    if ($VerifyS4StateInvalidation) {
        $pending = Invoke-AgentPost "/api/agent/sessions/$agentSessionId/finish-inbound-execution/s3/pending-preview" @{
            palletCodes = @($palletCode)
        } $token
        Assert-True ($pending.code -eq 200) "S4 state invalidation pending preview failed"
        $executePath = "/api/agent/sessions/$agentSessionId/finish-inbound-execution/s3/previews/$($archive[0])/confirm-and-execute"
        $s4StateDelayTriggerName = "s4_fi_request_$($suffix.Substring(0, 12))"
        Invoke-LocalMySql "DROP TRIGGER IF EXISTS $s4StateDelayTriggerName; CREATE TRIGGER $s4StateDelayTriggerName BEFORE INSERT ON agent_finish_inbound_execution_request FOR EACH ROW SET NEW.attempt_count = NEW.attempt_count + SLEEP(5);" | Out-Null
        $s4StateJob = Start-Job -ScriptBlock {
            param($Uri, $BearerToken)
            Invoke-RestMethod -Method Post -Uri $Uri -Headers @{ Authorization = "Bearer $BearerToken" } -ContentType 'application/json; charset=utf-8' -Body '{}'
        } -ArgumentList "$BaseUrl$executePath", $token

        $confirmationObserved = $false
        for ($poll = 0; $poll -lt 40; $poll++) {
            $confirmationCount = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_finish_inbound_execution_confirmation WHERE agent_session_id='$agentSessionId' AND status='CONFIRMED';")[0]
            if ($confirmationCount -eq 1) {
                $confirmationObserved = $true
                break
            }
            Start-Sleep -Milliseconds 100
        }
        Assert-True $confirmationObserved "S4 did not observe a committed confirmation before execution"
        Invoke-LocalMySql "UPDATE pallet_task SET status='CANCELED' WHERE id=$taskId;" | Out-Null
        $completedJob = Wait-Job -Job $s4StateJob -Timeout 30
        Assert-True ($null -ne $completedJob) "S4 delayed execution did not finish in time"
        $rejected = Receive-Job -Job $s4StateJob
        Remove-Job -Job $s4StateJob -Force
        $s4StateJob = $null
        Invoke-LocalMySql "DROP TRIGGER IF EXISTS $s4StateDelayTriggerName;" | Out-Null
        $s4StateDelayTriggerName = $null
        Assert-True ($rejected.code -eq 409) "S4 changed task state did not invalidate execution"
        Assert-True ([string]$rejected.msg -eq '任务、托盘、库位或生产关联已经变化，请重新预览') "S4 state invalidation returned an unsafe or unstable user message"

        $invalidatedState = (Invoke-LocalMySql "SELECT CONCAT(pc.status,'|',pt.status,'|',(SELECT COUNT(*) FROM inventory i WHERE i.pallet_code_id=pc.id),'|',(SELECT COUNT(*) FROM pallet_flow_record pfr WHERE pfr.pallet_code_id=pc.id),'|',(SELECT COUNT(*) FROM stock_movement_event sme WHERE sme.pallet_code_id=pc.id),'|',(SELECT cur_capacity FROM warehouse WHERE id=$warehouseId)) FROM pallet_code pc JOIN pallet_task pt ON pt.id=$taskId WHERE pc.id=$palletId;")[0].Split('|')
        Assert-True ($invalidatedState[0] -eq 'PENDING' -and $invalidatedState[1] -eq 'CANCELED') "S4 state invalidation changed unexpected pallet or task state"
        Assert-True ([int]$invalidatedState[2] -eq 0 -and [int]$invalidatedState[3] -eq 0 -and [int]$invalidatedState[4] -eq 0 -and [int]$invalidatedState[5] -eq 0) "S4 state invalidation left partial business writes"
        $invalidatedControl = (Invoke-LocalMySql "SELECT CONCAT(r.status,'|',r.attempt_count,'|',c.status,'|',p.status) FROM agent_finish_inbound_execution_request r JOIN agent_finish_inbound_execution_confirmation c ON c.confirmation_ref=r.confirmation_ref JOIN agent_finish_inbound_execution_preview p ON p.id=c.preview_id WHERE r.agent_session_id='$agentSessionId' LIMIT 1;")[0].Split('|')
        Assert-True ($invalidatedControl[0] -eq 'FAILED_FINAL' -and [int]$invalidatedControl[1] -eq 1) "S4 state invalidation did not persist a final failure"
        Assert-True ($invalidatedControl[2] -eq 'INVALIDATED' -and $invalidatedControl[3] -eq 'INVALIDATED') "S4 state invalidation did not invalidate confirmation and preview"
        $s4RejectedAuditCount = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_finish_inbound_execution_audit WHERE agent_session_id='$agentSessionId' AND event_type IN ('USER_CONFIRMED','EXECUTION_ACCEPTED','EXECUTION_REJECTED');")[0]
        Assert-True ($s4RejectedAuditCount -eq 3) "S4 state invalidation audit chain is incomplete"
        $s4StateInvalidationPassed = $true
    }

    $normalizedInput = (Invoke-LocalMySql "SELECT normalized_input_json FROM agent_finish_inbound_execution_preview WHERE agent_session_id='$agentSessionId' LIMIT 1;")[0]
    $entityState = (Invoke-LocalMySql "SELECT entity_state_json FROM agent_finish_inbound_execution_preview WHERE agent_session_id='$agentSessionId' LIMIT 1;")[0]
    Assert-True ($normalizedInput.Contains($palletCode) -and $normalizedInput.Contains($warehouseName)) "Normalized exact input was not persisted"
    Assert-True (-not $normalizedInput.Contains('rowNumber') -and -not $normalizedInput.Contains('layer')) "Normalized input contains forbidden location controls"
    Assert-True ($entityState.Contains('leftUsedRowsLayer1') -and $entityState.Contains('maxRows')) "Warehouse occupancy was not captured"

    $state = (Invoke-LocalMySql "SELECT CONCAT(pc.status,'|',pt.status,'|',(SELECT COUNT(*) FROM inventory i WHERE i.pallet_code_id=pc.id),'|',(SELECT COUNT(*) FROM pallet_flow_record pfr WHERE pfr.pallet_code_id=pc.id),'|',(SELECT COUNT(*) FROM stock_movement_event sme WHERE sme.pallet_code_id=pc.id),'|',(SELECT cur_capacity FROM warehouse WHERE id=$warehouseId)) FROM pallet_code pc JOIN pallet_task pt ON pt.id=$taskId WHERE pc.id=$palletId;")[0].Split('|')
    if ($VerifyS3DomainExecution -or $VerifyS4AuditRollback) {
        Assert-True ($state[0] -eq 'INSTOCK') "S3 did not update the pallet to the user-facing in-stock state"
        Assert-True ($state[1] -eq 'CONFIRMED') "S3 did not confirm the inbound task"
        Assert-True ([int]$state[2] -eq 1) "S3 did not create exactly one inventory row"
        Assert-True ([int]$state[3] -eq 1) "S3 did not create exactly one inbound flow record"
        Assert-True ([int]$state[4] -ge 1) "S3 did not persist a stock movement event"
        Assert-True ([int]$state[5] -eq 1) "S3 did not update warehouse capacity"
    } elseif ($VerifyS4StateInvalidation) {
        Assert-True ($state[0] -eq 'PENDING') "S4 invalidation unexpectedly changed pallet status"
        Assert-True ($state[1] -eq 'CANCELED') "S4 invalidation test mutation was not preserved"
        Assert-True ([int]$state[2] -eq 0 -and [int]$state[3] -eq 0 -and [int]$state[4] -eq 0 -and [int]$state[5] -eq 0) "S4 invalidation left business writes"
    } else {
        Assert-True ($state[0] -eq 'PENDING') "Preview changed pallet status"
        Assert-True ($state[1] -eq 'PENDING') "Preview changed task status"
        Assert-True ([int]$state[2] -eq 0) "Preview created inventory"
        Assert-True ([int]$state[3] -eq 0) "Preview created a pallet flow record"
        Assert-True ([int]$state[4] -eq 0) "Preview created a stock movement"
        Assert-True ([int]$state[5] -eq 0) "Preview changed warehouse capacity"
    }

    $previewAuditCount = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_tool_audit_log WHERE agent_session_id='$agentSessionId' AND tool_name='preview_finish_inbound_execution' AND result_code='SUCCESS';")[0]
    $executeAuditCount = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM agent_tool_audit_log WHERE agent_session_id='$agentSessionId' AND tool_name LIKE 'execute_%';")[0]
    Assert-True ($previewAuditCount -eq 1) "Exact preview tool audit is missing"
    Assert-True ($executeAuditCount -eq 0) "An execute tool was unexpectedly audited"

    [pscustomobject]@{
        Result = 'PASS'
        DatabaseHost = $dbHost
        FixturePrefix = $prefix
        Goal = 'FINISH_INBOUND_EXECUTION_PREVIEW'
        Tool = 'preview_finish_inbound_execution'
        CardType = 'finish_inbound_execution_preview'
        PreviewArchiveCount = $archiveCount
        BusinessWrites = if ($VerifyS3DomainExecution -or $VerifyS4AuditRollback) { 1 } else { 0 }
        ExecuteToolCalls = $executeAuditCount
        S2ControlPlane = if ($VerifyS2ControlPlane) { 'PASS' } else { 'NOT_RUN' }
        S2ExecutionRef = $s2ExecutionRef
        S2AuditEvents = $s2AuditCount
        S3DomainExecution = if ($VerifyS3DomainExecution) { 'PASS' } else { 'NOT_RUN' }
        ExecutionRoleCode = $ExecutionRoleCode
        S3ExecutionRef = $s3ExecutionRef
        S3AuditEvents = $s3AuditCount
        S3CommittedReplay = $s3Replayed
        S4PermissionDeniedBeforeGrant = $s4PermissionDeniedBeforeGrant
        S4AuditRollback = $s4AuditRollbackPassed
        S4RetryAfterAuditRecovery = $s4AuditRecoveryPassed
        S4StateInvalidation = $s4StateInvalidationPassed
    } | ConvertTo-Json -Depth 6
}
finally {
    if ($s4StateJob) {
        Stop-Job -Job $s4StateJob -ErrorAction SilentlyContinue
        Remove-Job -Job $s4StateJob -Force -ErrorAction SilentlyContinue
    }
    if ($s4StateDelayTriggerName) {
        Invoke-LocalMySql "DROP TRIGGER IF EXISTS $s4StateDelayTriggerName;" | Out-Null
    }
    if ($s4AuditTriggerName) {
        Invoke-LocalMySql "DROP TRIGGER IF EXISTS $s4AuditTriggerName;" | Out-Null
    }
    if ($agentSessionId) {
        $escapedSessionId = Escape-SqlLiteral $agentSessionId
        if ($VerifyS2ControlPlane -or $requiresS3Permission) {
            Invoke-LocalMySql "DELETE FROM agent_finish_inbound_execution_audit WHERE agent_session_id='$escapedSessionId'; DELETE FROM agent_finish_inbound_execution_request WHERE agent_session_id='$escapedSessionId'; DELETE FROM agent_finish_inbound_execution_confirmation WHERE agent_session_id='$escapedSessionId';" | Out-Null
            $s2Leftovers = [int](Invoke-LocalMySql "SELECT (SELECT COUNT(*) FROM agent_finish_inbound_execution_audit WHERE agent_session_id='$escapedSessionId') + (SELECT COUNT(*) FROM agent_finish_inbound_execution_request WHERE agent_session_id='$escapedSessionId') + (SELECT COUNT(*) FROM agent_finish_inbound_execution_confirmation WHERE agent_session_id='$escapedSessionId');")[0]
            Assert-True ($s2Leftovers -eq 0) "S2 fixture cleanup left $s2Leftovers control-plane rows"
        }
        Invoke-LocalMySql "DELETE FROM agent_finish_inbound_execution_preview WHERE agent_session_id='$escapedSessionId'; DELETE FROM agent_task_transition_preview WHERE agent_session_id='$escapedSessionId'; DELETE FROM agent_message_review_evidence WHERE review_id IN (SELECT id FROM agent_message_review WHERE agent_session_id='$escapedSessionId'); DELETE FROM agent_message_review WHERE agent_session_id='$escapedSessionId'; DELETE FROM agent_interrupt_state WHERE agent_session_id='$escapedSessionId'; DELETE FROM agent_api_audit_log WHERE agent_session_id='$escapedSessionId'; DELETE FROM agent_tool_audit_log WHERE agent_session_id='$escapedSessionId'; DELETE FROM agent_session WHERE id='$escapedSessionId';" | Out-Null
    }
    if ($palletId) {
        Invoke-LocalMySql "DELETE FROM stock_movement_event WHERE pallet_code_id=$palletId; DELETE FROM pallet_flow_record WHERE pallet_code_id=$palletId; DELETE FROM inventory WHERE pallet_code_id=$palletId;" | Out-Null
    }
    if ($taskId) {
        Invoke-LocalMySql "DELETE FROM pallet_task_semi_item WHERE pallet_task_id=$taskId; DELETE FROM pallet_task WHERE id=$taskId;" | Out-Null
    }
    if ($palletId) {
        Invoke-LocalMySql "DELETE FROM pallet_code WHERE id=$palletId;" | Out-Null
    }
    if ($warehouseId) {
        Invoke-LocalMySql "DELETE FROM inventory WHERE warehouse_id=$warehouseId; DELETE FROM in_stock WHERE warehouse_id=$warehouseId; DELETE FROM warehouse WHERE id=$warehouseId;" | Out-Null
    }
    if ($operatorId) {
        Invoke-LocalMySql "DELETE FROM user WHERE id=$operatorId;" | Out-Null
    }
    if ($employeeRosterId) {
        Invoke-LocalMySql "DELETE FROM employee_roster WHERE id=$employeeRosterId;" | Out-Null
    }
    if ($s3RolePermissionInserted) {
        Invoke-LocalMySql "DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';" | Out-Null
    }

    $leftovers = [int](Invoke-LocalMySql "SELECT (SELECT COUNT(*) FROM employee_roster WHERE employee_id='$(Escape-SqlLiteral $employeeId)') + (SELECT COUNT(*) FROM user WHERE employee_id='$(Escape-SqlLiteral $employeeId)') + (SELECT COUNT(*) FROM warehouse WHERE warehouse_name='$(Escape-SqlLiteral $warehouseName)');")[0]
    Assert-True ($leftovers -eq 0) "S1 fixture cleanup left $leftovers rows"
}
