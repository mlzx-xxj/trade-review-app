# V0.4 真实验证记录

基础：PR #3 已合并 main `afefcfa`，分支 codex/v04-calendar-analytics，独立 PR #4，不自动合并。Base 无 Android SDK，没有声称本地 Android 编译或手机实测。没有安装 Android Studio 或创建新环境。

## 完整 CI 已实际成功

提交 `289cbaad9273a569bcc565119119a4fb486bd9df` 的 [Actions 38140762209](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38140762209) 三个任务全部成功，已下载并解析实际 XML/签名/APK/截图产物：

| 检查 | 实际结果 |
| --- | --- |
| Debug APK / Android 测试 APK | 编译成功 |
| JVM 单元测试 | 49 项，0 失败、0 错误、0 跳过 |
| 签名、Secrets、公开 APK 与截图交付脚本 | 16 项通过（6 + 3 + 4 + 3） |
| Android Lint | 0 错误、13 警告：OldTargetApi 1、AppBundleLocaleChanges 1、GradleDependency 11 |
| API 29 x86_64 Pixel 2 模拟器 | 43 项，0 失败、0 错误、0 跳过 |
| 编译 APK 的实际身份及权限 | com.tradereview.app / 0.4.0 / 4；无 INTERNET |
| 发布任务 | 全部上游测试成功后发布临时测试包和截图 ZIP |

模拟器 43 项：BackupRepositoryTest 8、MigrationTest 4、TradeDatabaseTest 1、V03DataTest 7、V04DataTest 6、BackupSettingsTest 3、CalendarAnalyticsUiTest 8、FastEntryUiTest 4、NavigationTest 2。包含旧全部功能回归、完整 v1→v4 / v3→v4 升级、旧 ART/JVM 备份恢复、新版往返、SQL 失败回滚与新界面交互。

