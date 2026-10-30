param([ValidateSet('Prepare','Verify','Cleanup')][string]$Mode='Verify',
 [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
 [string]$BaseUrl='http://127.0.0.1:38082', [string]$EvidencePrefix='', [switch]$SkipModel,
 [ValidateRange(0,5)][int]$FollowupRounds=0, [switch]$SkipOverviewCases)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local only'}
if($EvidencePrefix -notmatch '^[a-zA-Z0-9_-]*$'){throw 'Invalid evidence prefix'}
$out=Join-Path $PSScriptRoot '../output/daily-report-overview'
[IO.Directory]::CreateDirectory($out)|Out-Null
function Save($name,$value){[IO.File]::WriteAllText((Join-Path $out "$EvidencePrefix$name.json"),($value|ConvertTo-Json -Depth 60),[Text.UTF8Encoding]::new($false))}
if($Mode -eq 'Cleanup'){
 & (Join-Path $PSScriptRoot 'verify-daily-report-fixture.ps1') -Mode Cleanup -EnvFile $EnvFile
 Save 'cleanup' (Get-Content -Raw (Join-Path $PSScriptRoot '../output/daily-report-fixture/cleanup.json') | ConvertFrom-Json)
 exit
}
if($Mode -eq 'Prepare'){
 & (Join-Path $PSScriptRoot 'verify-daily-report-fixture.ps1') -Mode Prepare -EnvFile $EnvFile
 $cfg=@{};foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
 if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?]+)'){throw 'Local DB only'}
 $hostName=$Matches[1];$port=if($Matches[2]){$Matches[2]}else{'3306'};$db=$Matches[3]
 $marker='UAT_DAILY_20260908'
 $sql=@"
START TRANSACTION;
INSERT INTO production_daily_metric_value(report_section_id,metric_code,metric_name_snapshot,group_code,group_name_snapshot,unit_snapshot,daily_actual,converted_tons,month_tons,year_tons,remark,display_order)
SELECT s.id,'FACTORY_TOTAL_OUTPUT','总产量','FACTORY_METRIC','全厂综合指标','件',CASE DAY(r.report_date) WHEN 6 THEN 100 ELSE 200 END,CASE DAY(r.report_date) WHEN 6 THEN 1 ELSE 2 END,9999,88888,'$marker',1
FROM production_daily_report r JOIN production_daily_report_section s ON s.report_id=r.id WHERE r.prepared_by_name='$marker' AND s.department_code='FACTORY_SUMMARY';
INSERT INTO production_daily_metric_value(report_section_id,metric_code,metric_name_snapshot,group_code,group_name_snapshot,unit_snapshot,daily_actual,remark,display_order)
SELECT s.id,'POLY_OUTPUT','冰糖产量','COMPREHENSIVE','综合指标','吨',CASE DAY(r.report_date) WHEN 6 THEN 0 ELSE NULL END,'$marker',29
FROM production_daily_report r JOIN production_daily_report_section s ON s.report_id=r.id WHERE r.prepared_by_name='$marker' AND s.department_code='POLYCRYSTAL';
INSERT INTO production_daily_metric_value(report_section_id,metric_code,metric_name_snapshot,group_code,group_name_snapshot,unit_snapshot,daily_actual,remark,display_order)
SELECT s.id,'FACTORY_TOTAL_POWER','总耗电量','FACTORY_METRIC','全厂综合指标','Kw.h',CASE DAY(r.report_date) WHEN 6 THEN 120 ELSE 180 END,'$marker',2
FROM production_daily_report r JOIN production_daily_report_section s ON s.report_id=r.id WHERE r.prepared_by_name='$marker' AND s.department_code='FACTORY_SUMMARY';
COMMIT;
"@
 $env:MYSQL_PWD=$cfg.DB_PASSWORD
 try{$sql | & 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$hostName --port=$port --user=$($cfg.DB_USERNAME) --database=$db --default-character-set=utf8mb4 --batch;if($LASTEXITCODE){throw 'Fixture SQL failed'}}finally{Remove-Item Env:MYSQL_PWD}
 'Prepared overview metrics; use this script Verify, then Cleanup. Original 52-row verification no longer applies.'
 exit
}
$h=@{}
function Api($path,$body=$null){$p=@{Uri="$BaseUrl$path";Headers=$h;TimeoutSec=180};if($null -ne $body){$p.Method='Post';$p.ContentType='application/json; charset=utf-8';$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 20))};Invoke-RestMethod @p}
function Check($ok,$label){if(-not $ok){throw "FAIL: $label"};"PASS: $label"}
$login=Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'}
if(-not $login.data.token){throw 'Login failed'};$h.Authorization="Bearer $($login.data.token)"
$query=@{startDate='2026-09-06';endDate='2026-09-07';size=20;page=1}
$r=Api '/api/production/agent-read/daily-reports/query' $query;Save 'page1' $r
Check ($r.code -eq 200 -and $r.data.total -eq 58) '58 details including six new saved metrics'
$factory=@($r.data.overview|Where-Object summaryType -eq '全厂产量')[0]
$poly=@($r.data.overview|Where-Object summaryType -eq '车间产量')[0]
$product=@($r.data.overview|Where-Object summaryType -eq '产品分类汇总')[0]
Check ($factory.quantity -eq 300 -and $factory.convertedTons -eq 3) 'factory output 300 pieces / saved 3 tons, no cumulative double count'
Check ($factory.quantityRows -eq 2 -and $factory.draftRows -eq 1) 'factory includes one draft section, explicitly marked'
Check ($poly.quantity -eq 0 -and $poly.quantityRows -eq 1 -and $poly.recordCount -eq 2 -and $poly.observedDays -eq 1) 'zero plus null remains partial zero, not two complete days'
Check ($product.quantity -eq 594 -and $null -eq $product.convertedTons -and $product.quantityRows -eq 46) 'product quantity excludes null without inventing tons'
Check (@($r.data.overview|Where-Object label -match '率').Count -eq 0) 'rates not aggregated'
$query.page=3;$last=Api '/api/production/agent-read/daily-reports/query' $query;Save 'page3' $last
Check (($r.data.overview|ConvertTo-Json -Depth 10 -Compress) -eq ($last.data.overview|ConvertTo-Json -Depth 10 -Compress)) 'overview identical on detail page 1 and 3'
$query.page=1;$query.itemQuery='黄冰糖';$filtered=Api '/api/production/agent-read/daily-reports/query' $query;Save 'product-filter' $filtered
Check ($filtered.data.overview.Count -eq 1 -and $filtered.data.overview[0].quantity -eq 594) 'product filter excludes unrelated factory metrics'
$query.Remove('itemQuery');$query.startDate='2026-09-05';$coverage=Api '/api/production/agent-read/daily-reports/query' $query;Save 'coverage' $coverage
Check ($coverage.data.requestedDays -eq 3 -and ($coverage.data.overview|Where-Object summaryType -eq '全厂产量').observedDays -eq 2) 'missing day remains partial coverage'
Check (-not (($r.data.overview|Where-Object summaryType -eq '全厂能耗').coverageLabel -match '折吨')) 'energy overview has no irrelevant tonnage warning'
if($SkipModel){exit}
$cases=@(
 @('O01','2026-09-06到2026-09-07产量如何？'),
 @('O02','2026-09-06到2026-09-07黄冰糖生产了多少？'),
 @('O03','查看2026-09-05到2026-09-07的生产概况'),
 @('O04','今天产量如何？'))
