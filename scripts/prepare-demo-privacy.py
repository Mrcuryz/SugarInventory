"""Prepare a demo-only SQL scrub from schema metadata; source database is read-only."""
import argparse
import json
from pathlib import Path
from urllib.parse import urlsplit
import pymysql

def ident(s): return '`'+s.replace('`','``')+'`'
def val(s): return "CONVERT(0x"+s.encode('utf-8').hex()+" USING utf8mb4) COLLATE utf8mb4_unicode_ci"

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--env-file',type=Path,required=True)
    ap.add_argument('--output',type=Path,required=True); a=ap.parse_args()
    cfg=dict(l.split('=',1) for l in a.env_file.read_text(encoding='utf-8-sig').splitlines() if '=' in l and not l.startswith('#'))
    uri=urlsplit(cfg['DB_URL'].removeprefix('jdbc:'))
    if uri.hostname not in ('localhost','127.0.0.1'): raise SystemExit('Local source only')
    db=pymysql.connect(host=uri.hostname,port=uri.port or 3306,user=cfg['DB_USERNAME'],password=cfg['DB_PASSWORD'],database=uri.path.strip('/'),charset='utf8mb4')
    with db.cursor() as c:
        c.execute('START TRANSACTION READ ONLY')
        c.execute("SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND DATA_TYPE IN ('varchar','text','longtext','json','char')")
        cols=c.fetchall()
        c.execute("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'")
        base_tables={row[0] for row in c.fetchall()}
        cols=[row for row in cols if row[0] in base_tables]
        c.execute('SELECT id,product_name FROM product ORDER BY id')
        products=c.fetchall()
        db.rollback()
    db.close()
    sql=['SET NAMES utf8mb4;', 'SET FOREIGN_KEY_CHECKS=0;', 'START TRANSACTION;']
    # The calling deployment script separately asserts DB_NAME=laibin_demo.
    tables=sorted({t for t,_,_,_ in cols})
    for t in tables:
        if t.startswith('agent_') or t=='operation_log':
            sql.append('DELETE FROM '+ident(t)+';')
    replacements=sorted([(n,f'演示冰糖{i:03}') for i,n in products if n],key=lambda x:-len(x[0]))
    brands=[('广西糖罐子食品有限公司','示例食品企业'),('来宾市来冰食品有限公司','示例食品企业'),('来冰公司','示例企业'),('来冰','示例企业'),('柳冰','协作企业'),('糖罐子','示例品牌')]
    clear={'source_text','consumption_text','remark','description','contact_person','address','phone','mobile','fax','email','factory_serial_no','installation_location','error_message','executor_instance','created_ip','user_agent','picked_by_name','prepared_by_name','cancel_reason'}
    json_clear={'planned_material_json','planned_output_json','standard_snapshot_json','failed_metrics_json','inbound_json','consumption_json','unmatched_names','ext_data','metadata_json','details_json'}
    skip={'schema_migration','schema_migration_lock','permission','role','role_permission'}
    for t,col,typ,nullable in cols:
        if t.startswith('agent_') or t in skip or t=='operation_log': continue
        target=ident(t); field=ident(col)
        if col in clear:
            sql.append(f'UPDATE {target} SET {field}='+('NULL' if nullable=='YES' else "''")+';'); continue
        if col in json_clear:
            sql.append(f'UPDATE {target} SET {field}='+('NULL' if nullable=='YES' else "'[]'")+';'); continue
        if col in {'created_by_name','updated_by_name','operator'}:
            sql.append(f"UPDATE {target} SET {field}='admin' WHERE {field} IS NOT NULL;"); continue
        if col=='team_name':
            sql.append(f'UPDATE {target} SET {field}={val("演示班组")} WHERE {field} IS NOT NULL;'); continue
        if col in {'standard_name','applied_standard_name'}:
            sql.append(f'UPDATE {target} SET {field}=CONCAT({val("演示标准")},id) WHERE {field} IS NOT NULL;'); continue
        if typ=='json' and col=='qualified_standards':
            sql.append(f"UPDATE {target} SET {field}='[]';"); continue
        if 'product_name' in col:
            branches=' '.join(f'WHEN {val(old)} THEN {val(new)}' for old,new in replacements)
            sql.append(f'UPDATE {target} SET {field}=CASE {field} {branches} ELSE {field} END;')
        if col=='related_products':
            sql.append(f'UPDATE {target} SET {field}=NULL;')
            continue
        for old,new in brands:
            sql.append(f'UPDATE {target} SET {field}=REPLACE({field},{val(old)},{val(new)}) WHERE LOCATE({val(old)},{field})>0;')
    # Clear actual measured values and private standard thresholds; retain linkage for the UI.
    sql += ['UPDATE quality_standard_item SET min_value=NULL,max_value=NULL;', "UPDATE quality_standards SET status='DISABLED',standard_code=CONCAT('DEMO-',id),standard_level=NULL;",'UPDATE assay SET color_value=NULL,reducing_sugar=NULL,dry_weight=NULL,conductivity_ash=NULL,sucrose=NULL,insoluble_impurity=NULL,ph_value=NULL,judge_result=\'NO_STANDARD\',is_qualified=\'无标准\',failed_metric_count=0,judge_message='+val('演示记录 未提供检测值')+';', 'COMMIT;']
    a.output.parent.mkdir(parents=True,exist_ok=True)
    sql.append('SET FOREIGN_KEY_CHECKS=1;')
    a.output.write_text('\n'.join(sql)+'\n',encoding='utf-8')
    print(json.dumps({'tables':len(tables),'productAliases':len(products),'statements':len(sql)}))

if __name__=='__main__': main()
