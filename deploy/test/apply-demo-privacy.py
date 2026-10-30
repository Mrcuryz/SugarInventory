"""Checksum-guarded update of the demo executable jar; never touches production."""
import hashlib,json,os,zipfile
from pathlib import Path
base=Path('/home/ubuntu/laibin-demo').resolve()
assert Path.cwd().resolve()==base
patch=Path('/home/ubuntu/demo-privacy-update')
jar=base/'artifacts/app.jar'
info=json.loads((patch/'jar-delta.json').read_text())
assert hashlib.sha256(jar.read_bytes()).hexdigest()==info['baseSha256'], 'Unexpected deployed jar; stop'
replacement=jar.with_suffix('.privacy.jar')
with zipfile.ZipFile(jar) as old,zipfile.ZipFile(patch/'jar-delta.zip') as delta,zipfile.ZipFile(replacement,'w') as out:
    for entry in old.infolist():
        if entry.filename not in info['removed'] and entry.filename not in info['changed']:
            out.writestr(entry,old.read(entry.filename))
    for entry in delta.infolist():out.writestr(entry,delta.read(entry.filename))
with zipfile.ZipFile(replacement) as check:
    assert check.testzip() is None
os.replace(replacement,jar)
print('JAR_PATCH_OK')
