# 交易复盘 V2.0 候选版

基于已合并V1.0的现有工程，重组为国内期货与普通A股个人交易管理：真实成交 → FIFO开平配对 → 当前持仓 → 独立风险金/策略 → 标的计划及持仓复盘 → 周月已实现盈亏。五页首页/交易/复盘/统计/账户；风险与策略固定顶部入口，备份设置仅ZIP/恢复/CSV。离线CNY新账本，无INTERNET、行情、浮盈亏、在线AI或CSV批量成交导入。

2.0.0/code21/com.tradereview.app；Room v8正式保留v1→v8迁移，JSON7/ZIP1兼容旧格式。旧16表、原盈亏/币种/recordKey/截图/其他业务原样保留，不推断旧持仓、不重复进入V2统计。金额用BigDecimal，费用分摊守恒，风险金不当现金。

V2 PR #10 与公开 CI 加固 PR #11 已合并。依 Issue #12 所有者授权，正式包从 V2 起固定使用新永久证书 `29b2f82887a0591d5b1d1f9ab148b90c0b658088530e3bcc1bd7e5fee64f2af9`，原 V0.3 指纹仅留历史证据。旧私钥不可恢复，**新证书不能覆盖安装旧 V0.3 签名应用**：先验证完整 ZIP 备份，再由所有者确认重装并恢复；不自动卸载或清除数据。四个 Environment Secrets 尚待所有者配置，未真实签名/真机验收，不宣称稳定发布。无凭据仅隔离 com.tradereview.app.rc.v200、2.0.0-rc.1 非调试 RC。

[永久签名迁移验证](docs/PERMANENT-SIGNING-V2-VALIDATION.md) · [公开仓库 CI 安全与手机操作](docs/PUBLIC-CI-SECURITY.md) · [实施计划](docs/V200-PLAN.md) · [实际验证/失败记录](docs/V200-VALIDATION.md) · [模型/计算/迁移/备份](docs/V200-DATA-AND-CALCULATIONS.md) · [手机签名配置](docs/V200-PHONE-SIGNING.md) · [手机验收](docs/V200-PHONE-ACCEPTANCE.md)

以下内容为历史版本，不代表V2页面或本轮CI结果。

# 交易复盘 V1.0 候选版

基于已合并 V0.8 的原生离线工程，保留全部交易、复盘、风险/R、日历筛选、截图、离线 OCR、智能洞察、周月报、目标、CSV 导出与 JSON/ZIP 恢复。不开发原 V0.9 CSV 成交导入、映射、自动配对。

正式目标 1.0.0 / code10 / com.tradereview.app；Room 仍 v7、JSON 6、ZIP 1，历史 recordKey、盈亏与完整迁移链不变。优化首页直接操作、可跳过引导、专业自适应/单色图标、48dp 操作和隐私说明。完整 ZIP 为主入口，真实导出后尽可能 SHA-256 回读；无法回读如实标注，取消选择不冒充完成。

同工程隔离 RC：com.tradereview.app.rc / 1.0.0-rc.1，非 debuggable、临时测试签名、数据独立，不保证长期覆盖更新。真正 Release 只允许原 V0.3 pin 的既有私钥，未配置不能回退随机 Debug 签名。稳定签名、必要验证与所有者真机验收未齐备时不能声称正式稳定发布。

[实施计划](docs/V100-PLAN.md) · [实际验证](docs/V100-VALIDATION.md) · [数据与备份](docs/V100-DATA-AND-BACKUP.md) · [手机配置原签名](docs/V100-PHONE-SIGNING.md) · [手机验收](docs/V100-PHONE-ACCEPTANCE.md)

以下为历史版本背景；历史测试和签名状态仅代表对应版本。

# V0.8 稳定性与长期升级

基于已合并 V0.7，保留全部离线交易、风险/R、截图、ZIP、报告、目标、OCR 和洞察功能。版本 0.8.0 / code 8 / com.tradereview.app；compile/target SDK 35，Room 仍 v7、JSON 6/ZIP 1，没有为了应用版本号改变数据库结构。

