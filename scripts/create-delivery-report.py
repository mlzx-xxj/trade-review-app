"""Derive delivery counts from the actual report XML; reject failures or missing artifacts."""
import argparse
from collections import Counter
import json
from pathlib import Path
from xml.etree import ElementTree as ET


def junit(root, fragment):
    total = Counter()
    for path in root.rglob("*.xml"):
        if fragment in path.as_posix():
            suite = ET.parse(path).getroot()
            total.update({k: int(suite.get(k, "0")) for k in ["tests", "failures", "errors", "skipped"]})
    assert total["tests"] > 0 and not any(total[k] for k in ["failures", "errors", "skipped"])
    return dict(total)


def main():
    p=argparse.ArgumentParser();p.add_argument("--reports",required=True);p.add_argument("--screenshots",required=True);p.add_argument("--dist",required=True);p.add_argument("--run-url",required=True)
    a=p.parse_args();root=Path(a.reports);dist=Path(a.dist)
    counts={"jvm":junit(root/"build","/test-results/")};assert counts["jvm"]["tests"]>=144
    lint=list((root/"build").rglob("lint-results-debug.xml"));assert len(lint)==1
    issues=ET.parse(lint[0]).getroot().findall("issue");counts["lint"]=dict(Counter(i.get("severity") for i in issues));assert not any(counts["lint"].get(k,0) for k in ["Error","Fatal"])
    metrics={}
    for api in [29,35]:
        directory=root/"devices"/f"android-test-reports-api{api}"
        counts[f"api{api}"]=junit(directory,"/androidTest-results/");assert counts[f"api{api}"]["tests"]>=94
        images=Path(a.screenshots)/f"v08-emulator-screenshots-api{api}"
        names={f.name for f in images.rglob("*.png")}
        for prefix in [f"{i:02}-" for i in range(1,27)]:assert any(n.startswith(prefix) for n in names),f"Missing API {api} screenshot {prefix}"
        for filename in [f"performance-api{api}.json",f"images-api{api}.json",f"activity-api{api}.json",f"ocr-evaluation-api{api}.json",f"process-probes-api{api}.json",f"attachment-queries-api{api}.json"]:
            paths=list(directory.rglob(filename));assert paths,filename
            contents=[json.loads(f.read_text()) for f in paths];assert all(c==contents[0] for c in contents)
            metrics[filename]=contents[0]
        assert metrics[f"performance-api{api}.json"].get("complete") is True
        assert metrics[f"process-probes-api{api}.json"]["allSixteenTablesUnchanged"]
    (dist/"PERFORMANCE-AND-OCR.json").write_text(json.dumps(metrics,ensure_ascii=False,indent=2)+"\n")
    text=f"versionName=0.8.0\nversionCode=8\nGitHubActions={a.run_url}\nDebugApkBuild=success\n"+json.dumps(counts,ensure_ascii=False,indent=2)+"\nAPI 29/35: complete regressions and actual process restart probes. All OCR images and device records are synthetic.\nNo real phone test or original-fixed-signer old-to-new installation upgrade is claimed. Fixed signing status is in SIGNING.txt and PUBLICATION-CHECK.txt.\nKnown limits: descriptive performance on shared CI hosts, not a phone benchmark; OCR can miss/misread fields and always needs manual confirmation; backups are plaintext.\n"
    (dist/"CI-RESULTS.txt").write_text(text);print(text)


if __name__=="__main__":main()
