param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [string]$MigrationsPath = 'migrations',

    [string]$BaselineThrough,

    [switch]$VerifyOnly,

    [switch]$AllowRemoteDatabase,

    [string]$ExpectedDatabase,

    [int]$LockTimeoutMinutes = 30
)

$ErrorActionPreference = 'Stop'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Escape-SqlLiteral {
    param([string]$Value)
    if ($null -eq $Value) { return '' }
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
$isLocal = $dbHost -in @('127.0.0.1', 'localhost', '::1')
if (-not $isLocal) {
    Assert-True $AllowRemoteDatabase 'Remote database refused; use -AllowRemoteDatabase with -ExpectedDatabase'
    Assert-True (-not [string]::IsNullOrWhiteSpace($ExpectedDatabase)) 'ExpectedDatabase is required for a remote database'
    Assert-True ($ExpectedDatabase -ceq $dbName) "Database confirmation mismatch: expected '$ExpectedDatabase'"
}

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

$migrationRoot = (Resolve-Path -LiteralPath $MigrationsPath).Path
$migrationFiles = @(Get-ChildItem -LiteralPath $migrationRoot -File -Filter '*.sql' | Sort-Object Name)
Assert-True ($migrationFiles.Count -gt 0) "No migrations found in $migrationRoot"
if ($BaselineThrough) {
    Assert-True ($migrationFiles.Name -ccontains $BaselineThrough) "Baseline migration does not exist: $BaselineThrough"
}

$mysqlArgs = @(
    "--host=$dbHost",
    "--port=$dbPort",
    "--user=$($config.DB_USERNAME)",
    "--database=$dbName",
    '--default-character-set=utf8mb4',
    '--batch',
    '--skip-column-names',
    '--binary-mode=1'
)

function Invoke-MySql {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $env:MYSQL_PWD = $config.DB_PASSWORD
    try {
        $output = @($Sql | & $mysql @mysqlArgs 2>&1)
        if ($LASTEXITCODE -ne 0) {
            $safeMessage = ($output | Select-Object -Last 8) -join [Environment]::NewLine
            throw "MySQL migration command failed against $dbHost/$dbName.`n$safeMessage"
        }
        return @($output | ForEach-Object { [string]$_ })
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Get-LedgerRows {
    $exists = @(Invoke-MySql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='schema_migration';")
    if ([int](($exists -join "`n").Trim()) -eq 0) { return @{} }
    $rows = @{}
    $rawRows = @(Invoke-MySql "SELECT CONCAT(migration_name, CHAR(9), checksum_sha256, CHAR(9), execution_type) FROM schema_migration ORDER BY installed_rank;") -join "`n"
    foreach ($line in @($rawRows -split "`r?`n")) {
        $parts = @($line -split '\\t')
        if ($parts.Count -eq 3) {
            $rows[$parts[0]] = [pscustomobject]@{ Checksum = $parts[1]; ExecutionType = $parts[2] }
        }
    }
    return $rows
}

$expected = [ordered]@{}
foreach ($file in $migrationFiles) {
    $expected[$file.Name] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}

$lockOwner = [Guid]::NewGuid().ToString()
$lockAcquired = $false
try {
    if (-not $VerifyOnly) {
        Invoke-MySql @"
CREATE TABLE IF NOT EXISTS schema_migration (
    installed_rank BIGINT NOT NULL AUTO_INCREMENT,
    migration_name VARCHAR(255) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    execution_type VARCHAR(16) NOT NULL,
    executed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    execution_ms BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (installed_rank),
    UNIQUE KEY uk_schema_migration_name (migration_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS schema_migration_lock (
    lock_name VARCHAR(64) NOT NULL,
    owner_token CHAR(36) NOT NULL,
    acquired_at DATETIME(6) NOT NULL,
    PRIMARY KEY (lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@ | Out-Null
        $timeout = [Math]::Max(1, $LockTimeoutMinutes)
        Invoke-MySql "DELETE FROM schema_migration_lock WHERE lock_name='warehouse_schema' AND acquired_at < DATE_SUB(NOW(6), INTERVAL $timeout MINUTE);" | Out-Null
        Invoke-MySql "INSERT INTO schema_migration_lock(lock_name,owner_token,acquired_at) VALUES('warehouse_schema','$(Escape-SqlLiteral $lockOwner)',NOW(6));" | Out-Null
        $lockAcquired = $true
    }

    $ledger = Get-LedgerRows
    $orphaned = @($ledger.Keys | Where-Object { -not $expected.Contains($_) } | Sort-Object)
    Assert-True ($orphaned.Count -eq 0) "Migration ledger contains files missing from the repository: $($orphaned -join ', ')"

    $checksumMismatches = @()
    foreach ($name in $ledger.Keys) {
        if ($ledger[$name].Checksum -cne $expected[$name]) { $checksumMismatches += $name }
    }
    Assert-True ($checksumMismatches.Count -eq 0) "Applied migration checksum mismatch: $($checksumMismatches -join ', ')"

    $pending = @($migrationFiles | Where-Object { -not $ledger.ContainsKey($_.Name) })
    if ($VerifyOnly) {
        [pscustomobject]@{
            Result = if ($pending.Count -eq 0) { 'PASS' } else { 'PENDING' }
            DatabaseHost = $dbHost
            DatabaseName = $dbName
            AppliedCount = $ledger.Count
            PendingCount = $pending.Count
            PendingMigrations = @($pending | ForEach-Object { $_.Name })
        } | ConvertTo-Json -Depth 5
        if ($pending.Count -gt 0) { exit 2 }
        exit 0
    }

    $baselinedCount = 0
    $appliedCount = 0
    foreach ($file in $pending) {
        $name = Escape-SqlLiteral $file.Name
        $checksum = $expected[$file.Name]
        $shouldBaseline = $BaselineThrough -and [string]::CompareOrdinal($file.Name, $BaselineThrough) -le 0
        if ($shouldBaseline) {
            Invoke-MySql "INSERT INTO schema_migration(migration_name,checksum_sha256,execution_type,execution_ms) VALUES('$name','$checksum','BASELINED',0);" | Out-Null
            $baselinedCount += 1
            continue
        }

        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $sql = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        $sql += "`nINSERT INTO schema_migration(migration_name,checksum_sha256,execution_type,execution_ms) VALUES('$name','$checksum','APPLIED',0);`n"
        Invoke-MySql $sql | Out-Null
        $stopwatch.Stop()
        Invoke-MySql "UPDATE schema_migration SET execution_ms=$($stopwatch.ElapsedMilliseconds) WHERE migration_name='$name';" | Out-Null
        $appliedCount += 1
    }

    $finalLedger = Get-LedgerRows
    Assert-True ($finalLedger.Count -eq $migrationFiles.Count) 'Migration ledger is incomplete after apply'
    [pscustomobject]@{
        Result = 'PASS'
        DatabaseHost = $dbHost
        DatabaseName = $dbName
        MigrationCount = $migrationFiles.Count
        BaselinedCount = $baselinedCount
        AppliedCount = $appliedCount
    } | ConvertTo-Json -Depth 4
} finally {
    if ($lockAcquired) {
        try {
            Invoke-MySql "DELETE FROM schema_migration_lock WHERE lock_name='warehouse_schema' AND owner_token='$(Escape-SqlLiteral $lockOwner)';" | Out-Null
        } catch {
            Write-Warning 'Failed to release database migration lock; it will expire by timeout.'
        }
    }
}
