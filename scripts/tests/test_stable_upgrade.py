"""Targeted upgrade failure gates; mocked device calls never count as a real upgrade."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import stable_upgrade as upgrade
from release_version import VERSION_CODE, VERSION_NAME


def identities():
    old = dict(applicationId=upgrade.APP, versionName="2.0.0", versionCode=22,
               debuggable=False, certificateSha256=upgrade.PIN, apkSha256=upgrade.STABLE_SHA)
    return old, dict(old, versionName=VERSION_NAME, versionCode=VERSION_CODE, apkSha256="a" * 64)


class UpgradeGateTest(unittest.TestCase):
    def write_zip(self, target, change):
        with zipfile.ZipFile(upgrade.FIXTURE) as z:
            entries = {name: z.read(name) for name in z.namelist()}
        data = json.loads(entries["data.json"]); change(data)
        data.pop("checksumSha256")
        compact = lambda obj: json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode()
        data["checksumSha256"] = hashlib.sha256(compact(data)).hexdigest()
        entries["data.json"] = compact(data)
        manifest = json.loads(entries["manifest.json"])
        manifest["data"].update(bytes=len(entries["data.json"]), sha256=hashlib.sha256(entries["data.json"]).hexdigest())
        entries["manifest.json"] = compact(manifest)
        with zipfile.ZipFile(target, "w") as z:
            for name, raw in entries.items(): z.writestr(name, raw)

    def evidence(self, root):
        old, new = identities()
        for name in ("before.zip", "after.zip", "restored.zip"):
            (root / name).write_bytes(upgrade.FIXTURE.read_bytes())
        def empty(data):
            data["trades"] = []; data["attachments"] = []
            data["ledger"]["executions"] = []
        self.write_zip(root / "empty.zip", empty)
        report = dict(api=29, old=old, new=new, result=upgrade.PASS, installedVersionCode=VERSION_CODE,
                      backupSha256={n: upgrade.digest(root / n) for n in ("before.zip", "after.zip", "empty.zip", "restored.zip")})
        (root / "upgrade.json").write_text(json.dumps(report))
        return report

    def test_retained_archives_are_rechecked_not_just_a_pass_flag(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); report = self.evidence(root)
            upgrade.verify_evidence(root, report["new"]["apkSha256"])
            self.write_zip(root / "restored.zip", lambda d: d["ledger"]["strategies"][0].update(description="lost original description"))
            report["backupSha256"]["restored.zip"] = upgrade.digest(root / "restored.zip")
            (root / "upgrade.json").write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError, "lost business"): upgrade.verify_evidence(root, report["new"]["apkSha256"])

    def test_restore_into_unchanged_nonempty_data_cannot_pass_as_recovery(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); report = self.evidence(root)
            (root / "empty.zip").write_bytes(upgrade.FIXTURE.read_bytes())
            report["backupSha256"]["empty.zip"] = upgrade.digest(root / "empty.zip")
            (root / "upgrade.json").write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError, "demonstrably empty"): upgrade.verify_evidence(root, report["new"]["apkSha256"])

    def test_gradle_and_gate_expect_exact_current_identity(self):
        gradle = (SCRIPTS.parent / "app/build.gradle.kts").read_text()
        self.assertEqual([str(VERSION_CODE)], re.findall(r"versionCode = (\d+)", gradle))
        self.assertEqual([VERSION_NAME], re.findall(r'versionName = "([^"]+)"', gradle))
        self.assertIn('applicationId = "com.tradereview.app"', gradle)

    def test_only_pinned_nondebuggable_stable_to_increasing_same_signer_formal_is_eligible(self):
        old, new = identities(); upgrade.eligible(old, new)
        for side, key, value in [(0, "apkSha256", "b"*64), (0, "versionName", "2.0.0-rc.3"),
                                 (0, "versionCode", 21), (0, "debuggable", True),
                                 (1, "certificateSha256", "b"*64), (0, "certificateSha256", "b"*64),
                                 (1, "applicationId", upgrade.APP + ".rc.v200"),
                                 (1, "versionCode", 22), (1, "versionName", "2.0.0"), (1, "debuggable", True)]:
            pair = [dict(old), dict(new)]; pair[side][key] = value
            with self.subTest(key=key, side=side), self.assertRaises(ValueError): upgrade.eligible(*pair)

    def test_fixture_has_real_nonempty_ledger_unknown_fees_and_attachment(self):
        self.assertEqual(upgrade.FIXTURE_SHA, upgrade.digest(upgrade.FIXTURE))
        facts = upgrade.facts(upgrade.FIXTURE)
        self.assertEqual(5, len(facts["ledger"]["executions"]))
        self.assertEqual(2, sum(e["fees"] is None for e in facts["ledger"]["executions"]))
        self.assertEqual(1, len(facts["attachments"]))

    def test_baseline_download_denied_outside_trusted_main_before_any_network(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(upgrade, "api") as api:
            with self.assertRaises(ValueError): upgrade.download_stable("unused")
            api.assert_not_called()

    def test_changed_or_prerelease_baseline_is_rejected_before_download(self):
        release = dict(tag_name="v2.0.0", draft=False, prerelease=False,
                       assets=[dict(name=upgrade.STABLE_ASSET, digest="sha256:" + upgrade.STABLE_SHA)])
        for mutate in (lambda v: v.update(prerelease=True), lambda v: v.update(draft=True),
                       lambda v: v["assets"][0].update(digest="sha256:" + "b"*64)):
            value = copy.deepcopy(release); mutate(value)
            with patch.object(upgrade, "require_trusted"), patch.object(upgrade, "api", return_value=value), patch.object(upgrade.subprocess, "run") as command:
                with self.assertRaises(ValueError): upgrade.download_stable("unused")
                command.assert_not_called()

    def test_actual_download_hash_is_required_even_when_server_metadata_matches(self):
        release = dict(tag_name="v2.0.0", draft=False, prerelease=False,
                       assets=[dict(name=upgrade.STABLE_ASSET, digest="sha256:" + upgrade.STABLE_SHA)])
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / upgrade.STABLE_ASSET).write_bytes(b"wrong public bytes")
            with patch.object(upgrade, "require_trusted"), patch.object(upgrade, "api", return_value=release), patch.object(upgrade.subprocess, "run"):
                with self.assertRaisesRegex(ValueError, "hash mismatch"): upgrade.download_stable(folder)

    def test_protected_environment_cannot_be_removed_from_upgrade_job(self):
        spec = importlib.util.spec_from_file_location("upgrade_workflow", SCRIPTS / "ci-check-workflow.py")
        workflow = importlib.util.module_from_spec(spec); spec.loader.exec_module(workflow)
        doc = workflow.read(SCRIPTS.parent / ".github/workflows/android.yml")
        workflow.check(doc); doc["jobs"]["signed-install"].pop("environment")
        with self.assertRaisesRegex(ValueError, "upgrade tests"): workflow.check(doc)

    def test_missing_or_foreign_candidate_evidence_is_rejected(self):
        old, new = identities()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            report = dict(api=29, old=old, new=new, result=upgrade.PASS, installedVersionCode=VERSION_CODE,
                          backupSha256={})
            (root / "upgrade.json").write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError, "Incomplete"): upgrade.verify_evidence(root, new["apkSha256"])
            with self.assertRaisesRegex(ValueError, "does not match"): upgrade.verify_evidence(root, "b"*64)

    def test_upgrade_run_refuses_personal_phone_before_apk_or_ui_access(self):
        from types import SimpleNamespace
        probe = SimpleNamespace(adb=lambda *a: subprocess.CompletedProcess([], 0, "0", ""))
        with patch.object(upgrade, "require_trusted"), patch.object(upgrade, "Device") as ui:
            with self.assertRaisesRegex(ValueError, "Disposable"): upgrade.run(probe, "old", "new", "aapt", "signer")
            ui.assert_not_called()

    def test_failed_overlay_never_clears_data_or_writes_a_success_report(self):
        from types import SimpleNamespace
        old, new = identities()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            probe = SimpleNamespace(ROOT=root, adb=lambda *a: subprocess.CompletedProcess([], 0, "1", ""),
                                    identity=SimpleNamespace(inspect=Mock(side_effect=[old, new])))
            ui = Mock(root=root)
            ui.export.return_value = upgrade.FIXTURE
            def adb(*args, **kwargs):
                failed = args[:2] == ("install", "-r")
                return subprocess.CompletedProcess([], 1 if failed else 0, "INSTALL_FAILED_UPDATE_INCOMPATIBLE" if failed else "Success", "")
            ui.adb.side_effect = adb
            with patch.object(upgrade, "require_trusted"), patch.object(upgrade, "Device", return_value=ui):
                with self.assertRaisesRegex(ValueError, "overlay install failed"): upgrade.run(probe, "old.apk", "new.apk", "aapt", "signer")
            self.assertFalse(any(call.args[:4] == ("shell", "pm", "clear", upgrade.APP) for call in ui.adb.call_args_list))
            self.assertFalse((root / "upgrade.json").exists())


if __name__ == "__main__": unittest.main()
