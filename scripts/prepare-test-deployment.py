"""Assemble already-built artifacts for the isolated Hong Kong demo server."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/hk-test-deployment/release'


def main():
    raise SystemExit('Legacy snapshot packaging is retired. Use scripts/build-demo-knowledge.py, scripts/prepare-demo-privacy.py and scripts/package-demo-privacy.py; see deploy/test/PRIVACY.md. Do not redeploy the private RAG corpus.')
    if OUT.exists():
        raise SystemExit('Release directory already exists; preserve it and choose a new release before rebuilding')
    sources = {
        'artifacts/app.jar': ROOT / 'target/SugarInventory-1.0-SNAPSHOT.jar',
        'artifacts/warehouse-mcp.jar': ROOT / 'warehouse-mcp/target/warehouse-mcp-0.1.0-exec.jar',
        'artifacts/agent-service.whl': ROOT / 'output/hk-test-deployment/agent-service.whl',
        'database/snapshot.sql': ROOT / 'output/hk-test-deployment/database/final-snapshot.sql',
        'database/snapshot.summary.json': ROOT / 'output/hk-test-deployment/database/final-snapshot.summary.json',
        '.env.example': ROOT / 'deploy/simple/.env.example',
        'agent.Dockerfile': ROOT / 'deploy/simple/agent.Dockerfile',
        'apply-migrations.sh': ROOT / 'deploy/simple/apply-migrations.sh',
    }
    for name in ('docker-compose.yml', 'nginx.conf', 'init-env.sh', 'update.sh', 'README.md'):
        sources[name] = ROOT / 'deploy/test' / name
    directories = {
        'artifacts/dist': ROOT / 'webpage/dist',
        'artifacts/database/migrations': ROOT / 'migrations',
        'artifacts/rag': ROOT / 'deploy/simple/artifacts/rag',
        'artifacts/rag-model': ROOT / 'deploy/simple/artifacts/rag-model',
    }
    for path in [*sources.values(), *directories.values()]:
        if not path.exists():
            raise SystemExit(f'Missing required artifact: {path}')
    with zipfile.ZipFile(sources['artifacts/agent-service.whl']) as wheel:
        for source in (ROOT / 'agent-service/app').rglob('*.py'):
            name = source.relative_to(ROOT / 'agent-service').as_posix()
            if wheel.read(name) != source.read_bytes():
                raise SystemExit(f'Agent wheel differs from current source: {name}')
    OUT.mkdir(parents=True)
    for name, source in sources.items():
        target = OUT / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if source.suffix in {'.sh', '.yml', '.conf', '.md'} or name == '.env.example':
            target.write_text(source.read_text(encoding='utf-8-sig'), encoding='utf-8', newline='\n')
        else:
            shutil.copy2(source, target)
    for name, source in directories.items():
        shutil.copytree(source, OUT / name)
    manifest = {p.relative_to(OUT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted(OUT.rglob('*')) if p.is_file()}
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(json.dumps({'release': str(OUT), 'files': len(manifest),
                      'bytes': sum(p.stat().st_size for p in OUT.rglob('*') if p.is_file())}))


if __name__ == '__main__':
    main()
