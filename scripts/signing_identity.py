"""Public signing policy, not private key material. Issue #12 authorizes this identity."""
from pathlib import Path

PIN = "29b2f82887a0591d5b1d1f9ab148b90c0b658088530e3bcc1bd7e5fee64f2af9"
LEGACY_V03_PIN = "30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c"
ALIAS = "trade-review-release-2026"
ROOT = Path(__file__).resolve().parents[1]


def require_committed_identity():
    if (ROOT / "docs/V200-CERTIFICATE-SHA256.txt").read_text().strip() != PIN:
        raise ValueError("Permanent V2 public certificate pin changed")
    if (ROOT / "docs/V03-CERTIFICATE-SHA256.txt").read_text().strip() != LEGACY_V03_PIN:
        raise ValueError("Historical V0.3 certificate evidence changed")


def upgrade_policy(old, new):
    """Policy only; never substitutes for actual Android installation/data checks."""
    if old["applicationId"] != new["applicationId"]:
        return "separate-application"
    if old["certificateSha256"] != new["certificateSha256"]:
        return "incompatible-certificate-backup-reinstall-restore-required"
    if new["certificateSha256"] != PIN:
        return "not-current-permanent-identity"
    if new["versionCode"] <= old["versionCode"]:
        return "versionCode-must-increase"
    return "same-permanent-identity-upgrade-eligible-not-device-verified"
