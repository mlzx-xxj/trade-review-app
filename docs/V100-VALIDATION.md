# V1.0 实际验证记录

开发基线：已合并 V0.8 PR #8，main `7f8c60ea9394f179ed8da48241ab46581d2b6238`。main [Actions 38303977043](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38303977043) 已实际完成 success。独立 [PR #9](https://github.com/mlzx-xxj/trade-review-app/pull/9)，不自动合并。

## 已执行

- Base 36 项 Python 发布/交付/故障决策测试通过，其中新增 V1.0 门禁 7 项。Python/shell 语法、YAML 解析、git diff --check 已执行；这些不是 Android 编译。
- 首轮 [Actions 38308733961](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38308733961)，源提交 `0510db73fab6d2c155f5a55a08751c957afc9fff`：Debug、Android测试、RC 和 unsigned Release 编译成功；151 JVM 测试，0 失败/错误/跳过；Debug/RC/Release 三种 Lint 均 0 错误、20 警告。
- 警告包含依赖升级建议、target35未采用最新SDK、平台Exif/存储建议、动态语言AppBundle及图标资源变体/保留旧资源等。本轮没有为了消除警告批量升级依赖、提升 target 或关闭检查。
- CI 生成的 Room7 schema 与仓库原文件字节一致。保留完整迁移链，未改变数据库结构。
- Base 下载真实 RC APK，用官方 apksigner 与独立二进制 Manifest 读取器核对：com.tradereview.app.rc / 1.0.0-rc.1 / code10 / non-debuggable；无 INTERNET、网络状态或广泛存储权限。实际摘要及证书随每次候选构建变化，以对应下载页 SIGNING/RC-IDENTITY/SHA256SUMS 为准。
- 原 V0.3 固定签名公开 APK 再次实际验签：证书 SHA-256 与原 pin 一致；包 com.tradereview.app / 0.3.0 / code3。旧 APK 不能替代原私钥。

## 首次真实失败与修复

首轮 API35 在 103 项开始后只完成54项，其中2项失败、测试进程崩溃；不是通过。新增 AndroidTest 备份 Provider 在独立测试 APK 进程加载时缺少 Kotlin Intrinsics（目标 APK 运行时不会自动进入该独立进程）。将该合成 Provider 改为仅 Android/Java 类，保留同样的损坏/写入失败/只写条件及全部原断言。修复后重新执行完整 CI，不删除失败测试。

第二轮 [Actions 38311222575](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38311222575)，源提交 `e20b76516366619def966747f831d2c779002eae`：三种编译/JVM/Lint 再次通过。API35 实际完成103项、0跳过，100通过/3失败；新增5项备份数据测试均通过，Provider崩溃消除。3项 UI 失败是对尚未组成的 LazyColumn 子节点直接 performScrollTo：home-plan、home-post-review、show-help。加入列表定位并从 LazyColumn performScrollToNode 搜索滚动；保留原保存/恢复/历史数据断言，月报切换先滚回控制项再点击。

恢复预览验证完成后优先滚到数量/范围/冲突说明；出错优先滚到结果提示。新增 UI 链同样从列表搜索恢复确认按钮。实际同证书升级未执行原因记录为原固定密钥未配置，不能误写为旧包不存在。

[Actions 38314770281](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38314770281)，源提交 `09acba9c4808181c3f0618c0f948ab962886e2b4`：API35 实际完成103项、102通过/1失败、0跳过。剩余完整链在图片保存反馈仍显示时点击盘后保存没有触发，20秒等待超时。根 Scaffold 的底部 Snackbar 覆盖了滚到可见边缘的保存按钮；按实际反馈高度预留内容空间，让操作按钮保持可点击。保留原保存及数据断言，补充 enabled/click action 和失败截图/合成状态诊断，不通过延长超时掩盖问题。

[Actions 38317583127](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38317583127)，源提交 `44d160caf3571c0897ef5b6f445846fe48c7af03`：API35 实际103项、102通过/1失败、0跳过。盘后保存已通过并进入日历；实际金额节点是 `CNY：+123.45 · 1 笔`，计算正确。新测试误将 assertTextContains 默认参数当作子串匹配，实际默认要求完整 Text 值。改为精确断言当日币种/金额/笔数，并精确断言周/月报同样数据；不修改金额或统计公式。

