"""Independent Decimal and raw ZIP checks for real MainActivity operations."""
from decimal import Decimal, ROUND_HALF_UP
from datetime import date
import hashlib
import json
from pathlib import Path
import re
import zipfile

D = Decimal
EXPECTED = (
    ("stock", "603999", "buy", "open", "10", "1000", "1.00"),
    ("stock", "603999", "buy", "open", "11", "1000", "1.10"),
    ("stock", "603999", "sell", "close", "12", "1500", "10.80"),
    ("futures", "i2701", "buy", "open", "800", "2", None),
    ("futures", "i2701", "sell", "close", "810", "1", None),
)
TRADING = ("executions", "cycles", "lots", "matches", "audits", "plans", "reviews")
PROFILES = ("regular-light", "regular-dark", "narrow-light", "narrow-dark")


def verify_financial(before, ledger):
    for key in TRADING:
        assert before[key] == ledger[key], "Configuration/restore changed " + key
    executions = sorted(ledger["executions"], key=lambda e: e["ordinal"])
    assert len(executions) == 5 and [e["ordinal"] for e in executions] == list(range(1, 6))
    for e, expected in zip(executions, EXPECTED):
        assert tuple(e.get(k) for k in ("accountId", "code", "side", "effect", "price", "quantity", "fees")) == expected
        if e["accountId"] == "stock":
            rate = D(".0001") if e["side"] == "buy" else D(".0006")
            assert D(e["fees"]) == (D(e["price"]) * D(e["quantity"]) * rate).quantize(D(".01"), rounding=ROUND_HALF_UP)
        else:
            assert e["fees"] is None and e["marginRate"] is None and e["multiplier"] == "100"
    stock = next(c for c in ledger["cycles"] if c["accountId"] == "stock")
    unknown = next(c for c in ledger["cycles"] if c["accountId"] == "futures")
    assert len(ledger["cycles"]) == 2
    assert stock["remaining"] == "500" and D(stock["realized"]) == D("2487.65")
    assert D(stock["openCost"]) == D("5500.55") and D(stock["averagePrice"]) == D("11")
    assert D(stock["fees"]) == D("12.90")
    assert unknown["remaining"] == "1"
    assert unknown["realized"] is None and unknown["fees"] is None and unknown["referenceMargin"] is None
    matches = ledger["matches"]
    assert len(matches) == 3
    order = {e["id"]: e["ordinal"] for e in executions}
    matched = sorted((m for m in matches if m["cycleId"] == stock["id"]), key=lambda m: order[m["openExecutionId"]])
    assert [m["openExecutionId"] for m in matched] == [executions[0]["id"], executions[1]["id"]]
    assert [m["quantity"] for m in matched] == ["1000", "500"], "FIFO must consume first lot before second"
    assert all(m["closeExecutionId"] == executions[2]["id"] for m in matched)
    gross = D(1000) * (D(12) - D(10)) + D(500) * (D(12) - D(11))
    assert gross == D(2500) and sum((D(m["gross"]) for m in matched), D(0)) == gross
    assert sum((D(m["openFees"]) for m in matched), D(0)) == D("1.55")
    assert sum((D(m["closeFees"]) for m in matched), D(0)) == D("10.80")
    assert sum((D(m["net"]) for m in matched), D(0)) == gross - D("1.55") - D("10.80")
    assert [D(m["openFees"]) for m in matched] == [D(1), D(".55")]
    assert [D(m["closeFees"]) for m in matched] == [D("7.2"), D("3.6")]
    assert [D(m["net"]) for m in matched] == [D("1991.8"), D("495.85")]
    missing = [m for m in matches if m["cycleId"] == unknown["id"]]
    assert len(missing) == 1 and D(missing[0]["gross"]) == D(1000)
    assert missing[0]["net"] is None and missing[0]["openFees"] is None and missing[0]["closeFees"] is None
    assert missing[0]["openExecutionId"] == executions[3]["id"] and missing[0]["closeExecutionId"] == executions[4]["id"]
    assert missing[0]["quantity"] == "1"
    stock_lots = sorted((l for l in ledger["lots"] if l["cycleId"] == stock["id"]), key=lambda l: order[l["executionId"]])
    assert [l["remaining"] for l in stock_lots] == ["0", "500"]
    assert sum((D(l["allocatedFees"]) for l in stock_lots), D(0)) == D("1.55")
    future_lots = [l for l in ledger["lots"] if l["cycleId"] == unknown["id"]]
    assert len(ledger["lots"]) == 3 and len(future_lots) == 1
    assert future_lots[0]["executionId"] == executions[3]["id"]
    assert future_lots[0]["remaining"] == "1" and future_lots[0]["allocatedFees"] is None
    # Average opening price excludes fees; openCost separately includes unallocated fees.
    execution_by_id = {e["id"]: e for e in executions}
    price_cost = sum((D(execution_by_id[l["executionId"]]["price"]) * D(l["remaining"]) for l in stock_lots), D(0))
    assert price_cost / D(stock["remaining"]) == D(stock["averagePrice"])
    assert D(500) * D(11) + (D("1.10") - D(".55")) == D(stock["openCost"])
    return dict(knownRealized="2487.65", stockRemaining="500", stockOpenCost="5500.55",
                unknownFeeExecutions=2, unknownGross="1000", unknownNet=None)


