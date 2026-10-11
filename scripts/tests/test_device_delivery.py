"""Publication safety: preserve a failed device task even when screenshots exist."""
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "run-device-tests.sh"
SCREENSHOTS = ["01-home", "02-calendar-heatmap", "03-daily-details", "04-combined-filters", "05-statistics", "06-pnl-chart", "07-dark-settings", "08-dark-red-calendar", "09-professional-auto", "10-risk-confirmation", "11-risk-rules", "12-r-analytics", "13-professional-dark"]


class DeviceDeliveryTest(unittest.TestCase):
    def invoke(self, status=0, names=SCREENSHOTS):
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        root = Path(scratch.name)
        outputs = root / "app/build/outputs/androidTest-additional-output/connected/debug/emulator - 29/v04-screenshots"
        outputs.mkdir(parents=True)
        for name in names:
            (outputs / (name + ".png")).write_bytes(b"screenshot-file-fixture")
        gradle = root / "gradlew"
        gradle.write_text("#!/bin/sh\nexit " + str(status) + "\n")
        gradle.chmod(0o755)
        result = subprocess.run(["bash", str(SCRIPT)], cwd=root, text=True, capture_output=True)
        return result, root

    def test_success_copies_every_required_page_from_paths_with_spaces(self):
        result, root = self.invoke()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(13, len(list((root / "ci-screenshots").glob("*.png"))))

    def test_failed_device_tests_remain_failed_even_if_all_screenshots_exist(self):
        result, root = self.invoke(status=7)
        self.assertEqual(7, result.returncode)
        self.assertTrue((root / "ci-screenshots/01-home.png").exists())

    def test_success_without_one_required_screenshot_blocks_publication(self):
        result, _ = self.invoke(names=SCREENSHOTS[1:])
        self.assertNotEqual(0, result.returncode)
        self.assertIn("01-home", result.stderr)


if __name__ == "__main__":
    unittest.main()
