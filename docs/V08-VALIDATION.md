# V0.8 实际验证记录

基线 main 9bc2dab（PR #7 已合并），Base 无 Android SDK，未安装 Android Studio、未进行真机安装或同固定签名升级。最终结果以正常提交的完整 Actions 与独立 APK 验签为准。

首轮 5677acb，[Actions 38229475762](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38229475762)：Debug/测试 APK、原 JVM 与 Lint 构建阶段成功。API 29 原始 XML 83 项，1 失败；API 35 原始 XML 83 项，2 失败，均 0 错误/跳过，发布跳过。

两套新基准夹具错误地使用 riskMode=manual，现有合法手填模式为 amount；严格专业数据校验正确拒绝，没有放宽生产校验。修正测试数据，重新执行完整基线。图片基准执行但 JSON 未被 AGP 自动拉取，补充测试专用 MediaStore 导出及明确 adb pull，保存原始样本，不编造未取得的数据。

API 35 原有品种管理回归在输入 NVDA 后结果被软键盘与过高说明/选择卡遮挡。搜索时折叠说明/市场选择/新增按钮，保留当前市场和清除搜索、增加 IME 搜索收键盘；原失败用例完整保留，待重新验证。

尚未取得完整性能基线，不声称优化已提升。正式 Room 仍 v7，迁移链、schema、备份版本/严格校验与财务口径未改变。Secrets/Variables 管理接口实际 403；未读取长期私钥或密码，固定签名实际状态待最新工作流产物核对。

本地当前实际执行原四组发布脚本 16 项和新增发布连续性决策 10 项通过；假 apksigner 仅用于门禁决策测试，不代表真实 APK 验签。待所有阶段结束补充最终记录与限制。
