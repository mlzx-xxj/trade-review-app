"""VM setup checks use a fake adb, never a real device or credentials."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

SCRIPT=Path(__file__).resolve().parents[1]/'ci-emulator-heap.sh'


class EmulatorHeapTest(unittest.TestCase):
    def invoke(self,qemu='1'):
        temp=tempfile.TemporaryDirectory();self.addCleanup(temp.cleanup)
        root=Path(temp.name);adb=root/'adb'
        adb.write_text('''#!/bin/bash
printf '%s\\n' "$*" >> "$TEST_ADB_CALLS"
case "$*" in
 'shell getprop ro.kernel.qemu') printf '%s\\n' "$TEST_QEMU";;
 'shell id -u') echo 0;;
 'shell getprop sys.boot_completed') echo 1;;
 'shell getprop dalvik.vm.heapgrowthlimit') echo 256m;;
 'shell getprop dalvik.vm.heapsize') echo 512m;;
esac
''');adb.chmod(0o755)
        env=dict(os.environ,PATH=str(root)+':'+os.environ['PATH'],TEST_QEMU=qemu,TEST_ADB_CALLS=str(root/'calls'))
        result=subprocess.run(['bash',str(SCRIPT)],cwd=root,env=env,capture_output=True,text=True)
        return result,(root/'calls').read_text().splitlines()
    def test_phone_is_refused_before_root_or_any_mutation(self):
        result,calls=self.invoke('0');self.assertNotEqual(0,result.returncode)
        self.assertEqual(['shell getprop ro.kernel.qemu'],calls)
    def test_userdebug_vm_config_is_set_before_start_and_checked_after_boot(self):
        result,calls=self.invoke();self.assertEqual(0,result.returncode,result.stderr)
        self.assertLess(calls.index('shell stop'),calls.index('shell setprop dalvik.vm.heapgrowthlimit 256m'))
        self.assertLess(calls.index('shell setprop dalvik.vm.heapsize 512m'),calls.index('shell start'))
        self.assertGreater(calls.index('shell getprop dalvik.vm.heapgrowthlimit'),calls.index('shell start'))


if __name__=='__main__':unittest.main()
