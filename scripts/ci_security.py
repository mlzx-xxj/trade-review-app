"""CI trust boundaries. No private signing material is read by this module."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request
from signing_identity import PIN, LEGACY_V03_PIN

REPOSITORY = "mlzx-xxj/trade-review-app"
ENVIRONMENT = "v03-signing-main"


def require(value, message):
    if not value:
        raise ValueError(message)


def context(environment=None):
    e = os.environ if environment is None else environment
    return dict(repository=e.get("GITHUB_REPOSITORY"), event=e.get("GITHUB_EVENT_NAME"),
                ref=e.get("GITHUB_REF"), source=e.get("SOURCE_COMMIT"),
                sha=e.get("GITHUB_SHA"), workflow=e.get("GITHUB_WORKFLOW_REF"))


def trusted(c):
    return (c.get("repository") == REPOSITORY and c.get("event") in ("push", "workflow_dispatch")
            and c.get("ref") == "refs/heads/main" and c.get("source") == c.get("sha")
            and bool(re.fullmatch(r"[0-9a-f]{40}", c.get("source") or ""))
            and c.get("workflow") == REPOSITORY + "/.github/workflows/android.yml@refs/heads/main")


def require_trusted(c=None, results=None):
    require(trusted(context() if c is None else c), "Only this repository's actual main push/manual workflow is trusted")
    if results is not None:
        require(results == {"build": "success", "device-tests": "success"}, "Both build and complete emulator matrix must succeed")


def environment_safe(data, policies):
    policy = data.get("deployment_branch_policy") or {}
    branches = policies.get("branch_policies", [])
    return (data.get("name") == ENVIRONMENT and policy.get("custom_branch_policies") is True
            and policy.get("protected_branches") is False and len(branches) == 1
            and branches[0].get("name") == "main" and branches[0].get("type") == "branch")


def api(path):
    # The API path is composed exclusively from constants and validated numeric IDs.
    return json.loads(subprocess.run(["gh", "api", "--method", "GET", path],
                                    check=True, capture_output=True, text=True).stdout)


def signing_environment():
    require_trusted()
    base = "repos/" + REPOSITORY + "/environments/" + ENVIRONMENT
    try:
        data = api(base)
        policies = api(base + "/deployment-branch-policies")
    except subprocess.CalledProcessError:
        return False, "Protected signing Environment missing or unreadable; no private-key step is allowed"
    if not environment_safe(data, policies):
        return False, "Signing Environment must allow exactly the main branch, no tag/wildcard/PR deployment"
    return True, "Verified existing main-only signing Environment; credentials are checked only in that job"


def output(name, value):
    require(re.fullmatch(r"[a-zA-Z0-9_]+", name) is not None and "\n" not in str(value), "Invalid workflow output")
    with open(os.environ["GITHUB_OUTPUT"], "a") as f:
        f.write(f"{name}={value}\n")


def digest(path):
    with Path(path).open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()


def provenance():
    run = os.environ["GITHUB_RUN_ID"]
    attempt = os.environ["GITHUB_RUN_ATTEMPT"]
    require(re.fullmatch(r"[1-9][0-9]*", run) is not None and re.fullmatch(r"[1-9][0-9]*", attempt) is not None, "Invalid run identifiers")
    require(os.environ["GITHUB_REPOSITORY"] == REPOSITORY, "Unexpected repository")
    require(re.fullmatch(r"[0-9a-f]{40}", os.environ["SOURCE_COMMIT"]) is not None, "Invalid source")
    return dict(repository=REPOSITORY, sourceCommit=os.environ["SOURCE_COMMIT"], runId=int(run), runAttempt=int(attempt))


def manifest(root, kind):
    root = Path(root)
    files = {}
    for path in sorted(root.rglob("*")):
        require(not path.is_symlink(), "Artifact symlink rejected")
        if path.is_file() and path != root / "MANIFEST.json":
            files[path.relative_to(root).as_posix()] = dict(sha256=digest(path), bytes=path.stat().st_size)
    require(bool(files), "Empty artifact rejected")
    value = dict(provenance(), kind=kind, files=files)
    target = root / "MANIFEST.json"
    target.write_text(json.dumps(value, sort_keys=True, indent=2) + "\n")
    return digest(target)


def verify_manifest(root, kind, expected_digest=None):
    root = Path(root)
    target = root / "MANIFEST.json"
    require(target.is_file() and not target.is_symlink(), "Artifact manifest missing")
    if expected_digest is not None:
        require(re.fullmatch(r"[0-9a-f]{64}", expected_digest) is not None and digest(target) == expected_digest, "Verified job manifest SHA mismatch")
    value = json.loads(target.read_text())
    p = provenance()
    require(all(value.get(k) == p[k] for k in ("repository", "sourceCommit", "runId")), "Artifact source/run/repository mismatch")
    require(type(value.get("runAttempt")) is int and 1 <= value["runAttempt"] <= p["runAttempt"] and value.get("kind") == kind, "Artifact attempt/kind mismatch")
    files = value.get("files")
    require(isinstance(files, dict) and files, "Artifact inventory missing")
    actual = set()
    for path in root.rglob("*"):
        require(not path.is_symlink(), "Artifact symlink rejected")
        if path.is_file() and path != target:
            name = path.relative_to(root).as_posix()
            actual.add(name)
            require(files.get(name) == dict(sha256=digest(path), bytes=path.stat().st_size), "Artifact payload hash/size mismatch")
    require(actual == set(files), "Artifact payload missing or added")
    return value
