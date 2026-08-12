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

function Remove-Fixture {
    param([object]$Fixture)
    $productId = [int]$Fixture.productId
    $operatorId = [int]$Fixture.operatorId
    $qrOnlyWarehouseId = [int]$Fixture.qrOnlyWarehouseId
    $mixedWarehouseId = [int]$Fixture.mixedWarehouseId
    $palletIds = @([int]$Fixture.qrOnlyPalletId, [int]$Fixture.mixedPalletId)
    $palletList = ($palletIds -join ',')

    Invoke-LocalMySql "DELETE FROM stock_movement_event WHERE product_id=$productId; DELETE FROM out_stock WHERE product_id=$productId; DELETE FROM pallet_flow_record WHERE pallet_code_id IN ($palletList); DELETE FROM pallet_task WHERE pallet_code_id IN ($palletList); DELETE FROM inventory WHERE product_id=$productId; DELETE FROM pallet_code WHERE id IN ($palletList); DELETE FROM assay WHERE product_id=$productId; DELETE FROM product WHERE id=$productId; UPDATE warehouse SET status='$(Escape-SqlLiteral ([string]$Fixture.qrOnlyOriginalStatus))', cur_capacity=$([int]$Fixture.qrOnlyOriginalCapacity) WHERE id=$qrOnlyWarehouseId; UPDATE warehouse SET status='$(Escape-SqlLiteral ([string]$Fixture.mixedOriginalStatus))', cur_capacity=$([int]$Fixture.mixedOriginalCapacity) WHERE id=$mixedWarehouseId; DELETE FROM user WHERE id=$operatorId; DELETE FROM employee_roster WHERE employee_id='$(Escape-SqlLiteral ([string]$Fixture.employeeId))';" | Out-Null
}

if ($CleanupManifest) {
    $manifestPath = (Resolve-Path -LiteralPath $CleanupManifest).Path
    $fixture = Get-Content -LiteralPath $manifestPath -Encoding UTF8 -Raw | ConvertFrom-Json
    if ($VerifyCompletedManifest) {
        $facts = (@(Invoke-LocalMySql "SELECT CONCAT((SELECT COUNT(*) FROM inventory WHERE warehouse_id=$($fixture.qrOnlyWarehouseId) AND product_id=$($fixture.productId) AND pallet_code_id=$($fixture.qrOnlyPalletId)),'|',(SELECT COUNT(*) FROM out_stock WHERE warehouse_id=$($fixture.qrOnlyWarehouseId) AND product_id=$($fixture.productId)),'|',(SELECT COUNT(*) FROM inventory WHERE warehouse_id=$($fixture.mixedWarehouseId) AND product_id=$($fixture.productId) AND pallet_code_id=$($fixture.mixedPalletId)),'|',(SELECT COUNT(*) FROM inventory WHERE warehouse_id=$($fixture.mixedWarehouseId) AND product_id=$($fixture.productId) AND pallet_code_id IS NULL),'|',(SELECT COUNT(*) FROM out_stock WHERE warehouse_id=$($fixture.mixedWarehouseId) AND product_id=$($fixture.productId)),'|',(SELECT status FROM pallet_code WHERE id=$($fixture.qrOnlyPalletId)),'|',(SELECT status FROM pallet_code WHERE id=$($fixture.mixedPalletId)),'|',(SELECT cur_capacity FROM warehouse WHERE id=$($fixture.qrOnlyWarehouseId)),'|',(SELECT cur_capacity FROM warehouse WHERE id=$($fixture.mixedWarehouseId))); ")[0]).Split('|')
        Assert-True ($facts[0] -eq '1') 'QR-only pallet inventory was changed'
        Assert-True ($facts[1] -eq '0') 'QR-only warehouse unexpectedly produced an out-stock record'
        Assert-True ($facts[2] -eq '1') 'Mixed warehouse pallet inventory was changed'
        Assert-True ($facts[3] -eq '0') 'Mixed warehouse legacy inventory was not consumed'
        Assert-True ($facts[4] -eq '1') 'Mixed warehouse did not produce exactly one out-stock record'
        Assert-True ($facts[5] -eq 'INSTOCK' -and $facts[6] -eq 'INSTOCK') 'Pallet status changed during legacy outbound'
        Assert-True ($facts[7] -eq '1' -and $facts[8] -eq '1') 'Warehouse capacity did not preserve QR inventory correctly'
        [pscustomobject]@{
            Result = 'PASS'
            QrOnlyRejected = $true
            MixedLegacyConsumed = $true
            PalletInventoriesPreserved = 2
            DatabaseHost = $dbHost
        } | ConvertTo-Json
        exit 0
    }
    Remove-Fixture $fixture
    Remove-Item -LiteralPath $manifestPath
    [pscustomobject]@{ Result = 'PASS'; Cleanup = 'COMPLETE'; DatabaseHost = $dbHost } | ConvertTo-Json
    exit 0
}

