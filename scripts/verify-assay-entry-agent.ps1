param(
    [string]$BaseUrl = 'http://127.0.0.1:38083',
    [string]$EvidenceName = 'assay-entry-agent',
    [ValidateSet('basic','date','precision','units','bounds')][string]$Scenario = 'basic'
)
$ErrorActionPreference = 'Stop'
if (([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')) { throw 'Local acceptance only' }
if ($EvidenceName -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid evidence directory' }
$out = Join-Path $PSScriptRoot "../output/$EvidenceName"
[IO.Directory]::CreateDirectory($out) | Out-Null
$headers = @{}
function Save($name,$value) {
    $json=$value | ConvertTo-Json -Depth 90
    $json=[regex]::Replace($json,'("(?:token|resumeToken|productRef|groupRef|previewRef|executionToken|idempotencyKey)"\s*:\s*)"[^"]*"','$1"[redacted]"')
    [IO.File]::WriteAllText((Join-Path $out "$name.json"),$json,[Text.UTF8Encoding]::new($false))
}
function Api($path,$body) {
    Invoke-RestMethod -Uri "$BaseUrl$path" -Headers $headers -TimeoutSec 180 -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 20)))
}
function HasSaveClaim([string]$answer) {
    foreach($match in [regex]::Matches($answer,'已(?:经)?(?:成功|完成)?(?:保存|录入|写入|提交)|(?:保存|录入|写入|提交)成功')) {
        $clause = ($answer.Substring(0,$match.Index) -split '[，。；;！？\n]|但是|但|而是|实际上|其实')[-1]
        if ($clause.EndsWith('未') -or $clause -match '(?:不能|不得|不要|不应|并非|不是|不代表|不表示|不等于)[^，。；;！？\n]{0,8}$') { continue }
        return $true
    }
    return $false
}
if ((HasSaveClaim '尚未保存，不能视为录入成功') -or -not (HasSaveClaim '尚未保存但已保存')) { throw 'Save-claim assertion self-check failed' }
$login = Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'}
if (-not $login.data.token) { throw 'Login failed' }
$headers.Authorization = "Bearer $($login.data.token)"
$turns = switch ($Scenario) {
    basic { @(
        @{Input='录入黄冰糖（袋）的化验，采样日期2026-09-23，色值268，还原糖0.15，pH7.52，先给我核对。';Kind='preview';Color=268},
        @{Input='色值改成0，其他保持不变，再给我核对。';Kind='preview';Color=0},
        @{Input='先取消这次录入，改查黄冰糖（袋）的最新化验。';Kind='query'}
    ) }
    date { @(
        @{Input='录入黄冰糖（袋）的化验，色值268，pH7.52。';Kind='date'},
        @{Input='采样日期2026-09-23，还原糖是0.15。';Kind='preview';Color=268}
    ) }
    precision { @(
        @{Input='录入黄冰糖（袋）的化验，采样日期2026-09-23，还原糖0.123。';Kind='precision'}
    ) }
    units { @(
        @{Input='录入黄冰糖（袋）的化验，采样日期2026-09-23，还原糖0.15，单位可能是百分比也可能是g/kg，我还不确定，先核对不要保存。';Kind='units'}
    ) }
    bounds { @(
        @{Input='录入黄冰糖（袋）的化验，采样日期2026-09-23，pH值14.01，先核对不要保存。';Kind='bounds'}
    ) }
}
$checks = [Collections.Generic.List[object]]::new()
$s = Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')}
$sid = $s.data.agentSessionId
if (-not $sid) { throw 'Session creation failed' }
try {
    $n = 0
    foreach ($turn in $turns) {
        $n++
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $r = Api "/api/agent/sessions/$sid/messages" @{message=$turn.Input;pageContext=@{debug=$true}}
        $types = @($r.data.cards | ForEach-Object cardType)
        $ok = $r.code -eq 200 -and -not [string]::IsNullOrWhiteSpace($r.data.answer)
        $ok = $ok -and $r.data.answer -notmatch '暂时无法完成|暂时没能完成|暂时未能整理' -and -not (HasSaveClaim $r.data.answer)
        $preview = @($r.data.cards | Where-Object cardType -eq 'assay_entry_preview' | ForEach-Object fields | Where-Object kind -eq 'assay_entry_preview_summary')
        if ($turn.Kind -eq 'preview') {
            $ok = $ok -and $preview.Count -eq 1 -and $preview[0].sampleDate -eq '2026-09-23' -and $preview[0].previewOnly -eq $true -and $r.data.answer -match '未保存|未录入|未写入|没有保存'
            $ok = $ok -and $r.data.answer -notmatch '未命中.*标准|未参与判定|不参与判定|无法参与判定|未作判定|未做判定|不自动判定|不视为合格或不合格'
            if ($preview.Count -eq 1) {
                $color = @($preview[0].metrics | Where-Object name -eq '色值')
                $ok = $ok -and $color.Count -eq 1 -and $color[0].provided -eq $true -and $color[0].value -eq $turn.Color
            }
        } else {
            $ok = $ok -and $preview.Count -eq 0
            if ($turn.Kind -eq 'date') { $ok = $ok -and $r.data.answer -match '日期|哪天' }
            if ($turn.Kind -eq 'precision') { $ok = $ok -and $r.data.answer -match '小数|精度|两位|2位' }
            if ($turn.Kind -eq 'units') { $ok = $ok -and $r.data.answer -match '单位' -and $r.data.answer -match '确认|明确|补充' }
            if ($turn.Kind -eq 'bounds') { $ok = $ok -and $r.data.answer -match '14|范围|输入.*不正确|输入.*无效' }
            if ($turn.Kind -eq 'query') { $ok = $ok -and $types -contains 'assay_report' }
        }
        $id = "$Scenario-$n"
        Save $id @{Input=$turn.Input;Response=$r;StructuredCheck=$ok;Seconds=$watch.Elapsed.TotalSeconds}
        $checks.Add(@{Case=$id;Pass=$ok;CardTypes=$types})
        Save "summary-$Scenario" $checks
        @{Case=$id;Pass=$ok;Answer=$r.data.answer;Cards=$types;Seconds=$watch.Elapsed.TotalSeconds} | ConvertTo-Json -Compress
    }
} finally { Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/agent/sessions/$sid" -Headers $headers | Out-Null }
if (@($checks | Where-Object {-not $_.Pass}).Count) { throw 'Assay agent acceptance failed; inspect evidence.' }
