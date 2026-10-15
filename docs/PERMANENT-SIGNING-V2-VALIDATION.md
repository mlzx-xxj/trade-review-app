# V2 永久签名迁移验证记录（Issue #12）

基线 main `337ac2c9bd5ccc2e70cdf2e7a3abb93be97eff8e`，PR #11 已合并；本轮分支 `codex/permanent-signing-v2`。签名PR依赖独立的 [CI测试修复PR #13](https://github.com/mlzx-xxj/trade-review-app/pull/13)，以其分支为审核基线，签名diff不改变App目录；组合代码的App生产源码/版本、正式applicationId、财务计算、Room或备份格式仍与main一致。请先审核/合并#13，再将本签名PR基线切回main，不自动合并。

## 实施内容

- 新永久 pin `29b2f82887a0591d5b1d1f9ab148b90c0b658088530e3bcc1bd7e5fee64f2af9`、alias `trade-review-release-2026`、PKCS#12，集中到公开策略模块及 V200 证书文件。
- 原 V03 证书文件保持原样，老版发布/身份验证脚本和测试仅作对应历史版本证据；旧 V1 私钥签名入口已停用。当前 V2 无 `fixed-v03` 或原升级变量门禁。
- 沿用 main-only Environment `v03-signing-main`，只在其签名 Job 引用四个 Secrets 和新公开变量；保持 PR只读、源SHA/Artifact摘要、全部测试、apksigner/payload检查、临时0600/finally清理。
- 新元数据明确 `permanentIdentityVerified`、`legacyV03UpgradeCompatible=false`；证书匹配不冒充真实升级或手机验收。可选历史 APK 测试改为 Android 实际拒绝不同签名，检查旧安装和合成文件仍在；随后新包首次安装。
- 删除不再适用的新签名 instrumentation fixture；不用新证书伪造旧 V0.3 instrumentation，也不重复上传无消费者的签名 fixture。
- 全无凭据允许隔离 RC；部分凭据/错证书/失败证据均失败。任何包只作 prerelease 候选，不自动 Stable。

## 环境审查与边界

2026-10-15 实查 main protected=true；Environment deployment policy 仅 Branch main（无通配符或tag）。Variables/Secrets 管理接口返回403，不能读配置值或据此宣布配置成功。所有者在 Issue/任务说明四个 Secrets 尚未配置，本轮不添加、不索取、不生成任何私钥。

本地实际执行：95 项 Python 安全/签名/启动/产物回归通过，actionlint 和工作流结构检查通过，私钥路径/内容扫描未发现跟踪的私钥；扫描不是完整 GitHub secret scanning。新增用例均使用合成字符串/模拟 apksigner/adb，不是真实新私钥签名或 Android 异证书安装验收。

完整本轮 GitHub Actions 结果将记录在关联 PR 的验证表，不能以本地脚本检查替代 Android 编译/API29/API35。PR/非main分支不能签名或发布；真实新 main 签名必须等所有者配置 Environment、审核合并后再验证。此文不把历史运行当本轮最终提交结果。

## 失败运行 38696646794 调查

[原 main 运行](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38696646794) attempt1：build成功；API35 在 connectedDebugAndroidTest 执行测试前解析 UTP 依赖失败（auto-service 1.1.1 / error_prone_annotations 2.23.0 的 parent POM、dagger2.48/protobuf-kotlin3.24.4/kotlin-reflect等未找到）。日志中的“POM解析”源于父POM不能解析，不是应用源码 Kotlin/财务错误。已实际复测原SHA：API35成功解析依赖并运行全124项，因此不是持续的版本不可用/源码编译错误；仍不能仅凭日志区分远端临时响应与依赖负缓存。不修改依赖版本/仓库、不禁用测试。复测API29全124项通过，API35另一项风险确认UI在30秒等待超时（见原运行attempt2），主运行仍失败，签名/发布正确skipped；不能称main全绿。

API29 attempt1真实完成124项、1失败。V200大字体/键盘/重建用例多次配置变更引发 MainActivity DESTROYED→CREATED→RESUMED；清理使用 BACK 后 Activity 退出，再 onActivity 抛 NPE。未见相应生产进程 FATAL，属于生命周期同步与清理竞争线索，不能把一次复测绿色称为彻底消除不稳定。

独立 [PR #13](https://github.com/mlzx-xxj/trade-review-app/pull/13) 只修改此测试：按主线程 lifecycle registry 等待 RESUMED及具体配置，明确隐藏IME而不发送可能退出页面的BACK；保留小屏、深色、字体、键盘、recreate、横屏、草稿与保存数据库全部断言，未吞异常或减少测试。API29严格夜间断言另暴露Android10延迟应用配置，测试显式睡眠/唤醒并等待实际深色；全部断言继续保留。

本签名最终SHA首轮PR API35在旧兼容host草稿等待超时、非main API29大数据测试发生16MB heap OOM，全部失败证据保留，未称全绿。独立#13补齐精确等待本轮seed草稿（此前仅等交易读取，可能还在旧catalog快照）、先前尝试的-prop dalvik参数被模拟器明确拒绝，API35实测仍192MiB，因此已移除。改在ro.kernel.qemu=1且支持root ADB的专用userdebug模拟器中配置实际ART growthlimit256m/heapsize512m并重启Zygote，核对boot属性及Runtime.maxMemory=256MiB；保留配置前后启动ANR/FATAL门禁。App仍正常UID，不设置生产largeHeap；1000/5000/10000 corpus不减，生产App不加largeHeap。风险确认用例等待操作结束/按钮可用并标记超时阶段。签名分支随后依赖#13进行完整组合复测；不能将其中某一API曾通过冒充最终矩阵成功。最终结果以关联PR实际工作流为准。

## 尚未验证

- 新真实 PKCS#12 签名、apksigner 新永久证书实测、正式包 API29/35 安装。
- 真实旧 V0.3→新永久包 Android 拒绝及数据保留探测（需真实新签名APK）；本地模拟只能验证拒绝逻辑。
- 同一新永久证书旧版→递增版本号新版的实际升级与数据保留（尚无较早新身份APK）。
- 所有者手机备份/迁移/安装验收、最终稳定发布。

所有者操作和旧包安全迁移见 [手机指南](V200-PHONE-SIGNING.md)。未来只能沿用同一新私钥/证书、正式包名并递增versionCode；旧V0.3私钥丢失不通过换证书假称解决覆盖升级。