def verify_zip(path, ledger, whole=None):
    raw = Path(path).read_bytes()
    assert 0 < len(raw) <= 64 * 1024 * 1024
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        assert len(names) == len(set(names)) and z.testzip() is None
        manifest = json.loads(z.read("manifest.json"))
        assert manifest["format"] == "trade-review-full-backup" and manifest["zipVersion"] == 1
        assert manifest["data"]["path"] == "data.json"
        items = [manifest["data"], *manifest["images"]]
        assert set(names) == {"manifest.json", *(i["path"] for i in items)}
        for item in items:
            assert item["path"] == "data.json" or re.fullmatch(r"images/[a-f0-9]{32}-[a-f0-9]{64}\.jpg", item["path"])
            content = z.read(item["path"])
            assert len(content) == item["bytes"] and hashlib.sha256(content).hexdigest() == item["sha256"]
        data = json.loads(z.read("data.json"))
        assert data["schemaVersion"] == 8 and data["backupScope"] == "with-attachment-files"
        assert data["ledger"] == ledger, "Actual exported ZIP differs from restored UI ledger"
        if whole is not None:
            data.pop("exportedAt")
            data.pop("checksumSha256")
            assert data == whole, "Legacy tables/configuration/attachments differ from actual ZIP"
    return dict(backupSha256=hashlib.sha256(raw).hexdigest(), backupBytes=len(raw), zipEntries=len(names))


