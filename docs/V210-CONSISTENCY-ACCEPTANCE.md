# V2.1 第三批入口检查（已完成）

核实时间：2026-10-18 UTC。仓库：mlzx-xxj/trade-review-app。

[完整成功的 main CI #242](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39101525143) ·
[原始证据来源及逐文件 SHA-256](v210-consistency/handoff/PROVENANCE.json) ·
[保留文件 SHA256SUMS](v210-consistency/handoff/SHA256SUMS.txt)

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

- [main 完整运行结果与7个任务](v210-consistency/handoff/main-ci-success.json)
- [API29 原始 JUnit XML](v210-consistency/handoff/api29/TEST-native.xml)、
  [API35 原始 JUnit XML](v210-consistency/handoff/api35/TEST-native.xml)
- [全部 JVM 原始 XML](v210-consistency/handoff/build-evidence/test-results)、
  [Debug Lint](v210-consistency/handoff/build-evidence/lint-results-debug.xml)、
  [RC Lint](v210-consistency/handoff/build-evidence/lint-results-rc.xml)、
  [Release Lint](v210-consistency/handoff/build-evidence/lint-results-release.xml)
- [8张未经修改的生产 MainActivity 原始 PNG](v210-consistency/handoff/screenshots)；来源均为 main API35，逐图哈希见 PROVENANCE。
- [API29 签名包安装结果](v210-consistency/handoff/api29/signed-install.json)、
  [API35 签名包安装结果](v210-consistency/handoff/api35/signed-install.json)、
  [独立 apksigner 输出](v210-consistency/handoff/main-signature-verified.txt)
- [全部基线源码文件哈希](v210-consistency/handoff/source-main-sha256.json)。
- [本地软件模拟器失败原始证据](v210-consistency/handoff/local-software-failure)单独保留，未计入任何通过数量。

## 初次基线失败与夹具修正

[运行39105217584](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39105217584)，
来源257681278576c52317581b41f6aae35cd5129a1e，生产源码与main完全相同。
API35执行147项，原有143项通过，新增4项均在夹具同步阶段超时；
API29执行147项，同样4项夹具超时，另有原有NavigationTest在保存窗口关闭后等待列表出现预期标的超时。
没有错误或跳过。这次不能作为配置交互缺陷已复现的证据。

原因：夹具按stock/futures插入，而DAO按ID返回futures/stock；测试错误地等待两种列表顺序完全相等。
改为先严格核对相同账户/风险内容及其他空表，再等待与实际数据库规范顺序一致的界面快照。
没有放宽任何交互、保存、重建或无障碍断言。原有NavigationTest暂不改动，保留失败，随完整矩阵重跑继续核实。

[原始失败XML、探针JSON、PNG、运行日志与来源哈希](v210-consistency/failures/fixture-order/FAILURE.json)。

## 第二次基线：已复现的产品问题

[运行39106982341](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39106982341)，
来源6ea15bd6f8ec4b2b6196d1fa68406dfa698780f8，生产代码仍与main相同。
两个API均执行147项，原有143项全部通过。API29新增3项失败，API35新增4项失败；没有错误或跳过。
前次原有NavigationTest的超时在本次双API均未再次出现，保留前次失败，不据此宣称已查明原因。

- 两个API：策略及合约规则编辑经Activity重建后关闭，未保存的更正丢失。
- 两个API：风险提示、历史完整性、合约核实三个状态控件都没有关联的可读名称。
- API35：真实键盘输入风险上限时，窗口向上平移，标题可访问性范围[120,-102,520,-27]，visible=false。
  API29同一标题检查通过；修复需继续覆盖两个API。

[原始JUnit、4个探针JSON、所有原始PNG、logcat及逐文件来源](v210-consistency/failures/product-baseline/FAILURE.json)，
[保留文件SHA256SUMS](v210-consistency/failures/product-baseline/SHA256SUMS.txt)。
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

[原始截图/操作阶段/探针JSON/JUnit/logcat/来源哈希](v210-consistency/failures/first-fix/FAILURE.json)。
这轮不是完整通过，不替代后续完整矩阵。
