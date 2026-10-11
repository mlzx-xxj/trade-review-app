# V0.4 实施计划与边界

PR #3 已于 2026-10-11 合并，基于 main `afefcfa996d3f229a31b7196366874d50ea87834` 创建 `codex/v04-calendar-analytics`，沿用原 Kotlin / Compose / Material 3 / Room 工程。目标 0.4.0 / versionCode 4 / com.tradereview.app，minSdk 26。

1. 实现统一 TradeFilter、BigDecimal 分币种分析、周一月历与日期聚合。统计只使用本机已保存交易，不含草稿；同日按原数据库编号排序。
2. 持久化配色与常用方案，提供 Room v3→v4 迁移，保留 v1/v2/v3 schema 与升级链。JSON 格式 3 纳入新数据，严格兼容格式 1/2，事务预览与恢复能力保留。
3. 新增第五个日历入口，保留原四个页面及录入、标签、品种、草稿、安全文件操作。加入每日详情、热力图、统一筛选、可点击图表和多维行为比较；计算和曲线降采样放在后台，长列表使用惰性布局。
4. Actions 实际执行编译、JVM、Lint 和 API 29 模拟器迁移/恢复/UI 测试，修复真实失败，采集合成数据截图。最终文档提交同样跑完整 CI，不使用 skip ci，不自动合并。

本轮不生成、读取或替换长期私钥。检查 Secrets/Variables 读取权限返回 GitHub 403，最新 main 的实际工作流未配置固定签名，V0.4 按实际 SIGNING.txt 状态交付临时测试包。固定签名未启用时不能承诺覆盖固定签名 V0.3。配置方法见 V04-DATA-AND-UPGRADES.md；测试结果独立记录在 V04-VALIDATION.md。
