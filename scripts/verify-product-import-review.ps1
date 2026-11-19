param([Parameter(Mandatory)][string]$Workbook,[int[]]$Rows=@(2,3,4,5,6,7),
      [string]$LoginName='陈思聪',[Parameter(Mandatory)][string]$LoginPassword)
$ErrorActionPreference='Stop'
$base='http://127.0.0.1:38082'
$file=Get-Item -LiteralPath $Workbook
$headers=@{}
function Post($path,$body) {
    Invoke-RestMethod "$base$path" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -TimeoutSec 180 -Body ($body | ConvertTo-Json -Depth 8)
}
$login=Post '/api/auth/web-login' @{name=$LoginName;password=$LoginPassword}
if (!$login.data.token) {throw '验收登录失败'}
$headers.Authorization="Bearer $($login.data.token)"
$sid=(Post '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')}).data.agentSessionId
if (!$sid){throw '验收会话创建失败'}
$root="/api/agent/sessions/$sid/product-import"
$failed=@()
$ref=$null
try {
    $cap=(Invoke-RestMethod "$base$root/capabilities" -Headers $headers).data
    if (!$cap.matchingEnabled -or $cap.executionEnabled){throw '仅在开放核对、禁止写入的服务验收'}
    $info=Invoke-RestMethod "$base$root/inspect" -Method Post -Headers $headers -Form @{file=$file}
    if ($info.data.Count -ne 1){throw '工作表检查失败'}
    $draft=Invoke-RestMethod "$base$root/drafts" -Method Post -Headers $headers -Form @{file=$file;sheet='产品核对';mapping='{"0":"productName","1":"productType","2":"status","3":"packagingMethod","4":"weightPerPiece"}'}
    $ref=$draft.data.reference
    if (!$ref -or $draft.data.rowCount -ne 6){throw "未取得6条来源行。$($draft.message)"}
    foreach ($row in $Rows) {
        $r=Post "/api/agent/sessions/$sid/messages" @{message="核对产品表格第${row}行，与现有产品比较。";pageContext=@{productImportAttachment=$ref;productImportRow=$row}}
        $card=@($r.data.cards | Where-Object cardType -eq 'product_import_review')[0]
        $d=@($card.fields)[0]
        $ok=switch ($row) {
            2 { @($d.options | Where-Object {$_.blocked -and $_.values.productName -eq '黄冰糖（袋）'}).Count -eq 1 }
            3 { @($d.options | Where-Object {$_.suggested -and $_.values.productName -eq '黄冰糖（袋）'}).Count -eq 1 -and $r.data.answer -match '黄冰糖' -and $r.data.answer -notmatch '(缺少|未包含|未提供).{0,12}(比较|明细)' }
            4 { $d.statusLabel -eq '没有差异' }
            {$_ -in 5,6} { $d.statusLabel -eq '表内重名' -and ($d.duplicateRows -join ',') -eq '5,6' }
            7 { $d.partial -and $d.allowManualCreate -and !$d.allowCreate -and $d.statusLabel -eq '需要核对' -and !@($d.options | Where-Object suggested).Count }
        }
        $ok=[bool]($ok -and $card -and $card.title.StartsWith("第${row}行") -and !$r.data.error)
        [pscustomobject]@{row=$row;pass=$ok;status=$d.statusLabel;answer=$r.data.answer;notice=$d.message;recommended=@($d.options|Where-Object suggested|ForEach-Object {$_.values.productName})} | ConvertTo-Json -Compress
        if(!$ok){$failed+=$row}
    }
} finally {
    try { if($ref){Invoke-RestMethod "$base$root/drafts/$ref" -Method Delete -Headers $headers | Out-Null} }
    finally {Invoke-RestMethod "$base/api/agent/sessions/$sid" -Method Delete -Headers $headers | Out-Null}
}
if($failed.Count){throw "核对未通过的表格行：$($failed -join ',')"}
