"""Only report-gate tests. Synthetic dictionaries are never emulator evidence."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('delivery',Path(__file__).resolve().parents[1]/'create-v200-delivery-report.py')
delivery=importlib.util.module_from_spec(spec);spec.loader.exec_module(delivery)


class PendingDeliveryTest(unittest.TestCase):
    def test_missing_or_forged_pending_and_rounded_ui_results_cannot_publish(self):
        data={'observations':[{'operation':'rounded-partial-net','displayedNet':'￥971.83','savedRawNet':'971.833333333333'}]+
              [dict(operation='pending-common-product',action=action,remaining=remaining,code='i2701',multiplier='100',savedFees=None,savedMarginRate=None) for action,remaining in [('buy-open','2'),('sell-close','1')]],
              'ledger':{'executions':[{} for _ in range(8)],'cycles':[dict(id='c',code='i2701',remaining='1',realized=None)],'matches':[dict(cycleId='c',gross='1000',net=None)]}}
        delivery.pending_entry_evidence(data)
        variants=[]
        v=copy.deepcopy(data);v['observations'][0]['displayedNet']='￥971.833333333333';variants.append(v)
        v=copy.deepcopy(data);v['observations'][1]['savedFees']='0';variants.append(v)
        v=copy.deepcopy(data);v['observations'].pop();variants.append(v)
        v=copy.deepcopy(data);v['ledger']['cycles'][0]['realized']='1000';variants.append(v)
        v=copy.deepcopy(data);v['ledger']['matches'][0]['net']='1000';variants.append(v)
        v=copy.deepcopy(data);v['ledger']['executions'].pop();variants.append(v)
        for v in variants:
            with self.assertRaises(AssertionError):delivery.pending_entry_evidence(v)


if __name__=='__main__':unittest.main()
