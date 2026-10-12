"""Actual process-stop/restart checks, only on a disposable Android emulator."""
import hashlib
import json
from pathlib import Path
import re
import shlex
import sqlite3
import subprocess
import time

APP = "com.tradereview.app"
KEY = "00000000000000000000000000000808"
ROOT = Path("ci-stability")


def adb(*args, check=True, binary=False):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=not binary)


def snapshot(name):
    folder = ROOT / name;folder.mkdir(parents=True, exist_ok=True)
    for suffix in ["", "-wal", "-shm"]:
        result = adb("exec-out", "run-as", APP, "cat", "databases/trade-review.db" + suffix, check=False, binary=True)
        if suffix == "":
            assert result.returncode == 0 and result.stdout.startswith(b"SQLite format 3\x00"), "Private database unavailable or invalid; refusing to treat adb diagnostics as a snapshot"
        if result.returncode == 0 and result.stdout and not result.stdout.startswith(b"run-as:"):
            (folder / ("trade-review.db" + suffix)).write_bytes(result.stdout)
    with sqlite3.connect(folder / "trade-review.db") as db:
        assert db.execute("PRAGMA user_version").fetchone()[0] == 7
        assert not db.execute("PRAGMA foreign_key_check").fetchall()
        tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'") if r[0] not in ["android_metadata", "sqlite_sequence", "room_master_table"]]
        rows = {table: sorted(db.execute('SELECT * FROM "'+table+'"').fetchall(), key=repr) for table in tables}
        trade = db.execute("SELECT netPnl,professional,riskRecord FROM trades WHERE recordKey=?", (KEY,)).fetchone()
        assert trade is not None and trade[0] == "123.45"
        assert json.loads(trade[1])["manualRisk"] == "200.00"
        assert trade[2] is not None
        attachments = db.execute("SELECT id,sha256,byteCount FROM attachments WHERE recordKey=?", (KEY,)).fetchall()
        assert len(attachments) == 1
        for image_id, digest, size in attachments:
            data = adb("exec-out", "run-as", APP, "cat", f"files/trade-evidence/{image_id}-{digest}.jpg", binary=True).stdout
            assert len(data) == size and hashlib.sha256(data).hexdigest() == digest
        assert db.execute("SELECT count(*) FROM ocr_drafts WHERE id='00000000000000000000000000000811'").fetchone()[0] == 1
        assert db.execute("SELECT count(*) FROM review_goals WHERE id='00000000000000000000000000000809'").fetchone()[0] == 1
    return rows


def start():
    result = adb("shell", "am", "start", "-W", "-n", APP + "/.MainActivity").stdout
    assert "Error" not in result
    timings = {k: int(v) for k, v in re.findall(r"^(ThisTime|TotalTime|WaitTime): (\d+)$", result, re.MULTILINE)}
    return result, timings


def main():
    ROOT.mkdir(exist_ok=True)
    assert adb("shell", "getprop", "ro.kernel.qemu").stdout.strip() == "1", "Never run this probe on a real phone"
    api = int(adb("shell", "getprop", "ro.build.version.sdk").stdout.strip())
    # Gradle UTP uninstalls both packages at the end of connectedDebugAndroidTest.
    # Manually install the SAME locally compiled test APKs and run an existing real
    # Activity regression to seed committed synthetic facts. Manual instrumentation
    # leaves the packages installed for process-stop probes; this is not an upgrade.
    for apk in ["app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
        assert Path(apk).is_file(), apk
        result = adb("install", "-r", apk)
        assert "Success" in result.stdout, "Disposable emulator fixture install failed"
    seed = adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
               "com.tradereview.app.stability.ActivityCompatibilityTest#actualActivityDraftSurvivesRecreationAndBackgroundResume",
               APP + ".test/androidx.test.runner.AndroidJUnitRunner")
    (ROOT / "fixture-instrumentation.txt").write_text(seed.stdout + seed.stderr)
    assert "OK (1 test)" in seed.stdout and "FAILURES" not in seed.stdout, "Actual Activity fixture regression failed"
    adb("shell", "am", "force-stop", APP)
    before = snapshot("before")
    # Simulate interrupted private-file staging. No DB row points to either synthetic orphan.
    command = "mkdir -p files/trade-evidence/.work; printf synthetic-orphan > files/trade-evidence/v08-orphan.jpg; printf interrupted-staging > files/trade-evidence/.work/v08-interrupted.tmp"
    adb("shell", "run-as " + APP + " sh -c " + shlex.quote(command))
    cold = [];warm = [];logs = []
    for index in range(3):
        adb("shell", "am", "force-stop", APP)
        log, timing = start();logs.append(log);cold.append(timing)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            result = adb("shell", "run-as " + APP + " sh -c " + shlex.quote("test ! -e files/trade-evidence/v08-orphan.jpg && test ! -e files/trade-evidence/.work/v08-interrupted.tmp"), check=False)
            if result.returncode == 0:break
            time.sleep(.2)
        else:raise AssertionError("Startup orphan cleanup did not complete")
        log, timing = start();logs.append(log);warm.append(timing)
        adb("shell", "am", "force-stop", APP)
        assert before == snapshot("after-" + str(index)), "Committed facts changed after process termination"
    adb("shell", "settings", "put", "system", "font_scale", "1.3")
    try:
        start();adb("shell", "uiautomator", "dump", "/sdcard/v08-stability-window.xml")
        xml = adb("exec-out", "cat", "/sdcard/v08-stability-window.xml").stdout
        (ROOT / "actual-main-window.xml").write_text(xml)
        assert "isn't responding" not in xml and "not responding" not in xml
        assert "首页" in xml
        image = adb("exec-out", "screencap", "-p", binary=True).stdout
        assert image.startswith(b"\x89PNG")
        (ROOT / "26-v08-process-restart-font.png").write_bytes(image)
    finally:adb("shell", "settings", "put", "system", "font_scale", "1.0")
    (ROOT / "launch-times.txt").write_text("\n".join(logs))
    report = {"api": api, "processRestarts": 3, "allSixteenTablesUnchanged": len(before) == 16, "attachmentShaVerified": True,
              "coldProcessLaunch": cold, "warmProcessLaunch": warm,
              "definition": "am start -W system launch times; OS/page caches warmed by prior tests; warm start omits force-stop and may reuse/create Activity; not phone/end-to-end data-ready latency. After Gradle UTP cleanup, the same locally compiled test APKs are manually installed and one existing Activity test seeds synthetic facts; this setup is not a fixed-signer old-to-new upgrade."}
    assert report["allSixteenTablesUnchanged"]
    (ROOT / f"process-probes-api{api}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    try:main()
    finally:
        ROOT.mkdir(exist_ok=True)
        result = adb("logcat", "-d", "-s", "ActivityManager:I", "ActivityTaskManager:I", "AndroidRuntime:E", check=False)
        (ROOT / "system-lifecycle-logcat.txt").write_text(result.stdout)
