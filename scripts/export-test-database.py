"""Read-only localhost export with a single synthetic admin identity."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urlsplit

import pymysql

IDENTITY_IDS = {'user_id', 'owner_user_id', 'exporter_user_id', 'operator_id',
                'created_by', 'updated_by', 'confirmed_by', 'tested_by', 'reviewed_by'}
IDENTITY_NAMES = {'created_by_name', 'updated_by_name', 'prepared_by_name', 'operator'}


def qi(value):
    return '`' + value.replace('`', '``') + '`'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--env-file', required=True)
    p.add_argument('--output', required=True)
    args = p.parse_args()
    cfg = dict(line.split('=', 1) for line in Path(args.env_file).read_text(encoding='utf-8-sig').splitlines()
               if '=' in line and not line.lstrip().startswith('#'))
    uri = urlsplit(cfg['DB_URL'].removeprefix('jdbc:'))
    if uri.hostname not in {'127.0.0.1', 'localhost'}:
        raise SystemExit('Refusing non-local source database')
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        raise SystemExit('Output exists; choose a fresh snapshot filename')
    db = pymysql.connect(host=uri.hostname, port=uri.port or 3306,
                         user=cfg['DB_USERNAME'], password=cfg['DB_PASSWORD'],
                         database=uri.path.lstrip('/'), charset='utf8mb4', autocommit=False)
    counts = {}
    try:
        with db.cursor() as cur, out.open('x', encoding='utf-8', newline='\n') as f:
            cur.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
            cur.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            cur.execute('SHOW FULL TABLES')
            objects = cur.fetchall()
            tables = [r[0] for r in objects if r[1] == 'BASE TABLE']
            views = [r[0] for r in objects if r[1] == 'VIEW']
            cur.execute('SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME '
                        'FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() '
                        'AND REFERENCED_TABLE_NAME IN (%s,%s)', ('user', 'employee_roster'))
            refs = {(t, c): (rt, rc) for t, c, rt, rc in cur.fetchall()}
            cur.execute('SELECT migration_name,checksum_sha256 FROM schema_migration')
            migrations = dict(cur.fetchall())
            root = Path(__file__).resolve().parents[1]
            expected = {x.name: hashlib.sha256(x.read_bytes()).hexdigest() for x in (root / 'migrations').glob('*.sql')}
            if migrations != expected:
                raise ValueError('Source migration ledger does not exactly match release scripts')
            cur.execute("SELECT COUNT(*) FROM role WHERE role_code='ADMIN'")
            if cur.fetchone()[0] != 1:
                raise ValueError('ADMIN role missing')
            f.write('-- Sanitized test snapshot: identity references represent demo admin, not historical actors.\n'
                    'SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n')
            for table in tables:
                cur.execute('SHOW CREATE TABLE ' + qi(table))
                f.write(cur.fetchone()[1] + ';\n')
            for table in tables:
                if table.startswith('agent_') or table in {'operation_log', 'schema_migration_lock', 'user', 'employee_roster'}:
                    counts[table] = 0
                    continue
                cur.execute('SELECT * FROM ' + qi(table))
                columns = [d[0] for d in cur.description]
                names = ','.join(map(qi, columns))
                n = 0
                while batch := cur.fetchmany(250):
                    values = []
                    for row in batch:
                        row = list(row)
                        for i, col in enumerate(columns):
                            if row[i] is None:
                                continue
                            ref = refs.get((table, col))
                            if ref:
                                row[i] = 'demo-admin' if ref[1] == 'employee_id' else 1
                            elif col in IDENTITY_IDS and isinstance(row[i], int):
                                row[i] = 1
                            elif col in IDENTITY_NAMES:
                                row[i] = 'admin'
                        values.append('(' + ','.join(db.escape(v) for v in row) + ')')
                    f.write('INSERT INTO ' + qi(table) + '(' + names + ') VALUES\n' + ',\n'.join(values) + ';\n')
                    n += len(batch)
                counts[table] = n
            f.write("INSERT INTO employee_roster(id,employee_id,name,mobile,department,position,status,role_code) "
                    "VALUES(1,'demo-admin','admin','','测试环境','管理员','在职','ADMIN');\n"
                    "INSERT INTO `user`(id,name,openid,role_code,employee_id,bind_status,bind_method,login_type) "
                    "VALUES(1,'admin',NULL,'ADMIN','demo-admin','UNBOUND',NULL,'WEB');\n")
            counts.update(user=1, employee_roster=1)
            for view in views:
                cur.execute('SHOW CREATE VIEW ' + qi(view))
                sql = re.sub(r'DEFINER=`[^`]*`@`[^`]*`', '', cur.fetchone()[1])
                sql = sql.replace('SQL SECURITY DEFINER', 'SQL SECURITY INVOKER')
                f.write(sql + ';\n')
            f.write('SET FOREIGN_KEY_CHECKS=1;\n')
            db.rollback()
        summary = {'source': 'localhost/' + uri.path.lstrip('/'), 'targetDatabase': 'laibin_demo',
                   'userCount': 1, 'employeeCount': 1, 'rows': counts,
                   'sha256': hashlib.sha256(out.read_bytes()).hexdigest(), 'bytes': out.stat().st_size}
        out.with_suffix('.summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
        print(json.dumps({k: v for k, v in summary.items() if k != 'rows'}))
    finally:
        db.close()


if __name__ == '__main__':
    main()
