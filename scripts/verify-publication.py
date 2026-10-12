"""Independent final gate. Reads APK/public metadata; never reads private signing material."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import zipfile

ORIGINAL_PIN = "30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c"


def metadata(path):
    fields = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            name, value = line.split("=", 1)
            if name in fields:
                raise ValueError("Duplicate signing metadata field")
            fields[name] = value
    return fields


def verify(directory, apksigner, aapt, source_commit):
    directory = Path(directory)
    pin = (Path(__file__).resolve().parents[1] / "docs/V03-CERTIFICATE-SHA256.txt").read_text().strip().lower()
    if pin != ORIGINAL_PIN:
        raise ValueError("Original V0.3 identity changed; publication refused")
    apks = list(directory.glob("*.apk"))
    if len(apks) != 1 or apks[0].is_symlink():
        raise ValueError("Exactly one regular APK required")
    apk = apks[0]
    fields = metadata(directory / "SIGNING.txt")
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit) or fields.get("sourceCommit") != source_commit:
        raise ValueError("APK source commit does not match this run")
    if any(fields.get(key) != value for key, value in {"versionName": "0.8.0", "versionCode": "8", "applicationId": "com.tradereview.app"}.items()):
        raise ValueError("Unexpected APK signing identity")
    configured = fields.get("GitHubActionsSecretsConfigured")
    upgrade = fields.get("upgradeVerified")
    if configured not in ["true", "false"] or upgrade not in ["true", "false"]:
        raise ValueError("Missing signing configuration/classification")
    signed = subprocess.run([str(apksigner), "verify", "--print-certs", str(apk)], check=True, capture_output=True, text=True).stdout
    certs = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", signed, re.MULTILINE)
    if len(certs) != 1 or certs[0].lower() != fields.get("certificateSha256"):
        raise ValueError("Actual APK signer differs from metadata or multiple signers")
    certificate = certs[0].lower()
    stable = apk.name == "TradeReview-v0.8-stable.apk"
    if stable:
        if upgrade != "true" or certificate != ORIGINAL_PIN:
            raise ValueError("Fixed upgrade identity not established; publication refused")
    elif apk.name != "TradeReview-v0.8-test.apk" or upgrade != "false" or configured != "false":
        raise ValueError("Temporary/fixed APK classification mismatch")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    sums = (directory / "SHA256SUMS.txt").read_text().splitlines()
    if len(sums) != 1 or sums[0] not in [f"{digest}  {apk.name}", f"{digest}  ./{apk.name}"]:
        raise ValueError("Published APK SHA-256/filename mismatch")
    identity = subprocess.run([str(aapt), "dump", "badging", str(apk)], check=True, capture_output=True, text=True).stdout.splitlines()[0]
    if "package: name='com.tradereview.app' versionCode='8' versionName='0.8.0'" not in identity:
        raise ValueError("Actual APK version/applicationId mismatch")
    permissions = subprocess.run([str(aapt), "dump", "permissions", str(apk)], check=True, capture_output=True, text=True).stdout
    if any("android.permission." + p in permissions for p in ["INTERNET", "ACCESS_NETWORK_STATE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_", "MANAGE_EXTERNAL_STORAGE"]):
        raise ValueError("Offline/storage permission policy changed")
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate APK entries")
        for script in ["Hani_ctc", "Latn_ctc"]:
            for model in ["conv_model.fb", "lstm_model.fb"]:
                if not any(script in n and n.endswith(model) for n in names):
                    raise ValueError("Bundled offline OCR model missing")
    report = f"versionName=0.8.0\nversionCode=8\nsourceCommit={source_commit}\ncertificateSha256={certificate}\noriginalUpgradePin={ORIGINAL_PIN}\napkSha256={digest}\nupgradeVerified={upgrade}\nclassification={'fixed-v03-identity' if stable else 'temporary-test-only'}\nchecked=actual-apksigner,original-pin,metadata,sha256,source,version,permissions,Chinese-Latin-models\n"
    (directory / "PUBLICATION-CHECK.txt").write_text(report)
    print(report.strip())
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ["directory", "apksigner", "aapt", "source-commit"]:
        parser.add_argument("--" + name, required=True)
    args = parser.parse_args()
    verify(args.directory, args.apksigner, args.aapt, args.source_commit)
