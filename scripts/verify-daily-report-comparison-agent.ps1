param([string]$BaseUrl='http://127.0.0.1:38083', [ValidateRange(1,3)][int]$Rounds=3,
 [string]$EvidenceName='daily-report-comparison-c2', [switch]$SkipModel)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local only'}
if($EvidenceName -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Invalid evidence name'}
$out=Join-Path $PSScriptRoot "../output/$EvidenceName"
[IO.Directory]::CreateDirectory($out)|Out-Null
function Save($name,$value){[IO.File]::WriteAllText((Join-Path $out "$name.json"),($value|ConvertTo-Json -Depth 75),[Text.UTF8Encoding]::new($false))}
$h=@{}
function Api($path,$body=$null){$p=@{Uri="$BaseUrl$path";Headers=$h;TimeoutSec=180};if($null -ne $body){$p.Method='Post';$p.ContentType='application/json; charset=utf-8';$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 25))};Invoke-RestMethod @p}
function Check($ok,$name){if(-not $ok){throw "FAIL: $name"};"PASS: $name"}
$login=Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'}
if(-not $login.data.token){throw 'Login failed'};$h.Authorization="Bearer $($login.data.token)"
$query=@{reportDefinitionId='production_daily_report_analysis_v1';reportVersion=1;startDate='2026-09-07';endDate='2026-09-07';comparisonMode='PREVIOUS_PERIOD'}
$report=Api '/api/analytics/agent-read/reports/run' $query;Save 'H01-comparison' $report
$factory=@($report.data.comparison.metrics|Where-Object { $_.metricLabel -like '全厂产量*数量*' })
Check ($report.code -eq 200 -and $factory.Count -eq 1 -and $factory[0].currentValue -eq 200 -and $factory[0].comparisonValue -eq 100 -and $factory[0].percentChange -eq 100) 'factory comparison 200 vs 100 / 100%'
$history=Api "/api/analytics/agent-read/reports/$($report.data.reportRunId)";Save 'H02-history' $history
Check (($report.data.comparison|ConvertTo-Json -Depth 40 -Compress) -eq ($history.data.comparison|ConvertTo-Json -Depth 40 -Compress)) 'immutable comparison snapshot'
Invoke-WebRequest -Uri "$BaseUrl/api/analytics/agent-read/reports/$($report.data.reportRunId)/export.xlsx" -Headers $h -OutFile (Join-Path $out 'H03-export.xlsx')|Out-Null
if($SkipModel){exit}
$turns=@(
 '比较2026-09-07与上一天的生产日报产量和能耗，全部车间、全部产品',
 '只看多晶车间，继续比较这两天',
 '改成全部车间，只看黄冰糖，仍比较这两天',
 '比较2026-09-08到2026-09-09与2026-09-06到2026-09-07的日报产量，全部车间、全部产品',
 '取消比较，查看2026-09-07的全部车间、全部产品日报趋势'
)
$checks=@()
for($round=1;$round -le $Rounds;$round++){
 $session=Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')};$sid=$session.data.agentSessionId
 if(-not $sid){throw 'Session failed'}
 try{
  for($i=0;$i -lt $turns.Count;$i++){
   $case="R$round-C$($i+1)";$watch=[Diagnostics.Stopwatch]::StartNew()
   $r=Api "/api/agent/sessions/$sid/messages" @{message=$turns[$i]}
   $cards=@($r.data.cards|Where-Object cardType -eq 'production_daily_report_analysis')
   $ok=$r.code -eq 200 -and $cards.Count -eq 1
   if($ok){
    $s=@($cards[0].fields|Where-Object kind -eq 'daily_analysis_summary')[0];$c=$s.comparison
    if($i -eq 4){$ok=$null -eq $c -and $s.dateRangeLabel -eq '2026-09-07'}
    elseif($i -eq 3){$f=@($c.metrics|Where-Object metricLabel -like '全厂产量*数量*');$ok=$c.comparisonMode -eq 'CUSTOM' -and $s.currentPeriodEmpty -and -not $s.isEmpty -and $f.Count -eq 1 -and $null -eq $f[0].currentValue -and $f[0].comparisonValue -eq 300}
    else{
     $ok=$c.currentDateRangeLabel -eq '2026-09-07' -and $c.comparisonDateRangeLabel -eq '2026-09-06'
     if($i -eq 0){$f=@($c.metrics|Where-Object metricLabel -like '全厂产量*数量*');$ok=$ok -and $f.Count -eq 1 -and $f[0].percentChange -eq 100}
     if($i -eq 1){$ok=$ok -and $s.departmentScopeLabel -eq '多晶车间' -and $c.metrics.Count -eq 4 -and @($c.metrics|Where-Object{$null -ne $_.percentChange}).Count -eq 0}
     if($i -eq 2){$ok=$ok -and $c.metrics.Count -eq 2 -and $c.metrics[0].currentValue -eq 297 -and $c.metrics[0].comparisonValue -eq 297 -and $null -eq $c.metrics[0].percentChange}
    }
   }
   Save $case @{Input=$turns[$i];Response=$r;StructuredCheck=$ok;Seconds=$watch.Elapsed.TotalSeconds}
   $checks+=@{Case=$case;Pass=$ok};@{Case=$case;Pass=$ok;Answer=$r.data.answer;Seconds=$watch.Elapsed.TotalSeconds}|ConvertTo-Json -Compress
  }
 }finally{Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $h|Out-Null}
 $session=Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')};$sid=$session.data.agentSessionId
 try{
  $msg='比较2026-09-05与2026-09-04的全部车间日报产量'
  $r=Api "/api/agent/sessions/$sid/messages" @{message=$msg}
  $s=@($r.data.cards|Where-Object cardType -eq 'production_daily_report_analysis'|ForEach-Object{$_.fields}|Where-Object kind -eq 'daily_analysis_summary')
  $ok=$r.code -eq 200 -and $s.Count -eq 1 -and $s[0].isEmpty -and $s[0].comparison.metrics.Count -eq 0
  Save "R$round-C6" @{Input=$msg;Response=$r;StructuredCheck=$ok};$checks+=@{Case="R$round-C6";Pass=$ok}
  @{Case="R$round-C6";Pass=$ok;Answer=$r.data.answer}|ConvertTo-Json -Compress
 }finally{Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $h|Out-Null}
 Save 'summary' $checks
}
Check (@($checks|Where-Object{-not $_.Pass}).Count -eq 0) 'comparison model cases (prose requires separate review)'
