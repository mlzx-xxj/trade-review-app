# V0.7 实际验证记录

基线 a07859c，PR #6 已合并。Base 未安装 Android SDK/Studio；Android 结果由 Actions 原始报告核对。

完整功能提交 cc6e5cfa8482d47b4b8b2d42f88d48e920224d32 已通过[第四轮 Actions 38218642053](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38218642053)。下列数字来自下载后的原始 JUnit/Lint 报告，不以静态检查代替编译或设备执行。

| 实际检查 | 结果 |
| --- | --- |
| Debug APK / Android 测试 APK | 两者编译成功 |
| JVM 单元测试 | 133 项，0 失败、错误、跳过 |
| Android Lint | 0 错误、17 警告 |
| API 29 x86_64 模拟器 | 81 项，0 失败、错误、跳过 |
| 发布脚本门禁 | 6+3+4+3=16 项通过 |
| Room 正式 schema | 实际 KSP v7 与提交文件逐字节一致 |
| 实际页面截图 | 22 张，旧十八张与新增 OCR/确认/洞察/盘前四张齐全 |
| 发布任务 | 成功；APK 与签名、摘要、身份文件及截图 ZIP 已上传 |

模拟器包括七项正式迁移及 v1→v7、v6→v7；十六表 JSON/带图片 ZIP 往返、旧版 JSON/ZIP、事务失败回滚；打包真实 OCR 中英数字/合约/负金额；OCR 待确认、人工补录、核实保存/证据、重复确认、草稿恢复、损坏输入不改变历史；洞察/目标/盘前数据与界面；全部此前快捷录入、标签、日历、风险、截图、周月报回归。OCR 图像全部是合成样本，不含真实个人交易信息。

Lint 的十七条为 ExifInterface 1、OldTargetApi 1、AppBundleLocaleChanges 1、GradleDependency 11、UsableSpace 2、UseKtx 1；原有警告保留，新增 Context 持有警告已消除。没有禁用 Lint 或跳过设备用例。

本地实际执行四组发布脚本与 git diff --check；Base 未安装 Android SDK，未声称本地执行 Kotlin/模拟器测试。新增和专业深色截图已用图像工具查看，属于真实模拟器输出，不是手机实测。

## APK 与签名独立核对

