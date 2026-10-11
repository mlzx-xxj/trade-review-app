# V0.2 数据安全与升级

## 分支与结构

开始时 PR #1 未合并：main 为 `72fa78e`，只有初始 README；MVP 在 `codex/android-offline-mvp`，头提交 `a868951`。本轮从该远端提交建立 `codex/v02-data-safety`，新 PR 以 MVP 分支为基底；待 PR #1 合并后可改为 main。未重建工程、删除旧文件或替换 Kotlin / Compose / Room 架构。

- `backup/BackupCodec.kt`：独立 JSON 格式、严格解析、校验、完整性摘要。
- `backup/BackupRepository.kt`：一致性快照、预览、合并与覆盖事务。
- `backup/TradeCsv.kt`：UTF-8 BOM + RFC 4180 CSV。
- `ui/BackupViewModel.kt` / `SettingsPage.kt`：SAF 文件选择、后台 I/O、恢复二次确认。
- Room v2：只给交易追加稳定标识及唯一索引，保留 v1 schema 与正式 Migration。

## 用户操作

主页面右上角「设置与备份」打开设置。四个主页面保持原有功能。

1. 保存完整 JSON 备份：系统文件选择器自主选择位置，包含所有币种交易、每日计划、复盘、标签及完成标记。
2. 选择 JSON 备份恢复：完整校验后显示备份数量、当前数量、预计新增、重复与冲突数量；此时数据库不变。
3. 选择「去重合并」或「覆盖恢复」，点「继续确认恢复」，再在第二个对话框点「确认恢复」。取消和返回预览不会写库。
4. 导出全部交易 CSV：包括日期、市场、品种、方向、币种、精确金额、仓位、策略、情绪、错误和纪律标签。

JSON 是明文。应用没有 INTERNET 权限，也不索取广泛存储权限；SAF 中的云盘由用户的文件提供方处理。失败导出可能留下空文件或不完整文件，只有成功提示才代表文件已写入并关闭；建议保留至少一份已验证备份。

## JSON 格式与校验

根字段：`format=trade-review-backup`、`schemaVersion=1`、`exportedAt`（ISO-8601 Instant）、`counts`、`trades`、`dailyReviews`、`checksumSha256`。备份格式版本与 Room 表版本独立。金额为十进制字符串，日期为 LocalDate epochDay；标签为固定选项 ID。未知版本不猜测、不自动转换。

严格 UTF-8 / JSON，拒绝空值、缺失或未知字段、重复 JSON 键、错误字段类型、重复交易 row ID / recordKey、重复日志日期、未知或重复标签、错误数量和摘要。日期 1900–2100 年；交易及已完成盘后复盘不能在未来；盘前计划可在未来。仓位 1–100%，评分 1–5，品种 1–30 字符，金额最多 12 位整数和 2 位小数。最多 16 MiB，每张表最多 10000 条，限制嵌套和单字段长度。

摘要覆盖固定顺序重新编码的全部字段（不含摘要自身），防止误损坏、截断或修改造成悄然恢复；它不是加密或身份认证。JSON 重新排版或改变对象键顺序不影响校验。超过限制时明确失败，禁止导出一个不可恢复的截断备份。

## 恢复规则

- 交易按持久的 32 位十六进制 recordKey 去重，两个内容相同但独立建立的交易拥有不同 key，不会被错误折叠。
- 本地 row ID 仅用于本机。覆盖保留备份 row ID；合并的新交易重新分配 row ID，避免跨设备编号冲突。
- 相同 key 内容相同：跳过；内容不同：保留本地并计入冲突，预览中明确提示。
- 每日日志按日期匹配。已有盘前计划、盘后复盘分别保留；缺少的完成部分从备份补齐；冲突分别计数。覆盖模式精确恢复所有字段（含未完成草稿）。
- 快照和导出读取同一事务。预览记录完整本地快照，确认恢复时在事务中重新比较；变化则拒绝并要求重新预览。
- 覆盖清空和插入、合并插入和更新均在单一 `withTransaction` 内。SQL 故障和取消不提交中间状态。禁止 destructive migration。

CSV 每个字段加双引号，内部引号加倍，记录使用 CRLF；中文用 UTF-8 BOM。自定义品种若可能被电子表格解释为公式，CSV 加单引号作为文本；JSON 始终保留原值。CSV 不包含完整日志，不能作为恢复格式。

## Room v1 → v2

`MIGRATION_1_2` 追加 `recordKey TEXT NOT NULL DEFAULT ''`，为每条旧交易一次性写入 `lower(hex(randomblob(16)))`，创建唯一索引。原 ID、交易字段、每日计划与复盘表保持原样。以后编辑/备份/合并始终保留同一 recordKey。应用注册此 Migration；没有 destructive fallback。

正式设备测试使用提交的 v1 schema 创建数据库，执行 `MigrationTestHelper.runMigrationsAndValidate`，验证全部旧字段、标签、完成标记、两条交易的唯一标识及再次打开后的标识稳定性；另测空库升级。生成的 v2 schema 作为报告上传，设备任务下载为测试资产，并提交入库。

## 签名与版本

`applicationId=com.tradereview.app` 不变，versionCode 从 1 增为 2，versionName 为 0.2.0。签名与版本方案见 [SIGNING-AND-UPGRADES.md](SIGNING-AND-UPGRADES.md)。旧密钥未找回前，测试 APK 仅能用于新测试设备，不能安全覆盖已安装 V0.1；不要卸载已有应用来安装测试包。真实交易始终保持在原应用中。
