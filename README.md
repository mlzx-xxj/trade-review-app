# 交易复盘 · Android 离线 MVP

原生 Kotlin + Jetpack Compose + Room。中文卡片界面，Android 8.0 及以上可用。所有交易保存在手机本地，应用没有联网权限、登录或分析 SDK，关闭系统云备份及设备迁移。

## 功能

- **首页**：净盈亏、胜率、复盘完成率、今日计划状态、最近交易；示例数据需手动添加。
- **交易记录**：日期、股票/期货/外汇/数字资产、常用品种与自定义品种、做多/做空、结算币种、净盈亏、仓位滑块；支持编辑、删除、市场与待复盘筛选。
- **勾选复盘**：按日期保存盘前判断、允许策略、最大仓位、准备清单；盘后情绪、执行纪律、错误、改进标签及 1–5 分评分。
- **统计分析**：全部/近7天/近30天胜率、累计净盈亏曲线、单笔交易错误分布、策略表现。人民币、美元与 USDT 分别统计，不进行币种换算。

只记录已平仓交易，净盈亏由用户输入并已扣除费用。金额使用 BigDecimal，支持 12 位整数、2 位小数；持平交易计入胜率分母。仓位是本笔占账户的百分比。统计错误来自**交易详情**的标签，每日日志中的错误用于当天总结。

## 手机下载

1. 用安卓浏览器登录有此私有仓库访问权的 GitHub 账号。
2. 打开 [Releases](../../releases)，选择最新的“交易复盘 Debug”预发布。
3. 展开 **Assets**，直接下载 `TradeReview-debug.apk`，按手机提示允许浏览器安装应用。
4. 或在 [Actions](../../actions/workflows/android.yml) 成功运行底部的 **Artifacts** 下载 APK ZIP 并解压。Actions 产物保留 30 天，Release 附件保留直到删除。

Debug APK 为试用包。卸载会删除全部本地记录，MVP 暂无导出备份。CI 缓存调试签名保证通常可覆盖更新；缓存被清除后签名可能变化，此时覆盖安装会被 Android 拒绝。应用发布及正式签名不在本 MVP 范围内。

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
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest  # 需要连接手机或模拟器
```

无 SDK 的 Base 环境继续编辑源码，由 GitHub Actions 提供 JDK 和 SDK，不安装 Android Studio。CI 在缺少 Wrapper JAR 时通过官方 Gradle 生成完整 Wrapper并设置官方发行包 SHA-256，随后验证 JAR。源码分支 `codex/**` 成功编译后自动提交生成的 Wrapper 和 Room v1 schema，后续构建使用仓库中的完整 Wrapper。每次运行上传 Wrapper、APK、测试和 Lint 报告；源码分支推送或手动运行还会创建带 APK 的预发布。

- `push` 至 main 或 codex 分支：编译 Debug APK、JVM 单元测试、Android Lint、上传产物和创建预发布。
- `pull_request`：上述验证加 Android API 29 模拟器上的 Room 持久化与 Compose 导航测试。
- `workflow_dispatch`：手动执行完整验证并上传可下载 APK。

JUnit 覆盖精确金额、输入限制、币种隔离、持平/空样本、按日排序曲线、策略与错误计数。Android 测试覆盖 Room 重开后读回、修改/删除、每日计划与复盘合并，以及四页导航。测试结果以 Actions 的实际运行和 [验证记录](docs/VALIDATION.md) 为准。

[项目结构与阶段计划](docs/PLAN.md)
