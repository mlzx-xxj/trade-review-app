"""Independently verify actual UI saves and immutable trade facts, using Decimal only."""
from decimal import Decimal, ROUND_HALF_UP
import json

D=Decimal
ANCHOR='2026-10-17'
EXPECTED=(('603999','buy','open','10','1000','1.00'),('603999','sell','close','11','1000','6.60'),('rb2701','buy','open','3500','2','7.01'),('rb2701','buy','open','3510','3','10.54'),('rb2701','sell','close','3490','4','13.97'),('i2701','buy','open','800','2',None),('i2701','sell','close','810','1',None))


def verify(first, second, api, profile):
    for value in (first,second):
        assert value['api']==api and value['profile']==profile and value['result']=='PASS'
    assert first['dateAnchor']==ANCHOR and first['syntheticOnly'] is True
    assert first['allExecutionsCreatedViaRealForm'] is True and first['allPlansReviewsCashSavedViaUi'] is True
    assert first['immutableTradingFactsPreserved'] is True
    assert first['pid']==second['previousPid'] and first['pid']!=second['pid']
    assert second['allFactsIdentical'] is True and second['planTextPreserved'] is True
    ledger=first['ledger'];assert second['ledger']==ledger
    for key in ('executions','lots','matches','cycles','audits','risks','rules','strategies'):
        assert first['immutableBeforeReview'][key]==ledger[key],f'Review/account UI changed {key}'
    executions=sorted(ledger['executions'],key=lambda e:e['ordinal'])
    assert len(executions)==7 and [e['ordinal'] for e in executions]==list(range(1,8))
    for e,expected in zip(executions,EXPECTED):
        assert tuple(e.get(k) for k in ('code','side','effect','price','quantity','fees'))==expected
        if e['code']=='603999':
            rate=D('.0001') if e['side']=='buy' else D('.0006')
            assert D(e['fees'])==(D(e['price'])*D(e['quantity'])*rate).quantize(D('.01'),rounding=ROUND_HALF_UP)
        elif e['code']=='rb2701':
            assert e['multiplier']=='10' and D(e['marginRate'])==D('.07')
            assert D(e['fees'])==(D(e['price'])*D(e['quantity'])*D(10)*D('.0001')+D('.01')).quantize(D('.01'),rounding=ROUND_HALF_UP)
        else:assert e['fees'] is None and e['marginRate'] is None and e['multiplier']=='100'
    cycles={c['code']:c for c in ledger['cycles']};assert len(cycles)==3
    assert D(cycles['603999']['realized'])==D('992.4') and cycles['603999']['remaining']=='0' and cycles['603999']['closedDay'] is not None
    assert D(cycles['rb2701']['realized'])==D('-628.006666666667') and cycles['rb2701']['remaining']=='1'
    assert D(cycles['rb2701']['referenceMargin'])==D('3510')*D(10)*D('.07')
    assert cycles['i2701']['realized'] is None and cycles['i2701']['fees'] is None and cycles['i2701']['referenceMargin'] is None
    rb=[m for m in ledger['matches'] if m['cycleId']==cycles['rb2701']['id']]
    assert len(rb)==2 and [m['quantity'] for m in sorted(rb,key=lambda m:next(e['ordinal'] for e in executions if e['id']==m['openExecutionId']))]==['2','2']
    assert sum((D(m['gross']) for m in rb),D(0))==D('-600')
    assert sum((D(m['net']) for m in rb),D(0))==D('-628.006666666667')
    unknown=[m for m in ledger['matches'] if m['cycleId']==cycles['i2701']['id']]
    assert len(unknown)==1 and D(unknown[0]['gross'])==D(1000) and unknown[0]['net'] is None
    plans=ledger['plans'];assert len(plans)==2 and len({p['id'] for p in plans})==2 and len({p['day'] for p in plans})==1
    p=next(p for p in plans if p['code']=='603999')
    assert p['entryPrice']=='9.5' and p['takeProfit']=='12' and p['stopLoss']=='9' and p['strategyId']=='review_fixture_strategy'
    assert json.loads(p['levels'])==[dict(label='支撑',price='9'),dict(label='观察',price='10.5')]
    assert len(p['note'])>300 and p['note'].endswith('已修改计划')
    reviews=ledger['reviews'];assert len(reviews)==3 and len({r['id'] for r in reviews})==3
    assert {r['cycleId'] for r in reviews}=={c['id'] for c in cycles.values()}
    assert next(r for r in reviews if r['cycleId']==cycles['rb2701']['id'])['note'].endswith('已修改复盘')
    flows=ledger['cashFlows'];assert len(flows)==4 and len({f['id'] for f in flows})==4
    expected={('stock','in'):D(1200),('stock','out'):D(200),('futures','in'):D(3000),('futures','out'):D(500)}
    assert {(f['accountId'],f['kind']):D(f['amount']) for f in flows}==expected
    accounts={a['id']:a for a in ledger['accounts']}
    assert accounts['stock']['initialCapital']=='10000' and accounts['stock']['historyComplete'] is True
    assert accounts['futures']['initialCapital']=='20000' and accounts['futures']['historyComplete'] is False
    cash=D(10000)+D(1200)-D(200)+D(11000)-D(10000)-D('7.60')
    assert cash==D('11992.4')
    observed={(r['tag'],r['displayed']) for r in first['displayedMoney']}
    assert {('account-cash-stock','待估算'),('account-net-stock','￥992.40'),('account-fees-stock','￥7.60'),('account-cash-stock','￥11992.40'),('account-net-futures','待估算'),('account-fees-futures','待估算')}<=observed
    assert all(o['result']=='PASS' and o['normalTaps']==o['taps']-o['fixtureDateTaps'] for o in first['operations'])
    assert {o['operation'] for o in first['operations']}=={'new-stock-plan','edit-stock-plan','new-futures-plan','new-open-review','edit-open-review','closed-review','pending-review','configure-stock','stock-in-cash','stock-out-cash','edit-cashflow','configure-futures','futures-in-cash','futures-out-cash'}
    assert first['screenshots'] and second['screenshots']
