# V0.7 当前开发版本

在已合并 V0.6 上增加打包中文/Latin 离线 OCR、逐项核实/专用草稿/重复提醒、集中本地洞察规则、盘前清单与周月报洞察。保留旧版全部功能；无新增 INTERNET 权限，原 V0.3 私钥与 pin 不替换。

[V0.7 实施计划](docs/V07-PLAN.md) · [OCR/规则/十六表迁移与备份说明](docs/V07-DATA-AND-REVIEW.md) · [真实验证记录](docs/V07-VALIDATION.md) · [手机逐项验收](docs/V07-PHONE-ACCEPTANCE.md)

版本 0.7.0 / code 7。正式 Room v6→v7 保留所有旧链；JSON schema 6 兼容格式 1–5，完整 ZIP 容器 1 保留全部图片/校验/事务安全。最新签名与 APK 下载以 V0.7 PR、Actions 和 SIGNING.txt 为准，固定 Secrets 未正确配置时仅临时测试包，不能保证覆盖升级。

以下保留已有功能和此前版本背景，历史验证/签名状态仅代表当时情况。

# 交易复盘 Android App

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

工程沿用现有架构与 Wrapper：Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose 插件 2.1.20、Compose BOM 2025.04.01、Room 2.7.2；minSdk 26、compile/targetSdk 35、CI JDK 17。版本 0.7.0，versionCode 7，applicationId com.tradereview.app。

[V0.6 实施计划](docs/V06-PLAN.md) · [计算、数据与签名配置](docs/V06-DATA-AND-REPORTS.md) · [V0.6 实际验证记录](docs/V06-VALIDATION.md)

Actions 实际执行 Debug/Android 测试 APK 编译、JVM 单元测试、Lint、API 29 模拟器数据库迁移/事务恢复/快捷录入/目录/草稿与 Compose 界面测试；所有任务成功才发布可用手机浏览器打开的 GitHub 预发布下载页。私有仓库需登录有访问权限的 GitHub 账号。CI APK/报告保留 30 天，Release 文件持久保留。

V0.3 已有长期证书 pin；本轮没有生成、读取或替换私钥。Secrets/Variables 管理 API 返回 403，最新 main 实际 CI 尚未启用固定签名。V0.6 依据 SIGNING.txt 交付明确标注的临时测试包，不能覆盖固定签名 V0.3；未来自动固定签名仍需所有者配置已有密钥的 Secrets，不能声称已经完成。安装前保存完整 JSON 备份；自行卸载会清空设备数据，随后可导入旧备份。密钥不得进入代码、PR 或日志。

## 构建

具备 Android SDK 的环境使用 JDK 17：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Base 缺少 SDK 时用 GitHub Actions，不需要完整 Android Studio。本地可执行 `python3 scripts/tests/test_signing_guard.py`、`python3 scripts/tests/test_signing_config.py`、`python3 scripts/tests/test_signed_payload.py` 与 `python3 scripts/tests/test_device_delivery.py`（共 16 项签名、配置、APK 内容及截图交付门禁测试，不处理私钥）。完整 CI 显式禁用 Gradle 构建缓存，以重新执行编译、测试和 Lint。

JSON 为明文，校验摘要不提供加密或身份认证。模拟器结果不等于真实手机测试；请以本轮 Actions 和验证文档中的实际结果为准。

历史方案与验证记录保留在 docs 中，包括 V0.1–V0.3；历史文档中的签名和版本状态仅描述当时情况。本轮以 V0.6 验证报告及 APK 自带的 SIGNING.txt 为准。
