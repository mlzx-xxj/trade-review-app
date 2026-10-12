# V0.6 实际验证记录

基础 main 87f0b7d（PR #5 已合并），独立分支 codex/v06-review-reports。Base 无 Android SDK，未运行本地 Android 编译、手机测试，也未安装 Android Studio 或新建环境。

## 已实际完成的完整验证

提交 213f6b90bd9dfce913ef95e9d11f7987b6c5dae2 的 [Actions 38204649126](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38204649126) 三个任务均成功：Debug/AndroidTest APK 编译、JVM/Lint，API 29 x86_64 Pixel 2 模拟器，以及验签产物发布。逐项读取下载的原始 XML：

| 检查 | 实际结果 |
| --- | --- |
| JVM | 103 项，0 失败、错误、跳过 |
| Android 模拟器 | 69 项，0 失败、错误、跳过 |
| Lint | 0 错误，17 警告 |
| 签名/配置/payload/设备交付门禁 | 16 项通过 |
| Room v6 schema | 实际 KSP 产物与提交字节一致 |
| 模拟器截图 | 18 张齐全，14–18 新截图逐图查看 |

模拟器包括图片/文件/ZIP 数据 9 项、迁移 6 项（含正式 v5→v6、v1→v6）、新界面 4 项和全部旧回归。JVM 包括报告跨年/闰月、多币种/有效 R、目标分母/归档/未知状态、恶意 ZIP/限额/摘要、旧 JSON 与新数据往返。系统选择器通过测试 Registry 校验真实 Intent/MIME 并注入实际测试 content URI；独立 Java 测试 Provider 不在生产 APK。容量不足测试注入可用空间，不填满设备。截图包含明确标注的合成交易图片，不是真实用户交易或真机测试。

