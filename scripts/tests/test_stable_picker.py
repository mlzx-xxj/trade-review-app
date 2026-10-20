"""Lightweight DocumentsUI replay; no device, key or full Android test run."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import stable_upgrade as upgrade


def picker(row="[48,911][376,1280]", title="[148,1255][273,1280]", package="com.android.documentsui"):
    # Exact failing API35 geometry: run39337950406 attempt3 operations[182].
    return ET.fromstring(f'''<hierarchy><node package="{package}" resource-id="{package}:id/dir_list" bounds="[0,48][768,1280]">
      <node resource-id="{package}:id/item_root" clickable="true" enabled="true" bounds="{row}">
        <node text="before.zip" resource-id="android:id/title" enabled="true" bounds="{title}"/>
        <node content-desc="Quick preview: before.zip" clickable="true" bounds="[48,911][376,1239]"/>
      </node></node></hierarchy>''')


class PickerVisibilityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.device = upgrade.Device(Mock(), self.temp.name)
        self.device.adb = Mock(return_value=subprocess.CompletedProcess([], 0, "", ""))
        self.full = picker("[48,280][376,704]", "[148,624][273,662]")

    def test_bottom_clipped_file_is_scrolled_then_tapped_at_fresh_title_coordinates(self):
        self.device.tree = Mock(side_effect=[picker(), self.full])
        self.device.select_document("before.zip")
        calls = [c.args for c in self.device.adb.call_args_list]
        self.assertEqual("swipe", calls[0][2])
        self.assertEqual(("shell", "input", "tap", "210", "643"), calls[1])
        self.assertEqual(2, self.device.tree.call_count)
        self.assertNotIn(("shell", "input", "tap", "210", "1267"), calls)

    def test_whole_tile_must_be_visible_even_if_its_title_is_above_the_edge(self):
        self.device.tree = Mock(side_effect=[picker(title="[148,1020][273,1058]"), self.full])
        self.device.select_document("before.zip")
        self.assertEqual("swipe", self.device.adb.call_args_list[0].args[2])

    def test_top_clipped_tile_scrolls_in_the_opposite_direction(self):
        self.device.tree = Mock(side_effect=[picker("[48,48][376,350]", "[148,70][273,108]"), self.full])
        self.device.select_document("before.zip")
        command = self.device.adb.call_args_list[0].args
        self.assertLess(int(command[4]), int(command[6]))

    def test_persistent_clipping_fails_without_a_single_tap_or_longer_retry_budget(self):
        self.device.tree = Mock(side_effect=lambda: picker())
        with self.assertRaisesRegex(ValueError, "fully visible"):
            self.device.select_document("before.zip")
        self.assertLessEqual(self.device.tree.call_count, 12)
        self.assertFalse(any(c.args[2] == "tap" for c in self.device.adb.call_args_list))
        title, _, area = upgrade.document_target(picker(), "before.zip")
        with self.assertRaisesRegex(ValueError, "unsafe"):
            self.device.tap(title, within=area)

    def test_fully_visible_api29_file_keeps_direct_selection(self):
        self.device.tree = Mock(return_value=picker("[0,600][768,800]", "[148,712][273,750]", "com.google.android.documentsui"))
        self.device.select_document("before.zip")
        self.device.adb.assert_called_once_with("shell", "input", "tap", "210", "731")

    def test_picker_exit_without_preview_cannot_reach_restore_confirmation(self):
        self.device.backup_page = Mock(); self.device.downloads = Mock()
        self.device.select_document = Mock(); self.device.tap = Mock()
        self.device.find = Mock(side_effect=[ET.Element("node"), ValueError("preview missing")])
        self.device.restore_failure = Mock()
        with self.assertRaisesRegex(ValueError, "preview missing"):
            self.device.restore("before.zip")
        self.assertEqual(["从 ZIP／JSON 恢复", "恢复预览（尚未写入）"], [c.args[0] for c in self.device.find.call_args_list])
        self.device.restore_failure.assert_called_once_with("before.zip")
        self.device.tap.assert_called_once()

    def test_missing_preview_keeps_screenshot_hierarchy_and_original_error_log(self):
        root = Path(self.temp.name)
        raw = '<hierarchy><node text="恢复失败，原有数据未改变"/></hierarchy>'
        def tree():
            (root / "last-window.xml").write_text(raw)
            return ET.fromstring(raw)
        self.device.tree = tree
        png = b"\x89PNG\r\n\x1a\nunit-fixture"
        self.device.adb = Mock(side_effect=lambda *args, **kwargs: subprocess.CompletedProcess([], 0, png if kwargs.get("binary") else "original logcat", ""))
        try: raise ValueError("preview missing")
        except ValueError: self.device.restore_failure("before.zip")
        self.assertEqual(png, (root / "restore-failure.png").read_bytes())
        self.assertEqual(raw, (root / "restore-failure.xml").read_text())
        self.assertEqual("original logcat", (root / "restore-failure-logcat.txt").read_text())
        self.assertIn("ValueError: preview missing", (root / "restore-failure.txt").read_text())

    def test_diagnostic_failure_never_masks_the_restore_failure(self):
        self.device.backup_page = Mock(side_effect=ValueError("original restore failure"))
        self.device.restore_failure = Mock(side_effect=OSError("diagnostic unavailable"))
        with self.assertRaisesRegex(ValueError, "original restore failure"):
            self.device.restore("before.zip")


if __name__ == "__main__": unittest.main()
