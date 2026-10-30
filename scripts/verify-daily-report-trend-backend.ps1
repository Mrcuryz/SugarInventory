param(
    [string]$EnvFile = 'D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
    [string]$EvidenceName = 'daily-trend-backend'
)
$ErrorActionPreference = 'Stop'
if ($EvidenceName -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid evidence name' }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$out = Join-Path $root "output/$EvidenceName"
[IO.Directory]::CreateDirectory($out) | Out-Null
$names = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'DAILY_TREND_MYSQL_UAT')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$started = Get-Date
$code = 1
try {
    $config = @{}
    foreach ($line in [IO.File]::ReadAllLines($EnvFile)) {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $config[$Matches[1]] = $Matches[2] }
    }
    if ($config.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(:[0-9]+)?/[^/?;]+(\?.*)?$') {
        throw 'Only the explicitly configured local database is allowed'
    }
    foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
        [Environment]::SetEnvironmentVariable($name, $config[$name], 'Process')
    }
    $env:DAILY_TREND_MYSQL_UAT = 'true'
    Push-Location $root
    try {
        & mvn -q '-Dtest=*RegisteredReport*Test,ProductionDaily*Test' test
        $code = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
$reports = @(Get-ChildItem (Join-Path $root 'target/surefire-reports/TEST-*.xml') |
    Where-Object { $_.Name -match 'RegisteredReport|ProductionDaily' -and $_.LastWriteTime -ge $started.AddSeconds(-2) })
$results = @($reports | ForEach-Object {
    [xml]$xml = Get-Content -Raw $_.FullName
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $out $_.Name)
    [pscustomobject]@{ Suite = $xml.testsuite.name; Tests = [int]$xml.testsuite.tests;
        Failures = [int]$xml.testsuite.failures; Errors = [int]$xml.testsuite.errors; Skipped = [int]$xml.testsuite.skipped }
})
$mysql = @($results | Where-Object Suite -match 'ProductionDailyAggregateMysqlTest$')
$verified = $code -eq 0 -and $mysql.Count -eq 1 -and $mysql[0].Tests -eq 1 -and $mysql[0].Skipped -eq 0
$summary = [ordered]@{
    Result = $(if ($verified) { 'PASS' } else { 'FAIL' }); StartedAt = $started.ToString('o'); CompletedAt = (Get-Date).ToString('o')
    Boundary = 'Local MySQL only; new fixtures rolled back in the same transaction; no existing rows updated'
    ModelOrBrowserAcceptance = 'NOT_RUN'; Suites = $results
}
[IO.File]::WriteAllText((Join-Path $out 'summary.json'), ($summary | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
$summary | ConvertTo-Json -Depth 10
if (-not $verified) { throw 'Daily trend backend checks did not pass; inspect the evidence directory' }
