"""Visual evidence is synthetic but must come from successful real device runs."""
import importlib.util
from pathlib import Path
import sys
import unittest
import copy
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location("visual_runner",ROOT/"scripts/run-v210-visual.py")
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
spec=importlib.util.spec_from_file_location("visual_report",ROOT/"scripts/v210-visual-report.py")
report=importlib.util.module_from_spec(spec);spec.loader.exec_module(report)


class VisualRunnerTest(unittest.TestCase):
    def test_native_runner_needs_explicit_success_and_no_failure(self):
        ok="OK (1 test)\nINSTRUMENTATION_CODE: -1\n"
        self.assertTrue(runner.entry.successful_runner(ok))
        for failure in ("FAILURES", "Process crashed", "INSTRUMENTATION_FAILED", "shortMsg=", "TIMEOUT:"):
            self.assertFalse(runner.entry.successful_runner(ok+failure))
        self.assertFalse(runner.entry.successful_runner("INSTRUMENTATION_CODE: -1"))
        self.assertFalse(runner.entry.successful_runner("OK (1 test)"))

    def test_four_real_configurations_and_required_scenes(self):
        self.assertEqual(("regular-light","regular-dark","narrow-light","narrow-dark"),runner.PROFILES)
        self.assertTrue({"stock-filled","futures-filled","keyboard","stock-position","futures-position","partial-position","pending-filled","pending-position","empty"}.issubset(runner.SCENES))
        self.assertEqual(len(runner.SCENES),len(set(runner.SCENES)))

    def test_physical_phone_rejected_before_any_mutation(self):
        with patch.object(runner.entry,"adb",return_value="0") as adb:
            with self.assertRaisesRegex(AssertionError,"phone"):runner.main()
            self.assertEqual([(("shell","getprop","ro.kernel.qemu"),)],[(v.args,) for v in adb.call_args_list])

    def example(self):
        return dict(observations=[dict(code="rb2701",action="买入开仓",price="3500",quantity="2",savedFees="7.01",remaining="2",realized="0",extraConfirmation=False,fieldsTyped=["execution-code","execution-price","execution-quantity"],saveVisible=True,tapsIncludingEntryAndSave=6,requiredScrollCommands=1)])

    def test_comparison_rejects_changed_financial_facts(self):
        before=self.example()
        for key,value in (("savedFees","0"),("remaining","3"),("realized","1"),("quantity","1")):
            after=copy.deepcopy(before);after["observations"][0][key]=value
            with self.subTest(key=key),self.assertRaisesRegex(AssertionError,"financial"):report.compare(before,after)

    def test_comparison_rejects_more_work_or_hidden_save(self):
        before=self.example()
        for key,value in (("tapsIncludingEntryAndSave",7),("requiredScrollCommands",2),("extraConfirmation",True),("saveVisible",False),("fieldsTyped",["extra-required-field"])):
            after=copy.deepcopy(before);after["observations"][0][key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):report.compare(before,after)

    def test_comparison_records_real_reduction_without_changing_facts(self):
        before=self.example();after=copy.deepcopy(before)
        after["observations"][0].update(tapsIncludingEntryAndSave=5,requiredScrollCommands=0)
        row=report.compare(before,after)[0]
        self.assertEqual((6,5,1,0,3),(row["beforeTaps"],row["afterTaps"],row["beforeScrolls"],row["afterScrolls"],row["fields"]))


if __name__=="__main__":unittest.main()
