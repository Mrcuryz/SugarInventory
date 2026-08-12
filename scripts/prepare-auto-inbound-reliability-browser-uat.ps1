param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [string]$CleanupManifest,

    [switch]$VerifyCompletedManifest
)

$ErrorActionPreference = 'Stop'
$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Join-Path (Get-Location).Path 'scripts'
} else {
    $PSScriptRoot
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

function New-ValidPalletCode {
    param([long]$Id)
    $alphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    $mixed = (($Id * 13) + 5) % 60466176
    $body = ''
    for ($index = 0; $index -lt 5; $index++) {
        $body = $alphabet[[int]($mixed % 36)] + $body
        $mixed = [Math]::Floor($mixed / 36)
    }
    $withoutCheck = "BT$body"
    $sum = 0
    foreach ($character in $withoutCheck.ToCharArray()) {
        $sum += $alphabet.IndexOf([string]$character)
    }
    return "$withoutCheck$($alphabet[$sum % 36])"
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
        Write-Output $output
    }
    finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Invoke-RedisCommand {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.Connect('127.0.0.1', 6379)
        $stream = $client.GetStream()
        $builder = [System.Text.StringBuilder]::new()
        [void]$builder.Append("*$($Arguments.Count)`r`n")
        foreach ($argument in $Arguments) {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes([string]$argument)
            [void]$builder.Append("`$$($bytes.Length)`r`n")
            [void]$builder.Append([string]$argument)
            [void]$builder.Append("`r`n")
        }
        $request = [System.Text.Encoding]::UTF8.GetBytes($builder.ToString())
        $stream.Write($request, 0, $request.Length)
        $stream.Flush()
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $false, 1024, $true)
        $line = $reader.ReadLine()
        if ($null -eq $line) { throw 'Redis returned no response' }
        if ($line.StartsWith('-')) { throw "Redis error: $($line.Substring(1))" }
        if ($line.StartsWith('+') -or $line.StartsWith(':')) { return $line.Substring(1) }
        if ($line.StartsWith('$')) {
            $length = [int]$line.Substring(1)
            if ($length -lt 0) { return $null }
            $buffer = New-Object char[] $length
            $read = $reader.ReadBlock($buffer, 0, $length)
            [void]$reader.ReadLine()
            if ($read -ne $length) { throw 'Redis bulk response was truncated' }
            return -join $buffer
        }
        throw "Unsupported Redis response: $line"
    }
    finally {
        if ($client.Connected) { $client.Close() }
        $client.Dispose()
    }
}

function Remove-Fixture {
    param([object]$Fixture)
    $batchId = Escape-SqlLiteral ([string]$Fixture.batchId)
    $palletId = [int]$Fixture.palletId
    $productId = [int]$Fixture.productId
    $warehouseId = [int]$Fixture.warehouseId
    $operatorId = [int]$Fixture.operatorId
    $employeeId = Escape-SqlLiteral ([string]$Fixture.employeeId)

    Invoke-RedisCommand @('DEL', "auto_inbound:batch:$batchId", "auto_inbound:owner:$batchId", "auto_inbound:confirm-lock:$batchId") | Out-Null
    Invoke-RedisCommand @('ZREM', "auto_inbound:history:$operatorId", $batchId) | Out-Null
    Invoke-LocalMySql "DELETE FROM auto_inbound_execution WHERE batch_id='$batchId'; DELETE FROM stock_movement_event WHERE product_id=$productId; DELETE FROM pallet_flow_record WHERE pallet_code_id=$palletId; DELETE FROM inventory WHERE pallet_code_id=$palletId OR product_id=$productId; DELETE FROM semi_product_record WHERE product_id=$productId; DELETE FROM in_stock WHERE product_id=$productId; DELETE FROM pallet_task WHERE pallet_code_id=$palletId; DELETE FROM assay WHERE product_id=$productId; DELETE FROM pallet_code WHERE id=$palletId; DELETE FROM product WHERE id=$productId; DELETE FROM warehouse WHERE id=$warehouseId; DELETE FROM user WHERE id=$operatorId; DELETE FROM employee_roster WHERE employee_id='$employeeId';" | Out-Null
}

