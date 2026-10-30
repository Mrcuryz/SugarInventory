param([string]$BaseUrl='http://127.0.0.1:38083', [switch]$SkipModel, [string]$EvidenceName='daily-report-trend-b2')
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')) { throw 'Local only' }
if($EvidenceName -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Invalid evidence name'}
$out=Join-Path $PSScriptRoot "../output/$EvidenceName"
[IO.Directory]::CreateDirectory($out)|Out-Null
function Save($name,$value){[IO.File]::WriteAllText((Join-Path $out "$name.json"),($value|ConvertTo-Json -Depth 70),[Text.UTF8Encoding]::new($false))}
$h=@{}
function Api($path,$body=$null){$p=@{Uri="$BaseUrl$path";Headers=$h;TimeoutSec=180};if($null -ne $body){$p.Method='Post';$p.ContentType='application/json; charset=utf-8';$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 20))};Invoke-RestMethod @p}
function Check($ok,$label){if(-not $ok){throw "FAIL: $label"};"PASS: $label"}
$login=Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'}
if(-not $login.data.token){throw 'Login failed'};$h.Authorization="Bearer $($login.data.token)"
$query=@{reportDefinitionId='production_daily_report_analysis_v1';reportVersion=1;startDate='2026-09-05';endDate='2026-09-07'}
$r=Api '/api/analytics/agent-read/reports/run' $query;Save 'H01-run' $r
Check ($r.code -eq 200 -and $r.data.productionDailyReport.series.Count -eq 4) 'four independent daily report series'
$factory=@($r.data.productionDailyReport.series|Where-Object summaryType -eq '全厂产量')[0]
Check ($factory.quantity -eq 300 -and $factory.convertedTons -eq 3 -and $null -eq $factory.points[0].quantity) '300 pieces / 3 saved tons and missing date stays null'
$id=$r.data.reportRunId
$history=Api "/api/analytics/agent-read/reports/$id";Save 'H02-snapshot' $history
Check (($r.data.productionDailyReport|ConvertTo-Json -Depth 30 -Compress) -eq ($history.data.productionDailyReport|ConvertTo-Json -Depth 30 -Compress)) 'history snapshot matches original'
Invoke-WebRequest -Uri "$BaseUrl/api/analytics/agent-read/reports/$id/export.xlsx" -Headers $h -OutFile (Join-Path $out 'H03-export.xlsx') | Out-Null
Check ((Get-Item (Join-Path $out 'H03-export.xlsx')).Length -gt 1000) 'export received (cell semantics covered by Java tests)'
$query.productQuery='黄冰糖';$filtered=Api '/api/analytics/agent-read/reports/run' $query;Save 'H04-product' $filtered
Check ($filtered.data.productionDailyReport.series.Count -eq 1 -and $filtered.data.productionDailyReport.series[0].quantity -eq 594) 'product scope 594 pieces, no factory double count'
if($SkipModel){exit}
$checks=@()
$s=Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')};$sid=$s.data.agentSessionId
if(-not $sid){throw 'Session failed'}
try {
 $turns=@('查看2026-09-05到2026-09-07的日报产量和能耗趋势','只看多晶车间','改成全部车间，只看黄冰糖','全部产品都看','查看2026-09-05当天的日报产量趋势')
 for($i=0;$i -lt $turns.Count;$i++){
  $watch=[Diagnostics.Stopwatch]::StartNew();$answer=Api "/api/agent/sessions/$sid/messages" @{message=$turns[$i];pageContext=@{debug=$true}}
  $cards=@($answer.data.cards|Where-Object cardType -eq 'production_daily_report_analysis')
  $ok=$answer.code -eq 200 -and $cards.Count -eq 1 -and $answer.data.answer -notmatch '暂时|超时|未提交行未计入|草稿没有纳入|仅统计已提交'
  if($ok){
   $summary=@($cards[0].fields|Where-Object kind -eq 'daily_analysis_summary')[0]
   $rows=@($cards[0].fields|Where-Object kind -eq 'daily_analysis_series')
   $ok=$summary.dateRangeLabel -eq $(if($i -eq 4){'2026-09-05'}else{'2026-09-05 至 2026-09-07'})
   if($i -eq 0 -or $i -eq 3){$factory=@($rows|Where-Object summaryType -eq '全厂产量');$ok=$ok -and $factory.Count -eq 1 -and $factory[0].quantity -eq 300}
   if($i -eq 1){$ok=$ok -and $summary.departmentScopeLabel -eq '多晶车间' -and @($rows|Where-Object departmentName -ne '多晶车间').Count -eq 0}
   if($i -eq 2){$ok=$ok -and $rows.Count -eq 1 -and $rows[0].quantity -eq 594}
   if($i -eq 4){$ok=$ok -and $summary.isEmpty -and $rows.Count -eq 0}
  }
  $case='M'+($i+1).ToString('00')
  $e=@{Input=$turns[$i];Response=$answer;Seconds=$watch.Elapsed.TotalSeconds;StructuredCheck=$ok};Save $case $e
  $checks+=@{Case=$case;Pass=$ok};@{Case=$case;Answer=$answer.data.answer;StructuredCheck=$ok;Seconds=$watch.Elapsed.TotalSeconds}|ConvertTo-Json -Compress
 }
} finally { Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $h|Out-Null }
Save 'summary' $checks
Check (@($checks|Where-Object Pass -eq $false).Count -eq 0) 'all model turns matched structured facts; prose still requires review'