首个完整成功发布：[临时 APK 与签名资料](https://github.com/mlzx-xxj/trade-review-app/releases/tag/v04-debug-38140762209-1)。APK SHA-256 为 `a7dea61efcb89166c1222cba693d48de7640063ffbfd15b32b6fcd353d3cf6b6`；用官方 apksigner 对下载的 APK 独立验证成功，v2 签名、单 signer，实际临时证书 `cc4ea8406f9c15ddfc91c55dc2cba2f5706634e92e40867ef0f19bc6483db02b`。SIGNING.txt 明确 `GitHubActionsSecretsConfigured=false`，未把该证书当作 V0.3 固定 pin。PR 流水线的 sourceCommit 为其合成 merge ref，head SHA 见 Actions。

此轮导出 8 张真实截图，但人工查看发现最后一张系统截图仍显示切页前画面；改为 Compose 渲染同步的 PixelCopy 截图，避免语义树已更新但系统显示帧滞后。截图全部来自测试合成数据。此修复及文档以普通提交重新运行完整 CI，不使用 skip ci；最终提交结果、最新 APK 和逐张截图核验以 PR #4 的验证记录为准，不将上一提交的通过结果移用于新提交。

## 开发阶段的实际失败与修复

本地运行签名门禁 6 项、Secrets 完整性 3 项、公开签名 APK 内容比较 4 项，均通过；它们不处理真实私钥，不能替代 Android 编译。

数据层首轮 [Actions 38136917399](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38136917399)，提交 f3aec17：Debug 与测试 APK 编译通过；JVM XML 实际 47 项、0 失败/错误/跳过；Lint XML 0 错误、13 警告。API 29 模拟器实际 33 项中 32 项通过，1 项失败，不发布 APK。

失败是 V0.3 旧格式目录对象的 Gson 反射字段排序跨 JVM/ART 不一致。已改为新格式显式规范顺序，保留两种旧布局校验，新增 JVM、ART fixture 和使用真实 ART Gson 生成旧校验的模拟器回归。正式 Room v4 schema 来自此轮真实 KSP 生成产物，保留全部历史 schema。

完整界面提交 a3b4d62，[Actions 38137934643](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38137934643)：Debug/测试 APK、48 项 JVM（0 失败/错误/跳过）与 Lint（0 错误、13 警告）通过。模拟器 40 项中 39 项通过，旧备份兼容、ART 实际旧格式校验、迁移和事务恢复通过；图表测试误用了全文匹配断言，实际节点文本为“2026年10月11日：+0.30 CNY · 2 笔”，已改为完整精确文本断言。截图测试通过但外部应用文件无法在 Gradle 后导出，已改用官方 additionalTestOutputDir 及单一脚本执行/收集，以确保失败状态和截图检查均能阻止发布。本轮仍失败，未发布 APK。

后续审核增加曲线异步坐标与最新数据一致性检查、月份/滚动返回保留、按选定日期进入日志编辑、防止页面状态随历史日期无限增长、包含旧草稿的跨平台 fixture 和独立新版 checksum golden fixture。CI 也新增 aapt 校验实际 APK 的版本、包名与无 INTERNET。所有新增修复与文档提交会重新完整验证；最终报告以真实 XML、Lint、SIGNING、APK 和截图产物更新。

候选 c85a3ca，[Actions 38138887112](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38138887112)：49 项 JVM（0 失败/错误/跳过）、Lint（0 错误、13 警告）、编译和实际 APK 身份/离线权限检查通过；43 项模拟器中 42 项通过。唯一失败为返回已保存滚动位置后，测试直接查找已被 LazyColumn 回收的今天按钮，已改为先让 LazyColumn 滚动并重新组合目标。截图仍未导出；新增测试专用 MediaStore 受控 Downloads 副本，模拟器可读取，且不会因卸载测试 APK 消失。不新增主 APK 权限，不使用真实数据。

发布脚本新增 3 项实际本地测试：带空格路径复制全部截图、即使有截图也保留原模拟器失败码、任何必需截图缺失都拒绝发布。CI 将实际执行全部 16 项发布/签名脚本测试。日历与统一筛选卡片压缩控件间距，保留文字图例并优先展示日期/结果；增加深色红盈绿亏日历截图。最后验证结果仍需最新完整 CI，不能将本段改动直接视为通过。

布局和截图通路提交 79bfcd4，[Actions 38139774401](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38139774401)：编译、49 项 JVM、16 项发布测试、Lint 0 错误/13 警告通过，实际 APK 已核对 0.4.0 / 4 / com.tradereview.app 且没有 INTERNET。模拟器 43 项中 42 项通过，返回/日期编辑测试修复通过，7 张实际截图成功导出并检查；截图用例最后一次切换配色被深色模式更新提示条遮挡，超时，已改为等待实际提示消失再点击并核对数据库配色。未把截图生成或其他通过用例当作完整工作流成功，未发布 APK。

## 必须验证的覆盖范围

JVM：月首尾、跨年/闰年/时区、三币种精确分组、零交易/持平、相对日期与组合筛选、任意/全部匹配、隐藏/改名稳定 ID、连续盈亏和已平仓回撤、评分范围、旧格式与新备份往返及非法数据拒绝。

模拟器：原 28 项全部保留；v1→v4 和 v3→v4 正式迁移、原六表与草稿/隐藏标签/目录保留、配色和常用筛选完整恢复、两种旧格式校验、筛选表 SQL 失败全事务回滚/过期预览/去重合并；月导航、当日分币种与编辑、计划无交易/持平、共享筛选和方案、隐藏标签/空状态、图表点击和配色设置。截图直接采集模拟器渲染，数据均为测试生成的合成记录。

所有编译、JVM、Lint、connectedDebugAndroidTest 都使用 --no-build-cache；最终文档提交不使用 skip ci。

## 签名与限制

最新 main 的实际 Actions 输出未配置固定签名。管理 API 返回 GitHub 403，不是假设配置成功；没有读写私钥/密码，没有新密钥。最终临时包不能覆盖固定签名 V0.3。所有者配置已有 keystore Secrets 后还必须实际 CI 验证；配置步骤和精确统计口径见 V04-DATA-AND-UPGRADES.md。

只验证 API 29 模拟器，不等于真实手机安装或覆盖升级。统计同日次序基于录入 ID，不代表真实成交次序；样本提示不代表统计显著性；JSON 为明文校验备份，不提供认证或加密。
