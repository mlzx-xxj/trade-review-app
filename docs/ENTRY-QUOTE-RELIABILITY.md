# 成交费用预览：取消后重新打开的可靠性

## 实际失败

main `6776d4d4c437c71fb3117ecf35f4cc22abfe8932` 的 [CI 39003413037，第二次执行](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39003413037/attempts/2) 中，API 29 完整回归通过；API 35 的原有 `TradeEntryExperienceTest.actualFormJourneyAndReopenEvidence` 失败。股票同日卖出被 T+1 检查拒绝，取消后以测试时钟切到下一天，重新输入 10 元、1000 股，费用预览在 45 秒内仍为“暂无法估算”，没有显示预期的 6.00 元。失败 PNG、原生 JUnit 和 logcat 已实际读取。该次失败不是模拟器未启动，也不是 Maven 下载失败。

日志不足以证明失败瞬间的 Compose 协程交错顺序。不能将推测当作已证明的根因。本修复针对异步预览的结果归属：计算结果携带本次输入和账本来源，仅用于匹配的表单；后台计算后在 Main 发布，不再让旧请求重置当前预览。费用公式、成交保存、T+1、FIFO、数据库、版本、签名和发布流程均不改动。

## 独立验证

新增真实 `MainActivity` 仪器测试 `EntryQuoteLifecycleTest`：从界面买入，连续十次“同日卖出被拒绝 → 取消 → 次日重开”，交替输入 10/11 元，逐次读取 6.00/6.60 元预览并核实账本没有变化；最后实际保存卖出，验证两笔成交、合计净盈亏 -7 元、剩余零股。共 22 次费用读取；只用模拟器、合成数据和注入时钟，不操作手机数据。原有全部测试保留。

`entry-quote-lifecycle-api29.json` / `entry-quote-lifecycle-api35.json` 保存每次实际读取的界面金额；发布报告用独立 Decimal 计算交叉核对，缺少证据、陈旧金额或改变事实均拒绝。失败时额外保存多窗口语义树和真实截图。

本地已实际执行 Python 回归 124 项通过。Android 编译、完整 API 29/35 及原有跨进程 UI 测试的最终状态以本 PR 的 [Actions](https://github.com/mlzx-xxj/trade-review-app/actions) 为准；提交时尚未执行完成，不预先标为通过。本修复与首页/统计视觉改造分开审核，不自动合并。
