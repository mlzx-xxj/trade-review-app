"""Reject missing or financially inconsistent full-page UI evidence."""
import copy
from datetime import date
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('full',Path(__file__).resolve().parents[1]/'v210-full-evidence.py')
full=importlib.util.module_from_spec(spec);spec.loader.exec_module(full)


class FullEvidenceTest(unittest.TestCase):
    def fixture(self):
        epoch=date(1970,1,1)
        rows=[('600001','stock','2026-09-30','992.4'),('al2701','futures','2026-10-11','43.98'),('600000','stock','2026-10-17','-7'),('rb2701','futures','2026-10-17','971.833333333333'),('rb2705','futures','2026-10-17','0'),('i2701','futures','2026-10-17',None)]
        executions=[dict(id='open'+str(i),effect='open',code='unused') for i in range(7)]
        matches=[]
        for i,(code,market,day,net) in enumerate(rows):
            executions.append(dict(id=str(i),code=code,accountId=market,tradingDay=(date.fromisoformat(day)-epoch).days,effect='close',fees=None if net is None else '1'))
            matches.append(dict(closeExecutionId=str(i),net=net,gross='1000',openFees=None,closeFees=None))
        checks=[]
        for kind,market,offset,count in [('week','all',0,11)]+[(k,m,0,13) for k in ['week','month'] for m in ['all','stock','futures']]+[('week','all',-1,13),('month','all',-1,13)]:
            start,end=('2026-10-13','2026-10-19') if kind=='week' else ('2026-10-01','2026-10-31')
            # The anchor is Saturday: Monday is October 12.
            if kind=='week':start,end=('2026-10-05','2026-10-11') if offset else ('2026-10-12','2026-10-18')
            elif offset:start,end='2026-09-01','2026-09-30'
            chosen=[r for r in rows if start<=r[2]<=end and (market=='all' or market==r[1]) and (count==13 or r[0]!='i2701')]
            known=[full.D(r[3]) for r in chosen if r[3] is not None];net=sum(known) if known else None
            checks.append(dict(type=kind,market=market,offset=offset,sourceExecutionCount=count,start=start,end=end,closed=len(chosen),valid=len(known),pending=len(chosen)-len(known),net=None if net is None else str(net),displayed='待估算' if net is None else '￥'+str(net.quantize(full.D('.01'),rounding=full.ROUND_HALF_UP)),result='PASS'))
        first=dict(api=29,profile='regular-light',result='PASS',syntheticOnly=True,allExecutionsCreatedViaRealForm=True,reviewSavedViaUi=True,pid=1,dateAnchor='2026-10-17',observations=[{}]*13,ledger=dict(executions=executions,matches=matches,reviews=[{}]),checks=checks,screenshots=[dict(scene=s) for s in ['empty-home','empty-statistics','known-home','known-statistics','pending-home','week-statistics','month-statistics','recreated-statistics']])
        reopen=dict(api=29,profile='regular-light',result='PASS',reviewPreserved=True,previousPid=1,pid=2,allFactsIdentical=True)
        return first,reopen

    def test_actual_complete_counts_and_decimal_results_accepted(self):full.verify(*self.fixture(),29,'regular-light')

    def test_unknown_net_cannot_be_zero_or_hidden_from_sample(self):
        a,b=self.fixture();a['ledger']['matches'][-1]['net']='0'
        with self.assertRaises(AssertionError):full.verify(a,b,29,'regular-light')

    def test_rounding_cannot_change_persisted_fifo_net(self):
        a,b=self.fixture();a['ledger']['matches'][3]['net']='971.83'
        with self.assertRaises(AssertionError):full.verify(a,b,29,'regular-light')

    def test_wrong_period_filter_or_sample_is_rejected(self):
        for key,value in [('start','2026-10-13'),('net','0'),('valid',99),('displayed','￥0.00')]:
            a,b=self.fixture();a['checks'][0][key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):full.verify(a,b,29,'regular-light')

    def test_same_process_missing_review_or_missing_scene_rejected(self):
        for key,value in [('pid',1),('reviewPreserved',False),('allFactsIdentical',False)]:
            a,b=self.fixture();b[key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):full.verify(a,b,29,'regular-light')
        a,b=self.fixture();a['screenshots']=[]
        with self.assertRaises(AssertionError):full.verify(a,b,29,'regular-light')


if __name__=='__main__':unittest.main()
