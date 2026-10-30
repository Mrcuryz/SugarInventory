param([string]$BaseUrl='http://127.0.0.1:38083', [ValidateRange(1,3)][int]$Rounds=1,
 [ValidateSet('fresh','followup','boundary')][string]$Scenario='fresh', [string]$EvidencePrefix='multi-')
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local only'}
if($EvidencePrefix -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Invalid evidence prefix'}
$out=Join-Path $PSScriptRoot '../output/daily-report-multi-query'
[IO.Directory]::CreateDirectory($out)|Out-Null
$h=@{}
function Api($path,$body){Invoke-RestMethod -Method Post -Uri "$BaseUrl$path" -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 15))) -TimeoutSec 180}
function Save($name,$value){[IO.File]::WriteAllText((Join-Path $out "$EvidencePrefix$name.json"),($value|ConvertTo-Json -Depth 60),[Text.UTF8Encoding]::new($false))}
$login=Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'}
if(-not $login.data.token){throw 'Login failed'};$h.Authorization="Bearer $($login.data.token)"
$queries=@('2026-09-05到2026-09-07，全部车间的耗电和结晶率如何？')
if($Scenario -eq 'followup'){$queries=@('查看2026-09-05到2026-09-07的生产概况，每页20条','下一页','只看黄冰糖','不限制产品，看看这三天的耗电和结晶率','下一页')}
if($Scenario -eq 'boundary'){$queries=@('2026-09-05到2026-09-07，全厂耗水和耗电分别多少？','2026-09-05当天，全部车间的耗电和结晶率分别多少？','2026-09-05到2026-09-07，查看全部车间的耗电和结晶率，每页1条','结晶率下一页')}
$checks=@()
for($r=1;$r -le $Rounds;$r++){
 $s=Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')};$sid=$s.data.agentSessionId
 if(-not $sid){throw 'Session failed'}
 try{
  for($i=0;$i -lt $queries.Count;$i++){
   $watch=[Diagnostics.Stopwatch]::StartNew();$reply=Api "/api/agent/sessions/$sid/messages" @{message=$queries[$i];pageContext=@{debug=$true}};$watch.Stop()
   $cards=@($reply.data.cards|Where-Object cardType -eq 'production_daily_records')
   $ok=$reply.code -eq 200 -and $reply.data.answer -notmatch '超时|服务暂不可用|暂时没能完成'
   if($Scenario -eq 'boundary'){
    $summaries=@($cards.fields|Where-Object kind -eq 'daily_records_summary')
    $rows=@($cards.fields|Where-Object kind -eq 'daily_records_row')
    $energy=@($cards.fields|Where-Object {$_.kind -eq 'daily_records_overview' -and $_.label -eq '总耗电量'})
    if($i -eq 0){
     # isEmpty 描述整份日报是否存在；已有日报而指标无匹配时应检查 total=0。
     $ok=$ok -and $cards.Count -eq 2 -and @($summaries|Where-Object total -eq 0).Count -eq 1
     $ok=$ok -and $energy.Count -eq 1 -and $energy[0].quantity -eq 300 -and $reply.data.answer -match '300'
    }elseif($i -eq 1){
     $ok=$ok -and $cards.Count -eq 2 -and @($summaries|Where-Object total -eq 0).Count -eq 2 -and $rows.Count -eq 0
     $ok=$ok -and @($summaries|Where-Object dateRangeLabel -ne '2026-09-05').Count -eq 0
    }elseif($i -eq 2){
     $ok=$ok -and $cards.Count -eq 2 -and $rows.Count -eq 2
     $ok=$ok -and @($summaries|Where-Object {$_.page -eq 1 -and $_.size -eq 1 -and $_.hasMore}).Count -eq 2
     $ok=$ok -and $energy.Count -eq 1 -and $energy[0].quantity -eq 300
    }else{
     $ok=$ok -and $cards.Count -eq 1 -and $summaries[0].page -eq 2 -and $summaries[0].size -eq 1
     $ok=$ok -and $rows.Count -eq 1 -and $rows[0].itemName -like '*结晶率*' -and $rows[0].dailyActual -eq 83.5
    }
   }elseif($Scenario -eq 'fresh' -or $i -eq 3){
    $rows=@($cards.fields|Where-Object kind -eq 'daily_records_row')
    $summaries=@($cards.fields|Where-Object kind -eq 'daily_records_summary')
    $energy=@($cards.fields|Where-Object {$_.kind -eq 'daily_records_overview' -and $_.label -eq '总耗电量'})
    $rate=@($rows|Where-Object {$_.itemName -like '*结晶率*' -and $_.dailyActual -eq 83.5})
    $ok=$ok -and $cards.Count -eq 2 -and $energy.Count -eq 1 -and $energy[0].quantity -eq 300 -and $rate.Count -eq 2
    $ok=$ok -and @($summaries|Where-Object dateRangeLabel -ne '2026-09-05 至 2026-09-07').Count -eq 0
    $ok=$ok -and $reply.data.answer -match '83[.]5' -and $reply.data.answer -match '300'
   }elseif($i -eq 4){
    # Java 的 needsUserSelection 只表示带候选按钮；自由文本澄清没有 options，合法值为 false。
    $ok=$ok -and $cards.Count -eq 0 -and @($reply.data.toolCalls).Count -eq 0
    $ok=$ok -and $reply.data.answer -match '结晶率' -and $reply.data.answer -match '电'
    $ok=$ok -and $reply.data.answer -match '哪|选择|明确|补充'
   }
   else{
    $summary=@($cards.fields|Where-Object kind -eq 'daily_records_summary')
    $overview=@($cards.fields|Where-Object kind -eq 'daily_records_overview')
    $expectedPage=if($i -eq 1){2}else{1}
    $expectedTotal=if($i -eq 2){48}else{58}
    $ok=$ok -and $cards.Count -eq 1 -and $summary.Count -eq 1
    $ok=$ok -and $summary[0].page -eq $expectedPage -and $summary[0].total -eq $expectedTotal
    $ok=$ok -and $summary[0].size -eq 20
    $ok=$ok -and $summary[0].dateRangeLabel -eq '2026-09-05 至 2026-09-07'
    if($i -eq 2){
     $ok=$ok -and $overview.Count -eq 1 -and $overview[0].quantity -eq 594
     $ok=$ok -and $reply.data.answer -match '594'
    }else{
     $factory=@($overview|Where-Object summaryType -eq '全厂产量')
     $ok=$ok -and $factory.Count -eq 1 -and $factory[0].quantity -eq 300 -and $factory[0].convertedTons -eq 3
    }
    if($i -eq 1){
     $ok=$ok -and $reply.data.answer -match '第\s*[2二]\s*页'
     $ok=$ok -and $reply.data.answer -notmatch '部分完成|没有取到|无法给出下一页|请再发一次'
    }
   }
   $case="$Scenario-R$r-T$($i+1)"
   $performance=@{}
   foreach($property in $reply.data.debug.PSObject.Properties){
    if($property.Name -match '^(totalDurationMs|mainRoute(Ms|DecisionCount|InputBytes)|expertInitial(Ms|DecisionCount|InputBytes)|expertResultAnalysis(Ms|DecisionCount|InputBytes)|tool(DurationMs|CallCount)|modelDecisionCount)$'){
     $n=0L;if([long]::TryParse([string]$property.Value,[ref]$n)){$performance[$property.Name]=$n}
    }
   }
   $e=@{Case=$case;Input=$queries[$i];Answer=$reply.data.answer;Cards=$reply.data.cards;ToolCalls=$reply.data.toolCalls;Performance=$performance;Seconds=[Math]::Round($watch.Elapsed.TotalSeconds,2);NeedsSelection=$reply.data.needsUserSelection;Check=$ok}
   Save $case $e;$checks+=@{Case=$case;Pass=$ok}
   @{Case=$case;Answer=$e.Answer;Seconds=$e.Seconds;Check=$ok}|ConvertTo-Json -Compress
   if(-not $ok){
    # 后续问题依赖本轮上下文，前置失败后不能继续产生伪有效追问证据。
    Save 'checks' $checks
    throw "Acceptance stopped at $case; retain evidence and clean fixture"
   }
  }
 }finally{Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $h -TimeoutSec 15|Out-Null}
}
Save 'checks' $checks
if(@($checks|Where-Object{-not $_.Pass}).Count){throw 'Multi-query acceptance failed; retain evidence and clean fixture'}
