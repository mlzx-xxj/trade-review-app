"""Create one immutable prerelease per validated source SHA, never replace assets."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile
from ci_security import api, digest, provenance, require, require_trusted


def asset_names(directory):
    required={"TradeReview-v2.0-RC.apk","RC-IDENTITY.json","SIGNING-STATUS.json","SIGNING.txt","SHA256SUMS.txt","PUBLICATION-CHECK.json","CI-RESULTS.txt","PERFORMANCE-AND-OCR.json","CI-PROVENANCE.json","CI-EVIDENCE.zip","TradeReview-v2.0-emulator-screenshots.zip"}
    if (directory/"FIXED-IDENTITY.json").exists():required|={"FIXED-IDENTITY.json","TradeReview-v2.0-fixed-candidate.apk"}
    actual={p.name for p in directory.iterdir()}
    require(actual==required and all(p.is_file() and not p.is_symlink() for p in directory.iterdir()),"Unexpected publication files")
    return sorted(required)


def existing_release(release, tag, source, current_run, expected_names):
    require(release.get("target_commitish")==source and release.get("prerelease") is True and not release.get("draft"),"Existing release source/classification differs")
    assets=release.get("assets",[])
    require({a["name"] for a in assets}==set(expected_names),"Existing release incomplete; review manually, never overwrite")
    with tempfile.TemporaryDirectory() as temp:
        names=["RC-IDENTITY.json","CI-PROVENANCE.json"]
        if "FIXED-IDENTITY.json" in expected_names:names.append("FIXED-IDENTITY.json")
        for name in names:
            subprocess.run(["gh","release","download",tag,"--repo","mlzx-xxj/trade-review-app","--pattern",name,"--dir",temp],check=True)
            asset=next(a for a in assets if a["name"]==name)
            require(asset.get("digest")=="sha256:"+digest(Path(temp)/name),"Existing identity/provenance digest mismatch")
        proof=json.loads((Path(temp)/"CI-PROVENANCE.json").read_text())
        require(proof.get("sourceCommit")==source and proof.get("repository")=="mlzx-xxj/trade-review-app" and proof.get("stable") is False,"Existing provenance invalid")
        require(type(proof.get("runId")) is int,"Existing workflow run missing")
        run=api(f"repos/mlzx-xxj/trade-review-app/actions/runs/{proof['runId']}")
        require(run.get("head_sha")==source and run.get("head_branch")=="main" and run.get("event") in ("push","workflow_dispatch") and run.get("path")==".github/workflows/android.yml","Existing candidate came from an untrusted workflow")
        require(proof["runId"]==current_run or run.get("conclusion")=="success","Existing validation never completed successfully")
        for name,apk in [("RC-IDENTITY.json","TradeReview-v2.0-RC.apk"),("FIXED-IDENTITY.json","TradeReview-v2.0-fixed-candidate.apk")]:
            if name in names:
                value=json.loads((Path(temp)/name).read_text())
                require(value.get("sourceCommit")==source and value.get("debuggable") is False and value.get("stablePublished") is False,"Existing APK source/type mismatch")
                asset=next(a for a in assets if a["name"]==apk)
                require(asset.get("digest")=="sha256:"+value["apkSha256"],"Existing APK digest differs from validated identity")
    return release["html_url"]


def main():
    require_trusted(results={"build":os.environ["BUILD_RESULT"],"device-tests":os.environ["DEVICE_RESULT"]})
    p=provenance();directory=Path("dist")
    # Store each real screenshot once in Release, not again as dozens of assets.
    with zipfile.ZipFile(directory/"TradeReview-v2.0-emulator-screenshots.zip","w",zipfile.ZIP_DEFLATED) as z:
        for image in sorted(Path("screenshots").rglob("*.png")):
            require(not image.is_symlink(),"Unsafe screenshot")
            z.write(image,image.as_posix())
    with zipfile.ZipFile(directory/"CI-EVIDENCE.zip","w",zipfile.ZIP_DEFLATED) as z:
        for evidence in sorted(Path("verified-reports").rglob("*.xml")):
            require(not evidence.is_symlink(),"Unsafe XML evidence")
            z.write(evidence,evidence.as_posix())
        for schema in sorted(Path("verified-reports/build/schemas").rglob("*.json")):
            require(not schema.is_symlink(),"Unsafe migration schema")
            z.write(schema,schema.as_posix())
    evidence=dict(p,workflowRef=os.environ["GITHUB_WORKFLOW_REF"],build="success",api29="success",api35="success",stable=False,artifacts=json.loads(Path("ci-artifact-receipts.json").read_text()))
    (directory/"CI-PROVENANCE.json").write_text(json.dumps(evidence,indent=2)+"\n")
    names=asset_names(directory)
    hashes={name:digest(directory/name) for name in names}
    # Full source SHA prevents collisions and makes retries idempotent. Existing assets are never overwritten.
    channel="fixed" if (directory/"FIXED-IDENTITY.json").exists() else "rc"
    tag="v200-ci-"+channel+"-"+p["sourceCommit"]
    existing=subprocess.run(["gh","api",f"repos/{p['repository']}/releases/tags/{tag}"],capture_output=True,text=True)
    if existing.returncode==0:
        release=json.loads(existing.stdout)
        url=existing_release(release,tag,p["sourceCommit"],p["runId"],names)
        print("This source already has a validated candidate; kept unchanged: "+url)
        return
    require("404" in existing.stderr,"Cannot safely determine whether the release exists")
    notes=Path(os.environ["RUNNER_TEMP"])/"public-ci-release-notes.txt"
    notes.write_text("V2.0 RC 测试候选；独立包名、临时签名，不保证覆盖旧版或未来RC升级。若含新永久V2证书候选仍需真机验收，不能覆盖旧V0.3证书；先验证完整ZIP备份再由所有者决定重新安装恢复，不自动认定稳定版。仅可信main、完整209 JVM及API29/35各124测试成功后发布。签名和权限见SIGNING.txt、身份与SHA见RC-IDENTITY.json。ZIP/JSON为明文。\n\n"+(directory/"CI-RESULTS.txt").read_text())
    require(all(digest(directory/name)==sha for name,sha in hashes.items()),"Candidate changed immediately before publication")
    subprocess.run(["gh","release","create",tag,*[str(directory/name) for name in names],"--repo",p["repository"],"--target",p["sourceCommit"],"--title","交易复盘 V2.0 · 已验证main RC","--prerelease","--notes-file",str(notes)],check=True)
    release=api(f"repos/{p['repository']}/releases/tags/{tag}")
    actual={a["name"]:a.get("digest") for a in release["assets"]}
    require(actual=={name:"sha256:"+sha for name,sha in hashes.items()},"Uploaded release asset digest mismatch; do not use this candidate")
    print(release["html_url"])
    with open(os.environ["GITHUB_STEP_SUMMARY"],"a") as out:out.write("[已验证main候选包]("+release["html_url"]+")\n")


if __name__=="__main__":main()
