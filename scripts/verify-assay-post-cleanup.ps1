param([string]$BaseUrl='http://127.0.0.1:38083')
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local only'}
$root=Split-Path $PSScriptRoot -Parent
$f=Get-Content -Raw -Encoding utf8 (Join-Path $root 'output/assay-group-live-20260924/fixture.json')|ConvertFrom-Json
$a=Get-Content -Raw -Encoding utf8 (Join-Path $root 'output/assay-d4-permissions/fixture.json')|ConvertFrom-Json
if(-not $f.Cleaned -or -not $a.Cleaned){throw 'Cleanup must be complete'}
$out=Join-Path $f.CleanupEvidence 'post-cleanup.json'
$headers=@{}
function Api($path,$body=$null,$method='Post'){
    $p=@{Uri="$BaseUrl$path";Method=$method;Headers=$headers;TimeoutSec=180;SkipHttpErrorCheck=$true}
    if($null -ne $body){$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 15));$p.ContentType='application/json; charset=utf-8'}
    Invoke-RestMethod @p
}
$checks=[Collections.Generic.List[object]]::new()
foreach($name in @($a.QcName,$a.StaffName)){
    $r=Api '/api/auth/web-login' @{name=$name;password='lbsp-isolated-uat'}
    $checks.Add(@{Case='RemovedAccountLogin';Name=$name;Code=$r.code;Pass=($r.code -ne 200 -and -not $r.data.token)})
}
$login=Api '/api/auth/web-login' @{name='陈思聪';password='lbsp-isolated-uat'}
if($login.code -ne 200 -or -not $login.data.token){throw 'Existing admin login failed'}
$headers.Authorization="Bearer $($login.data.token)"
$r=Api '/api/production/agent-read/daily-reports/query' @{startDate='2026-09-19';endDate='2026-09-25';size=20}
$checks.Add(@{Case='DailyRead';Pass=($r.code -eq 200 -and $r.data.isEmpty);Response=$r})
$before=Api '/api/assay/by-product-date?productId=84&productionDate=2026-09-23' $null 'Get'
$r=Api '/api/assay/entry/preview' @{productId=84;sampleDate='2026-09-23';colorValue=0;phValue=0}
$checks.Add(@{Case='ExistingProductReadOnlyPreview';Pass=($r.code -eq 200 -and $r.data.previewOnly -eq $true);Response=$r})
$after=Api '/api/assay/by-product-date?productId=84&productionDate=2026-09-23' $null 'Get'
$checks.Add(@{Case='PreviewDidNotWrite';Pass=($before.code -eq 200 -and $after.code -eq 200 -and ($before.data|ConvertTo-Json -Depth 20 -Compress) -eq ($after.data|ConvertTo-Json -Depth 20 -Compress))})
$sid=(Api '/api/agent/sessions' @{}).data.agentSessionId
if(-not $sid){throw 'Agent session failed'}
try{
    $input='查看2026-09-19到2026-09-25的日报产量趋势'
    $r=Api "/api/agent/sessions/$sid/messages" @{message=$input}
    $checks.Add(@{Case='DailyAgentAfterCleanup';Input=$input;Pass=($r.code -eq 200 -and @($r.data.cards|Where-Object cardType -eq 'production_daily_report_analysis').Count -eq 1 -and $r.data.answer -match '无|没有|未');Response=$r})
}finally{Api "/api/agent/sessions/$sid" $null 'Delete'|Out-Null}
$json=$checks|ConvertTo-Json -Depth 70
$json=[regex]::Replace($json,'("(?:token|productRef|groupRef|previewRef|executionToken|idempotencyKey)"\s*:\s*)"[^"]*"','$1"[redacted]"')
[IO.File]::WriteAllText($out,$json,[Text.UTF8Encoding]::new($false))
$checks|ForEach-Object {@{Case=$_.Case;Pass=$_.Pass;Answer=$_.Response.data.answer}|ConvertTo-Json -Compress}
if(@($checks|Where-Object {-not $_.Pass}).Count){throw 'Post-cleanup smoke failed'}