if ($CleanupManifest) {
    $manifestPath = (Resolve-Path -LiteralPath $CleanupManifest).Path
    $fixture = Get-Content -LiteralPath $manifestPath -Encoding UTF8 -Raw | ConvertFrom-Json
    if ($VerifyCompletedManifest) {
        $batchId = Escape-SqlLiteral ([string]$fixture.batchId)
        $facts = (@(Invoke-LocalMySql "SELECT CONCAT((SELECT COUNT(*) FROM auto_inbound_execution WHERE batch_id='$batchId' AND status='COMMITTED'),'|',(SELECT COUNT(*) FROM pallet_task WHERE pallet_code_id=$($fixture.palletId) AND status='CONFIRMED' AND operation_batch_no=CONCAT('AUTO_INBOUND:','$batchId')),'|',(SELECT COUNT(*) FROM inventory WHERE pallet_code_id=$($fixture.palletId) AND product_id=$($fixture.productId)),'|',(SELECT COUNT(*) FROM pallet_flow_record WHERE pallet_code_id=$($fixture.palletId)),'|',(SELECT status FROM pallet_code WHERE id=$($fixture.palletId)),'|',(SELECT cur_capacity FROM warehouse WHERE id=$($fixture.warehouseId)));" )[0]).Split('|')
        Assert-True ($facts[0] -eq '1') 'Expected exactly one committed idempotency fact'
        Assert-True ($facts[1] -eq '1') 'Expected exactly one confirmed pallet task with the batch number'
        Assert-True ($facts[2] -eq '1') 'Expected exactly one pallet inventory row'
        Assert-True ([int]$facts[3] -ge 2) 'Expected bind and inbound flow records'
        Assert-True ($facts[4] -eq 'INSTOCK') 'Pallet code was not finalized as INSTOCK'
        Assert-True ($facts[5] -eq '1') 'Warehouse capacity should increase exactly once'
    }
    Remove-Fixture $fixture
    Remove-Item -LiteralPath $manifestPath
    [pscustomobject]@{ Result = 'PASS'; Cleanup = 'COMPLETE'; DatabaseHost = $dbHost } | ConvertTo-Json
    exit 0
}

Assert-True ((Invoke-RedisCommand @('PING')) -eq 'PONG') 'Local Redis is unavailable'
$migrationPath = Join-Path $scriptRoot '..\migrations\2026-08-12-auto-inbound-execution-idempotency.sql'
$migration = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $migrationPath).Path)
Invoke-LocalMySql $migration | Out-Null

$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12).ToUpperInvariant()
$testName = "报数可靠性验收$($suffix.Substring(0, 4))"
$employeeId = "AR$suffix"
$mobile = "16$([Math]::Abs($employeeId.GetHashCode()).ToString().PadLeft(9, '0').Substring(0, 9))"
$productName = "UAT报数半成品$($suffix.Substring(4, 4))"
$warehouseName = "UAT报数库位$($suffix.Substring(8, 4))"
$palletCode = "TEMP$suffix"
$batchId = [Guid]::NewGuid().ToString()
$taskId = [Guid]::NewGuid().ToString()

