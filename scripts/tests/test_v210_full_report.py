"""The visual comparison may not hide changed facts, unreadable amounts or extra work."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('full_report',Path(__file__).resolve().parents[1]/'v210-full-report.py')
report=importlib.util.module_from_spec(spec);spec.loader.exec_module(report)


class FullReportTest(unittest.TestCase):
    def fixture(self):
        value=dict(dateAnchor='2026-10-17',expectedClosed=[dict(net='1')],checks=[dict(net='1',valid=1)],observations=[dict(code='600000',action='买入',price='10',quantity='1000',savedFees='1.00',remaining='1000',realized='0',extraConfirmation=False,fieldsTyped=['code','price','quantity'],saveVisible=True,tapsIncludingEntryAndSave=5,requiredScrollCommands=1)],moneyLayouts=[dict(tag='home-realized-week',text='￥1.00',fontSp=18,lines=1,overflowWidth=False,overflowHeight=False,visible=True,argb='#FFAF273C')])
        value['toolbarSafety']=[dict(orientation=o,result='PASS',safeLeft=0,safeRight=720,safeTop=24,buttons=[dict(tag=t,left=500,right=600,top=30) for t in ['open-backup','open-risk-strategy']]) for o in (1,2)]
        return value,copy.deepcopy(value)

    def test_real_larger_type_with_same_facts_is_recorded(self):
        a,b=self.fixture();b['moneyLayouts'][0]['fontSp']=28
        self.assertEqual(28,report.compare(a,b)['moneyLayouts'][0]['afterFontSp'])

    def test_changed_fee_or_period_result_is_rejected(self):
        a,b=self.fixture();b['observations'][0]['savedFees']='0'
        with self.assertRaises(AssertionError):report.compare(a,b)
        a,b=self.fixture();b['checks'][0]['valid']=0
        with self.assertRaises(AssertionError):report.compare(a,b)

    def test_overflow_small_type_or_swapped_color_rejected(self):
        for key,value in [('overflowWidth',True),('overflowHeight',True),('fontSp',13),('argb','#FF087044')]:
            a,b=self.fixture();b['moneyLayouts'][0][key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):report.compare(a,b)

    def test_system_navigation_overlap_or_missing_orientation_rejected(self):
        a,b=self.fixture();b['toolbarSafety'][0]['buttons'][0]['right']=721.5
        with self.assertRaises(AssertionError):report.compare(a,b)
        a,b=self.fixture();b['toolbarSafety']=b['toolbarSafety'][:1]
        with self.assertRaises(AssertionError):report.compare(a,b)

    def test_previously_visible_amount_cannot_disappear(self):
        a,b=self.fixture();b['moneyLayouts'][0]['visible']=False
        with self.assertRaises(AssertionError):report.compare(a,b)

    def test_different_date_or_money_value_rejected(self):
        a,b=self.fixture();b['dateAnchor']='2026-10-18'
        with self.assertRaises(AssertionError):report.compare(a,b)
        a,b=self.fixture();b['moneyLayouts'][0]['text']='￥0.00'
        with self.assertRaises(AssertionError):report.compare(a,b)

    def test_more_entry_work_or_extra_confirmation_rejected(self):
        for key,value in [('tapsIncludingEntryAndSave',6),('requiredScrollCommands',2),('extraConfirmation',True)]:
            a,b=self.fixture();b['observations'][0][key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):report.compare(a,b)


if __name__=='__main__':unittest.main()
