"""Package a checksum-guarded demo update without uploading unchanged Java libraries."""
from pathlib import Path
import hashlib,json,shutil,tarfile,zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/demo-privacy'
stage=OUT/'update'
stage.mkdir(exist_ok=True)
old=ROOT/'output/hk-test-deployment/release/artifacts/app.jar'
new=OUT/'backend-build/target/SugarInventory-1.0-SNAPSHOT.jar'
exporter='BOOT-INF/classes/com/Laibin/SugarInventory/production/service/impl/ProductionDailyReportXlsxExporter.class'
with zipfile.ZipFile(new) as candidate:
    current=ROOT/'target/classes'/exporter.removeprefix('BOOT-INF/classes/')
    if candidate.read(exporter) != current.read_bytes():
        raise SystemExit('Stale backend build: rebuild the isolated backend from current source before packaging.')
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b,zipfile.ZipFile(stage/'jar-delta.zip','w') as delta:
    changed=[]
    for item in b.infolist():
        if item.filename not in a.namelist() or a.read(item.filename)!=b.read(item.filename):
            delta.writestr(item,b.read(item.filename));changed.append(item.filename)
    removed=sorted(set(a.namelist())-set(b.namelist()))
(stage/'jar-delta.json').write_text(json.dumps({'baseSha256':hashlib.sha256(old.read_bytes()).hexdigest(),'changed':changed,'removed':removed}),encoding='utf-8')
for name,source in {'dist':OUT/'dist','rag':OUT/'knowledge-build/runtime'}.items():
    shutil.copytree(source,stage/name,dirs_exist_ok=True)
for f in (stage/'dist').rglob('*.js'):
    text=f.read_text(encoding='utf-8')
    for name in ['桂ICP备2025058642号-2','桂 ICP 备 2025058642 号-2']:
        text=text.replace(name,'演示环境')
    f.write_text(text,encoding='utf-8')
shutil.copy2(OUT/'privacy.sql',stage/'privacy.sql')
shutil.copy2(ROOT/'deploy/test/docker-compose.yml',stage/'docker-compose.yml')
for name in ['apply-demo-privacy.py','apply-demo-privacy.sh']:
    shutil.copy2(ROOT/'deploy/test'/name,stage/name)
with tarfile.open(OUT/'demo-privacy-update.tar.gz','w:gz') as t:
    t.add(stage,arcname='demo-privacy-update')
print(json.dumps({'changedJarEntries':len(changed),'archiveBytes':(OUT/'demo-privacy-update.tar.gz').stat().st_size}))
