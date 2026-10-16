"""Compare real AVD evidence without generating or editing screenshot pixels.

Run only on previously verified same-run artifact trees. Output is a responsive
HTML gallery plus byte-for-byte original PNG copies and measured operation data.
"""
import argparse
import hashlib
import html
import json
from pathlib import Path
import shutil
import struct

PROFILES=("regular-light","regular-dark","narrow-light","narrow-dark")
SCENES=("empty","stock-filled","stock-position","futures-filled","keyboard","futures-position","saved-list","partial-position","pending-filled","pending-position","statistics")
LABELS={"empty":"空持仓","stock-filled":"股票录入","stock-position":"股票持仓","futures-filled":"期货录入","keyboard":"实际输入法","futures-position":"期货持仓","saved-list":"成交列表","partial-position":"部分平仓","pending-filled":"费用待估算录入","pending-position":"待估算持仓","statistics":"统计回归"}


def one(root,name):
    paths=list(Path(root).rglob(name))
    if len(paths)!=1:raise ValueError("Missing or duplicate evidence: "+name)
    return paths[0]


def read(root,api,profile):
    data=json.loads(one(root,f"v210-visual-{profile}-api{api}.json").read_text())
    assert data["result"]=="PASS" and data["syntheticOnly"] is True and data["allExecutionsCreatedViaRealForm"] is True
    assert data["api"]==api and data["profile"]==profile and data["night"]==profile.endswith("dark")
    assert abs(data["fontScale"]-(1.3 if profile.startswith("narrow") else 1.0))<.01
    assert data["screenWidthDp"] in (range(318,323) if profile.startswith("narrow") else range(358,363))
    assert len(data["observations"])==5 and {s["scene"] for s in data["screenshots"]}==set(SCENES)
    assert [v["code"] for v in data["observations"]]==["600000","rb2701","rb2701","rb2701","i2701"]
    for scene in data["screenshots"]:
        raw=one(root,scene["file"]).read_bytes()
        assert raw[:8]==b"\x89PNG\r\n\x1a\n" and list(struct.unpack(">II",raw[16:24]))==scene["pixels"]
    return data


def compare(before,after):
    assert [(v["code"],v["action"],v["price"],v["quantity"],v["savedFees"],v["remaining"],v["realized"]) for v in before["observations"]]==[(v["code"],v["action"],v["price"],v["quantity"],v["savedFees"],v["remaining"],v["realized"]) for v in after["observations"]],"Changed financial outcomes or fixture"
    rows=[]
    for first,second in zip(before["observations"],after["observations"]):
        assert not first["extraConfirmation"] and not second["extraConfirmation"]
        assert set(first["fieldsTyped"])==set(second["fieldsTyped"]) and second["saveVisible"] is True
        assert second["tapsIncludingEntryAndSave"]<=first["tapsIncludingEntryAndSave"],"Entry takes more control taps"
        assert second["requiredScrollCommands"]<=first["requiredScrollCommands"],"Entry takes more required scrolls"
        rows.append(dict(code=first["code"],action=first["action"],beforeTaps=first["tapsIncludingEntryAndSave"],afterTaps=second["tapsIncludingEntryAndSave"],beforeScrolls=first["requiredScrollCommands"],afterScrolls=second["requiredScrollCommands"],fields=len(second["fieldsTyped"]),financialFactsEqual=True))
    return rows


def main():
    p=argparse.ArgumentParser();p.add_argument("--before",required=True);p.add_argument("--after",required=True);p.add_argument("--output",required=True);p.add_argument("--baseline-run",required=True);p.add_argument("--candidate-run",required=True);a=p.parse_args()
    out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
    metrics={};sections=[];hashes={}
    for api in (29,35):
        for profile in PROFILES:
            before=read(Path(a.before)/f"api{api}",api,profile);after=read(Path(a.after)/f"api{api}",api,profile)
            metrics[f"api{api}-{profile}"]=compare(before,after)
            # Full original screenshots remain in both API artifacts. Keep the
            # review gallery compact: all regular-light, three dark, two narrow.
            selected=SCENES if profile=="regular-light" else (("futures-filled","partial-position","empty") if profile=="regular-dark" else ("keyboard","partial-position"))
            if api!=35:continue
            for scene in selected:
                title=f"API {api} · {profile} · {LABELS[scene]}"
                images=[]
                for label,root,data in (("V2.0",a.before,before),("V2.1",a.after,after)):
                    name=f"v210-{profile}-{scene}.png";source=one(Path(root)/f"api{api}",name)
                    relative=f"{'before' if label=='V2.0' else 'after'}/{name}"
                    target=out/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
                    hashes[relative]=hashlib.sha256(target.read_bytes()).hexdigest()
                    images.append(f'<figure><figcaption>{label}</figcaption><a href="{relative}"><img src="{relative}" alt="{html.escape(title)} {label}" loading="lazy"></a></figure>')
                sections.append(f'<section><h2>{html.escape(title)}</h2><div class="pair">'+"".join(images)+"</div></section>")
    report=dict(baselineRun=a.baseline_run,candidateRun=a.candidate_run,operationComparison=metrics,screenshotSha256=hashes,images="unaltered original UiAutomation PNG; CSS scales display only",realPhone="not-executed")
    (out/"comparison.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    page='<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>V2.0 / V2.1 实际模拟器对照</title><style>body{font:16px system-ui;background:#f3f5f9;color:#17243a;margin:16px auto;max-width:900px;padding:0 12px}h1{font-size:24px}h2{font-size:17px}section{padding:12px;background:white;border:1px solid #d7dfea;border-radius:12px;margin:16px 0}.pair{display:grid;grid-template-columns:1fr 1fr;gap:8px}figure{margin:0;min-width:0}figcaption{padding:8px;font-weight:600}img{width:100%;height:auto;display:block}a{color:#304c75}</style><h1>V2.0 / V2.1 真实界面对照</h1><p>同一设备配置、同一合成数据、实际生产 MainActivity；原始 PNG 未编辑。点击可查看原图。</p>'+f'<p>基线：<a href="{html.escape(a.baseline_run,quote=True)}">Actions</a>　新版：<a href="{html.escape(a.candidate_run,quote=True)}">Actions</a>　<a href="comparison.json">操作计数与图片 SHA-256</a></p>'+"".join(sections)+"</html>"
    (out/"index.html").write_text(page)
    print(json.dumps({"profilesCompared":len(metrics),"originalPngCopies":len(hashes),"result":"PASS"}))


if __name__=="__main__":main()
