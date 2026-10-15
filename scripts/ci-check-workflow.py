"""Structural policy regression; environment/branch enforcement still belongs to GitHub."""
from pathlib import Path
import re
import yaml
from ci_security import ENVIRONMENT, require


def check(document):
    require(set(document["on"])=={"push","pull_request","workflow_dispatch"},"Only three reviewed event types allowed")
    require(document["on"]["push"]["branches"]==["main"],"Push must be main only")
    require(document["permissions"]=={"contents":"read"},"Default token must be read-only")
    jobs=document["jobs"]
    require(set(jobs)=={"build","device-tests","trusted-signing","signed-install","publish"},"Unexpected privilege-bearing jobs")
    writes=[];secret_steps=[]
    for name,job in jobs.items():
        permissions=job.get("permissions",{})
        require(set(permissions)<= {"contents","actions"},"Unexpected token capability")
        require(permissions.get("contents")==("write" if name=="publish" else "read"),"Wrong contents permission")
        if "write" in permissions.values():writes.append(name)
        require(0<int(job["timeout-minutes"])<=45,"Every job needs a bounded timeout")
        for step in job["steps"]:
            if "uses" in step:
                require(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*@[0-9a-f]{40}",step["uses"]) is not None,"Actions must use immutable commit pins")
            if step.get("uses","").startswith("actions/checkout@"):
                require(step.get("with",{}).get("persist-credentials")=="false","Checkout must not persist tokens")
            require("${{" not in step.get("run",""),"Use environment arguments instead of workflow expression interpolation in shell")
            if "secrets." in str(step):secret_steps.append((name,step))
            if step.get("uses","").startswith("actions/upload-artifact@"):
                require(3<=int(step["with"]["retention-days"])<=7,"Temporary retention must be bounded")
    require(writes==["publish"],"Only publisher may write")
    require(len(secret_steps)==1 and secret_steps[0][0]=="trusted-signing","Secrets escaped dedicated signing step")
    require(jobs["trusted-signing"]["environment"]==ENVIRONMENT,"Signing must bind protected Environment")
    for name in ("trusted-signing","signed-install","publish"):
        condition=jobs[name]["if"]
        for guard in ("github.repository == 'mlzx-xxj/trade-review-app'","github.ref == 'refs/heads/main'","github.event_name == 'push'","github.event_name == 'workflow_dispatch'"):
            require(guard in condition,"Sensitive job missing event/main/repository guard")
    for name in ("trusted-signing","publish"):
        require(set(jobs[name]["needs"])>={"build","device-tests"},"Verification dependencies missing")
        for guard in ("needs.build.result == 'success'","needs.device-tests.result == 'success'"):
            require(guard in jobs[name]["if"],"Failed tests might reach a sensitive job")
    require("needs.build.outputs.signing_allowed == 'true'" in jobs["trusted-signing"]["if"],"Missing environment preflight")
    matrix=jobs["device-tests"]["strategy"]
    require(matrix["fail-fast"]=="false" and {row["api"] for row in matrix["matrix"]["include"]}=={"29","35"},"Keep both complete device suites")
    build_runs="\n".join(s.get("run","") for s in jobs["build"]["steps"])
    for task in (":app:assembleDebug",":app:assembleDebugAndroidTest",":app:assembleRc",":app:assembleRelease",":app:testDebugUnitTest",":app:lintDebug",":app:lintRc",":app:lintRelease"):
        require(task in build_runs,"Required Android task removed")
    require(document["concurrency"].get("cancel-in-progress")=="true","Obsolete runs must be cancellable")
    require("pull_request_target" not in str(document) and "workflow_run" not in str(document),"Untrusted external trigger forbidden")


def read(path):
    # BaseLoader uses YAML1.2-compatible string keys for GitHub's `on`.
    return yaml.load(Path(path).read_text(),Loader=yaml.BaseLoader)


if __name__=="__main__":
    check(read(".github/workflows/android.yml"))
    print("Workflow permissions, event guards, pinned actions and complete test tasks: passed")
