"""Reject missing/failed actual CI evidence; never turn signing/phone gaps into success."""
import argparse
from collections import Counter
import json
import importlib.util
from pathlib import Path
from xml.etree import ElementTree as ET


def junit(root, fragment, minimum, required):
    total=Counter(); classes=Counter()
    for path in root.rglob('*.xml'):
        if fragment not in path.as_posix():continue
        suite=ET.parse(path).getroot()
        total.update({k:int(suite.get(k,'0')) for k in ['tests','failures','errors','skipped']})
        for case in suite.iter('testcase'):classes[case.get('classname','').split('.')[-1]]+=1
    assert total['tests']>=minimum and not any(total[k] for k in ['failures','errors','skipped']),dict(total)
    for name,count in required.items():assert classes[name]>=count,(name,classes[name])
    return dict(total)


def pending_entry_evidence(data):
    """Require the actual new UI acceptance cases before a trusted candidate."""
    observations=data['observations']
    rounded=[v for v in observations if v.get('operation')=='rounded-partial-net']
    assert len(rounded)==1 and rounded[0]['displayedNet']=='￥971.83' and rounded[0]['savedRawNet']=='971.833333333333'
    pending=[v for v in observations if v.get('operation')=='pending-common-product']
    assert len(pending)==2 and [v['action'] for v in pending]==['buy-open','sell-close']
    assert [v['remaining'] for v in pending]==['2','1']
    assert all(v['code']=='i2701' and v['multiplier']=='100' and v['savedFees'] is None and v['savedMarginRate'] is None for v in pending)
    ledger=data['ledger'];assert len(ledger['executions'])==8
    cycles=[v for v in ledger['cycles'] if v['code']=='i2701']
    assert len(cycles)==1 and cycles[0]['remaining']=='1' and cycles[0]['realized'] is None
    matches=[v for v in ledger['matches'] if v['cycleId']==cycles[0]['id']]
    assert len(matches)==1 and matches[0]['gross']=='1000' and matches[0]['net'] is None


def ui_publication_evidence(reports, api):
    """A PASS label cannot hide worker-thread UI publication or a broken probe."""
    required={
        'calibration': {'workerEmissionDetected':True},
        'ledger': {'confirmedEditAndDelete':True,'previewKeptFacts':True,'failedWriteKeptFacts':True,'auditCount':2},
        'backup': {'jsonAndZipReadback':True,'previewRefreshCancel':True,'restoreKeptFacts':True,'writeFailureKeptFacts':True,'csvBothKinds':True},
    }
    for kind, checks in required.items():
        value=reports[kind]
        assert value['api']==api and value['result']=='PASS' and value['syntheticOnly'] is True
        assert value['checks']==checks and value['emissions']
        assert all(isinstance(e['main'],bool) and e['thread'] for e in value['emissions'])
        if kind=='calibration':
            assert [(e['flow'],e['value'],e['main']) for e in value['emissions']]==[
                ('calibration','0',True),('calibration','1',False),('calibration','2',True)]
        else:
            assert all(e['main'] is True for e in value['emissions']), 'Worker published UI state'
    assert [e['value'] for e in reports['ledger']['emissions'] if e['flow']=='pending']==['none','edit','none','delete','none']
    assert sum(e['flow']=='preview' and e['value']=='present' for e in reports['backup']['emissions'])>=4


