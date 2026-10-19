# 第三批开发记录与失败证据

以下按开发当时的状态保留；其中“待验收”“正在运行”等描述均为历史记录。当前结论见[验收报告](../V210-CONSISTENCY-ACCEPTANCE.md)。

# V2.1 第三批入口检查（已完成）

核实时间：2026-10-18 UTC。仓库：mlzx-xxj/trade-review-app。

[完整成功的 main CI #242](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39101525143) ·
[原始证据来源及逐文件 SHA-256](handoff/PROVENANCE.json) ·
[保留文件 SHA256SUMS](handoff/SHA256SUMS.txt)

入口核验已完成；第三批修复和验收仍在进行。第二次基线已在真实 API29/API35 复现问题，正在作最小修复；候选完整验收尚未完成。

| 项目 | 核实结果 |
| --- | --- |
| PR #20 | merged=true；2026-10-18 05:43:22Z 合并至 main |
| main | 67e97ccfaffbfaae10be7d796bb8d5b29454bb35；与 PR #20 head b524469 的树无差异 |
| 合并后 CI | Android CI #242 / run 39101525143；7个任务全部 completed/success |
| 已完成 CI 构建 | Debug、AndroidTest、RC、Release 编译成功；241 JVM，0 failure/error/skip |
| 三种 Lint | 无 Error/Fatal；Debug 21 / RC 22 / Release 22 个 Warning |
| Android API29 | 已成功；143项原生测试，零失败/错误/跳过；四配置22次额外真实UI instrumentation及新PID重开证据通过 |
| Android API35 | 已成功；143项原生测试，零失败/错误/跳过；四配置22次额外真实UI instrumentation及新PID重开证据通过 |
| 正式身份 | 2.0.0 / code22 / com.tradereview.app |
| 数据协议 | 源码 Room v9，28 实体；JSON v8；ZIP 容器 v1；旧迁移链和测试保留 |
| Stable | v2.0.0，非 prerelease；tag 提交 dc4e20259eeb4248b66cedbedc644e9e559c98a9 |
| Stable 公开 APK | TradeReview-v2.0-fixed-candidate.1.apk；实际 apksigner v2/v3 验证成功 |
| APK SHA-256 | d157327870baa1dcd6d777de8ffcc95b1c504020a7c450ad615c7f56a6f92b5d |
| 永久证书 SHA-256 | 29b2f82887a0591d5b1d1f9ab148b90c0b658088530e3bcc1bd7e5fee64f2af9；与源码 pin 一致 |
| 仓库指令 | 仓库树没有 AGENTS.md |
| 测试源码 | 32 份 JVM Kotlin 文件；35 份 Android *Test.kt；共享 fixture 2 份 |
| 本地入口检查 | 157 项脚本测试通过；workflow 静态门禁、私钥路径/内容扫描、diff --check 通过 |

main 构建归档 ID 12127485268，ZIP SHA-256：
7b024b06780382b0d9760ad9f5ec33a53085eb4a1ab2327bed899b4b534e0d96。
MANIFEST 来源提交和 runId 一致，45 个归档文件的大小和 SHA-256 均核对。

main API35 归档 ID 12127576832，ZIP SHA-256：
70fb9e55309f8afe64bdf22464e8e76a250eba6e3fb25146c7d1c4ed43ca7f72。
453 个原始文件全部核对；四配置首页/统计和计划/复盘/账户独立事实校验通过。
main API29 归档 ID 12127038634，ZIP SHA-256：
fcbefeb479840a2e6ee1b202e349bad7e249ec08a2542ba35d8e422988ac4a57；453个原始文件全部核对。

受保护 main 的永久签名任务已成功。签名归档 ID 12127558855，ZIP SHA-256：
2da25a1e03b79b6e1997bbdc66b7f924f9305a03439434345d246336e2bc0a65；3个公开产物全部核对。
独立 apksigner v2/v3 验签通过；实际正式候选 APK SHA-256：
006906d97ba5194aeed519e546590d6dc24d5a6688fd251e8247eee484a79a35。
来源为当前 main，non-debuggable，版本/code/包名/永久证书 pin 均保持原身份。
这不是既有 Stable APK，也不代表手机覆盖升级或 Stable 发布。
两个正式包安装任务及最终候选产物任务也已成功；完整 CI 已成功。
API35 安装归档 ID12127810881，SHA-256 3746151d70a22386d3accf9d39b8179458c3a7bc6ac74a479cf25ee11ee79fcb；
API29 安装归档 ID12127845895，SHA-256 dac48ee110664d6b9a85d4c1d557fe636a80b660672cfe092ae5883cd0f75905。
来源、全部文件哈希、实际正式 APK 哈希和安装成功状态均已核对。
运行中的“未提供早期永久包”仅表示该次安装脚本没有升级 fixture，不表示仓库没有既有 Stable。

另核对 PR #20 最终 API35 归档（run 39075189056，artifact 12117911768），
归档 SHA-256 a5c31828f13aa5d7982934e767875d8836232fbb312bf609538c3b85254a21be，
453 个原始文件全部核对；JUnit 143 项，0 failure/error/skip。
它只代表该历史提交，不替代合并后的 main CI。

完整 CI 成功后重新 fetch main，确认仍为同一 SHA，创建 codex/v210-consistency-acceptance。
初始仅增加严格的真实 UI 回归及原始证据运输，尚未修改产品代码。
本对话没有读取签名私钥、修改 Stable；现有 main CI 自行执行其原有候选产物流程。
本机已补齐 Android SDK/JDK17；原样 main 的 Debug/AndroidTest 编译、241 JVM 和 Debug Lint 已成功。
本机无 KVM。一次额外的软件模拟器导航验证在测试发现前失败（0 tests，Process crashed）；
日志显示安装目标 App 前系统 Phone 反复 ANR，随后目标进程启动超时并被杀死，CPU 等待严重。
已停止该模拟器并保留失败原图、JUnit、完整 logcat 和操作日志；不计为通过，不以此认定产品源码缺陷。
最终真实模拟器验收仍使用 GitHub Actions 的 API29/API35 完整硬件加速矩阵。

## 第三批执行计划（入口已完成）

1. 已完成：下载并核对 main API29/API35 原始产物及全部7个任务，入口没有阻塞。
2. 已完成：重新获取最新 main，创建独立分支，冻结来源 SHA 和保护文件哈希。
3. 复用现有生产 MainActivity 的四配置真实成交、持仓、首页、统计、计划、复盘、账户旅程；
   补充风险/策略、合约参数、备份恢复的窄屏、深浅主题、键盘、旋转、语义与实际操作审查。
4. 先保留问题的原始截图与可重复操作，再作最小修复。已完成页面不重设计，不扩展功能。
   第二次基线已确认策略/合约编辑重建丢失、三个状态控件缺少名称，以及 API35 风险输入窗口上移。
5. 原有测试全部保留。增加必要的真实 UI 回归、新 PID 强制停止后重开与保存事实核对，
   使用独立 Decimal/原始快照验证 FIFO、手续费、未知金额、保证金及统计事实未变。
6. 完成 API29/API35、JVM、三种 Lint 和完整 CI。保留原始 PNG、操作日志、JUnit XML、
   报告、来源提交 SHA、GitHub 归档摘要及逐文件 SHA-256，创建独立 PR 等待用户与 ChatGPT 审核。

不改 FIFO/费用/保证金/统计公式、Room schema、正式包名、备份格式、长期签名及版本；
不自动合并、不发布 Stable、不操作真实手机数据。

## 可独立核验的入口原始证据

- [main 完整运行结果与7个任务](handoff/main-ci-success.json)
- [API29 原始 JUnit XML](handoff/api29/TEST-native.xml)、
  [API35 原始 JUnit XML](handoff/api35/TEST-native.xml)
- [全部 JVM 原始 XML](handoff/build-evidence/test-results)、
  [Debug Lint](handoff/build-evidence/lint-results-debug.xml)、
  [RC Lint](handoff/build-evidence/lint-results-rc.xml)、
  [Release Lint](handoff/build-evidence/lint-results-release.xml)
- [8张未经修改的生产 MainActivity 原始 PNG](handoff/screenshots)；来源均为 main API35，逐图哈希见 PROVENANCE。
- [API29 签名包安装结果](handoff/api29/signed-install.json)、
  [API35 签名包安装结果](handoff/api35/signed-install.json)、
  [独立 apksigner 输出](handoff/main-signature-verified.txt)
- [全部基线源码文件哈希](handoff/source-main-sha256.json)。
- [本地软件模拟器失败原始证据](handoff/local-software-failure)单独保留，未计入任何通过数量。

## 初次基线失败与夹具修正

[运行39105217584](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39105217584)，
来源257681278576c52317581b41f6aae35cd5129a1e，生产源码与main完全相同。
API35执行147项，原有143项通过，新增4项均在夹具同步阶段超时；
API29执行147项，同样4项夹具超时，另有原有NavigationTest在保存窗口关闭后等待列表出现预期标的超时。
没有错误或跳过。这次不能作为配置交互缺陷已复现的证据。

原因：夹具按stock/futures插入，而DAO按ID返回futures/stock；测试错误地等待两种列表顺序完全相等。
改为先严格核对相同账户/风险内容及其他空表，再等待与实际数据库规范顺序一致的界面快照。
没有放宽任何交互、保存、重建或无障碍断言。原有NavigationTest暂不改动，保留失败，随完整矩阵重跑继续核实。

[原始失败XML、探针JSON、PNG、运行日志与来源哈希](failures/fixture-order/FAILURE.json)。

## 第二次基线：已复现的产品问题

[运行39106982341](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39106982341)，
来源6ea15bd6f8ec4b2b6196d1fa68406dfa698780f8，生产代码仍与main相同。
两个API均执行147项，原有143项全部通过。API29新增3项失败，API35新增4项失败；没有错误或跳过。
前次原有NavigationTest的超时在本次双API均未再次出现，保留前次失败，不据此宣称已查明原因。

- 两个API：策略及合约规则编辑经Activity重建后关闭，未保存的更正丢失。
- 两个API：风险提示、历史完整性、合约核实三个状态控件都没有关联的可读名称。
- API35：真实键盘输入风险上限时，窗口向上平移，标题可访问性范围[120,-102,520,-27]，visible=false。
  API29同一标题检查通过；修复需继续覆盖两个API。

[原始JUnit、4个探针JSON、所有原始PNG、logcat及逐文件来源](failures/product-baseline/FAILURE.json)，
[保留文件SHA256SUMS](failures/product-baseline/SHA256SUMS.txt)。
初次夹具失败与本次产品证据分别保留，没有把初次空首页图当作缺陷before图。

候选仅保存编辑开关/稳定ID/策略草稿、为状态控件建立整行名称和状态，并使用IME insets保持风险页面窗口位置。
不重设计现有页面。新增完整真实交互旅程使用生产MainActivity、系统DocumentsUI、五笔真实表单成交、
策略/规则更正及旋转、风险保存、ZIP导出/取消/覆盖恢复、外部force-stop新PID重开。
四配置为360dp/1.0与320dp/1.3的深浅主题；保留原有22次额外instrumentation，新增8次，合计30次/API。
交易事实和完整备份数据独立核验，只忽略每次导出必然变化的时间及传输校验字段。
为保留全部原测试，只有只读设备矩阵预算从45分钟增至60分钟；其他任务仍最多45分钟，
单次原生20分钟、额外instrumentation 600秒边界及签名/发布门禁不变。

候选实际结果仍待完整CI，不把测试实现或本地编译当作设备通过证据。

## 第一轮候选：四项修复通过，继续处理键盘保存

来源ac52d3b1c6340d037423ec7c9623572610d1b1c0，
[运行39108530795](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39108530795)。
构建成功，241 JVM零失败/错误/跳过，三种Lint无Error/Fatal；两个API各148项原生测试、2失败、0错误/跳过。
四项新增严格探针全部通过：两类编辑重建、三个有名状态控件、风险标题实际可见。

剩余两个失败分别是：横屏键盘下策略保存按钮不可见（两API actual buttonBoundsPx=null），
以及原有风险旅程点击时保存按钮未进入IME缩小后的视口。后一项保留全部原业务断言，
仅扩展原有视口稳定等待至风险列表，并对实际按钮精确滚动、强制可见后点击；未增加等待时限或隐藏键盘。
策略/规则复用原有ReviewForm键盘安全布局，字段、校验、候选构造及model写入调用不变。
新规则通过系统DatePicker设为2026-10-15生效，覆盖17日历史未知费用成交，确保新增参考参数不会回填历史null。

[原始截图/操作阶段/探针JSON/JUnit/logcat/来源哈希](failures/first-fix/FAILURE.json)。
这轮不是完整通过，不替代后续完整矩阵。

独立复核第一轮候选五笔真实成交时，发现新校验器误把averagePrice预期为含费单位成本11.0011。
原有MatchingEngine及MatchingEngineTest明确区分平均开仓价与含费剩余成本：正确值分别为11和5500.55。
已修正校验器预期并独立按剩余FIFO批次价格/数量计算均价，新增拒绝含费均价的回归。
原始成交、引擎、费用及成本公式完全不变；两API原始数据均通过纠正后的独立Decimal复核。
此为测试校验器定义错误，不作为产品财务缺陷。

## 窄高度与真实系统文件选择：继续验收

来源0436db74391425250cb64aa9d33297bf0438a400，
[运行39125043129](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39125043129)。
171项脚本、241 JVM、三种Lint均成功；两个API各148项原生、1失败、0错误/跳过。
**两API的原有143项及四项新探针全部通过**，原风险保存驱动补强已验证。

API29横屏保存触达范围[24,156,1016,252]，键盘顶部248，下缘重叠4px。
API35保存按钮在键盘上方，但原图显示输入区域已被操作行挤没；随后在系统Downloads选择ZIP时未完成文件选择。
原始系统截图显示文件网格和下方ZIP，驱动需要滚动实际目录；导出与回读阶段已经完成，恢复尚未完成。
[全部原始JUnit、探针、PNG、相关logcat与哈希来源](failures/short-viewport/FAILURE.json)。

仅策略/合约配置编辑启用低高度操作栏：键盘留下不足200dp时，取消/保存共用一行，保留可滚动输入区。
共享表单新增默认关闭的选项；现有计划/复盘/账户调用保持默认布局，继续完整回归。
同时验证两个配置编辑横竖屏：五次保存测量中四次要求输入区仍有可读行，所有按钮至少48dp且完全在键盘上方。
保留原所有业务断言，操作前后交易/费用/匹配/风险事实和整个备份内容仍严格比较。
系统文件选择补充真实目录滚动、文件名核对与失败原始层级诊断。
首次运行与新PID重开的PNG加入阶段名，独立检查文件集合不重叠，防止同名覆盖验收原图。
这轮尚不是完整通过，后续仍需完整API29/API35、全部原额外旅程及真实备份恢复、新PID验收。


## 第四轮：输入可读区域测量定义修正

来源7dcd4af1a9b23534ae121a238f3d6b7979329fe4，
[运行39128317494](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39128317494)。
API35原生148项、1失败、0错误/跳过；原143项及四个新探针全部通过。
原图已显示保存与输入文字。Save为[902,48,1024,144]、48dp，完整位于IME顶端271上方；
多行输入框[24,146,1016,272]的边缘超过IME 1px，可见输入区仍有62.5dp。
旧断言错误地要求整个多行框边缘都在IME上方，现改为实际可见交集至少24dp；
保存触达至少48dp且完整位于IME上方的要求不变。独立校验器重算交集并拒绝伪造、被遮挡或不足一行的输入区域。
[原始截图、JSON、JUnit与日志](failures/input-viewport-check/FAILURE.json)。
该轮不作为完整通过。API29原生148项零失败/错误/跳过，原有22次额外交互全部通过。新增regular-light首次旅程通过，重开时因测试选择器错误失败：持仓卡片合并语义后，数量子节点必须使用unmerged finder。原图明确显示500股，已保留精确文本和可见断言，修正查找方式。新PID为17633，首次17429；不把这轮计为完整通过。

复查发现同一共享布局也用于计划、复盘、账户和流水编辑。因此短高度分支统一按实际可用高度触发；
常规高度布局保留。新增四个表单横屏真实键盘、草稿旋转及取消后整个Ledger和完整备份事实不变的回归。
完整旅程共九次真实保存触达测量，四个额外表单仅检查并取消，不增加业务记录。
本地第五轮AndroidTest编译成功，174项脚本通过；这些不是设备通过证据，仍需双API完整CI。

第四轮API29的ZIP字节原先在重开成功后才提取，因此本轮重开失败时没有留存原始ZIP，不能宣称独立ZIP核验通过。后续改为首次旅程成功后立即提取并校验，再进行进程重开；全部原有断言保留。


## 第五轮：系统选择器快速预览误点击

来源94fa91fbef8216bb01df573bd265ace00f610283，
[运行39132452369](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39132452369)。
174脚本、241 JVM、三Lint成功；API35原生148项、1失败、0错误/跳过，原143项和四新探针全部通过。
策略和合约横竖屏、风险的五次保存/输入测量均通过；策略/规则横屏可见输入交集62.5dp，保存触达48dp且完整位于键盘上方。

新完整旅程在系统文件选择时失败。原始日志明确显示精确文件名未找到，随后contentDescription模糊匹配点击[264,848]，触发QuickViewIntentBuilder；这是ZIP缩略图的快速预览按钮，不是文件名选择。
后续仅修测试驱动：使用系统列表视图入口、真实目录滚动和精确文件名文本，记录实际标题/控件/点击范围；未返回时保留系统层级。
App备份实现与格式不变，不将这次驱动失败视为产品恢复失败。
[原始PNG、五探针JSON、JUnit、HTML与完整case日志/哈希](failures/document-preview-selection/FAILURE.json)。
四个新增共享表单取消回归在本次API35尚未执行。API29完整成功：148项原生零失败/错误/跳过、原22次加新增8次额外交互全部通过，四配置九次键盘触达、四个真实ZIP、整库恢复与不同PID重开均通过独立核验。成功归档12140405159，SHA-256为2d85e4a8b0ebf584afe3fbca04e4faed3eeb71b6639b2540bb5adc2ffc019b42，603个文件均核对；相应原始JSON/ZIP/XML/30份操作日志及横屏截图已一并留档。该轮整体仍因API35失败，不计为完整通过。

## 第六轮：新增完整旅程通过，原有系统目录选择驱动失败

来源14e60740fd1df6d86d75e7383b76cdba9398200e，
[运行39171159119](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39171159119)。
API35全部148项原生零失败/错误/跳过，原143项与新增5项均通过。
新增旅程实际选择两次精确ZIP文件名，完成导出、预览取消、覆盖恢复及四个共享表单取消；
九次键盘保存/输入检查通过。四个共享表单横屏原图已逐一查看，标题、保存和输入可见。
独立Decimal按原始JSON复核财务事实通过；本次失败归档没有保留原生旅程导出的ZIP字节，不能宣称完成独立ZIP核验。

随后原有额外成交旅程在系统目录选择处抛出StaleObjectException（TradeEntryExperienceTest.kt:284）。
原始截图显示导航抽屉打开；原始层级中Downloads同时出现在面包屑、工具栏和目录列表。
原驱动没有等待抽屉稳定，且全局文本匹配可命中失效的背景节点。
后续仅修该系统驱动：等待系统空闲，限定roots_list中的Downloads，真实滚动dir_list并核对精确文件名。
保留全部原业务、回读、恢复及财务断言；生产代码一字未变。
[原始XML、PNG、层级、操作日志及逐文件哈希](failures/entry-picker-stale-node/FAILURE.json)。
API29也已结束：原生148项零失败/错误/跳过；九个键盘检查及独立财务复核通过。
随后同一原有额外成交旅程未找到导出ZIP（原测试第285行）：原始PNG显示列表前六个文件夹占满视口，
刚导出的entry-proof ZIP在下缘系统导航下方，原始层级只暴露文件夹；旧驱动没有滚动目录。
后续驱动补齐实际dir_list滚动，不使用模糊contentDescription或直接URI注入。
该轮整体失败，完整双API四配置、新PID及原始ZIP验收仍待后续。

第七轮使用仅增加上述测试驱动修正的9143d5d256e440b27361a45d62dd45115672fc61，
在临时只读分支codex/v210-consistency-c7-device-check上手动运行相同完整矩阵，
[运行39173642117](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39173642117)。
临时分支用于保留第六轮仍在执行的证据，仍只交付原独立PR #21；不触发永久签名或发布。

## 第七轮首次：原图读取返回255

来源9143d5d256e440b27361a45d62dd45115672fc61，run39173642117/attempt1。
API35原生148项零失败/错误/跳过；原成交和备份旅程以及新PID重开通过，原系统目录选择修正已验证。
共有19次已执行额外instrumentation全部OK (1 test)，三个完整配置的原首页/统计及计划/复盘/账户事实独立复核通过。
最后narrow-dark首页统计首次UI日志和JSON均为PASS，原JSON及empty-home-calendar.png已读出，
下一张empty-home.png的adb shell -T run-as cat返回255，后续finally收集也失败。
原stderr没有保存，底层原因未知；实际缺失的图片或JSON没有重建，未计为整轮通过。
[原始证据及来源哈希](failures/adb-evidence-transfer/FAILURE.json)。
计划同源码重跑失败的完整API35任务，保留首次失败。GitHub返回403“workflow run already running”，
因此先等API29完成，再申请单任务重跑。

## 第七轮同源码重跑：原有完整矩阵通过，新增窗口包名探针失效

同source9143d5d256e440b27361a45d62dd45115672fc61，run39173642117/attempt2，API35 job117823233021。
148项原生零失败/错误/跳过，原22次额外交互完整通过，四配置旧full/review事实独立校验通过。
新增regular-light的前五个配置阶段完成，五个键盘测量及独立财务检查通过；CreateDocument已经显示真实Save界面，
随后缓存UiObject2.applicationPackage刷新失败抛StaleObjectException。原生narrow-dark曾通过同一旅程，额外regular-light遇到窗口加载切换。
本次没有完成该配置实际ZIP、四个共享表单或新PID阶段，不能计为新完整旅程通过。
[原始证据与来源哈希](failures/document-window-package-probe/FAILURE.json)。
首次adb原图读取255在此轮未复现；原始原因仍未知，不宣称已经定位或修好。

第八轮仅改新测试的包名就绪读取：真实UiAutomation窗口根节点快照、20秒原有期限、加载完成后选择目录。
另为两处实际丢失stderr的采集错误保留原始字节及哈希；读失败仍失败，不重试操作或UI。
新增两项回归验证上述失败证据和单次执行语义；本地176脚本与AndroidTest编译通过，生产代码与前轮相同。
