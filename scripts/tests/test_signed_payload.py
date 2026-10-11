import importlib.util
import io
from pathlib import Path
import unittest
import warnings
import zipfile

spec = importlib.util.spec_from_file_location("adopt", Path(__file__).parents[1] / "adopt-signed-apk.py")
adopt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(adopt)


def apk(entries):
    output = io.BytesIO()
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(output, "w") as archive:
            for name, value in entries:
                archive.writestr(name, value)
    return output.getvalue()


class SignedPayloadTest(unittest.TestCase):
    original = [("AndroidManifest.xml", b"manifest"), ("classes.dex", b"code"), ("resources.arsc", b"Chinese resources")]

    def test_signature_metadata_can_change(self):
        adopt.require_identical_payload(apk(self.original + [("META-INF/OLD.SF", b"old")]), apk(self.original + [("META-INF/NEW.SF", b"new")]))

    def test_changed_code_is_rejected(self):
        with self.assertRaises(ValueError):
            adopt.require_identical_payload(apk(self.original), apk([(n, b"other code" if n == "classes.dex" else v) for n, v in self.original]))

    def test_added_or_missing_payload_is_rejected(self):
        for entries in [self.original[:-1], self.original + [("assets/extra", b"extra")]]:
            with self.assertRaises(ValueError):
                adopt.require_identical_payload(apk(self.original), apk(entries))

    def test_duplicate_zip_entries_are_rejected(self):
        with self.assertRaises(ValueError):
            adopt.require_identical_payload(apk(self.original), apk(self.original + [("classes.dex", b"code")]))


if __name__ == "__main__":
    unittest.main()
