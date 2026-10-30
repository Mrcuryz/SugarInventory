param(
    [ValidateSet('Prepare','Http','Model','Readback','Changes','VerifyBrowser','VerifyRecovery','Cleanup')][string]$Mode='Http',
    [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
    [string]$BaseUrl='http://127.0.0.1:38083',
    [string]$EvidenceName='assay-group-live-20260924',
    [string]$LoginPassword='lbsp-isolated-uat'
)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('localhost','127.0.0.1')){throw 'Local acceptance only'}
if($EvidenceName -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid evidence directory'}
$cfg=@{}
foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?;]+)'){throw 'Local DB only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$dbName=$Matches[3]
function Sql($query){
    $old=[Environment]::GetEnvironmentVariable('MYSQL_PWD','Process');$env:MYSQL_PWD=$cfg.DB_PASSWORD
    try {$rows=@($query | & 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$dbName --default-character-set=utf8mb4 --batch --skip-column-names)
        if($LASTEXITCODE){throw 'Local fixture SQL failed'};return ,$rows
    } finally {[Environment]::SetEnvironmentVariable('MYSQL_PWD',$old,'Process')}
}
$out=Join-Path $PSScriptRoot "../output/$EvidenceName"
[IO.Directory]::CreateDirectory($out)|Out-Null
$manifest=Join-Path $out 'fixture.json'
function Save($name,$value){
    $json=$value|ConvertTo-Json -Depth 40
    $json=[regex]::Replace($json,'("(?:groupRef|productRef|resumeToken|executionToken|idempotencyKey)"\s*:\s*)"[^"]*"','$1"[redacted]"')
    [IO.File]::WriteAllText((Join-Path $out "$name.json"),$json,[Text.UTF8Encoding]::new($false))
}
if($Mode -eq 'Prepare'){
    if(Test-Path $manifest){throw 'Existing manifest; use a fresh evidence name'}
    $marker='UAT_AGLIVE_'+[Guid]::NewGuid().ToString('N')
    $prefix='化验组验收'+(Get-Date -Format 'MMddHHmmss')
    $f=@{Marker=$marker;Prefix=$prefix;ProductIds=@();ProductNames=@();StandardIds=@();GroupIds=@();GroupNames=@();Sessions=@();Cleaned=$false}
    Save 'fixture' $f
    for($i=0;$i -lt 2;$i++){
        $name=$prefix+@('甲糖','乙糖')[$i];$code=$marker+"_$i"
        Sql "INSERT INTO product(product_name,product_type,status,weight_per_piece,pieces_per_pallet,can_stack) VALUES('$name','黄冰糖','成品',25,40,0);"|Out-Null
        $id=[int](Sql "SELECT id FROM product WHERE product_name='$name';")[0]
        $f.ProductIds+=@($id);$f.ProductNames+=@($name);Save 'fixture' $f
        Sql "INSERT INTO quality_standards(standard_code,standard_name,product_type,version,status) VALUES('$code','$name 标准','黄冰糖',1,'ENABLED');"|Out-Null
        $standard=[int](Sql "SELECT id FROM quality_standards WHERE standard_code='$code';")[0]
        $f.StandardIds+=@($standard);Save 'fixture' $f
        $limit=@(30,5)[$i]
        Sql "INSERT INTO quality_standard_item(quality_standard_id,metric_code,metric_name,max_value,compare_type,sort_order) VALUES($standard,'color_value','色值',$limit,'lte',1); INSERT INTO product_quality_standard_relation(product_id,quality_standard_id,is_default,priority,enabled) VALUES($id,$standard,1,1,1);"|Out-Null
    }
    for($i=0;$i -lt 2;$i++){
        $name=$prefix+@('甲组','乙组')[$i];$members=if($i -eq 0){$f.ProductIds -join ','}else{[string]$f.ProductIds[0]}
        Sql "INSERT INTO assay_group(standard_name,related_products) VALUES('$name','$members');"|Out-Null
        $f.GroupIds+=@([int](Sql "SELECT id FROM assay_group WHERE standard_name='$name';")[0]);$f.GroupNames+=@($name);Save 'fixture' $f
    }
    $f|ConvertTo-Json;exit
}
if(-not(Test-Path $manifest)){throw 'Prepare first'}
$f=Get-Content $manifest -Raw|ConvertFrom-Json -AsHashtable
if($f.Cleaned -or $f.Marker -notmatch '^UAT_AGLIVE_[a-f0-9]{32}$' -or $f.Prefix -notmatch '^化验组验收[0-9]{10}$'){throw 'Invalid or cleaned fixture'}
for($i=0;$i -lt $f.ProductIds.Count;$i++){
    $id=[int]$f.ProductIds[$i];$name=[string]$f.ProductNames[$i]
    if($name -notmatch '^化验组验收[0-9]{10}[甲乙]糖$' -or [int](Sql "SELECT COUNT(*) FROM product WHERE id=$id AND product_name='$name';")[0] -ne 1){throw 'Product identity mismatch'}
}
$ids=($f.ProductIds|ForEach-Object {[int]$_}) -join ','
$groups=($f.GroupIds|ForEach-Object {[int]$_}) -join ','
$standards=($f.StandardIds|ForEach-Object {[int]$_}) -join ','
if($Mode -eq 'Cleanup'){
    if($groups){Sql "DELETE a FROM agent_assay_entry_audit a JOIN agent_assay_entry_preview p ON a.preview_ref=p.preview_ref WHERE JSON_EXTRACT(p.normalized_input_json,'$.request.groupId') IN ($groups); DELETE c FROM agent_assay_entry_confirmation c JOIN agent_assay_entry_preview p ON c.preview_ref=p.preview_ref WHERE JSON_EXTRACT(p.normalized_input_json,'$.request.groupId') IN ($groups); DELETE FROM agent_assay_entry_preview WHERE JSON_EXTRACT(normalized_input_json,'$.request.groupId') IN ($groups); DELETE FROM assay_group WHERE id IN ($groups) AND standard_name LIKE '$($f.Prefix)%';"|Out-Null}
    if($ids){Sql "DELETE FROM assay WHERE product_id IN ($ids); DELETE FROM product_quality_standard_relation WHERE product_id IN ($ids); DELETE FROM product WHERE id IN ($ids) AND product_name LIKE '$($f.Prefix)%';"|Out-Null}
    if($standards){Sql "DELETE FROM quality_standard_item WHERE quality_standard_id IN ($standards); DELETE FROM quality_standards WHERE id IN ($standards) AND standard_code LIKE '$($f.Marker)%';"|Out-Null}
    $remaining=@{
        Products=[int](Sql "SELECT COUNT(*) FROM product WHERE id IN ($ids);")[0]
        Assays=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0]
        Groups=[int](Sql "SELECT COUNT(*) FROM assay_group WHERE id IN ($groups);")[0]
        Standards=[int](Sql "SELECT COUNT(*) FROM quality_standards WHERE id IN ($standards);")[0]
        Previews=[int](Sql "SELECT COUNT(*) FROM agent_assay_entry_preview WHERE JSON_EXTRACT(normalized_input_json,'$.request.groupId') IN ($groups);")[0]
    }
    $clean=@($remaining.Values|Where-Object {$_ -ne 0}).Count -eq 0
    Save 'cleanup' @{Pass=$clean;Remaining=$remaining;InventoryTouched=$false}
    if(-not $clean){throw 'Fixture cleanup incomplete'}
    $f.Cleaned=$true;Save 'fixture' $f;'Fixture cleaned';exit
}
if($Mode -eq 'VerifyRecovery'){
    $rows=Sql "SELECT product_id,version,color_value,ph_value,dry_weight,judge_result FROM assay WHERE product_id IN ($ids) AND sample_date='2026-09-25' ORDER BY product_id,version;"
    $ok=$rows.Count -eq 2 -and @($rows|Where-Object {$_ -notmatch '^\d+\t1\t10\.00\t0\.00\tNULL\t'}).Count -eq 0
    Save 'recovery-database' @{Pass=$ok;Rows=$rows;Expected='Only original two records after refresh/recovery; no version2'}
    if(-not $ok){throw 'Recovery records mismatch'};$rows;exit
}
if($Mode -eq 'VerifyBrowser'){
    $rows=Sql "SELECT product_id,version,color_value,ph_value,dry_weight,judge_result FROM assay WHERE product_id IN ($ids) AND sample_date='2026-09-24' ORDER BY product_id,version;"
    $ok=$rows.Count -eq 2 -and @($rows|Where-Object {$_ -notmatch '^\d+\t1\t10\.00\t0\.00\tNULL\t'}).Count -eq 0
    Save 'browser-database' @{Pass=$ok;Rows=$rows;Expected='Two products, version1/color10/pH0/dryWeightNULL'}
    if(-not $ok){throw 'Browser records mismatch'};$rows;exit
}
$headers=@{}
function Api($path,$body=$null,$method='Post'){
    $params=@{Uri="$BaseUrl$path";Method=$method;Headers=$headers;ContentType='application/json; charset=utf-8';TimeoutSec=180}
    if($null -ne $body){$params.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 30))}
    Invoke-RestMethod @params
}
function Assert($name,$ok,$evidence){Save $name @{Pass=[bool]$ok;Evidence=$evidence};if(-not $ok){throw "Failed $name"};"$name PASS"}
function Chat($path,$body){
    $response=Invoke-WebRequest -Uri "$BaseUrl$path" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 20))) -TimeoutSec 180
    $data=@{answer='';cards=@();needsUserSelection=$false};$events=@()
    foreach($line in ($response.Content -split "`n")){
        if($line -notmatch '^data:\s*(\{.*\})\s*$'){continue}
        $event=$Matches[1]|ConvertFrom-Json -AsHashtable;$events+=@($event);$payload=$event.payload
        switch($event.type){
            'card' {$data.cards+=@($payload)}
            'text_delta' {$data.answer+=$payload.text}
            'clarification' {$data.answer=$payload.prompt;$data.needsUserSelection=$true;$data.interrupt=$payload}
            'error' {Save "stream-error-$Mode-$(Get-Date -Format 'HHmmss')" @{Input=$body;Events=$events};throw "Agent stream failed: $($payload.code) $($payload.errorCode) $($payload.message)"}
        }
    }
    if($data.interrupt){
        $candidate=@($data.cards|Where-Object cardType -eq 'candidate_selection')
        if(-not $candidate){$card=@{cardType='candidate_selection';options=$data.interrupt.options};$data.cards+=@($card)}else{$card=$candidate[0]}
        $card.interruptId=$data.interrupt.interruptId;$card.resumeToken=$data.interrupt.resumeToken
    }
    return @{code=200;data=$data;transport='SSE';events=$events}
}
$login=Api '/api/auth/web-login' @{name='陈思聪';password=$LoginPassword}
if(-not $login.data.token){throw 'Login failed'};$headers.Authorization="Bearer $($login.data.token)"
$session=(Api '/api/agent/sessions' @{}).data.agentSessionId
if(-not $session){throw 'Session missing'};$f.Sessions+=@($session);Save 'fixture' $f
$root="/api/agent/sessions/$session/assay-entry/previews"
try {
    if($Mode -eq 'Readback'){
        $before=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0]
        $text="查询$($f.ProductNames[1])在2026-09-25的化验记录，只查询已有记录，不录入。"
        $r=Chat "/api/agent/sessions/$session/messages/stream" @{message=$text}
        if($r.data.needsUserSelection){
            Save 'Q01-product-selection' @{Input=$text;Response=$r}
            $card=@($r.data.cards|Where-Object cardType -eq 'candidate_selection')[0]
            if($card.options.Count -ne 1){throw 'Readback product is not unique'}
            $r=Chat "/api/agent/sessions/$session/interrupts/$($card.interruptId)/resume/stream" @{resumeToken=$card.resumeToken;action='SELECT_OPTION';clientRequestId=[Guid]::NewGuid().ToString('N');selection=@{optionId=$card.options[0].optionId}}
        }
        $report=@($r.data.cards|Where-Object cardType -eq 'assay_report')[0]
        $summary=@($report.fields|Where-Object kind -eq 'assay_summary')[0]
        $color=@($report.fields|Where-Object metricName -eq '色值')[0]
        $ph=@($report.fields|Where-Object metricName -eq 'pH')[0]
        $dry=@($report.fields|Where-Object metricName -eq '干燥失重')[0]
        Assert 'Q02-readback' ($r.code -eq 200 -and $r.data.answer -match '不合格' -and $summary.productLabel -eq $f.ProductNames[1] -and $summary.sampleDate -eq '2026-09-25' -and $color.actualValueText -eq '10' -and $ph.actualValueText -eq '0' -and $dry.actualValueText -eq '未填写' -and @($r.data.cards|Where-Object cardType -eq 'assay_entry_preview').Count -eq 0) @{Input=$text;Response=$r}
        Assert 'Q03-read-only' ([int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0] -eq $before) @{Before=$before}
    } elseif($Mode -eq 'Changes'){
        $before=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0]
        $catalog=Api '/api/quality/agent-read/assay-groups/query' @{groupName=$f.Prefix}
        $row=@($catalog.data.records|Where-Object groupName -eq $f.GroupNames[0])[0]
        $input=@{groupRef=$row.groupRef;values=@{sampleDate='2026-09-24';colorValue=20;phValue=0}}
        $ref=(Api "$root/from-group" $input).data.previewRef
        $groupId=[int]$f.GroupIds[0];$firstId=[int]$f.ProductIds[0];$standard=[int]$f.StandardIds[1]
        try {
            Sql "UPDATE assay_group SET related_products='$firstId' WHERE id=$groupId;"|Out-Null
            $r=Api "$root/$ref/confirm"
            Assert 'C01-member-change' ($r.code -eq 1055) $r
        } finally {Sql "UPDATE assay_group SET related_products='$ids' WHERE id=$groupId;"|Out-Null}
        $ref=(Api "$root/from-group" $input).data.previewRef
        $confirmation=Api "$root/$ref/confirm"
        if($confirmation.code -ne 200){throw 'Confirmation failed'}
        try {
            Sql "UPDATE quality_standard_item SET max_value=6 WHERE quality_standard_id=$standard AND metric_code='color_value';"|Out-Null
            $r=Api "$root/$ref/execute" @{executionToken=$confirmation.data.executionToken;idempotencyKey=$confirmation.data.idempotencyKey}
            Assert 'C02-standard-change-after-confirm' ($r.code -eq 1055) $r
        } finally {Sql "UPDATE quality_standard_item SET max_value=5 WHERE quality_standard_id=$standard AND metric_code='color_value';"|Out-Null}
        $ref=(Api "$root/from-group" $input).data.previewRef
        try {
            Sql "UPDATE assay_group SET related_products='' WHERE id=$groupId;"|Out-Null
            $r=Api "$root/$ref/confirm"
            Assert 'C03-empty-group' ($r.code -eq 1055) $r
        } finally {Sql "UPDATE assay_group SET related_products='$ids' WHERE id=$groupId;"|Out-Null}
        Assert 'C04-no-write' ([int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0] -eq $before) @{Before=$before}
    } elseif($Mode -eq 'Model'){
        $before=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0]
        $text="录入$($f.Prefix)的化验，采样日期2026-09-24，色值10，pH值0，其他未检测。"
        $r=Chat "/api/agent/sessions/$session/messages/stream" @{message=$text}
        $cards=@($r.data.cards|Where-Object cardType -eq 'candidate_selection')
        Assert 'M01-candidates' ($r.code -eq 200 -and $cards.Count -eq 1 -and $cards[0].options.Count -eq 2) @{Input=$text;Response=$r}
        $card=$cards[0];$choice=@($card.options|Where-Object displayLabel -eq $f.GroupNames[0])[0]
        $r=Chat "/api/agent/sessions/$session/interrupts/$($card.interruptId)/resume/stream" @{resumeToken=$card.resumeToken;action='SELECT_OPTION';clientRequestId=[Guid]::NewGuid().ToString('N');selection=@{optionId=$choice.optionId}}
        $preview=@($r.data.cards|Where-Object cardType -eq 'assay_entry_preview')[0]
        $summary=@($preview.fields|Where-Object kind -eq 'assay_entry_preview_summary')[0]
        $action=@($preview.fields|Where-Object kind -eq 'assay_entry_action')[0]
        Assert 'M02-group-preview' ($r.code -eq 200 -and $summary.entryKind -eq 'GROUP_V1' -and $summary.products.Count -eq 2 -and $action.entryInput.groupRef -match '^aer_' -and $r.data.answer -match '未保存' -and $r.data.answer -notmatch '当前没有保存能力|系统没有保存能力|尚未开放人工保存入口|不支持保存') @{Input="选择$($f.GroupNames[0])";Response=$r}
        $text='色值改成20，其他不变，重新预览。'
        $r=Chat "/api/agent/sessions/$session/messages/stream" @{message=$text}
        $preview=@($r.data.cards|Where-Object cardType -eq 'assay_entry_preview')[0]
        $action=@($preview.fields|Where-Object kind -eq 'assay_entry_action')[0]
        Assert 'M03-modify' ($r.code -eq 200 -and $action.entryInput.values.colorValue -eq 20 -and $action.entryInput.values.phValue -eq 0 -and $r.data.answer -match '未保存' -and $r.data.answer -notmatch '当前没有保存能力|系统没有保存能力|尚未开放人工保存入口|不支持保存') @{Input=$text;Response=$r}
        Assert 'M04-no-write' ([int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0] -eq $before) @{Before=$before}
    } else {
        $catalog=Api '/api/quality/agent-read/assay-groups/query' @{groupName=$f.Prefix}
        $row=@($catalog.data.records|Where-Object groupName -eq $f.GroupNames[0])[0]
        Assert 'H01-reference' ($row.groupRef -match '^aer_' -and $catalog.data.records.Count -eq 2) $catalog
        $input=@{groupRef=$row.groupRef;values=@{sampleDate='2026-09-23';colorValue=10;phValue=0}}
        $p=Api '/api/assay/entry/group/from-reference/preview' $input
        Assert 'H02-read-preview' ($p.code -eq 200 -and $p.data.productCount -eq 2 -and $p.data.products[0].judgementLabel -eq '合格' -and $p.data.products[1].judgementLabel -eq '不合格') $p
        $archive=Api "$root/from-group" $input;$ref=$archive.data.previewRef
        Assert 'H03-archive' ($archive.code -eq 200 -and $archive.data.preview.productCount -eq 2) $archive
        $c=Api "$root/$ref/confirm";$again=Api "$root/$ref/confirm"
        Assert 'H04-confirm-idempotent-no-write' ($c.code -eq 200 -and $c.data.executionToken -ceq $again.data.executionToken -and [int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0] -eq 0) @{Code=$c.code;Replay=$again.data.replayed}
        $credentials=@{executionToken=$c.data.executionToken;idempotencyKey=$c.data.idempotencyKey}
        $saved=Api "$root/$ref/execute" $credentials;$retry=Api "$root/$ref/execute" $credentials
        Assert 'H05-atomic-replay' ($saved.data.saved -and $saved.data.details.productCount -eq 2 -and $retry.data.replayed -and ($saved.data.details.products.assayId -join ',') -eq ($retry.data.details.products.assayId -join ',')) @{First=$saved;Retry=$retry}
        $read=Api "$root/$ref/result" $null 'Get'
        Assert 'H06-result' ($read.data.saved -and $read.data.details.products.Count -eq 2) $read
        $cancel=(Api "$root/from-group" $input).data.previewRef
        Api "$root/$cancel/cancel"|Out-Null
        $late=Api "$root/$cancel/confirm"
        Assert 'H07-cancel' ($late.code -in @(1054,1058)) $late
        Assert 'H08-database' ([int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0] -eq 2) @{Rows=(Sql "SELECT product_id,version,color_value,ph_value,dry_weight FROM assay WHERE product_id IN ($ids);")}
    }
} finally {Api "/api/agent/sessions/$session" $null 'Delete'|Out-Null}
