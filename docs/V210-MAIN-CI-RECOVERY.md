# V2.1 界面批次：main CI 前置修复

本 PR 先处理开发前检查发现的失败，**尚未开始首页与统计的视觉改造**。
不合并、不发布 Stable，不改变 `v2.0.0`、`2.0.0 / code22`、包名或签名。

## 实际基线

PR #16 已合并，最新 main 为 `3550a03d8291e564361c9f02d3fae163c5e52e2a`。
[main Android CI 38990324927](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38990324927) 未全部成功：

| 检查 | 实际结果 |
|---|---|
| 编译 Debug、RC、Release | 成功 |
| JVM | 241 项，0 失败／错误／跳过；已核验 JUnit |
| Debug／RC／Release Lint | 0 Error、0 Fatal；既有 Warning 分别 20／21／21 |
| API 29 第一次运行 | 136 项全部成功，追加真实成交、重开、四种视觉配置也成功 |
| API 35 第一次运行 | 130／136 后进程崩溃，SnapshotStateObserver 跨线程绘制异常 |
| API 35 第二次完整运行 | 136 项原生测试全部成功；追加真实成交旅程在删除预览时失败 |
| 签名、安装候选与发布 | 因完整矩阵失败而跳过，不能据此推断 Secrets 缺失 |

第一次失败发生在 `V200MainUiTest.realizedCalendarWeeklyMonthlyChartsAccountsAndFullZipRestoreUseRealFacts`。
第二次的原始旅程日志显示：`DefaultDispatcher-worker-4` 上触发重组，创建
`DialogWrapper` 时抛出 `Can't create handler ... has not called Looper.prepare()`；
栈尾明确回到 `V200ViewModel.deleteExecution` 中的 `pending.value` 发布。
第一次跨线程绘制与这一机制一致，但其堆栈未直接指出最初的 worker 发布点，不把这一推断冒充独立确证。

## 最小修复范围

- `V200ViewModel.kt`：成交修改／删除预览、确认后清理统一在 `Dispatchers.Main.immediate` 发布。
- `BackupViewModel.kt`：JSON／ZIP 预览、刷新、确认恢复后的清理及操作反馈统一在主线程发布。
- 数据库访问、FIFO 重算、文件读取／写入、ZIP 校验仍在原后台上下文执行。
- 不改任何金额、费用、保证金、风险公式、Room 结构、备份格式或页面布局。

`StateFlow` 本身允许跨线程使用；这里约束的是供界面直接消费的状态发布位置，
避免后台回调与 Compose 测试帧调度交错时在 worker 创建窗口或绘制。
没有吞掉异常、增加失败重试、删除断言或禁用测试。

## 新回归与证据

新增 `UiPublicationThreadTest` 三项原生测试，使用独立内存数据库和合成文档：

1. 校准探针：刻意从 IO 发布一次本地测试状态，必须记录为非主线程；再从 main 发布并记录为主线程。
2. 新增、修改预览、确认、删除预览、确认、失败写入：直接记录发布线程，并核验确认前事实不变、确认后审计保留。
3. JSON／ZIP 导出回读、预览、刷新、覆盖恢复、取消、CSV、输出失败：核验发布线程及交易事实保持一致。

探针使用即时启动的 Unconfined 收集器，记录真实发布线程，不用 Main 收集器隐藏来源。
原有 MainActivity 真实成交、键盘、T+1、FIFO、备份恢复、进程重开及全部 API 29／35 回归仍完整保留。

产物增加 `ui-publication-{calibration,ledger,backup}-api{29,35}.json`。
交付门禁要求至少 **139 项原生测试**、新增三项测试和完整线程证据；单独写 PASS、
缺少确认／恢复事件、UI 状态来自 worker、探针不能识别 worker 均会被拒绝。

本地已实际执行：121 项 Python 回归通过；工作流静态安全检查、私钥文件扫描、`git diff --check` 通过。
本地没有 Android SDK，Android 编译及模拟器结果须以本 PR 后续完整 Actions 为准，当前未宣称通过。
这里的独立 ViewModel 线程测试不冒充真实页面操作；真实操作证据来自保留的 MainActivity 回归和追加旅程。

## 当前阻塞与下一步

先验证并审核这一 CI 恢复 PR，合并后等待最新 main 全部成功。
随后从新的 main 建立首页／统计视觉分支，采集同设备同数据的前后截图，
交付 `V210-FULL-UI-EXPERIENCE.md`；复盘和账户视觉调整仍留在后续批次。
当前没有新版视觉对比或 V2.1 安装候选，不把既有截图称为本轮改造成果。
用户真机、同证书覆盖升级和 V2.1 Stable 均未执行。