[第四轮手机浏览器下载页面](https://github.com/mlzx-xxj/trade-review-app/releases/tag/v07-debug-38218642053-1)提供完整 APK、SIGNING.txt、SHA256SUMS.txt、APK-IDENTITY.txt 和二十二张截图 ZIP。私有仓库需要登录有访问权限的 GitHub 账号。

完整 APK 63,608,733 字节，包含 Hani_ctc/Latn_ctc 的 conv/lstm 模型。通过有界的三个校验副本在 Base 拼回原始字节后，SHA-256 与 SHA256SUMS 及 GitHub Release 资产摘要三者一致：

```
9c6fd5ae1b6f220ce0642e816f03c1c7a744b266c268e66f7ae132097f9c7a65
```

官方 apksigner 本地实际验签成功（v2）；证书 SHA-256：

```
7fb5ffdc65e2f9b6507fd01234693cbee8fee13eb0af6083f7098ab7a7dd5bfb
```

APK 身份 0.7.0 / code 7 / com.tradereview.app；aapt 输出只有 DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION，没有 INTERNET、联网状态或广泛存储权限。SIGNING、摘要和身份文件本地摘要也逐项匹配 Release。发布目标是上述 PR 功能 HEAD；SIGNING 的 sourceCommit=0c48173773eea17a21c6bd6138cb6179018c1515 是 Actions pull_request 合成合并提交，两者已核对。

真实工作流及 SIGNING.txt 的 GitHubActionsSecretsConfigured=false，证书为临时 runner Debug 身份，与保留的 V0.3 pin 不同。没有读取、重新生成或替换长期私钥；当前不能保证覆盖旧包或未来版本。所有者仍需按 [签名配置说明](V07-DATA-AND-REVIEW.md#签名与限制)配置既有密钥。临时包仅供测试，不自动卸载或清除手机数据。

## 剩余验收与最终提交

[真机逐项验收](V07-PHONE-ACCEPTANCE.md)尚未执行：首次飞行模式 OCR、厂家文件选择器、低内存/大图库、小屏键盘和同签名覆盖升级仍需手机验证。合成样本 OCR 通过不代表任意券商横向表格能完整解析；缺失或冲突保留待确认，可人工补录或使用原快捷录入。备份为明文，不提供加密或身份认证。

这份结果文档随后作为正常提交运行完整 CI，最终 HEAD 的实际运行、APK 和证书链接在 PR #7 汇总；不使用 skip ci，不自动合并。不同临时 runner 包的摘要与证书可能变化，应使用对应 Release 的 SIGNING.txt 和 SHA256SUMS.txt。

## 失败记录与修复过程

以下保留前三轮当时的实际失败与修复，不把部分任务成功误称完整 CI 成功。第四轮已验证全部修复及新增损坏 OCR 回归。

Secrets 查询真实 403，不读取私钥。打包 AAR 真实下载检查含 Hani/Latn 模型资产，Google Maven 经现有代理连接成功。最终 APK 要独立验签、核对模型/权限/版本与 Release 资产摘要。最终提交含文档必须完整 CI，不使用 skip ci；PR 不自动合并。

最终 HEAD 的 CI、APK、签名和截图链接在新 PR 更新。真机验收仍未执行，单独清单不冒充测试结果。

首轮 8865dac，[Actions 38214811934](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38214811934)：Debug 和测试 APK 已编译，实际 KSP 生成正式十六表 v7 schema（本提交保存）；JVM 132 项执行，2 项失败：ZIP 读入硬编码只接受 schema 5，拒绝新 schema 6；原 V0.6 重导出测试仍期待 5。修复 ZIP 明确接受受支持的 5–当前版本，同时更新重导出版本预期，旧 golden 摘要及校验保留。Lint 未取得完整成功结果，模拟器/发布跳过，未视为通过。

同步审查补齐：每次 OCR 会话唯一身份，恢复相同字段也重新确认；每候选草稿稳定 ID 重复保存不产生副本，保存交易只清理对应 OCR 草稿；切换盘前日期/数据时旧计算不用于新日期；风险预览在后台计算，旧重复/风险确认不跨会话复用。

第二轮 8154a4c，[Actions 38215464882](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38215464882)：Debug/测试 APK、132 JVM（0 失败/错误/跳过）、16 发布检查成功，Lint 0 错误/18 警告（新增 StaticFieldLeak，改为 AndroidViewModel 仅持有 Application，待再验证），真实 KSP schema 与提交一致。实际签名日志 Fixed V0.3 key not configured，证书 15e10885c300873fa5e22a347fe115c32623da9133ec5fd75fa1eaf70f371cd3；完整 APK 39 MiB 的 Base 直接下载遭存储端 403，未作本地验签，增加 24 MiB 有界校验副本和小元数据，通过已授权连接器下载，原完整 APK 发布保持不变。

模拟器原始 XML 80 项：76 通过、4 失败、0 错误/跳过。真实打包 OCR 中英/合约数字/负金额、七项迁移（v1→v7/v6→v7）、新十六表/文件往返/失败回滚/旧 JSON 回归均成功。失败为旧 V0.3 重导出断言仍期待 5，两处设置偏好卡片加入口后过高、目标触点在屏外，以及 OCR 用例要求自动识别出可选合约字段。实际 OCR 原文有 rb2701，但合约标签未解析，生产未猜测、已保存专业合约为 null；用例改为放大查看合成原图后人工补录并逐项核实，再验证专业字段保存。

设置拆成管理与显示两张卡，修正应用数据库/备份版本文案；OCR 复用原大图查看。原四项失败验收保留，等待下一轮完整验证。该轮 21 张实际截图，缺旧专业深色页，发布跳过；新增 OCR、确认、洞察、盘前截图已逐图查看。截图/文字均为合成信息，没有手机实测。

第三轮 7b9c192，[Actions 38217758184](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38217758184)：Debug/测试 APK、133 JVM、16 发布检查通过，Lint 0 错误/17 警告（Context 持有警告已消除）。打包 Hani/Latn 模型与离线权限、实际签名检查通过；但新分块辅助脚本按外层产物大小估计设置 48 MiB 上限，APK 本身更大，命中断言，使任务失败，模拟器/发布跳过。修正为最多六个各 24 MiB 的有界副本，完整 APK 不裁剪，不作为本轮全部通过。

另补充显式损坏 OCR 输入的 Android 回归，验证识别错误不改变任一历史表或旧图片；第四轮已实际通过。
