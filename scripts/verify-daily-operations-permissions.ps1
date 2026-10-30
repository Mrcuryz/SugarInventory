param(
    [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
    [string]$BaseUrl='http://127.0.0.1:38083',
    [switch]$VerifyRevocation
)
$ErrorActionPreference='Stop'
if(([uri]$BaseUrl).Host -notin @('127.0.0.1','localhost')){throw 'Local acceptance only'}
$cfg=@{}
foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?]+)'){throw 'Local database only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$dbName=$Matches[3]
function Sql($query){
    $previous=$env:MYSQL_PWD;$env:MYSQL_PWD=$cfg.DB_PASSWORD
    try{
        $rows=@($query | & 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$dbName --default-character-set=utf8mb4 --batch --skip-column-names)
        if($LASTEXITCODE){throw 'Local permission fixture SQL failed'}
        return ,$rows
    }finally{if($null -eq $previous){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previous}}
}
function Api($path,$body=$null,$token='',$method=''){
    $p=@{Uri="$BaseUrl$path";Headers=@{};TimeoutSec=180;SkipHttpErrorCheck=$true;StatusCodeVariable='http'}
    if($token){$p.Headers.Authorization="Bearer $token"}
    if($null -ne $body){$p.Method='Post';$p.ContentType='application/json; charset=utf-8';$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 15))}
    if($method){$p.Method=$method}
    $r=Invoke-RestMethod @p
    @{Http=$http;Body=$r}
}
function Ok($r){$r.Http -eq 200 -and $r.Body.code -eq 200}
function Denied($r){$r.Http -eq 403 -or $r.Body.code -eq 403}
function Assert($condition,$message){if(-not $condition){throw $message}}
$tag='DOP_'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $PSScriptRoot "../output/daily-operations-permissions/$tag"
[IO.Directory]::CreateDirectory($out)|Out-Null
function Save($name,$value){[IO.File]::WriteAllText((Join-Path $out "$name.json"),($value|ConvertTo-Json -Depth 80),[Text.UTF8Encoding]::new($false))}
$day=[DateTimeOffset]::Now.ToOffset([TimeSpan]::FromHours(8)).ToString('yyyy-MM-dd')
$roles=@(
    @{Kind='daily-only';Suffix='D';Permissions=@('production:daily-report:view');Daily=$true;Operations=$false},
    @{Kind='operations-only';Suffix='O';Permissions=@('production:order:view','production:material:view','assay:view','inventory:view','task:view');Daily=$false;Operations=$true},
    @{Kind='neither';Suffix='N';Permissions=@();Daily=$false;Operations=$false}
)
$created=[Collections.Generic.List[string]]::new()
$evidence=[Collections.Generic.List[object]]::new()
try{
    foreach($role in $roles){
        $code=$tag+'_'+$role.Suffix
        $perms=@('system:access','agent:use')+$role.Permissions
        $in=($perms|ForEach-Object{"'$_'"}) -join ','
        Assert ([long](Sql "SELECT COUNT(*) FROM permission WHERE perm_code IN ($in);")[0] -eq $perms.Count) 'Required permission definition missing'
        Assert ([long](Sql "SELECT (SELECT COUNT(*) FROM role WHERE role_code='$code')+(SELECT COUNT(*) FROM employee_roster WHERE employee_id='$code')+(SELECT COUNT(*) FROM user WHERE name='$code');")[0] -eq 0) 'Fixture identity collision'
        $created.Add($code)
        $mobile='19'+(Get-Random -Minimum 100000000 -Maximum 999999999)
        Sql "START TRANSACTION; INSERT INTO role(role_name,role_code,description,status) VALUES('$code','$code','Local daily operations permission acceptance','ENABLED'); INSERT INTO role_permission(role_id,permission_id) SELECT r.id,p.id FROM role r JOIN permission p ON p.perm_code IN ($in) WHERE r.role_code='$code'; INSERT INTO employee_roster(employee_id,name,mobile,department,position,status,role_code) VALUES('$code','$code','$mobile','本地验收','权限验收','在职','$code'); COMMIT;"|Out-Null
        $login=Api '/api/auth/web-login' @{name=$code;password='lbsp-isolated-uat'}
        Assert (Ok $login) 'Fixture login failed'
        $token=$login.Body.data.token
        $info=Api '/api/user/info' $null $token
        Assert (Ok $info) 'Fixture identity read failed'
        Save ($role.Kind+'-permissions') $info.Body.data.permissionCodes
        $daily=Api '/api/production/agent-read/daily-reports/query' @{startDate=$day;endDate=$day;page=1;size=20} $token
        Assert ($(if($role.Daily){Ok $daily}else{Denied $daily})) 'Daily permission boundary failed'
        $ops=Api '/api/analytics/agent-read/reports/run' @{reportDefinitionId='today_operations_overview_v1';reportVersion=1;startDate=$day;endDate=$day} $token
        Assert ($(if($role.Operations){Ok $ops}else{Denied $ops})) 'Operations permission boundary failed'
        if($role.Daily){
            $report=Api '/api/analytics/agent-read/reports/run' @{reportDefinitionId='production_daily_report_analysis_v1';reportVersion=1;startDate=$day;endDate=$day} $token
            Assert (Ok $report) 'Daily-only role cannot run daily analysis'
            $id=$report.Body.data.reportRunId
            Assert (-not [string]::IsNullOrWhiteSpace($id)) 'Missing report id'
            $history=Api "/api/analytics/agent-read/reports/$id" $null $token
            $export=Api "/api/analytics/agent-read/reports/$id/export.xlsx" $null $token
            Assert ((Ok $history) -and (Denied $export)) 'View/export isolation failed'
            Save 'daily-only-view-export' @{Run=$report;History=$history;Export=$export}
        }
        $session=Api '/api/agent/sessions' @{clientType='WEB';requestedScopes=@('mcp:warehouse:read')} $token
        Assert (Ok $session) 'Agent session creation failed'
        $sid=$session.Body.data.agentSessionId
        try{
            $answer=Api "/api/agent/sessions/$sid/messages" @{message='今天整体运营情况如何？'} $token
            Save ($role.Kind+'-agent') @{Input='今天整体运营情况如何？';Response=$answer;DailyDirect=$daily;OperationsDirect=$ops}
            Assert (Ok $answer) 'Agent response transport failed'
            Assert ($answer.Body.data.answer -notmatch 'TOOL_ERROR|PERMISSION_DENIED') 'Internal error codes leaked to answer'
            if(-not $role.Daily -and -not $role.Operations){
                Assert ($answer.Body.data.answer -match '权限' -and $answer.Body.data.answer -notmatch '稍后重试') 'Permission denial must not be presented as a transient failure'
            }
            $cards=@($answer.Body.data.cards)
            Assert ((@($cards|Where-Object cardType -eq 'production_daily_records').Count -eq $(if($role.Daily){1}else{0})) -and (@($cards|Where-Object cardType -eq 'daily_operations_business_facts').Count -eq $(if($role.Operations){1}else{0}))) 'Agent exposed forbidden data or omitted allowed data'
            $evidence.Add(@{Role=$role.Kind;Result='PASS';Answer=$answer.Body.data.answer})
            Save 'summary' $evidence
        }finally{Api "/api/agent/sessions/$sid" $null $token 'Delete'|Out-Null}
        if($VerifyRevocation -and $role.Daily){
            # 仅撤销本轮临时角色的日报查看权限；保留原登录token，验证逐请求复核。
            Sql "DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE r.role_code='$code' AND r.description='Local daily operations permission acceptance' AND p.perm_code='production:daily-report:view';"|Out-Null
            $revokedHistory=Api "/api/analytics/agent-read/reports/$id" $null $token
            $revokedExport=Api "/api/analytics/agent-read/reports/$id/export.xlsx" $null $token
            Save 'daily-only-revoked' @{History=$revokedHistory;Export=$revokedExport;UsedOriginalLoginToken=$true}
            Assert ((Denied $revokedHistory) -and (Denied $revokedExport)) 'Revoked view permission still exposes archived report'
            $evidence.Add(@{Role='daily-only-after-revocation';Result='PASS';Answer='原登录凭证无法读取或导出已归档日报分析'})
            Save 'summary' $evidence
        }
    }
}finally{
    foreach($code in $created){
        # Only identities created by this invocation; no existing account or role is changed.
        Sql "START TRANSACTION; DELETE FROM agent_session WHERE user_id IN (SELECT id FROM user WHERE employee_id='$code' AND role_code='$code'); DELETE FROM user WHERE employee_id='$code' AND role_code='$code'; DELETE FROM employee_roster WHERE employee_id='$code' AND role_code='$code'; DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id WHERE r.role_code='$code'; DELETE FROM role WHERE role_code='$code' AND description='Local daily operations permission acceptance'; COMMIT;"|Out-Null
        Assert ([long](Sql "SELECT (SELECT COUNT(*) FROM role WHERE role_code='$code')+(SELECT COUNT(*) FROM employee_roster WHERE employee_id='$code')+(SELECT COUNT(*) FROM user WHERE employee_id='$code');")[0] -eq 0) 'Fixture cleanup incomplete'
    }
    Save 'cleanup' @{Identities=$created;Remaining=0}
}
$evidence|ConvertTo-Json -Depth 5
