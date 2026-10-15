"""Read-only assembly and validation of the current run's candidate evidence."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
from ci_security import api, manifest, output, provenance, require, require_trusted, verify_manifest

def module(name, filename):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(filename))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

artifacts=module("artifacts", "ci-artifacts.py")
identity=module("identity", "v200-identity.py")
reports=module("reports", "create-v200-delivery-report.py")


def stage(include_signed=False):
    require_trusted(results={"build":os.environ["BUILD_RESULT"],"device-tests":os.environ["DEVICE_RESULT"]})
    attempt=os.environ["BUILD_ATTEMPT"]
    require(re.fullmatch(r"[1-9][0-9]*",attempt) is not None,"Invalid build attempt")
    artifacts.download("rc-apk-attempt"+attempt,"incoming/rc","rc",os.environ["RC_DIGEST"])
    artifacts.download("build-evidence-attempt"+attempt,"verified-reports/build","build-reports",os.environ["REPORTS_DIGEST"])
    dist=Path("dist");dist.mkdir(exist_ok=True)
    for name in ("TradeReview-v2.0-RC.apk","RC-IDENTITY.json"):
        artifacts.copy_file(Path("incoming/rc")/name,dist/name)
    run=provenance()
    listing=api(f"repos/{run['repository']}/actions/runs/{run['runId']}/artifacts?per_page=100")["artifacts"]
    for api_number in (29,35):
        prefix=f"device-evidence-api{api_number}-attempt"
        attempts=[int(a["name"][len(prefix):]) for a in listing if re.fullmatch(prefix+r"[1-9][0-9]*",a["name"])]
        require(bool(attempts),"Missing complete emulator evidence")
        latest=max(attempts)
        target=Path("verified-reports/devices")/f"android-test-reports-api{api_number}"
        artifacts.download(prefix+str(latest),target,f"device-{api_number}")
        shutil.copytree(target/"screenshots",Path("screenshots")/f"v200-emulator-screenshots-api{api_number}")
    status=dict(sourceCommit=run["sourceCommit"],configured=False,permanentPinVerified=False,legacyV03UpgradeCompatible=False,
                realPhoneAcceptance="not-confirmed",credentialsInspected=False,
                reason="No permanent identity used; credentials absent or protected Environment unavailable")
    if os.environ.get("SIGNING_RESULT")=="success":
        artifacts.download("signing-result-attempt"+os.environ["SIGNING_ATTEMPT"],"incoming/signed","signing-result",os.environ["SIGNED_DIGEST"])
        status=json.loads(Path("incoming/signed/dist/SIGNING-STATUS.json").read_text())
        require(status.get("configured") is include_signed,"Signing result/candidate state mismatch")
    if include_signed:
        require(os.environ["SIGNING_RESULT"]=="success","Signing job failed")
        for name in ("TradeReview-v2.0-fixed-candidate.apk","FIXED-IDENTITY.json"):
            artifacts.copy_file(Path("incoming/signed/dist")/name,dist/name)
        for api_number in (29,35):
            # Signed-install jobs run after the original read-only full matrix, without Secrets.
            prefix=f"signed-install-api{api_number}-attempt"
            values=[int(a["name"][len(prefix):]) for a in listing if re.fullmatch(prefix+r"[1-9][0-9]*",a["name"])]
            require(bool(values),"Missing permanent-identity install evidence")
            target=Path("signed-install")/str(api_number)
            artifacts.download(prefix+str(max(values)),target,f"signed-install-{api_number}")
            probe=json.loads((target/f"package-probes-api{api_number}.json").read_text())
            require(probe["fixedReleaseInstall"]=="passed-permanent-v2-pin-non-debuggable-release","Permanent identity was not actually installed")
            require(probe.get("legacyV03UpgradeCompatible") is False and probe.get("samePermanentSignerUpgrade")=="not-executed-no-earlier-permanent-apk","Upgrade compatibility claim invalid")
            require(probe["fixedActualApkSha"]==json.loads((dist/"FIXED-IDENTITY.json").read_text())["apkSha256"],"Installed fixed APK differs from signed candidate")
            destination=Path("verified-reports/devices")/f"android-test-reports-api{api_number}"/"metrics"/f"package-probes-api{api_number}.json"
            destination.write_text(json.dumps(probe,indent=2)+"\n")
            shutil.copy2(target/"56-v200-fixed-release-install.png",Path("screenshots")/f"v200-emulator-screenshots-api{api_number}"/"56-v200-fixed-release-install.png")
    (dist/"SIGNING-STATUS.json").write_text(json.dumps(status,indent=2)+"\n")
    tools=Path(os.environ["ANDROID_HOME"])/"build-tools/35.0.0"
    identity.verify_delivery(dist,tools/"aapt",tools/"apksigner",run["sourceCommit"])
    subprocess_args=["python3",str(Path(__file__).with_name("create-v200-delivery-report.py")),"--reports","verified-reports","--screenshots","screenshots","--dist","dist","--run-url",f"https://github.com/{run['repository']}/actions/runs/{run['runId']}"]
    import subprocess
    subprocess.run(subprocess_args,check=True)
    return dist


if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--signed",action="store_true");p.add_argument("--for-signing",action="store_true");a=p.parse_args()
    stage(a.signed)
    if a.for_signing:
        artifacts.download("signing-inputs-attempt"+os.environ["BUILD_ATTEMPT"],"verified-signing-inputs","signing-inputs",os.environ["INPUTS_DIGEST"])
