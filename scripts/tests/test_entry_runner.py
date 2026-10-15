import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import tempfile

spec=importlib.util.spec_from_file_location("entry_runner",Path(__file__).resolve().parents[1]/"run-entry-experience.py")
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)


class EntryRunnerTest(unittest.TestCase):
    def test_failed_or_crashed_instrumentation_never_becomes_success(self):
        good="OK (1 test)\nINSTRUMENTATION_CODE: -1"
        self.assertTrue(runner.successful_runner(good))
        for bad in ("", "OK (1 test)", "INSTRUMENTATION_CODE: -1", good+"\nFAILURES!!!", good+"\nProcess crashed", good+"\nshortMsg=error"):
            self.assertFalse(runner.successful_runner(bad))
    def test_refuses_a_phone_before_install_force_stop_or_file_access(self):
        with patch.object(runner,"adb",return_value="0") as adb:
            with self.assertRaisesRegex(AssertionError,"phone"):runner.main()
            adb.assert_called_once_with("shell","getprop","ro.kernel.qemu")
    def test_failed_journey_keeps_raw_log_and_pulls_diagnostics_without_pass_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with patch.object(runner,"ROOT",root),patch.object(Path,"is_file",return_value=True),patch.object(runner,"adb",side_effect=["1","29","Success","Success","FAILURES!!!\nINSTRUMENTATION_CODE: -1",""]) as adb:
                with self.assertRaisesRegex(AssertionError,"Actual UI journey failed"):runner.main()
            self.assertIn("FAILURES!!!",(root/"journey-instrumentation.txt").read_text())
            self.assertEqual(("pull",runner.REMOTE,str(root)),adb.call_args.args)
            self.assertFalse((root/"entry-runner-api29.json").exists())
