param([string]$BaseUrl='http://127.0.0.1:38083', [string]$LoginPassword='lbsp-isolated-uat', [string]$EvidenceName='assay-colloquial-20260929', [switch]$GuidanceOnly)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local verification only'}
if($EvidenceName -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Invalid evidence name'}
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $root "output/$EvidenceName"
[IO.Directory]::CreateDirectory($out)|Out-Null
$headers=@{}
function Api($path,$body=$null,$method='Post') {
    $p=@{Uri="$BaseUrl$path";Method=$method;Headers=$headers;TimeoutSec=180;SkipHttpErrorCheck=$true}
    if($null -ne $body){$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 20));$p.ContentType='application/json; charset=utf-8'}
    Invoke-RestMethod @p
}
function Save($name,$value){
    $json=$value|ConvertTo-Json -Depth 70
    $json=[regex]::Replace($json,'("(?:token|resumeToken|productRef|groupRef|previewRef|executionToken|idempotencyKey)"\s*:\s*)"[^"]*"','$1"[redacted]"')
    [IO.File]::WriteAllText((Join-Path $out "$name.json"),$json,[Text.UTF8Encoding]::new($false))
}
function Check($name,$condition,$inputText,$r){
    Save $name @{Pass=[bool]$condition;Input=$inputText;Response=$r}
    [pscustomobject]@{Case=$name;Pass=[bool]$condition;Answer=$r.data.answer}|ConvertTo-Json -Compress
    if(-not $condition){throw "Failed: $name"}
}
function Chat($text){$r=Api "/api/agent/sessions/$script:sid/messages" @{message=$text}; if($r.code -ne 200){Save 'error' $r;throw 'Chat request failed'};return $r}
function Preview($r){return @($r.data.cards|Where-Object cardType -eq 'assay_entry_preview'|ForEach-Object fields|Where-Object kind -eq 'assay_entry_preview_summary')[0]}
$login=Api '/api/auth/web-login' @{name='陈思聪';password=$LoginPassword}
if(-not $login.data.token){throw 'Local login failed'}
$headers.Authorization="Bearer $($login.data.token)"
$fixture=Get-Content -Raw -Encoding utf8 (Join-Path $root 'output/assay-demo-20260928/fixture.json')|ConvertFrom-Json
if($fixture.Cleaned){throw 'Demo fixture no longer available'}
$name=$fixture.ProductNames[0]
$yesterday=(Get-Date).AddDays(-1).ToString('yyyy-MM-dd')
$before=Api "/api/assay/by-product-date?productId=$($fixture.ProductIds[0])&productionDate=$yesterday" $null 'Get'
if(-not $GuidanceOnly){
$script:sid=(Api '/api/agent/sessions' @{}).data.agentSessionId
if(-not $sid){throw 'Session missing'}
try {
    $text="给产品【$name】记个化验，刚测完，色值二百五，酸碱度六点八，其他没测。"
    $r=Chat $text
    # Web DTO uses needsUserSelection only for selectable cards, not free-text questions.
    Check 'C01-missing-sampling-date' (-not (Preview $r) -and $r.data.answer -match '取|采样' -and $r.data.answer -match '请|哪') $text $r
    $text='昨天取的样。'
    $r=Chat $text;$p=Preview $r
    Check 'C02-short-date-answer' ($p.sampleDate -eq $yesterday -and @($p.metrics|Where-Object {$_.name -eq '色值' -and $_.value -eq 250}).Count -eq 1 -and @($p.metrics|Where-Object {$_.name -eq 'pH值' -and $_.value -eq 6.8}).Count -eq 1) $text $r
    $text='色值说错了，是二十，其他别动。'
    $r=Chat $text;$p=Preview $r
    Check 'C03-correction-preserves-context' ($p.sampleDate -eq $yesterday -and @($p.metrics|Where-Object {$_.name -eq '色值' -and $_.value -eq 20}).Count -eq 1 -and @($p.metrics|Where-Object {$_.name -eq 'pH值' -and $_.value -eq 6.8}).Count -eq 1) $text $r
    $text='就这样，帮我记上吧。'
    $r=Chat $text;$p=Preview $r
    $action=@($r.data.cards|ForEach-Object fields|Where-Object kind -eq 'assay_entry_action')
    Check 'C04-save-request-new-preview' ($p.previewOnly -and $action.Count -eq 1 -and $r.data.answer -match '未保存|没有保存') $text $r
    $text='算了，先别存。'
    $r=Chat $text
    Check 'C05-cancel-no-preview' (-not (Preview $r) -and $r.data.answer -match '未保存|没有保存|不.*保存|取消') $text $r
} finally {Api "/api/agent/sessions/$sid" $null 'Delete'|Out-Null}
$script:sid=(Api '/api/agent/sessions' @{}).data.agentSessionId
try {
    $text="给产品【$name】录一下化验，昨天取的样，色值好像二十左右，其他没测。"
    $r=Chat $text
    Check 'C06-uncertain-measurement' (-not (Preview $r) -and $r.data.answer -match '确认|准确|具体|确定') $text $r
    $text='确认了，是二十。'
    $r=Chat $text;$p=Preview $r
    Check 'C07-confirm-measurement' ($p.sampleDate -eq $yesterday -and @($p.metrics|Where-Object {$_.name -eq '色值' -and $_.value -eq 20}).Count -eq 1) $text $r
} finally {Api "/api/agent/sessions/$sid" $null 'Delete'|Out-Null}
$after=Api "/api/assay/by-product-date?productId=$($fixture.ProductIds[0])&productionDate=$yesterday" $null 'Get'
Check 'C08-no-business-write' (($before.data|ConvertTo-Json -Depth 30 -Compress) -eq ($after.data|ConvertTo-Json -Depth 30 -Compress)) '只读比对前后化验记录；脚本不调用确认或执行接口' $after
}
if($GuidanceOnly){
    $script:sid=(Api '/api/agent/sessions' @{}).data.agentSessionId
    try {
        $text='录个化验。';$r=Chat $text
        Check 'C09-minimal-request' (-not (Preview $r) -and $r.data.answer -match '产品|糖|化验组') $text $r
        $text='黄冰糖，袋装的，昨天取的样，水分零点三，别的没测。';$r=Chat $text
        Check 'C10-ambiguous-metric' (-not (Preview $r) -and $r.data.answer -match '干燥失重|水分' -and $r.data.answer -match '确认|是否|指的是|请问') $text $r
    } finally {Api "/api/agent/sessions/$sid" $null 'Delete'|Out-Null}
    $script:sid=(Api '/api/agent/sessions' @{}).data.agentSessionId
    try {
        $text='黄冰糖袋装的录个化验，昨天取的样，色值二百五，其他没测。';$r=Chat $text;$p=Preview $r
        Check 'C11-packaging-before-correction' ($p.productName -match '袋' -and $p.sampleDate -eq $yesterday) $text $r
        $text='不是袋装，是箱装，刚才说错了，日期和数值不要改。';$r=Chat $text
        $p=Preview $r
        $options=@($r.data.options)
        # Local catalog has multiple box sizes; merely returning one box is NOT sufficient.
        $matches=(-not $p -and $options.Count -ge 3 -and @($options|Where-Object {$_.displayLabel -notmatch '箱' -or $_.supported -eq $false}).Count -eq 0)
        Check 'C12-packaging-correction' $matches $text $r
    } finally {Api "/api/agent/sessions/$sid" $null 'Delete'|Out-Null}
}
