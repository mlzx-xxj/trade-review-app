"""Additional actual MainActivity + DocumentsUI journey and external process boundary."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


entry = module("run-entry-experience")
check = module("v210-consistency-evidence")
ROOT = Path("ci-v200/v210-consistency")
LOGS = Path("ci-v200/v210-visual")
METHOD = "realConfigurationBackupAndReopenPreserveFinancialFacts"


def native_gate():
    cases = []
    xmls = list(Path("app/build/outputs/androidTest-results").rglob("TEST-*.xml"))
    assert xmls, "Full native XML missing"
    for path in xmls:
        root = ET.parse(path).getroot()
        cases.extend(root.iter("testcase"))
        assert all(not node.findall("failure") and not node.findall("error") and not node.findall("skipped") for node in root.iter("testcase"))
    assert len(cases) >= 148, "All 143 original and five new native tests must execute"
    required = {"strategyEditorKeepsIdentityAndDraftAfterRecreation", "contractRuleEditorKeepsIdentityAndDraftAfterRecreation",
                "riskInputKeepsHeaderVisibleWithRealKeyboard", "riskRuleAndAccountControlsExposeNamesAndState", METHOD}
    actual = {n.get("name") for n in cases if n.get("classname", "").startswith(entry.APP + ".v210.V210Consistency")}
    assert required <= actual, "A new strict native regression was not executed"
    return len(cases)


def collect(profile):
    folder = f"files/v210-evidence/{profile}/v210-consistency"
    listing = entry.adb("shell", "run-as", entry.APP, "ls", folder).splitlines()
    assert 0 < len(listing) <= 80
    target = ROOT / profile
    target.mkdir(parents=True, exist_ok=True)
    for name in listing:
        assert re.fullmatch(r"v210-consistency-[a-zA-Z0-9-]+\.(?:png|json|txt)", name)
        response = subprocess.run(["adb", "shell", "-T", "run-as", entry.APP, "cat", folder + "/" + name],
                                  capture_output=True, timeout=30, check=True)
        assert len(response.stdout) <= 8 * 1024 * 1024
        if name.endswith(".png"):
            assert response.stdout.startswith(b"\x89PNG\r\n\x1a\n")
        (target / name).write_bytes(response.stdout)


def run_profile(api, profile, directory):
    phases = {}
    for phase in ("journey", "reopen"):
        entry.adb("shell", "am", "force-stop", entry.APP)
        log = LOGS / f"consistency-{profile}-{phase}-instrumentation.txt"
        try:
            result = entry.adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
                               entry.APP + ".v210.V210ConsistencyJourneyTest#" + METHOD,
                               "-e", "visualProfile", profile, "-e", "consistencyPhase", phase,
                               "-e", "additionalTestOutputDir", directory + "/files/v210-evidence/" + profile,
                               entry.APP + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=600)
        except subprocess.TimeoutExpired as error:
            raw = error.stdout or b""
            log.write_text((raw.decode(errors="replace") if isinstance(raw, bytes) else raw) + "\nTIMEOUT: consistency UI failed\n")
            collect(profile)
            raise
        log.write_text(result)
        collect(profile)
        assert entry.successful_runner(result), f"Real consistency {profile}/{phase} failed; raw evidence retained"
        phases[phase] = hashlib.sha256(log.read_bytes()).hexdigest()
        if phase == "journey":
            # Preserve actual exported bytes before crossing the process boundary,
            # so a later reopen failure cannot discard successful export evidence.
            path = ROOT / profile
            first = json.loads((path / f"v210-consistency-{profile}-api{api}.json").read_text())
            name = first["backupFileName"]
            assert re.fullmatch(r"v210-consistency-" + str(api) + "-" + re.escape(profile) + r"-[0-9]+\.zip", name)
            raw = subprocess.run(["adb", "exec-out", "cat", "/sdcard/Download/" + name], capture_output=True, timeout=30, check=True).stdout
            assert 0 < len(raw) <= 64 * 1024 * 1024 and raw.startswith(b"PK")
            backup = path / "exported.zip"
            backup.write_bytes(raw)
            check.verify_zip(backup, first["ledger"], first["wholeBackupAtExport"])
    path = ROOT / profile
    first = json.loads((path / f"v210-consistency-{profile}-api{api}.json").read_text())
    second = json.loads((path / f"v210-consistency-reopen-{profile}-api{api}.json").read_text())
    backup = path / "exported.zip"
    verified = check.verify(first, second, api, profile, backup)
    for proof in (first, second):
        assert all((path / n).is_file() for n in proof["screenshots"])
    return dict(**verified, actualInstrumentations=2, runnerLogSha256=phases, result="PASS")


def main():
    adb = entry.adb
    assert adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Disposable emulator only"
    api = int(adb("shell", "getprop", "ro.build.version.sdk").strip())
    assert api in (29, 35)
    native_count = native_gate()
    directory = adb("shell", "run-as", entry.APP, "pwd").strip()
    assert re.fullmatch(r"/data/(?:user/0|data)/" + re.escape(entry.APP), directory)
    ROOT.mkdir(parents=True, exist_ok=True)
    LOGS.mkdir(parents=True, exist_ok=True)
    old_font = adb("shell", "settings", "get", "system", "font_scale").strip()
    old_night = adb("shell", "settings", "get", "secure", "ui_night_mode").strip()
    reports = []
    try:
        for profile in check.PROFILES:
            adb("shell", "am", "force-stop", entry.APP)
            adb("shell", "wm", "size", "640x1136" if profile.startswith("narrow") else "720x1280")
            adb("shell", "wm", "density", "320")
            adb("shell", "settings", "put", "system", "font_scale", "1.3" if profile.startswith("narrow") else "1.0")
            adb("shell", "su", "0", "cmd", "uimode", "night", "yes" if profile.endswith("dark") else "no")
            adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
            time.sleep(1)
            adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            adb("shell", "wm", "dismiss-keyguard")
            time.sleep(2)
            reports.append(run_profile(api, profile, directory))
    finally:
        adb("shell", "am", "force-stop", entry.APP)
        adb("shell", "wm", "size", "reset")
        adb("shell", "wm", "density", "reset")
        adb("shell", "settings", "put", "system", "font_scale", old_font if old_font != "null" else "1.0")
        adb("shell", "su", "0", "cmd", "uimode", "night", "yes" if old_night == "2" else "no")
        adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
        time.sleep(1)
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb("shell", "wm", "dismiss-keyguard")
    report = dict(api=api, nativeTests=native_count, actualInstrumentations=8, profiles=reports,
                  complete=True, realPhone="not-executed", result="PASS")
    (ROOT / f"v210-consistency-runner-api{api}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
