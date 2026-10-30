param(
    [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
    [switch]$Execute
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'inspect-assay-acceptance-fixtures.ps1') -EnvFile $EnvFile | Out-Null
if(-not $?){throw 'Read-only preflight failed'}
$fixturePath=Join-Path $root 'output/assay-group-live-20260924/fixture.json'
$accountPath=Join-Path $root 'output/assay-d4-permissions/fixture.json'
$f=Get-Content -Raw -Encoding utf8 $fixturePath|ConvertFrom-Json
$a=Get-Content -Raw -Encoding utf8 $accountPath|ConvertFrom-Json
$cfg=@{}
foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?;]+)'){throw 'Local database only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$db=$Matches[3]
$mysql='C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
$dump='C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe'
function Sql($q){
    $old=$env:MYSQL_PWD;$env:MYSQL_PWD=$cfg.DB_PASSWORD
    try{$rows=@(& $mysql --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$db --default-character-set=utf8mb4 --batch --skip-column-names --execute=$q);if($LASTEXITCODE){throw 'SQL failed; transaction connection closed without commit'};return ,$rows}
    finally{$env:MYSQL_PWD=$old}
}
function Count($q){$r=Sql $q;if($r.Count -ne 1 -or $r[0] -notmatch '^\d+$'){throw 'Missing count'};return [int]$r[0]}
function Ids($q){$r=Sql $q;foreach($id in $r){if($id -notmatch '^\d+$'){throw 'Invalid ID'}};return ,$r}
$targets=[ordered]@{}
$predicates=@{}
function Target($table,$predicate){
    $targets[$table]=Ids "SELECT id FROM $table WHERE $predicate ORDER BY id"
    $predicates[$table]=$predicate
}
function InIds($table){if($targets[$table].Count){return ($targets[$table] -join ',')};return '0'}
Target 'product' "id IN ($($f.ProductIds -join ',')) AND product_name IN ('$($f.Prefix)甲糖','$($f.Prefix)乙糖')"
Target 'quality_standards' "id IN ($($f.StandardIds -join ',')) AND standard_code IN ('$($f.Marker)_0','$($f.Marker)_1')"
Target 'assay_group' "id IN ($($f.GroupIds -join ',')) AND standard_name IN ('$($f.Prefix)甲组','$($f.Prefix)乙组')"
Target 'user' "name=employee_id AND name IN ('$($a.Tag)_QC','$($a.Tag)_STAFF')"
Target 'employee_roster' "name=employee_id AND employee_id IN ('$($a.Tag)_QC','$($a.Tag)_STAFF')"
Target 'role' "role_code IN ('$($a.Tag)_Q','$($a.Tag)_S') AND description='Local assay D4 permission acceptance'"
Target 'assay' "product_id IN ($(InIds 'product'))"
Target 'product_quality_standard_relation' "product_id IN ($(InIds 'product')) AND quality_standard_id IN ($(InIds 'quality_standards'))"
Target 'quality_standard_item' "quality_standard_id IN ($(InIds 'quality_standards'))"
Target 'role_permission' "role_id IN ($(InIds 'role'))"
Target 'agent_assay_entry_preview' "JSON_EXTRACT(normalized_input_json,'$.productId') IN ($(InIds 'product')) OR JSON_EXTRACT(normalized_input_json,'$.request.groupId') IN ($(InIds 'assay_group'))"
$previewRefs="SELECT preview_ref FROM agent_assay_entry_preview WHERE id IN ($(InIds 'agent_assay_entry_preview'))"
Target 'agent_assay_entry_confirmation' "preview_ref IN ($previewRefs)"
Target 'agent_assay_entry_audit' "preview_ref IN ($previewRefs) OR owner_user_id IN ($(InIds 'user')) OR assay_id IN ($(InIds 'assay'))"
foreach($table in @('product','quality_standards','assay_group','user','employee_roster','role')){if($targets[$table].Count -ne 2){throw "Identity mismatch: $table"}}
if($targets.assay.Count -ne 11){throw 'Unexpected assay count; review before cleanup'}
$tableNames=($targets.Keys|ForEach-Object {"'$_'"}) -join ','
if((Count "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ($tableNames) AND ENGINE='InnoDB'") -ne $targets.Count){throw 'All target tables must support transaction rollback'}

