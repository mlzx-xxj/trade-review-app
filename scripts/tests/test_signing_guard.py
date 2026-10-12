"""Verify publication decisions without generating or handling any signing private key."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'check-apk-signature.sh'
CERT = (SCRIPT.parent.parent / 'docs/V03-CERTIFICATE-SHA256.txt').read_text().strip()

class SigningGuardTest(unittest.TestCase):
    def invoke(self, configured, expected=CERT, actual=CERT, extra_signer=False):
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        root = Path(scratch.name)
        tool = root / 'sdk/build-tools/35.0.0/apksigner'
        tool.parent.mkdir(parents=True)
        tool.write_text('#!/bin/sh\nprintf "Signer #1 certificate SHA-256 digest: ' + actual + '\\n"\n' +
                        ('printf "Signer #2 certificate SHA-256 digest: ' + actual + '\\n"\n' if extra_signer else ''))
        tool.chmod(0o755)
        apk = root / 'test.apk'
        apk.write_bytes(b'test-fixture')
        output = root / 'github-output'
        env = dict(os.environ, ANDROID_HOME=str(root / 'sdk'), GITHUB_OUTPUT=str(output),
                   TRADE_REVIEW_EXISTING_KEY_CONFIGURED=configured, TRADE_REVIEW_UPGRADE_CERT_SHA256=expected)
        result = subprocess.run(['bash', str(SCRIPT), str(apk)], cwd=root, env=env, capture_output=True, text=True)
        return result, root, output

    def test_missing_key_is_test_only_and_never_an_upgrade(self):
        result, root, output = self.invoke('false')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('upgrade_verified=false', output.read_text())
        self.assertTrue((root / 'dist/TradeReview-v0.6-test.apk').exists())
        self.assertFalse((root / 'dist/TradeReview-v0.6-stable.apk').exists())
        self.assertIn('no fixed signing key configured', (root / 'dist/SIGNING.txt').read_text())

    def test_matching_existing_certificate_allows_only_upgrade_artifact(self):
        result, root, output = self.invoke('true')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('upgrade_verified=true', output.read_text())
        self.assertTrue((root / 'dist/TradeReview-v0.6-stable.apk').exists())
        self.assertFalse((root / 'dist/TradeReview-v0.6-test.apk').exists())

    def test_wrong_certificate_blocks_all_apk_publication(self):
        result, root, output = self.invoke('true', '0' * 64)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Upgrade publication refused', result.stderr)
        self.assertEqual([], list((root / 'dist').glob('*.apk')))
        self.assertFalse(output.exists())

    def test_fixed_key_without_public_certificate_pin_blocks_publication(self):
        result, root, output = self.invoke('true', '')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], list((root / 'dist').glob('*.apk')))

    def test_replacement_key_with_matching_variable_still_fails_committed_v03_pin(self):
        result, root, output = self.invoke('true', '1' * 64, '1' * 64)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Upgrade publication refused', result.stderr)
        self.assertEqual([], list((root / 'dist').glob('*.apk')))
        self.assertFalse(output.exists())

    def test_multiple_signers_are_rejected(self):
        result, root, output = self.invoke('true', extra_signer=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Exactly one APK signer', result.stderr)
        self.assertFalse(output.exists())

if __name__ == '__main__':
    unittest.main()
