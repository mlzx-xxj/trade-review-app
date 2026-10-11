# 交易复盘 · Android 离线 App（V0.2）

原生 Kotlin + Jetpack Compose + Room。中文卡片界面，Android 8.0 及以上可用。所有交易保存在手机本地，应用没有联网权限、登录或分析 SDK，关闭系统云备份及设备迁移。

## 功能

- **首页**：净盈亏、胜率、复盘完成率、今日计划状态、最近交易；示例数据需手动添加。
- **交易记录**：日期、股票/期货/外汇/数字资产、常用品种与自定义品种、做多/做空、结算币种、净盈亏、仓位滑块；支持编辑、删除、市场与待复盘筛选。
- **勾选复盘**：按日期保存盘前判断、允许策略、最大仓位、准备清单；盘后情绪、执行纪律、错误、改进标签及 1–5 分评分。
- **统计分析**：全部/近7天/近30天胜率、累计净盈亏曲线、单笔交易错误分布、策略表现。人民币、美元与 USDT 分别统计，不进行币种换算。

只记录已平仓交易，净盈亏由用户输入并已扣除费用。金额使用 BigDecimal，支持 12 位整数、2 位小数；持平交易计入胜率分母。仓位是本笔占账户的百分比。统计错误来自**交易详情**的标签，每日日志中的错误用于当天总结。

## 手机下载

1. 用安卓浏览器登录有此私有仓库访问权的 GitHub 账号。
2. 打开 [Releases](https://github.com/mlzx-xxj/trade-review-app/releases)，选择 V0.2 预发布，先阅读签名与安装限制。
3. 展开 **Assets**，新测试设备下载 `TradeReview-v0.2-test.apk`；只有找到匹配旧私钥并通过证书校验时才提供 `TradeReview-v0.2-upgrade.apk`。按手机提示允许浏览器安装应用。
4. 或在 [Actions](https://github.com/mlzx-xxj/trade-review-app/actions/workflows/android.yml) 成功运行底部的 **Artifacts** 下载 APK ZIP 并解压。Actions 产物保留 30 天，Release 附件保留直到删除。

Debug APK 为试用包。V0.2 支持完整 JSON 备份，但已安装的 V0.1 没有此功能。旧构建的调试密钥未正确缓存，且两个旧 APK 证书不同；缺少对应旧私钥时不能覆盖升级。**请勿为安装测试包卸载已安装 V0.1，卸载会删除全部本地记录。**正式签名密钥需另行授权，本轮没有生成或更换。

## 技术版本

| 工具 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin / Compose Compiler 插件 | 2.1.20 |
| KSP | 2.1.20-1.0.32 |
| Compose BOM | 2025.04.01 |
| Room | 2.7.2 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |

## 构建与验证

有 Android SDK 的机器可运行：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest  # 需要连接手机或模拟器
```

无 SDK 的 Base 环境继续编辑源码，由 GitHub Actions 提供 JDK 和 SDK，不安装 Android Studio。CI 在缺少 Wrapper JAR 时通过官方 Gradle 生成完整 Wrapper并设置官方发行包 SHA-256，随后验证 JAR。仓库已包含完整 Wrapper 与 Room v1/v2 schemas；新的 schema 随代码变更提交，CI 同时生成并上传以核对。每次运行上传 Wrapper、APK、测试和 Lint 报告；全部验证任务成功后才创建带 APK 的预发布；测试包和证书匹配的升级包有不同的名称及明确标识。

- `push` 至 main、`pull_request`、`workflow_dispatch` 均执行 Debug / 测试 APK 编译、JVM 单元测试、Android Lint、API 29 模拟器测试并上传报告。
- 同仓库来源的完整验证成功后发布 APK；外部 fork 的 PR 不接收 Secrets，也不发布 Release。

工作流合并至 main 后即可在 Actions 界面手动执行；合并前新 PR 已运行完整验证；功能分支不重复触发相同的 push 构建。测试 APK 编译也在前置构建中执行，编译错误会在启动模拟器前被发现。

JUnit 覆盖精确金额、输入限制、币种隔离、持平/空样本、按日排序曲线、策略与错误计数。Android 测试覆盖 Room 重开后读回、修改/删除、每日计划与复盘合并、四页导航，以及通过界面新增/修改/删除交易。V0.2 另测 JSON 往返与非法拒绝、CSV 中文转义、稳定标识合并、实际 SQL 故障回滚、Room v1→v2 迁移和设置页二次确认。测试结果以 Actions 的实际运行、[MVP 验证记录](docs/VALIDATION.md) 和 [V0.2 验证记录](docs/V02-VALIDATION.md) 为准。

[项目结构与阶段计划](docs/PLAN.md)

## V0.2：本地备份与恢复

设置入口新增完整 JSON 备份、校验预览与二次确认恢复、去重合并／覆盖、交易 CSV 导出和版本信息；Room 正式 v1→v2 Migration 保留既有数据。详见 [V0.2 数据安全](docs/V02-DATA-SAFETY.md)。

**已有 V0.1 用户：旧构建签名未被正确缓存，当前缺少对应旧私钥，无法保证覆盖升级。请勿卸载已有应用来安装 V0.2 测试包。** Actions 会拒绝签名不匹配的升级发布，并将无旧密钥的 APK 明确标为新设备测试。详见 [签名与升级方案](docs/SIGNING-AND-UPGRADES.md)。
