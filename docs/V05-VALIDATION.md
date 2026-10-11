# V0.5 实际验证记录

基础 main be22c68（已合并 PR #4），独立分支 codex/v05-risk-analytics。Base 无 SDK，未运行本地 Android 编译或真机测试，未安装 Android Studio 或新建环境。

已实际本地执行：签名门禁 6、配置完整性 3、公开签名 payload 4、模拟器交付门禁 3，共 16 项通过。只验证发布脚本，不代替 Android 编译/测试。

已写入 JVM 计算/规则/筛选/R/旧备份/新格式 golden 与 CSV 回归；原 49 项 JVM 用例保留并更新当前格式的预期。原 43 项模拟器保留，扩展正式 v4→v5 和 v1→v5、九表失败回滚、风险确认/过期拒绝、专业编辑、草稿、规则/模板、R 与深色界面。预计数量不作为通过结果；后续以 Actions 的实际 XML 更新。

全部编译、JVM、Lint 与 connectedDebugAndroidTest 显式 --no-build-cache。截图由模拟器 UI 实际生成，使用合成数据；发布脚本要求原八张和新增五张齐全，保持模拟器原失败码。V0.4 第四张采集限制保留（列表/筛选入口），本轮专业窗口采用系统窗口截图及渲染帧同步，结果需逐图确认。

构建阶段结果已取得，模拟器及最终提交仍需按下述实际记录判断。最终提交含文档更新也必须完整 CI，不使用 skip ci。最终提交状态、完整结果、APK 与截图会在独立 PR 中记录。

签名：main 构建日志实际未配置固定密钥，管理 API 403，保留 V0.3 pin；本轮不读写长期私钥。待本轮实际 APK 与 SIGNING.txt 核对后报告。计算与兼容口径见 V05-CALCULATIONS-AND-UPGRADES.md。

首轮提交 243379b，[Actions 38182484262](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38182484262)：Room KSP 执行，但主 Kotlin 编译失败，三处把 @Composable pnlColor 用作函数引用。改为可组合上下文的直接 lambda 调用；本轮 JVM/Lint/模拟器未执行，不发布 APK。另补强纪律分母及违规快照一致性校验，缺有效风险/权益不能计为守规。

第二轮提交 42ee7bf，[Actions 38182768346](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38182768346)：Debug/测试 APK 编译成功，实际 JVM XML 76 项、0 失败/错误/跳过；Lint XML 0 错误/13 警告；16 项发布门禁通过。Room v5 schema 与实际 KSP 输出一致。下载 APK 独立 apksigner 验证 v2 单 signer 成功，实际 0.5.0 / 5 / com.tradereview.app 无 INTERNET；SIGNING.txt 明确 SecretsConfigured=false、临时 runner 签名。本轮模拟器仍待完成，因此未声称完整 CI 成功。

后续审查发现新 AndroidTest fixture 应从测试 APK assets 读取；旧 V0.4 golden 重导出升级为格式 4 后，摘要应改变，仍需验证字段完整往返。已修正测试适配，保留原有旧摘要导入校验。风险异步预览增加输入/规则/历史来源一致性检查，避免旧结果用于新表单显示；这些改动尚需最新完整 CI。

第二轮模拟器 XML 实际 55 项，49 通过、6 失败（0 错误/跳过）。1 项旧格式摘要重导出预期未升级，4 项错误地从主 APK 而非测试 APK 读取 fixtures，均已按先前审查修正。另 1 项为模板编辑 LazyColumn 回收了屏外勾选项，已先滚动到目标后再点击。迁移、风险确认/快照、专业保存/编辑与金额/R 界面测试已实际通过，但本轮整体失败，不发布 APK。

本轮 12 张截图真实导出；手工查看发现数字键盘覆盖保存区，说明旧 assertIsDisplayed 无法证明没有另一窗口遮挡。已改全屏 Dialog 的窗口 Insets/ADJUST_RESIZE 布局，并增加实际 AccessibilityWindowInfo 输入法与保存按钮屏幕矩形断言。本轮错误与视觉发现都未当成全部成功，修复后须重新完整 CI。
