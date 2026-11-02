# V2.2.0 发布准备检查

基线：`c02288d35d8b89d6b5fe57c798b9f104c52fc57c`（PR #24、#25 已合并）。本次只同步版本与校验断言，并修复已定位的测试滚动操作；没有修改生产 Kotlin、财务公式、数据库、备份格式、签名流程或 CI 工作流。

## 构建身份

| 类型 | applicationId | versionName / versionCode | 签名要求 |
| --- | --- | --- | --- |
| 正式目标 | com.tradereview.app | 2.2.0 / 24 | 既有受保护 main 永久证书 |
| 隔离 RC | com.tradereview.app.rc.v200 | 2.2.0-rc.1 / 24 | 既有临时测试签名；不是正式升级包 |

永久证书 SHA-256：`29b2f82887a0591d5b1d1f9ab148b90c0b658088530e3bcc1bd7e5fee64f2af9`。未读取密钥、创建新永久证书或修改任何 Release/tag；保留 `v200` 目录、RC 包名和传输文件名，以免扩大改动。实际新 APK 身份与下载地址以本 PR 同 SHA 的 CI 产物为准，不能由配置值推断已经生成正式签名包。

## 原始失败与修正

[main CI 40279164966](https://github.com/mlzx-xxj/trade-review-app/actions/runs/40279164966)，attempt 1，head SHA 等于基线；构建/JVM/Lint/安全检查成功，设备失败使永久签名及后续发布门禁跳过。

- [API29 Job](https://github.com/mlzx-xxj/trade-review-app/actions/runs/40279164966/job/121429876770)：149 项运行完成，唯一失败为合约快捷入口用例，06:36:30 UTC 报 `performScrollTo()` 找不到 `i2701 · 铁矿石参数`。
- [API35 Job](https://github.com/mlzx-xxj/trade-review-app/actions/runs/40279164966/job/121429876791)：同一用例在 06:35:18 UTC 报相同节点缺失；另一个统计/账户/ZIP 用例在 06:34:58 UTC 报 `ComposeTimeoutException`（30000 ms）。邻近有 ColorBuffer 错误，但日志不能确定超时发生阶段或因果；原失败 ZIP 已不在当前 artifacts 列表，缺少当时的语义树/截图，根因未确定，保留为阻塞，不延长超时、不改生产代码。
- 已定位的合约测试改用 `v2-contract-rules.performScrollToNode(contract-rule-${id})` 先组合实际卡片，再在卡片内滚动查找标题、费用、编辑按钮。未知费用、预估刷新、草稿保留及历史事实断言全部保留。

## 本地已执行与边界

68 项针对版本、身份、永久签名边界、Stable 升级门禁、系统文件选择器及发布校验的 Python 单测通过。`ci-check-workflow.py`、`ci-scan-private-files.py`、`git diff --check` 通过。单测使用模拟设备接口，不代表真实升级成功。

本地没有 KVM、Android 平台 SDK 或模拟器，未运行设备用例或升级。相关 Kotlin 编译、两批功能及原有计算/完整性测试以正常自动 PR CI 的实际结果为准；不手动 dispatch/retry。合约参数与历史搜索功能不再扩展，本地未重新制作截图或大型验收档案。

## 已实际核对的旧 APK

两份均经真实 `aapt dump badging`、`apksigner verify --print-certs` 检查，正式包名、非 debuggable、四 ABI 及上述永久证书一致；下载文件的 SHA-256 与 GitHub asset digest 一致。

| 来源 | 实际版本 | asset ID | APK SHA-256 |
| --- | --- | --- | --- |
| [V2.0.0 Stable 原 APK](https://github.com/mlzx-xxj/trade-review-app/releases/download/v2.0.0/TradeReview-v2.0-fixed-candidate.1.apk) | 2.0.0 / 22 | 647113240 | d157327870baa1dcd6d777de8ffcc95b1c504020a7c450ad615c7f56a6f92b5d |
| [V2.1.0 正式签名候选](https://github.com/mlzx-xxj/trade-review-app/releases/download/v2.1.0-rc.1/TradeReview-v2.1.0-private-test.apk) | 2.1.0 / 23 | 661260946 | b711c80c2ddc448a297d00f7aaa0a9492fc54ebf2a17b9203ee3014333e84123 |

第二份虽然 Release 标签含 `rc.1`，APK 实际是正式包名和永久证书，不是隔离 RC。该标签当前指向 `ed875fd6b8965210cb31f7f14fddfe2c8bc75e78`；实际内容核对以下载的 APK、asset ID 和哈希为准。

## 尚未执行的正式升级与发布条件

目前不满足 Stable 发布条件。V2.0.0(22) → V2.2.0(24)、V2.1.0(23) → V2.2.0(24) 的真实覆盖安装、ZIP 导出/恢复和重启持久化均未执行；新永久签名 APK 只能在合并后由受保护 main 全部门禁通过时生成。本地空库启动和 RC 安装不计入这两条路径的结果。

最少后续事项：

1. 正常 PR CI 核对 Kotlin/JVM/安全检查和 API29/API35；本次合约测试修正须实际通过，API35 未定位超时若再现则保留原始 XML/语义树/截图及阶段证据，停止扩张修复。
2. 经独立审核合并后，允许正常 main 工作流生成永久签名 2.2.0/code24，并核对实际包名、pin、APK SHA-256 与来源提交。现有自动 Stable 升级流程仍严格使用哈希锁定的原 code22 APK；报告标识随目标版本更新，ZIP/图片/业务事实逐字段检查不减少。
3. 两条升级路径在可丢弃模拟器上装入既有非空合成材料，覆盖安装区间不得卸载或清除数据。核对成交、FIFO、持仓、策略、规则、费用、复盘、账户和历史附件；完整导出 ZIP，证明覆盖前后事实一致，再执行真实恢复、导出比对及进程重开。B 路径使用上表已核验 code23 APK，仍须实际执行，不能由 A 路径结果替代；本 PR 没有新增 B 路径的 CI 编排。

正式设备结果、签名候选下载地址及最终可发布结论必须随后基于真实产物补齐；本 PR 不自动合并或发布 Stable。
