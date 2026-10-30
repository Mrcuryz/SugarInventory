param(
    [ValidateSet('Prepare','Http','Model','Recovery','VerifyBrowser','Cleanup')][string]$Mode='Http',
    [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
    [string]$BaseUrl='http://127.0.0.1:38083',
    [string]$LoginPassword='lbsp-isolated-uat',
    [string]$EvidenceName='assay-entry-ui'
)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local acceptance only'}
$cfg=@{}
foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?;]+)'){throw 'Local DB only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$dbName=$Matches[3]
function Sql($query){
    $old=[Environment]::GetEnvironmentVariable('MYSQL_PWD','Process'); $env:MYSQL_PWD=$cfg.DB_PASSWORD
    try {
        $rows=@($query | & 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$dbName --default-character-set=utf8mb4 --batch --skip-column-names)
        if($LASTEXITCODE){throw 'Local fixture SQL failed'}
        return ,$rows
    } finally { [Environment]::SetEnvironmentVariable('MYSQL_PWD',$old,'Process') }
}
if($EvidenceName -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid evidence directory'}
$out=Join-Path $PSScriptRoot "../output/$EvidenceName"
[IO.Directory]::CreateDirectory($out)|Out-Null
$fixturePath=Join-Path $out 'fixture.json'
function Save($name,$value){[IO.File]::WriteAllText((Join-Path $out "$name.json"),($value|ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))}
if($Mode -eq 'Prepare'){
    if(Test-Path $fixturePath){throw 'Existing fixture manifest; verify/clean it before preparing another'}
    $marker='UAT_ASSAY_UI_'+[Guid]::NewGuid().ToString('N')
    $productName='化验录入验收糖'+(Get-Date -Format 'MMddHHmmss')+'_'+$marker.Substring($marker.Length-8)
    Sql "INSERT INTO product(product_name,product_type,status,weight_per_piece,pieces_per_pallet,can_stack) VALUES('$productName','黄冰糖','成品',25,40,0);" | Out-Null
    $id=[int](Sql "SELECT id FROM product WHERE product_name='$productName';")[0]
    $fixture=@{ProductId=$id;ProductName=$productName;Marker=$marker;BaseUrl=$BaseUrl;Sessions=@();Cleaned=$false}
    Save 'fixture' $fixture
    $fixture|ConvertTo-Json;exit
}
if(-not(Test-Path $fixturePath)){throw 'Run Prepare first'}
$fixture=Get-Content $fixturePath -Raw|ConvertFrom-Json -AsHashtable
if($fixture.Cleaned){throw 'Fixture already cleaned'}
$id=[int]$fixture.ProductId;$marker=[string]$fixture.Marker
$productName=[string]$fixture.ProductName
if($id -le 0 -or $marker -notmatch '^UAT_ASSAY_UI_[a-f0-9]{32}$'){throw 'Invalid fixture identity'}
if($productName -notmatch '^化验录入验收糖[0-9]{10}_[a-f0-9]{8}$'){throw 'Invalid fixture name'}
if([int](Sql "SELECT COUNT(*) FROM product WHERE id=$id AND product_name='$productName';")[0] -ne 1){throw 'Fixture identity mismatch'}
if($Mode -eq 'Cleanup'){
    # Delete only control records of this exact test product, never another preview in the same session.
    Sql "DELETE a FROM agent_assay_entry_audit a JOIN agent_assay_entry_preview p ON a.preview_ref=p.preview_ref WHERE JSON_EXTRACT(p.normalized_input_json,'$.productId')=$id; DELETE c FROM agent_assay_entry_confirmation c JOIN agent_assay_entry_preview p ON c.preview_ref=p.preview_ref WHERE JSON_EXTRACT(p.normalized_input_json,'$.productId')=$id; DELETE FROM agent_assay_entry_preview WHERE JSON_EXTRACT(normalized_input_json,'$.productId')=$id;"|Out-Null
    Sql "DELETE FROM assay WHERE product_id=$id; DELETE FROM product WHERE id=$id AND product_name='$productName';"|Out-Null
    $remaining=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$id;")[0]
    if($remaining -ne 0){throw 'Fixture assay cleanup failed'}
    $fixture.Cleaned=$true;Save 'fixture' $fixture;Save 'cleanup' @{Pass=$true;ProductId=$id;RemainingAssays=$remaining;InventoryTouched=$false}
    'Fixture business/control data cleaned; existing users and inventory untouched.';exit
}
if($Mode -eq 'VerifyBrowser'){
    $rows=Sql "SELECT version,color_value,ph_value,dry_weight,judge_result FROM assay WHERE product_id=$id AND sample_date='2026-09-24' ORDER BY version;"
    Save 'browser-database' @{ProductId=$id;Rows=$rows;Expected='Exactly one version: color=268,pH=0,dryWeight=NULL'}
    if($rows.Count -ne 1 -or $rows[0] -notmatch '^1\t268\.00\t0\.00\tNULL\t'){throw 'Browser saved record mismatch'}
    $rows;exit
}
$headers=@{}
function Api($path,$body=$null,$method='Post'){
    $params=@{Uri="$BaseUrl$path";Method=$method;Headers=$headers;ContentType='application/json; charset=utf-8';TimeoutSec=60}
    if($null -ne $body){$params.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 20))}
    Invoke-RestMethod @params
}
function Assert($test,$condition,$value){Save $test @{Pass=[bool]$condition;Result=$value};if(-not $condition){throw "Failed: $test"}}
$login=Api '/api/auth/web-login' @{name='陈思聪';password=$LoginPassword}
if(-not $login.data.token){throw 'Login failed'}
$headers.Authorization="Bearer $($login.data.token)"
$session=(Api '/api/agent/sessions' @{}).data.agentSessionId
if(-not $session){throw 'Session missing'}
$fixture.Sessions+=@($session);Save 'fixture' $fixture
$root="/api/agent/sessions/$session/assay-entry/previews"
if($Mode -eq 'Recovery'){
    $before=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$id;")[0]
    $preview=(Api $root @{productId=$id;sampleDate='2026-09-24';colorValue=271;phValue=0}).data.previewRef
    $confirmed=Api "$root/$preview/confirm"
    Api "/api/agent/sessions/$session" $null 'Delete'|Out-Null
    $settled=Api "$root/$preview/recover"
    Assert 'R01-revoked-session-settle' ($settled.code -eq 200 -and -not $settled.data.saved -and $settled.data.statusLabel -eq '已取消') $settled
    $late=Api "$root/$preview/execute" @{executionToken=$confirmed.data.executionToken;idempotencyKey=$confirmed.data.idempotencyKey}
    Assert 'R02-late-write-denied' ($late.code -ne 200) @{Code=$late.code;Message=$late.msg}
    $saved=@(Sql "SELECT agent_session_id,preview_ref FROM agent_assay_entry_preview WHERE JSON_EXTRACT(normalized_input_json,'$.productId')=$id AND status='SUCCEEDED';")
    if($saved.Count -ne 1){throw 'Expected exactly one browser successful preview'}
    $identity=$saved[0] -split "`t"
    $readback=Api "/api/agent/sessions/$($identity[0])/assay-entry/previews/$($identity[1])/recover"
    Assert 'R03-saved-readback' ($readback.code -eq 200 -and $readback.data.saved -and $readback.data.version -eq 1) $readback
    Assert 'R04-no-new-version' ([int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$id;")[0] -eq $before) @{Before=$before;After=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$id;")[0];ControlStates=(Sql "SELECT status,COUNT(*) FROM agent_assay_entry_preview WHERE JSON_EXTRACT(normalized_input_json,'$.productId')=$id GROUP BY status;")}
    'Recovery HTTP: 4 PASS; no additional assay.';exit
}
if($Mode -eq 'Model'){
    $before=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$id;")[0]
    $turns=@(
        @{Text="预览${productName}的化验，采样日期2026-09-24，色值269，pH值0，其他未检测。";Color=269},
        @{Text='色值改成270，其他不变，重新预览。';Color=270}
    )
    try {
        $index=0
        foreach($turn in $turns){
            $index++
            $r=Api "/api/agent/sessions/$session/messages" @{message=$turn.Text}
            $fields=@($r.data.cards | Where-Object cardType -eq 'assay_entry_preview' | ForEach-Object fields)
            $summary=@($fields | Where-Object kind -eq 'assay_entry_preview_summary')
            $action=@($fields | Where-Object kind -eq 'assay_entry_action')
            $ok=$r.code -eq 200 -and $summary.Count -eq 1 -and $action.Count -eq 1
            if($ok){
                $ok=$action[0].entryInput.productRef -match '^aer_' -and -not $action[0].entryInput.values.productId
                $ok=$ok -and $action[0].entryInput.values.colorValue -eq $turn.Color -and $action[0].entryInput.values.phValue -eq 0
                $ok=$ok -and $summary[0].existingVersion -eq 1 -and $summary[0].proposedVersion -eq 2
                $action[0].entryInput.productRef='[signed reference redacted]'
            }
            $ok=$ok -and $r.data.answer -match '未保存' -and $r.data.answer -notmatch '如需入库|确认入库|完成入库|入库成功|保存入库|没有可用的保存|尚不支持保存'
            Assert "M0$index-model-preview" $ok @{Input=$turn.Text;Response=$r}
            @{Case="M0$index";Pass=$ok;Answer=$r.data.answer}|ConvertTo-Json -Compress
        }
        Assert 'M03-no-write' ([int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$id;")[0] -eq $before) @{Before=$before;After=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$id;")[0]}
    } finally {Api "/api/agent/sessions/$session" $null 'Delete'|Out-Null}
    exit
}
$input=@{productId=$id;sampleDate='2026-09-23';colorValue=0;phValue=7.52}
$p=Api '/api/assay/entry/preview' $input
Assert 'H01-capability' ($p.code -eq 200 -and $p.data.confirmationAvailable -eq $true) @{Input=$input;Preview=$p}
$preview=Api $root $input;$ref=$preview.data.previewRef
Assert 'H02-exact-preview' ($preview.code -eq 200 -and $ref -and $preview.data.preview.metrics.Count -eq 7) @{Input=$input;Preview=$preview}
$c=Api "$root/$ref/confirm";$again=Api "$root/$ref/confirm"
Assert 'H03-confirm-replay-no-write' ($c.code -eq 200 -and $c.data.executionToken -ceq $again.data.executionToken -and [int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$id;")[0] -eq 0) @{Code=$c.code;Replay=$again.data.replayed;TokensStoredInEvidence=$false}
$credentials=@{executionToken=$c.data.executionToken;idempotencyKey=$c.data.idempotencyKey}
$tampered=Api "$root/$ref/execute" ($credentials+@{colorValue=999})
Assert 'H04-extra-business-input-rejected' ($tampered.code -eq 1058) $tampered
$saved=Api "$root/$ref/execute" $credentials;$retry=Api "$root/$ref/execute" $credentials
Assert 'H05-save-idempotent' ($saved.code -eq 200 -and $saved.data.saved -and $retry.data.replayed -and $saved.data.assayId -eq $retry.data.assayId) @{First=$saved;Retry=$retry}
$result=Api "$root/$ref/result" $null 'Get'
Assert 'H06-result-readback' ($result.data.assayId -eq $saved.data.assayId) $result
$old=(Api $root $input).data.previewRef;$next=(Api $root $input).data.previewRef
$stale=Api "$root/$old/confirm"
Assert 'H07-replaced-preview-rejected' ($stale.code -in @(1054,1058)) $stale
Api "$root/$next/cancel"|Out-Null
$canceled=Api "$root/$next/confirm"
Assert 'H08-canceled-preview-rejected' ($canceled.code -in @(1054,1058)) $canceled
$count=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id=$id;")[0]
Assert 'H09-database-single-record' ($count -eq 1) @{Count=$count;Rows=(Sql "SELECT version,color_value,ph_value,dry_weight FROM assay WHERE product_id=$id;")}
'HTTP acceptance: 9 PASS. Fixture kept for browser acceptance; run Cleanup afterward.'
