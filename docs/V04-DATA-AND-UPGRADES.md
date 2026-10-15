# V0.4 数据、统计与升级

> Public 仓库安全更新：本文的旧 Repository Secrets/旧分支签名步骤仅作历史记录，请勿执行。当前必须使用仅允许 main 的受保护 Environment，并移除 Repository 签名副本；见 [当前 CI 安全及手机操作指南](PUBLIC-CI-SECURITY.md)。旧 configure-v03-signing.sh 已停用，不读取私有材料。

0.4.0 / versionCode 4，applicationId 仍为 com.tradereview.app。保留原四页、离线录入、标签、品种、草稿、SAF 备份/恢复和 CSV；新增日历入口。不新增 INTERNET 或宽泛存储权限，不连接行情服务。

## 数据与备份

Room v4 的必要变化是 app_settings 新增 `pnlPalette TEXT NOT NULL DEFAULT 'green_profit'`，新增 saved_filters（稳定 UUID、名称、币种和严格序列化的筛选条件）。`MIGRATION_3_4` 只新增列/表，保留 trades、daily_reviews、tags、instruments 和 entry_drafts 原内容；保留 v1→v2→v3→v4 全链路，不使用 destructive migration。日历和统计均为派生结果，不复制持久化盈亏。

JSON schemaVersion=3，包含原六类数据、配色及 savedFilters，数量和 SHA-256 纳入所有新字段。保留 16 MiB、数组上限、严格 UTF-8、重复键/未知字段/类型/日期/金额/稳定 ID 和关联校验；筛选方案最多 100 个，每类条件最多 100 项。筛选中的自定义/隐藏标签仍按 (kind,id) 查找，重命名不会断开历史关联。历史品种仍使用交易原 symbol。

继续导入 V0.2 schema=1 与 V0.3 schema=2。旧备份恢复到默认绿盈红亏和空常用筛选。旧格式 2 的 Gson 反射序列化在 JVM 和 ART 上字段顺序不同；在完整字段与内容校验后，分别验证两种原始校验布局，不放宽校验或跳过摘要。新格式所有对象使用显式字段顺序，跨平台导出一致。

覆盖恢复在单一事务内替换全部七张表；合并按稳定 ID 去重，保留本地名称、设置和同 ID 筛选方案，并补入新方案/目录/交易/日志。预览显示筛选数量和配色，确认后才写入；预览后任何相关表变化均拒绝旧确认。新筛选表写入失败也回滚交易、日志、目录、草稿、设置和筛选。JSON 仍为明文，摘要不提供加密或身份认证。V0.3 无法读取新格式 3；从 V0.4 降级到旧版需另行管理备份和安装，不能直接打开 v4 数据库。

## 统计口径

- 交易记录与统计页面共用一份筛选状态，列表、数字及图表来自同一次后台计算。币种单选，人民币、美元、USDT 从不直接相加。日历按所选币种展示全部交易，日详情分别列出当天所有币种。
- 日期都是 LocalDate/epochDay，月历周一开始；本周为本周一至今天，本月为月初至今天，近 30 天包含今天。自定义范围两端包含；常用方案保存相对日期模式，应用时重新计算。
- 不同条件之间 AND，每类多选默认匹配任意，可切换匹配全部。单笔只有一个策略/情绪，选多个且匹配全部会得到空样本。隐藏且有历史使用的标签仍可筛选。清除条件保留当前币种。
- 胜率为盈利笔数 / 全部笔数，持平在分母中；空样本显示“—”且不生成图表数据。平均金额与盈亏比用 BigDecimal 计算并按展示精度舍入，缺少盈利或亏损样本时盈亏比未定义。
- 连续盈亏按日期、数据库编号排序，持平中断。最大回撤以筛选后的同币种已平仓交易累计净收益 0 为起点，历史高点减后续低点的最大金额差；包含同日录入次序中的回撤，不含浮动盈亏、初始权益和回撤百分比。原记录没有成交时间，不能推断同日真实成交次序。
- 日柱和累计曲线按交易日排列，每周周一汇总，边界周只汇总当前筛选范围。坐标使用浮点仅用于绘图，金额与标签始终使用 BigDecimal；大量点保留分段极值，点击仍查询完整数据。
- 同一错误在同一笔交易只计一次；多个标签可关联同一交易，各行不能相加当作总笔数。按错误/情绪/纪律分组只显示统计相关性，不推断因果。每组展示样本量，少于 20 笔提示不稳定，20 为界面提示阈值，并非统计显著性判断。
- 每日执行评分来自已保存的盘后复盘；只有日期条件时显示范围内日志，有交易条件时进一步限定为匹配交易的日期。日志不区分币种，不将评分当作某币种独有指标。

## 签名与 APK

继续使用 [V0.3 公开证书 pin](V03-CERTIFICATE-SHA256.txt)：`30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c`。本轮不生成或替换私钥，也没有读取旧私钥/密码。当前 GitHub 管理 API 返回 403；实际构建会检查 Secrets 是否为空，四项完整时使用原 keystore，否则空配置只生成 `TradeReview-v0.4-test.apk`，部分配置直接失败。固定包必须同时匹配仓库变量和已提交的 V0.3 pin，恰好一个 signer，否则拒绝发布。

所有者需在仓库 **Settings → Secrets and variables → Actions** 中配置已有 V0.3 密钥资料：

1. Secrets：`TRADE_REVIEW_KEYSTORE_BASE64`、`TRADE_REVIEW_KEYSTORE_PASSWORD`、`TRADE_REVIEW_KEY_ALIAS`、`TRADE_REVIEW_KEY_PASSWORD`。使用已存在的长期 keystore，不生成新 key；不要在 PR、日志或聊天中发送值。
2. Variables：`TRADE_REVIEW_UPGRADE_CERT_SHA256`，值为上面的公开 pin。
3. 可在可信电脑用有管理权限的 GitHub CLI 执行现有 `scripts/configure-v03-signing.sh /你的安全路径/trade-review-v03`；脚本通过 body-file 读取既有资料，不应开启 shell tracing。
4. 手动运行 Android CI，保留外部 APK blob 输入为空。只有 `SIGNING.txt` 表明 `GitHubActionsSecretsConfigured=true`、证书为既有 pin，且全部测试通过，才算完成新的自动固定签名验证。

Secrets 尚未配置成功时，临时 V0.4 不能覆盖固定签名 V0.3，也不能保证后续临时包覆盖升级。安装前先另存完整 JSON 备份；如果自行卸载旧版，设备数据会被清空，随后可恢复旧备份。固定签名配置成功后，同 applicationId、同私钥、递增 versionCode 及正式 Room 迁移满足未来覆盖升级要求；本轮不声称已经真机安装或升级验证。

CI 成功后才发布 GitHub 预发布 APK、SIGNING.txt、SHA256SUMS 和模拟器截图压缩包。截图使用测试生成的合成数据，不是真实账户。私有仓库手机浏览器需登录有权限的 GitHub 账号；Actions artifact 保留 30 天，Release 文件保留。
