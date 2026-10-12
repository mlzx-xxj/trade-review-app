# V0.5 实际验证记录

基础 main be22c68（已合并 PR #4），独立分支 codex/v05-risk-analytics。Base 无 SDK，未运行本地 Android 编译或真机测试，未安装 Android Studio 或新建环境。

已实际本地执行：签名门禁 6、配置完整性 3、公开签名 payload 4、模拟器交付门禁 3，共 16 项通过。只验证发布脚本，不代替 Android 编译/测试。

## 已取得的完整验证

提交 54ad6382c333d23b8acc6d6b9ab29122e5509a54 的 [Actions 38184810663](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38184810663) 三个 job 均成功：Debug/测试 APK 编译、JVM/Lint，API 29 x86_64 Pixel 2 模拟器，以及 APK/截图发布。以下数量来自下载后的原始 XML，未将缓存或静态审查视为测试。

| 验证 | 实际结果 |
| --- | --- |
| Debug 和 AndroidTest APK | 编译成功，0.5.0 / 5 / com.tradereview.app，无 INTERNET |
| JVM 单元测试 | 76 项，0 失败、0 错误、0 跳过 |
| Android 模拟器 | 55 项，0 失败、0 错误、0 跳过 |
| Lint | 0 错误、13 警告（OldTargetApi 1、AppBundleLocaleChanges 1、GradleDependency 11） |
| 发布门禁 | 16 项通过（签名 6、配置 3、payload 4、模拟器交付 3） |
| Room schema | 正式 KSP v5 输出与提交的 schema 一致 |
| 截图 | 13 张真实合成数据截图齐全，新增 09–13 已逐图查看 |

原 49 项 JVM 用例保留并更新当前备份格式预期；新增多空/成本/精度/模式/R/规则/筛选/模板及完整备份校验。模拟器包括原回归与正式 v4→v5、v1→v5 升级链、九表失败回滚、风险确认/过期拒绝、手动/自动编辑、草稿、规则/模板及 R/深色界面。实际类数量：MigrationTest 5、V05DataTest 6、ProfessionalUiTest 5；其余旧数据、恢复、录入、导航和日历统计回归 39。

