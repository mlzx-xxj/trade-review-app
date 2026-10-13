"""Publication decision tests only; real aapt/apksigner run separately on compiled APKs."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('v100',Path(__file__).resolve().parents[1]/'v100-identity.py');gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
SOURCE='a'*40


def rc():return dict(applicationId='com.tradereview.app.rc',versionCode=10,versionName='1.0.0-rc.1',debuggable=False,certificateSha256='b'*64,apkSha256='c'*64,apkBytes=100,abis=['arm64-v8a','armeabi-v7a','x86','x86_64'])
def status(configured=False):return dict(sourceCommit=SOURCE,configured=configured,originalPinVerified=configured,realPhoneAcceptance='not-confirmed')


class IdentityTest(unittest.TestCase):
    def test_non_debuggable_isolated_rc_is_allowed(self):gate.require_identity(rc(),'rc')
    def test_rc_cannot_impersonate_original_identity(self):
        data=rc();data['certificateSha256']=gate.PIN
        with self.assertRaises(ValueError):gate.require_identity(data,'rc')
    def test_formal_release_requires_original_pin(self):
        data=rc();data.update(applicationId='com.tradereview.app',versionName='1.0.0')
        with self.assertRaises(ValueError):gate.require_identity(data,'release')
        data['certificateSha256']=gate.PIN;gate.require_identity(data,'release')
    def test_debuggable_or_wrong_version_or_package_rejected(self):
        for key,value in [('debuggable',True),('versionCode',8),('versionName','1.0.0'),('applicationId','com.tradereview.app')]:
            data=rc();data[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):gate.require_identity(data,'rc')
    def test_delivery_metadata_must_match_real_apk_and_never_claim_stable(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'TradeReview-v1.0-RC.apk').write_bytes(b'fake-unit-fixture')
            (root/'SIGNING-STATUS.json').write_text(json.dumps(status()))
            data=rc();saved=dict(data,sourceCommit=SOURCE,stablePublished=False,phoneAcceptance='not-confirmed',upgradeIdentityVerified=False,buildType='rc',classification='isolated-rc')
            meta=root/'RC-IDENTITY.json';meta.write_text(json.dumps(saved))
            with patch.object(gate,'inspect',return_value=data):
                gate.verify_delivery(root,'fake-aapt','fake-signer',SOURCE)
                for key,value in [('stablePublished',True),('phoneAcceptance','passed'),('upgradeIdentityVerified',True),('apkSha256','d'*64),('sourceCommit','e'*40),('buildType','debug'),('classification','stable-release')]:
                    meta.write_text(json.dumps(dict(saved,**{key:value})))
                    with self.subTest(key=key),self.assertRaises(ValueError):gate.verify_delivery(root,'fake-aapt','fake-signer',SOURCE)
    def test_configured_secrets_cannot_be_claimed_without_fixed_apk(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            for value in [status(True),dict(status(),originalPinVerified=True),dict(status(),realPhoneAcceptance='passed'),dict(status(),configured=0)]:
                (root/'SIGNING-STATUS.json').write_text(json.dumps(value))
                with self.subTest(value=value),self.assertRaises(ValueError):gate.verify_delivery(root,'fake-aapt','fake-signer',SOURCE)
    def test_unexpected_apk_is_rejected_before_inspection(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'SIGNING-STATUS.json').write_text(json.dumps(status()))
            (root/'random-debug.apk').write_bytes(b'fake')
            with self.assertRaises(ValueError):gate.verify_delivery(root,'fake-aapt','fake-signer',SOURCE)


if __name__=='__main__':unittest.main()
