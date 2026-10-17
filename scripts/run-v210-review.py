"""Actual plan/review/account UI instrumentation, with an external process boundary."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess


def run(api, profile, directory, root):
    spec=importlib.util.spec_from_file_location('entry_runner',Path(__file__).with_name('run-entry-experience.py'))
    entry=importlib.util.module_from_spec(spec);spec.loader.exec_module(entry)
    target=Path(root)/profile/'review';target.mkdir(parents=True,exist_ok=True)
    output=directory+'/files/v210-evidence/'+profile
    def collect():
        listing=entry.adb('shell','run-as',entry.APP,'ls',f'files/v210-evidence/{profile}/v210-review').splitlines()
        assert 0<len(listing)<=80
        for name in listing:
            assert re.fullmatch(r'v210-review-[A-Za-z0-9-]+\.(?:png|json|txt)',name)
            response=subprocess.run(['adb','shell','-T','run-as',entry.APP,'cat',f'files/v210-evidence/{profile}/v210-review/{name}'],capture_output=True,timeout=30,check=True)
            assert len(response.stdout)<=8*1024*1024
            if name.endswith('.png'):assert response.stdout.startswith(b'\x89PNG\r\n\x1a\n')
            (target/name).write_bytes(response.stdout)
    logs={}
    for phase in ('journey','reopen'):
        entry.adb('shell','am','force-stop',entry.APP)
        log=Path(root)/f'review-{profile}-{phase}-instrumentation.txt'
        try:
            result=entry.adb('shell','am','instrument','-w','-r','-e','class',entry.APP+'.v210.V210PlanReviewJourneyTest#realPlansReviewsCashAndProcessReopen','-e','visualProfile',profile,'-e','reviewUiPhase',phase,'-e','additionalTestOutputDir',output,entry.APP+'.test/androidx.test.runner.AndroidJUnitRunner',timeout=600)
        except subprocess.TimeoutExpired as error:
            raw=error.stdout or b'';log.write_text((raw.decode(errors='replace') if isinstance(raw,bytes) else raw)+'\nTIMEOUT: actual review UI failed\n');raise
        log.write_text(result)
        collect()
        assert entry.successful_runner(result),f'Actual plan/review {profile}/{phase} failed; raw evidence retained'
        logs[phase]=hashlib.sha256(log.read_bytes()).hexdigest()
    first=json.loads((target/f'v210-review-{profile}-api{api}.json').read_text())
    second=json.loads((target/f'v210-review-reopen-{profile}-api{api}.json').read_text())
    spec=importlib.util.spec_from_file_location('review_evidence',Path(__file__).with_name('v210-review-evidence.py'))
    check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)
    check.verify(first,second,api,profile)
    check.verify_candidate(first)
    return dict(profile=profile,actualInstrumentations=2,runnerLogSha256=logs,result='PASS')
