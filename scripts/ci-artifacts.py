"""Bounded same-run artifact transfer, immutable manifests and deduplicated evidence."""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import tempfile
import zipfile
from ci_security import api, digest, manifest, output, provenance, require, verify_manifest

MAX_BYTES = 512 * 1024 * 1024


def unpack(archive, destination):
    destination = Path(destination)
    require(not destination.exists(), "Artifact destination already exists")
    with zipfile.ZipFile(archive) as z:
        entries = z.infolist()
        require(0 < len(entries) <= 10000 and len({i.filename for i in entries}) == len(entries), "Duplicate or excessive artifact entries")
        require(sum(i.file_size for i in entries) <= MAX_BYTES, "Expanded artifact exceeds bound")
        for entry in entries:
            p = PurePosixPath(entry.filename)
            require(bool(p.parts) and entry.filename.rstrip("/") == p.as_posix() and not p.is_absolute() and ".." not in p.parts and "\\" not in entry.filename and not stat.S_ISLNK(entry.external_attr >> 16), "Unsafe artifact path/type")
            require(entry.file_size <= 160 * 1024 * 1024 and not entry.flag_bits & 1, "Oversized/encrypted artifact entry")
        destination.mkdir(parents=True)
        z.extractall(destination)


def download(name, destination, kind, expected=None):
    p = provenance()
    require(re.fullmatch(r"[A-Za-z0-9_-]+", name) is not None, "Invalid artifact name")
    base = f"repos/{p['repository']}/actions/runs/{p['runId']}"
    run = api(base)
    require(run.get("head_sha") == p["sourceCommit"] and run.get("repository", {}).get("full_name") == p["repository"], "Workflow source/repository mismatch")
    artifacts = []
    for page in range(1, 11):
        batch = api(base + f"/artifacts?per_page=100&page={page}").get("artifacts", [])
        artifacts.extend(batch)
        if len(batch) < 100:
            break
    choices = [a for a in artifacts if a.get("name") == name and not a.get("expired")]
    require(len(choices) == 1, "Artifact missing, expired or ambiguous")
    artifact = choices[0]
    require(artifact.get("workflow_run", {}).get("id") == p["runId"] and artifact["workflow_run"].get("head_sha") == p["sourceCommit"], "Artifact came from another source/run")
    require(type(artifact.get("id")) is int and 0 < artifact.get("size_in_bytes", 0) <= MAX_BYTES, "Artifact size/id rejected")
    sha = artifact.get("digest", "")
    require(re.fullmatch(r"sha256:[0-9a-f]{64}", sha) is not None, "Server artifact SHA unavailable")
    with tempfile.TemporaryDirectory() as temp:
        archive = Path(temp) / "artifact.zip"
        with archive.open("wb") as out:
            subprocess.run(["gh", "api", f"repos/{p['repository']}/actions/artifacts/{artifact['id']}/zip"], check=True, stdout=out)
        require(archive.stat().st_size <= MAX_BYTES and "sha256:" + digest(archive) == sha, "Downloaded archive SHA mismatch")
        unpack(archive, destination)
    checked = verify_manifest(destination, kind, expected)
    attempt = re.search(r"-attempt([1-9][0-9]*)$", name)
    require(attempt is not None and checked["runAttempt"] == int(attempt[1]), "Artifact name/manifest attempt mismatch")
    receipt = Path("ci-artifact-receipts.json")
    receipts = json.loads(receipt.read_text()) if receipt.exists() else []
    receipts.append(dict(name=name, artifactId=artifact["id"], archiveSha256=sha[7:],
                         manifestSha256=digest(Path(destination)/"MANIFEST.json"),
                         sourceCommit=p["sourceCommit"], runId=p["runId"], kind=kind))
    receipt.write_text(json.dumps(receipts, indent=2)+"\n")
    return checked


def copy_file(source, destination):
    source, destination = Path(source), Path(destination)
    require(source.is_file() and not source.is_symlink(), "Required evidence file missing")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def pack_build():
    rc = Path("ci-rc")
    copy_file("app/build/outputs/apk/rc/app-rc.apk", rc / "TradeReview-v2.0-RC.apk")
    copy_file("dist/RC-IDENTITY.json", rc / "RC-IDENTITY.json")
    output("rc_digest", manifest(rc, "rc"))
    reports = Path("ci-build-evidence")
    for source in Path("app/build/test-results").rglob("TEST-*.xml"):
        copy_file(source, reports / "test-results" / source.name)
    for variant in ("debug", "rc", "release"):
        copy_file(f"app/build/reports/lint-results-{variant}.xml", reports / f"lint-results-{variant}.xml")
    for source in Path("app/schemas").rglob("*.json"):
        copy_file(source, reports / "schemas" / source.relative_to("app/schemas"))
    copy_file("ci-context.json", reports / "ci-context.json")
    output("reports_digest", manifest(reports, "build-reports"))
    if os.environ.get("TRUSTED_MAIN") == "true":
        inputs = Path("ci-trusted-inputs")
        copy_file("app/build/outputs/apk/release/app-release-unsigned.apk", inputs / "app-release-unsigned.apk")
        output("inputs_digest", manifest(inputs, "signing-inputs"))


def pack_device(api_number):
    root = Path("ci-device-evidence")
    xmls = list(Path("app/build/outputs/androidTest-results").rglob("TEST-*.xml"))
    require(bool(xmls), "Native XML evidence missing")
    for index, source in enumerate(xmls):
        copy_file(source, root / "androidTest-results" / f"{index}-{source.name}")
    for folder in ("ci-metrics", "ci-stability", "ci-v200", "app/build/outputs/androidTest-additional-output"):
        for source in Path(folder).rglob(f"*-api{api_number}.json"):
            target = root / "metrics" / source.name
            if target.exists():
                require(target.read_bytes() == source.read_bytes(), "Conflicting duplicate metric evidence")
            else:
                copy_file(source, target)
    for source in Path("ci-screenshots").glob("*.png"):
        copy_file(source, root / "screenshots" / source.name)
    for source in Path("ci-v200/entry-experience").glob("*-instrumentation.txt"):
        copy_file(source,root / "entry-run-logs" / source.name)
    for source in Path("ci-v200/v210-visual").glob("*-instrumentation.txt"):
        copy_file(source,root / "visual-run-logs" / source.name)
    for source in Path("ci-v200/v210-consistency").glob("*/exported.zip"):
        copy_file(source,root / "synthetic-backups" / (source.parent.name + ".zip"))
    output("evidence_digest", manifest(root, f"device-{api_number}"))


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("command", choices=["pack-build", "pack-device", "download", "verify"])
    p.add_argument("--api", type=int, choices=[29, 35])
    p.add_argument("--name"); p.add_argument("--destination"); p.add_argument("--kind"); p.add_argument("--expected")
    a = p.parse_args()
    if a.command == "pack-build": pack_build()
    elif a.command == "pack-device": pack_device(a.api)
    elif a.command == "download": download(a.name, a.destination, a.kind, a.expected)
    else: verify_manifest(a.destination, a.kind, a.expected)