$ledgerExists = [int](@(Invoke-LocalMySql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='stock_movement_event';")[0])
$ledgerMigrationApplied = $false
if ($ledgerExists -eq 0) {
    $migration = [System.IO.File]::ReadAllText((Join-Path $scriptRoot '..\migrations\2026-07-29-add-stock-movement-ledger-and-daily-snapshot.sql'))
    Invoke-LocalMySql $migration | Out-Null
    $ledgerMigrationApplied = $true
}

$qrOnlyWarehouseId = 74
$mixedWarehouseId = 75
$warehouseFacts = @(Invoke-LocalMySql "SELECT CONCAT(id,'|',status,'|',cur_capacity,'|',(SELECT COUNT(*) FROM inventory i WHERE i.warehouse_id=w.id)) FROM warehouse w WHERE id IN ($qrOnlyWarehouseId,$mixedWarehouseId) ORDER BY id;")
Assert-True ($warehouseFacts.Count -eq 2) 'Required isolated warehouse fixtures 74 and 75 do not exist'
$qrOriginal = $warehouseFacts[0].Split('|')
$mixedOriginal = $warehouseFacts[1].Split('|')
Assert-True ($qrOriginal[3] -eq '0' -and $mixedOriginal[3] -eq '0') 'Required isolated warehouses are not empty'

$suffix = [Guid]::NewGuid().ToString('N')
$employeeId = "LB$($suffix.Substring(0, 12))"
$testName = "边界验收$($suffix.Substring(0, 6))"
$mobile = "19$([Math]::Abs($suffix.GetHashCode()).ToString().PadLeft(9, '0').Substring(0, 9))"
$productName = "UAT边界黄冰糖$($suffix.Substring(0, 6))"

$operatorId = 0
$productId = 0
$assayId = 0
$qrOnlyPalletId = 0
$mixedPalletId = 0

try {
    Invoke-LocalMySql "INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$(Escape-SqlLiteral $employeeId)','$(Escape-SqlLiteral $testName)','$(Escape-SqlLiteral $mobile)','本地验收','库存边界验收','在职','ADMIN');" | Out-Null
    Invoke-LocalMySql "INSERT INTO user(name,role_code,employee_id,bind_status,login_type) VALUES('$(Escape-SqlLiteral $testName)','ADMIN','$(Escape-SqlLiteral $employeeId)','UNBOUND','WEB');" | Out-Null
    $operatorId = [int](@(Invoke-LocalMySql "SELECT id FROM user WHERE employee_id='$(Escape-SqlLiteral $employeeId)' LIMIT 1;")[0])
    Invoke-LocalMySql "INSERT INTO product(product_name,product_type,status,packaging_method,weight_per_piece,created_by,pieces_per_pallet,can_stack) VALUES('$(Escape-SqlLiteral $productName)','黄冰糖','成品','袋',1.00,$operatorId,25,0);" | Out-Null
    $productId = [int](@(Invoke-LocalMySql "SELECT id FROM product WHERE product_name='$(Escape-SqlLiteral $productName)' LIMIT 1;")[0])
    Invoke-LocalMySql "INSERT INTO assay(product_id,sample_date,tested_by,version,qualified_standards,judge_result,failed_metric_count,is_qualified) VALUES($productId,CURDATE(),$operatorId,1,JSON_ARRAY('UAT验收'),'PASS',0,'合格');" | Out-Null
    $assayId = [int](@(Invoke-LocalMySql "SELECT id FROM assay WHERE product_id=$productId AND sample_date=CURDATE() AND version=1 LIMIT 1;")[0])
    Invoke-LocalMySql "INSERT INTO pallet_code(code,status,product_id,fixed_mode_enabled,product_status,production_date,assay_id,created_by,current_cycle_no) VALUES(NULL,'INSTOCK',$productId,0,'成品',CURDATE(),$assayId,$operatorId,1);" | Out-Null
    $qrOnlyPalletId = [int](@(Invoke-LocalMySql "SELECT id FROM pallet_code WHERE product_id=$productId ORDER BY id ASC LIMIT 1;")[0])
    Invoke-LocalMySql "INSERT INTO pallet_code(code,status,product_id,fixed_mode_enabled,product_status,production_date,assay_id,created_by,current_cycle_no) VALUES(NULL,'INSTOCK',$productId,0,'成品',CURDATE(),$assayId,$operatorId,1);" | Out-Null
    $mixedPalletId = [int](@(Invoke-LocalMySql "SELECT id FROM pallet_code WHERE product_id=$productId ORDER BY id DESC LIMIT 1;")[0])
    $qrOnlyCode = "BTU$($suffix.Substring(0, 8).ToUpperInvariant())"
    $mixedCode = "BTV$($suffix.Substring(8, 8).ToUpperInvariant())"
    Invoke-LocalMySql "UPDATE pallet_code SET code='$(Escape-SqlLiteral $qrOnlyCode)' WHERE id=$qrOnlyPalletId; UPDATE pallet_code SET code='$(Escape-SqlLiteral $mixedCode)' WHERE id=$mixedPalletId; UPDATE warehouse SET status='正常',cur_capacity=1 WHERE id=$qrOnlyWarehouseId; UPDATE warehouse SET status='正常',cur_capacity=2 WHERE id=$mixedWarehouseId; INSERT INTO inventory(warehouse_id,product_id,entry_date,side,``row_number``,layer,quantity,assay_id,product_status,pallet_code_id,pieces) VALUES($qrOnlyWarehouseId,$productId,CURDATE(),'左',1,1,1,$assayId,'成品',$qrOnlyPalletId,0),($mixedWarehouseId,$productId,CURDATE(),'左',1,1,1,$assayId,'成品',$mixedPalletId,0),($mixedWarehouseId,$productId,CURDATE(),'左',2,1,1,$assayId,'成品',NULL,0);" | Out-Null

    $fixture = [ordered]@{
        employeeId = $employeeId
        testName = $testName
        loginPassword = 'lbsp'
        operatorId = $operatorId
        productId = $productId
        productName = $productName
        assayId = $assayId
        qrOnlyWarehouseId = $qrOnlyWarehouseId
        qrOnlyWarehouseName = '74'
        qrOnlyOriginalStatus = $qrOriginal[1]
        qrOnlyOriginalCapacity = [int]$qrOriginal[2]
        qrOnlyPalletId = $qrOnlyPalletId
        qrOnlyPalletCode = $qrOnlyCode
        mixedWarehouseId = $mixedWarehouseId
        mixedWarehouseName = '75'
        mixedOriginalStatus = $mixedOriginal[1]
        mixedOriginalCapacity = [int]$mixedOriginal[2]
        mixedPalletId = $mixedPalletId
        mixedPalletCode = $mixedCode
        ledgerMigrationApplied = $ledgerMigrationApplied
    }
    $manifestDir = Join-Path (Resolve-Path (Join-Path $scriptRoot '..')).Path 'output\legacy-boundary-uat'
    [System.IO.Directory]::CreateDirectory($manifestDir) | Out-Null
    $manifestPath = Join-Path $manifestDir "legacy-boundary-$($suffix.Substring(0, 12)).json"
    [System.IO.File]::WriteAllText($manifestPath, ($fixture | ConvertTo-Json -Depth 4), [System.Text.UTF8Encoding]::new($false))
    [pscustomobject]@{
        Result = 'PASS'
        TestName = $testName
        ProductName = $productName
        QrOnlyWarehouseId = $qrOnlyWarehouseId
        MixedWarehouseId = $mixedWarehouseId
        ManifestPath = $manifestPath
        LedgerMigrationApplied = $ledgerMigrationApplied
        DatabaseHost = $dbHost
    } | ConvertTo-Json
}
catch {
    if ($operatorId -gt 0) {
        $partial = [pscustomobject]@{
            productId = $productId
            operatorId = $operatorId
            employeeId = $employeeId
            qrOnlyWarehouseId = $qrOnlyWarehouseId
            mixedWarehouseId = $mixedWarehouseId
            qrOnlyPalletId = $qrOnlyPalletId
            mixedPalletId = $mixedPalletId
            qrOnlyOriginalStatus = $qrOriginal[1]
            qrOnlyOriginalCapacity = [int]$qrOriginal[2]
            mixedOriginalStatus = $mixedOriginal[1]
            mixedOriginalCapacity = [int]$mixedOriginal[2]
        }
        Remove-Fixture $partial
    }
    throw
}