[该轮 APK 与 18 张截图](https://github.com/mlzx-xxj/trade-review-app/releases/tag/v06-debug-38204649126-1) 的 APK SHA-256：`71efa839c0d33acb165782935f9a71adba262282e784129cac805c4b37d61208`。下载 APK 独立官方 apksigner 验证 v2 单 signer 成功，证书 SHA-256：`c0ca20cb667fb737e48abc63e5c0a6ef96d101b098c0f430de8d077e9be68b8c`。SIGNING.txt 实际为 `GitHubActionsSecretsConfigured=false`、`temporary runner Debug key`；仍不能保证覆盖升级。APK 标识实际为 com.tradereview.app、0.6.0 / 6，无 INTERNET/广泛存储权限。Release 资产摘要与下载文件一致。

PR 工作流默认编译 GitHub 生成的合并测试提交：该轮 SIGNING.txt 的 sourceCommit 是 `50c2b0fe387fce7325baabb92a4a2a2b0fe8c21e`，Actions head_sha 为上述真实分支提交，PR 仍未合并。两者均保留便于追溯。

截图审查发现目标趋势纵轴展示了内部计算精度的长小数，本提交将比例纵轴显示为两位小数，计算/阈值判定仍使用原 BigDecimal 精度。本记录和该显示修改提交后还必须完整执行 CI；最终提交的真实 Actions、APK、证书和摘要更新在 [PR #6](https://github.com/mlzx-xxj/trade-review-app/pull/6) 中，只有最终全部成功后才标为可审核。不使用 skip ci，也不自动合并。

## 环境与历轮问题修复

本地实际执行：签名门禁 6、配置完整性 3、签名 payload 4、模拟器交付 3，共 16 项通过。该结果只验证发布脚本，不代替 Android 编译/JVM/模拟器。

测试覆盖报告日期/跨年/闰月、分币种/有效 R/未知纪律、目标分母/精度/归档/文字目标、ZIP 路径/重复/超限/摘要，以及原格式兼容和新增数据往返。AndroidTest 覆盖真实 ContentResolver 图片/EXIF/损坏/权限/容量/删除、ZIP 十二表与文件协调回滚、v5→v6 与完整旧链，以及截图/报告/目标/备份界面。上述成功记录来自实际原始 XML；以下保留失败历史，不将未执行结果标为通过。

工作流重新执行 Debug/测试 APK、JVM、Lint 和 connectedDebugAndroidTest，显式 --no-build-cache；发布只在全部任务成功且原 13 张与新增 5 张截图齐全后进行，保留失败退出码。最终提交包含文档也须完整 CI，无 skip ci。

签名以实际 APK 与 SIGNING.txt 为准。前置 main 的真实日志仍为 Fixed V0.3 key not configured；保留原 pin，本轮不读取、生成或替换长期私钥。测试环境/数据/限制说明见 V06-DATA-AND-REPORTS.md。

首轮 89a7ba4，[Actions 38198694976](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38198694976)：Room KSP 已实际生成十二表 v6 schema，但主 Kotlin 编译失败，Android 公开 SDK 无 OsConstants.O_DIRECTORY。改用 O_RDONLY 打开已知私有目录，保留 fsync；正式 schema 来自该轮真实 KSP 产物。本轮 JVM/Lint/模拟器未执行，发布跳过，不当作通过。

第二轮 627406d，[Actions 38199041760](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38199041760)：Debug/测试 APK 编译成功，原始 JVM XML 101 项、0 失败/错误/跳过；Lint XML 0 错误/17 警告（ExifInterface 1、OldTargetApi 1、AppBundleLocaleChanges 1、GradleDependency 11、UsableSpace 2、UseKtx 1），16 项发布门禁通过，实际 KSP v6 schema 与提交一致。实际 APK 0.6.0 / 6 无 INTERNET/广泛存储权限，独立 apksigner v2 单 signer 验证成功，SIGNING.txt 明确 SecretsConfigured=false、临时 runner 签名。

该轮模拟器进程中途崩溃，XML 只记录 35 项：32 通过、3 失败、0 错误/跳过，其余用例尚未执行，无截图，发布跳过。两项失败为旧 JSON 重导出格式/摘要预期未更新，已保留旧 golden 导入摘要检查并验证升级格式完整往返。测试 APK 的独立 Provider 进程缺少目标 APK 中的 Kotlin 标准库，导致 NoClassDefFoundError；改为只依赖 Android/Java 的测试夹具，生产 Kotlin 架构/权限不变。另补充图片奇数像素采样上限、缺失文件 ZIP 恢复及 ZIP 文件数/解压声明总量边界，等待新的完整 CI。

第三轮 fbbc5fa，[Actions 38200572309](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38200572309)：Debug/测试 APK、103 JVM、16 发布门禁通过，Lint 0 错误/17 警告，schema 一致；实际 APK 0.6.0/6 无 INTERNET/广泛存储权限、独立 v2 单 signer 验证、仍临时签名。模拟器原始 XML 完整 69 项：64 通过、5 失败、0 错误/跳过，无进程崩溃。

九项图片/文件/ZIP数据测试与六项迁移测试均通过，覆盖删除、位置清除、缺失恢复、十二表/文件回滚；旧数据/计算回归执行。剩余失败为新 UI 三处局部文本误用 assertTextContains 默认精确匹配、首页屏外交易未先滚动，以及旧筛选方案的快速物理点击时序。改为实际完整文案断言/明确 substring、先滚动交易列表、等待临时反馈并重新定位方案。保持所有用例与真实触控，不以语义点击或跳过测试掩盖问题。此轮 14 张截图，新增周报已查看；缺其余新增截图，发布跳过。

另补齐目标四周期可点击小趋势图与对应界面验收，缺失点不填 0；周报/月报截图调整至真实金额/日历区域。最新提交须重新完整 CI。

第四轮 be0c869，[Actions 38201937859](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38201937859)：Debug/测试 APK、103 JVM、16 发布门禁通过，Lint 0 错误/17 警告，schema 一致，APK 身份/权限与临时单 signer 独立验证成功。模拟器 69 项完整执行，67 通过、2 失败、0 错误/跳过；原筛选回归已通过。

剩余归档点击未出现确认窗口，测试需等待第二个目标保存的临时反馈消失；缩略图 XML 明确位于 unmerged tree（实际存在），因父点击列合并了子语义，改为明确定位未合并图片并先滚动交易附件区域。没有跳过实际显示/触控/归档断言。此轮 17 张截图，14 截图页（含实际私有合成图片）、16 月历、18 完整 ZIP 预览已逐图查看，缺 17 目标页，发布跳过。最新补充失败前也保存目标页，再继续实际归档/历史检查，须重新完整 CI。

第五轮 a8c0a8a，[Actions 38203363074](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38203363074)：Debug/测试 APK、103 JVM、16 发布门禁通过，Lint 0 错误/17 警告、schema 一致、临时 APK 独立验签及身份/权限校验成功。模拟器 69 项，68 通过、1 失败、0 错误/跳过，图库验收已通过；仍缺目标截图，发布跳过。

剩余目标用例在文字目标保存后找不到 goals-list，定位到测试助手用系统 Back 隐藏输入法：窗口状态可能仍显示正在关闭的键盘，延迟 Back 导致返回报告页。改用实际 IME Done，图片说明和目标名称增加明确完成动作；目标保存先收起键盘和清焦点，不发送系统返回。保留归档、历史、文字目标非量化及交易事实不变验收，重新完整 CI。
