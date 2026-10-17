"""Independent Decimal/date checks for actual MainActivity home/statistics evidence."""
from datetime import date, timedelta
from decimal import Decimal as D, ROUND_HALF_UP


def verify(first, reopen, api, profile):
    assert first['result'] == reopen['result'] == 'PASS'
    assert first['api'] == reopen['api'] == api and first['profile'] == reopen['profile'] == profile
    assert first['syntheticOnly'] is True and first['allExecutionsCreatedViaRealForm'] is True
    assert first['reviewSavedViaUi'] is True and reopen['reviewPreserved'] is True
    assert first['pid'] == reopen['previousPid'] and first['pid'] != reopen['pid']
    assert reopen['allFactsIdentical'] is True
    ledger = first['ledger']; executions = ledger['executions']; matches = ledger['matches']
    assert len(executions) == 13 and len(first['observations']) == 13 and len(ledger['reviews']) == 1
    wanted = {'600001': D('992.4'), 'al2701': D('43.98'), '600000': D('-7'), 'rb2701': D('971.833333333333'), 'rb2705': D(0), 'i2701': None}
    closed = []
    for code, expected in wanted.items():
        close = [e for e in executions if e['code'] == code and e['effect'] != 'open']
        assert len(close) == 1
        e = close[0]; rows = [m for m in matches if m['closeExecutionId'] == e['id']]
        assert rows
        actual = None if any(m['net'] is None for m in rows) else sum(D(m['net']) for m in rows)
        assert actual == expected, (code, actual, expected)
        if code == 'i2701':
            assert e['fees'] is None and sum(D(m['gross']) for m in rows) == D(1000)
            assert all(m['openFees'] is None and m['closeFees'] is None for m in rows)
        closed.append(dict(day=e['tradingDay'], market=e['accountId'], code=code, net=actual))
    assert sum(v['net'] for v in closed if v['net'] is not None) == D('2001.213333333333')
    assert len(first['checks']) == 9
    anchor = date.fromisoformat(first['dateAnchor'])
    epoch = date(1970, 1, 1)
    for check in first['checks']:
        offset = check['offset']
        if check['type'] == 'week':
            start = anchor - timedelta(days=anchor.weekday()) + timedelta(weeks=offset); end = start + timedelta(days=6)
        else:
            month = anchor.year * 12 + anchor.month - 1 + offset
            start = date(month // 12, month % 12 + 1, 1)
            nxt = month + 1; end = date(nxt // 12, nxt % 12 + 1, 1) - timedelta(days=1)
        assert check['start'] == str(start) and check['end'] == str(end)
        chosen = [v for v in closed if (start - epoch).days <= v['day'] <= (end - epoch).days and (check['market'] == 'all' or v['market'] == check['market']) and (check['sourceExecutionCount'] == 13 or v['code'] != 'i2701')]
        known = [v['net'] for v in chosen if v['net'] is not None]
        net = sum(known) if known else None
        assert check['closed'] == len(chosen) and check['valid'] == len(known) and check['pending'] == len(chosen) - len(known)
        assert (None if check['net'] is None else D(check['net'])) == net
        assert check['displayed'] == ('待估算' if net is None else '￥' + str(net.quantize(D('.01'), rounding=ROUND_HALF_UP)))
        assert check['result'] == 'PASS'
    assert {v['market'] for v in first['checks']} == {'all', 'stock', 'futures'}
    assert any(v['offset'] == -1 and v['type'] == 'week' for v in first['checks'])
    assert any(v['offset'] == -1 and v['type'] == 'month' for v in first['checks'])
    required = {'empty-home','empty-statistics','known-home','known-statistics','pending-home','week-statistics','month-statistics','recreated-statistics'}
    assert required <= {v['scene'] for v in first['screenshots']}
