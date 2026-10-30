param(
    [string]$BaseUrl = 'http://127.0.0.1:38082',
    [string]$LoginName = '陈思聪',
    [string]$LoginPassword = 'lbsp-isolated-uat',
    [string[]]$CaseFilter = @(),
    [string]$EvidencePrefix = ''
)
$ErrorActionPreference = 'Stop'
if (([uri]$BaseUrl).Host -notin @('127.0.0.1', 'localhost', '::1')) { throw 'Local acceptance only' }
$headers = @{}
function Api($path, $body) {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl$path" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 20 -Compress))) -TimeoutSec 180
}
$login = Api '/api/auth/web-login' @{ name=$LoginName; password=$LoginPassword }
if (-not $login.data.token) { throw "Login failed: $($login.msg)" }
$headers.Authorization = "Bearer $($login.data.token)"
$output = Join-Path $PSScriptRoot '../output/daily-report-real-uat'
[IO.Directory]::CreateDirectory($output) | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
function SaveEvidence($name, $value) {
    if ($EvidencePrefix -notmatch '^[a-zA-Z0-9_-]*$') { throw 'Invalid evidence prefix' }
    [IO.File]::WriteAllText((Join-Path $output "$EvidencePrefix$name.json"), ($value | ConvertTo-Json -Depth 60), $utf8)
}
$direct = Api '/api/production/agent-read/daily-reports/query' @{startDate='2026-09-01';endDate='2026-09-08';page=1;size=20}
SaveEvidence 'direct-query' $direct
[pscustomobject]@{Case='direct-query';Code=$direct.code;Total=$direct.data.total;IsEmpty=$direct.data.isEmpty} | ConvertTo-Json -Compress
$cases = @(
    @{id='D01';messages=@('查看今天多晶车间的生产日报')},
    @{id='D02';messages=@('查询最近30天全部产品的生产日报填报明细')},
    @{id='D03';messages=@('查询2026-06-30的生产日报')},
    @{id='D04';messages=@('今天产量如何？','看生产日报填报量')},
    @{id='D05';messages=@('查询2026-06-30的订单登记产出日报')},
    @{id='D06';messages=@('今天整体运营情况如何？')},
    @{id='D07';messages=@('今天多晶车间生产情况如何？','改成昨天','看全部车间，全部产品')}
)
foreach($case in $cases) {
    if ($CaseFilter.Count -and $case.id -notin $CaseFilter) { continue }
    $session = Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')}
    $id = $session.data.agentSessionId
    if (-not $id) { throw "Session failed: $($session.msg)" }
    try {
        $turn = 0
        foreach($message in $case.messages) {
            $turn++
            $watch=[Diagnostics.Stopwatch]::StartNew()
            $response = Api "/api/agent/sessions/$id/messages" @{message=$message}
            $watch.Stop()
            # Never persist session credentials, opaque choices or authorization headers.
            $evidence=[ordered]@{Case="$($case.id)-$turn";Input=$message;Seconds=[math]::Round($watch.Elapsed.TotalSeconds,2);Code=$response.code;Answer=$response.data.answer;NeedsSelection=$response.data.needsUserSelection;Cards=$response.data.cards;ToolCalls=$response.data.toolCalls;Message=$response.msg}
            SaveEvidence "$($case.id)-$turn" $evidence
            $evidence | ConvertTo-Json -Depth 15 -Compress
        }
    } finally {
        Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$id" -Headers $headers -TimeoutSec 15 | Out-Null
    }
}
