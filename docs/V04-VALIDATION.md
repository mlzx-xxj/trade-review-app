# V0.4 真实验证记录

基础：PR #3 已合并 main `afefcfa`，分支 codex/v04-calendar-analytics，独立 PR #4，不自动合并。Base 无 Android SDK，没有声称本地 Android 编译或手机实测。没有安装 Android Studio 或创建新环境。

## 已实际执行

本地运行签名门禁 6 项、Secrets 完整性 3 项、公开签名 APK 内容比较 4 项，均通过；它们不处理真实私钥，不能替代 Android 编译。

数据层首轮 [Actions 38136917399](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38136917399)，提交 f3aec17：Debug 与测试 APK 编译通过；JVM XML 实际 47 项、0 失败/错误/跳过；Lint XML 0 错误、13 警告。API 29 模拟器实际 33 项中 32 项通过，1 项失败，不发布 APK。

失败是 V0.3 旧格式目录对象的 Gson 反射字段排序跨 JVM/ART 不一致。已改为新格式显式规范顺序，保留两种旧布局校验，新增 JVM、ART fixture 和使用真实 ART Gson 生成旧校验的模拟器回归。正式 Room v4 schema 来自此轮真实 KSP 生成产物，保留全部历史 schema。

完整界面提交 a3b4d62，[Actions 38137934643](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38137934643)：Debug/测试 APK、48 项 JVM（0 失败/错误/跳过）与 Lint（0 错误、13 警告）通过。模拟器 40 项中 39 项通过，旧备份兼容、ART 实际旧格式校验、迁移和事务恢复通过；图表测试误用了全文匹配断言，实际节点文本为“2026年10月11日：+0.30 CNY · 2 笔”，已改为完整精确文本断言。截图测试通过但外部应用文件无法在 Gradle 后导出，已改用官方 additionalTestOutputDir 及单一脚本执行/收集，以确保失败状态和截图检查均能阻止发布。本轮仍失败，未发布 APK。

后续审核增加曲线异步坐标与最新数据一致性检查、月份/滚动返回保留、按选定日期进入日志编辑、防止页面状态随历史日期无限增长、包含旧草稿的跨平台 fixture 和独立新版 checksum golden fixture。CI 也新增 aapt 校验实际 APK 的版本、包名与无 INTERNET。所有新增修复与文档提交会重新完整验证；最终报告以真实 XML、Lint、SIGNING、APK 和截图产物更新。

## 必须验证的覆盖范围

JVM：月首尾、跨年/闰年/时区、三币种精确分组、零交易/持平、相对日期与组合筛选、任意/全部匹配、隐藏/改名稳定 ID、连续盈亏和已平仓回撤、评分范围、旧格式与新备份往返及非法数据拒绝。

模拟器：原 28 项全部保留；v1→v4 和 v3→v4 正式迁移、原六表与草稿/隐藏标签/目录保留、配色和常用筛选完整恢复、两种旧格式校验、筛选表 SQL 失败全事务回滚/过期预览/去重合并；月导航、当日分币种与编辑、计划无交易/持平、共享筛选和方案、隐藏标签/空状态、图表点击和配色设置。截图直接采集模拟器渲染，数据均为测试生成的合成记录。

所有编译、JVM、Lint、connectedDebugAndroidTest 都使用 --no-build-cache；最终文档提交不使用 skip ci。

## 签名与限制

最新 main 的实际 Actions 输出未配置固定签名。管理 API 返回 GitHub 403，不是假设配置成功；没有读写私钥/密码，没有新密钥。最终临时包不能覆盖固定签名 V0.3。所有者配置已有 keystore Secrets 后还必须实际 CI 验证；配置步骤和精确统计口径见 V04-DATA-AND-UPGRADES.md。

只验证 API 29 模拟器，不等于真实手机安装或覆盖升级。统计同日次序基于录入 ID，不代表真实成交次序；样本提示不代表统计显著性；JSON 为明文校验备份，不提供认证或加密。
