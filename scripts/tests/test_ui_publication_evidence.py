"""Publication evidence must prove actual emitter confinement, not just a PASS label."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('delivery',Path(__file__).resolve().parents[1]/'create-v200-delivery-report.py')
delivery=importlib.util.module_from_spec(spec)
spec.loader.exec_module(delivery)


def emission(flow,value,main=True):
    return dict(flow=flow,value=value,main=main,thread='main' if main else 'DefaultDispatcher-worker-1')


class UiPublicationEvidenceTest(unittest.TestCase):
    def fixture(self):
        def report(checks,events):
            return dict(api=35,result='PASS',syntheticOnly=True,checks=checks,emissions=events)
        return dict(
            calibration=report(dict(workerEmissionDetected=True),[emission('calibration','0'),emission('calibration','1',False),emission('calibration','2')]),
            ledger=report(dict(confirmedEditAndDelete=True,previewKeptFacts=True,failedWriteKeptFacts=True,auditCount=2),[emission('pending',v) for v in ['none','edit','none','delete','none']]),
            backup=report(dict(jsonAndZipReadback=True,previewRefreshCancel=True,restoreKeptFacts=True,writeFailureKeptFacts=True,csvBothKinds=True),[emission('preview','present') for _ in range(4)]),
        )

    def test_complete_main_publications_and_real_worker_calibration_are_accepted(self):
        delivery.ui_publication_evidence(self.fixture(),35)

    def test_pass_label_cannot_hide_worker_ui_publication(self):
        for kind in ['ledger','backup']:
            value=self.fixture();value[kind]['emissions'][1]=emission('pending','edit',False)
            with self.subTest(kind=kind),self.assertRaises(AssertionError):delivery.ui_publication_evidence(value,35)

    def test_main_dispatched_probe_cannot_pretend_to_measure_emitter_thread(self):
        value=self.fixture();value['calibration']['emissions'][1]['main']=True
        with self.assertRaises(AssertionError):delivery.ui_publication_evidence(value,35)

    def test_missing_actual_confirmation_or_restore_transitions_are_rejected(self):
        for kind in ['ledger','backup']:
            value=self.fixture();value[kind]['emissions']=value[kind]['emissions'][:-1]
            with self.subTest(kind=kind),self.assertRaises(AssertionError):delivery.ui_publication_evidence(value,35)

    def test_wrong_device_failed_facts_or_empty_events_are_rejected(self):
        original=self.fixture()
        changes=[('api',29),('result','FAIL'),('syntheticOnly',False),('emissions',[]),('checks',dict(restoreKeptFacts=False))]
        for key,replacement in changes:
            value=copy.deepcopy(original);value['backup'][key]=replacement
            with self.subTest(key=key),self.assertRaises(AssertionError):delivery.ui_publication_evidence(value,35)


if __name__=='__main__':unittest.main()
