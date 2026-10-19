import importlib.util
import os
from pathlib import Path
import re
import subprocess
from ci_security import require, require_trusted
from stable_upgrade import download_stable

require_trusted()
spec=importlib.util.spec_from_file_location("artifacts",Path(__file__).with_name("ci-artifacts.py"));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
for key in ("BUILD_ATTEMPT","SIGNING_ATTEMPT"):
    require(re.fullmatch(r"[1-9][0-9]*",os.environ[key]) is not None,"Invalid artifact attempt")
m.download("rc-apk-attempt"+os.environ["BUILD_ATTEMPT"],"delivered-packages","rc",os.environ["RC_DIGEST"])
m.download("signing-result-attempt"+os.environ["SIGNING_ATTEMPT"],"signed-input","signing-result",os.environ["SIGNED_DIGEST"])
for name in ("TradeReview-v2.0-fixed-candidate.apk","FIXED-IDENTITY.json","SIGNING-STATUS.json"):
    m.copy_file(Path("signed-input/dist")/name,Path("delivered-packages")/name)
# Always require the exact public Stable baseline; no optional/RC fallback.
download_stable("delivered-packages/stable")
tag,asset=os.environ.get("OLD_TAG",""),os.environ.get("OLD_ASSET","")
if tag or asset:
    require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,100}",tag) is not None and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,100}\.apk",asset) is not None,"Invalid exact public old tag/APK name")
    subprocess.run(["gh","release","download",tag,"--repo","mlzx-xxj/trade-review-app","--pattern",asset,"--dir","old-public-apk"],check=True)
    m.copy_file(Path("old-public-apk")/asset,"delivered-packages/old-fixed.apk")