$fixture = $null
try {
    Invoke-LocalMySql "INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$(Escape-SqlLiteral $employeeId)','$(Escape-SqlLiteral $testName)','$mobile','本地验收','报数可靠性验收','在职','ADMIN'); INSERT INTO user(name,role_code,employee_id,bind_status,login_type) VALUES('$(Escape-SqlLiteral $testName)','ADMIN','$(Escape-SqlLiteral $employeeId)','UNBOUND','WEB');" | Out-Null
    $operatorId = [int](@(Invoke-LocalMySql "SELECT id FROM user WHERE employee_id='$(Escape-SqlLiteral $employeeId)' LIMIT 1;")[0])
    $screenMeshId = [int](@(Invoke-LocalMySql "SELECT id FROM screen_mesh ORDER BY id LIMIT 1;")[0])
    Invoke-LocalMySql "INSERT INTO product(product_name,product_type,status,packaging_method,weight_per_piece,created_by,pieces_per_pallet,can_stack,screen_mesh_id) VALUES('$(Escape-SqlLiteral $productName)','黄冰糖','半成品','袋',1.00,$operatorId,25,0,$screenMeshId); INSERT INTO warehouse(warehouse_name,status,max_capacity,cur_capacity,max_rows) VALUES('$(Escape-SqlLiteral $warehouseName)','正常',4,0,2);" | Out-Null
    $productId = [int](@(Invoke-LocalMySql "SELECT id FROM product WHERE product_name='$(Escape-SqlLiteral $productName)' LIMIT 1;")[0])
    $warehouseId = [int](@(Invoke-LocalMySql "SELECT id FROM warehouse WHERE warehouse_name='$(Escape-SqlLiteral $warehouseName)' LIMIT 1;")[0])
    Invoke-LocalMySql "INSERT INTO pallet_code(code,status,fixed_product_id,fixed_mode_enabled,created_by,current_cycle_no) VALUES('$(Escape-SqlLiteral $palletCode)','FREE',$productId,1,$operatorId,0);" | Out-Null
    $palletId = [int](@(Invoke-LocalMySql "SELECT id FROM pallet_code WHERE code='$(Escape-SqlLiteral $palletCode)' LIMIT 1;")[0])
    $palletCode = New-ValidPalletCode $palletId
    Invoke-LocalMySql "UPDATE pallet_code SET code='$(Escape-SqlLiteral $palletCode)' WHERE id=$palletId;" | Out-Null

    $task = [ordered]@{
        taskId = $taskId
        batchId = $batchId
        type = 'SEMI_PRODUCT'
        riskLevel = 'GREEN'
        riskReason = ''
        entryDate = (Get-Date).ToString('yyyy-MM-dd')
        side = '左'
        semiProductId = $productId
        semiProductName = $productName
        semiWarehouseId = $warehouseId
        semiWarehouseName = $warehouseName
        semiBoardQuantity = 1
        semiPieceQuantity = 0
        requiredQrCount = 1
        availableQrCount = 1
        missingFields = @()
        warnings = @()
        taskItems = @([ordered]@{ seq = 1; quantity = 1; unit = '0'; displayQuantity = '1板' })
        status = 'DRAFT'
        canAutoStockIn = $true
    }
    $json = ConvertTo-Json -InputObject @($task) -Depth 8 -Compress
    $timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    Invoke-RedisCommand @('SET', "auto_inbound:batch:$batchId", $json, 'EX', '86400') | Out-Null
    Invoke-RedisCommand @('SET', "auto_inbound:owner:$batchId", [string]$operatorId, 'EX', '86400') | Out-Null
    Invoke-RedisCommand @('ZADD', "auto_inbound:history:$operatorId", [string]$timestamp, $batchId) | Out-Null
    Invoke-RedisCommand @('EXPIRE', "auto_inbound:history:$operatorId", '86400') | Out-Null

    $fixture = [ordered]@{
        employeeId = $employeeId
        testName = $testName
        loginPassword = 'lbsp'
        operatorId = $operatorId
        productId = $productId
        productName = $productName
        warehouseId = $warehouseId
        warehouseName = $warehouseName
        palletId = $palletId
        palletCode = $palletCode
        batchId = $batchId
        taskId = $taskId
    }
    $manifestDir = Join-Path (Resolve-Path (Join-Path $scriptRoot '..')).Path 'output\auto-inbound-reliability-uat'
    [System.IO.Directory]::CreateDirectory($manifestDir) | Out-Null
    $manifestPath = Join-Path $manifestDir "auto-inbound-$($suffix.ToLowerInvariant()).json"
    [System.IO.File]::WriteAllText($manifestPath, ($fixture | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))

    [pscustomobject]@{
        Result = 'PASS'
        TestName = $testName
        LoginPassword = 'lbsp'
        ProductName = $productName
        WarehouseName = $warehouseName
        PalletCode = $palletCode
        BatchId = $batchId
        ManifestPath = $manifestPath
        DatabaseHost = $dbHost
    } | ConvertTo-Json
}
catch {
    if ($null -ne $fixture) { Remove-Fixture ([pscustomobject]$fixture) }
    throw
}
