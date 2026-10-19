"""Failed evidence transfers remain failures and keep the original diagnostics."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


class VisualCollectionFailureTests(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('visual_collection_test', Path(__file__).parents[1] / 'run-v210-visual.py')
        self.runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.runner)
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.runner.ROOT = Path(self.directory.name)
        self.stdout = b'\x89PNG\r\n\x1a\npartial\x00image'
        self.stderr = b'original remote read error\xff\n'

    def failed_read(self, command, **kwargs):
        return subprocess.CompletedProcess(command, 255, self.stdout, self.stderr)

    def assert_diagnostics(self, filename):
        prefix = self.runner.ROOT / 'collection-failures' / ('regular-light-' + filename)
        self.assertEqual(self.stdout, Path(str(prefix) + '.stdout.bin').read_bytes())
        self.assertEqual(self.stderr, Path(str(prefix) + '.stderr.bin').read_bytes())
        info = json.loads(Path(str(prefix) + '.json').read_text())
        self.assertEqual(255, info['returnCode'])
        self.assertEqual(hashlib.sha256(self.stdout).hexdigest(), info['stdoutSha256'])
        self.assertEqual(hashlib.sha256(self.stderr).hexdigest(), info['stderrSha256'])
        self.assertEqual(len(self.stdout), info['stdoutBytes'])
        self.assertEqual(len(self.stderr), info['stderrBytes'])
        self.assertFalse(info['retried'])

    def test_full_transfer_error_keeps_bytes_and_does_not_rerun_ui(self):
        filename = 'v210-full-regular-light-empty-home.png'
        result = 'OK (1 test)\nINSTRUMENTATION_CODE: -1\n'
        with patch.object(self.runner.entry, 'adb', side_effect=['', result, filename + '\n']) as adb, \
                patch.object(self.runner.subprocess, 'run', side_effect=self.failed_read) as read:
            with self.assertRaises(subprocess.CalledProcessError) as raised:
                self.runner.full_journey(35, 'regular-light', '/data/user/0/com.tradereview.app')
        self.assertEqual(255, raised.exception.returncode)
        self.assertEqual(self.stderr, raised.exception.stderr)
        self.assertEqual(3, adb.call_count)  # force-stop, one instrumentation, listing
        self.assertEqual(1, read.call_count)
        self.assertFalse((self.runner.ROOT / 'regular-light/full' / filename).exists())
        self.assert_diagnostics(filename)

    def test_final_collection_error_keeps_bytes_and_stays_failure(self):
        filename = 'v210-regular-light-empty.png'
        with patch.object(self.runner.subprocess, 'run', side_effect=self.failed_read) as read:
            with self.assertRaisesRegex(RuntimeError, 'Cannot read private synthetic visual evidence'):
                self.runner.collect(35, 'regular-light')
        self.assertEqual(1, read.call_count)
        self.assertFalse((self.runner.ROOT / 'regular-light' / filename).exists())
        self.assert_diagnostics(filename)


if __name__ == '__main__':
    unittest.main()