## 后续真实失败与修复

[Actions 38319722827](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38319722827)，源提交 `a748f889515d4ccdf9e0cc8e580c69661b031c82`：编译、151 JVM 和三种 Lint 再次通过。API29 完成103项、102通过/1失败；API35 完成103项、99通过/4失败，均0跳过。新增链的当日/周报金额精确断言已通过，月报失败为等待尚未滚入视野的 LazyColumn 节点，改为确认月报页面和计算完成后从父列表搜索滚动。保存并继续的回归等待还需同时确认 UI 保存回调及实际清空的金额，而不是只等待 Room 新行可见；清空断言保留。

该轮 API35 的系统日志在交易测试开始前已记录 Launcher 和 system_server 输入分发 ANR，随后实际键盘未显示，键盘断言失败并使交付探针拒绝继续。没有关闭键盘断言或忽略/清除 ANR；完整日志保留在失败运行产物，下一提交重新在新模拟器运行全部测试。

[Actions 38327162890](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38327162890)，源提交 `731ec228299853ad74775790fa6695e78b752944`：编译、151 JVM、三种 Lint 再次通过，Room7 schema 字节一致。API35实际103项、101通过/2失败、0跳过：新链已通过月报，恢复预览超高卡片内的按钮虽有语义节点但尚未进入可点击视野；补充精确子节点滚动与显示/enabled断言，并在截取恢复截图前断言二次确认对话框已出现。品种搜索结果被真实键盘和滚动位置挡住，测试执行已存在的 Search IME 操作后滚到结果，保留实际可见性断言。该轮大字体/深色/键盘/横屏、保存并继续均已通过，没有删除这些检查。恢复交易、recordKey、净盈亏与私有图片校验断言全部保留。

该轮API29在新提交启动后被并发策略取消，没有将未完成结果标为通过。中间被后续提交取代的运行同样不能代表最终提交验证。

[Actions 38329297307](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38329297307)实际发现新增测试的编译错误：`performTextInput`返回Unit，不能继续链式调用`performImeAction`。拆分为两次操作同一输入节点，保留搜索/键盘动作与结果可见性断言；该失败运行没有执行模拟器测试，未记为通过。

[Actions 38329863912](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38329863912)，源提交`2829081dc8e0f131f346f54e5fbbda0189602993`：构建/JVM/Lint通过；API35实际103项全部通过，0失败/错误/跳过，完整ZIP链与不可调试RC安装/重启通过，十六表及附件校验一致。API29实际103项、102通过/1失败，0跳过，完整ZIP链已通过，剩余保存并继续的清空/反馈等待超时。代码审查发现编辑器与证据查看器位于派生统计加载条件分支内，Room写入后统计失效可卸载这些组件，导致本地状态及保存回调丢失；将两者保持挂载，独立于派生统计重算。保留清空与反馈断言，增加两笔真实净盈亏精确断言和失败时的合成状态/界面诊断。财务公式、存储事务与数据库结构未变，重新执行完整CI。

[Actions 38332817128](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38332817128)，源提交`15f70e14193e7ee0298b48ea66c5333dc2c0856e`：构建、151 JVM、三种Lint通过；API29完整103项及实际RC安装/重启通过，保存并继续清空、反馈及两笔精确净盈亏断言通过，十六表和附件校验一致。API35完成103项、101通过/2失败，均为真实键盘未显示；其余包括完整ZIP链、保存并继续通过。系统日志在01:48:55记录SystemUI keyguard服务ANR，应用到01:52:21才首次启动，随后安装探针也拒绝ANR对话框。没有删除键盘断言或关闭ANR检测。新增启动后20秒稳定等待与应用测试前的系统日志门禁，出现ANR/崩溃即保留日志、lastanr和截图并失败，不把未执行测试标为通过；重新在新模拟器执行完整CI。Base新增3项合成启动故障决策检查，当前共39项Python测试；这些不是Android启动或键盘实测。

[Actions 38335765567](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38335765567)发现runner缺少rg，新增启动门禁的合成检查因证据读取工具不可用而失败，未继续Android编译/模拟器。改用标准grep并保留工具可用性、日志存在检查，增强合成检查失败信息；Base39项重新通过，随后完整CI继续核对。

