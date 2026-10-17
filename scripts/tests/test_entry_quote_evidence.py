import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('delivery_quote',Path(__file__).resolve().parents[1]/'create-v200-delivery-report.py')
delivery=importlib.util.module_from_spec(spec);spec.loader.exec_module(delivery)


class QuoteEvidenceTest(unittest.TestCase):
    def fixture(self):
        phases=['initial-buy']+[phase+str(i) for i in range(10) for phase in ['rejected-','reopened-']]+['final-sell']
        return dict(api=35,syntheticOnly=True,result='PASS',reopenCycles=10,t1RejectedWithoutMutation=True,confirmedExecutions=2,finalNet='-7',finalRemaining='0',quotes=[dict(phase=p,side='buy' if p=='initial-buy' else 'sell',price='10',quantity='1000',fee='1.00' if p=='initial-buy' else '6.00',displayed='预计手续费：¥'+('1.00' if p=='initial-buy' else '6.00')) for p in phases])

    def test_actual_twenty_two_quotes_accepted(self):delivery.quote_evidence(self.fixture(),35)

    def test_missing_reopen_or_stale_quote_rejected(self):
        a=self.fixture();a['quotes']=a['quotes'][:-1]
        with self.assertRaises(AssertionError):delivery.quote_evidence(a,35)
        a=self.fixture();a['quotes'][2]['displayed']='预计手续费：暂无法估算'
        with self.assertRaises(AssertionError):delivery.quote_evidence(a,35)

    def test_wrong_fee_or_changed_facts_rejected(self):
        a=self.fixture();a['quotes'][1]['fee']='1.00'
        with self.assertRaises(AssertionError):delivery.quote_evidence(a,35)
        for key,value in [('finalNet','0'),('confirmedExecutions',3),('t1RejectedWithoutMutation',False)]:
            a=self.fixture();a[key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):delivery.quote_evidence(a,35)
