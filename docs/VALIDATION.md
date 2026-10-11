# 实际验证记录

## Base 环境

- 已使用现有 Base 工作区，未创建新环境，未安装 Android Studio。
- 已检查网络策略：文件快照为 unrestricted，运行时状态 unknown；环境代理为 proxy:8080。
- 首次 Wrapper 下载报 curl (7) 无法连接代理。两次额外网络授权请求均被用户中止，因此没有确认获得执行联网命令的授权；三个目标域名的连通性测试没有完成。不修改代理、不绕过策略、不重复下载。
- 没有 Android SDK 或 Kotlin 编译器。本地未执行 Android 编译、JVM 测试或模拟器测试。
- 已执行并通过 XML 语法、工程骨架、Manifest 无 INTERNET 权限且禁用备份的静态检查。这些检查不等于 Android 编译通过。
- 第二阶段已执行：10 个 Kotlin 源文件和 3 个测试文件存在性、5 个 XML 解析、4 项云端检查任务声明、GitHub Actions YAML 解析；结果通过。源码完整性检查不是 Kotlin 编译或 JUnit 运行。
- 第三阶段已执行：`bash -n scripts/generate-wrapper.sh`、`sh -n gradlew`、`git diff --check`；结果通过。官方 Wrapper JAR 生成和 Android 构建留给云端执行。

## GitHub Actions

- 初次运行 #2（https://github.com/mlzx-xxj/trade-review-app/actions/runs/38105221389）实际失败在 SDK 初始化，原因是 setup-android 默认安装已下架的 `tools` 包；日志：`Failed to find package 'tools'`。尚未进入应用编译。
- 已改为仅安装 `platform-tools`，同时使用独立 command-line tools；两项任务均修复，并更新 checkout/setup-java 至 Node 24 对应的 v5。
- 后续编译及测试结果待实际运行后填写。
