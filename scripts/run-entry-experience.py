"""Repeat actual MainActivity entry, real SAF, then force-stop and verify from a new process.

Only disposable emulators. No direct execution inserts, private keys or skipped regressions.
"""
import hashlib
import json
from pathlib import Path
import subprocess

APP="com.tradereview.app"
CLASS=APP+".v200.TradeEntryExperienceTest"
ROOT=Path("ci-v200/entry-experience")
REMOTE="/sdcard/Android/media/"+APP+"/entry-experience"


def adb(*args,timeout=60):
    return subprocess.run(["adb",*args],check=True,capture_output=True,text=True,timeout=timeout).stdout


def successful_runner(text):
    return ("OK (1 test)" in text and "INSTRUMENTATION_CODE: -1" in text
            and not any(v in text for v in ("FAILURES", "INSTRUMENTATION_FAILED", "Process crashed", "shortMsg=")))


def main():
    assert adb("shell","getprop","ro.kernel.qemu").strip()=="1","Never run this fixture on a phone"
    ROOT.mkdir(parents=True,exist_ok=True)
    api=int(adb("shell","getprop","ro.build.version.sdk").strip())
    assert api in (29,35)
    for apk in ("app/build/outputs/apk/debug/app-debug.apk","app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
        assert Path(apk).is_file()
        assert "Success" in adb("install","-r",apk)
    logs={}
    try:
        for phase in ("journey","reopen"):
            if phase=="reopen":
                adb("shell","am","force-stop",APP)
                assert not subprocess.run(["adb","shell","pidof",APP],capture_output=True,text=True,check=False).stdout.strip(),"Process did not stop"
            result=adb("shell","am","instrument","-w","-r","-e","class",CLASS,"-e","entryPhase",phase,"-e","additionalTestOutputDir",REMOTE+"/"+phase,APP+".test/androidx.test.runner.AndroidJUnitRunner",timeout=900)
            path=ROOT/(phase+"-instrumentation.txt");path.write_text(result)
            assert successful_runner(result),"Actual UI "+phase+" failed; retain runner logs"
            logs[phase]=hashlib.sha256(path.read_bytes()).hexdigest()
    except BaseException:
        # Keep the real failure and collect its screenshot/hierarchy before the AVD
        # is destroyed. Failure diagnostics must never become a successful result.
        try: adb("pull",REMOTE,str(ROOT))
        except (OSError,subprocess.SubprocessError): pass
        raise
    else:
        adb("pull",REMOTE,str(ROOT))
    journey=list(ROOT.rglob(f"trade-entry-experience-api{api}.json"));reopened=list(ROOT.rglob(f"entry-reopen-api{api}.json"))
    assert len(journey)==len(reopened)==1,"Missing/duplicate actual UI evidence"
    first=json.loads(journey[0].read_text());second=json.loads(reopened[0].read_text())
    assert first["result"]==second["result"]=="PASS" and first["allExecutionsCreatedViaRealForm"] is True
    assert first["zipSafExportReadbackAndConfirmedReplace"] is True
    assert first["pid"]!=second["pid"] and second["allFactsIdentical"] is True
    assert second["listPositionsAndStatisticsVerifiedViaUi"] is True
    report=dict(api=api,complete=True,actualInstrumentations=2,source="same locally compiled test and production MainActivity",forceStopBetweenPhases=True,runnerLogSha256=logs,journeyPid=first["pid"],reopenPid=second["pid"],result="PASS",realPhone="not-executed")
    (ROOT/f"entry-runner-api{api}.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report))


if __name__=="__main__":main()
