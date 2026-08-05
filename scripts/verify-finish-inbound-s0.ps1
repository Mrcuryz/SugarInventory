param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [string]$BaseUrl = "http://127.0.0.1:38082",

    [Parameter(Mandatory = $true)]
    [string]$LoginName,

    [Parameter(Mandatory = $true)]
    [string]$LoginPassword,

    [ValidateRange(1, 10)]
    [int]$ConcurrencyRounds = 3
)

$ErrorActionPreference = "Stop"

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
    $modulus = 60466176L
    $mixed = (($Id * 13L) + 5L) % $modulus
    $body = (Convert-ToBase36 $mixed).PadLeft(5, '0')
    $withoutCheck = "BT$body"
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
        # Keep a one-row result as a one-element array. Without -NoEnumerate,
        # callers that index [0] can accidentally index the first character.
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
        -Body ($Body | ConvertTo-Json -Depth 8 -Compress)
}

$prefix = "S0UAT_$([DateTime]::Now.ToString('yyyyMMddHHmmss'))_$([Guid]::NewGuid().ToString('N').Substring(0, 6))"
$warehouseName = $prefix
$warehouseId = $null
$palletIds = New-Object System.Collections.Generic.List[int]
$taskIds = New-Object System.Collections.Generic.List[int]
$palletCodes = New-Object System.Collections.Generic.List[string]
$evidence = New-Object System.Collections.Generic.List[object]

function New-FinishInboundFixture {
    param([string]$Suffix)
    $palletId = [int](Invoke-LocalMySql "INSERT INTO pallet_code(code,status,product_id,fixed_mode_enabled,product_status,production_date,screen_mesh_id,created_by,current_cycle_no) VALUES(NULL,'PENDING',$script:productId,0,'成品',CURDATE(),$script:screenMeshId,$script:operatorId,1); SELECT LAST_INSERT_ID();")[0]
    $palletIds.Add($palletId)
    $code = New-PalletCode $palletId
    $escapedCode = Escape-SqlLiteral $code
    Invoke-LocalMySql "UPDATE pallet_code SET code='$escapedCode' WHERE id=$palletId;" | Out-Null
    $remark = Escape-SqlLiteral "$prefix-$Suffix"
    $taskId = [int](Invoke-LocalMySql "INSERT INTO pallet_task(pallet_code_id,task_type,status,product_id,product_status,production_date,screen_mesh_id,created_by,remark,cycle_no) VALUES($palletId,'FINISH_IN','PENDING',$script:productId,'成品',CURDATE(),$script:screenMeshId,$script:operatorId,'$remark',1); SELECT LAST_INSERT_ID();")[0]
    $taskIds.Add($taskId)
    $palletCodes.Add($code)
    return [pscustomobject]@{ PalletId = $palletId; TaskId = $taskId; Code = $code }
}

function Get-FixtureState {
    param([object]$Fixture)
    $row = (Invoke-LocalMySql "SELECT CONCAT(pc.status,'|',pt.status,'|',(SELECT COUNT(*) FROM inventory i WHERE i.pallet_code_id=pc.id),'|',(SELECT COUNT(*) FROM pallet_flow_record pfr WHERE pfr.pallet_code_id=pc.id AND pfr.operation_type='FINISH_INSTOCK'),'|',(SELECT COUNT(*) FROM stock_movement_event sme WHERE sme.pallet_code_id=pc.id)) FROM pallet_code pc JOIN pallet_task pt ON pt.id=$($Fixture.TaskId) WHERE pc.id=$($Fixture.PalletId);")[0]
    $parts = $row.Split('|')
    return [pscustomobject]@{
        PalletStatus = $parts[0]
        TaskStatus = $parts[1]
        InventoryCount = [int]$parts[2]
        FinishFlowCount = [int]$parts[3]
        MovementCount = [int]$parts[4]
    }
}

