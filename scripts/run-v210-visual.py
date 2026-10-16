"""Repeat the SAME production MainActivity journey in four real AVD configurations.

No phone use, database execution inserts, synthetic screenshots or signature access.
Profile changes happen before launching instrumentation; native/full regression remains intact.
"""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import time

spec = importlib.util.spec_from_file_location("entry_runner", Path(__file__).with_name("run-entry-experience.py"))
entry = importlib.util.module_from_spec(spec)
spec.loader.exec_module(entry)
APP = entry.APP
ROOT = Path("ci-v200/v210-visual")
PROFILES = ("regular-light", "regular-dark", "narrow-light", "narrow-dark")
SCENES = ("empty", "stock-filled", "stock-position", "futures-filled", "keyboard", "futures-position", "saved-list", "partial-position", "pending-filled", "pending-position", "statistics")


def collect(api, profile):
    names = tuple(f"v210-{profile}-{scene}.png" for scene in SCENES + ("failure",)) + (f"v210-visual-{profile}-api{api}.json", f"v210-{profile}-failure.txt")
    for name in names:
        result = subprocess.run(["adb", "shell", "-T", "run-as", APP, "cat", f"files/v210-evidence/{profile}/v210-visual/{name}"], capture_output=True, timeout=30, check=False)
        if result.returncode:
            if b"No such file or directory" in result.stderr + result.stdout:
                continue
            raise RuntimeError("Cannot read private synthetic visual evidence")
        assert len(result.stdout) <= 8 * 1024 * 1024, "Evidence exceeds file bound"
        if name.endswith(".png"):
            assert result.stdout.startswith(b"\x89PNG\r\n\x1a\n"), "Not an actual PNG"
        target = ROOT / profile / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(result.stdout)


def main():
    adb = entry.adb
    assert adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Never run visual fixtures on a phone"
    api = int(adb("shell", "getprop", "ro.build.version.sdk").strip())
    assert api in (29, 35)
    assert adb("shell", "su", "0", "id", "-u").strip() == "0", "AVD profile changes need the existing root-capable test image"
    ROOT.mkdir(parents=True, exist_ok=True)
    directory = adb("shell", "run-as", APP, "pwd").strip()
    import re
    assert re.fullmatch(r"/data/(?:user/0|data)/" + re.escape(APP), directory)
    old_font = adb("shell", "settings", "get", "system", "font_scale").strip()
    old_night = adb("shell", "settings", "get", "secure", "ui_night_mode").strip()
    profiles = []
    try:
        for profile in PROFILES:
            adb("shell", "am", "force-stop", APP)
            adb("shell", "wm", "size", "640x1136" if profile.startswith("narrow") else "720x1280")
            adb("shell", "wm", "density", "320")
            adb("shell", "settings", "put", "system", "font_scale", "1.3" if profile.startswith("narrow") else "1.0")
            adb("shell", "su", "0", "cmd", "uimode", "night", "yes" if profile.endswith("dark") else "no")
            # API29 applies night changes on display-off. No Activity exists yet.
            adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
            time.sleep(1)
            adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            adb("shell", "wm", "dismiss-keyguard")
            time.sleep(2)
            log = ROOT / f"{profile}-instrumentation.txt"
            try:
                result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", APP + ".v210.V210VisualJourneyTest", "-e", "visualProfile", profile, "-e", "additionalTestOutputDir", directory + "/files/v210-evidence/" + profile, APP + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=600)
            except subprocess.TimeoutExpired as error:
                raw = error.stdout or b""
                log.write_text((raw.decode(errors="replace") if isinstance(raw, bytes) else raw) + "\nTIMEOUT: visual instrumentation failed\n")
                raise
            log.write_text(result)
            assert entry.successful_runner(result), f"Actual visual journey failed: {profile}; logs retained"
            collect(api, profile)
            report = json.loads((ROOT / profile / f"v210-visual-{profile}-api{api}.json").read_text())
            assert report["result"] == "PASS" and report["allExecutionsCreatedViaRealForm"] is True
            assert len(report["observations"]) == 5 and {s["scene"] for s in report["screenshots"]} == set(SCENES)
            for scene in SCENES:
                assert (ROOT / profile / f"v210-{profile}-{scene}.png").is_file()
            profiles.append(dict(profile=profile, runnerLogSha256=hashlib.sha256(log.read_bytes()).hexdigest(), result="PASS"))
    finally:
        # Collect diagnostics without changing a failed result, then restore AVD.
        for profile in PROFILES:
            collect(api, profile)
        adb("shell", "am", "force-stop", APP)
        adb("shell", "wm", "size", "reset")
        adb("shell", "wm", "density", "reset")
        adb("shell", "settings", "put", "system", "font_scale", old_font if old_font != "null" else "1.0")
        adb("shell", "su", "0", "cmd", "uimode", "night", "yes" if old_night == "2" else "no")
        adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
        time.sleep(1)
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb("shell", "wm", "dismiss-keyguard")
    report = dict(api=api, profiles=profiles, actualInstrumentations=4, complete=True, result="PASS", realPhone="not-executed")
    (ROOT / f"v210-runner-api{api}.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
