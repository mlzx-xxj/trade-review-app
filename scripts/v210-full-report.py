"""Pair untouched real PNGs and independently verified overview evidence for mobile review."""
import argparse
import hashlib
import html
import importlib.util
import json
from pathlib import Path
import shutil
import struct

PROFILES=('regular-light','regular-dark','narrow-light','narrow-dark')
spec=importlib.util.spec_from_file_location('full_evidence',Path(__file__).with_name('v210-full-evidence.py'))
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)
spec=importlib.util.spec_from_file_location('entry_comparison',Path(__file__).with_name('v210-visual-report.py'))
entry=importlib.util.module_from_spec(spec);spec.loader.exec_module(entry)


def one(root,name):
    paths=list(Path(root).rglob(name));assert len(paths)==1,(name,len(paths));return paths[0]


def read(root,api,profile):
    first=json.loads(one(root,f'v210-full-{profile}-api{api}.json').read_text())
    second=json.loads(one(root,f'v210-full-reopen-{profile}-api{api}.json').read_text())
    checker.verify(first,second,api,profile)
    assert first['night']==profile.endswith('dark')
    assert abs(first['fontScale']-(1.3 if profile.startswith('narrow') else 1))<.01
    assert first['screenWidthDp'] in (range(318,323) if profile.startswith('narrow') else range(358,363))
    assert first['orientationRestored'] is True and first['moneyLayouts']
    for scene in first['screenshots']:
        raw=one(root,scene['file']).read_bytes()
        assert raw[:8]==b'\x89PNG\r\n\x1a\n' and list(struct.unpack('>II',raw[16:24]))==scene['pixels']
    return first


def compare(before,after):
    assert before['dateAnchor']==after['dateAnchor'], 'Different calendar fixture dates'
    safety=after['toolbarSafety']
    assert len(safety)==2 and {v['orientation'] for v in safety}=={1,2}
    for sample in safety:
        assert sample['result']=='PASS' and {v['tag'] for v in sample['buttons']}=={'open-backup','open-risk-strategy'}
        for button in sample['buttons']:
            assert button['left']>=sample['safeLeft']-1 and button['right']<=sample['safeRight']+1 and button['top']>=sample['safeTop']-1
    rows=entry.compare(before,after)
    assert before['expectedClosed']==after['expectedClosed']
    assert before['checks']==after['checks'], 'Changed period/market/sample/net outcomes'
    assert [(m['tag'],m['text']) for m in before['moneyLayouts']]==[(m['tag'],m['text']) for m in after['moneyLayouts']]
    assert all(not m['overflowWidth'] and not m['overflowHeight'] for m in after['moneyLayouts']), 'New overview amounts overflow'
    rendered=[]
    for a,b in zip(before['moneyLayouts'],after['moneyLayouts']):
        assert a['argb']==b['argb'], 'Changed profit/loss/neutral meaning'
        assert b['fontSp']>=14
        assert not a['visible'] or b['visible'], 'Previously visible overview amount is hidden'
        rendered.append(dict(tag=a['tag'],text=a['text'],beforeFontSp=a['fontSp'],afterFontSp=b['fontSp'],beforeLines=a['lines'],afterLines=b['lines'],beforeVisible=a['visible'],afterVisible=b['visible']))
    return dict(entry=rows,moneyLayouts=rendered,periodChecks=len(after['checks']),financialFactsEqual=True,actualProcessReopen=True,toolbarSafety=safety)