[已验证测试包下载页](https://github.com/mlzx-xxj/trade-review-app/releases/tag/v05-debug-38184810663-1) 同时提供 APK、SIGNING.txt、APK-IDENTITY.txt、SHA256SUMS.txt 和 13 张截图 ZIP。09-professional-auto 实际显示数字键盘与两个完整保存按钮；自动化同时确认真实输入法窗口、完整可点击保存按钮至少 48dp，且位于输入法上沿之上。10 为超限确认，11 为风险规则，12 为有效 R 样本统计，13 为深色专业录入。截图使用合成记录，不含用户实际交易数据。

独立下载 APK 后使用官方 apksigner 验证 v2 单 signer 成功。该轮 APK SHA-256 为 `5762af5d2ddbb98c5ca75be0acf259c8d4df1e56260f2b557c68ab244b373635`；证书 SHA-256 为 `191695e5622139ba5cfbf8c1f7abd97ae2bde8a2fc06b0dd6cb47a5ad032189e`。SIGNING.txt 明确 GitHubActionsSecretsConfigured=false、temporary runner Debug key，并非原 V0.3 固定证书。本轮不生成、读取或替换长期私钥。

本报告更新后正常提交，最终提交仍须重新完整 CI，不使用 skip ci。最终 HEAD 对应的完整 CI、APK、签名与截图链接记录在 [PR #5](https://github.com/mlzx-xxj/trade-review-app/pull/5)，仅在全部成功后标记 Ready for review，不自动合并。

## 限制与后续验证

Base 没有 SDK；未执行本地 Android 编译、真实手机或 API 35 设备测试。模拟器结果不代替手机实测。Lint 13 项非阻断警告仍存在，本轮沿用兼容的原技术版本。V0.4 第四张截图仍为列表/筛选入口，并非筛选弹窗；新增专业窗口使用系统截图与渲染帧同步。

固定签名未配置，当前仅交付临时测试包，不能保证覆盖原固定版、其他 runner 临时包或未来升级。所有者须使用已有 V0.3 密钥配置 Secrets，步骤见 V05-CALCULATIONS-AND-UPGRADES.md。JSON 明文；V0.5 新格式/数据库不支持交给旧 App 降级读取。自动计算限定已由用户确认的线性计价、同结算币种；需换汇或非线性合约使用手动净盈亏/手填风险，不自动猜参数。

## 发现并修复的问题

全部编译、JVM、Lint 与 connectedDebugAndroidTest 显式 --no-build-cache。截图由模拟器 UI 实际生成，使用合成数据；发布脚本要求原八张和新增五张齐全，保持模拟器原失败码。早期截图暴露键盘问题，修复后采用系统窗口截图及渲染帧同步，成功轮次已逐图确认新增窗口。

下列失败记录保留，修复后的完整成功结果见上方；未把失败轮次当作通过。

签名：main 构建日志实际未配置固定密钥，管理 API 为 GitHub 403（Resource not accessible by integration），保留 V0.3 pin。不是自动审批拒绝；本轮用允许的临时测试包完成交付，未绕过签名门禁。计算与兼容口径见 V05-CALCULATIONS-AND-UPGRADES.md。

首轮提交 243379b，[Actions 38182484262](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38182484262)：Room KSP 执行，但主 Kotlin 编译失败，三处把 @Composable pnlColor 用作函数引用。改为可组合上下文的直接 lambda 调用；本轮 JVM/Lint/模拟器未执行，不发布 APK。另补强纪律分母及违规快照一致性校验，缺有效风险/权益不能计为守规。

第二轮提交 42ee7bf，[Actions 38182768346](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38182768346)：Debug/测试 APK 编译成功，实际 JVM XML 76 项、0 失败/错误/跳过；Lint XML 0 错误/13 警告；16 项发布门禁通过。Room v5 schema 与实际 KSP 输出一致。下载 APK 独立 apksigner 验证 v2 单 signer 成功，实际 0.5.0 / 5 / com.tradereview.app 无 INTERNET；SIGNING.txt 明确 SecretsConfigured=false、临时 runner 签名。本轮模拟器仍待完成，因此未声称完整 CI 成功。

审查发现新 AndroidTest fixture 应从测试 APK assets 读取；旧 V0.4 golden 重导出升级为格式 4 后，摘要应改变，并需验证字段完整往返。已修正测试适配，保留原有旧摘要导入校验。风险异步预览增加输入/规则/历史来源一致性检查，避免旧结果用于新表单显示；这些改动已在成功轮次接受完整 CI。

第二轮模拟器 XML 实际 55 项，49 通过、6 失败（0 错误/跳过）。1 项旧格式摘要重导出预期未升级，4 项错误地从主 APK 而非测试 APK 读取 fixtures，均已按先前审查修正。另 1 项为模板编辑 LazyColumn 回收了屏外勾选项，已先滚动到目标后再点击。迁移、风险确认/快照、专业保存/编辑与金额/R 界面测试已实际通过，但本轮整体失败，不发布 APK。

本轮 12 张截图真实导出；手工查看发现数字键盘覆盖保存区，说明旧 assertIsDisplayed 无法证明没有另一窗口遮挡。已改全屏 Dialog 的窗口 Insets/ADJUST_RESIZE 布局，并增加实际 AccessibilityWindowInfo 输入法与保存按钮屏幕矩形断言。本轮错误与视觉发现都未当成全部成功，修复后须重新完整 CI。

第三轮提交 9de36e6，[Actions 38183806245](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38183806245)：编译、76 JVM、16 发布门禁、Lint 0 错误/13 警告再次通过，实际 APK 0.5.0 / 5 无 INTERNET、临时单 signer 独立验证成功。模拟器 XML 实际 55 项，54 通过、1 失败（0 错误/跳过），只剩键盘验收的“Save accessibility node exists”；其他迁移/fixture/模板修复通过。Compose 的虚拟节点不能依赖系统 find-by-text 快捷路径，验收改为实际树遍历并寻找完整可点击按钮、检查至少 48dp 高度与键盘上沿；失败前也先保留实际截图。此轮整体失败，发布跳过，不将编译或其他用例通过当作完成。
