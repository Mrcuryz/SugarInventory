param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [string]$CleanupManifest
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
    $sqlPath = Join-Path ([System.IO.Path]::GetTempPath()) (
        "laibin-permission-matrix-" + [Guid]::NewGuid().ToString('N') + '.sql')
    [System.IO.File]::WriteAllText(
        $sqlPath,
        $Sql,
        [System.Text.UTF8Encoding]::new($false))
    $sourcePath = $sqlPath.Replace('\\', '/')
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
            "--execute=source $sourcePath"
        if ($LASTEXITCODE -ne 0) { throw "mysql exited with code $LASTEXITCODE" }
        Write-Output $output
    }
    finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $sqlPath -Force -ErrorAction SilentlyContinue
    }
}

function Remove-Fixture {
    param([object]$Fixture)
    $viewRoleCode = Escape-SqlLiteral ([string]$Fixture.viewRoleCode)
    $viewEmployeeId = Escape-SqlLiteral ([string]$Fixture.viewEmployeeId)
    $adminEmployeeId = Escape-SqlLiteral ([string]$Fixture.adminEmployeeId)
    Invoke-LocalMySql "DELETE FROM user WHERE employee_id IN ('$viewEmployeeId','$adminEmployeeId'); DELETE FROM employee_roster WHERE employee_id IN ('$viewEmployeeId','$adminEmployeeId'); DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id WHERE r.role_code='$viewRoleCode'; DELETE FROM role WHERE role_code='$viewRoleCode';" | Out-Null
}

if ($CleanupManifest) {
    $manifestPath = (Resolve-Path -LiteralPath $CleanupManifest).Path
    $fixture = Get-Content -LiteralPath $manifestPath -Encoding UTF8 -Raw | ConvertFrom-Json
    Remove-Fixture $fixture
    Remove-Item -LiteralPath $manifestPath
    [pscustomobject]@{ Result = 'PASS'; Cleanup = 'COMPLETE'; DatabaseHost = $dbHost } | ConvertTo-Json
    exit 0
}

$migrationPath = Join-Path $scriptRoot '..\migrations\2026-08-12-complete-business-write-permission-matrix.sql'
$migration = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $migrationPath).Path)
Invoke-LocalMySql $migration | Out-Null

$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8).ToUpperInvariant()
$viewRoleCode = "UAT_VIEW_$suffix"
$viewName = "PV$($suffix.Substring(0, 4))"
$adminName = "PA$($suffix.Substring(4, 4))"
$viewEmployeeId = "PV$($suffix)"
$adminEmployeeId = "PA$($suffix)"
$viewMobile = "18$([Math]::Abs($viewRoleCode.GetHashCode()).ToString().PadLeft(9, '0').Substring(0, 9))"
$adminMobile = "17$([Math]::Abs($adminEmployeeId.GetHashCode()).ToString().PadLeft(9, '0').Substring(0, 9))"

$fixture = [ordered]@{
    viewRoleCode = $viewRoleCode
    viewEmployeeId = $viewEmployeeId
    adminEmployeeId = $adminEmployeeId
}

try {
    Invoke-LocalMySql "INSERT INTO role(role_name,role_code,description,status) VALUES('Browser read-only UAT','$viewRoleCode','Local permission-matrix UAT only','ENABLED'); INSERT INTO role_permission(role_id,permission_id) SELECT r.id,p.id FROM role r JOIN permission p ON p.perm_code IN ('system:access','dashboard:view','product:view','inventory:view','warehouse:view','warehouse_map:view','qrcode:view','task:view','screen_mesh:view','assay_group:view','quality_standard:view','record:query') WHERE r.role_code='$viewRoleCode'; INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$viewEmployeeId','$(Escape-SqlLiteral $viewName)','$viewMobile','Local UAT','Read-only acceptance',CONVERT(0xE59CA8E8818C USING utf8mb4),'$viewRoleCode'),('$adminEmployeeId','$(Escape-SqlLiteral $adminName)','$adminMobile','Local UAT','Administrator acceptance',CONVERT(0xE59CA8E8818C USING utf8mb4),'ADMIN'); INSERT INTO user(name,role_code,employee_id,bind_status,login_type) VALUES('$(Escape-SqlLiteral $viewName)','$viewRoleCode','$viewEmployeeId','UNBOUND','WEB'),('$(Escape-SqlLiteral $adminName)','ADMIN','$adminEmployeeId','UNBOUND','WEB');" | Out-Null

    $facts = (@(Invoke-LocalMySql "SELECT CONCAT((SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='$viewRoleCode' AND p.perm_code IN ('inventory:inbound','inventory:outbound','inventory:transfer','warehouse:update','task:create','task:confirm','qrcode:generate','qrcode:invalidate')),'|',(SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='ADMIN' AND p.perm_code IN ('inventory:inbound','inventory:outbound','inventory:transfer','warehouse:update','task:create','task:confirm','qrcode:generate','qrcode:invalidate')));")[0]).Split('|')
    Assert-True ($facts[0] -eq '0') 'View-only role unexpectedly received write permissions'
    Assert-True ([int]$facts[1] -eq 8) 'ADMIN is missing required browser acceptance permissions'

    $fixture.viewName = $viewName
    $fixture.adminName = $adminName
    $manifestDir = Join-Path (Resolve-Path (Join-Path $scriptRoot '..')).Path 'output\permission-matrix-uat'
    [System.IO.Directory]::CreateDirectory($manifestDir) | Out-Null
    $manifestPath = Join-Path $manifestDir "permission-matrix-$($suffix.ToLowerInvariant()).json"
    [System.IO.File]::WriteAllText($manifestPath, ($fixture | ConvertTo-Json -Depth 4), [System.Text.UTF8Encoding]::new($false))

    [pscustomobject]@{
        Result = 'PASS'
        ViewName = $viewName
        AdminName = $adminName
        ManifestPath = $manifestPath
        DatabaseHost = $dbHost
    } | ConvertTo-Json
}
catch {
    Remove-Fixture ([pscustomobject]$fixture)
    throw
}
