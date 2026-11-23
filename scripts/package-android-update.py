"""Create a verified, static Android update directory; never uploads to a server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

PACKAGE = "com.laibin.sugarinventory.dt50"
CERTIFICATE = "24e4ff7cb3bf9065383e0f5e086b847ae4abeaf84ba132a7dc76d06350c75f60"


def run(*args):
    return subprocess.run(args, check=True, capture_output=True, encoding="utf-8", errors="replace", timeout=30).stdout


def atomic_write(target, data):
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(data)
    try:
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--build-tools", required=True, type=Path)
    parser.add_argument("--java", default=str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")) if os.environ.get("JAVA_HOME") else "java")
    parser.add_argument("--notes", required=True, type=Path, help="UTF-8 release notes, maximum 2000 characters")
    parser.add_argument("--output", required=True, type=Path, help="Output root containing android/latest.json")
    args = parser.parse_args()
    notes = args.notes.read_text(encoding="utf-8").strip()
    if not notes or len(notes) > 2000:
        raise ValueError("Release notes must contain 1 to 2000 characters")
    # Use Java directly so APK paths are never interpreted by a batch shell.
    verified = run(args.java, "-jar", str(args.build_tools / "lib/apksigner.jar"),
                   "verify", "--print-certs", str(args.apk))
    certificates = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)$", verified, re.M)
    if certificates != [CERTIFICATE]:
        raise ValueError("APK must use the existing v2 project signing certificate")
    aapt = args.build_tools / ("aapt.exe" if os.name == "nt" else "aapt")
    info = run(str(aapt), "dump", "badging", str(args.apk))
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", info)
    sdk_match = re.search(r"sdkVersion:'(\d+)'", info)
    if not match or not sdk_match or match[1] != PACKAGE or "application-debuggable" in info:
        raise ValueError("A non-debuggable release APK for the expected package is required")
    code = int(match[2])
    if code < 9:
        raise ValueError("The initial updater release must have versionCode >= 9")
    target = args.output / "android"
    target.mkdir(parents=True, exist_ok=True)
    latest = target / "latest.json"
    if latest.exists() and json.loads(latest.read_text(encoding="utf-8"))["versionCode"] >= code:
        raise ValueError("versionCode must be greater than the previously published release")
    apk_bytes = args.apk.read_bytes()
    sha = hashlib.sha256(apk_bytes).hexdigest()
    name = f"laibin-warehouse-{code}-{sha[:12]}.apk"
    atomic_write(target / name, apk_bytes)
    manifest = dict(schemaVersion=1, packageName=PACKAGE, versionCode=code, versionName=match[3],
                    minSdk=int(sdk_match[1]), certificateSha256=CERTIFICATE,
                    downloadUrl=f"https://ccgl.site/app-updates/android/{name}",
                    sha256=sha, sizeBytes=len(apk_bytes), releaseNotes=notes)
    atomic_write(latest, (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    print(latest)


if __name__ == "__main__":
    main()
