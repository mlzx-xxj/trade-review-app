# V0.6 实际验证记录

基础 main 87f0b7d（PR #5 已合并），独立分支 codex/v06-review-reports。Base 无 Android SDK，未运行本地 Android 编译、手机测试，也未安装 Android Studio 或新建环境。

本地实际执行：签名门禁 6、配置完整性 3、签名 payload 4、模拟器交付 3，共 16 项通过。该结果只验证发布脚本，不代替 Android 编译/JVM/模拟器。

已添加报告日期/跨年/闰月、分币种/有效 R/未知纪律、目标分母/精度/归档/文字目标、ZIP 路径/重复/超限/摘要，以及原格式兼容和新增数据往返测试。AndroidTest 新增真实 ContentResolver 图片/EXIF/损坏/权限/容量/删除、ZIP 十二表与文件协调回滚、v5→v6 与完整旧链，以及截图/报告/目标/备份界面。未执行结果不标为通过，后续以 Actions 原始 XML 更新。

工作流重新执行 Debug/测试 APK、JVM、Lint 和 connectedDebugAndroidTest，显式 --no-build-cache；发布只在全部任务成功且原 13 张与新增 5 张截图齐全后进行，保留失败退出码。最终提交包含文档也须完整 CI，无 skip ci。

签名以实际 APK 与 SIGNING.txt 为准。前置 main 的真实日志仍为 Fixed V0.3 key not configured；保留原 pin，本轮不读取、生成或替换长期私钥。测试环境/数据/限制说明见 V06-DATA-AND-REPORTS.md。

首轮 89a7ba4，[Actions 38198694976](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38198694976)：Room KSP 已实际生成十二表 v6 schema，但主 Kotlin 编译失败，Android 公开 SDK 无 OsConstants.O_DIRECTORY。改用 O_RDONLY 打开已知私有目录，保留 fsync；正式 schema 来自该轮真实 KSP 产物。本轮 JVM/Lint/模拟器未执行，发布跳过，不当作通过。

第二轮 627406d，[Actions 38199041760](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38199041760)：Debug/测试 APK 编译成功，原始 JVM XML 101 项、0 失败/错误/跳过；Lint XML 0 错误/17 警告（ExifInterface 1、OldTargetApi 1、AppBundleLocaleChanges 1、GradleDependency 11、UsableSpace 2、UseKtx 1），16 项发布门禁通过，实际 KSP v6 schema 与提交一致。实际 APK 0.6.0 / 6 无 INTERNET/广泛存储权限，独立 apksigner v2 单 signer 验证成功，SIGNING.txt 明确 SecretsConfigured=false、临时 runner 签名。

该轮模拟器进程中途崩溃，XML 只记录 35 项：32 通过、3 失败、0 错误/跳过，其余用例尚未执行，无截图，发布跳过。两项失败为旧 JSON 重导出格式/摘要预期未更新，已保留旧 golden 导入摘要检查并验证升级格式完整往返。测试 APK 的独立 Provider 进程缺少目标 APK 中的 Kotlin 标准库，导致 NoClassDefFoundError；改为只依赖 Android/Java 的测试夹具，生产 Kotlin 架构/权限不变。另补充图片奇数像素采样上限、缺失文件 ZIP 恢复及 ZIP 文件数/解压声明总量边界，等待新的完整 CI。
