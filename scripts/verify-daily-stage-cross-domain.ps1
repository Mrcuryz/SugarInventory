param(
    [string]$BaseUrl = 'http://127.0.0.1:38083',
    [ValidateRange(1,3)][int]$Rounds = 3,
    [string]$EvidenceName = 'daily-stage-cross-domain'
)
$ErrorActionPreference = 'Stop'
if (([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')) { throw 'Local acceptance only' }
if ($EvidenceName -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid evidence directory' }
$out = Join-Path $PSScriptRoot "../output/$EvidenceName"
[IO.Directory]::CreateDirectory($out) | Out-Null
$headers = @{}
function Save($name,$value) {
    [IO.File]::WriteAllText((Join-Path $out "$name.json"),($value | ConvertTo-Json -Depth 90),[Text.UTF8Encoding]::new($false))
}
function Api($path,$body) {
    $p = @{Uri="$BaseUrl$path";Headers=$headers;TimeoutSec=180;Method='Post';ContentType='application/json; charset=utf-8'}
    $p.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 20))
    Invoke-RestMethod @p
}
$login = Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'}
if (-not $login.data.token) { throw 'Login failed' }
$headers.Authorization = "Bearer $($login.data.token)"
$turns = @(
    @{Id='X01';Input='查看2026-09-05到2026-09-07的日报产量趋势';Kind='daily'},
    @{Id='X02';Input='现在不看生产，查3号库位当前库存';Kind='inventory'},
    @{Id='X03';Input='改看最近7天全部产品的化验质量趋势，不限制库位';Kind='quality'},
    @{Id='X04';Input='成品入库操作流程是什么？只讲系统操作，不查询库存也不执行入库';Kind='software-help'},
    @{Id='X04b';Input='不问系统了，白砂糖分装工艺的流程是什么？';Kind='knowledge'},
    @{Id='X05';Input='3号库位入库2板黄冰糖';Kind='inbound'},
    @{Id='X06';Input='先不办理入库，改查2026-09-06到2026-09-07全部车间全部产品的日报产量';Kind='daily-records'}
)
$checks = [Collections.Generic.List[object]]::new()
for ($round=1; $round -le $Rounds; $round++) {
    $s = Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')}
    $sid = $s.data.agentSessionId
    if (-not $sid) { throw 'Session creation failed' }
    try {
        foreach ($turn in $turns) {
            $watch = [Diagnostics.Stopwatch]::StartNew()
            $r = Api "/api/agent/sessions/$sid/messages" @{message=$turn.Input;pageContext=@{debug=$true}}
            $types = @($r.data.cards | ForEach-Object cardType)
            $ok = $r.code -eq 200 -and -not [string]::IsNullOrWhiteSpace($r.data.answer)
            $ok = $ok -and $r.data.answer -notmatch '暂时无法完成|暂时没能完成|暂时未能整理|稍后重试|TOOL_ERROR|PERMISSION_DENIED|未提交行未计入|草稿没有纳入|仅统计已提交'
            if ($turn.Kind -in @('daily','daily-records')) {
                $expectedCard = if ($turn.Kind -eq 'daily') {'production_daily_report_analysis'} else {'production_daily_records'}
                $expectedKind = if ($turn.Kind -eq 'daily') {'daily_analysis_series'} else {'daily_records_overview'}
                $factory = @($r.data.cards | Where-Object cardType -eq $expectedCard | ForEach-Object fields | Where-Object { $_.kind -eq $expectedKind -and $_.summaryType -eq '全厂产量' })
                $ok = $ok -and $types -contains $expectedCard -and $factory.Count -eq 1 -and $factory[0].quantity -eq 300 -and $factory[0].convertedTons -eq 3
            }
            else {
                $ok = $ok -and $types -notcontains 'production_daily_records' -and $types -notcontains 'production_daily_report_analysis'
                if ($turn.Kind -eq 'quality') {
                    $ok = $ok -and $types -contains 'quality_assay_trend_report'
                    # Fixture has a complete seven-day zero series; model sampling is not missing data.
                    $ok = $ok -and $r.data.answer -notmatch '少一行|缺少一天|缺少一行|序列不完整'
                }
                if ($turn.Kind -eq 'inventory') { $ok = $ok -and $types -contains 'inventory_distribution' }
                if ($turn.Kind -eq 'knowledge') { $ok = $ok -and $types -contains 'knowledge_evidence' -and $r.data.answer -match '白砂糖分装工艺' }
                if ($turn.Kind -eq 'software-help') {
                    $ok = $ok -and $types -notcontains 'knowledge_evidence' -and $r.data.answer -notmatch '白砂糖分装|称量、装袋|金属控制'
                }
                if ($turn.Kind -eq 'inbound') {
                    # 本轮禁止确认、提交；只验意图切换及补充信息，不代替写入验收。
                    $ok = $ok -and $r.data.answer -notmatch '已完成入库|入库成功|已成功入库|已创建入库任务'
                    $ok = $ok -and $types -contains 'candidate_selection'
                }
            }
            $id = "R$round-$($turn.Id)"
            $e = @{Input=$turn.Input;Response=$r;StructuredCheck=$ok;Seconds=$watch.Elapsed.TotalSeconds;Kind=$turn.Kind}
            Save $id $e
            $checks.Add(@{Case=$id;Pass=$ok;Seconds=$e.Seconds;CardTypes=$types})
            Save 'summary' $checks
            @{Case=$id;Pass=$ok;Answer=$r.data.answer;Cards=$types;Seconds=$e.Seconds} | ConvertTo-Json -Compress
        }
    } finally { Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $headers | Out-Null }
}
if (@($checks | Where-Object {-not $_.Pass}).Count) { throw 'Cross-domain structure failed; inspect evidence and review all answers.' }
