"""Real non-debuggable Stable -> candidate upgrade; protected main, disposable AVD only."""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile
from ci_security import api, digest, require, require_trusted, REPOSITORY
from release_version import VERSION_NAME, VERSION_CODE
from signing_identity import PIN, upgrade_policy

APP = "com.tradereview.app"
STABLE_TAG = "v2.0.0"
STABLE_ASSET = "TradeReview-v2.0-fixed-candidate.1.apk"
STABLE_SHA = "d157327870baa1dcd6d777de8ffcc95b1c504020a7c450ad615c7f56a6f92b5d"
FIXTURE = Path(__file__).with_name("fixtures") / "v210-upgrade.zip"
FIXTURE_SHA = "8494964bb73c6539b0e079ebbf904f313021ff7622f928682adc39a4921a45ac"
PASS = "passed-stable-v2.0.0-to-v2.1.0"


def load_check():
    spec = importlib.util.spec_from_file_location("upgrade_zip_check", Path(__file__).with_name("v210-consistency-evidence.py"))
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def download_stable(directory):
    require_trusted()
    release = api(f"repos/{REPOSITORY}/releases/tags/{STABLE_TAG}")
    require(release.get("tag_name") == STABLE_TAG and release.get("draft") is False and release.get("prerelease") is False, "Original Stable release required")
    assets = [a for a in release.get("assets", []) if a.get("name") == STABLE_ASSET]
    require(len(assets) == 1 and assets[0].get("digest") == "sha256:" + STABLE_SHA, "Pinned Stable asset changed or missing")
    directory = Path(directory); directory.mkdir(parents=True, exist_ok=True)
    subprocess.run(["gh", "release", "download", STABLE_TAG, "--repo", REPOSITORY, "--pattern", STABLE_ASSET, "--dir", str(directory)], check=True)
    result = directory / STABLE_ASSET
    require(digest(result) == STABLE_SHA, "Downloaded Stable APK hash mismatch")
    return result


def eligible(old, new):
    require(old["apkSha256"] == STABLE_SHA and old["versionName"] == "2.0.0" and old["versionCode"] == 22 and not old["debuggable"], "Genuine non-debuggable Stable V2.0.0/code22 required")
    require(new["versionName"] == VERSION_NAME and new["versionCode"] == VERSION_CODE and not new["debuggable"], "Current non-debuggable candidate required")
    require(old["applicationId"] == new["applicationId"] == APP and old["certificateSha256"] == new["certificateSha256"] == PIN, "Same formal package and permanent certificate required; RC is forbidden")
    require(upgrade_policy(old, new) == "same-permanent-identity-upgrade-eligible-not-device-verified", "Version code must increase")


def facts(path):
    with zipfile.ZipFile(path) as z:
        data = json.loads(z.read("data.json"))
    load_check().verify_zip(path, data["ledger"])
    # Only export metadata changes; retain IDs, every business field and image hashes.
    data.pop("exportedAt"); data.pop("checksumSha256")
    return data


def verify_evidence(root, candidate_sha, expected_api=None):
    root = Path(root); report = json.loads((root / "upgrade.json").read_text())
    eligible(report["old"], report["new"])
    require(report["api"] in (29, 35) and (expected_api is None or report["api"] == expected_api), "Upgrade device API mismatch")
    require(report["new"]["apkSha256"] == candidate_sha and report["result"] == PASS and report["installedVersionCode"] == VERSION_CODE, "Upgrade report does not match signed candidate")
    for name, sha in report["backupSha256"].items():
        require(name in ("before.zip", "after.zip", "empty.zip", "restored.zip") and digest(root / name) == sha, "Upgrade backup hash mismatch")
    require(set(report["backupSha256"]) == {"before.zip", "after.zip", "empty.zip", "restored.zip"}, "Incomplete upgrade/restore evidence")
    before = facts(root / "before.zip")
    require(before == facts(FIXTURE) == facts(root / "after.zip") == facts(root / "restored.zip"), "Upgrade or real ZIP restore lost business facts/images")
    empty = facts(root / "empty.zip")
    require(not empty["ledger"]["executions"] and not empty["trades"] and not empty["attachments"], "Restore must start from demonstrably empty synthetic data")
    require(len(before["ledger"]["executions"]) == 5 and len(before["attachments"]) == 1, "Nonempty trade and attachment fixture required")
    return report