$checks=@()
if($SkipOverviewCases){$cases=@()}
foreach($c in $cases){
 $s=Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')};$sid=$s.data.agentSessionId
 if(-not $sid){throw 'Session failed'}
 try{
  $w=[Diagnostics.Stopwatch]::StartNew();$answer=Api "/api/agent/sessions/$sid/messages" @{message=$c[1]}
  $cards=@($answer.data.cards|Where-Object cardType -eq 'production_daily_records')
  $ok=$answer.code -eq 200 -and $cards.Count -eq 1 -and $answer.data.answer -notmatch '服务暂不可用'
  if($ok -and $c[0] -ne 'O04'){
   $items=@($cards[0].fields|Where-Object kind -eq 'daily_records_overview')
   $match=if($c[0] -eq 'O02'){@($items|Where-Object summaryType -eq '产品分类汇总')}else{@($items|Where-Object summaryType -eq '全厂产量')}
   $ok=$match.Count -eq 1 -and $match[0].quantity -eq $(if($c[0] -eq 'O02'){594}else{300})
  }
  $e=@{Input=$c[1];Answer=$answer.data.answer;Cards=$answer.data.cards;Seconds=$w.Elapsed.TotalSeconds;StructuredCheck=$ok};Save $c[0] $e
  $checks+=@{Case=$c[0];Pass=$ok};@{Case=$c[0];Answer=$e.Answer;StructuredCheck=$ok}|ConvertTo-Json -Compress
 }finally{Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $h|Out-Null}
}
for($round=1;$round -le $FollowupRounds;$round++){
 $s=Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')};$sid=$s.data.agentSessionId
 if(-not $sid){throw 'Session failed'}
 try{
  $turns=@('查看2026-09-05到2026-09-07的生产概况，每页20条','下一页','只看黄冰糖')
  for($i=0;$i -lt $turns.Count;$i++){
   $watch=[Diagnostics.Stopwatch]::StartNew()
   $answer=Api "/api/agent/sessions/$sid/messages" @{message=$turns[$i]}
   $cards=@($answer.data.cards|Where-Object cardType -eq 'production_daily_records')
   $ok=$answer.code -eq 200 -and $cards.Count -eq 1
   if($ok){
    $summary=@($cards[0].fields|Where-Object kind -eq 'daily_records_summary')[0]
    $overview=@($cards[0].fields|Where-Object kind -eq 'daily_records_overview')
    $expectedPage=if($i -eq 1){2}else{1}
    $ok=$summary.page -eq $expectedPage -and $summary.size -eq 20 -and $summary.dateRangeLabel -eq '2026-09-05 至 2026-09-07'
    if($i -eq 2){
     $ok=$ok -and $summary.total -eq 48 -and $overview.Count -eq 1 -and $overview[0].quantity -eq 594
    }else{
     $factory=@($overview|Where-Object summaryType -eq '全厂产量')
     $ok=$ok -and $summary.total -eq 58 -and $factory.Count -eq 1 -and $factory[0].quantity -eq 300 -and $factory[0].convertedTons -eq 3
    }
   }
   $case="F$round-T$($i+1)"
   $e=@{Input=$turns[$i];Answer=$answer.data.answer;Cards=$answer.data.cards;Seconds=$watch.Elapsed.TotalSeconds;StructuredCheck=$ok}
   Save $case $e;$checks+=@{Case=$case;Pass=$ok}
   @{Case=$case;Answer=$e.Answer;StructuredCheck=$ok}|ConvertTo-Json -Compress
  }
 }finally{Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $h|Out-Null}
}
Save 'model-checks' $checks
if(@($checks|Where-Object{-not $_.Pass}).Count){throw 'Real-model overview acceptance failed; retain evidence and clean fixtures'}
