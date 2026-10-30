param(
    [string]$BaseUrl = 'http://127.0.0.1:38083',
    [ValidateRange(1,3)][int]$Rounds = 3,
    [ValidateSet('All','Direct')][string]$Cases = 'All',
    [string]$EvidenceName = 'daily-operations-c3-live'
)
$ErrorActionPreference = 'Stop'
if (([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')) { throw 'Local acceptance only' }
if ($EvidenceName -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid evidence directory' }
$out = Join-Path $PSScriptRoot "../output/$EvidenceName"
[IO.Directory]::CreateDirectory($out) | Out-Null
$headers = @{}
function Save($name, $value) {
    [IO.File]::WriteAllText((Join-Path $out "$name.json"), ($value | ConvertTo-Json -Depth 90), [Text.UTF8Encoding]::new($false))
}
function Api($path, $body = $null) {
    $p = @{Uri="$BaseUrl$path"; Headers=$headers; TimeoutSec=180}
    if ($null -ne $body) {
        $p.Method='Post'; $p.ContentType='application/json; charset=utf-8'
        $p.Body=[Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 20))
    }
    Invoke-RestMethod @p
}
$login = Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'}
if (-not $login.data.token) { throw 'Login failed' }
$headers.Authorization="Bearer $($login.data.token)"
$day=[DateTimeOffset]::Now.ToOffset([TimeSpan]::FromHours(8)).ToString('yyyy-MM-dd')
$baseline=Api '/api/production/agent-read/daily-reports/query' @{startDate=$day;endDate=$day;page=1;size=20}
$legacy=Api '/api/analytics/agent-read/reports/run' @{reportDefinitionId='today_operations_overview_v1';reportVersion=1;startDate=$day;endDate=$day}
if ($baseline.code -ne 200 -or $legacy.code -ne 200) { throw 'Baseline queries failed' }
Save 'baseline' @{Daily=$baseline.data;Legacy=$legacy.data;BusinessDate=$day}
$checks=[Collections.Generic.List[object]]::new()
function CheckTurn($sid, $id, $inputText, $kind) {
    if ($kind -notin @('daily','combined','legacy','restricted','export')) { throw 'Invalid test kind; no request sent' }
    $watch=[Diagnostics.Stopwatch]::StartNew()
    $r=Api "/api/agent/sessions/$sid/messages" @{message=$inputText;pageContext=@{debug=$true}}
    $cards=@($r.data.cards)
    $daily=@($cards | Where-Object cardType -eq 'production_daily_records')
    $business=@($cards | Where-Object cardType -eq 'daily_operations_business_facts')
    $old=@($cards | Where-Object cardType -eq 'today_operations_overview_report')
    $ok=$r.code -eq 200 -and -not [string]::IsNullOrWhiteSpace($r.data.answer) -and $r.data.answer -notmatch '暂时无法完成|暂时未能整理|稍后重试|TOOL_ERROR|PERMISSION_DENIED'
    if ($kind -in @('daily','combined')) {
        $ok=$ok -and $daily.Count -eq 1
        if ($daily.Count -eq 1) {
            $summary=@($daily[0].fields | Where-Object kind -eq 'daily_records_summary')[0]
            $values=@($daily[0].fields | Where-Object kind -eq 'daily_records_overview')
            $ok=$ok -and $summary.dateRangeLabel -eq $day -and $summary.isEmpty -eq $baseline.data.isEmpty
            foreach ($expected in $baseline.data.overview) {
                $actual=@($values | Where-Object {$_.summaryType -eq $expected.summaryType -and $_.label -eq $expected.label -and $_.departmentName -eq $expected.departmentName})
                $ok=$ok -and $actual.Count -eq 1 -and $actual[0].quantity -eq $expected.quantity -and $actual[0].convertedTons -eq $expected.convertedTons
            }
        }
    }
    if ($kind -eq 'combined') {
        $ok=$ok -and $business.Count -eq 1 -and $old.Count -eq 0
        $ok=$ok -and $r.data.debug.recipeSummaryMode -eq 'MODEL_ANALYSIS'
        $ok=$ok -and (($cards | ConvertTo-Json -Depth 30 -Compress) -notmatch 'reportRunId|productionOutput|productionFlow')
        if ($business.Count -eq 1) {
            $quality=@($business[0].fields | Where-Object label -eq '今日化验')[0].value
            $pending=@($business[0].fields | Where-Object label -eq '当前待处理')[0].value
            $ok=$ok -and $quality.StartsWith("$($legacy.data.operationsOverview.assayQuality.assayRecordCount) 条")
            $ok=$ok -and $pending -eq "$($legacy.data.operationsOverview.currentPendingTaskCount) 条"
        }
    } elseif ($kind -eq 'legacy') {
        $ok=$ok -and $old.Count -eq 1 -and $daily.Count -eq 0
        $qrCount=$legacy.data.operationsOverview.currentInventory.palletCount
        $ok=$ok -and $r.data.answer -notmatch "${qrCount}\s*整板"
    } elseif ($kind -in @('restricted','export')) {
        $ok=$ok -and $business.Count -eq 0 -and $old.Count -eq 0
    }
    $e=@{Input=$inputText;Response=$r;StructuredCheck=$ok;Seconds=$watch.Elapsed.TotalSeconds;Kind=$kind}
    Save $id $e
    $checks.Add(@{Case=$id;Pass=$ok;Seconds=$e.Seconds})
    Save 'summary' $checks
    @{Case=$id;Pass=$ok;Answer=$r.data.answer;Seconds=$e.Seconds}|ConvertTo-Json -Compress
}
for ($round=1; $round -le $Rounds; $round++) {
    $groups=@(@{Turns=@(@{Id='O02';Input='今天整体运营情况如何？';Kind='combined'})})
    if ($Cases -eq 'All') {
        $groups=@(
            @{Turns=@(@{Id='O01';Input='今天产量如何？';Kind='daily'},@{Id='O01-followup';Input='今天整体运营情况如何？';Kind='combined'})},
            @{Turns=@(@{Id='O02';Input='今天整体运营情况如何？';Kind='combined'},@{Id='O02-export';Input='把刚才这份整体运营结果导出';Kind='export'})},
            @{Turns=@(@{Id='O03';Input='昨天整体运营情况如何？';Kind='restricted'})},
            @{Turns=@(@{Id='O04';Input='今天黄冰糖的整体运营情况如何？只看黄冰糖，不看其他产品';Kind='restricted'})},
            @{Turns=@(@{Id='O05';Input='按原有生产订单登记口径，查询今天的运营概览报表，不使用生产日报';Kind='legacy'})}
        )
    }
    foreach ($group in $groups) {
        $session=Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')}
        $sid=$session.data.agentSessionId
        if (-not $sid) { throw 'Session creation failed' }
        try { foreach ($turn in $group.Turns) { CheckTurn $sid "R$round-$($turn.Id)" $turn.Input $turn.Kind } }
        finally { Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $headers | Out-Null }
    }
}
if (@($checks | Where-Object {-not $_.Pass}).Count) { throw 'Structured acceptance failed; inspect saved evidence. Prose still needs review.' }
