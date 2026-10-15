import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

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
