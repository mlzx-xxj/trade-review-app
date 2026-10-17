"""The UUID normalization must not hide changed money or broken associations."""
import copy
import importlib.util
from pathlib import Path
import unittest
import tempfile

spec=importlib.util.spec_from_file_location('review_report',Path(__file__).resolve().parents[1]/'v210-review-report.py')
report=importlib.util.module_from_spec(spec);spec.loader.exec_module(report)


def fixture(prefix):
    return dict(executions=[dict(id=prefix+'e',ordinal=1,price='10',fees='1.00',planId=prefix+'p')],cycles=[dict(id=prefix+'c',accountId='stock',code='603999',direction='long',realized=None)],plans=[dict(id=prefix+'p',accountId='stock',code='603999',day=1,note='相同计划')],lots=[dict(id=prefix+'l',executionId=prefix+'e',cycleId=prefix+'c')],matches=[],reviews=[dict(id=prefix+'r',cycleId=prefix+'c',day=1,note='相同复盘')],cashFlows=[dict(id=prefix+'f',accountId='stock',kind='in',amount='1200')],accounts=[dict(id='stock',initialCapital=None)])


class ReviewComparisonTest(unittest.TestCase):
    def test_existing_pack_duplicate_originals_require_identical_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            for folder in ('ci-screenshots','ci-v200/review'):
                (root/folder).mkdir(parents=True);(root/folder/'image.png').write_bytes(b'original image')
            self.assertEqual(report.one(root,'image.png').read_bytes(),b'original image')
            (root/'ci-screenshots/image.png').write_bytes(b'different image')
            with self.assertRaises(AssertionError):report.one(root,'image.png')
    def test_missing_original_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(AssertionError):report.one(temp,'missing.png')
    def test_different_fresh_ui_ids_preserve_the_same_saved_facts(self):self.assertEqual(report.canonical(fixture('before_')),report.canonical(fixture('after_')))
    def test_changed_fee_is_not_normalized_away(self):
        a=fixture('a');b=fixture('b');b['executions'][0]['fees']='0'
        self.assertNotEqual(report.canonical(a),report.canonical(b))
    def test_unknown_net_cannot_become_zero(self):
        a=fixture('a');b=fixture('b');b['cycles'][0]['realized']='0'
        self.assertNotEqual(report.canonical(a),report.canonical(b))
    def test_missing_or_wrong_plan_link_is_detected(self):
        a=fixture('a');b=fixture('b');b['executions'][0]['planId']=None
        self.assertNotEqual(report.canonical(a),report.canonical(b))
    def test_changed_review_text_is_not_normalized_away(self):
        a=fixture('a');b=fixture('b');b['reviews'][0]['note']='不同复盘'
        self.assertNotEqual(report.canonical(a),report.canonical(b))
    def test_reused_ambiguous_fixture_id_is_rejected(self):
        a=fixture('a');a['cashFlows'][0]['id']=a['plans'][0]['id']
        with self.assertRaises(AssertionError):report.canonical(a)


class ReviewColorEvidenceTest(unittest.TestCase):
    def evidence(self,night=False):
        profit,loss=('#FFFFB4B0','#FF80D5AF') if night else ('#FFAF273C','#FF087044')
        return dict(night=night,layouts=[dict(visible=True,text=text,argb=color) for text,color in [('￥992.40',profit),('￥-628.01',loss),('待估算','#FF53637B')]])
    def test_light_and_dark_actual_color_shapes(self):
        for night in (True,False):report.verify_colors(self.evidence(night))
    def test_reversed_profit_and_loss_are_rejected(self):
        value=self.evidence();value['layouts'][0]['argb']='#FF087044'
        with self.assertRaises(AssertionError):report.verify_colors(value)
    def test_unknown_using_a_profit_color_is_rejected(self):
        value=self.evidence();value['layouts'][2]['argb']='#FFAF273C'
        with self.assertRaises(AssertionError):report.verify_colors(value)
    def test_missing_negative_sample_is_not_called_color_validation(self):
        value=self.evidence();value['layouts'][1]['visible']=False
        with self.assertRaises(AssertionError):report.verify_colors(value)
