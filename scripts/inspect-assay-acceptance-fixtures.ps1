param([string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env')
$ErrorActionPreference='Stop'
# Read-only preflight. No cleanup mode, business API calls, or mutating SQL.
$root=Split-Path $PSScriptRoot -Parent
$f=Get-Content -Raw -Encoding utf8 (Join-Path $root 'output/assay-group-live-20260924/fixture.json')|ConvertFrom-Json
$a=Get-Content -Raw -Encoding utf8 (Join-Path $root 'output/assay-d4-permissions/fixture.json')|ConvertFrom-Json
if($f.Cleaned -or $f.Marker -notmatch '^UAT_AGLIVE_[a-f0-9]{32}$' -or $f.Prefix -notmatch '^化验组验收[0-9]{10}$' -or $a.Tag -notmatch '^AD4_[a-f0-9]{8}$'){throw 'Invalid fixture manifest'}
$ids=($f.ProductIds|ForEach-Object {[int]$_}) -join ','
$groups=($f.GroupIds|ForEach-Object {[int]$_}) -join ','
$standards=($f.StandardIds|ForEach-Object {[int]$_}) -join ','
$cfg=@{}
foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?;]+)'){throw 'Local database only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$db=$Matches[3]
function Sql([string]$q){
    if($q -notmatch '^SELECT\s' -or $q.Contains(';')){throw 'Only a single SELECT is allowed'}
    $old=$env:MYSQL_PWD;$env:MYSQL_PWD=$cfg.DB_PASSWORD
    try{
        $rows=@(& 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$db --default-character-set=utf8mb4 --batch --skip-column-names --execute=$q)
        if($LASTEXITCODE){throw 'Read-only SQL failed'}
        return ,$rows
    }finally{$env:MYSQL_PWD=$old}
}
function Count([string]$q){$r=Sql $q;if($r.Count -ne 1 -or $r[0] -notmatch '^\d+$'){throw 'Missing count; cannot assume zero'};return [int]$r[0]}
$identity=@{
    Products=Count "SELECT COUNT(*) FROM product WHERE id IN ($ids) AND product_name IN ('$($f.Prefix)甲糖','$($f.Prefix)乙糖')"
    Standards=Count "SELECT COUNT(*) FROM quality_standards WHERE id IN ($standards) AND standard_code IN ('$($f.Marker)_0','$($f.Marker)_1')"
    Groups=Count "SELECT COUNT(*) FROM assay_group WHERE id IN ($groups) AND standard_name IN ('$($f.Prefix)甲组','$($f.Prefix)乙组')"
    Accounts=Count "SELECT COUNT(*) FROM user WHERE name=employee_id AND name IN ('$($a.Tag)_QC','$($a.Tag)_STAFF') AND role_code IN ('$($a.Tag)_Q','$($a.Tag)_S')"
    Roles=Count "SELECT COUNT(*) FROM role WHERE role_code IN ('$($a.Tag)_Q','$($a.Tag)_S') AND description='Local assay D4 permission acceptance'"
}
if(@($identity.Values|Where-Object {$_ -ne 2}).Count){throw 'Fixture identity mismatch'}
$references=[Collections.Generic.List[object]]::new()
$columns=Sql "SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND COLUMN_NAME IN ('product_id','assay_id') ORDER BY TABLE_NAME,COLUMN_NAME"
foreach($line in $columns){
    $table,$column=$line -split "`t"
    if($table -notmatch '^[a-z_]+$' -or $column -notmatch '^[a-z_]+$'){throw 'Unexpected SQL identifier'}
    $filter=if($column -eq 'product_id'){"$column IN ($ids)"}else{"$column IN (SELECT id FROM assay WHERE product_id IN ($ids))"}
    $count=Count "SELECT COUNT(*) FROM $table WHERE $filter"
    $references.Add(@{Table=$table;Column=$column;Count=$count})
}
$predicate="JSON_EXTRACT(p.normalized_input_json,'$.productId') IN ($ids) OR JSON_EXTRACT(p.normalized_input_json,'$.request.groupId') IN ($groups)"
$controls=@{
    Previews=Count "SELECT COUNT(*) FROM agent_assay_entry_preview p WHERE $predicate"
    Confirmations=Count "SELECT COUNT(*) FROM agent_assay_entry_confirmation c JOIN agent_assay_entry_preview p ON p.preview_ref=c.preview_ref WHERE $predicate"
    Audits=Count "SELECT COUNT(*) FROM agent_assay_entry_audit a JOIN agent_assay_entry_preview p ON p.preview_ref=a.preview_ref WHERE $predicate"
    ActivePreviews=Count "SELECT COUNT(*) FROM agent_assay_entry_preview p WHERE ($predicate) AND p.status IN ('READY','CONFIRMED') AND p.expires_at>NOW()"
}
$external=@{
    OtherProductStandardRelations=Count "SELECT COUNT(*) FROM product_quality_standard_relation WHERE quality_standard_id IN ($standards) AND product_id NOT IN ($ids)"
    OtherRoleUsers=Count "SELECT COUNT(*) FROM user WHERE role_code IN ('$($a.Tag)_Q','$($a.Tag)_S') AND name NOT IN ('$($a.Tag)_QC','$($a.Tag)_STAFF')"
    OtherRoleRoster=Count "SELECT COUNT(*) FROM employee_roster WHERE role_code IN ('$($a.Tag)_Q','$($a.Tag)_S') AND employee_id NOT IN ('$($a.Tag)_QC','$($a.Tag)_STAFF')"
    OtherGroups=Count "SELECT COUNT(*) FROM assay_group WHERE id NOT IN ($groups) AND (FIND_IN_SET('$($f.ProductIds[0])',related_products) OR FIND_IN_SET('$($f.ProductIds[1])',related_products))"
}
$allowed=@('assay','product_quality_standard_relation','agent_assay_entry_audit','agent_assay_entry_confirmation')
$unexpected=@($references|Where-Object {$_.Count -gt 0 -and $_.Table -notin $allowed})
$result=@{ReadOnly=$true;InspectedAt=(Get-Date -Format o);Identity=$identity;References=$references;Controls=$controls;ExternalReferences=$external;UnexpectedBusinessReferences=$unexpected;ClearanceRequired=$true;CleanupExecuted=$false}
$dir=Join-Path $root 'output/assay-fixture-preflight';[IO.Directory]::CreateDirectory($dir)|Out-Null
[IO.File]::WriteAllText((Join-Path $dir 'inspection.json'),($result|ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
$result|ConvertTo-Json -Depth 10
if($unexpected.Count -or @($external.Values|Where-Object {$_ -gt 0}).Count -or $controls.ActivePreviews){throw 'External or active references require review; do not clean'}
