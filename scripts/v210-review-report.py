"""Copy untouched real AVD evidence and compare matching plan/review/account journeys."""
import argparse
import hashlib
import html
import importlib.util
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
from xml.etree import ElementTree

spec=importlib.util.spec_from_file_location('review',Path(__file__).with_name('v210-review-evidence.py'))
review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)
PROFILES=('regular-light','regular-dark','narrow-light','narrow-dark')


def one(root,name):
    matches=sorted(Path(root).rglob(name));assert matches,(name,0)
    # The existing device pack retains both the runner's original PNG and its
    # screenshot inventory copy. Accept only byte-identical copies; a differing
    # duplicate must stop evidence generation rather than select a convenient UI.
    assert len({hashlib.sha256(p.read_bytes()).hexdigest() for p in matches})==1, (name,'conflicting evidence copies')
    return matches[0]


def read(root,api,profile):
    first=json.loads(one(root,f'v210-review-{profile}-api{api}.json').read_text())
    second=json.loads(one(root,f'v210-review-reopen-{profile}-api{api}.json').read_text())
    review.verify(first,second,api,profile)
    for scene in first['screenshots']+second['screenshots']:
        assert scene['transientNoticeSettled'] is True, 'Screenshot captured before the real save notice settled'
        data=one(root,scene['file']).read_bytes()
        assert data[:8]==b'\x89PNG\r\n\x1a\n' and list(struct.unpack('>II',data[16:24]))==scene['pixels']
    return first,second


def canonical(ledger):
    """Actual UUIDs differ per clean UI run; preserve every economic field and relation."""
    ids={}
    for e in ledger['executions']:ids[e['id']]=f"execution-{e['ordinal']}"
    for c in ledger['cycles']:ids[c['id']]=f"cycle-{c['accountId']}-{c['code']}-{c['direction']}"
    for p in ledger['plans']:ids[p['id']]=f"plan-{p['accountId']}-{p['code']}-{p['day']}"
    for lot in ledger['lots']:ids[lot['id']]='lot-'+ids[lot['executionId']]
    for m in ledger['matches']:ids[m['id']]='match-'+ids[m['closeExecutionId']]+'-'+ids[m['openExecutionId']]
    for r in ledger['reviews']:ids[r['id']]='review-'+ids[r['cycleId']]+'-'+str(r['day'])
    for f in ledger['cashFlows']:ids[f['id']]=f"flow-{f['accountId']}-{f['kind']}"
    def rewrite(v):
        if isinstance(v,dict):return {k:rewrite(x) for k,x in sorted(v.items())}
        if isinstance(v,list):return sorted((rewrite(x) for x in v),key=lambda x:json.dumps(x,ensure_ascii=False,sort_keys=True))
        if isinstance(v,str):return ids.get(v,v)
        return v
    assert len(ids)==sum(len(ledger[k]) for k in ('executions','cycles','plans','lots','matches','reviews','cashFlows')), 'Fixture IDs are ambiguous'
    return rewrite(ledger)


def verify_colors(first):
    profit,loss=('#FFFFB4B0','#FF80D5AF') if first['night'] else ('#FFAF273C','#FF087044')
    expected={'￥992.40':profit,'￥-628.01':loss}
    seen=set()
    for row in first['layouts']:
        if not row['visible']:continue
        text=row['text']
        if text in expected:
            assert row['argb']==expected[text], row
            seen.add(text)
        elif text=='待估算':
            assert row['argb'] not in (profit,loss), row
            seen.add(text)
    assert seen==set(expected)|{'待估算'}, 'Missing actual positive, negative or pending layout color evidence'


def compare(before,after):
    review.verify_candidate(after)
    verify_colors(after)
    for key in ('api','profile','dateAnchor','screenWidthDp','fontScale','night'):assert before[key]==after[key],key
    assert canonical(before['ledger'])==canonical(after['ledger']), 'Changed saved values, authoritative financial facts or associations'
    assert len(before['operations'])==len(after['operations'])==14, 'Missing or extra measured UI operation'
    rows=[]
    for old,new in zip(before['operations'],after['operations']):
        assert old['operation']==new['operation']
        if old['operation'] in ('new-stock-plan','new-futures-plan'):
            assert new['typedFields']==old['typedFields']
            assert new['normalTaps']+new['keyboardBackKeys']<=old['normalTaps']+old['keyboardBackKeys']
        # Record locator scrolling verbatim. IME bring-into-view timing can change
        # how many explicit scroll commands the harness needs; that count is not
        # a user's gesture count or proof of a faster/slower form. Required fields,
        # clicks, real keyboard clearance and every financial check remain gates.
        rows.append(dict(operation=old['operation'],before=old,after=new,
                         locatorScrollDelta=new['scrollCommands']-old['scrollCommands']))
    assert len(rows)==14
    return dict(economicFactsAndAssociationsEqual=True,actualProcessReopen=True,operations=rows,beforeCashEditTargetRetained=before['cashEditTargetRetained'],afterCashEditTargetRetained=after['cashEditTargetRetained'],beforeLayouts=before['layouts'],afterLayouts=after['layouts'])