[Actions 38336114233](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38336114233)的API29启动检查发现误判：Google搜索进程以ActivityManager标签自行记录“ANR in”，但系统`dumpsys activity lastanr`明确显示`<no ANR has occurred since boot>`。该轮应用测试未执行，没有记为通过。改用平台实际ANR记录，同时保留原日志和崩溃检查；真实SystemUI ANR仍拒绝，已有界面对话框/键盘断言全部保留。新增对应合成故障测试，目前Base40项通过；这不是Android启动实测。

同一运行API35实际完整103项、0失败/跳过及不可调试RC安装/重启通过，十六表与附件校验一致。应用功能回归已分别在两种API实际通过，但不同历史运行不能替代最终提交同一完整CI；判定修正与文档继续一起接受最后完整验证。

Base独立下载本次真实APK，用官方apksigner和二进制Manifest读取器核对证书、包名、non-debuggable、版本/权限/摘要；四份中文/Latin OCR模型与全部原ABI实际存在。实际SIGNING-STATUS仍configured=false，不产生固定签名包。交付报告门禁还已用上一轮真实失败XML检查，正确拒绝API29的1失败和API35的4失败，不把成功安装探针冒充全套成功。

## 最终提交的验证与交付入口

以[PR #9 当前提交 Checks](https://github.com/mlzx-xxj/trade-review-app/pull/9/checks)核对最终head的完整Actions；源码、文档改动都必须重新通过完整工作流，无skip ci。运行中的任务、被取消的运行及失败的历史运行不能用于声称最终通过。

工作流只有在构建与API29/35全部成功后才创建[手机浏览器候选下载页](https://github.com/mlzx-xxj/trade-review-app/releases)。每个V1.0候选页随实际APK提供：

- `CI-RESULTS.txt`：从真实JUnit XML与Lint XML生成的151项JVM、各103项API29/35及三种Lint统计，拒绝任何失败、错误、跳过或缺少新测试。
- `RC-IDENTITY.json`、`SIGNING.txt`、`SHA256SUMS.txt`、`PUBLICATION-CHECK.json`：实际apksigner/aapt验证的证书、包名、版本、non-debuggable、权限约束、完整离线模型、APK摘要及40位来源提交。最终交付以该候选文件为准，临时证书可能随构建变化。
- `PERFORMANCE-AND-OCR.json`：两种API的实际列表/计算/图片/OCR评估、十六张表进程重启一致性和交付APK安装探针；共享runner数据不是手机性能结论。
- `TradeReview-v1.0-emulator-screenshots.zip`：各API的34个页面/状态截图，全部使用合成记录和图片。首页、引导、回读、隐私、证据、恢复确认及实际RC首次启动/重启截图另提供API35 PNG。

API29/35回归包括Room完整升级链、旧JSON/ZIP与事务/文件回滚、OCR误识别防护、风险/R/多币种、草稿/标签/品种、日历/洞察/周月报、真实键盘/大字体/深色/横屏，以及新增的手工交易→截图→盘后复盘→日历→周月报→完整ZIP→删除合成记录→预览与二次确认→恢复稳定ID、金额、图片和日志。额外探针实际安装交付的不可调试RC，跳过引导、结束进程并重启确认状态。

最终运行链接、APK链接、摘要与Ready状态在PR正文及交付回复登记；核对`sourceCommit`与PR最终head一致。这里保留失败和修复证据，最终成功结果直接来自对应运行报告，不通过预填成功数字代替执行。

## 正式发布阻塞

首轮实际 SIGNING-STATUS.json：configured=false、originalPinVerified=false。管理 Secrets/Variables API 权限不足，不能读取其值；实际工作流尚无法使用原私钥。没有读取、生成或替换长期私钥。

真正固定签名 non-debuggable Release 签署/安装、真实原证书旧版→V1.0 install-r，以及用户手机验收尚未执行。RC 独立包名仅提供测试，不保证长期 RC 覆盖升级；正式状态仍为候选。所有者按[手机签名指南](V100-PHONE-SIGNING.md)配置原材料，再执行[真机清单](V100-PHONE-ACCEPTANCE.md)。无法取得原私钥时必须等待明确迁移选择，不能自行创建替代身份。
