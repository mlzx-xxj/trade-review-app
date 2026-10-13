import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('attempts',Path(__file__).resolve().parents[1]/'select-v200-attempts.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class AttemptSelectionTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        self.reports=self.root/'reports';self.shots=self.root/'shots';self.reports.mkdir();self.shots.mkdir()
    def fixture(self,api,attempt,shots=True):
        folder=self.reports/f'android-test-reports-api{api}-attempt{attempt}';folder.mkdir();(folder/'evidence.xml').write_text(str(attempt))
        if shots:(self.shots/f'v200-emulator-screenshots-api{api}-attempt{attempt}').mkdir()
    def choose(self):return module.select(self.reports,self.shots,self.root/'out-reports',self.root/'out-shots')
    def test_failed_reports_are_retained_and_latest_each_api_selected(self):
        self.fixture(29,1);self.fixture(29,2);self.fixture(35,1)
        self.assertEqual({'29':2,'35':1},self.choose())
        self.assertEqual('2',(self.root/'out-reports/android-test-reports-api29/evidence.xml').read_text())
        self.assertTrue((self.reports/'android-test-reports-api29-attempt1/evidence.xml').is_file())
    def test_latest_missing_success_artifact_cannot_fall_back(self):
        self.fixture(29,1);self.fixture(29,2,shots=False);self.fixture(35,1)
        with self.assertRaisesRegex(ValueError,'no fallback'):self.choose()
    def test_missing_required_api_is_rejected(self):
        self.fixture(29,1)
        with self.assertRaisesRegex(ValueError,'API 35'):self.choose()

if __name__=='__main__':unittest.main()
