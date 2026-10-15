"""Owner-held permanent V2 key operation. Historical filename; never uses the lost V0.3 key."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from ci_security import PIN, digest, output, require, require_trusted, verify_manifest
from signing_identity import ALIAS, require_committed_identity


def load(name, filename):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(filename))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value


def run_private(arguments):
    # Never echo private command arguments or keystore exception messages.
    result=subprocess.run(arguments,capture_output=True)
    require(result.returncode==0,"Permanent-key signing failed; private diagnostics suppressed")
    return result.stdout.decode()


def main():
    require_trusted()
    require(os.environ.get("VERIFIED_INPUTS")=="verified-signing-inputs","Only staged current-run signing inputs allowed")
    verify_manifest("verified-signing-inputs","signing-inputs",os.environ["INPUTS_DIGEST"])
    state=load("signing_state","signing-config-state.py").signing_state(os.environ)
    dist=Path("dist");dist.mkdir(exist_ok=True)
    if state=="true":
        require_committed_identity()
        require(os.environ.get("TRADE_REVIEW_SIGNING_CERT_SHA256")==PIN,"Permanent public certificate pin absent/mismatched")
        require(os.environ.get("EXISTING_KEY_ALIAS")==ALIAS,"Permanent key alias mismatched")
        signer=str(Path(os.environ["ANDROID_HOME"])/"build-tools/35.0.0/apksigner")
        private=None
        try:
            with tempfile.NamedTemporaryFile(dir=os.environ["RUNNER_TEMP"],suffix=".keystore",delete=False) as f:
                private=Path(f.name)
                private.chmod(0o600)
                try:f.write(base64.b64decode(os.environ["EXISTING_KEY_BASE64"],validate=True))
                except Exception:raise ValueError("Permanent keystore encoding invalid") from None
            for source,target in [("app-release-unsigned.apk",dist/"TradeReview-v2.0-fixed-candidate.apk")]:
                run_private([signer,"sign","--ks",str(private),"--ks-type","PKCS12","--ks-key-alias",os.environ["EXISTING_KEY_ALIAS"],"--ks-pass","env:EXISTING_STORE_PASSWORD","--key-pass","env:EXISTING_KEY_PASSWORD","--v4-signing-enabled","false","--out",str(target),str(Path("verified-signing-inputs")/source)])
                verified=run_private([signer,"verify","--print-certs",str(target)])
                certs=re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$",verified,re.M)
                require(certs==[PIN],"Permanent V2 certificate mismatch; candidate refused")
                load("payload","adopt-signed-apk.py").require_identical_payload((Path("verified-signing-inputs")/source).read_bytes(),target.read_bytes())
        finally:
            if private is not None:private.unlink(missing_ok=True)
        verified=load("identity","v200-identity.py").prepare(dist/"TradeReview-v2.0-fixed-candidate.apk",dist,"release",Path(os.environ["ANDROID_HOME"])/"build-tools/35.0.0/aapt",signer,os.environ["SOURCE_COMMIT"])
        verified.update(verifiedUnsignedSha256=digest("verified-signing-inputs/app-release-unsigned.apk"),verifiedInputsManifestSha256=os.environ["INPUTS_DIGEST"],applicationPayloadCompared=True)
        (dist/"FIXED-IDENTITY.json").write_text(json.dumps(verified,indent=2)+"\n")
    (dist/"SIGNING-STATUS.json").write_text(json.dumps(dict(sourceCommit=os.environ["SOURCE_COMMIT"],configured=state=="true",permanentPinVerified=state=="true",legacyV03UpgradeCompatible=False,credentialsInspected=True,realPhoneAcceptance="not-confirmed"),indent=2)+"\n")
    output("configured",state)


if __name__=="__main__":
    try:main()
    except (ValueError,KeyError) as error:raise SystemExit(str(error)) from None
