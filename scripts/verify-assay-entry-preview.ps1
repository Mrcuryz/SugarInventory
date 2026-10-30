param(
    [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
    [string]$BaseUrl='http://127.0.0.1:38083',
    [string]$LoginPassword='lbsp-isolated-uat'
)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local acceptance only'}
$cfg=@{}
foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?]+)'){throw 'Local DB only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$dbName=$Matches[3]
function Sql($query){
    if($query -notmatch '^SELECT '){throw 'Only SELECT is allowed by this verifier'}
    $env:MYSQL_PWD=$cfg.DB_PASSWORD
    try{
        $rows=@($query | & 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$dbName --default-character-set=utf8mb4 --batch --skip-column-names)
        if($LASTEXITCODE){throw 'Read-only SQL failed'}
        return ,$rows
    }finally{Remove-Item Env:MYSQL_PWD}
}
$out=Join-Path $PSScriptRoot '../output/assay-entry-preview'
[IO.Directory]::CreateDirectory($out)|Out-Null
function Save($name,$value){[IO.File]::WriteAllText((Join-Path $out "$name.json"),($value|ConvertTo-Json -Depth 25),[Text.UTF8Encoding]::new($false))}
$headers=@{}
function Api($path,$body){
    Invoke-RestMethod -Uri "$BaseUrl$path" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 12))) -TimeoutSec 60
}
$countsSql='SELECT COUNT(*),COALESCE(MAX(id),0),COALESCE(SUM(version),0) FROM assay;'
$before=Sql $countsSql
$checks=[Collections.Generic.List[object]]::new()
function Check($case,$body,$expectedCode,$predicate){
    $r=Api '/api/assay/entry/preview' $body
    $passed=$r.code -eq $expectedCode -and (& $predicate $r)
    Save $case @{Input=$body;Response=$r;Pass=$passed}
    $checks.Add(@{Case=$case;Pass=$passed;Code=$r.code})
    if(-not $passed){throw "Failed preview case: $case"}
}
try{
    $login=Api '/api/auth/web-login' @{name='陈思聪';password=$LoginPassword}
    if(-not $login.data.token){throw 'Login failed'}
    $headers.Authorization="Bearer $($login.data.token)"
    $productId=[int](Sql "SELECT id FROM product WHERE status='成品' ORDER BY id LIMIT 1;")[0]
    $normal=@{productId=$productId;sampleDate='2026-09-23';colorValue=268;reducingSugar=0.15;phValue=7.52}
    Check 'P01-single-product' $normal 200 {param($r) $r.data.previewOnly -and $r.data.metrics.Count -eq 7 -and ($r.data.metrics|Where-Object field -eq 'colorValue').value -eq 268 -and ($r.data.metrics|Where-Object field -eq 'dryWeight').value -eq $null}
    Check 'P02-repeat-preview' $normal 200 {param($r) $r.data.previewOnly -and $r.data.proposedVersion -ge 1}
    Check 'P03-zero-not-null' @{productId=$productId;sampleDate='2026-09-23';colorValue=0} 200 {param($r) ($r.data.metrics|Where-Object field -eq 'colorValue').provided -and ($r.data.metrics|Where-Object field -eq 'colorValue').value -eq 0}
    Check 'P04-excess-precision' @{productId=$productId;sampleDate='2026-09-23';colorValue=268.001} 1051 {param($r) $r.msg -match '两位小数' -and $null -eq $r.data}
    Check 'P05-invalid-ph' @{productId=$productId;sampleDate='2026-09-23';phValue=14.01} 1051 {param($r) $null -eq $r.data}
    Check 'P06-empty-measurements' @{productId=$productId;sampleDate='2026-09-23'} 1051 {param($r) $r.msg -match '至少提供一项'}
    Check 'P07-missing-date' @{productId=$productId;colorValue=268} 400 {param($r) $null -eq $r.data}
    Check 'P08-reject-group-fields' @{productId=$productId;sampleDate='2026-09-23';colorValue=268;selectType=2;relatedId=1} 1051 {param($r) $r.msg -match '不支持的录入字段'}
    $missing=[int](Sql 'SELECT COALESCE(MAX(id),0)+1 FROM product;')[0]
    Check 'P09-missing-product' @{productId=$missing;sampleDate='2026-09-23';colorValue=268} 1001 {param($r) $null -eq $r.data}
    $existing=Sql 'SELECT a.product_id,a.sample_date,MAX(a.version) FROM assay a JOIN product p ON p.id=a.product_id GROUP BY a.product_id,a.sample_date ORDER BY MAX(a.id) DESC LIMIT 1;'
    if(-not $existing){throw 'Existing assay required for version preview acceptance'}
    $parts=$existing[0] -split "`t";$expectedVersion=[int]$parts[2]
    Check 'P10-existing-version' @{productId=[int]$parts[0];sampleDate=$parts[1];colorValue=268} 200 {param($r) $r.data.existingVersion -eq $expectedVersion -and $r.data.proposedVersion -eq ($expectedVersion+1) -and ($r.data.warnings -join ' ') -match '不覆盖旧记录'}
    Check 'P11-all-seven-measurements' @{productId=$productId;sampleDate='2026-09-23';colorValue=268;reducingSugar=0.15;dryWeight=0;conductivityAsh=0.01;sucrose=99.50;insolubleImpurity=0;phValue=7.52} 200 {param($r) $r.data.metrics.Count -eq 7 -and @($r.data.metrics|Where-Object { -not $_.provided }).Count -eq 0 -and ($r.data.metrics|Where-Object field -eq 'dryWeight').value -eq 0}
    $anonymous=Invoke-WebRequest -Uri "$BaseUrl/api/assay/entry/preview" -Method Post -ContentType 'application/json' -Body ($normal|ConvertTo-Json) -SkipHttpErrorCheck -TimeoutSec 30
    $anonymousRejected=[int]$anonymous.StatusCode -in @(401,403)
    Save 'P12-anonymous-denied' @{Input=$normal;HttpStatus=[int]$anonymous.StatusCode;Response=$anonymous.Content;Pass=$anonymousRejected}
    $checks.Add(@{Case='P12-anonymous-denied';Pass=$anonymousRejected;Code=[int]$anonymous.StatusCode})
    if(-not $anonymousRejected){throw 'Anonymous request must be rejected by authentication filter'}
}finally{
    $after=Sql $countsSql
    $unchanged=($before -join '') -eq ($after -join '')
    Save 'database-observation' @{Before=$before;After=$after;CountMaxIdVersionSumUnchanged=$unchanged;Note='本脚本仅调用预览和SELECT；无测试业务写入。此聚合检查不冒充逐字段数据库指纹。'}
    Save 'summary' $checks
    if(-not $unchanged){throw 'Assay aggregate changed during preview acceptance; inspect before proceeding'}
}
$checks | ConvertTo-Json -Depth 5
