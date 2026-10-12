"""Actual delivered non-debuggable RC/fixed APK install checks; disposable emulator only."""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import time
from xml.etree import ElementTree as ET

ROOT=Path("ci-v100");ROOT.mkdir(exist_ok=True)
spec=importlib.util.spec_from_file_location("identity",Path(__file__).with_name("v100-identity.py"));identity=importlib.util.module_from_spec(spec);spec.loader.exec_module(identity)


def adb(*args,check=True,binary=False,timeout=45):return subprocess.run(["adb",*args],check=check,capture_output=True,text=not binary,timeout=timeout)


def window(package,needle,label):
    deadline=time.monotonic()+60;attempts=[]
    try:
        while time.monotonic()<deadline:
            adb("shell","rm","-f","/sdcard/v100-window.xml")
            result=adb("shell","uiautomator","dump","/sdcard/v100-window.xml",check=False);attempts.append(result.stdout+result.stderr)
            text=adb("exec-out","cat","/sdcard/v100-window.xml",check=False).stdout
            try:root=ET.fromstring(text)
            except ET.ParseError:attempts.append("Invalid hierarchy: "+text);time.sleep(.3);continue
            (ROOT/(label+"-window.xml")).write_text(text)
            assert "isn't responding" not in text and "not responding" not in text,"ANR must not be dismissed"
            nodes=[n for n in root.iter("node") if n.get("package")==package and needle in n.get("text","")]
            if nodes:return nodes[0]
            time.sleep(.3)
        raise AssertionError("Expected real package UI missing: "+label)
    finally:
        (ROOT/(label+"-attempts.txt")).write_text("\n".join(attempts))


def capture(name):
    value=adb("exec-out","screencap","-p",binary=True).stdout;assert value.startswith(b"\x89PNG");(ROOT/(name+".png")).write_bytes(value)


def tap(node):
    numbers=[int(n) for n in re.findall(r"\d+",node.get("bounds", ""))];assert len(numbers)==4
    adb("shell","input","tap",str((numbers[0]+numbers[2])//2),str((numbers[1]+numbers[3])//2))


def start(package):
    output=adb("shell","am","start","-W","-n",package+"/com.tradereview.app.MainActivity").stdout
    assert "Error" not in output


def main():
    p=argparse.ArgumentParser();p.add_argument("--directory",required=True);p.add_argument("--aapt",required=True);p.add_argument("--apksigner",required=True);p.add_argument("--old-apk");p.add_argument("--upgrade-fixture");a=p.parse_args()
    assert adb("shell","getprop","ro.kernel.qemu").stdout.strip()=="1","Never run on a personal phone"
    api=int(adb("shell","getprop","ro.build.version.sdk").stdout.strip());folder=Path(a.directory)
    rc=folder/"TradeReview-v1.0-RC.apk";data=identity.inspect(rc,a.aapt,a.apksigner);identity.require_identity(data,"rc")
    assert "Success" in adb("install","-r",str(rc)).stdout
    package="com.tradereview.app.rc";start(package)
    node=window(package,"跳过，稍后在设置查看","rc-first-start");capture("33-v100-rc-first-start");tap(node)
    window(package,"快捷记账","rc-home")
    adb("shell","am","force-stop",package);start(package);window(package,"快捷记账","rc-restart");capture("34-v100-rc-home-restart")
    report=dict(api=api,rcActualApkSha=data["apkSha256"],rcPackage=package,rcNonDebuggable=True,rcInstalled=True,rcFirstGuideSkipped=True,rcRestartKeptGuideCompletion=True,
        fixedReleaseInstall="not-executed-no-fixed-key",sameOriginalSignerUpgrade="not-executed-no-old-fixed-apk",realPhone="not-executed")
    fixed=folder/"TradeReview-v1.0-fixed-candidate.apk"
    if fixed.exists():
        new=identity.inspect(fixed,a.aapt,a.apksigner);identity.require_identity(new,"release")
        # Prior Main regression data are all synthetic, and already reported. Discard ONLY here on qemu.
        adb("uninstall","com.tradereview.app",check=False)
        if a.old_apk:
            old=identity.inspect(a.old_apk,a.aapt,a.apksigner)
            assert old["applicationId"]=="com.tradereview.app" and old["certificateSha256"]==identity.PIN and 3<=old["versionCode"]<10,"Real old original-signer APK required"
            assert a.upgrade_fixture,"Same fixed-signed synthetic instrumentation required"
            assert "Success" in adb("install",a.old_apk).stdout
            assert "Success" in adb("install","-r",a.upgrade_fixture).stdout
            start("com.tradereview.app");window("com.tradereview.app","快捷记账","old-fixed-ready")
            def fixture(phase):
                result=adb("shell","am","instrument","-w","-e","phase",phase,"com.tradereview.app.test/com.tradereview.app.release.LegacyUpgradeInstrumentation",timeout=120)
                (ROOT/("old-fixed-"+phase+".txt")).write_text(result.stdout+result.stderr)
                assert "V100_UPGRADE_FIXTURE_OK="+phase in result.stdout
            fixture("prepare");adb("shell","am","force-stop","com.tradereview.app")
            # Actual Android install -r over the REAL original fixed identity. No uninstall/clear in between.
            assert "Success" in adb("install","-r",str(fixed)).stdout
            start("com.tradereview.app");window("com.tradereview.app","快捷记账","fixed-upgraded-home");fixture("verify")
            report["sameOriginalSignerUpgrade"]="passed-real-original-apk-to-release-with-synthetic-trade-and-diary"
        else:
            assert "Success" in adb("install",str(fixed)).stdout
            start("com.tradereview.app");tap(window("com.tradereview.app","跳过，稍后在设置查看","fixed-first-start"));window("com.tradereview.app","快捷记账","fixed-home")
        capture("35-v100-fixed-release-install");report["fixedReleaseInstall"]="passed-original-pin-non-debuggable-release"
    (ROOT/f"package-probes-api{api}.json").write_text(json.dumps(report,indent=2)+"\n");print(json.dumps(report))


if __name__=="__main__":
    try:main()
    finally:
        result=adb("logcat","-d","-s","ActivityManager:I","ActivityTaskManager:I","AndroidRuntime:E",check=False)
        (ROOT/"package-lifecycle-logcat.txt").write_text(result.stdout)
