"""Public identity and mocked failure-boundary tests; no real key/signature is created."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(SCRIPTS))
import signing_identity as policy


def load(name,file):
    spec=importlib.util.spec_from_file_location(name,SCRIPTS/file)
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value


gate=load("v2_identity","v200-identity.py")
signer=load("permanent_signer","ci-sign-original.py")
workflow=load("permanent_workflow","ci-check-workflow.py")
SOURCE="a"*40


def apk(cert=policy.PIN,code=20):
    return dict(applicationId="com.tradereview.app",versionName="2.0.0",versionCode=code,
                certificateSha256=cert,debuggable=False,apkSha256="c"*64,apkBytes=100,abis=[])


class PermanentIdentityTest(unittest.TestCase):
    def test_new_pin_and_historical_evidence_are_distinct_and_committed(self):
        policy.require_committed_identity()
        self.assertEqual("29b2f82887a0591d5b1d1f9ab148b90c0b658088530e3bcc1bd7e5fee64f2af9",policy.PIN)
        self.assertEqual("30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c",policy.LEGACY_V03_PIN)
        self.assertNotEqual(policy.PIN,policy.LEGACY_V03_PIN)
    def test_old_pin_is_never_a_new_formal_signer(self):
        gate.require_identity(apk(),"release")
        for cert in (policy.LEGACY_V03_PIN,"b"*64):
            with self.assertRaisesRegex(ValueError,"permanent V2"):gate.require_identity(apk(cert),"release")
    def test_rc_rejects_both_formal_identities(self):
        for cert in (policy.PIN,policy.LEGACY_V03_PIN):
            value=apk(cert);value.update(applicationId="com.tradereview.app.rc.v200",versionName="2.0.0-rc.1")
            with self.assertRaises(ValueError):gate.require_identity(value,"rc")
    def test_old_v03_and_new_v2_same_package_are_expected_incompatible(self):
        self.assertEqual("incompatible-certificate-backup-reinstall-restore-required",policy.upgrade_policy(apk(policy.LEGACY_V03_PIN,3),apk()))
    def test_future_upgrade_needs_same_new_certificate_and_increasing_code(self):
        self.assertEqual("same-permanent-identity-upgrade-eligible-not-device-verified",policy.upgrade_policy(apk(),apk(code=21)))
        for code in (19,20):self.assertEqual("versionCode-must-increase",policy.upgrade_policy(apk(),apk(code=code)))
        self.assertEqual("not-current-permanent-identity",policy.upgrade_policy(apk("b"*64),apk("b"*64,21)))
    def test_separate_rc_is_not_an_old_data_upgrade(self):
        rc=apk();rc["applicationId"]="com.tradereview.app.rc.v200"
        self.assertEqual("separate-application",policy.upgrade_policy(apk(),rc))
    def test_environment_variable_cannot_fall_back_to_old_upgrade_name(self):
        doc=workflow.read(SCRIPTS.parent/".github/workflows/android.yml")
        step=next(s for s in doc["jobs"]["trusted-signing"]["steps"] if s.get("id")=="signing")
        step["env"]["TRADE_REVIEW_UPGRADE_CERT_SHA256"]=step["env"].pop("TRADE_REVIEW_SIGNING_CERT_SHA256")
        with self.assertRaisesRegex(ValueError,"permanent Environment"):workflow.check(doc)
    def test_candidate_never_claims_old_upgrade_or_phone_acceptance(self):
        for kind in ("rc","release"):
            meta=gate.identity_metadata(kind)
            self.assertFalse(meta["legacyV03UpgradeCompatible"])
            self.assertFalse(meta["stablePublished"])
            self.assertEqual("not-confirmed",meta["phoneAcceptance"])
            self.assertEqual("not-executed",meta["sameSignerUpgrade"])


class PrivateBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.old=Path.cwd();os.chdir(self.temp.name);self.addCleanup(lambda:os.chdir(self.old))
        self.root=Path(self.temp.name);(self.root/"verified-signing-inputs").mkdir()
        (self.root/"verified-signing-inputs/app-release-unsigned.apk").write_bytes(b"mock unsigned, not a real APK")
        self.environment=dict(SOURCE_COMMIT=SOURCE,VERIFIED_INPUTS="verified-signing-inputs",INPUTS_DIGEST="d"*64,
            ANDROID_HOME=self.temp.name,RUNNER_TEMP=self.temp.name,GITHUB_OUTPUT=str(self.root/"outputs"),
            TRADE_REVIEW_SIGNING_CERT_SHA256=policy.PIN,EXISTING_KEY_ALIAS=policy.ALIAS,
            EXISTING_KEY_BASE64="c3ludGhldGlj",EXISTING_STORE_PASSWORD="synthetic",EXISTING_KEY_PASSWORD="synthetic")
        for target in ("require_trusted","verify_manifest"):
            mock=patch.object(signer,target);mock.start();self.addCleanup(mock.stop)
        mock=patch.dict(os.environ,self.environment,clear=True);mock.start();self.addCleanup(mock.stop)
    def test_absent_secrets_produces_rc_only_status_without_reading_a_key(self):
        for key in ("EXISTING_KEY_BASE64","EXISTING_STORE_PASSWORD","EXISTING_KEY_ALIAS","EXISTING_KEY_PASSWORD"):os.environ.pop(key)
        with patch.object(signer.base64,"b64decode") as decode,patch.object(signer,"run_private") as command:
            signer.main();decode.assert_not_called();command.assert_not_called()
        status=json.loads(Path("dist/SIGNING-STATUS.json").read_text())
        self.assertFalse(status["configured"]);self.assertFalse(status["permanentPinVerified"])
        self.assertFalse(status["legacyV03UpgradeCompatible"])
        self.assertEqual([],list(Path("dist").glob("*.apk")))
    def test_each_partial_secret_configuration_fails_before_key_decode(self):
        for missing in ("EXISTING_KEY_BASE64","EXISTING_STORE_PASSWORD","EXISTING_KEY_ALIAS","EXISTING_KEY_PASSWORD"):
            with patch.dict(os.environ,self.environment,clear=True):
                os.environ.pop(missing)
                with patch.object(signer.base64,"b64decode") as decode:
                    with self.assertRaisesRegex(ValueError,"incomplete"):signer.main()
                    decode.assert_not_called()
    def test_wrong_missing_old_pin_or_wrong_alias_fails_before_key_decode(self):
        for variable,value in (("TRADE_REVIEW_SIGNING_CERT_SHA256",policy.LEGACY_V03_PIN),("TRADE_REVIEW_SIGNING_CERT_SHA256",""),("TRADE_REVIEW_SIGNING_CERT_SHA256","f"*64),("EXISTING_KEY_ALIAS","wrong")):
            with patch.dict(os.environ,{variable:value}),patch.object(signer.base64,"b64decode") as decode:
                with self.assertRaises(ValueError):signer.main()
                decode.assert_not_called()
    def test_invalid_encoding_removes_private_temp_file(self):
        with patch.dict(os.environ,EXISTING_KEY_BASE64="%invalid%"):
            with self.assertRaisesRegex(ValueError,"encoding invalid"):signer.main()
        self.assertEqual([],list(self.root.glob("*.keystore")))
    def test_apksigner_certificate_mismatch_cleans_0600_temp_and_refuses_status(self):
        observed=[]
        def command(args):
            if args[1]=="sign":
                key=Path(args[args.index("--ks")+1]);observed.append(key)
                self.assertEqual(0o600,stat.S_IMODE(key.stat().st_mode))
                self.assertEqual("PKCS12",args[args.index("--ks-type")+1])
                self.assertIn("env:EXISTING_STORE_PASSWORD",args)
                self.assertNotIn("synthetic",args)
                return ""
            return "Signer #1 certificate SHA-256 digest: "+policy.LEGACY_V03_PIN+"\n"
        with patch.object(signer,"run_private",side_effect=command):
            with self.assertRaisesRegex(ValueError,"certificate mismatch"):signer.main()
        self.assertTrue(observed);self.assertTrue(all(not p.exists() for p in observed))
        self.assertFalse(Path("dist/SIGNING-STATUS.json").exists())
    def test_private_diagnostics_are_suppressed(self):
        import subprocess
        result=subprocess.CompletedProcess([],1,stdout=b"synthetic secret",stderr=b"synthetic password")
        with patch.object(signer.subprocess,"run",return_value=result):
            with self.assertRaisesRegex(ValueError,"private diagnostics suppressed") as error:signer.run_private(["fake"])
        self.assertNotIn("synthetic",str(error.exception))


class InstallCompatibilityProbeTest(unittest.TestCase):
    def invoke(self,failure="INSTALL_FAILED_UPDATE_INCOMPATIBLE"):
        import subprocess
        probe=load("install_probe","run-v200-package-probes.py")
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);(root/"TradeReview-v2.0-fixed-candidate.apk").write_bytes(b"synthetic")
            rc=apk("b"*64);rc.update(applicationId="com.tradereview.app.rc.v200",versionName="2.0.0-rc.1")
            old=apk(policy.LEGACY_V03_PIN,3);old["debuggable"]=True
            calls=[]
            def adb(*args,**kwargs):
                calls.append(args)
                if args==("shell","getprop","ro.kernel.qemu"):return subprocess.CompletedProcess([],0,"1","")
                if args==("shell","getprop","ro.build.version.sdk"):return subprocess.CompletedProcess([],0,"29","")
                if args==("shell","pm","path","com.tradereview.app"):return subprocess.CompletedProcess([],0,"package:/synthetic/old/base.apk","")
                if args==("install","-r",str(root/"TradeReview-v2.0-fixed-candidate.apk")):
                    return subprocess.CompletedProcess([],1,"",failure)
                return subprocess.CompletedProcess([],0,"Success","")
            with patch.object(probe,"ROOT",root),patch.object(probe,"adb",side_effect=adb),patch.object(probe,"capture"),patch.object(probe,"start"),patch.object(probe,"window"),patch.object(probe.identity,"inspect",side_effect=[rc,apk(),old]),patch.object(sys,"argv",["probe","--directory",temp,"--aapt","mock","--apksigner","mock","--old-apk","synthetic-old.apk"]):
                probe.main()
            report=json.loads((root/"package-probes-api29.json").read_text())
            return report,calls
    def test_real_probe_requires_android_signature_rejection_and_keeps_old_data_until_verified(self):
        report,calls=self.invoke()
        self.assertFalse(report["legacyV03UpgradeCompatible"])
        self.assertEqual("passed-android-rejected-different-certificate-old-install-and-marker-preserved",report["legacyV03IncompatibleInstall"])
        self.assertEqual("not-executed-no-earlier-permanent-apk",report["samePermanentSignerUpgrade"])
        start=calls.index(("install","synthetic-old.apk"))
        verified=calls.index(("shell","run-as","com.tradereview.app","test","-f","files/v2-migration-synthetic-marker"))
        self.assertFalse(any(c[0]=="uninstall" for c in calls[start:verified]))
        self.assertFalse(any("LegacyUpgradeInstrumentation" in str(c) for c in calls))
    def test_other_install_failure_cannot_pass_as_expected_incompatibility(self):
        with self.assertRaisesRegex(AssertionError,"Different certificate"):self.invoke("INSTALL_FAILED_INSUFFICIENT_STORAGE")


if __name__=="__main__":unittest.main()
