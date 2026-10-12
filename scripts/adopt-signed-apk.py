"""Publish a public, externally signed APK only when its payload matches this build.

This fallback transfers an APK through GitHub's repository API. It never handles
private signing material and does not configure or replace Actions Secrets.
"""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import zipfile


def application_payload(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate APK entries")
        if sum(entry.file_size for entry in archive.infolist()) > 256 * 1024 * 1024:
            raise ValueError("APK expanded size limit exceeded")
        return {name: archive.read(name) for name in names if not name.startswith("META-INF/")}


def require_identical_payload(compiled, signed):
    original = application_payload(compiled)
    replacement = application_payload(signed)
    if "AndroidManifest.xml" not in original or original != replacement:
        raise ValueError("Signed APK application payload differs from the verified build")


def main():
    parser = argparse.ArgumentParser()
    for option in ["blob-file", "blob-sha", "compiled-apk", "apksigner", "expected-cert-file", "output-dir", "source-commit", "secrets-configured"]:
        parser.add_argument("--" + option, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.blob_sha):
        raise ValueError("Invalid Git blob SHA")
    blob = json.loads(Path(args.blob_file).read_text())
    if blob.get("encoding") != "base64" or blob.get("size", 0) > 64 * 1024 * 1024:
        raise ValueError("Invalid APK blob format or size")
    signed = base64.b64decode("".join(blob["content"].split()), validate=True)
    if not signed or len(signed) != blob["size"]:
        raise ValueError("Invalid APK blob size")
    git_hash = hashlib.sha1(b"blob " + str(len(signed)).encode() + b"\0" + signed).hexdigest()
    if git_hash != args.blob_sha:
        raise ValueError("Git blob digest mismatch")
    require_identical_payload(Path(args.compiled_apk).read_bytes(), signed)
    expected = Path(args.expected_cert_file).read_text().strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Invalid committed certificate pin")
    output = Path(args.output_dir)
    output.mkdir(parents=True, exist_ok=True)
    candidate = output / "verified-candidate.apk"
    candidate.write_bytes(signed)
    verification = subprocess.run([args.apksigner, "verify", "--print-certs", str(candidate)], check=True, text=True, capture_output=True).stdout
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", verification)
    if certificates != [expected]:
        raise ValueError("Fixed APK signer does not match the committed V0.3 certificate")
    for old_apk in output.glob("*.apk"):
        if old_apk != candidate:
            old_apk.unlink()
    apk = output / "TradeReview-v0.7-stable.apk"
    candidate.replace(apk)
    digest = hashlib.sha256(signed).hexdigest()
    (output / "SHA256SUMS.txt").write_text(f"{digest}  {apk.name}\n")
    (output / "SIGNING.txt").write_text(
        f"versionName=0.7.0\nversionCode=7\napplicationId=com.tradereview.app\n"
        f"sourceCommit={args.source_commit}\ncertificateSha256={expected}\napkSha256={digest}\n"
        "signingOrigin=explicitly authorized V0.3 long-term key; signed with official standalone apksigner in Base\n"
        f"GitHubActionsSecretsConfigured={args.secrets_configured}\n"
        "Actions verified the actual signer against the committed pin and compared every application payload entry with its own freshly compiled APK.\n"
        "Secrets administration is still required for automatic future builds. Preserve this private key, retain applicationId and increment versionCode for updates.\n"
        "Old differently signed test versions require a manual uninstall first. No real phone installation or upgrade test was performed.\n"
    )
    print(f"Fixed certificate and application payload verified: {expected}; APK SHA-256 {digest}")


if __name__ == "__main__":
    main()