# Verify every actual FK, including fixed_product_id and applied_standard_id.
$fk=Sql "SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL"
$externalChecks=[Collections.Generic.List[string]]::new()
foreach($line in $fk){
    $table,$column,$parent=$line -split "`t"
    if(-not $targets.Contains($parent)){continue}
    foreach($identifier in @($table,$column,$parent)){if($identifier -notmatch '^[a-z_]+$'){throw 'Unexpected SQL identifier'}}
    $exclude=if($targets.Contains($table)){" AND id NOT IN ($(InIds $table))"}else{''}
    $query="SELECT COUNT(*) FROM $table WHERE $column IN ($(InIds $parent))$exclude"
    if((Count $query) -ne 0){throw "External dependency: $table.$column"}
    $externalChecks.Add($query)
}
$guardQueries=@(
    "SELECT COUNT(*) FROM user WHERE role_code IN ('$($a.Tag)_Q','$($a.Tag)_S') AND id NOT IN ($(InIds 'user'))",
    "SELECT COUNT(*) FROM employee_roster WHERE role_code IN ('$($a.Tag)_Q','$($a.Tag)_S') AND id NOT IN ($(InIds 'employee_roster'))",
    "SELECT COUNT(*) FROM assay_group WHERE id NOT IN ($(InIds 'assay_group')) AND (FIND_IN_SET('$($f.ProductIds[0])',related_products) OR FIND_IN_SET('$($f.ProductIds[1])',related_products))",
    "SELECT COUNT(*) FROM assay_group WHERE (id=$($f.GroupIds[0]) AND related_products<>'$($f.ProductIds -join ',')') OR (id=$($f.GroupIds[1]) AND related_products<>'$($f.ProductIds[0])')"
)
foreach($q in $guardQueries){if((Count $q) -ne 0){throw 'External logical dependency'};$externalChecks.Add($q)}
$counts=[ordered]@{};foreach($table in $targets.Keys){$counts[$table]=$targets[$table].Count}
if(-not $Execute){@{Mode='Plan';Counts=$counts;NoWrites=$true}|ConvertTo-Json -Depth 5;exit}
$out=Join-Path $root ('output/assay-cleanup/'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
[IO.Directory]::CreateDirectory($out)|Out-Null
function Save($name,$value){[IO.File]::WriteAllText((Join-Path $out $name),($value|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))}
function DumpRows($table,$predicate,$path){
    $old=$env:MYSQL_PWD;$env:MYSQL_PWD=$cfg.DB_PASSWORD
    try{& $dump --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --default-character-set=utf8mb4 --no-create-info --skip-comments --compact --complete-insert --skip-extended-insert --skip-add-locks --skip-lock-tables --order-by-primary --set-gtid-purged=OFF --no-tablespaces "--where=$predicate" "--result-file=$path" $db $table;if($LASTEXITCODE){throw 'Backup failed'} }
    finally{$env:MYSQL_PWD=$old}
}
$hashes=[ordered]@{}
foreach($table in $targets.Keys){
    DumpRows $table "id IN ($(InIds $table))" (Join-Path $out "$table-target.sql")
    $path=Join-Path $out "$table-remaining-before.sql"
    DumpRows $table "id NOT IN ($(InIds $table))" $path
    $hashes[$table]=(Get-FileHash $path -Algorithm SHA256).Hash
}
# Also preserve whole operational tables which this cleanup must never touch.
$protected=@('inventory','in_stock','out_stock','pallet_code','pallet_task','pallet_flow_record','production_order','production_daily_report','production_daily_product_line')
foreach($table in $protected){$path=Join-Path $out "$table-before.sql";DumpRows $table '1=1' $path;$hashes[$table]=(Get-FileHash $path -Algorithm SHA256).Hash}
Save 'plan.json' @{Counts=$counts;Ids=$targets;UnaffectedHashes=$hashes;Authorization='User explicitly authorized thread test data and accounts cleanup';BackupContainsLocalTestAndExistingRows=$true}
$sql=[Collections.Generic.List[string]]::new()
$sql.Add('CREATE TEMPORARY TABLE cleanup_assertion (id INT PRIMARY KEY)')
$sql.Add('INSERT INTO cleanup_assertion VALUES (1)')
$sql.Add('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE')
$sql.Add('START TRANSACTION')
foreach($table in $targets.Keys){
    $sql.Add("SELECT id FROM $table WHERE id IN ($(InIds $table)) ORDER BY id FOR UPDATE")
    $sql.Add("INSERT INTO cleanup_assertion SELECT 1 WHERE (SELECT COUNT(*) FROM $table WHERE $($predicates[$table])) <> $($targets[$table].Count)")
}
foreach($q in $externalChecks){$sql.Add("INSERT INTO cleanup_assertion SELECT 1 WHERE ($q) <> 0")}
$sql.Add("INSERT INTO cleanup_assertion SELECT 1 WHERE (SELECT COUNT(*) FROM agent_assay_entry_preview WHERE id IN ($(InIds 'agent_assay_entry_preview')) AND status IN ('READY','CONFIRMED') AND expires_at>NOW()) <> 0")
$order=@('agent_assay_entry_audit','agent_assay_entry_confirmation','agent_assay_entry_preview','assay','product_quality_standard_relation','assay_group','quality_standard_item','quality_standards','product','role_permission','user','employee_roster','role')
foreach($table in $order){
    $sql.Add("DELETE FROM $table WHERE id IN ($(InIds $table))")
    $sql.Add("INSERT INTO cleanup_assertion SELECT 1 WHERE ROW_COUNT() <> $($targets[$table].Count)")
}
$sql.Add('COMMIT')
Sql (($sql -join ';')+';')|Out-Null
$remaining=[ordered]@{};$unchanged=[ordered]@{}
foreach($table in $targets.Keys){
    $remaining[$table]=Count "SELECT COUNT(*) FROM $table WHERE id IN ($(InIds $table))"
    $path=Join-Path $out "$table-remaining-after.sql";DumpRows $table "id NOT IN ($(InIds $table))" $path
    $unchanged[$table]=(Get-FileHash $path -Algorithm SHA256).Hash -eq $hashes[$table]
}
foreach($table in $protected){$path=Join-Path $out "$table-after.sql";DumpRows $table '1=1' $path;$unchanged[$table]=(Get-FileHash $path -Algorithm SHA256).Hash -eq $hashes[$table]}
$ok=@($remaining.Values|Where-Object {$_ -ne 0}).Count -eq 0 -and @($unchanged.Values|Where-Object {-not $_}).Count -eq 0
Save 'result.json' @{Pass=$ok;DeletedCounts=$counts;Remaining=$remaining;UnaffectedRowsUnchanged=$unchanged;EvidenceDirectory=$out;HistoricalSessionsReportsAndGeneralAuditRetained=$true}
if(-not $ok){throw "Post-cleanup verification failed; inspect $out; do not replay"}
foreach($path in @($fixturePath,$accountPath)){
    $manifest=Get-Content -Raw -Encoding utf8 $path|ConvertFrom-Json
    $manifest.Cleaned=$true
    $manifest|Add-Member -NotePropertyName CleanupEvidence -NotePropertyValue $out -Force
    [IO.File]::WriteAllText($path,($manifest|ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
}
@{Pass=$ok;DeletedCounts=$counts;EvidenceDirectory=$out;NonTestRowsUnchanged=$true}|ConvertTo-Json -Depth 6
