# V0.2 实际验证记录

## 阶段与执行范围

本轮使用原 Base 环境，没有创建新环境或安装 Android Studio。当前网络策略为 unrestricted/enforced，实际 HTTPS 连接 raw.githubusercontent.com、services.gradle.org、dl.google.com 成功。Base 无 Android SDK，本地未声称完成 Android 构建或真机升级。

- 本地 SQLite：从提交的 Room v1 schema 建表，插入旧交易与每日日志，执行正式 Migration 的 SQL；旧字段与日志保留，新增 32 位标识及唯一索引。此烟雾检查不代替 Room 测试。
- 本地静态检查：git diff --check、工作流 YAML 与发布依赖、无 INTERNET、无 destructive migration、签名脚本 bash 语法，均实际执行。
- 本地签名发布检查：3 项（无旧密钥仅测试包、匹配证书升级包、错证书阻止所有 APK 发布），全部通过；使用公开证书测试桩，不生成/处理私钥，不声称完成真实手机升级。

## 首轮完整 GitHub Actions

[PR CI 38121823529](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38121823529)，源码 `f38ff723f50349151d0d092cb2aedc29a8fca556`：build / device-tests / publish 全部成功。

| 实际执行项 | 报告结果 |
| --- | --- |
| assembleDebug / assembleDebugAndroidTest | 成功 |
| BackupCodecTest | 9 通过，0 失败/错误/跳过 |
| TradeCsvTest | 3 通过，0 失败/错误/跳过 |
| 原有 AnalyticsTest | 6 通过，0 失败/错误/跳过 |
| Android Lint | 0 错误，13 警告 |
| API 29 x86_64 模拟器 | 15 通过，0 失败/错误/跳过 |

设备 XML 时间为 `2026-10-11T07:42:10`：BackupRepositoryTest 8 项、MigrationTest 2 项、TradeDatabaseTest 1 项、BackupSettingsTest 2 项、NavigationTest 2 项。包含真实 SQLite trigger 故障，验证交易与日志的事务回滚；迁移使用 Room MigrationTestHelper 创建和校验正式 v1/v2 schema。设置页测试通过 ContentResolver 读写文件、预览及第二次确认；未声称覆盖所有厂家文件选择器或云盘提供方。

Lint 的 13 条警告为 OldTargetApi、AppBundleLocaleChanges 和 11 条 GradleDependency 更新提示，本轮保持 MVP 已验证的兼容技术版本。CI 另有 Actions Node runtime 和 ubuntu-latest 镜像更新提示，无构建错误。

编译器生成 v2 schema（identityHash `92a527fba00712746ab2126b933f0eea`），CI 自动提交 `9a72af7`；本地取回并保持 v1 schema 原样。随后金额校验收紧为规范两位小数，补充拒绝空白金额与签名发布检查。最终代码验证提交为下节列出的 7db4057。

同提交的重复 push 运行 `38121774669` 被主动取消以避免重复验证；自动 schema 提交产生的 `38122202028` 为 action_required，未计为通过。最新人工源提交重新触发正常 PR 验证，后续功能分支不重复触发 push，main/PR/手动运行均保留完整验证。

报告直接命令行下载曾被 artifact 存储端拒绝；改用已授权 GitHub 连接器成功下载 XML 报告和 schema，没有改变代理或绕过策略。

## 最终代码验证与下载

[最终完整 CI 38122646957](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38122646957)，源码与工作流提交 **`7db4057aef40620366d68c6229ede1aface083e4`**。build、device-tests、publish 三项任务全部 success。

- Debug APK 与测试 APK 编译成功。
- 18 项 JVM 测试全部通过（9 JSON + 3 CSV + 6 原有统计），0 失败、错误或跳过；XML 时间 `2026-10-11T07:51:23`。
- 3 项签名发布门禁测试在 Actions 实际执行并全部通过；真实 APK 另经 apksigner verify 校验证书。
- Android Lint 0 错误、13 警告，与首轮类型相同。
- API 29 模拟器实际运行 16 项，0 失败、错误或跳过：恢复 8、迁移 2、持久化 1、设置/确认/非法文件提示 3、四页导航及交易增改删 2。包括先滚动至下方再导入非法文件、检查错误提示可见且原记录未改。
- Room 编译器输出的 v2 schema 与提交文件逐字节相同。v1 schema 与 Manifest 相比原 MVP 无改动，没有删除原工程文件。
- 最后仅更新本验证文档，使用 `[skip ci]` 避免重复执行未受影响的 Android 测试；代码验证以以上提交为准。

[手机浏览器下载页](https://github.com/mlzx-xxj/trade-review-app/releases/tag/v02-debug-38122646957-1)（私有仓库需登录）。产物 `TradeReview-v0.2-test.apk` **仅供新测试设备，不是 V0.1 覆盖升级包**；对应校验值与 SIGNING.txt 一同发布。

APK 实际下载后 SHA-256 校验一致：

```text
39809a73e0ca78da89b572796ded81995495b565211a0792dd32bdcda0ca550f
```

APK 大小 17,615,552 字节；证书 SHA-256 `9ccaa3056488221fc4521ab13dcf7c2988f06396ed4fb0c482c3537c168b92c2`，与旧 V0.1 两个已知证书不同，所以没有将此产物发布为升级包。测试临时签名可能随 CI 变化，不应作为长期使用的稳定签名。

最终报告产物：`verification-reports`（11695588301）、`android-test-reports`（11696405924）、APK（11695773045）。已通过 GitHub 连接器下载并检查 XML、schema、APK 和校验文件。

中间提交 `2867804`、`1067fbd` 的运行被后续 UI 修正自动取消；不计为完整通过。最终没有编译、Lint 或设备测试失败。没有执行真实手机覆盖升级，也没有部署或恢复用户实际交易数据。

## 签名限制

实际旧 APK 证书、缓存未保存日志和 Secrets 权限不足记录见 [签名方案](SIGNING-AND-UPGRADES.md)。缺少已安装 V0.1 对应私钥，无法提供覆盖升级包，原地手机升级演练未执行。无密钥的 CI 仅发布新设备测试 APK，Release、文件名和 SIGNING.txt 均有明确限制。不要卸载已有 V0.1 或清除数据。
