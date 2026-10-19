"""Actual delivered non-debuggable RC/fixed APK install checks; disposable emulator only."""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import time
import sys
from xml.etree import ElementTree as ET

ROOT=Path("ci-v200");ROOT.mkdir(exist_ok=True)
spec=importlib.util.spec_from_file_location("identity",Path(__file__).with_name("v200-identity.py"));identity=importlib.util.module_from_spec(spec);spec.loader.exec_module(identity)


def adb(*args,check=True,binary=False,timeout=45):return subprocess.run(["adb",*args],check=check,capture_output=True,text=not binary,timeout=timeout)


def window(package,needle,label):
    deadline=time.monotonic()+60;attempts=[]
    try:
        while time.monotonic()<deadline:
            adb("shell","rm","-f","/sdcard/v200-window.xml")
            result=adb("shell","uiautomator","dump","/sdcard/v200-window.xml",check=False);attempts.append(result.stdout+result.stderr)
            text=adb("exec-out","cat","/sdcard/v200-window.xml",check=False).stdout
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
    p=argparse.ArgumentParser();p.add_argument("--directory",required=True);p.add_argument("--aapt",required=True);p.add_argument("--apksigner",required=True);p.add_argument("--old-apk");p.add_argument("--stable-apk");a=p.parse_args()
    assert adb("shell","getprop","ro.kernel.qemu").stdout.strip()=="1","Never run on a personal phone"
    api=int(adb("shell","getprop","ro.build.version.sdk").stdout.strip());folder=Path(a.directory)
    rc=folder/"TradeReview-v2.0-RC.apk";data=identity.inspect(rc,a.aapt,a.apksigner);identity.require_identity(data,"rc")
    assert "Success" in adb("install","-r",str(rc)).stdout
    package="com.tradereview.app.rc.v200";start(package)
    window(package,"累计表现","rc-first-start");capture("50-v200-rc-first-start")
    adb("shell","am","force-stop",package);start(package);window(package,"累计表现","rc-restart");capture("51-v200-rc-home-restart")
    report=dict(api=api,rcActualApkSha=data["apkSha256"],rcPackage=package,rcNonDebuggable=True,rcInstalled=True,rcFirstHomeVisible=True,rcRestartKeptHome=True,
        fixedReleaseInstall="not-executed-no-permanent-key",legacyV03UpgradeCompatible=False,legacyV03IncompatibleInstall="not-executed-no-permanent-key",samePermanentSignerUpgrade="not-executed-no-earlier-permanent-apk",realPhone="not-executed")
    fixed=folder/"TradeReview-v2.0-fixed-candidate.apk"
    if fixed.exists():
        new=identity.inspect(fixed,a.aapt,a.apksigner);identity.require_identity(new,"release")
        report["fixedActualApkSha"]=new["apkSha256"]
        report["legacyV03IncompatibleInstall"]="not-executed-no-old-v03-apk-supplied"
        # Prior regression data are synthetic. This cleanup is forbidden outside qemu.
        adb("uninstall","com.tradereview.app",check=False)
        adb("uninstall","com.tradereview.app.test",check=False)
        if a.old_apk:
            old=identity.inspect(a.old_apk,a.aapt,a.apksigner,require_current_payload=False)
            assert old["applicationId"]=="com.tradereview.app" and old["certificateSha256"]==identity.LEGACY_V03_PIN and 3<=old["versionCode"]<20 and old["debuggable"],"Real historical V0.3 debuggable APK required for synthetic private-data preservation probe"
            assert identity.upgrade_policy(old,new)=="incompatible-certificate-backup-reinstall-restore-required"
            assert "Success" in adb("install",a.old_apk).stdout
            # No new-signed instrumentation can access the old identity. Use only run-as
            # on the genuine historical debuggable APK, on a disposable emulator.
            adb("shell","run-as","com.tradereview.app","mkdir","-p","files")
            adb("shell","run-as","com.tradereview.app","touch","files/v2-migration-synthetic-marker")
            before=adb("shell","pm","path","com.tradereview.app").stdout
            result=adb("install","-r",str(fixed),check=False)
            assert result.returncode!=0 and "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in result.stdout+result.stderr,"Different certificate must be rejected by Android"
            assert adb("shell","pm","path","com.tradereview.app").stdout==before,"Rejected update must keep the old installation"
            adb("shell","run-as","com.tradereview.app","test","-f","files/v2-migration-synthetic-marker")
            report["legacyV03IncompatibleInstall"]="passed-android-rejected-different-certificate-old-install-and-marker-preserved"
            report["legacyV03ActualApkSha"]=old["apkSha256"]
            # Only after proving rejection and preservation, discard the synthetic emulator
            # fixture for a separate FRESH install. This is never a phone migration script.
            assert "Success" in adb("uninstall","com.tradereview.app").stdout
        assert "Success" in adb("install",str(fixed)).stdout
        start("com.tradereview.app");window("com.tradereview.app","累计表现","permanent-v2-fresh-home")
        capture("56-v200-fixed-release-install");report["fixedReleaseInstall"]="passed-permanent-v2-pin-non-debuggable-release"
        if a.stable_apk:
            import stable_upgrade
            stable_upgrade.run(sys.modules[__name__],a.stable_apk,fixed,a.aapt,a.apksigner)
            report["samePermanentSignerUpgrade"]=stable_upgrade.PASS
    (ROOT/f"package-probes-api{api}.json").write_text(json.dumps(report,indent=2)+"\n");print(json.dumps(report))


if __name__=="__main__":
    try:main()
    finally:
        result=adb("logcat","-d","-s","ActivityManager:I","ActivityTaskManager:I","AndroidRuntime:E",check=False)
        (ROOT/"package-lifecycle-logcat.txt").write_text(result.stdout)