def verify(first, second, api, profile, backup):
    assert profile in PROFILES and api in (29, 35)
    for proof in (first, second):
        assert proof["api"] == api and proof["profile"] == profile and proof["result"] == "PASS"
    assert first["syntheticOnly"] is True and first["dateAnchor"] == "2026-10-17"
    assert first["activity"] == "com.tradereview.app.MainActivity"
    assert first["screenWidthDp"] in (range(318, 323) if profile.startswith("narrow") else range(358, 363))
    assert abs(first["fontScale"] - (1.3 if profile.startswith("narrow") else 1.0)) < .01
    assert first["night"] == profile.endswith("dark")
    for key in ("allExecutionsCreatedViaRealForm", "configurationSavedViaUi", "systemDocumentsUiUsed",
                "zipExportedAndReadbackVerified", "previewCancelPreservedFacts", "replaceRestoreMatchedExport"):
        assert first[key] is True, key
    assert first["pid"] == second["previousPid"] and first["pid"] != second["pid"]
    assert second["allFactsIdentical"] is True and second["savedFieldsReadViaUi"] is True
    assert first["ledger"] == second["ledger"]
    assert first["wholeBackupFactsIdentical"] is True and second["wholeBackupFactsIdentical"] is True
    assert first["wholeBackupAtExport"] == second["wholeBackupFacts"]
    before, ledger = first["financialBeforeConfiguration"], first["ledger"]
    financial = verify_financial(before, ledger)
    assert before["accounts"] == ledger["accounts"]
    strategies = ledger["strategies"]
    assert len(strategies) == len(before["strategies"]) == 1
    assert strategies[0]["id"] == before["strategies"][0]["id"]
    assert strategies[0]["name"] == "合成复核策略已更正" and 100 <= len(strategies[0]["description"]) <= 200
    assert min(ledger["executions"], key=lambda e: e["ordinal"])["strategyId"] == strategies[0]["id"]
    risks = {r["id"]: r for r in ledger["risks"]}
    assert risks["stock"]["riskFund"] == "10000" and risks["stock"]["limitValue"] == "500"
    assert risks["stock"]["limitMode"] == "amount"
    assert risks["futures"] == next(r for r in before["risks"] if r["id"] == "futures")
    assert len(ledger["rules"]) == 1 and not before["rules"]
    rule = ledger["rules"][0]
    assert rule["code"] == "i2701" and rule["name"] == "合成合约参数已更正"
    assert rule["effectiveDay"] == (date(2026, 10, 15) - date(1970, 1, 1)).days
    assert all(rule["effectiveDay"] <= e["tradingDay"] for e in ledger["executions"] if e["accountId"] == "futures")
    assert rule["verified"] is True and rule["multiplier"] == "100" and D(rule["marginRate"]) == D(".1")
    assert tuple(rule[k] for k in ("openFee", "closeFee", "todayFee")) == ("1", "2", "3")
    expected_steps = {
        "create strategy through UI", "save five real-form executions",
        "correct linked strategy and recreate editor", "save independent stock risk through keyboard UI",
        "save verified future rule without repricing old unknown fees",
        "real DocumentsUI ZIP export preview cancel and replace restore",
    }
    for step in expected_steps:
        assert [o["event"] for o in first["operations"] if o["step"] == step] == ["begin", "complete"]
    picker = [o for o in first["operations"] if o["step"] == "DocumentsUI"]
    assert [o["kind"] for o in picker] == ["CreateDocument", "OpenDocument", "OpenDocument"]
    assert all(re.fullmatch(r"com(?:\.google)?\.android\.documentsui", o["package"]) for o in picker)
    assert len(first["controls"]) == 5
    assert [c["inputTag"] for c in first["controls"]] == ["strategy-description", "strategy-description", None, "rule-name", "rule-name"]
    assert [c["orientation"] for c in first["controls"]] == [2, 1, 1, 2, 1]
    assert {c["orientation"] for c in first["controls"]} == {1, 2}
    for control in first["controls"]:
        assert control["keyboardVisible"] is True and control["aboveKeyboard"] is True
        assert control["touchHeightDp"] >= 48 and control["buttonBoundsPx"]
        left, top, right, bottom = control["buttonBoundsPx"]
        assert right > left and top >= 0 and bottom <= control["imeTopPx"]
        assert (bottom - top) / 2 == control["touchHeightDp"]
        if control["inputTag"] is not None:
            assert control["inputVisible"] is True and control["inputBoundsPx"]
            left, top, right, bottom = control["inputBoundsPx"]
            assert right > left and top >= 0 and bottom <= control["imeTopPx"] and (bottom - top) >= 48
    displayed = {
        ("account-net-stock", "￥2487.65"), ("account-fees-stock", "￥12.90"),
        ("account-cost-stock", "￥5500.55"), ("account-cash-stock", "待估算"),
        ("account-net-futures", "待估算"), ("account-fees-futures", "待估算"),
        ("account-margin-futures", "待估算"), ("period-realized-net", "￥2487.65"),
    }
    for proof in (first, second):
        assert {(m["tag"], m["value"]) for m in proof["displayedMoney"]} == displayed
        for m in proof["displayedMoney"]:
            assert m["visible"] is True and m["fontSp"] >= 14 and not m["overflowWidth"] and not m["overflowHeight"]
            if m["tag"] in ("account-net-stock", "period-realized-net"):
                assert m["argb"].upper() == ("#FFFFB4B0" if profile.endswith("dark") else "#FFAF273C")
    assert len(first["screenshots"]) >= 8 and len(second["screenshots"]) >= 2
    assert not set(first["screenshots"]) & set(second["screenshots"]), "Reopen must retain distinct original PNGs"
    return dict(api=api, profile=profile, financial=financial, **verify_zip(backup, ledger, first["wholeBackupAtExport"]))
