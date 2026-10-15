"""Build-side, read-only preflight. PRs never inspect credentials or deployments."""
import json
import os
from pathlib import Path
import subprocess
from ci_security import context, output, require, signing_environment, trusted

c = context()
require(subprocess.run(["git", "rev-parse", "HEAD"], check=True, capture_output=True, text=True).stdout.strip() == c["source"], "Checkout differs from the event source")
is_main = trusted(c)
allowed, reason = signing_environment() if is_main else (False, "PR/non-main context: no signing or publication permission")
Path("ci-context.json").write_text(json.dumps(dict(c, trustedMain=is_main, signingEnvironmentReady=allowed, reason=reason, credentialsInspected=False), indent=2) + "\n")
output("trusted_main", str(is_main).lower())
output("signing_allowed", str(allowed).lower())
print(reason)
