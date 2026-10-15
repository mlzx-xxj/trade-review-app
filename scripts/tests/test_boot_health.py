"""Synthetic shell fault checks only; not Android boot/keyboard acceptance."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT=Path(__file__).resolve().parents[1]/'run-device-tests.sh'


class BootHealthTest(unittest.TestCase):
    def invoke(self, log, last_anr='ACTIVITY MANAGER LAST ANR\n  <no ANR has occurred since boot>\n'):
        scratch=tempfile.TemporaryDirectory();self.addCleanup(scratch.cleanup)
        root=Path(scratch.name);bin_dir=root/'bin';bin_dir.mkdir()
        (root/'fixture-log.txt').write_text(log)
        (root/'fixture-last-anr.txt').write_text(last_anr)
        (root/'gradlew').write_text('#!/bin/sh\ntouch gradle-invoked\nexit 7\n');(root/'gradlew').chmod(0o755)
        (bin_dir/'sleep').write_text('#!/bin/sh\nexit 0\n');(bin_dir/'sleep').chmod(0o755)
        (bin_dir/'adb').write_text('''#!/bin/sh
case "$*" in
  'shell getprop ro.kernel.qemu') echo 1;;
  'shell id -u') echo 0;;
  'shell getprop sys.boot_completed') echo 1;;
  'shell getprop dalvik.vm.heapgrowthlimit') echo 256m;;
  'shell getprop dalvik.vm.heapsize') echo 512m;;
  'logcat -d -s ActivityManager:I ActivityTaskManager:I AndroidRuntime:E') cat fixture-log.txt;;
  'shell dumpsys activity lastanr') cat fixture-last-anr.txt;;
  'exec-out screencap -p') printf 'synthetic-png';;
esac
exit 0
''');(bin_dir/'adb').chmod(0o755)
        env=dict(os.environ,PATH=str(bin_dir)+os.pathsep+os.environ['PATH'],V100_CHECK_BOOT='true',V100_PACKAGE_PROBES='false',V08_STABILITY_PROBES='false')
        result=subprocess.run(['bash',str(SCRIPT)],cwd=root,env=env,capture_output=True,text=True)
        return root,result

    def test_clean_boot_reaches_real_test_command_and_keeps_its_failure(self):
        root,result=self.invoke('I ActivityManager: Starting healthy synthetic emulator\n')
        self.assertTrue((root/'gradle-invoked').exists(),result.stderr);self.assertEqual(7,result.returncode)

    def test_pre_app_systemui_anr_is_rejected_with_evidence_and_no_native_test_claim(self):
        text='E ActivityManager: ANR in com.android.systemui\nReason: synthetic keyguard service timeout\n'
        root,result=self.invoke(text,'ACTIVITY MANAGER LAST ANR\nANR in com.android.systemui\nReason: synthetic keyguard timeout\n')
        self.assertEqual(1,result.returncode);self.assertFalse((root/'gradle-invoked').exists())
        self.assertEqual(text,(root/'ci-stability/boot-logcat.txt').read_text())
        self.assertTrue((root/'ci-stability/boot-last-anr.txt').is_file(),result.stderr);self.assertTrue((root/'ci-stability/boot-failure.png').is_file(),result.stderr)
        self.assertIn('no dialog dismissed',result.stderr)

    def test_platform_crash_is_rejected_before_native_tests(self):
        root,result=self.invoke('E AndroidRuntime: FATAL EXCEPTION: synthetic boot process\n')
        self.assertEqual(1,result.returncode);self.assertFalse((root/'gradle-invoked').exists())

    def test_google_internal_anr_text_cannot_override_platform_no_anr_state(self):
        text='E ActivityManager: ANR in com.google.android.googlequicksearchbox:search\n'
        root,result=self.invoke(text)
        self.assertTrue((root/'gradle-invoked').exists(),result.stderr);self.assertEqual(7,result.returncode)
        self.assertEqual(text,(root/'ci-stability/boot-logcat.txt').read_text())


if __name__=='__main__':unittest.main()
