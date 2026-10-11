# 交易复盘 Android App

V0.3：Kotlin + Jetpack Compose + Material 3 + Room，中文卡片界面，离线运行，无 INTERNET 权限。

- 保留首页、交易记录、勾选复盘、统计分析及 V0.2 JSON/事务恢复/CSV 功能。
- 快速录入：日期、市场、常用品种、方向、明确盈利/亏损/持平、金额与策略；复制上一笔、保存并继续，本地自动草稿。
- 详细选项折叠：仓位、情绪、多选错误/纪律、复盘完成与备注。
- 六类自定义标签：新增、重命名、收藏、隐藏、排序；稳定 ID 保留历史关联，统计/导出使用当前名称。
- 品种收藏、中文/代码搜索、自定义品种、排序与最近使用，预设 15 个国内期货品种，不接实时行情。
- 跟随系统/浅色/深色模式；首页快捷记账，保存后首页与统计自动刷新。
- Room 正式 v2→v3（保留 v1→v2）；备份格式 schemaVersion 2，兼容 V0.2 schemaVersion 1，覆盖全部新增数据。

工程沿用现有架构与 Wrapper：Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose 插件 2.1.20、Compose BOM 2025.04.01、Room 2.7.2；minSdk 26、compile/targetSdk 35、CI JDK 17。版本 0.3.0，versionCode 3，applicationId com.tradereview.app。

[实施计划](docs/V03-PLAN.md) · [数据与稳定签名配置](docs/V03-DATA-AND-UPGRADES.md) · [V0.2 已执行验证记录](docs/V02-VALIDATION.md)

Actions 实际执行 Debug/Android 测试 APK 编译、JVM 单元测试、Lint、API 29 模拟器数据库迁移/事务恢复/快捷录入/目录/草稿与 Compose 界面测试；所有任务成功才发布可用手机浏览器打开的 GitHub 预发布下载页。私有仓库需登录有访问权限的 GitHub 账号。CI APK/报告保留 30 天，Release 文件持久保留。

固定签名 Secrets 尚未验证配置成功时只交付临时测试包，不承诺长期覆盖升级。用户可手动卸载旧测试版再安装；卸载清空设备数据。V0.3 新数据请定期备份，切换固定签名前保存完整 JSON。密钥不入 GitHub 代码，不在聊天中发送。固定 Secrets 和证书 pin 的具体步骤见上述数据/签名文档。

## 构建

具备 Android SDK 的环境使用 JDK 17：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Base 缺少 SDK 时用 GitHub Actions，不需要完整 Android Studio。本地可执行签名门禁测试 `python3 scripts/tests/test_signing_guard.py`（使用合成证书，不处理私钥）。

JSON 为明文，校验摘要不提供加密或身份认证。模拟器结果不等于真实手机测试；请以本轮 Actions 和验证文档中的实际结果为准。

历史方案与实际验证保留在 `docs/PLAN.md`、`docs/VALIDATION.md`、`docs/V02-DATA-SAFETY.md` 和 `docs/V02-VALIDATION.md`；这些文档中的旧签名限制是当时的历史状态，本轮安装策略以上述 V0.3 说明为准。
