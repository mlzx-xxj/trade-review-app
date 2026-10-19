import importlib.util
import os
from pathlib import Path
import shutil
from stable_upgrade import verify_evidence
import json
from ci_security import manifest, require, require_trusted

require_trusted()
api=os.environ["API_LEVEL"];require(api in ("29","35"),"Unexpected API")
spec=importlib.util.spec_from_file_location("artifacts",Path(__file__).with_name("ci-artifacts.py"));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
for name in (f"package-probes-api{api}.json","56-v200-fixed-release-install.png"):
    m.copy_file(Path("ci-v200")/name,Path("ci-signed-install")/name)
candidate=json.loads(Path("delivered-packages/FIXED-IDENTITY.json").read_text())
verify_evidence("ci-v200/same-signer-upgrade",candidate["apkSha256"],int(api))
shutil.copytree("ci-v200/same-signer-upgrade","ci-signed-install/same-signer-upgrade")
manifest("ci-signed-install",f"signed-install-{api}")
