import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import tempfile
import subprocess

spec=importlib.util.spec_from_file_location("entry_runner",Path(__file__).resolve().parents[1]/"run-entry-experience.py")
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)


class EntryRunnerTest(unittest.TestCase):
    def test_failed_or_crashed_instrumentation_never_becomes_success(self):
        good="OK (1 test)\nINSTRUMENTATION_CODE: -1"
        self.assertTrue(runner.successful_runner(good))
        for bad in ("", "OK (1 test)", "INSTRUMENTATION_CODE: -1", good+"\nFAILURES!!!", good+"\nProcess crashed", good+"\nshortMsg=error",good+"\nTIMEOUT: stopped"):
            self.assertFalse(runner.successful_runner(bad))
    def test_refuses_a_phone_before_install_force_stop_or_file_access(self):
        with patch.object(runner,"adb",return_value="0") as adb:
            with self.assertRaisesRegex(AssertionError,"phone"):runner.main()
            adb.assert_called_once_with("shell","getprop","ro.kernel.qemu")
    def test_failed_journey_keeps_raw_log_and_collects_diagnostics_without_pass_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with patch.object(runner,"ROOT",root),patch.object(Path,"is_file",return_value=True),patch.object(runner,"collect_output") as collect,patch.object(runner,"adb",side_effect=["1","29","Success","Success","/data/user/0/"+runner.APP,"FAILURES!!!\nINSTRUMENTATION_CODE: -1"]):
                with self.assertRaisesRegex(AssertionError,"Actual UI journey failed"):runner.main()
            self.assertIn("FAILURES!!!",(root/"journey-instrumentation.txt").read_text())
            collect.assert_called_once_with(29)
            self.assertFalse((root/"entry-runner-api29.json").exists())
    def test_collector_reads_fixed_private_files_without_storage_permissions_or_archive_extraction(self):
        relative="journey/entry-experience/70-entry-stock-filled.png"
        def read(args,**kwargs):
            self.assertEqual(["adb","shell","-T","run-as",runner.APP,"cat"],args[:6])
            self.assertTrue(args[6].startswith("files/entry-experience/"));self.assertNotIn("..",args[6])
            return subprocess.CompletedProcess(args,0,b"synthetic-unit-bytes",b"") if args[6]==runner.REMOTE+"/"+relative else subprocess.CompletedProcess(args,1,b"",b"No such file or directory")
        with tempfile.TemporaryDirectory() as tmp,patch.object(runner,"ROOT",Path(tmp)),patch.object(runner.subprocess,"run",side_effect=read):
            runner.collect_output(29)
            self.assertEqual(b"synthetic-unit-bytes",(Path(tmp)/"private-output"/relative).read_bytes())
    def test_collector_rejects_permission_errors_and_oversize_files(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(runner,"ROOT",Path(tmp)):
            with patch.object(runner.subprocess,"run",return_value=subprocess.CompletedProcess([],1,b"",b"Permission denied")):
                with self.assertRaisesRegex(RuntimeError,"private UI test evidence"):runner.collect_output(35)
            with patch.object(runner.subprocess,"run",return_value=subprocess.CompletedProcess([],0,b"x"*(8*1024*1024+1),b"")):
                with self.assertRaisesRegex(AssertionError,"exceeds bound"):runner.collect_output(35)
            self.assertFalse(list(Path(tmp).rglob("*.png")))
    def test_unexpected_private_directory_cannot_become_an_instrumentation_argument(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(runner,"ROOT",Path(tmp)),patch.object(Path,"is_file",return_value=True),patch.object(runner,"adb",side_effect=["1","29","Success","Success","/tmp/untrusted-dir"]) as adb:
            with self.assertRaisesRegex(AssertionError,"private fixture directory"):runner.main()
            self.assertFalse(any("instrument" in c.args for c in adb.call_args_list))
    def test_timeout_keeps_partial_runner_stdout_and_cannot_write_a_pass_report(self):
        timeout=subprocess.TimeoutExpired("adb",900,output=b"INSTRUMENTATION_STATUS: class=stuck")
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with patch.object(runner,"ROOT",root),patch.object(Path,"is_file",return_value=True),patch.object(runner,"collect_output"),patch.object(runner,"adb",side_effect=["1","29","Success","Success","/data/user/0/"+runner.APP,timeout]):
                with self.assertRaises(subprocess.TimeoutExpired):runner.main()
            self.assertIn("class=stuck",(root/"journey-instrumentation.txt").read_text())
            self.assertIn("TIMEOUT:",(root/"journey-instrumentation.txt").read_text())
            self.assertFalse((root/"entry-runner-api29.json").exists())
