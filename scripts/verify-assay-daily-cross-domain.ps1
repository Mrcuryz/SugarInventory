param(
    [string]$BaseUrl='http://127.0.0.1:38083',
    [ValidateRange(1,3)][int]$Rounds=3,
    [string]$EvidenceName='assay-daily-cross-domain-20260925'
)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local acceptance only'}
if($EvidenceName -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid evidence directory'}
$out=Join-Path $PSScriptRoot "../output/$EvidenceName"
[IO.Directory]::CreateDirectory($out)|Out-Null
$headers=@{}
function Save($name,$value){
    $json=$value|ConvertTo-Json -Depth 90
    $json=[regex]::Replace($json,'("(?:token|resumeToken|productRef|groupRef|previewRef|executionToken|idempotencyKey)"\s*:\s*)"[^"]*"','$1"[redacted]"')
    [IO.File]::WriteAllText((Join-Path $out "$name.json"),$json,[Text.UTF8Encoding]::new($false))
}
function Api($path,$body=$null,$method='Post'){
    $p=@{Uri="$BaseUrl$path";Headers=$headers;Method=$method;TimeoutSec=180}
    if($null -ne $body){$p.ContentType='application/json; charset=utf-8';$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 30))}
    $r=Invoke-RestMethod @p
    if($r.code -ne 200){throw "Business request failed: $path / $($r.code)"}
    return $r
}
$login=Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'}
if(-not $login.data.token){throw 'Login failed'}
$headers.Authorization="Bearer $($login.data.token)"
$daily=Api '/api/production/agent-read/daily-reports/query' @{startDate='2026-09-19';endDate='2026-09-25';size=20}
Save 'baseline-daily' $daily
if(-not $daily.data.isEmpty -or $daily.data.total -ne 0 -or @($daily.data.overview).Count){throw 'This corpus requires verified empty daily source; do not compare against obsolete fixture numbers'}
$fixture=Get-Content -Raw -Encoding utf8 (Join-Path $PSScriptRoot '../output/assay-group-live-20260924/fixture.json')|ConvertFrom-Json
if($fixture.Cleaned -or $fixture.Marker -notmatch '^UAT_AGLIVE_[a-f0-9]{32}$'){throw 'Assay fixture unavailable'}
$before=@{}
foreach($id in $fixture.ProductIds){$before["$id"]=(Api "/api/assay/by-product-date?productId=$id&productionDate=2026-09-25" $null 'Get').data}
Save 'baseline-assay' $before
$name=$fixture.ProductNames[1]
$turns=@(
    @{Id='X01';Input='查看2026-09-19到2026-09-25的日报产量趋势';Kind='daily-empty'},
    @{Id='X02';Input="先不看日报，查询${name}在2026-09-25的化验记录，只查询已有记录，不录入。";Kind='assay-read'},
    @{Id='X03';Input=('录入单个产品，产品全名是“{0}”，采样日期2026-09-25，色值22，pH值0，其他未检测，先给我预览，不要保存。' -f $name);Kind='assay-preview'},
    @{Id='X04';Input='先不录入化验，改查2026-09-19到2026-09-25全部车间全部产品的日报产量';Kind='daily-records-empty'},
    @{Id='X05';Input='现在不看生产，查3号库位当前库存';Kind='inventory'},
    @{Id='X06';Input='改看最近7天全部产品的化验质量趋势，不限制库位';Kind='quality'},
    @{Id='X07';Input='成品入库操作流程是什么？只讲系统操作，不查询库存也不执行入库';Kind='software-help'},
    @{Id='X08';Input='不问系统了，白砂糖分装工艺的流程是什么？';Kind='knowledge'}
)
$checks=[Collections.Generic.List[object]]::new()
for($round=1;$round -le $Rounds;$round++){
    $sid=(Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')}).data.agentSessionId
    if(-not $sid){throw 'Session missing'}
    try {
        foreach($turn in $turns){
            $watch=[Diagnostics.Stopwatch]::StartNew()
            $r=Api "/api/agent/sessions/$sid/messages" @{message=$turn.Input;pageContext=@{debug=$true}}
            $types=@($r.data.cards|ForEach-Object cardType)
            $ok=-not [string]::IsNullOrWhiteSpace($r.data.answer) -and $r.data.answer -notmatch '暂时无法完成|暂时没能完成|暂时未能整理|稍后重试|TOOL_ERROR|PERMISSION_DENIED|未提交行未计入|草稿没有纳入|仅统计已提交'
            switch($turn.Kind){
                'daily-empty' {
                    $ok=$ok -and $r.data.answer -match '无|未|没有|缺' -and $types -notcontains 'assay_entry_preview'
                    $ok=$ok -and $r.data.answer -notmatch '产量(?:为|是|合计|共计)?\s*0\s*(吨|件)|300\s*件|3\s*吨'
                }
                'daily-records-empty' {$ok=$ok -and $r.data.answer -match '无|未|没有|缺' -and $types -notcontains 'assay_entry_preview' -and $types -notcontains 'assay_report'}
                'assay-read' {$ok=$ok -and $types -contains 'assay_report' -and $types -notcontains 'assay_entry_preview' -and $r.data.answer -match '不合格'}
                'assay-preview' {
                    $preview=@($r.data.cards|Where-Object cardType -eq 'assay_entry_preview'|ForEach-Object fields|Where-Object kind -eq 'assay_entry_preview_summary')
                    $ok=$ok -and $preview.Count -eq 1 -and $preview[0].productName -eq $name -and $preview[0].sampleDate -eq '2026-09-25'
                    $color=@($preview[0].metrics|Where-Object name -eq '色值');$ph=@($preview[0].metrics|Where-Object name -eq 'pH值')
                    $ok=$ok -and $color[0].value -eq 22 -and $ph[0].value -eq 0 -and $r.data.answer -match '未保存'
                }
                'inventory' {$ok=$ok -and $types -contains 'inventory_distribution' -and $types -notcontains 'production_daily_records'}
                'quality' {$ok=$ok -and $types -contains 'quality_assay_trend_report' -and $r.data.answer -notmatch '少一行|缺少一天|缺少一行|序列不完整'}
                'software-help' {$ok=$ok -and $types -notcontains 'knowledge_evidence' -and $r.data.answer -notmatch '白砂糖分装|称量、装袋|金属控制'}
                'knowledge' {$ok=$ok -and $types -contains 'knowledge_evidence' -and $r.data.answer -match '白砂糖分装工艺'}
            }
            $id="R$round-$($turn.Id)"
            Save $id @{Input=$turn.Input;Response=$r;StructuredCheck=$ok;Seconds=$watch.Elapsed.TotalSeconds;Kind=$turn.Kind}
            $checks.Add(@{Case=$id;Pass=[bool]$ok;Seconds=$watch.Elapsed.TotalSeconds;CardTypes=$types})
            Save 'summary' $checks
            @{Case=$id;Pass=$ok;Answer=$r.data.answer;Cards=$types}|ConvertTo-Json -Compress
        }
    }finally{Api "/api/agent/sessions/$sid" $null 'Delete'|Out-Null}
}
$after=@{}
foreach($id in $fixture.ProductIds){$after["$id"]=(Api "/api/assay/by-product-date?productId=$id&productionDate=2026-09-25" $null 'Get').data}
$unchanged=$true
foreach($id in $fixture.ProductIds){if(($before["$id"]|ConvertTo-Json -Depth 40 -Compress) -ne ($after["$id"]|ConvertTo-Json -Depth 40 -Compress)){$unchanged=$false}}
Save 'assay-unchanged' @{Pass=$unchanged;Before=$before;After=$after;Scope='Existing assays of exclusive products on 2026-09-25; no execute endpoint called'}
if(-not $unchanged -or @($checks|Where-Object {-not $_.Pass}).Count){throw 'Cross-domain checks failed; retain evidence and review answers'}
'Cross-domain structured checks PASS; full answer review remains required.'
