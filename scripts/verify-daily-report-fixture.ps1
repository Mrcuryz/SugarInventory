param([ValidateSet('Prepare','Verify','Cleanup')][string]$Mode='Verify',
 [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
 [string]$BaseUrl='http://127.0.0.1:38082', [string]$EvidencePrefix='',
 [ValidateSet('All','F01','F02','F03','F04')][string]$CaseFilter='All')
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local only'}
$marker='UAT_DAILY_20260908'
$cfg=@{};foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?]+)'){throw 'Local DB only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$dbName=$Matches[3]
function Sql($query){
 $env:MYSQL_PWD=$cfg.DB_PASSWORD
 try{$rows=@($query | & 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$dbName --default-character-set=utf8mb4 --batch --skip-column-names);if($LASTEXITCODE){throw 'SQL failed'};return ,$rows}
 finally{Remove-Item Env:MYSQL_PWD}
}
$out=Join-Path $PSScriptRoot '../output/daily-report-fixture';[IO.Directory]::CreateDirectory($out)|Out-Null
function Save($name,$value){if($EvidencePrefix -notmatch '^[a-zA-Z0-9_-]*$'){throw 'Invalid prefix'};[IO.File]::WriteAllText((Join-Path $out "$EvidencePrefix$name.json"),($value|ConvertTo-Json -Depth 60),[Text.UTF8Encoding]::new($false))}
if($Mode -eq 'Prepare'){
 if([int](Sql "SELECT COUNT(*) FROM production_daily_report WHERE report_date BETWEEN '2026-09-05' AND '2026-09-07';")[0] -ne 0){throw 'Dates occupied; refuse overwrite'}
 $products=Sql "SELECT id FROM product WHERE status='成品' ORDER BY id LIMIT 1;"
 if(-not $products){throw 'No existing finished product'}
 $pidValue=[int]$products[0]
 $sql=[Collections.Generic.List[string]]::new();$sql.Add('START TRANSACTION;')
 foreach($day in 5..7){
  $date='2026-09-{0:D2}' -f $day;$state=if($day -eq 6){'SUBMITTED'}else{'DRAFT'}
  $sql.Add("INSERT INTO production_daily_report(report_date,prepared_by_name,status,created_by_name,created_at) VALUES('$date','$marker','$state','$marker',NOW()); SET @r=LAST_INSERT_ID();")
  if($day -eq 5){continue}
  foreach($dept in @(@('POLYCRYSTAL','多晶车间'),@('BROWN_SUGAR','红糖车间'),@('MONOCRYSTAL','单晶车间'),@('FACTORY_SUMMARY','全厂汇总'))){
   $s=if($day -eq 6 -or $dept[0] -eq 'POLYCRYSTAL'){'SUBMITTED'}else{'DRAFT'}
   $sql.Add("INSERT INTO production_daily_report_section(report_id,department_code,department_name_snapshot,status,updated_by_name) VALUES(@r,'$($dept[0])','$($dept[1])','$s','$marker');")
   if($dept[0] -eq 'POLYCRYSTAL'){$sql.Add('SET @s=LAST_INSERT_ID();')}
  }
  foreach($n in 1..24){$value=if($n -eq 1){'0'}elseif($n -eq 2){'NULL'}else{"$n"};$sql.Add("INSERT INTO production_daily_product_line(report_section_id,category_code,category_name_snapshot,product_id,product_name_snapshot,product_status_snapshot,product_type_snapshot,unit,daily_actual,converted_tons,month_quantity,month_tons,year_tons,remark,display_order,created_at) SELECT @s,'DIRECT_PRODUCT','直接成品',id,product_name,status,product_type,'件',$value,NULL,123,4.5,8.5,'$marker','$n',NOW() FROM product WHERE id=$pidValue;")}
  $sql.Add("INSERT INTO production_daily_metric_value(report_section_id,metric_code,metric_name_snapshot,group_code,group_name_snapshot,unit_snapshot,daily_actual,month_tons,year_tons,remark,display_order) VALUES(@s,'POLY_CRYSTALLIZATION_RATE','冰糖结晶率','COMPREHENSIVE','综合指标','%',83.5,86,87,'$marker',30),(@s,'POLY_POWER_USAGE','冰糖总电耗','COMPREHENSIVE','综合指标','Kw.h',120,NULL,NULL,'$marker',31);")
 }
 $sql.Add('COMMIT;');Sql ($sql -join "`n")|Out-Null
 $ids=Sql "SELECT id FROM production_daily_report WHERE prepared_by_name='$marker' ORDER BY id;";Save 'manifest' @{Marker=$marker;ReportIds=$ids;ProductId=$pidValue;Dates=@('2026-09-05','2026-09-06','2026-09-07')}
 "Prepared 3 marked reports, 48 product rows, 4 metrics"
 exit
}
if($Mode -eq 'Cleanup'){
 $manifest=Get-Content -Raw (Join-Path $out 'manifest.json')|ConvertFrom-Json
 $ids=($manifest.ReportIds|ForEach-Object{[long]$_}) -join ','
 if($manifest.Marker -ne $marker -or $manifest.ReportIds.Count -ne 3){throw 'Invalid manifest'}
 if([int](Sql "SELECT COUNT(*) FROM production_daily_report WHERE id IN ($ids) AND prepared_by_name='$marker' AND created_by_name='$marker';")[0] -ne 3){throw 'Marker mismatch; refuse cleanup'}
 $foreign=Sql "SELECT (SELECT COUNT(*) FROM production_daily_report_section WHERE report_id IN ($ids) AND (updated_by_name IS NULL OR updated_by_name<>'$marker')) + (SELECT COUNT(*) FROM production_daily_product_line v JOIN production_daily_report_section s ON s.id=v.report_section_id WHERE s.report_id IN ($ids) AND (v.remark IS NULL OR v.remark<>'$marker')) + (SELECT COUNT(*) FROM production_daily_metric_value v JOIN production_daily_report_section s ON s.id=v.report_section_id WHERE s.report_id IN ($ids) AND (v.remark IS NULL OR v.remark<>'$marker'));"
 if([int]$foreign[0]){throw 'Foreign changes found; refuse cleanup'}
 Sql "START TRANSACTION; DELETE v FROM production_daily_metric_value v JOIN production_daily_report_section s ON s.id=v.report_section_id WHERE s.report_id IN ($ids) AND v.remark='$marker'; DELETE v FROM production_daily_product_line v JOIN production_daily_report_section s ON s.id=v.report_section_id WHERE s.report_id IN ($ids) AND v.remark='$marker'; DELETE FROM production_daily_report_section WHERE report_id IN ($ids) AND updated_by_name='$marker'; DELETE FROM production_daily_report WHERE id IN ($ids) AND prepared_by_name='$marker'; COMMIT;"|Out-Null
 $left=Sql "SELECT (SELECT COUNT(*) FROM production_daily_report WHERE id IN ($ids)), (SELECT COUNT(*) FROM production_daily_report_section WHERE report_id IN ($ids)), (SELECT COUNT(*) FROM production_daily_product_line WHERE remark='$marker'), (SELECT COUNT(*) FROM production_daily_metric_value WHERE remark='$marker');"
 $counts=$left[0] -split "`t";Save 'cleanup' @{RemainingReports=[int]$counts[0];RemainingSections=[int]$counts[1];RemainingProductRows=[int]$counts[2];RemainingMetrics=[int]$counts[3]};if(($counts|ForEach-Object{[int]$_}|Measure-Object -Sum).Sum){throw 'Cleanup incomplete'};'Cleanup verified';exit
}
$h=@{}
function Api($path,$body=$null){$p=@{Uri="$BaseUrl$path";Headers=$h;TimeoutSec=180};if($null -ne $body){$p.Method='Post';$p.ContentType='application/json; charset=utf-8';$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 20))};Invoke-RestMethod @p}
$login=Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'};if(-not $login.data.token){throw 'Login failed'};$h.Authorization="Bearer $($login.data.token)"
function Check($ok,$label){if(-not $ok){throw "FAIL: $label"};"PASS: $label"}
$all=@();foreach($page in 1..3){$r=Api '/api/production/agent-read/daily-reports/query' @{startDate='2026-09-06';endDate='2026-09-07';page=$page;size=20};Save "page-$page" $r;Check ($r.code -eq 200 -and $r.data.total -eq 52) "page $page total";$all+=@($r.data.records);Check ($r.data.records.Count -eq $(if($page -eq 3){12}else{20})) "page $page size"}
Check ($all.Count -eq 52) 'all pages'
$web=Api '/api/production/daily-reports/2026-09-07';Save 'web-report' $web
$products=@($web.data.sections|ForEach-Object{$_.groups}|ForEach-Object{$_.productLines}|Where-Object{$_.remark -eq $marker})
Check ($products.Count -eq 24) 'Web saved product rows'
$agent=@($all|Where-Object{$_.reportDate -eq '2026-09-07' -and $_.itemKindLabel -eq '产品'})
for($i=0;$i -lt 24;$i++){foreach($key in @('dailyActual','convertedTons','monthQuantity','monthTons','yearTons','unit')){Check ($products[$i].$key -eq $agent[$i].$key) "Web/Agent row $i $key"}}
foreach($case in @(@('header','2026-09-05',$null),@('no-match','2026-09-07','不存在的验收产品'),@('metric','2026-09-07','结晶率'))){$q=@{startDate=$case[1];endDate=$case[1]};if($case[2]){$q.itemQuery=$case[2]};$r=Api '/api/production/agent-read/daily-reports/query' $q;Save $case[0] $r;Check (-not $r.data.isEmpty) "$($case[0]) header exists";Check ($r.data.total -eq $(if($case[0] -eq 'metric'){1}else{0})) "$($case[0]) total"}
$cases=@(@('F01',@('查看2026-09-06至2026-09-07的生产日报，每页20条','下一页','下一页')),@('F02',@('查看2026-09-07多晶车间的冰糖结晶率日报填报值，包括月累计和年累计')),@('F03',@('查看2026-09-05的生产日报')),@('F04',@('查询2026-09-07的生产日报，只看名称包含“验收专用不存在产品XYZ”的填报明细')))
$checks=@()
foreach($c in $cases){
 if($CaseFilter -ne 'All' -and $c[0] -ne $CaseFilter){continue}
 $s=Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')};$sid=$s.data.agentSessionId;if(-not $sid){throw 'Session failed'}
 try{
  $i=0
  foreach($msg in $c[1]){
   $i++;$w=[Diagnostics.Stopwatch]::StartNew();$r=Api "/api/agent/sessions/$sid/messages" @{message=$msg}
   $summary=@($r.data.cards|Where-Object{$_.cardType -eq 'production_daily_records'}|ForEach-Object{$_.fields}|Where-Object{$_.kind -eq 'daily_records_summary'})
   $ok=$r.code -eq 200 -and $summary.Count -eq 1 -and $r.data.answer -notmatch '服务暂不可用'
   if($ok){
    $expectedTotal=if($c[0] -eq 'F01'){52}elseif($c[0] -eq 'F02'){1}else{0}
    $expectedCount=if($c[0] -eq 'F01'){if($i -eq 3){12}else{20}}elseif($c[0] -eq 'F02'){1}else{0}
    $ok=$summary[0].total -eq $expectedTotal -and $summary[0].returnedRecordCount -eq $expectedCount -and -not $summary[0].isEmpty
    if($c[0] -eq 'F01'){$ok=$ok -and $summary[0].page -eq $i -and $summary[0].hasMore -eq ($i -lt 3)}
   }
   $e=@{Input=$msg;Answer=$r.data.answer;Cards=$r.data.cards;Code=$r.code;Seconds=$w.Elapsed.TotalSeconds;StructuredCheck=$ok};Save "$($c[0])-$i" $e
   $checks+=@{Case="$($c[0])-$i";Pass=$ok};@{Case="$($c[0])-$i";Answer=$e.Answer;StructuredCheck=$ok}|ConvertTo-Json -Compress
  }
 }finally{Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $h|Out-Null}
}
Save 'model-checks' $checks
if(@($checks|Where-Object{-not $_.Pass}).Count){throw 'Real-model acceptance failed; inspect evidence. HTTP 200 alone is not a pass.'}