def main():
    p=argparse.ArgumentParser();p.add_argument('--before',required=True);p.add_argument('--after',required=True);p.add_argument('--output',required=True);p.add_argument('--baseline-run',required=True);p.add_argument('--candidate-run',required=True)
    a=p.parse_args();out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
    comparison={};sources={};hashes={};evidence_hashes={};sections=[];markdown=[]
    labels={'known-home':'首页（已知盈利）','pending-home':'首页（含待估算）','month-statistics':'月度统计','month-statistics-chart':'每日净盈亏图','month-statistics-distribution':'品种分布','pending-home-calendar':'展开月历','landscape-home':'横屏首页','empty-home':'空账本首页'}
    for api in (29,35):
        for profile in PROFILES:
            roots=[Path(a.before)/f'api{api}',Path(a.after)/f'api{api}']
            values=[read(r,api,profile) for r in roots]
            comparison[f'api{api}-{profile}']=compare(*values)
            for label,root in zip(('before','after'),roots):
                receipt=json.loads((root/'LOCAL-VERIFICATION.json').read_text())
                assert len(receipt['source'])==40 and len(receipt['archiveSha256'])==64
                assert receipt['junit']['tests']>=140 and not any(receipt['junit'][k] for k in ('failures','errors','skipped'))
                sources[f'{label}-api{api}']=receipt
                # Keep compact original synthetic proof readable after temporary
                # Actions retention expires. No log or screenshot is rewritten.
                proofs=[one(root,f'v210-full-{profile}-api{api}.json'),one(root,f'v210-full-reopen-{profile}-api{api}.json')]
                proofs += [one(root,f'full-{profile}-{phase}-instrumentation.txt') for phase in ('journey','reopen')]
                if profile==PROFILES[0]:
                    xmls=[p for p in root.rglob('*.xml') if 'androidTest-results' in p.parts]
                    assert xmls, 'Original native JUnit missing'
                    proofs += xmls
                for proof in proofs:
                    relative=Path('evidence')/label/f'api{api}'/proof.name
                    target=out/relative;target.parent.mkdir(parents=True,exist_ok=True)
                    assert proof.stat().st_size <= 2*1024*1024
                    if target.exists():assert target.read_bytes()==proof.read_bytes()
                    else:shutil.copyfile(proof,target)
                    evidence_hashes[relative.as_posix()]=hashlib.sha256(target.read_bytes()).hexdigest()
            if api!=35:continue
            scenes=['pending-home','month-statistics']
            if profile.startswith('regular'):scenes+=['known-home']
            if profile=='regular-light':scenes+=['month-statistics-chart','month-statistics-distribution','pending-home-calendar','landscape-home','empty-home']
            for scene in scenes:
                title=f'API {api} · {profile} · {labels[scene]}';figures=[];paths=[]
                for label,root,value in zip(('before','after'),roots,values):
                    record=next(s for s in value['screenshots'] if s['scene']==scene)
                    path=Path(label)/record['file'];target=out/path;target.parent.mkdir(parents=True,exist_ok=True)
                    shutil.copyfile(one(root,record['file']),target)
                    hashes[path.as_posix()]=hashlib.sha256(target.read_bytes()).hexdigest();paths.append(path.as_posix())
                    figures.append(f'<figure><figcaption>{"第一阶段基线" if label=="before" else "本批改造"}</figcaption><a href="{path.as_posix()}"><img src="{path.as_posix()}" loading="lazy" alt="{html.escape(title)} {label}"></a></figure>')
                assert next(s['pixels'] for s in values[0]['screenshots'] if s['scene']==scene)==next(s['pixels'] for s in values[1]['screenshots'] if s['scene']==scene)
                sections.append(f'<section><h2>{html.escape(title)}</h2><div class="pair">'+''.join(figures)+'</div></section>')
                markdown.append(f'### {title}\n\n| 改造前 | 改造后 |\n|---|---|\n| [![改造前]({paths[0]})]({paths[0]}) | [![改造后]({paths[1]})]({paths[1]}) |\n')
    for side,root in [('before',Path(a.before)/'build'),('after',Path(a.after)/'build')]:
        receipt=json.loads((root/'LOCAL-VERIFICATION.json').read_text())
        assert receipt['source']==sources[f'{side}-api29']['source']==sources[f'{side}-api35']['source'], 'Mixed source artifacts'
        assert receipt['junit']['tests']>=241 and not any(receipt['junit'][k] for k in ('failures','errors','skipped'))
        sources[f'{side}-build']=receipt
        proofs=list((root/'test-results').glob('*.xml'))+list(root.glob('lint-results-*.xml'))
        assert len(list(root.glob('lint-results-*.xml')))==3
        for proof in proofs:
            relative=Path('evidence')/side/'jvm-lint'/proof.relative_to(root)
            target=out/relative;target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(proof,target)
            evidence_hashes[relative.as_posix()]=hashlib.sha256(target.read_bytes()).hexdigest()
    # Existing native regression also scrolls the real month chart to the
    # populated dates. Keep these original PNGs alongside the four-profile
    # journey, whose initial month viewport intentionally starts on day one.
    native_pairs={}
    for filename,label in [('48-v200-monthly-bars.png','原有全量回归：月图滑到有盈亏的日期'),('78-entry-statistics-bars.png','原有真实录入回归：已知部分与待估算日期')]:
        figures=[];paths=[];dimensions=[]
        for side,root in [('before',Path(a.before)/'api35'),('after',Path(a.after)/'api35')]:
            source=one(root,filename);raw=source.read_bytes()
            assert raw[:8]==b'\x89PNG\r\n\x1a\n'
            dimensions.append(list(struct.unpack('>II',raw[16:24])))
            relative=Path(side)/filename;target=out/relative
            shutil.copyfile(source,target);paths.append(relative.as_posix())
            hashes[relative.as_posix()]=hashlib.sha256(raw).hexdigest()
            figures.append(f'<figure><figcaption>{"第一阶段基线" if side=="before" else "本批改造"}</figcaption><a href="{relative.as_posix()}"><img src="{relative.as_posix()}" loading="lazy" alt="{label} {side}"></a></figure>')
        assert dimensions[0]==dimensions[1]
        native_pairs[filename]=dict(pixels=dimensions[0],source='same unchanged native regression fixture and screenshot steps; API 35 light',financialAssertions='see retained original JUnit and unchanged V200MainUiTest / TradeEntryExperienceTest')
        sections.append(f'<section><h2>API 35 · {label}</h2><div class="pair">'+''.join(figures)+'</div></section>')
        markdown.append(f'### API 35 · {label}\n\n| 改造前 | 改造后 |\n|---|---|\n| [![改造前]({paths[0]})]({paths[0]}) | [![改造后]({paths[1]})]({paths[1]}) |\n')
    result=dict(baselineRun=a.baseline_run,candidateRun=a.candidate_run,verifiedSources=sources,profiles=comparison,nativeChartPairs=native_pairs,screenshotSha256=hashes,originalEvidenceSha256=evidence_hashes,images='byte-for-byte actual UiAutomation PNG; no pixel edits, crops, AI or synthetic UI',phoneAcceptance='not-executed')
    (out/'comparison.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    (out/'README.md').write_text('# 首页／统计实际模拟器前后对照\n\n原始 PNG 按字节复制，未重绘、裁剪或合成；点击可看原图。全量截图与 JUnit、原始操作日志在对应 Actions 产物中。\n\n[来源 SHA、归档／文件哈希与操作和字号对照](comparison.json) · [原始 JUnit、界面数据及外部操作日志](evidence)\n\n'+'\n'.join(markdown))
    page='<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>V2.1 首页与统计实际对照</title><style>body{font:16px system-ui;background:#f3f5f9;color:#17243a;margin:16px auto;max-width:900px;padding:0 12px}h1{font-size:24px}h2{font-size:17px}section{padding:12px;background:white;border:1px solid #d7dfea;border-radius:12px;margin:16px 0}.pair{display:grid;grid-template-columns:1fr 1fr;gap:8px}figure{margin:0;min-width:0}figcaption{padding:8px;font-weight:600}img{width:100%;height:auto;display:block}</style><h1>V2.1 第一批实际界面对照</h1><p>同设备、同数据、同字体、同主题；仅浏览器缩放显示，不编辑截图像素。正式版本仍为 2.0.0 / code22。</p>'+''.join(sections)+'</html>'
    (out/'index.html').write_text(page)
    print(json.dumps(dict(profilesCompared=len(comparison),unalteredPngCopies=len(hashes),result='PASS')))


if __name__=='__main__':main()
