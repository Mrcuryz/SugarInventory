param(
    [ValidateSet('Prepare','Http','Revoke','Restore','Inspect','VerifyDoubleClick')][string]$Mode='Http',
    [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
    [string]$BaseUrl='http://127.0.0.1:38083'
)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('localhost','127.0.0.1')){throw 'Local acceptance only'}
$cfg=@{}
foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?;]+)'){throw 'Local DB only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$dbName=$Matches[3]
function Sql($query){
    $old=$env:MYSQL_PWD;$env:MYSQL_PWD=$cfg.DB_PASSWORD
    try {$rows=@($query|& 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$dbName --default-character-set=utf8mb4 --batch --skip-column-names);if($LASTEXITCODE){throw 'Fixture SQL failed'};return ,$rows}
    finally {$env:MYSQL_PWD=$old}
}
$out=Join-Path $PSScriptRoot '../output/assay-d4-permissions'
[IO.Directory]::CreateDirectory($out)|Out-Null
$manifest=Join-Path $out 'fixture.json'
function Save($name,$value){
    $json=$value|ConvertTo-Json -Depth 40
    $json=[regex]::Replace($json,'("(?:token|productRef|groupRef|previewRef|executionToken|idempotencyKey)"\s*:\s*)"[^"]*"','$1"[redacted]"')
    [IO.File]::WriteAllText((Join-Path $out "$name.json"),$json,[Text.UTF8Encoding]::new($false))
}
function Api($path,$body,$token,$method='Post'){
    $p=@{Uri="$BaseUrl$path";Method=$method;Headers=@{};TimeoutSec=120;SkipHttpErrorCheck=$true;StatusCodeVariable='status'}
    if($token){$p.Headers.Authorization="Bearer $token"}
    if($null -ne $body){$p.ContentType='application/json; charset=utf-8';$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 20))}
    $r=Invoke-RestMethod @p;@{Http=$status;Body=$r}
}
function Assert($name,$pass,$evidence){Save $name @{Pass=[bool]$pass;Evidence=$evidence};if(-not $pass){throw "$name FAILED"};"$name PASS"}
function Denied($r){$r.Http -eq 403 -or $r.Body.code -in @(403,1056)}
if($Mode -eq 'Prepare'){
    if(Test-Path $manifest){throw 'Existing permission fixture; reuse it'}
    $tag='AD4_'+[Guid]::NewGuid().ToString('N').Substring(0,8)
    $f=@{Tag=$tag;WriteRole=$tag+'_Q';ReadRole=$tag+'_S';QcName=$tag+'_QC';StaffName=$tag+'_STAFF';Description='Local assay D4 permission acceptance';Cleaned=$false}
    Save 'fixture' $f
    foreach($kind in @('Q','S')){
        $role=if($kind -eq 'Q'){$f.WriteRole}else{$f.ReadRole};$name=if($kind -eq 'Q'){$f.QcName}else{$f.StaffName}
        $codes=@('system:access','agent:use','assay:view','product:view')
        if($kind -eq 'Q'){$codes+=@('quality:test')}
        $in=($codes|ForEach-Object {"'$_'"}) -join ','
        if([int](Sql "SELECT COUNT(*) FROM permission WHERE perm_code IN ($in);")[0] -ne $codes.Count){throw 'Missing permission definitions'}
        if([int](Sql "SELECT (SELECT COUNT(*) FROM role WHERE role_code='$role')+(SELECT COUNT(*) FROM employee_roster WHERE employee_id='$name')+(SELECT COUNT(*) FROM user WHERE name='$name');")[0] -ne 0){throw 'Fixture collision'}
        $mobile='19'+(Get-Random -Minimum 100000000 -Maximum 999999999)
        Sql "START TRANSACTION; INSERT INTO role(role_name,role_code,description,status) VALUES('$role','$role','$($f.Description)','ENABLED'); INSERT INTO role_permission(role_id,permission_id) SELECT r.id,p.id FROM role r JOIN permission p ON p.perm_code IN ($in) WHERE r.role_code='$role'; INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$name','$name','$mobile','本地验收','化验权限验收','在职','$role'); COMMIT;"|Out-Null
        $login=Api '/api/auth/web-login' @{name=$name;password='lbsp-isolated-uat'} ''
        if($login.Body.code -ne 200 -or -not $login.Body.data.token){throw 'Fixture login failed'}
    }
    $f|ConvertTo-Json;exit
}
if(-not(Test-Path $manifest)){throw 'Prepare first'}
$f=Get-Content -Encoding utf8 -Raw $manifest|ConvertFrom-Json
if($f.Tag -notmatch '^AD4_[a-f0-9]{8}$' -or $f.WriteRole -ne "$($f.Tag)_Q" -or $f.ReadRole -ne "$($f.Tag)_S" -or $f.QcName -ne "$($f.Tag)_QC" -or $f.StaffName -ne "$($f.Tag)_STAFF"){throw 'Invalid fixture identity'}
if([int](Sql "SELECT COUNT(*) FROM role WHERE role_code IN ('$($f.WriteRole)','$($f.ReadRole)') AND description='Local assay D4 permission acceptance';")[0] -ne 2){throw 'Fixture role identity mismatch'}
function SetQcRole($role){
    if($role -notin @($f.WriteRole,$f.ReadRole)){throw 'Invalid role'}
    $name=$f.QcName
    if([int](Sql "SELECT COUNT(*) FROM user WHERE name='$name' AND employee_id='$name' AND role_code IN ('$($f.WriteRole)','$($f.ReadRole)');")[0] -ne 1){throw 'Fixture account identity mismatch'}
    Sql "START TRANSACTION; UPDATE user SET role_code='$role' WHERE name='$name' AND employee_id='$name'; UPDATE employee_roster SET role_code='$role' WHERE employee_id='$name' AND name='$name'; COMMIT;"|Out-Null
}
if($Mode -eq 'Revoke'){SetQcRole $f.ReadRole;'Exclusive QC write authority revoked';exit}
if($Mode -eq 'Restore'){SetQcRole $f.WriteRole;'Exclusive QC write authority restored';exit}
if($Mode -eq 'Inspect'){Sql "SELECT name,role_code FROM user WHERE employee_id IN ('$($f.QcName)','$($f.StaffName)');";exit}
$products=Get-Content -Encoding utf8 -Raw (Join-Path $PSScriptRoot '../output/assay-group-live-20260924/fixture.json')|ConvertFrom-Json
if($products.Cleaned -or $products.Marker -notmatch '^UAT_AGLIVE_[a-f0-9]{32}$'){throw 'Assay fixture unavailable'}
$ids=($products.ProductIds|ForEach-Object {[int]$_}) -join ','
if($Mode -eq 'VerifyDoubleClick'){
    $rows=Sql "SELECT product_id,version,color_value,ph_value,dry_weight,judge_result FROM assay WHERE product_id IN ($ids) AND sample_date='2026-09-25' ORDER BY product_id,version;"
    $expected=@("$($products.ProductIds[0])`t1`t10.00`t0.00`tNULL`tPASS", "$($products.ProductIds[0])`t2`t20.00`t0.00`tNULL`tPASS", "$($products.ProductIds[1])`t1`t10.00`t0.00`tNULL`tFAIL")
    Assert 'P08-doubleclick-one-version' (($rows -join '|') -eq ($expected -join '|')) @{Rows=$rows;Expected=$expected;Scope='Exclusive two products on 2026-09-25'}
    exit
}
$before=[int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0]
$qc=(Api '/api/auth/web-login' @{name=$f.QcName;password='lbsp-isolated-uat'} '').Body.data.token
$staff=(Api '/api/auth/web-login' @{name=$f.StaffName;password='lbsp-isolated-uat'} '').Body.data.token
if(-not $qc -or -not $staff){throw 'Login failed'}
$sessions=@();$root=$null;$ref=$null
try {
    $s=(Api '/api/agent/sessions' @{} $qc).Body.data.agentSessionId;$sessions+=@(@{Id=$s;Token=$qc})
    $ss=(Api '/api/agent/sessions' @{} $staff).Body.data.agentSessionId;$sessions+=@(@{Id=$ss;Token=$staff})
    if(-not $s -or -not $ss){throw 'Session missing'}
    $query=@{productId=[int]$products.ProductIds[1];productionDate='2026-09-25'}
    $read=Api "/api/assay/by-product-date?productId=$($query.productId)&productionDate=2026-09-25" $null $staff 'Get'
    Assert 'P01-staff-read' ($read.Body.code -eq 200) $read
    $input=@{productId=[int]$products.ProductIds[0];sampleDate='2026-09-25';colorValue=20;phValue=0}
    $denied=Api "/api/agent/sessions/$ss/assay-entry/previews" $input $staff
    Assert 'P02-staff-preview-denied' (Denied $denied) $denied
    $root="/api/agent/sessions/$s/assay-entry/previews"
    $preview=Api $root $input $qc;$ref=$preview.Body.data.previewRef
    Assert 'P03-qc-preview' ($preview.Body.code -eq 200 -and $ref) $preview
    $confirmation=Api "$root/$ref/confirm" $null $qc
    Assert 'P04-qc-confirm' ($confirmation.Body.code -eq 200) $confirmation
    SetQcRole $f.ReadRole
    $execute=Api "$root/$ref/execute" @{executionToken=$confirmation.Body.data.executionToken;idempotencyKey=$confirmation.Body.data.idempotencyKey} $qc
    Assert 'P05-revoked-execute' (Denied $execute) @{Response=$execute;UsedOriginalLoginToken=$true}
    $recover=Api "$root/$ref/recover" $null $qc
    Assert 'P06-revoked-recovery' (Denied $recover) $recover
    Assert 'P07-no-assay-write' ([int](Sql "SELECT COUNT(*) FROM assay WHERE product_id IN ($ids);")[0] -eq $before) @{Before=$before}
} finally {
    SetQcRole $f.WriteRole
    if($root -and $ref){Api "$root/$ref/cancel" $null $qc|Out-Null}
    foreach($session in $sessions){if($session.Id){Api "/api/agent/sessions/$($session.Id)" $null $session.Token 'Delete'|Out-Null}}
}
