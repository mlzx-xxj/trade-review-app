import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('review',Path(__file__).resolve().parents[1]/'v210-review-evidence.py')
review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)


def evidence():
    controls=dict(composeVisible=True,fullyAboveKeyboard=True,buttonBoundsPx=[0,0,120,52],touchHeightDp=52,keyboardVisible=True)
    operations=[dict(operation=k,normalTaps=n,keyboardBackKeys=0,typedFields=['plan-code','plan-name','plan-note'],saveControl=copy.deepcopy(controls)) for k,n in [('new-stock-plan',5),('new-futures-plan',6),('new-open-review',2)]]
    return dict(cashEditTargetRetained=True,operations=operations,layouts=[dict(visible=True,text='￥-628.01',fontSp=18,overflowWidth=False,overflowHeight=False)])


class ReviewQualityTest(unittest.TestCase):
    def test_real_measurement_shape_is_accepted(self):review.verify_candidate(evidence())
    def test_lost_edit_target_is_rejected(self):
        v=evidence();v['cashEditTargetRetained']=False
        with self.assertRaises(AssertionError):review.verify_candidate(v)
    def test_keyboard_overlap_or_missing_bounds_is_rejected(self):
        for change in [dict(fullyAboveKeyboard=False),dict(composeVisible=False),dict(buttonBoundsPx=None),dict(touchHeightDp=47)]:
            v=evidence();v['operations'][0]['saveControl'].update(change)
            with self.subTest(change=change),self.assertRaises(AssertionError):review.verify_candidate(v)
    def test_keyboard_hidden_to_save_is_rejected_for_quick_plan(self):
        v=evidence();v['operations'][0]['keyboardBackKeys']=1
        with self.assertRaises(AssertionError):review.verify_candidate(v)
    def test_more_quick_input_steps_are_rejected(self):
        for key,value in [('normalTaps',6),('typedFields',['plan-code','plan-name','plan-note','extra'])]:
            v=evidence();v['operations'][0][key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):review.verify_candidate(v)
    def test_missing_actual_keyboard_observation_is_rejected(self):
        v=evidence();v['operations'][0]['saveControl']['keyboardVisible']=False
        with self.assertRaises(AssertionError):review.verify_candidate(v)
    def test_small_or_overflowing_key_amount_is_rejected(self):
        for change in [dict(fontSp=13),dict(overflowWidth=True),dict(overflowHeight=True)]:
            v=evidence();v['layouts'][0].update(change)
            with self.subTest(change=change),self.assertRaises(AssertionError):review.verify_candidate(v)
    def test_long_scrolling_note_or_caption_is_not_mislabeled_as_key_amount(self):
        v=evidence();v['layouts'].append(dict(visible=True,text='原流水：入金 ￥1000.00，保存为明确更正。',fontSp=12,overflowWidth=False,overflowHeight=False));review.verify_candidate(v)
