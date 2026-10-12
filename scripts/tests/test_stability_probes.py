"""Probe failure handling only: fake adb does not represent an Android UI test."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location("probes",Path(__file__).resolve().parents[1]/"run-stability-probes.py")
probes=importlib.util.module_from_spec(spec);spec.loader.exec_module(probes)
HOME='<hierarchy><node package="com.tradereview.app" text="首页"/><node package="com.tradereview.app" text="快捷记账 · 恢复草稿"/></hierarchy>'


class ProbeTests(unittest.TestCase):
    def run_probe(self, responses, clocks):
        values=iter(responses);clock=iter(clocks);calls=[]
        def fake_adb(*args, **kwargs):
            calls.append(args)
            if args[:2]==("exec-out","cat"):return SimpleNamespace(stdout=next(values),stderr="",returncode=0)
            if args[:2]==("exec-out","screencap"):return SimpleNamespace(stdout=b"\x89PNG-test-only",stderr=b"",returncode=0)
            return SimpleNamespace(stdout="dump diagnostics",stderr="",returncode=0)
        with patch.object(probes,"adb",fake_adb),patch.object(probes.time,"monotonic",lambda:next(clock)),patch.object(probes.time,"sleep",lambda _:None):
            probes.wait_for_home()
        return calls

    def test_zero_exit_missing_file_is_not_treated_as_xml_and_successful_retry_is_logged(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(probes,"ROOT",Path(directory)):
            self.run_probe(["cat: hierarchy.xml: No such file or directory",HOME],[0,0,1])
            self.assertIn("No valid hierarchy yet",(Path(directory)/"window-dump-attempts.txt").read_text())
            self.assertEqual(HOME,(Path(directory)/"actual-main-window.xml").read_text())

    def test_launcher_text_and_loading_navigation_cannot_pass_as_ready_app_home(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(probes,"ROOT",Path(directory)):
            launcher=HOME.replace("com.tradereview.app","com.android.launcher")
            loading='<hierarchy><node package="com.tradereview.app" text="首页"/></hierarchy>'
            self.run_probe([launcher,loading,HOME],[0,0,1,2])
            self.assertEqual(2,(Path(directory)/"window-dump-attempts.txt").read_text().count("app home not ready"))

    def test_anr_is_rejected_and_failure_screenshot_is_kept(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(probes,"ROOT",Path(directory)):
            with self.assertRaisesRegex(AssertionError,"ANR"):
                self.run_probe(['<hierarchy><node text="App isn\'t responding"/></hierarchy>'],[0,0])
            self.assertTrue((Path(directory)/"26-v08-process-restart-font.png").is_file())
            self.assertIn("App isn't responding",(Path(directory)/"actual-main-window.xml").read_text())


if __name__=="__main__":unittest.main()
