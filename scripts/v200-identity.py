"""V2.0 actual APK identity gate. Never reads a private key/password."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile
from signing_identity import PIN, LEGACY_V03_PIN, require_committed_identity, upgrade_policy

ROOT=Path(__file__).resolve().parents[1]


def sha(path):
    with Path(path).open("rb") as f:return hashlib.file_digest(f,"sha256").hexdigest()


def inspect(apk,aapt,signer,require_current_payload=True):
    apk=Path(apk)
    if not apk.is_file() or apk.is_symlink():raise ValueError("Regular APK required")
    badging=subprocess.run([str(aapt),"dump","badging",str(apk)],check=True,capture_output=True,text=True).stdout
    match=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging,re.MULTILINE)
    if not match:raise ValueError("Actual APK identity unavailable")
    cert_output=subprocess.run([str(signer),"verify","--print-certs",str(apk)],check=True,capture_output=True,text=True).stdout
    certs=re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$",cert_output,re.MULTILINE)
    if len(certs)!=1:raise ValueError("Exactly one verified signer required")
    permissions=subprocess.run([str(aapt),"dump","permissions",str(apk)],check=True,capture_output=True,text=True).stdout
    if any("android.permission."+p in permissions for p in ["INTERNET","ACCESS_NETWORK_STATE","READ_EXTERNAL_STORAGE","WRITE_EXTERNAL_STORAGE","READ_MEDIA_","MANAGE_EXTERNAL_STORAGE"]):raise ValueError("Offline/SAF permission policy violated")
    with zipfile.ZipFile(apk) as z:
        names=z.namelist()
        if len(names)!=len(set(names)):raise ValueError("Duplicate APK entries")
        abis=sorted({n.split('/')[1] for n in names if n.startswith("lib/") and n.endswith(".so")})
        if require_current_payload:
            for language in ["Hani_ctc","Latn_ctc"]:
                for model in ["conv_model.fb","lstm_model.fb"]:
                    if not any(language in n and n.endswith(model) for n in names):raise ValueError("Bundled offline OCR model missing")
            if abis!=["arm64-v8a","armeabi-v7a","x86","x86_64"]:raise ValueError("Existing ABI support changed")
    return dict(applicationId=match[1],versionCode=int(match[2]),versionName=match[3],debuggable="application-debuggable" in badging,
                certificateSha256=certs[0].lower(),apkSha256=sha(apk),apkBytes=apk.stat().st_size,abis=abis)


def require_identity(data,kind):
    if kind not in ("rc","release"):raise ValueError("Known build type required")
    require_committed_identity()
    package="com.tradereview.app.rc.v200" if kind=="rc" else "com.tradereview.app"
    version="2.0.0-rc.3" if kind=="rc" else "2.0.0"
    if data["applicationId"]!=package or data["versionName"]!=version or data["versionCode"]!=22 or data["debuggable"]:raise ValueError("Package/version/non-debuggable build identity mismatch")
    if kind=="release" and data["certificateSha256"]!=PIN:raise ValueError("Formal Release must use the permanent V2 private identity")
    if kind=="rc" and data["certificateSha256"] in (PIN,LEGACY_V03_PIN):raise ValueError("Isolated RC must not consume either permanent or historical formal identity")


def identity_metadata(kind):
    return dict(buildType=kind,classification="isolated-rc" if kind=="rc" else "permanent-v2-release-candidate",
                permanentIdentityVerified=kind=="release",legacyV03UpgradeCompatible=False,
                sameSignerUpgrade="not-executed",stablePublished=False,phoneAcceptance="not-confirmed")


def prepare(apk,directory,kind,aapt,signer,source):
    if not re.fullmatch(r"[0-9a-f]{40}",source):raise ValueError("Verified source SHA required")
    data=inspect(apk,aapt,signer);require_identity(data,kind)
    out=Path(directory);out.mkdir(parents=True,exist_ok=True)
    data.update(sourceCommit=source,**identity_metadata(kind))
    target=out/("RC-IDENTITY.json" if kind=="rc" else "FIXED-IDENTITY.json")
    target.write_text(json.dumps(data,indent=2)+"\n")
    print(json.dumps(data));return data


def verify_delivery(directory,aapt,signer,source):
    root=Path(directory);expected={"TradeReview-v2.0-RC.apk":"rc"}
    if (root/"FIXED-IDENTITY.json").exists():expected["TradeReview-v2.0-fixed-candidate.apk"]="release"
    status=json.loads((root/"SIGNING-STATUS.json").read_text())
    if type(status.get("configured")) is not bool or status.get("permanentPinVerified") is not status["configured"] or status.get("legacyV03UpgradeCompatible") is not False or status.get("realPhoneAcceptance")!="not-confirmed" or status.get("originalPinVerified",False) is not False:raise ValueError("Signing/phone status must reflect this observed candidate build")
    if status.get("sourceCommit")!=source or status.get("configured")!=('release' in expected.values()):raise ValueError("Observed fixed signing configuration does not match delivered identities")
    if {p.name for p in root.glob("*.apk")}!=set(expected):raise ValueError("Unexpected APK or incomplete identity pair")
    hashes=[];checked={}
    for filename,kind in expected.items():
        data=inspect(root/filename,aapt,signer);require_identity(data,kind)
        saved=json.loads((root/("RC-IDENTITY.json" if kind=="rc" else "FIXED-IDENTITY.json")).read_text())
        if any(saved.get(k)!=v for k,v in data.items()) or saved.get("sourceCommit")!=source or saved.get("stablePublished") is not False or saved.get("phoneAcceptance")!="not-confirmed":raise ValueError("Actual APK/metadata/source/RC status mismatch")
        if any(saved.get(k)!=v for k,v in identity_metadata(kind).items()) or saved.get("upgradeIdentityVerified",False) is not False:raise ValueError("Build or upgrade compatibility misclassified")
        hashes.append(f'{data["apkSha256"]}  {filename}');checked[filename]=saved
    (root/"SHA256SUMS.txt").write_text("\n".join(hashes)+"\n")
    (root/"PUBLICATION-CHECK.json").write_text(json.dumps(checked,indent=2)+"\n")
    rc=checked["TradeReview-v2.0-RC.apk"]
    text=f'versionName={rc["versionName"]}\nversionCode=22\napplicationId={rc["applicationId"]}\nbuildType=rc\ndebuggable=false\nsourceCommit={source}\ncertificateSha256={rc["certificateSha256"]}\napkSha256={rc["apkSha256"]}\npermanentV2CertificatePin={PIN}\nhistoricalV03CertificatePin={LEGACY_V03_PIN}\nlegacyV03UpgradeCompatible=false\nsamePermanentSignerUpgrade=not-executed\nclassification=isolated-rc\nGitHubActionsSecretsConfigured={str(status["configured"]).lower()}\nThis RC uses a temporary runner test key; no sustainable RC-to-RC upgrades are promised. Old V0.3 and new permanent V2 certificates are incompatible: verify a full ZIP backup, then owner-approved reinstall and restore. Never uninstall/clear data automatically. Future permanent updates must retain com.tradereview.app and the V2 certificate, and increase versionCode. Stable publication and real phone acceptance are not claimed.\n'
    if "TradeReview-v2.0-fixed-candidate.apk" in checked:
        fixed=checked["TradeReview-v2.0-fixed-candidate.apk"]
        text+=f'\nPermanentCandidateApplicationId={fixed["applicationId"]}\nPermanentCandidateVersionName={fixed["versionName"]}\nPermanentCandidateVersionCode={fixed["versionCode"]}\nPermanentCandidateCertificateSha256={fixed["certificateSha256"]}\nPermanentCandidateApkSha256={fixed["apkSha256"]}\nPermanentCandidateDebuggable=false\nPermanentCandidateClassification=permanent-v2-release-candidate\nPermanentPinVerified=true\n'
    (root/"SIGNING.txt").write_text(text);print(json.dumps(checked));return checked


if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--apk");p.add_argument("--kind",choices=["rc","release"]);p.add_argument("--directory",required=True);p.add_argument("--aapt",required=True);p.add_argument("--apksigner",required=True);p.add_argument("--source",required=True);p.add_argument("--verify",action="store_true");a=p.parse_args()
    if a.verify:verify_delivery(a.directory,a.aapt,a.apksigner,a.source)
    else:prepare(a.apk,a.directory,a.kind,a.aapt,a.apksigner,a.source)