def main():
    p=argparse.ArgumentParser();p.add_argument('--before',required=True);p.add_argument('--after',required=True);p.add_argument('--output',required=True);p.add_argument('--baseline-run',required=True);p.add_argument('--candidate-run',required=True)
    args=p.parse_args();out=Path(args.output);out.mkdir(parents=True,exist_ok=True)
    result=dict(baselineRun=args.baseline_run,candidateRun=args.candidate_run,profiles={},verifiedSources={},screenshotSha256={},originalEvidenceSha256={},realPhone='not-executed')
    pairs=[];markdown=[]
    scenes={'empty-plans':'空计划','plans-list':'同日多条计划','stock-plan-keyboard':'股票计划与键盘','futures-plan-keyboard':'期货计划与键盘','plan-editor-long':'计划长文字与关键价位','plan-landscape':'横屏计划','open-review-summary':'只读持仓与真实亏损','review-optional-prices':'复盘止盈止损与关键价位','review-cycles':'持仓周期与历史复盘','review-editor-long':'长文字复盘','review-keyboard':'复盘与键盘','review-landscape':'横屏复盘','pending-review':'费用待估算复盘','stock-account-unknown':'参考现金不可计算','stock-account-known':'股票账户实际金额','futures-account-pending':'期货账户待估算','cash-edit-landscape':'流水编辑旋转后的目标','cashflow-list':'资金流水'}
    def retain(source,relative,section):
        target=out/relative;target.parent.mkdir(parents=True,exist_ok=True)
        assert source.stat().st_size<=8*1024*1024
        shutil.copyfile(source,target);result[section][relative.as_posix()]=hashlib.sha256(target.read_bytes()).hexdigest()
    for api in (29,35):
        for profile in PROFILES:
            roots=[Path(args.before)/f'api{api}',Path(args.after)/f'api{api}']
            values=[read(root,api,profile)[0] for root in roots]
            result['profiles'][f'api{api}-{profile}']=compare(*values)
            for side,root in zip(('before','after'),roots):
                receipt=json.loads((root/'LOCAL-VERIFICATION.json').read_text())
                assert re.fullmatch(r'[0-9a-f]{40}',receipt['source'])
                assert receipt['junit']['tests']>=143 and not any(receipt['junit'][k] for k in ('failures','errors','skipped'))
                feedback=[case for xml in root.rglob('*.xml') if 'androidTest-results' in xml.parts
                          for case in ElementTree.parse(xml).getroot().iter('testcase')
                          if case.get('classname','').endswith('V210FeedbackLifecycleTest')]
                assert len(feedback)==1 and feedback[0].get('name')=='repeatedSameTextBeforeNextFrameIsShownAndDrainedWithoutChangingLedger'
                assert not any(feedback[0].find(k) is not None for k in ('failure','error','skipped'))
                result['verifiedSources'][f'{side}-api{api}']=receipt
                for filename in (f'v210-review-{profile}-api{api}.json',f'v210-review-reopen-{profile}-api{api}.json',f'review-{profile}-journey-instrumentation.txt',f'review-{profile}-reopen-instrumentation.txt'):
                    retain(one(root,filename),Path('evidence')/side/f'api{api}'/filename,'originalEvidenceSha256')
                if profile==PROFILES[0]:
                    for xml in root.rglob('*.xml'):
                        if 'androidTest-results' in xml.parts:retain(xml,Path('evidence')/side/f'api{api}'/xml.name,'originalEvidenceSha256')
            if api!=35:continue
            selected=['stock-plan-keyboard','review-keyboard','stock-account-known','futures-account-pending']
            if profile=='regular-light':selected=list(scenes)
            for scene in selected:
                label=f'API {api} · {profile} · {scenes[scene]}';paths=[];dimensions=[]
                for side,root,value in zip(('before','after'),roots,values):
                    record=next(s for s in value['screenshots'] if s['scene']==scene);dimensions.append(record['pixels'])
                    rel=Path(side)/record['file'];retain(one(root,record['file']),rel,'screenshotSha256');paths.append(rel.as_posix())
                assert dimensions[0]==dimensions[1],label
                markdown.append(f'### {label}\n\n| 改造前 | 改造后 |\n|---|---|\n| [![改造前]({paths[0]})]({paths[0]}) | [![改造后]({paths[1]})]({paths[1]}) |\n')
                pairs.append('<section><h2>'+html.escape(label)+'</h2><div>'+''.join('<figure><figcaption>'+side+'</figcaption><img src="'+path+'" loading="lazy"></figure>' for side,path in zip(('改造前','改造后'),paths))+'</div></section>')
    for side,root in [('before',Path(args.before)/'build'),('after',Path(args.after)/'build')]:
        receipt=json.loads((root/'LOCAL-VERIFICATION.json').read_text());assert receipt['source']==result['verifiedSources'][f'{side}-api29']['source']==result['verifiedSources'][f'{side}-api35']['source']
        assert receipt['junit']['tests']>=241 and not any(receipt['junit'][k] for k in ('failures','errors','skipped'))
        result['verifiedSources'][f'{side}-build']=receipt
        for source in list((root/'test-results').glob('*.xml'))+list(root.glob('lint-results-*.xml')):
            retain(source,Path('evidence')/side/'jvm-lint'/source.relative_to(root),'originalEvidenceSha256')
    # Prove that retained screenshots still represent the current production UI.
    ui_files=['app/src/main/java/com/tradereview/app/v200/'+name for name in
              ('V200Review.kt','V200AccountsStats.kt','V210ReviewComponents.kt')]
    before_source=result['verifiedSources']['before-build']['source']
    after_source=result['verifiedSources']['after-build']['source']
    changed=subprocess.run(['git','diff','--name-only',before_source,after_source,'--','app/src/main'],check=True,capture_output=True,text=True).stdout.splitlines()
    assert set(changed)==set(ui_files), changed
    stats_path='app/src/main/java/com/tradereview/app/v200/V200AccountsStats.kt'
    old_stats=subprocess.run(['git','show',before_source+':'+stats_path],check=True,capture_output=True).stdout
    new_stats=subprocess.run(['git','show',after_source+':'+stats_path],check=True,capture_output=True).stdout
    marker=b'private data class CalculatedStatistics'
    assert old_stats[old_stats.index(marker):]==new_stats[new_stats.index(marker):], 'Changed accepted statistics UI/calculation suffix'
    result['statisticsSuffixUnchanged']=True
    result['productionUiSha256']={}
    for path in ui_files:
        data=subprocess.run(['git','show',after_source+':'+path],check=True,capture_output=True).stdout
        current=Path(path).read_bytes();assert data==current, 'Screenshots no longer match current UI: '+path
        result['productionUiSha256'][path]=hashlib.sha256(data).hexdigest()
    # Both sides share the minimal feedback lifecycle repair discovered by real
    # regression failures. It does not alter baseline page layouts or ledger data.
    shared='app/src/main/java/com/tradereview/app/v200/V200App.kt'
    old=subprocess.run(['git','show',before_source+':'+shared],check=True,capture_output=True).stdout
    new=subprocess.run(['git','show',after_source+':'+shared],check=True,capture_output=True).stdout
    assert old==new==Path(shared).read_bytes(), 'Different shared feedback/navigation source'
    result['sharedFeedbackUiSha256']={shared:hashlib.sha256(new).hexdigest()}
    result['sharedUiTestSourceSha256']={}
    for name in ('V210PlanReviewJourneyTest.kt','V210HomeStatisticsJourneyTest.kt','V210FeedbackLifecycleTest.kt'):
        path='app/src/androidTest/java/com/tradereview/app/v210/'+name
        old=subprocess.run(['git','show',before_source+':'+path],check=True,capture_output=True).stdout
        new=subprocess.run(['git','show',after_source+':'+path],check=True,capture_output=True).stdout
        assert old==new==Path(path).read_bytes(), 'Different before/after UI test or measurement source: '+path
        result['sharedUiTestSourceSha256'][path]=hashlib.sha256(new).hexdigest()
    (out/'.gitattributes').write_text('# Keep original native/JVM evidence bytes, including CRLF.\nevidence/** -text\n')
    (out/'comparison.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    (out/'README.md').write_text('# 计划、复盘、账户：真实模拟器前后对照\n\n原始 UiAutomation PNG 按字节复制，无裁剪、重绘或合成。点击查看原图。\n\n[来源 SHA、归档/文件哈希与实际操作和布局测量](comparison.json) · [原始 JSON / JUnit / 操作日志](evidence)\n\n'+'\n'.join(markdown))
    css='body{font:16px system-ui;max-width:950px;margin:auto;padding:16px;background:#f3f5f9;color:#17243a}h1{font-size:24px}h2{font-size:16px}section{background:white;border:1px solid #d7dfea;border-radius:12px;padding:12px;margin:16px 0}section>div{display:grid;grid-template-columns:1fr 1fr;gap:8px}figure{margin:0;min-width:0}img{width:100%;height:auto}figcaption{padding:8px}'
    (out/'index.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>V2.1 第二批实际对照</title><style>'+css+'</style><h1>计划、复盘与账户实际对照</h1><p>同数据、同屏幕、同字体与主题；仅浏览器缩放，不修改截图像素。正式版本仍为2.0.0 / code22。</p>'+''.join(pairs)+'</html>')
    print(json.dumps(dict(profilesCompared=len(result['profiles']),unalteredPngCopies=len(result['screenshotSha256']),retainedOriginalEvidence=len(result['originalEvidenceSha256']),result='PASS')))


if __name__=='__main__':main()