def main():
    p=argparse.ArgumentParser();p.add_argument('--reports',required=True);p.add_argument('--screenshots',required=True);p.add_argument('--dist',required=True);p.add_argument('--run-url',required=True);a=p.parse_args()
    root=Path(a.reports);dist=Path(a.dist)
    counts={'jvm':junit(root/'build','/test-results/',241,{'ExperiencePolicyTest':3,'ExportReadbackTest':4,'MatchingEngineTest':37,'AutomaticEntryTest':19,'LedgerBackupTest':7,'ChartScaleTest':11,'LedgerCsvTest':3,'PendingAmountsTest':13})}
    for variant in ['debug','rc','release']:
        lint=list((root/'build').rglob('lint-results-'+variant+'.xml'));assert len(lint)==1,variant
        counts['lint-'+variant]=dict(Counter(i.get('severity') for i in ET.parse(lint[0]).getroot().findall('issue')))
        assert not any(counts['lint-'+variant].get(k,0) for k in ['Error','Fatal'])
    metrics={}
    for api in [29,35]:
        directory=root/'devices'/f'android-test-reports-api{api}'
        counts[f'api{api}']=junit(directory,'/androidTest-results/',140,{'ExperienceDataTest':5,'ReleaseExperienceUiTest':4,'LedgerMigrationTest':2,'PendingMigrationTest':3,'LedgerRepositoryTest':19,'TradeEntryExperienceTest':1,'V200MainUiTest':6,'V210VisualJourneyTest':1,'V210LayoutTest':1,'UiPublicationThreadTest':3,'V210HomeStatisticsJourneyTest':1})
        names={f.name for f in (Path(a.screenshots)/f'v200-emulator-screenshots-api{api}').rglob('*.png')}
        for prefix in [f'{i:02}-' for i in list(range(1,33))+list(range(40,56))+list(range(70,84))]:assert any(n.startswith(prefix) for n in names),f'Missing API {api} screenshot {prefix}'
        files=[f'{kind}-api{api}.json' for kind in ['performance','images','activity','ocr-evaluation','process-probes','attachment-queries','package-probes','trade-entry-experience','entry-reopen','entry-runner']]
        for filename in files:
            paths=list(directory.rglob(filename));assert paths,filename
            contents=[json.loads(f.read_text()) for f in paths];assert all(c==contents[0] for c in contents)
            metrics[filename]=contents[0]
        publication={}
        for kind in ['calibration','ledger','backup']:
            filename=f'ui-publication-{kind}-api{api}.json'
            paths=list(directory.rglob(filename));assert len(paths)==1,filename
            publication[kind]=json.loads(paths[0].read_text());metrics[filename]=publication[kind]
        ui_publication_evidence(publication,api)
        for profile in ['regular-light','regular-dark','narrow-light','narrow-dark']:
            full={}
            for phase in ['', 'reopen-']:
                filename=f'v210-full-{phase}{profile}-api{api}.json'
                paths=list(directory.rglob(filename));assert len(paths)==1,filename
                full[phase]=json.loads(paths[0].read_text());metrics[filename]=full[phase]
                assert all(s['file'] in names for s in full[phase]['screenshots'])
            spec=importlib.util.spec_from_file_location('full_evidence',Path(__file__).with_name('v210-full-evidence.py'))
            checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)
            checker.verify(full[''],full['reopen-'],api,profile)
            filename=f'v210-visual-{profile}-api{api}.json'
            paths=list(directory.rglob(filename));assert len(paths)==1,filename
            data=json.loads(paths[0].read_text());metrics[filename]=data
            assert data['result']=='PASS' and data['syntheticOnly'] is True and data['allExecutionsCreatedViaRealForm'] is True
            assert data['profile']==profile and data['api']==api and data['night']==profile.endswith('dark')
            assert abs(data['fontScale']-(1.3 if profile.startswith('narrow') else 1.0))<.01
            assert len(data['observations'])==5 and len(data['screenshots'])==11
            assert all(s['file'] in names for s in data['screenshots'])
            density=data['directionDensity']
            assert density['result']=='PASS' and density['allExecutionsCreatedViaRealForm'] is True
            assert density['positionCount']==4 and density['executionCount']==7 and density['additionalExecutions']==2
            assert len(density['positions'])==4 and {s['scene'] for s in density['screenshots']}=={'density-top','density-long','density-short','density-pending'}
            assert all(s['file'] in names for s in density['screenshots'])
            expected='#FFB4CBF7' if data['night'] else '#FF304C75'
            assert all(p['directionArgb']==expected for p in density['positions'])
            rb=[p for p in density['positions'] if p['code']=='rb2701']
            assert {p['direction'] for p in rb}=={'long','short'}
            assert {p['displayedNet'] for p in rb}=={'￥971.83','￥-108.03'}
        visual_runner=list(directory.rglob(f'v210-runner-api{api}.json'));assert len(visual_runner)==1
        visual=json.loads(visual_runner[0].read_text());assert visual['complete'] is True and visual['actualInstrumentations']==4 and visual['result']=='PASS'
        assert visual['fullUiInstrumentations']==8
        layout_reports=list(directory.rglob(f'v210-layout-api{api}.json'));assert len(layout_reports)==1
        layout=json.loads(layout_reports[0].read_text());metrics[f'v210-layout-api{api}.json']=layout
        assert layout['result']=='PASS' and layout['widthDp']==320 and layout['syntheticOnly'] is True
        assert len(layout['checks'])==8 and all(not c['overflowWidth'] and not c['overflowHeight'] and c['fontSp']>=14 for c in layout['checks'])
        for layout in ['v210-layout-long-name-large-money-portrait.png','v210-layout-long-name-large-money-landscape.png']:assert layout in names,layout
        assert metrics[f'performance-api{api}.json'].get('complete') is True
        assert metrics[f'process-probes-api{api}.json']['allSixteenTablesUnchanged'] and metrics[f'process-probes-api{api}.json']['allLedgerTablesUnchanged']
        assert metrics[f'entry-runner-api{api}.json']['complete'] is True
        assert metrics[f'trade-entry-experience-api{api}.json']['allExecutionsCreatedViaRealForm'] is True
        pending_entry_evidence(metrics[f'trade-entry-experience-api{api}.json'])
        assert metrics[f'entry-reopen-api{api}.json']['allFactsIdentical'] is True
        probe=metrics[f'package-probes-api{api}.json']
        assert probe['rcInstalled'] and probe['rcNonDebuggable'] and probe['rcRestartKeptHome']
        rc=json.loads((dist/'RC-IDENTITY.json').read_text());assert probe['rcActualApkSha']==rc['apkSha256']
        if (dist/'FIXED-IDENTITY.json').exists():
            assert probe['fixedReleaseInstall']=='passed-permanent-v2-pin-non-debuggable-release'
            assert any(n.startswith('56-') for n in names)
    (dist/'PERFORMANCE-AND-OCR.json').write_text(json.dumps(metrics,ensure_ascii=False,indent=2)+'\n')
    text=f'versionName=2.0.0-rc.3\nversionCode=22\napplicationId=com.tradereview.app.rc.v200\nGitHubActions={a.run_url}\nDebugBuild=success\nRcNonDebuggableBuild=success\nReleaseCompilation=success\n'+json.dumps(counts,ensure_ascii=False,indent=2)+'\nAPI 29/35: complete regressions, actual process restart and delivered non-debuggable APK install probes. All OCR images and database fixtures are synthetic.\nStableRelease=false\nRealPhoneAcceptance=not-executed\nPermanent V2 candidate installation and optional expected V0.3 signature incompatibility are recorded in package-probes-api*.json and FIXED-IDENTITY.json. Old V0.3 cannot be overlaid by the new V2 certificate. No same-permanent-signer earlier APK upgrade or real phone acceptance is claimed. Missing permanent Secrets means RC only.\nLimits: legacy UI regressions are retained in a Debug-only host; screenshots40-55 cover V2 actual MainActivity. Shared-host performance is descriptive, not a phone benchmark; old OCR remains bundled but V2 has no OCR entry; ZIP/JSON backups are plaintext; device backup timestamps and guide state are local operational metadata and are not restored as business data.\n'
    (dist/'CI-RESULTS.txt').write_text(text);print(text)


if __name__=='__main__':main()
