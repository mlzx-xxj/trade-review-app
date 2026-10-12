"""Keyless decision tests; fake tools are explicitly not real APK signature verification."""
import contextlib
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "verify-publication.py"
spec = importlib.util.spec_from_file_location("gate", SCRIPT)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class PublicationTest(unittest.TestCase):
    def setup(self, stable=False, actual=None, configured=None):
        folder = tempfile.TemporaryDirectory();self.addCleanup(folder.cleanup)
        root = Path(folder.name)
        actual = actual or (gate.ORIGINAL_PIN if stable else "a" * 64)
        apk = root / ("TradeReview-v0.8-stable.apk" if stable else "TradeReview-v0.8-test.apk")
        with zipfile.ZipFile(apk, "w") as z:
            for script in ["Hani_ctc", "Latn_ctc"]:
                for model in ["conv_model.fb", "lstm_model.fb"]:
                    z.writestr(f"assets/{script}/{model}", b"synthetic model entry")
        commit = "b" * 40
        fields = dict(versionName="0.8.0", versionCode="8", applicationId="com.tradereview.app", sourceCommit=commit, certificateSha256=actual,
                      upgradeVerified=str(stable).lower(), GitHubActionsSecretsConfigured=configured or str(stable).lower())
        (root / "SIGNING.txt").write_text("".join(k + "=" + v + "\n" for k, v in fields.items()))
        (root / "SHA256SUMS.txt").write_text(hashlib.sha256(apk.read_bytes()).hexdigest() + "  ./" + apk.name + "\n")
        signer = root / "apksigner";signer.write_text("#!/bin/sh\nprintf '%s\\n' 'Signer #1 certificate SHA-256 digest: " + actual + "'\n");signer.chmod(0o755)
        aapt = root / "aapt";aapt.write_text("#!/bin/sh\nif [ \"$2\" = badging ]; then printf \"package: name='com.tradereview.app' versionCode='8' versionName='0.8.0'\\n\"; else printf 'package: com.tradereview.app\\n'; fi\n");aapt.chmod(0o755)
        return root, signer, aapt, commit

    def verify(self, values):
        with contextlib.redirect_stdout(io.StringIO()): return gate.verify(*values)

    def edit(self, root, key, value):
        path = root / "SIGNING.txt";fields = gate.metadata(path);fields[key] = value;path.write_text("".join(k+"="+v+"\n" for k,v in fields.items()))

    def test_real_original_public_pin_is_preserved(self):
        self.assertEqual("30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c", gate.ORIGINAL_PIN)

    def test_temporary_is_never_marked_as_upgrade(self):
        values = self.setup();self.assertIn("upgradeVerified=false", self.verify(values))

    def test_fixed_identity_requires_actual_original_certificate(self):
        self.assertIn("classification=fixed-v03-identity", self.verify(self.setup(stable=True)))
        with self.assertRaises(ValueError): self.verify(self.setup(stable=True, actual="c"*64))

    def test_owner_signed_public_apk_does_not_imply_secrets_configured(self):
        self.assertIn("upgradeVerified=true", self.verify(self.setup(stable=True, configured="false")))

    def test_renamed_temporary_cannot_become_fixed(self):
        values = self.setup();root = values[0];(root / "TradeReview-v0.8-test.apk").rename(root / "TradeReview-v0.8-stable.apk")
        with self.assertRaises(ValueError): self.verify(values)

    def test_signer_metadata_mismatch_is_rejected(self):
        values=self.setup();self.edit(values[0],"certificateSha256","d"*64)
        with self.assertRaises(ValueError):self.verify(values)

    def test_source_and_version_are_checked(self):
        for key,value in [("sourceCommit","e"*40),("versionCode","7"),("upgradeVerified","maybe")]:
            values=self.setup();self.edit(values[0],key,value)
            with self.assertRaises(ValueError):self.verify(values)

    def test_corrupt_digest_and_duplicate_metadata_are_rejected(self):
        values=self.setup();(values[0]/"SHA256SUMS.txt").write_text("0"*64+"  ./TradeReview-v0.8-test.apk\n")
        with self.assertRaises(ValueError):self.verify(values)
        values=self.setup();p=values[0]/"SIGNING.txt";p.write_text(p.read_text()+"upgradeVerified=true\n")
        with self.assertRaises(ValueError):self.verify(values)

    def test_multiple_apks_and_multiple_signers_are_rejected(self):
        values=self.setup();(values[0]/"unexpected.apk").write_bytes(b"fixture")
        with self.assertRaises(ValueError):self.verify(values)
        values=self.setup();p=values[1];p.write_text(p.read_text()+"printf '%s\\n' 'Signer #2 certificate SHA-256 digest: "+"a"*64+"'\n")
        with self.assertRaises(ValueError):self.verify(values)

    def test_missing_model_and_unwanted_permission_are_rejected(self):
        values=self.setup();p=values[2];p.write_text("#!/bin/sh\nif [ \"$2\" = badging ]; then printf \"package: name='com.tradereview.app' versionCode='8' versionName='0.8.0'\\n\"; else printf 'uses-permission: android.permission.INTERNET\\n'; fi\n")
        with self.assertRaises(ValueError):self.verify(values)
        values=self.setup();apk=values[0]/"TradeReview-v0.8-test.apk"
        with zipfile.ZipFile(apk,"w") as z:z.writestr("assets/Hani_ctc/conv_model.fb",b"fixture")
        (values[0]/"SHA256SUMS.txt").write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+"  "+apk.name+"\n")
        with self.assertRaises(ValueError):self.verify(values)

if __name__ == "__main__":unittest.main()
