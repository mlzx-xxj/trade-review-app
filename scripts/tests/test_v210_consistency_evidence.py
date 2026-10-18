import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
import sys

spec = importlib.util.spec_from_file_location("consistency", Path(__file__).parents[1] / "v210-consistency-evidence.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)

sys.path.insert(0, str(Path(__file__).parents[1]))
policy_spec = importlib.util.spec_from_file_location("consistency_workflow_policy", Path(__file__).parents[1] / "ci-check-workflow.py")
policy = importlib.util.module_from_spec(policy_spec)
policy_spec.loader.exec_module(policy)


def financial_fixture():
    rows = []
    for index, expected in enumerate(check.EXPECTED, 1):
        row = dict(zip(("accountId", "code", "side", "effect", "price", "quantity", "fees"), expected))
        row.update(id="e" + str(index), ordinal=index, multiplier="100" if index > 3 else None, marginRate=None)
        rows.append(row)
    return dict(
        executions=rows, audits=[], plans=[], reviews=[],
        cycles=[
            dict(id="s", accountId="stock", remaining="500", realized="2487.65", openCost="5500.55", averagePrice="11", fees="12.90"),
            dict(id="f", accountId="futures", remaining="1", realized=None, fees=None, referenceMargin=None),
        ],
        lots=[
            dict(executionId="e1", cycleId="s", remaining="0", allocatedFees="1"),
            dict(executionId="e2", cycleId="s", remaining="500", allocatedFees="0.55"),
            dict(executionId="e4", cycleId="f", remaining="1", allocatedFees=None),
        ],
        matches=[
            dict(cycleId="s", openExecutionId="e1", closeExecutionId="e3", quantity="1000", gross="2000", openFees="1", closeFees="7.2", net="1991.8"),
            dict(cycleId="s", openExecutionId="e2", closeExecutionId="e3", quantity="500", gross="500", openFees=".55", closeFees="3.6", net="495.85"),
            dict(cycleId="f", openExecutionId="e4", closeExecutionId="e5", quantity="1", gross="1000", openFees=None, closeFees=None, net=None),
        ],
    )


class ConsistencyEvidenceTest(unittest.TestCase):
    def test_sixty_minute_exception_is_limited_to_read_only_device_matrix(self):
        document = policy.read(Path(__file__).parents[2] / ".github/workflows/android.yml")
        document["jobs"]["device-tests"]["timeout-minutes"] = "60"
        policy.check(document)
        for name in document["jobs"]:
            value = copy.deepcopy(document)
            value["jobs"][name]["timeout-minutes"] = "61" if name == "device-tests" else "46"
            with self.subTest(job=name), self.assertRaises(ValueError):
                policy.check(value)

    def test_fifo_partial_cost_and_unknown_financial_facts(self):
        ledger = financial_fixture()
        result = check.verify_financial(copy.deepcopy(ledger), ledger)
        self.assertEqual(result["knownRealized"], "2487.65")
        self.assertIsNone(result["unknownNet"])

    def reject_consistent_forgery(self, mutate):
        ledger = financial_fixture()
        mutate(ledger)
        with self.assertRaises(AssertionError):
            check.verify_financial(copy.deepcopy(ledger), ledger)

    def test_identical_before_after_does_not_hide_wrong_net(self):
        self.reject_consistent_forgery(lambda x: x["cycles"][0].update(realized="2500"))

    def test_average_opening_price_must_not_include_unallocated_fees(self):
        self.reject_consistent_forgery(lambda x: x["cycles"][0].update(averagePrice="11.0011"))

    def test_unknown_fee_cannot_be_replaced_with_zero(self):
        self.reject_consistent_forgery(lambda x: x["executions"][3].update(fees="0"))

    def test_unknown_reference_margin_cannot_be_zero(self):
        self.reject_consistent_forgery(lambda x: x["cycles"][1].update(referenceMargin="0"))

    def test_wrong_fifo_is_rejected_even_if_both_snapshots_agree(self):
        self.reject_consistent_forgery(lambda x: x["matches"][0].update(quantity="999"))

    def test_preserving_total_fees_does_not_hide_wrong_allocation(self):
        def mutate(x):
            x["matches"][0]["openFees"] = ".5"
            x["matches"][1]["openFees"] = "1.05"
        self.reject_consistent_forgery(mutate)

    def test_configuration_cannot_change_a_historical_execution(self):
        before = financial_fixture()
        after = copy.deepcopy(before)
        after["executions"][0]["price"] = "9"
        with self.assertRaises(AssertionError):
            check.verify_financial(before, after)

    def test_unknown_match_reference_must_still_use_the_actual_future_open(self):
        self.reject_consistent_forgery(lambda x: x["matches"][2].update(openExecutionId="e1"))

    def archive(self, path, ledger, bad_hash=False):
        raw = json.dumps(dict(schemaVersion=8, backupScope="with-attachment-files", ledger=ledger,
                              exportedAt="2026-10-17T00:00:00Z", checksumSha256="synthetic", attachments=[])).encode()
        manifest = dict(format="trade-review-full-backup", zipVersion=1, images=[],
                        data=dict(path="data.json", bytes=len(raw), sha256="0" * 64 if bad_hash else hashlib.sha256(raw).hexdigest()))
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("data.json", raw)
            z.writestr("manifest.json", json.dumps(manifest))

    def test_actual_zip_bytes_and_restored_facts_are_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "synthetic.zip"
            ledger = financial_fixture()
            self.archive(path, ledger)
            self.assertEqual(check.verify_zip(path, ledger)["zipEntries"], 2)

    def test_wrong_raw_zip_digest_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "synthetic.zip"
            ledger = financial_fixture()
            self.archive(path, ledger, True)
            with self.assertRaises(AssertionError):
                check.verify_zip(path, ledger)

    def test_zip_with_other_ledger_cannot_prove_restore(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "synthetic.zip"
            ledger = financial_fixture()
            self.archive(path, ledger)
            changed = copy.deepcopy(ledger)
            changed["executions"][0]["price"] = "9"
            with self.assertRaises(AssertionError):
                check.verify_zip(path, changed)

    def test_full_backup_metadata_and_attachments_must_also_match(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "synthetic.zip"
            ledger = financial_fixture()
            self.archive(path, ledger)
            whole = dict(schemaVersion=8, backupScope="with-attachment-files", ledger=ledger, attachments=[])
            check.verify_zip(path, ledger, whole)
            whole["attachments"] = [dict(id="unexpected")]
            with self.assertRaises(AssertionError):
                check.verify_zip(path, ledger, whole)


if __name__ == "__main__":
    unittest.main()