本轮加强原 V0.3 签名连续性门禁、恢复取消及删除标识校验、受限缩略图缓存、现有索引查询、统计一次排序/亏损段扫描与显示范围一致性。APK 保留全部 ABI 和中文/Latin OCR 模型，仅移除未用扩展图标和默认 IDE inspector；开发需要 inspector 时使用 `-PenableComposeTooling=true`。

[V0.8 PR #8](https://github.com/mlzx-xxj/trade-review-app/pull/8)提供最终提交的 CI、手机浏览器 APK、实际 API 29/35 截图和完整性能/OCR原始结果；不自动合并。固定 Secrets 尚未配置成功时仅临时测试包，不保证覆盖安装。真正原签名旧版升级与真机验收尚未执行，不以同版本模拟器进程重启替代。

[实施计划](docs/V08-PLAN.md) · [实际验证/失败记录](docs/V08-VALIDATION.md) · [数据安全与兼容](docs/V08-DATA-SAFETY.md) · [性能与OCR实测](docs/V08-PERFORMANCE-AND-OCR.md) · [仅用安卓手机配置原签名](docs/V08-PHONE-SIGNING.md) · [手机验收清单](docs/V08-PHONE-ACCEPTANCE.md)

以下保留 V0.1–V0.7 的功能与历史验证背景，历史结果仅代表当时的版本。

# V0.7 交易复盘

在已合并 V0.6 上增加打包中文/Latin 离线 OCR、逐项核实/专用草稿/重复提醒、集中本地洞察规则、盘前清单与周月报洞察。保留旧版全部功能；无新增 INTERNET 权限，原 V0.3 私钥与 pin 不替换。

[V0.7 实施计划](docs/V07-PLAN.md) · [OCR/规则/十六表迁移与备份说明](docs/V07-DATA-AND-REVIEW.md) · [真实验证记录](docs/V07-VALIDATION.md) · [手机逐项验收](docs/V07-PHONE-ACCEPTANCE.md)

版本 0.7.0 / code 7。正式 Room v6→v7 保留所有旧链；JSON schema 6 兼容格式 1–5，完整 ZIP 容器 1 保留全部图片/校验/事务安全。[V0.7 PR #7](https://github.com/mlzx-xxj/trade-review-app/pull/7)提供最终提交的 CI、APK 和截图链接，不自动合并。功能提交已实际通过 Debug/测试 APK 编译、133 JVM、81 模拟器测试，Lint 0 错误/17 警告，详见验证记录。实际固定 Secrets 未启用，交付临时测试包，不能保证覆盖升级；真机验收待执行。

以下保留已有功能和此前版本背景，历史验证/签名状态仅代表当时情况。

# V0.1–V0.6 历史功能背景

V0.6：Kotlin + Jetpack Compose + Material 3 + Room，中文卡片界面，离线运行，无 INTERNET 权限。

- 保留首页、交易记录、勾选复盘、统计分析及 V0.2 JSON/事务恢复/CSV 功能。
- 快速录入：日期、市场、常用品种、方向、明确盈利/亏损/持平、金额与策略；复制上一笔、保存并继续，本地自动草稿。
- 详细选项折叠：仓位、情绪、多选错误/纪律、复盘完成与备注。
- 六类自定义标签：新增、重命名、收藏、隐藏、排序；稳定 ID 保留历史关联，统计/导出使用当前名称。
- 品种收藏、中文/代码搜索、自定义品种、排序与最近使用，预设 15 个国内期货品种，不接实时行情。
- 折叠专业录入：线性多空精确盈亏、成本组成、初始止损/手填风险、R、计划内外、用户核实合约模板；手动模式默认，未知数据不填 0。
- 各币种参考风险规则，超限确认仍保存真实事实与稳定快照；有效样本 R/期望、策略/品种、计划与纪律分析。
- 跟随系统/浅色/深色模式；首页快捷记账，保存后首页与统计自动刷新。
- 交易日历、当日详情和净盈亏热力图，周一为首日，可查看当月或近三个月；无交易、持平和计划日期清晰区分。
- 统一日期/市场/品种/方向/结果/标签/复盘组合筛选，标签任意/全部匹配、常用方案与历史隐藏标签。
- 精确分币种统计、连续盈亏/最大回撤、策略/品种/方向/市场/错误/纪律/情绪比较与可点击图表，后台计算，小样本提示。
- 绿盈红亏或红盈绿亏偏好统一用于日历、金额与图表。
- 交易截图：系统选择器、私有存档、四类证据、说明/缩略图/大图切换、每笔十张，后台压缩并去位置信息。
- 本地周报/月报、分币种盈亏与有效 R、前期对照、错误排行、评分趋势；并行纪律目标、明确分母/未知状态、归档与真实历史。
- Room 正式 v5→v6（保留全部旧链）；完整 ZIP 格式 1 含十二表与全部截图，JSON 格式 5 明确不含图片，兼容 V0.2–V0.5 格式 1/2/3/4，严格预览/二次确认与文件/数据库协调恢复。

以下为历史技术配置（当前版本见顶部 V1.0）：Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose 插件 2.1.20、Compose BOM 2025.04.01、Room 2.7.2；minSdk 26、compile/targetSdk 35、CI JDK 17。版本 0.7.0，versionCode 7，applicationId com.tradereview.app。

[V0.6 实施计划](docs/V06-PLAN.md) · [计算、数据与签名配置](docs/V06-DATA-AND-REPORTS.md) · [V0.6 实际验证记录](docs/V06-VALIDATION.md)

Actions 实际执行 Debug/Android 测试 APK 编译、JVM 单元测试、Lint、API 29 模拟器数据库迁移/事务恢复/快捷录入/目录/草稿与 Compose 界面测试；所有任务成功才发布可用手机浏览器打开的 GitHub 预发布下载页。私有仓库需登录有访问权限的 GitHub 账号。CI APK/报告保留 30 天，Release 文件持久保留。

V0.3 已有长期证书 pin；本轮没有生成、读取或替换私钥。Secrets/Variables 管理 API 返回 403，最新 main 实际 CI 尚未启用固定签名。V0.6 依据 SIGNING.txt 交付明确标注的临时测试包，不能覆盖固定签名 V0.3；未来自动固定签名仍需所有者配置已有密钥的 Secrets，不能声称已经完成。安装前保存完整 JSON 备份；自行卸载会清空设备数据，随后可导入旧备份。密钥不得进入代码、PR 或日志。

## 当前工程构建

具备 Android SDK 的环境使用 JDK 17：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRc :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRc :app:lintRelease
./gradlew :app:connectedDebugAndroidTest
```

Base 缺少 SDK 时用 GitHub Actions，不需要完整 Android Studio。Base 可执行 `python3 -m unittest discover -s scripts/tests -p 'test_*.py'`（含当前工作流隔离、发布/交付和模拟器故障回归；实际数量见对应 CI，不代表 Android 编译或真实固定密钥验签）。RC 自动使用临时测试身份；正式 Release 没有新永久签名配置时只编译 unsigned 输出，绝不作为可安装正式包发布。完整 CI 复用编译缓存，但 JVM Test 禁用 up-to-date 和测试输出缓存以实际执行测试体；PR 只读，新永久签名和发布仅允许通过完整验证的 main。签名材料必须由所有者放入仅允许 main 的受保护 Environment，不能配置为 Repository Secrets，详见当前安全指南。模拟器启动后保留20秒稳定时间，应用测试前核对平台dumpsys记录的ANR及崩溃日志，两份证据都保留；故障立即失败，不忽略对话框。其他应用自行打印“ANR in”文本不能替代系统实际记录。

JSON 为明文，校验摘要不提供加密或身份认证。模拟器结果不等于真实手机测试；请以本轮 Actions 和验证文档中的实际结果为准。

历史方案与验证记录保留在 docs 中，包括 V0.1–V0.6；历史文档中的签名和版本状态仅描述当时情况。当前以 V2 验证报告、Issue #12、当前 PR 与对应候选 APK 的 SIGNING/RC-IDENTITY/SHA256SUMS 为准。
