# V0.3 数据、备份与后续升级

版本 0.3.0 / versionCode 3；applicationId 保持 `com.tradereview.app`。

## 数据结构

Room v3：原 trades 追加默认空备注；原 daily_reviews 不变。新增 tags、instruments、app_settings、entry_drafts。正式 MIGRATION_2_3 保留全部旧数据、recordKey 和标签关联，种入原预设稳定 ID 与 15 个国内期货品种；历史自定义品种加入选择目录，交易 symbol 文本不被修改。保留 MIGRATION_1_2 和 v1/v2 schema，禁止 destructive migration。

六类标签以 (kind,id) 为主键，原 ID 完整保留；用户新增 ID 使用 UUID。重命名、隐藏、收藏和排序只修改属性。历史记录和已选表单仍通过稳定 ID 查找当前名称，包括已隐藏标签。多选集合继续存储 ID，不存名称。隐藏的标签不进入新的候选列表，已选的隐藏项显示“已隐藏”。

品种目录的 name/code、收藏、排序、最近时间持久化。交易保存仅更新目录最近使用，历史记录继续保留交易当时的 symbol 文本。最近使用优先，其次收藏，最后手动排序；管理页按手动顺序排列便于调序。同市场同名称不能重复添加。

草稿独立于已保存交易，金额可为空或未完成输入，不计入首页/统计。新增草稿键 new，编辑草稿键交易 recordKey；每次表单变化立即写本地 Room。保存交易、记录最近使用、清除草稿是一个事务。复制保留市场、品种、方向、币种、仓位、策略，清空盈亏选择、金额、备注及执行/复盘信息。连续新增日期默认今天。

## 备份格式

V0.3 的 schemaVersion=2，与 Room 版本独立。包含全部交易（含备注）、每日日志、六类标签定义、品种目录/收藏/排序/最近使用、应用设置和全部草稿；保留导出时间、数量与 SHA-256 完整性摘要。UTF-8 严格解析，拒绝重复 JSON 键、未知字段/版本、错误类型/范围、重复标识、缺失预设稳定 ID 和无效标签/草稿关联。单文件 16 MiB，各数组最多 10000 条。

可以读取 V0.2 schemaVersion=1：先按原字段顺序验证原摘要和严格字段，再补入预设目录、默认设置及空备注。重新导出为 schemaVersion=2。V0.2 App 不能导入新版格式。

覆盖恢复替换全部六张表，空交易备份也可明确覆盖为空。去重合并按交易 recordKey、日志日期、标签 (kind,id)、品种 ID/市场名称和草稿键去重；冲突保留本地值，补入缺少的计划/复盘部分与新目录项。保留本地设置及同 ID 的名称、收藏、排序，不把远端设置覆盖本地。编辑草稿的数据库行 ID 按稳定 recordKey 重映射。恢复预览显示版本、交易/日志/标签/品种/草稿数量；必须二次确认。预览后任意相关表发生变化会拒绝过期确认。所有写入在单一 Room 事务中，SQL 失败回滚全部表。

JSON 是明文；摘要用于发现损坏，不能证明文件来源或加密。CSV 使用当前标签名称、交易当时品种和备注，UTF-8 BOM、字段引号、双引号转义、CRLF 行尾；自定义文本的表格公式前缀加单引号。SAF 由用户选择文件位置，无 INTERNET 或宽泛存储权限。

## 签名实际状态与配置

本轮未生成正式私钥，私钥不得提交 GitHub、PR、聊天或构建产物。默认 CI 临时 Debug 私钥仅生成在一次性 runner；未配置以下 Secrets 时，输出 `TradeReview-v0.3-test.apk`，它不建立未来的固定升级身份。`SIGNING.txt` 记录该次构建的真实证书。用户已明确允许手动卸载旧 V0.1/V0.2 测试版重新安装；卸载删除手机本地数据。V0.3 新数据应定期 JSON 备份。

需要仓库所有者完成以下操作；此文档不表示已经配置成功：

1. 在可信本地设备准备/安全生成一份用于 V0.3 起长期使用的 keystore，离线备份私钥及密码；如果需要 Codex 代为生成，须单独授权。不要依赖临时 runner 或 Gradle 缓存保管密钥。
2. 打开仓库 Settings → Secrets and variables → Actions → Secrets，添加 `TRADE_REVIEW_KEYSTORE_BASE64`（完整 keystore 的 Base64）、`TRADE_REVIEW_KEYSTORE_PASSWORD`、`TRADE_REVIEW_KEY_ALIAS`、`TRADE_REVIEW_KEY_PASSWORD`。建议用 GitHub CLI `gh secret set NAME --body-file /安全本地路径` 或交互粘贴到 GitHub 设置；不要把值写到代码、命令日志或聊天。
3. 用本地 keytool 查看该 alias 的公开证书 SHA-256；在同页 Variables 添加 `TRADE_REVIEW_UPGRADE_CERT_SHA256`（64 位十六进制，可带冒号）。这里从 V0.3 起 pin 所选固定证书，不再默认绑定旧 V0.1 临时证书。
4. 重新运行 Android CI。只有 Secrets 提供固定密钥且 apksigner 验证与 pin 完全匹配，才输出 `TradeReview-v0.3-stable.apk`。缺少 pin、不匹配或 Secrets 不全会失败，禁止稳定包发布。私钥临时文件在 always 步骤删除，从不上传。
5. 第一次安装固定签名 V0.3 需卸载不同签名的临时测试版，先保存新版 JSON（含新数据），安装后恢复。之后 V0.4/V0.5 保持相同 applicationId/私钥、递增 versionCode，并提供正式 Room Migration，就能满足 Android 覆盖升级条件。实际真机升级仍需设备验证；仅代码和模拟器测试不能声称已经真机保留数据升级成功。

这是 Debug APK 交付方案；不混同 Play 正式发布。配置正式 release 流程时复用受控签名身份并明确版本/密钥管理，不能重新随机生成身份覆盖已有安装。
