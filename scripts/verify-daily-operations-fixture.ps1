param(
    [Parameter(Mandatory=$true)][ValidateSet('Prepare','Cleanup')][string]$Mode,
    [string]$EnvFile='D:/Users/Mrcury/Desktop/laibin-shadow-model.env',
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$BusinessDate=([DateTimeOffset]::Now.ToOffset([TimeSpan]::FromHours(8)).ToString('yyyy-MM-dd'))
)
$ErrorActionPreference='Stop'
[DateTime]::ParseExact($BusinessDate,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)|Out-Null
$marker='UAT_DAILY_OPS_C3_'+$BusinessDate.Replace('-','')
$out=Join-Path $PSScriptRoot '../output/daily-operations-fixture'
[IO.Directory]::CreateDirectory($out)|Out-Null
$manifestPath=Join-Path $out "$BusinessDate-manifest.json"
$cfg=@{}
foreach($line in [IO.File]::ReadAllLines($EnvFile)){if($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(?::(\d+))?/([^?]+)'){throw 'Local database only'}
$dbHost=$Matches[1];$dbPort=if($Matches[2]){$Matches[2]}else{'3306'};$dbName=$Matches[3]
function Sql($query){
    $previous=$env:MYSQL_PWD
    $env:MYSQL_PWD=$cfg.DB_PASSWORD
    try{
        $rows=@($query | & 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=$dbHost --port=$dbPort --user=$($cfg.DB_USERNAME) --database=$dbName --default-character-set=utf8mb4 --batch --skip-column-names)
        if($LASTEXITCODE){throw 'Fixture SQL failed'}
        return ,$rows
    }finally{if($null -eq $previous){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previous}}
}
function Fingerprint($id){
    # Complete rows protect against deleting a fixture that a person later edited.
    $rows=Sql "SELECT * FROM production_daily_report WHERE id=$id; SELECT * FROM production_daily_report_section WHERE report_id=$id ORDER BY id; SELECT v.* FROM production_daily_metric_value v JOIN production_daily_report_section s ON s.id=v.report_section_id WHERE s.report_id=$id ORDER BY v.id; SELECT v.* FROM production_daily_product_line v JOIN production_daily_report_section s ON s.id=v.report_section_id WHERE s.report_id=$id ORDER BY v.id;"
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($rows -join "`n"))))
}
if($Mode -eq 'Prepare'){
    if([long](Sql "SELECT COUNT(*) FROM production_daily_report WHERE report_date='$BusinessDate';")[0]){throw 'Date occupied; no existing report changed'}
    $sql=@"
START TRANSACTION;
INSERT INTO production_daily_report(report_date,prepared_by_name,status,created_by_name,created_at) VALUES('$BusinessDate','$marker','DRAFT','$marker',NOW());
SET @r=LAST_INSERT_ID();
INSERT INTO production_daily_report_section(report_id,department_code,department_name_snapshot,status,updated_by_name) VALUES(@r,'FACTORY_SUMMARY','全厂汇总','DRAFT','$marker');
SET @s=LAST_INSERT_ID();
INSERT INTO production_daily_metric_value(report_section_id,metric_code,metric_name_snapshot,group_code,group_name_snapshot,unit_snapshot,daily_actual,converted_tons,remark,display_order)
VALUES(@s,'FACTORY_TOTAL_OUTPUT','总产量','FACTORY_METRIC','全厂综合指标','件',200,2,'$marker',1),
      (@s,'FACTORY_TOTAL_POWER','总耗电量','FACTORY_METRIC','全厂综合指标','Kw.h',180,NULL,'$marker',2);
INSERT INTO production_daily_report_section(report_id,department_code,department_name_snapshot,status,updated_by_name) VALUES(@r,'POLYCRYSTAL','多晶车间','DRAFT','$marker');
SET @s=LAST_INSERT_ID();
INSERT INTO production_daily_metric_value(report_section_id,metric_code,metric_name_snapshot,group_code,group_name_snapshot,unit_snapshot,daily_actual,converted_tons,remark,display_order)
VALUES(@s,'POLY_OUTPUT','冰糖产量','COMPREHENSIVE','综合指标','吨',0,NULL,'$marker',29);
COMMIT;
SELECT @r;
"@
    $id=[long](Sql $sql)[0]
    $manifest=@{Marker=$marker;BusinessDate=$BusinessDate;ReportId=$id;Fingerprint=(Fingerprint $id);FactoryQuantity=200;FactoryTons=2;Power=180;PolyQuantity=0;PolyTons=$null}
    [IO.File]::WriteAllText($manifestPath,($manifest|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    'Prepared one marked draft report; existing production orders and inventory untouched'
}else{
    $manifest=Get-Content -Raw $manifestPath|ConvertFrom-Json
    if($manifest.Marker -ne $marker -or $manifest.BusinessDate -ne $BusinessDate){throw 'Manifest mismatch'}
    $id=[long]$manifest.ReportId
    if($id -le 0 -or [long](Sql "SELECT COUNT(*) FROM production_daily_report WHERE id=$id AND report_date='$BusinessDate' AND prepared_by_name='$marker' AND created_by_name='$marker';")[0] -ne 1){throw 'Fixture identity mismatch'}
    if((Fingerprint $id) -ne $manifest.Fingerprint){throw 'Fixture changed since preparation; refusing cleanup'}
    Sql "START TRANSACTION; DELETE v FROM production_daily_metric_value v JOIN production_daily_report_section s ON s.id=v.report_section_id WHERE s.report_id=$id AND v.remark='$marker'; DELETE FROM production_daily_report_section WHERE report_id=$id AND updated_by_name='$marker'; DELETE FROM production_daily_report WHERE id=$id AND prepared_by_name='$marker'; COMMIT;"|Out-Null
    $remaining=[long](Sql "SELECT (SELECT COUNT(*) FROM production_daily_report WHERE id=$id)+(SELECT COUNT(*) FROM production_daily_report_section WHERE report_id=$id)+(SELECT COUNT(*) FROM production_daily_metric_value WHERE remark='$marker');")[0]
    [IO.File]::WriteAllText((Join-Path $out "$BusinessDate-cleanup.json"),(@{Marker=$marker;Remaining=$remaining}|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    if($remaining){throw 'Cleanup incomplete'}
    'Cleanup verified: zero fixture rows remain'
}
