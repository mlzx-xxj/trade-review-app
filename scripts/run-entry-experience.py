"""Repeat actual MainActivity entry, real SAF, then force-stop and verify from a new process.

Only disposable emulators. No direct execution inserts, private keys or skipped regressions.
"""
import hashlib
import json
from pathlib import Path
import re
import subprocess

APP="com.tradereview.app"
CLASS=APP+".v200.TradeEntryExperienceTest"
ROOT=Path("ci-v200/entry-experience")
REMOTE="files/entry-experience"
SCREENSHOTS=("70-entry-stock-filled.png","71-entry-futures-filled.png","72-entry-saved-list.png","73-entry-partial-position.png","74-entry-statistics.png","75-entry-keyboard.png","76-entry-unknown-contract.png","77-entry-after-process-restart.png","78-entry-statistics-bars.png")


def adb(*args,timeout=60):
    return subprocess.run(["adb",*args],check=True,capture_output=True,text=True,timeout=timeout).stdout


def successful_runner(text):
    return ("OK (1 test)" in text and "INSTRUMENTATION_CODE: -1" in text
            and not any(v in text for v in ("FAILURES", "INSTRUMENTATION_FAILED", "Process crashed", "shortMsg=")))


def collect_output(api):
    """Read only known synthetic files from the debuggable fixture's private dir.

    Android 10 cannot create our ad-hoc external media dir without broad storage
    access. Internal storage + run-as needs no App permission or tar extraction.
    """
    assert api in (29,35)
    names=SCREENSHOTS+(f"trade-entry-experience-api{api}.json",f"entry-reopen-api{api}.json","entry-failure.txt","entry-failure.xml","entry-failure.png")
    for phase in ("journey","reopen"):
        for name in names:
            relative=f"{phase}/entry-experience/{name}"
            result=subprocess.run(["adb","exec-out","run-as",APP,"cat",REMOTE+"/"+relative],capture_output=True,timeout=30,check=False)
            if result.returncode:
                if b"No such file or directory" in result.stderr or b"No such file or directory" in result.stdout:continue
                raise RuntimeError("Cannot read private UI test evidence")
            assert len(result.stdout)<=8*1024*1024,"UI evidence file exceeds bound"
            target=ROOT/"private-output"/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(result.stdout)


def main():
    assert adb("shell","getprop","ro.kernel.qemu").strip()=="1","Never run this fixture on a phone"
    ROOT.mkdir(parents=True,exist_ok=True)
    api=int(adb("shell","getprop","ro.build.version.sdk").strip())
    assert api in (29,35)
    for apk in ("app/build/outputs/apk/debug/app-debug.apk","app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
        assert Path(apk).is_file()
        assert "Success" in adb("install","-r",apk)
    directory=adb("shell","run-as",APP,"pwd").strip()
    assert re.fullmatch(r"/data/(?:user/0|data)/"+re.escape(APP),directory),"Unexpected private fixture directory"
    logs={}
    try:
        for phase in ("journey","reopen"):
            if phase=="reopen":
                adb("shell","am","force-stop",APP)
                assert not subprocess.run(["adb","shell","pidof",APP],capture_output=True,text=True,check=False).stdout.strip(),"Process did not stop"
            result=adb("shell","am","instrument","-w","-r","-e","class",CLASS,"-e","entryPhase",phase,"-e","additionalTestOutputDir",directory+"/"+REMOTE+"/"+phase,APP+".test/androidx.test.runner.AndroidJUnitRunner",timeout=900)
            path=ROOT/(phase+"-instrumentation.txt");path.write_text(result)
            assert successful_runner(result),"Actual UI "+phase+" failed; retain runner logs"
            logs[phase]=hashlib.sha256(path.read_bytes()).hexdigest()
    except BaseException:
        # Keep the real failure and collect its screenshot/hierarchy before the AVD
        # is destroyed. Failure diagnostics must never become a successful result.
        try: collect_output(api)
        except (OSError,subprocess.SubprocessError,RuntimeError,AssertionError): pass
        raise
    else:
        collect_output(api)
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
