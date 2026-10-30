param(
    [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
    [string]$BaseUrl='http://127.0.0.1:38083',
    [ValidateRange(1,5)][int]$Rounds=3,
    [switch]$VerifyExistingEvidence
)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('localhost','127.0.0.1')){throw 'Local acceptance only'}
$f=Get-Content -Raw -Encoding utf8 (Join-Path $PSScriptRoot '../output/assay-group-live-20260924/fixture.json')|ConvertFrom-Json
if($f.Cleaned -or $f.Marker -notmatch '^UAT_AGLIVE_[a-f0-9]{32}$'){throw 'Exclusive fixture required'}
$productId=[int]$f.ProductIds[0];$name=$f.ProductNames[0];$date='2026-09-22'
if($name -notmatch '^化验组验收[0-9]{10}甲糖$'){throw 'Invalid exclusive product name'}
$cfg=@{}
foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?;]+)'){throw 'Local DB only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$dbName=$Matches[3]
function Sql($query){
    $old=$env:MYSQL_PWD;$env:MYSQL_PWD=$cfg.DB_PASSWORD
    try{$rows=@(& 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$dbName --default-character-set=utf8mb4 --batch --skip-column-names --execute=$query);if($LASTEXITCODE){throw 'Local SQL failed'};return ,$rows}
    finally{$env:MYSQL_PWD=$old}
}
if([int](Sql "SELECT COUNT(*) FROM product p JOIN product_quality_standard_relation r ON r.product_id=p.id JOIN quality_standards s ON s.id=r.quality_standard_id WHERE p.id=$productId AND p.product_name='$name' AND s.standard_code='$($f.Marker)_0';")[0] -ne 1){throw 'Exclusive fixture identity mismatch'}
$out=Join-Path $PSScriptRoot '../output/assay-web-agent-race-20260925'
if($VerifyExistingEvidence){
    $evidence=Get-Content -Raw -Encoding utf8 (Join-Path $out 'summary.json')|ConvertFrom-Json
    $rows=Sql "SELECT version,color_value,ph_value,dry_weight,judge_result FROM assay WHERE product_id=$productId AND sample_date='$date' ORDER BY version;"
    $expected=@(1..4|ForEach-Object {"$_`t24.00`t0.00`tNULL`tPASS"})
    $ok=$evidence.Count -eq 4 -and @($evidence|Where-Object {$_.Web.Body.code -ne 200 -or $_.Agent.Body.code -ne 1055}).Count -eq 0 -and ($rows -join '|') -eq ($expected -join '|')
    $result=@{Pass=[bool]$ok;Rows=$rows;Expected=$expected;OriginalScriptPassed=$false;Note='Read-only reconciliation: four Web saves, four stale Agent rejections. Original last-round baseline read was empty/zero; original failure evidence retained. No test replay or cleanup.'}
    [IO.File]::WriteAllText((Join-Path $out 'read-only-reconciliation.json'),($result|ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
    $result|ConvertTo-Json -Depth 10
    if(-not $ok){throw 'Existing evidence reconciliation failed'}
    exit
}
if([int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$productId AND sample_date='$date';")[0] -ne 0){throw 'Race date must start empty; do not replay destructive cleanup'}
if(Test-Path (Join-Path $out 'summary.json')){throw 'Existing race evidence; inspect before another run'}
[IO.Directory]::CreateDirectory($out)|Out-Null
function Save($name,$value){
    $j=$value|ConvertTo-Json -Depth 60
    $j=[regex]::Replace($j,'("(?:token|previewRef|executionToken|idempotencyKey)"\s*:\s*)"[^"]*"','$1"[redacted]"')
    [IO.File]::WriteAllText((Join-Path $out "$name.json"),$j,[Text.UTF8Encoding]::new($false))
}
$headers=@{}
function Api($path,$body=$null,$method='Post'){
    $p=@{Uri="$BaseUrl$path";Headers=$headers;Method=$method;TimeoutSec=60;SkipHttpErrorCheck=$true}
    if($null -ne $body){$p.ContentType='application/json; charset=utf-8';$p.Body=[Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $body -Depth 20))}
    Invoke-RestMethod @p
}
$account=Get-Content -Raw -Encoding utf8 (Join-Path $PSScriptRoot '../output/assay-d4-permissions/fixture.json')|ConvertFrom-Json
$token=(Api '/api/auth/web-login' @{name=$account.QcName;password='lbsp-isolated-uat'}).data.token
if(-not $token){throw 'QC login failed'}
$headers.Authorization="Bearer $token"
$sid=(Api '/api/agent/sessions' @{}).data.agentSessionId
if(-not $sid){throw 'Session creation failed'}
$root="/api/agent/sessions/$sid/assay-entry/previews"
$http=[Net.Http.HttpClient]::new();$http.Timeout=[TimeSpan]::FromSeconds(60)
$http.DefaultRequestHeaders.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token)
function BeginPost($path,$body){
    $content=[Net.Http.StringContent]::new((ConvertTo-Json -InputObject $body -Depth 20),[Text.Encoding]::UTF8,'application/json')
    return $http.PostAsync("$BaseUrl$path",$content)
}
function Complete($task){
    $response=$task.GetAwaiter().GetResult()
    try{ return @{Http=[int]$response.StatusCode;Body=($response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json)} }
    finally{$response.Dispose()}
}
$checks=[Collections.Generic.List[object]]::new()
try{
    for($round=0;$round -le $Rounds;$round++){
        $baseline=Sql "SELECT COALESCE(MAX(version),0) FROM assay WHERE product_id=$productId AND sample_date='$date';"
        if($baseline.Count -ne 1 -or $baseline[0] -notmatch '^\d+$'){throw 'Missing or invalid version baseline; no requests sent'}
        $before=[int]$baseline[0]
        if($before -lt $round){throw 'Version baseline regressed; no requests sent'}
        $input=@{productId=$productId;sampleDate=$date;colorValue=23;phValue=0}
        $preview=Api $root $input
        if($preview.code -ne 200){throw 'Preview failed'}
        $ref=$preview.data.previewRef
        $confirm=Api "$root/$ref/confirm"
        if($confirm.code -ne 200){throw 'Confirmation failed'}
        $credentials=@{executionToken=$confirm.data.executionToken;idempotencyKey=$confirm.data.idempotencyKey}
        $webBody=@(@{productId=$productId;selectType=1;sampleDate=$date;colorValue=24;phValue=0})
        if($round -eq 0){
            # Deterministic stale-confirmation check before genuine races.
            $web=Complete (BeginPost '/api/assay/import' $webBody)
            $agent=Complete (BeginPost "$root/$ref/execute" $credentials)
        }else{
            # Both HTTP requests start before either response is awaited.
            $agentTask=BeginPost "$root/$ref/execute" $credentials
            $webTask=BeginPost '/api/assay/import' $webBody
            $agent=Complete $agentTask;$web=Complete $webTask
        }
        $rows=Sql "SELECT version,color_value,ph_value,dry_weight,judge_result FROM assay WHERE product_id=$productId AND sample_date='$date' AND version>$before ORDER BY version;"
        $saved=$agent.Body.code -eq 200 -and $agent.Body.data.saved -eq $true
        $expected=if($saved){@("$($before+1)`t23.00`t0.00`tNULL`tPASS","$($before+2)`t24.00`t0.00`tNULL`tPASS")}else{@("$($before+1)`t24.00`t0.00`tNULL`tPASS")}
        $ok=$web.Body.code -eq 200 -and (($rows -join '|') -eq ($expected -join '|')) -and ($saved -or $agent.Body.code -eq 1055)
        if($round -eq 0){$ok=$ok -and -not $saved}
        if($saved){$ok=$ok -and $agent.Body.data.version -eq ($before+1)}
        $e=@{Case=if($round -eq 0){'web-before-agent'}else{"race-$round"};Pass=[bool]$ok;Product=$name;Date=$date;VersionBefore=$before;Agent=$agent;Web=$web;Rows=$rows;Expected=$expected}
        Save $e.Case $e;$checks.Add($e);Save 'summary' $checks
        @{Case=$e.Case;Pass=$ok;WebCode=$web.Body.code;AgentCode=$agent.Body.code;Rows=$rows}|ConvertTo-Json -Compress
        if(-not $ok){throw 'Web/Agent race invariant failed'}
        Api "$root/$ref/cancel"|Out-Null
    }
}finally{
    $http.Dispose()
    Api "/api/agent/sessions/$sid" $null 'Delete'|Out-Null
}
Save 'remaining' @{Rows=(Sql "SELECT product_id,sample_date,version,color_value,ph_value FROM assay WHERE product_id=$productId AND sample_date='$date' ORDER BY version;");Cleaned=$false;Note='Exclusive fixture retained pending explicit cleanup authorization'}