class Device:
    """Use actual system UI; no run-as, URI injection or app database access."""
    def __init__(self, probe, root):
        self.probe, self.root, self.operations = probe, Path(root), []
        self.root.mkdir(parents=True, exist_ok=True)

    def adb(self, *args, **kwargs):
        check = kwargs.pop("check", True)
        result = self.probe.adb(*args, check=False, **kwargs)
        self.operations.append(dict(command=list(args), returnCode=result.returncode, stdout=result.stdout if isinstance(result.stdout, str) else f"{len(result.stdout)} binary bytes", stderr=result.stderr if isinstance(result.stderr, str) else ""))
        (self.root / "operations.json").write_text(json.dumps(self.operations, ensure_ascii=False, indent=2) + "\n")
        if check: result.check_returncode()
        return result

    def tree(self):
        self.adb("shell", "rm", "-f", "/sdcard/v210-upgrade-window.xml")
        self.adb("shell", "uiautomator", "dump", "/sdcard/v210-upgrade-window.xml")
        raw = self.adb("exec-out", "cat", "/sdcard/v210-upgrade-window.xml").stdout
        (self.root / "last-window.xml").write_text(raw)
        require("isn't responding" not in raw and "not responding" not in raw, "Never dismiss ANR")
        return ET.fromstring(raw)

    def find(self, text, *, description=False, system=False, scroll=False, prefix=False):
        deadline = time.monotonic() + 45
        for attempt in range(12):
            tree = self.tree()
            for node in tree.iter("node"):
                package = node.get("package", "")
                value = node.get("content-desc" if description else "text", "")
                matches = value.startswith(text) if prefix else value == text
                if matches and node.get("enabled") == "true" and ((system and package.endswith(".documentsui")) or (not system and package == APP)):
                    return node
            if time.monotonic() >= deadline: break
            if scroll: self.adb("shell", "input", "swipe", "360", "1000", "360", "360", "300")
            else: time.sleep(.2)
        raise ValueError("Expected real UI missing: " + text)

    def tap(self, node):
        box = [int(n) for n in re.findall(r"\d+", node.get("bounds", ""))]
        require(len(box) == 4 and box[2] > box[0] and box[3] > box[1], "Visible UI target required")
        self.adb("shell", "input", "tap", str((box[0] + box[2]) // 2), str((box[1] + box[3]) // 2))

    def backup_page(self):
        self.adb("shell", "am", "force-stop", APP)
        self.adb("shell", "am", "start", "-W", "-n", APP + "/com.tradereview.app.MainActivity")
        self.tap(self.find("备份与导出", description=True))

    def downloads(self):
        tree = self.tree()
        toolbar = next(n for n in tree.iter("node") if n.get("resource-id", "").endswith(":id/toolbar") and n.get("package", "").endswith(".documentsui"))
        self.tap(next(n for n in toolbar.iter("node") if n.get("class") == "android.widget.ImageButton" and n.get("clickable") == "true"))
        tree = self.tree()
        roots = next(n for n in tree.iter("node") if n.get("resource-id", "").endswith(":id/roots_list"))
        self.tap(next(n for n in roots.iter("node") if n.get("text") in ("Downloads", "下载")))

    def restore(self, filename):
        self.backup_page(); self.tap(self.find("从 ZIP／JSON 恢复")); self.downloads()
        self.tap(self.find(filename, system=True, scroll=True))
        self.tap(self.find("覆盖备份范围", scroll=True))
        self.tap(self.find("继续确认恢复", scroll=True))
        self.find("确认覆盖备份范围？")
        self.tap(self.find("确认恢复"))
        self.find("覆盖恢复完成", scroll=True)

    def export(self, filename):
        self.backup_page(); self.tap(self.find("保存完整 ZIP 备份")); self.tap(self.find("完整 ZIP（推荐）"))
        self.downloads()
        field = next(n for n in self.tree().iter("node") if n.get("class") == "android.widget.EditText")
        self.tap(field)
        self.adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        self.adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * 80))
        self.adb("shell", "input", "text", filename)
        tree = self.tree()
        self.tap(next(n for n in tree.iter("node") if n.get("resource-id") == "android:id/button1" and n.get("enabled") == "true"))
        self.find("完整 ZIP 已保存", prefix=True, scroll=True)
        raw = self.adb("exec-out", "cat", "/sdcard/Download/" + filename, binary=True).stdout
        target = self.root / filename; target.write_bytes(raw); facts(target)
        return target


def run(probe, old_apk, new_apk, aapt, signer):
    require_trusted()
    require(probe.adb("shell", "getprop", "ro.kernel.qemu").stdout.strip() == "1", "Disposable emulator only")
    old, new = (probe.identity.inspect(p, aapt, signer) for p in (old_apk, new_apk))
    eligible(old, new)
    require(digest(FIXTURE) == FIXTURE_SHA, "Synthetic fixture changed")
    ui = Device(probe, probe.ROOT / "same-signer-upgrade")
    # Before this interval, discard only this AVD's separately tested fresh install.
    require("Success" in ui.adb("uninstall", APP).stdout, "Fresh AVD candidate must exist")
    require("Success" in ui.adb("install", str(old_apk)).stdout, "Stable install failed")
    ui.adb("push", str(FIXTURE), "/sdcard/Download/upgrade-fixture.zip")
    ui.restore("upgrade-fixture.zip")
    before = ui.export("before.zip")
    require(facts(before) == facts(FIXTURE), "Stable must really import all fixture facts before upgrade")
    ui.adb("shell", "am", "force-stop", APP)
    # No uninstall/clear between Stable export and the verified candidate export.
    require("Success" in ui.adb("install", "-r", str(new_apk)).stdout, "Android overlay install failed")
    installed = ui.adb("shell", "dumpsys", "package", APP).stdout
    require(re.search(r"\bversionCode=" + str(VERSION_CODE) + r"\b", installed) is not None, "Installed candidate version mismatch")
    after = ui.export("after.zip")
    require(facts(before) == facts(after), "Overlay upgrade lost data")
    # Only after retention is proven, clear the disposable fixture to prove restore.
    require("Success" in ui.adb("shell", "pm", "clear", APP).stdout, "Synthetic reset failed")
    ui.export("empty.zip")
    ui.restore("before.zip"); ui.export("restored.zip")
    report = dict(result=PASS, api=int(ui.adb("shell", "getprop", "ro.build.version.sdk").stdout.strip()), old=old, new=new, installedVersionCode=VERSION_CODE,
                  backupSha256={n: digest(ui.root / n) for n in ("before.zip", "after.zip", "empty.zip", "restored.zip")})
    (ui.root / "upgrade.json").write_text(json.dumps(report, indent=2) + "\n")
    verify_evidence(ui.root, new["apkSha256"])
    return report
