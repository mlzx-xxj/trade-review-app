"""Exercise the actual workflow conditions and artifact/signing failure boundaries."""
import ast
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPTS=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(SCRIPTS))
import ci_security as security


def load(name,filename):
    spec=importlib.util.spec_from_file_location(name,SCRIPTS/filename)
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value


workflow=load("workflow","ci-check-workflow.py")
artifacts=load("artifacts","ci-artifacts.py")
signer=load("signer","ci-sign-original.py")
publisher=load("publisher","ci-publish.py")


def condition(expression,values):
    """Small AST interpreter for the real YAML expressions; no Python eval."""
    text=expression.replace("&&"," and ").replace("||"," or ")
    text=re.sub(r"!(?!=)"," not ",text).strip()
    text=re.sub(r"(?:github|needs)\.[A-Za-z0-9_.-]+",lambda m:"v["+repr(m[0])+"]",text)
    def visit(node):
        if isinstance(node,ast.Constant):return node.value
        if isinstance(node,ast.BoolOp):return (all if isinstance(node.op,ast.And) else any)(visit(v) for v in node.values)
        if isinstance(node,ast.UnaryOp) and isinstance(node.op,ast.Not):return not visit(node.operand)
        if isinstance(node,ast.Subscript):return values.get(visit(node.slice),"")
        if isinstance(node,ast.Compare):
            left=visit(node.left)
            for op,other in zip(node.ops,node.comparators):
                right=visit(other)
                if not (left==right if isinstance(op,ast.Eq) else left!=right):return False
                left=right
            return True
        if isinstance(node,ast.Call) and isinstance(node.func,ast.Name) and node.func.id=="cancelled" and not node.args:return values.get("cancelled",False)
        raise ValueError("Unexpected workflow expression")
    return bool(visit(ast.parse(text,mode="eval").body))


class WorkflowSecurityTest(unittest.TestCase):
    def setUp(self):
        self.document=workflow.read(SCRIPTS.parent/".github/workflows/android.yml")
        self.good={"github.repository":security.REPOSITORY,"github.ref":"refs/heads/main","github.event_name":"push",
                   "needs.build.result":"success","needs.device-tests.result":"success","needs.build.outputs.trusted_main":"true",
                   "needs.build.outputs.signing_allowed":"true","needs.trusted-signing.result":"success",
                   "needs.trusted-signing.outputs.configured":"true","needs.signed-install.result":"success"}
    def allowed(self,job,**changes):return condition(self.document["jobs"][job]["if"],dict(self.good,**changes))
    def test_three_triggers_parse_and_actual_workflow_passes_policy(self):workflow.check(self.document)
    def test_same_repository_and_fork_pr_both_deny_signing_and_publication(self):
        for fork in (False,True):
            for job in ("trusted-signing","signed-install","publish"):
                with self.subTest(fork=fork,job=job):
                    self.assertFalse(self.allowed(job,**{"github.event_name":"pull_request","github.ref":"refs/pull/11/merge","fork":fork}))
    def test_pull_request_target_never_passes_even_with_fake_main_ref(self):
        for job in ("trusted-signing","publish"):self.assertFalse(self.allowed(job,**{"github.event_name":"pull_request_target"}))
    def test_main_push_and_main_manual_are_allowed_only_after_checks(self):
        for event in ("push","workflow_dispatch"):
            for job in ("trusted-signing","publish"):self.assertTrue(self.allowed(job,**{"github.event_name":event}))
    def test_non_main_manual_and_fork_repository_are_denied(self):
        for changes in ({"github.event_name":"workflow_dispatch","github.ref":"refs/heads/codex/public-ci-security"},{"github.repository":"attacker/trade-review-app"}):
            for job in ("trusted-signing","publish"):self.assertFalse(self.allowed(job,**changes))
    def test_failure_skipped_cancelled_build_or_emulator_matrix_blocks_publish(self):
        for dependency in ("build","device-tests"):
            for result in ("failure","cancelled","skipped",""):
                for job in ("trusted-signing","publish"):self.assertFalse(self.allowed(job,**{f"needs.{dependency}.result":result}))
        self.assertFalse(self.allowed("publish",cancelled=True))
    def test_no_safe_environment_denies_private_step_but_allows_verified_rc(self):
        self.assertFalse(self.allowed("trusted-signing",**{"needs.build.outputs.signing_allowed":"false"}))
        self.assertTrue(self.allowed("publish",**{"needs.trusted-signing.result":"skipped","needs.trusted-signing.outputs.configured":"","needs.signed-install.result":"skipped"}))
    def test_empty_secrets_can_publish_rc_but_partial_bad_signing_cannot(self):
        self.assertTrue(self.allowed("publish",**{"needs.trusted-signing.outputs.configured":"false","needs.signed-install.result":"skipped"}))
        self.assertFalse(self.allowed("publish",**{"needs.trusted-signing.result":"failure","needs.trusted-signing.outputs.configured":"false"}))
        self.assertFalse(self.allowed("publish",**{"needs.signed-install.result":"failure"}))
        self.assertFalse(self.allowed("publish",**{"needs.signed-install.result":"skipped"}))
    def test_read_only_pr_jobs_and_only_publisher_writes(self):
        for name in ("build","device-tests","trusted-signing","signed-install"):self.assertEqual("read",self.document["jobs"][name]["permissions"]["contents"])
        self.assertEqual("write",self.document["jobs"]["publish"]["permissions"]["contents"])
    def test_mutations_exposing_secrets_or_write_token_are_rejected(self):
        for mutate in (lambda d:d["jobs"]["build"]["permissions"].update(contents="write"),lambda d:d["jobs"]["build"]["steps"].append({"run":"echo blocked","env":{"KEY":"${{ secrets.PRIVATE }}"}}),lambda d:d["on"].update(pull_request_target={}),lambda d:d["jobs"]["trusted-signing"].update(environment="unprotected")):
            value=copy.deepcopy(self.document);mutate(value)
            with self.assertRaises(ValueError):workflow.check(value)
    def test_main_gate_or_api_or_action_pin_cannot_be_removed(self):
        for mutate in (lambda d:d["jobs"]["publish"].update({"if":"true"}),lambda d:d["jobs"]["device-tests"]["strategy"]["matrix"].update(include=[{"api":"29"}]),lambda d:d["jobs"]["build"]["steps"][0].update(uses="actions/checkout@v5")):
            value=copy.deepcopy(self.document);mutate(value)
            with self.assertRaises(ValueError):workflow.check(value)


