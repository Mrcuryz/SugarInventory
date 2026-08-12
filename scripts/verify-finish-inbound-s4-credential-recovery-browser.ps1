param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [string]$BaseUrl = "http://127.0.0.1:38082",

    [string]$LoginPassword = "lbsp-isolated-uat"
)

$ErrorActionPreference = "Stop"

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
    $withoutCheck = "BT$((Convert-ToBase36 ((($Id * 13L) + 5L) % 60466176L)).PadLeft(5, '0'))"
    $sum = 0
    foreach ($char in $withoutCheck.ToCharArray()) { $sum += $chars.IndexOf([string]$char) }
    return $withoutCheck + $chars[$sum % 36]
}

$baseUri = [Uri]$BaseUrl
Assert-True ($baseUri.Host -in @('127.0.0.1', 'localhost', '::1')) "Refusing non-local application host"
$config = @{}
foreach ($line in [System.IO.File]::ReadAllLines($EnvFile)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $config[$Matches[1]] = $Matches[2] }
}
foreach ($required in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    Assert-True ($config.ContainsKey($required)) "Missing $required in env file"
}
Assert-True ($config['DB_URL'] -match '^jdbc:mysql://([^/:]+)(?::(\d+))?/([^?]+)') "Unsupported DB_URL"
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$dbName = $Matches[3]
Assert-True ($dbHost -in @('127.0.0.1', 'localhost', '::1')) "Refusing non-local database host"
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
Assert-True (Test-Path -LiteralPath $mysql) "mysql.exe not found"

