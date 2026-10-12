# V0.7 实施计划

PR #6 已于 2026-10-12T06:08:41Z 合并，最新 main a07859ce4926328b028e76be0d0c06663dee4a7a。分支 codex/v07-smart-review，沿用 Kotlin/Compose/Room；Base 保持不变，Android 编译与模拟器交给 Actions，不安装 Android Studio。

1. 接入 ML Kit 打包中文/Latin OCR，后台读取、私有临时图、显式字段解析、多候选/冲突/草稿、逐项核实和重复/风险确认。
2. 集中纯 Kotlin 洞察规则，币种/有效 R/未知状态/样本门槛、真实明细和周月报复用；盘前清单与目标/日志同日期关联，保留检查快照。
3. Room 正式 v6→v7 新增四表，保留 v1→v7 旧链；JSON schema 6 与 ZIP 容器 1 覆盖十六表和图片，旧 JSON 1–5 兼容。
4. 保留所有原回归，新增解析/统计/提醒/备份、真实离线 OCR、迁移及界面用例；保留旧 18 张截图并新增 OCR/确认/洞察/盘前 4 张。
5. 完整最终 CI、原始 XML、独立 APK 验签与哈希、截图审查、独立 PR；不自动合并。生成真机验收清单但不冒称手机实测。

Secrets 名称 API 实际返回 403，不读取私有密钥。Google Maven 经现有代理成功访问，已检查 Chinese/Latin 模型资产；最终签名状态依据实际 CI 和 APK。