try {
    # Login is the readiness probe as actuator endpoints are intentionally authenticated.
    $login = Invoke-AgentPost '/api/auth/web-login' @{ name = $LoginName; password = $LoginPassword } ''
    Assert-True ($login.code -eq 200) "Web login failed"
    Assert-True ([bool]$login.data.token) "Web login returned no token"
    $token = $login.data.token

    $escapedLoginName = Escape-SqlLiteral $LoginName
    $operatorRows = Invoke-LocalMySql "SELECT id FROM user WHERE name='$escapedLoginName' LIMIT 1;"
    Assert-True ($operatorRows.Count -eq 1) "Operator user was not found"
    $script:operatorId = [int]$operatorRows[0]

    $productRows = Invoke-LocalMySql "SELECT CONCAT(id,'|',screen_mesh_id) FROM product WHERE product_name='黄冰糖（袋）' AND screen_mesh_id IS NOT NULL ORDER BY id LIMIT 1;"
    Assert-True ($productRows.Count -eq 1) "Suitable finished product fixture was not found"
    $productParts = $productRows[0].Split('|')
    $script:productId = [int]$productParts[0]
    $script:screenMeshId = [int]$productParts[1]

    $escapedWarehouse = Escape-SqlLiteral $warehouseName
    $warehouseId = [int](Invoke-LocalMySql "INSERT INTO warehouse(status,max_capacity,cur_capacity,max_rows,warehouse_name) VALUES('空置',20,0,20,'$escapedWarehouse'); SELECT LAST_INSERT_ID();")[0]

    # Case 1: the second item fails after the first item has entered the service loop.
    # The outer transaction must roll back every write made by the first item.
    $rollbackA = New-FinishInboundFixture 'rollback-a'
    $rollbackB = New-FinishInboundFixture 'rollback-b'
    $missingWarehouse = "$prefix`_MISSING"
    $rollbackResponse = Invoke-AgentPost '/api/pallet-codes/tasks/confirm' @{
        items = @(
            @{ code = $rollbackA.Code; warehouseName = $warehouseName; side = '左'; rowNumber = 1; layer = 1; quantity = 1; unit = '0'; remark = $prefix },
            @{ code = $rollbackB.Code; warehouseName = $missingWarehouse; side = '左'; rowNumber = 2; layer = 1; quantity = 1; unit = '0'; remark = $prefix }
        )
    } $token
    Assert-True ($rollbackResponse.code -ne 200) "Rollback case unexpectedly succeeded"
    $rollbackStateA = Get-FixtureState $rollbackA
    $rollbackStateB = Get-FixtureState $rollbackB
    foreach ($state in @($rollbackStateA, $rollbackStateB)) {
        Assert-True ($state.PalletStatus -eq 'PENDING') "Batch rollback left pallet outside PENDING"
        Assert-True ($state.TaskStatus -eq 'PENDING') "Batch rollback left task outside PENDING"
        Assert-True ($state.InventoryCount -eq 0) "Batch rollback left inventory"
        Assert-True ($state.FinishFlowCount -eq 0) "Batch rollback left finish-inbound flow"
        Assert-True ($state.MovementCount -eq 0) "Batch rollback left stock movement"
    }
    $rollbackInStockCount = [int](Invoke-LocalMySql "SELECT COUNT(*) FROM in_stock WHERE warehouse_id=$warehouseId;")[0]
    $rollbackCapacity = [int](Invoke-LocalMySql "SELECT cur_capacity FROM warehouse WHERE id=$warehouseId;")[0]
    Assert-True ($rollbackInStockCount -eq 0) "Batch rollback left in_stock row"
    Assert-True ($rollbackCapacity -eq 0) "Batch rollback changed warehouse capacity"
    $evidence.Add([pscustomobject]@{ Case = 'batch_rollback'; Result = 'PASS'; ResponseCode = $rollbackResponse.code })

    # Case 2: confirmation and cancellation compete for the same pallet row.
    # Exactly one legal final state is allowed after both requests complete.
    Add-Type -AssemblyName System.Net.Http
    for ($round = 1; $round -le $ConcurrencyRounds; $round++) {
        $fixture = New-FinishInboundFixture "race-$round"
        $confirmBody = @{
            items = @(
                @{ code = $fixture.Code; warehouseName = $warehouseName; side = '左'; rowNumber = 2 + $round; layer = 1; quantity = 1; unit = '0'; remark = "$prefix-race-$round" }
            )
        } | ConvertTo-Json -Depth 8 -Compress
        $cancelBody = @{ codes = @($fixture.Code); remark = "$prefix-race-$round" } | ConvertTo-Json -Depth 8 -Compress

        $client = [System.Net.Http.HttpClient]::new()
        try {
            $confirmRequest = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$BaseUrl/api/pallet-codes/tasks/confirm")
            $confirmRequest.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
            $confirmRequest.Content = [System.Net.Http.StringContent]::new($confirmBody, [System.Text.Encoding]::UTF8, 'application/json')
            $cancelRequest = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$BaseUrl/api/pallet-codes/tasks/cancel")
            $cancelRequest.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
            $cancelRequest.Content = [System.Net.Http.StringContent]::new($cancelBody, [System.Text.Encoding]::UTF8, 'application/json')

            $confirmTask = $client.SendAsync($confirmRequest)
            $cancelTask = $client.SendAsync($cancelRequest)
            [System.Threading.Tasks.Task]::WaitAll(@($confirmTask, $cancelTask))
            $confirmResult = ($confirmTask.Result.Content.ReadAsStringAsync().Result | ConvertFrom-Json)
            $cancelResult = ($cancelTask.Result.Content.ReadAsStringAsync().Result | ConvertFrom-Json)
        }
        finally {
            $client.Dispose()
        }

        $state = Get-FixtureState $fixture
        if ($state.TaskStatus -eq 'CONFIRMED') {
            Assert-True ($state.PalletStatus -eq 'INSTOCK') "Confirmed task did not leave pallet INSTOCK (was $($state.PalletStatus))"
            Assert-True ($state.InventoryCount -eq 1) "Confirmed task did not create exactly one inventory row"
            Assert-True ($state.FinishFlowCount -eq 1) "Confirmed task did not create exactly one finish-inbound flow"
            Assert-True ($state.MovementCount -eq 1) "Confirmed task did not create exactly one stock movement"
            Assert-True ($confirmResult.code -eq 200) "Confirmed final state has a failed confirm response"
            $winner = 'confirm'
        }
        elseif ($state.TaskStatus -eq 'CANCELED') {
            Assert-True ($state.PalletStatus -eq 'FREE') "Canceled task did not leave pallet FREE (was $($state.PalletStatus))"
            Assert-True ($state.InventoryCount -eq 0) "Canceled task created inventory"
            Assert-True ($state.FinishFlowCount -eq 0) "Canceled task created finish-inbound flow"
            Assert-True ($state.MovementCount -eq 0) "Canceled task created stock movement"
            Assert-True ($cancelResult.code -eq 200) "Canceled final state has a failed cancel response"
            $winner = 'cancel'
        }
        else {
            throw "Illegal race final state: pallet=$($state.PalletStatus), task=$($state.TaskStatus)"
        }
        $evidence.Add([pscustomobject]@{
            Case = "confirm_cancel_race_$round"
            Result = 'PASS'
            Winner = $winner
            ConfirmCode = $confirmResult.code
            CancelCode = $cancelResult.code
        })
    }

    [pscustomobject]@{
        Result = 'PASS'
        DatabaseHost = $dbHost
        FixturePrefix = $prefix
        ConcurrencyRounds = $ConcurrencyRounds
        Evidence = $evidence
    } | ConvertTo-Json -Depth 8
}
finally {
    if ($palletIds.Count -gt 0) {
        $palletIdList = ($palletIds | ForEach-Object { [string]$_ }) -join ','
        $inStockIds = @()
        if ($warehouseId) {
            $inStockIds = @(Invoke-LocalMySql "SELECT id FROM in_stock WHERE warehouse_id=$warehouseId;")
        }
        Invoke-LocalMySql "DELETE FROM stock_movement_event WHERE pallet_code_id IN ($palletIdList);" | Out-Null
        Invoke-LocalMySql "DELETE FROM pallet_flow_record WHERE pallet_code_id IN ($palletIdList);" | Out-Null
        Invoke-LocalMySql "DELETE FROM inventory WHERE pallet_code_id IN ($palletIdList);" | Out-Null
        if ($inStockIds.Count -gt 0) {
            $inStockIdList = ($inStockIds | ForEach-Object { [string]$_ }) -join ','
            Invoke-LocalMySql "DELETE FROM inventory WHERE in_stock_id IN ($inStockIdList); DELETE FROM in_stock WHERE id IN ($inStockIdList);" | Out-Null
        }
        if ($taskIds.Count -gt 0) {
            $taskIdList = ($taskIds | ForEach-Object { [string]$_ }) -join ','
            Invoke-LocalMySql "DELETE FROM pallet_task_semi_item WHERE pallet_task_id IN ($taskIdList); DELETE FROM pallet_task WHERE id IN ($taskIdList);" | Out-Null
        }
        Invoke-LocalMySql "DELETE FROM pallet_code WHERE id IN ($palletIdList);" | Out-Null
    }
    if ($warehouseId) {
        Invoke-LocalMySql "DELETE FROM warehouse WHERE id=$warehouseId;" | Out-Null
    }

    $leftoverCount = 0
    if ($palletIds.Count -gt 0) {
        $palletIdList = ($palletIds | ForEach-Object { [string]$_ }) -join ','
        $leftoverCount += [int](Invoke-LocalMySql "SELECT (SELECT COUNT(*) FROM pallet_code WHERE id IN ($palletIdList)) + (SELECT COUNT(*) FROM inventory WHERE pallet_code_id IN ($palletIdList)) + (SELECT COUNT(*) FROM pallet_flow_record WHERE pallet_code_id IN ($palletIdList)) + (SELECT COUNT(*) FROM stock_movement_event WHERE pallet_code_id IN ($palletIdList));")[0]
    }
    if ($taskIds.Count -gt 0) {
        $taskIdList = ($taskIds | ForEach-Object { [string]$_ }) -join ','
        $leftoverCount += [int](Invoke-LocalMySql "SELECT COUNT(*) FROM pallet_task WHERE id IN ($taskIdList);")[0]
    }
    if ($warehouseId) {
        $leftoverCount += [int](Invoke-LocalMySql "SELECT (SELECT COUNT(*) FROM warehouse WHERE id=$warehouseId) + (SELECT COUNT(*) FROM in_stock WHERE warehouse_id=$warehouseId) + (SELECT COUNT(*) FROM inventory WHERE warehouse_id=$warehouseId);")[0]
    }
    Assert-True ($leftoverCount -eq 0) "Fixture cleanup left $leftoverCount rows"
}