function Invoke-LocalMySql {
    param([string]$Sql)
    $env:MYSQL_PWD = $config['DB_PASSWORD']
    try {
        $output = & $mysql "--host=$dbHost" "--port=$dbPort" "--user=$($config['DB_USERNAME'])" `
            "--database=$dbName" '--default-character-set=utf8mb4' '--batch' '--raw' `
            '--skip-column-names' "--execute=$Sql"
        if ($LASTEXITCODE -ne 0) { throw "mysql exited with code $LASTEXITCODE" }
        Write-Output -NoEnumerate @($output)
    }
    finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    try {
        $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $headers `
            -ContentType 'application/json; charset=utf-8' `
            -Body $(if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 12 -Compress }) `
            -TimeoutSec 30
        return [pscustomobject]@{ HttpStatus = 200; Body = $response }
    }
    catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $body = $_.ErrorDetails.Message
        $parsed = $body
        if ($body) { try { $parsed = $body | ConvertFrom-Json } catch {} }
        return [pscustomobject]@{ HttpStatus = $status; Body = $parsed }
    }
}

function Assert-RejectedBeforeWrite {
    param([object]$Response, [int[]]$AllowedCodes, [string]$Label, [int]$OwnerUserId)
    $businessCode = if ($Response.Body -is [string]) { $Response.HttpStatus } else { [int]$Response.Body.code }
    Assert-True ($AllowedCodes -contains $businessCode) "$Label returned unexpected code $businessCode"
    $writes = [int](Invoke-LocalMySql "SELECT (SELECT COUNT(*) FROM agent_finish_inbound_execution_request WHERE owner_user_id=$OwnerUserId) + (SELECT COUNT(*) FROM inventory i JOIN pallet_code pc ON pc.id=i.pallet_code_id WHERE pc.created_by=$OwnerUserId) + (SELECT COUNT(*) FROM stock_movement_event sme JOIN pallet_code pc ON pc.id=sme.pallet_code_id WHERE pc.created_by=$OwnerUserId);")[0]
    Assert-True ($writes -eq 0) "$Label reached request or business write boundary"
}

$suffix = [Guid]::NewGuid().ToString('N')
$nameA = "S4凭据A$($suffix.Substring(0,8))"
$nameB = "S4凭据B$($suffix.Substring(8,8))"
$employeeA = "S4A$($suffix.Substring(0,12))"
$employeeB = "S4B$($suffix.Substring(12,12))"
$mobileSeed = [long]([DateTime]::UtcNow.Ticks % 1000000000L)
$mobileA = "18$(([string]$mobileSeed).PadLeft(9, '0'))"
$mobileB = "18$(([string](($mobileSeed + 1) % 1000000000L)).PadLeft(9, '0'))"
$rosterA = 0; $rosterB = 0; $userA = 0; $userB = 0
$warehouseId = 0; $palletId = 0; $taskId = 0; $palletCode = ''; $warehouseName = ''
$sessionA = ''; $sessionB = ''
$previewRef = ''; $confirmationRef = ''; $executionToken = ''; $idempotencyKey = ''
$permissionInserted = $false

try {
    $baselinePermission = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';")[0]
    Assert-True ($baselinePermission -eq 0) "Dedicated execute permission must be default-unassigned"
    Invoke-LocalMySql "INSERT INTO role_permission(role_id,permission_id) SELECT r.id,p.id FROM role r JOIN permission p WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';" | Out-Null
    $permissionInserted = $true
    $rosterA = [int](Invoke-LocalMySql "INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$(Escape-SqlLiteral $employeeA)','$(Escape-SqlLiteral $nameA)','$mobileA','本地验收','S4 凭据 A','在职','ADMIN'); SELECT LAST_INSERT_ID();")[0]
    $rosterB = [int](Invoke-LocalMySql "INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$(Escape-SqlLiteral $employeeB)','$(Escape-SqlLiteral $nameB)','$mobileB','本地验收','S4 凭据 B','在职','ADMIN'); SELECT LAST_INSERT_ID();")[0]
    $loginA = Invoke-Api 'Post' '/api/auth/web-login' @{ name=$nameA; password=$LoginPassword } ''
    $loginB = Invoke-Api 'Post' '/api/auth/web-login' @{ name=$nameB; password=$LoginPassword } ''
    Assert-True ($loginA.Body.code -eq 200 -and $loginB.Body.code -eq 200) "Credential test login failed"
    $tokenA = [string]$loginA.Body.data.token; $tokenB = [string]$loginB.Body.data.token
    $userA = [int](Invoke-LocalMySql "SELECT id FROM user WHERE employee_id='$(Escape-SqlLiteral $employeeA)' LIMIT 1;")[0]
    $userB = [int](Invoke-LocalMySql "SELECT id FROM user WHERE employee_id='$(Escape-SqlLiteral $employeeB)' LIMIT 1;")[0]
    $sessionAResponse = Invoke-Api 'Post' '/api/agent/sessions' @{ clientType='S4_CREDENTIAL_BROWSER'; requestedScopes=@('mcp:warehouse:read'); mcpTransport='STDIO' } $tokenA
    $sessionBResponse = Invoke-Api 'Post' '/api/agent/sessions' @{ clientType='S4_CREDENTIAL_BROWSER'; requestedScopes=@('mcp:warehouse:read'); mcpTransport='STDIO' } $tokenB
    $sessionA = [string]$sessionAResponse.Body.data.agentSessionId
    $sessionB = [string]$sessionBResponse.Body.data.agentSessionId
    Assert-True ($sessionA -and $sessionB) "Credential test sessions were not created"

    $product = (Invoke-LocalMySql "SELECT CONCAT(id,'|',screen_mesh_id) FROM product WHERE product_name='黄冰糖（袋）' AND screen_mesh_id IS NOT NULL AND pieces_per_pallet IS NOT NULL ORDER BY id LIMIT 1;")[0].Split('|')
    $warehouseName = "S4CRED_$($suffix.Substring(0,10))"
    $warehouseId = [int](Invoke-LocalMySql "INSERT INTO warehouse(status,max_capacity,cur_capacity,max_rows,warehouse_name) VALUES('空置',4,0,1,'$warehouseName'); SELECT LAST_INSERT_ID();")[0]
    $palletId = [int](Invoke-LocalMySql "INSERT INTO pallet_code(code,status,product_id,fixed_mode_enabled,product_status,production_date,screen_mesh_id,created_by,current_cycle_no) VALUES(NULL,'PENDING',$($product[0]),0,'成品',CURDATE(),$($product[1]),$userA,1); SELECT LAST_INSERT_ID();")[0]
    $palletCode = New-PalletCode $palletId
    Invoke-LocalMySql "UPDATE pallet_code SET code='$palletCode' WHERE id=$palletId;" | Out-Null
    $taskId = [int](Invoke-LocalMySql "INSERT INTO pallet_task(pallet_code_id,task_type,status,product_id,product_status,production_date,screen_mesh_id,created_by,remark,cycle_no) VALUES($palletId,'FINISH_IN','PENDING',$($product[0]),'成品',CURDATE(),$($product[1]),$userA,'S4 凭据恢复验收',1); SELECT LAST_INSERT_ID();")[0]
    $payload = @{ previewVersion=1; items=@(@{ code=$palletCode; warehouseName=$warehouseName; entryDate=[DateTime]::Now.ToString('yyyy-MM-dd'); side='左'; quantity=1; unit='0'; remark='S4 凭据恢复验收' }) }
    $ticks = ([string][char]96) * 3
    $message = "请生成成品入库精确执行预览（只预览，不执行）。以下是我已经填写的表单：`n`n${ticks}json`n$($payload | ConvertTo-Json -Depth 8)`n$ticks"
    $preview = Invoke-Api 'Post' "/api/agent/sessions/$sessionA/messages" @{ message=$message; pageContext=@{} } $tokenA
    Assert-True ($preview.Body.code -eq 200) "Credential recovery exact preview failed"
    $previewRef = [string](Invoke-LocalMySql "SELECT preview_ref FROM agent_finish_inbound_execution_preview WHERE owner_user_id=$userA AND agent_session_id='$(Escape-SqlLiteral $sessionA)' AND status='READY' ORDER BY id DESC LIMIT 1;")[0]
    Assert-True ($previewRef -match '^fip1_') "Credential recovery preview archive is missing"

    $confirm = Invoke-Api 'Post' "/api/agent/sessions/$sessionA/finish-inbound-execution/confirmations" @{ previewRef=$previewRef } $tokenA
    Assert-True ($confirm.Body.code -eq 200) "S2 credential confirmation failed"
    $confirmationRef = [string]$confirm.Body.data.confirmationRef
    $executionToken = [string]$confirm.Body.data.executionToken
    $idempotencyKey = [string]$confirm.Body.data.idempotencyKey
    Assert-True ($executionToken -and $idempotencyKey) "Server credentials were not issued"

    $crossUser = Invoke-Api 'Post' "/api/agent/sessions/$sessionB/finish-inbound-execution/s2-noop/confirmations/$confirmationRef/consume" @{ executionToken=$executionToken; idempotencyKey=$idempotencyKey } $tokenB
    Assert-RejectedBeforeWrite $crossUser @(403,404) 'Cross-user replay' $userA

    $revoke = Invoke-Api 'Post' "/api/agent/sessions/$sessionA/finish-inbound-execution/confirmations/$confirmationRef/revoke" @{} $tokenA
    Assert-True ($revoke.Body.code -eq 200) "Confirmation revoke failed"
    $revoked = Invoke-Api 'Post' "/api/agent/sessions/$sessionA/finish-inbound-execution/s2-noop/confirmations/$confirmationRef/consume" @{ executionToken=$executionToken; idempotencyKey=$idempotencyKey } $tokenA
    Assert-RejectedBeforeWrite $revoked @(409) 'Revoked confirmation' $userA

    Invoke-LocalMySql "UPDATE agent_finish_inbound_execution_confirmation SET status='CONFIRMED',revoked_at=NULL,expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE confirmation_ref='$(Escape-SqlLiteral $confirmationRef)'; UPDATE agent_finish_inbound_execution_preview SET status='CONFIRMED' WHERE preview_ref='$(Escape-SqlLiteral $previewRef)';" | Out-Null
    $expired = Invoke-Api 'Post' "/api/agent/sessions/$sessionA/finish-inbound-execution/s2-noop/confirmations/$confirmationRef/consume" @{ executionToken=$executionToken; idempotencyKey=$idempotencyKey } $tokenA
    Assert-RejectedBeforeWrite $expired @(410) 'Expired confirmation' $userA
    $expiredState = (Invoke-LocalMySql "SELECT CONCAT(c.status,'|',p.status) FROM agent_finish_inbound_execution_confirmation c JOIN agent_finish_inbound_execution_preview p ON p.id=c.preview_id WHERE c.confirmation_ref='$(Escape-SqlLiteral $confirmationRef)';")[0]
    Assert-True ($expiredState -eq 'EXPIRED|EXPIRED') "Expired confirmation and preview were not persisted"

    Invoke-LocalMySql "DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';" | Out-Null
    $permissionInserted = $false
    $permissionDenied = Invoke-Api 'Post' "/api/agent/sessions/$sessionA/finish-inbound-execution/s3/pending-preview" @{ palletCodes=@($palletCode) } $tokenA
    Assert-RejectedBeforeWrite $permissionDenied @(403) 'Permission rollback' $userA

    $revokeSession = Invoke-Api 'Delete' "/api/agent/sessions/$sessionA" @{ revokedReason='S4_CREDENTIAL_BROWSER_UAT' } $tokenA
    Assert-True ($revokeSession.Body.code -eq 200) "Session revoke failed"
    $afterSessionRevoke = Invoke-Api 'Post' "/api/agent/sessions/$sessionA/finish-inbound-execution/s2-noop/confirmations/$confirmationRef/consume" @{ executionToken=$executionToken; idempotencyKey=$idempotencyKey } $tokenA
    Assert-RejectedBeforeWrite $afterSessionRevoke @(401,403,404,409,410) 'Revoked session' $userA

    $auditTypes = @(Invoke-LocalMySql "SELECT event_type FROM agent_finish_inbound_execution_audit WHERE confirmation_ref='$(Escape-SqlLiteral $confirmationRef)' ORDER BY id;")
    $auditTypeText = ($auditTypes | ForEach-Object { [string]$_ }) -join ','
    Assert-True ($auditTypeText -match '(^|[\s,])USER_REVOKED($|[\s,])' -and $auditTypeText -match '(^|[\s,])CONFIRMATION_EXPIRED($|[\s,])') "Recovery audit events are incomplete: $auditTypeText"
    [pscustomobject]@{
        Result='PASS'; CrossUserRejected=$true; RevokedRejected=$true; ExpiredRejected=$true
        RevokedSessionRejected=$true; PermissionRollbackRejected=$true; ExecutionRequests=0; BusinessWrites=0
        CredentialsReturnedToOutput=$false; DatabaseHost=$dbHost
    } | ConvertTo-Json
}
finally {
    if ($confirmationRef) { Invoke-LocalMySql "DELETE FROM agent_finish_inbound_execution_audit WHERE confirmation_ref='$(Escape-SqlLiteral $confirmationRef)'; DELETE FROM agent_finish_inbound_execution_request WHERE confirmation_ref='$(Escape-SqlLiteral $confirmationRef)'; DELETE FROM agent_finish_inbound_execution_confirmation WHERE confirmation_ref='$(Escape-SqlLiteral $confirmationRef)';" | Out-Null }
    if ($previewRef) { Invoke-LocalMySql "DELETE FROM agent_finish_inbound_execution_preview WHERE preview_ref='$(Escape-SqlLiteral $previewRef)';" | Out-Null }
    foreach ($session in @($sessionA,$sessionB)) { if ($session) { Invoke-LocalMySql "DELETE FROM agent_message_review_evidence WHERE review_id IN (SELECT id FROM agent_message_review WHERE agent_session_id='$(Escape-SqlLiteral $session)'); DELETE FROM agent_message_review WHERE agent_session_id='$(Escape-SqlLiteral $session)'; DELETE FROM agent_interrupt_state WHERE agent_session_id='$(Escape-SqlLiteral $session)'; DELETE FROM agent_api_audit_log WHERE agent_session_id='$(Escape-SqlLiteral $session)'; DELETE FROM agent_tool_audit_log WHERE agent_session_id='$(Escape-SqlLiteral $session)'; DELETE FROM agent_session WHERE id='$(Escape-SqlLiteral $session)';" | Out-Null } }
    if ($palletId -gt 0) { Invoke-LocalMySql "DELETE FROM stock_movement_event WHERE pallet_code_id=$palletId; DELETE FROM pallet_flow_record WHERE pallet_code_id=$palletId; DELETE FROM inventory WHERE pallet_code_id=$palletId;" | Out-Null }
    if ($taskId -gt 0) { Invoke-LocalMySql "DELETE FROM pallet_task_semi_item WHERE pallet_task_id=$taskId; DELETE FROM pallet_task WHERE id=$taskId;" | Out-Null }
    if ($palletId -gt 0) { Invoke-LocalMySql "DELETE FROM pallet_code WHERE id=$palletId;" | Out-Null }
    if ($warehouseId -gt 0) { Invoke-LocalMySql "DELETE FROM inventory WHERE warehouse_id=$warehouseId; DELETE FROM in_stock WHERE warehouse_id=$warehouseId; DELETE FROM warehouse WHERE id=$warehouseId;" | Out-Null }
    if ($userA -gt 0) { Invoke-LocalMySql "DELETE FROM user WHERE id=$userA;" | Out-Null }
    if ($userB -gt 0) { Invoke-LocalMySql "DELETE FROM user WHERE id=$userB;" | Out-Null }
    if ($rosterA -gt 0) { Invoke-LocalMySql "DELETE FROM employee_roster WHERE id=$rosterA;" | Out-Null }
    if ($rosterB -gt 0) { Invoke-LocalMySql "DELETE FROM employee_roster WHERE id=$rosterB;" | Out-Null }
    if ($permissionInserted) { Invoke-LocalMySql "DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='ADMIN' AND p.perm_code='agent:finish-inbound:execute';" | Out-Null }
}