class ContextAndEnvironmentTest(unittest.TestCase):
    def setUp(self):
        self.c=dict(repository=security.REPOSITORY,event="push",ref="refs/heads/main",sha="a"*40,source="a"*40,workflow=security.REPOSITORY+"/.github/workflows/android.yml@refs/heads/main")
    def test_real_main_context_and_matching_sha_required(self):
        security.require_trusted(self.c,{"build":"success","device-tests":"success"})
        for changes in ({"event":"pull_request"},{"event":"workflow_run"},{"source":"b"*40},{"workflow":"evil.yml@main"},{"ref":"refs/tags/main"}):
            with self.assertRaises(ValueError):security.require_trusted(dict(self.c,**changes))
    def test_failed_or_incomplete_verification_results_denied(self):
        for results in ({"build":"success"},{"build":"success","device-tests":"failure"}):
            with self.assertRaises(ValueError):security.require_trusted(self.c,results)
    def test_only_preexisting_exact_main_branch_environment_is_safe(self):
        env={"name":security.ENVIRONMENT,"deployment_branch_policy":{"custom_branch_policies":True,"protected_branches":False}}
        self.assertTrue(security.environment_safe(env,{"branch_policies":[{"name":"main","type":"branch"}]}))
        for rules in ([],[{"name":"*","type":"branch"}],[{"name":"main","type":"tag"}],[{"name":"main","type":"branch"},{"name":"codex/*","type":"branch"}]):self.assertFalse(security.environment_safe(env,{"branch_policies":rules}))
        self.assertFalse(security.environment_safe(dict(env,deployment_branch_policy=None),{"branch_policies":[{"name":"main","type":"branch"}]}))
    def test_private_signer_rejects_pr_before_decoding_or_process_execution(self):
        with patch.dict(os.environ,{"GITHUB_EVENT_NAME":"pull_request"},clear=True),patch.object(signer.base64,"b64decode") as decode,patch.object(signer.subprocess,"run") as run:
            with self.assertRaises(ValueError):signer.main()
            decode.assert_not_called();run.assert_not_called()
    def test_wrong_public_pin_rejected_before_opening_private_material(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);(root/"verified-signing-inputs").mkdir();(root/"verified-signing-inputs/app-release-unsigned.apk").write_bytes(b"synthetic unsigned fixture")
            environment={"GITHUB_REPOSITORY":security.REPOSITORY,"GITHUB_EVENT_NAME":"push","GITHUB_REF":"refs/heads/main","GITHUB_SHA":"a"*40,"SOURCE_COMMIT":"a"*40,"GITHUB_WORKFLOW_REF":self.c["workflow"],"GITHUB_RUN_ID":"123","GITHUB_RUN_ATTEMPT":"1","VERIFIED_INPUTS":"verified-signing-inputs","TRADE_REVIEW_UPGRADE_CERT_SHA256":"f"*64,"EXISTING_KEY_BASE64":"synthetic","EXISTING_STORE_PASSWORD":"synthetic","EXISTING_KEY_ALIAS":"synthetic","EXISTING_KEY_PASSWORD":"synthetic"}
            old=Path.cwd()
            try:
                os.chdir(root)
                with patch.dict(os.environ,environment):
                    sha=security.manifest("verified-signing-inputs","signing-inputs");os.environ["INPUTS_DIGEST"]=sha
                    (root/"docs").mkdir();(root/"docs/V03-CERTIFICATE-SHA256.txt").write_text(security.PIN)
                    with patch.object(signer.base64,"b64decode") as decode:
                        with self.assertRaisesRegex(ValueError,"pin"):signer.main()
                        decode.assert_not_called()
            finally:os.chdir(old)


class ArtifactBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        self.environment=patch.dict(os.environ,{"GITHUB_REPOSITORY":security.REPOSITORY,"SOURCE_COMMIT":"a"*40,"GITHUB_RUN_ID":"123","GITHUB_RUN_ATTEMPT":"2"});self.environment.start();self.addCleanup(self.environment.stop)
    def fixture(self):
        root=self.root/"artifact";root.mkdir();(root/"TradeReview-v2.0-RC.apk").write_bytes(b"synthetic-apk-not-installable")
        return root,security.manifest(root,"rc")
    def test_round_trip_binds_actual_sha_run_repository_and_file_inventory(self):
        root,digest=self.fixture();self.assertEqual(123,security.verify_manifest(root,"rc",digest)["runId"])
    def test_changed_missing_and_added_apk_are_rejected(self):
        for operation in ("changed","missing","added"):
            with self.subTest(operation=operation):
                if (self.root/"artifact").exists():import shutil;shutil.rmtree(self.root/"artifact")
                root,digest=self.fixture();file=root/"TradeReview-v2.0-RC.apk"
                if operation=="changed":file.write_bytes(b"changed")
                elif operation=="missing":file.unlink()
                else:(root/"unexpected.apk").write_bytes(b"extra")
                with self.assertRaises(ValueError):security.verify_manifest(root,"rc",digest)
    def test_changed_manifest_wrong_sha_run_or_kind_are_rejected(self):
        root,digest=self.fixture()
        with self.assertRaises(ValueError):security.verify_manifest(root,"rc","f"*64)
        for changes in ({"SOURCE_COMMIT":"b"*40},{"GITHUB_RUN_ID":"124"},{"GITHUB_REPOSITORY":"attacker/fork"}):
            with patch.dict(os.environ,changes):
                with self.assertRaises(ValueError):security.verify_manifest(root,"rc",digest)
        with self.assertRaises(ValueError):security.verify_manifest(root,"signing-inputs",digest)
    def test_artifact_symlink_is_rejected(self):
        root,digest=self.fixture();(root/"link").symlink_to(root/"TradeReview-v2.0-RC.apk")
        with self.assertRaises(ValueError):security.verify_manifest(root,"rc",digest)
    def test_nested_manifest_is_not_an_unverified_file(self):
        root,digest=self.fixture();(root/"nested").mkdir();(root/"nested/MANIFEST.json").write_text("unexpected")
        with self.assertRaises(ValueError):security.verify_manifest(root,"rc",digest)
    def test_missing_remote_artifact_or_wrong_run_aborts(self):
        run={"head_sha":"a"*40,"repository":{"full_name":security.REPOSITORY}}
        with patch.object(artifacts,"api",side_effect=[run,{"artifacts":[]}]):
            with self.assertRaisesRegex(ValueError,"missing"):artifacts.download("rc-apk-attempt1",self.root/"out","rc")
        with patch.object(artifacts,"api",return_value=dict(run,head_sha="b"*40)):
            with self.assertRaisesRegex(ValueError,"source"):artifacts.download("rc-apk-attempt1",self.root/"out","rc")
    def test_path_traversal_duplicate_symlink_and_oversized_zip_are_rejected(self):
        import warnings
        for kind in ("traversal","duplicate","symlink","large"):
            stream=io.BytesIO()
            with warnings.catch_warnings():
                warnings.simplefilter("ignore",UserWarning)
                with zipfile.ZipFile(stream,"w") as z:
                    if kind=="traversal":z.writestr("../escape",b"no")
                    elif kind=="duplicate":z.writestr("same",b"1");z.writestr("same",b"2")
                    elif kind=="symlink":entry=zipfile.ZipInfo("link");entry.external_attr=(stat.S_IFLNK|0o777)<<16;z.writestr(entry,b"../outside")
                    else:z.writestr("large",b"123")
            stream.seek(0)
            with patch.object(artifacts,"MAX_BYTES",2 if kind=="large" else 512*1024*1024):
                with self.assertRaises(ValueError):artifacts.unpack(stream,self.root/kind)
    def test_publication_has_exact_whitelist_and_no_private_file(self):
        root=self.root/"release";root.mkdir()
        with self.assertRaises(ValueError):publisher.asset_names(root)
        (root/"private.keystore").write_bytes(b"synthetic-no-key")
        with self.assertRaises(ValueError):publisher.asset_names(root)


if __name__=="__main__":unittest.main()
