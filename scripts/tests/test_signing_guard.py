"""Verify publication decisions without generating or handling any signing private key."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'check-apk-signature.sh'
CERT = '8272cb328cfdbc2e585ce88b71c1425f18abc5a57d6082bc69b8c305a154eb82'

class SigningGuardTest(unittest.TestCase):
    def invoke(self, configured, expected=CERT):
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        root = Path(scratch.name)
        tool = root / 'sdk/build-tools/35.0.0/apksigner'
        tool.parent.mkdir(parents=True)
        tool.write_text('#!/bin/sh\nprintf "Signer #1 certificate SHA-256 digest: ' + CERT + '\\n"\n')
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
        self.assertTrue((root / 'dist/TradeReview-v0.3-test.apk').exists())
        self.assertFalse((root / 'dist/TradeReview-v0.3-stable.apk').exists())
        self.assertIn('no fixed signing key configured', (root / 'dist/SIGNING.txt').read_text())

    def test_matching_existing_certificate_allows_only_upgrade_artifact(self):
        result, root, output = self.invoke('true')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('upgrade_verified=true', output.read_text())
        self.assertTrue((root / 'dist/TradeReview-v0.3-stable.apk').exists())
        self.assertFalse((root / 'dist/TradeReview-v0.3-test.apk').exists())

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

if __name__ == '__main__':
    unittest.main()
