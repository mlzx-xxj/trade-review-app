from ci_security import manifest, output, require_trusted
import importlib.util
from pathlib import Path
import json

require_trusted()
spec=importlib.util.spec_from_file_location("artifacts",Path(__file__).with_name("ci-artifacts.py"))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
names=["SIGNING-STATUS.json"]
if json.loads(Path("dist/SIGNING-STATUS.json").read_text())["configured"]:
    names += ["TradeReview-v2.0-fixed-candidate.apk","FIXED-IDENTITY.json"]
for name in names:
    m.copy_file(Path("dist")/name,Path("ci-signed/dist")/name)
output("digest",manifest("ci-signed","signing-result"))
