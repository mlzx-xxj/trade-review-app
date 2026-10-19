# V2.1 第三批：视觉一致性与真实交互验收

第八轮双API各148项原生测试仅原有日历链路失败：找不到金额节点；新增五项全部通过。
第九轮补原日历驱动的具体日期滚动、节点就绪及失败诊断，重新进行完整CI；不能将此前局部通过当作整轮成功。
独立[PR #21](https://github.com/mlzx-xxj/trade-review-app/pull/21)，最新受测源码以PR head及CI SOURCE_COMMIT为准。

## 入口交接检查

先核实入口条件，再从最新main创建codex/v210-consistency-acceptance。
2026-10-18 22:35 UTC再查远端main仍是同一提交。

| 项目 | 实际核实结果 |
| --- | --- |
| PR #20 | 2026-10-18 05:43:22Z合并至main |
| main | 67e97ccfaffbfaae10be7d796bb8d5b29454bb35，与PR #20 head的树一致 |
| 合并后完整CI | [run39101525143 / #242](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39101525143)，7个任务全部成功 |
| 原始JVM / Lint | 241 JVM零失败/错误/跳过；Debug、RC、Release均无Error/Fatal |
| 原始API29 / API35 | 各143原生零失败/错误/跳过，另各22次真实UI instrumentation成功 |
| 正式身份 | 2.0.0 / code22 / com.tradereview.app |
| 数据协议 | Room v9 / 28实体，JSON v8 / ZIP v1，迁移链保留 |
| 既有Stable | v2.0.0，非prerelease，tag来源dc4e20259eeb4248b66cedbedc644e9e559c98a9 |
| 永久证书pin | 29b2f82887a0591d5b1d1f9ab148b90c0b658088530e3bcc1bd7e5fee64f2af9 |

[入口原始清单和哈希](v210-consistency/handoff/PROVENANCE.json)、
[完整CI原始状态](v210-consistency/handoff/main-ci-success.json)、
[独立apksigner原始输出](v210-consistency/handoff/main-signature-verified.txt)。
实际Stable和合并后main正式候选APK的公开文件哈希及v2/v3签名均已核对，二者不是同一个APK。
入口的两个正式安装任务成功，但没有较早永久签名APK的覆盖升级fixture，不能宣称已验证同签名升级。

## 有证据支持的修复

生产代码只改变四个UI文件，未扩展功能。

[MainActivity](../app/src/main/java/com/tradereview/app/MainActivity.kt)、
[风险/策略](../app/src/main/java/com/tradereview/app/v200/V200Risk.kt)、
[账户控件](../app/src/main/java/com/tradereview/app/v200/V200AccountsStats.kt)、
[共享表单](../app/src/main/java/com/tradereview/app/v200/V210ReviewComponents.kt)。

| 已复现的问题 | 最小修复 |
| --- | --- |
| 策略/合约编辑重建后关闭、草稿丢失 | 保存编辑开关、稳定ID及草稿，按ID查找实体 |
| API35风险键盘输入将标题平移出屏幕 | MainActivity使用resize交付IME insets；风险列表消费IME空间 |
| 风险提示、合约核实、账户完整性缺少可读名称 | 文字与状态合为可切换整行，保留原回调，触达至少48dp |
| 横屏键盘挤掉共享表单输入或保存区域 | 实际可用高度不足200dp时，取消/标题/保存共用操作行，正文滚动；正常高度共享布局保留 |

策略/合约编辑复用已有ReviewForm，取代原AlertDialog，因此不宣称原风险弹窗外观完全未变。
交易、持仓、首页、统计及其他已完成页面保留现有设计。
同一短视口修复覆盖计划、复盘、账户、流水，并补充真实键盘、旋转草稿及取消后整库事实不变回归。
原始问题、失败截图及每轮修正见[开发记录](v210-consistency/DEVELOPMENT-HISTORY.md)。

## 页面审查范围

| 页面 | 审查内容与处理 |
| --- | --- |
| 成交、持仓 | 沿用实际表单保存、历史成交、部分平仓及详情旅程；核对窄屏大字体、费用与数量、未知金额；保留现有设计 |
| 首页、统计、图表、日历 | 沿用四配置旅程及周/月、市场、已知/未知样本事实校验；统计实现与main逐字节一致 |
| 计划、复盘、账户、流水 | 保留原实际保存旅程；补横屏IME、草稿旋转与取消后整库不变，修共享短视口布局 |
| 风险、策略 | 复现并修标题平移、策略草稿/稳定ID、状态控件名称和触达问题；实际保存及新PID重开验证 |
| 合约规则 | 复现并修重建关闭及无名核实控件；实际日期选择、参数保存、历史快照不回填验证 |
| 备份、恢复 | 实际系统文件选择、导出回读、预览取消、两次确认覆盖恢复；修系统选择测试驱动，App备份实现保持原状 |

无障碍覆盖实际可访问性树中的名称、角色、状态和可见触达尺寸；没有真人TalkBack语音验收。
独立审核还可按原始PNG逐张复看密度、层级及主题表现。
日历和日柱图保留已有横向滚动，原图展示当前视口；截图索引另含周/月图表、红盈绿亏分布及未知费用日历。

## 测试范围和当前状态

真实API29/API35模拟器运行生产com.tradereview.app.MainActivity；
各覆盖360dp/字体1.0与320dp/字体1.3的深浅主题。

| 验收内容 | 完整验收要求 |
| --- | --- |
| JVM | 原241项用例集合完整保留，零失败/错误/跳过 |
| Lint | Debug、RC、Release均无Error/Fatal；既有Warning如实保留 |
| 脚本 | 原157项加17项事实/几何校验及2项失败证据回归，共176项 |
| 原生 | 每API精确包含原143项加新增5项，共148项，零失败/错误/跳过 |
| 额外交互 | 每API原22次加新增8次，共30次真实instrumentation |
| 实际保存 | 五笔成交均用真实表单；策略、规则、风险通过UI保存 |
| 键盘/重建 | 每旅程9次按钮及输入测量，四个共享编辑器旋转并取消后整库不变 |
| 系统备份 | 真实DocumentsUI创建、回读、预览取消、两次确认覆盖恢复；各API保留4个实际ZIP |
| 进程重开 | 外部force-stop后确认不同PID，从真实页面及数据库重新读取保存事实 |

第六轮14e6074双API原生148项均通过，241 JVM、174脚本及三Lint通过；
随后原额外成交旅程的系统文件选择驱动失败，所以整轮失败。
API29文件在首屏下缘系统导航下方，旧驱动未滚动；API35原目录导航行抛StaleObjectException，
具体失效UiObject2未记录。后续仅补目录稳定等待、roots_list范围及dir_list真实滚动，保留所有业务断言。
[双API共88份原始文件及来源哈希](v210-consistency/failures/entry-picker-stale-node/FAILURE.json)。

原107份测试源码中104份逐字节不变，仅原风险保存、系统目录选择、日历日期点击三个驱动作必要修正；原业务断言和用例名称保留。
新增5项原生用例覆盖稳定ID/草稿、键盘标题、控件语义及完整配置/备份旅程。
第七轮先在临时只读分支执行相同完整矩阵，避免推进开发分支影响前轮证据收集；最终统一交付PR #21。

第七轮API35首次148项原生及19次已执行的额外交互全部通过，原系统备份选择驱动和新PID重开已验证。
regular-light、regular-dark、narrow-light的首页/统计及计划/复盘/账户首轮与重开事实已独立核对。
narrow-dark的首页统计首次原始instrumentation为OK (1 test)，完整JSON为PASS，JSON和前一张PNG已读出；
随后run-as cat读取empty-home.png返回255，finally中的读取也失败。
原采集器没有保存该命令stderr，底层原因未知；不能据此认定产品缺陷，也不能计为整轮通过。
[原始JUnit、19份操作日志、实际保留的JSON/PNG、完整job日志与哈希](v210-consistency/failures/adb-evidence-transfer/FAILURE.json)。
API29随后完整成功：603个归档文件全部核对来源、大小和SHA-256，148项精确包含原143加新增5项，
30份原始交互日志通过；四配置实际ZIP、完整备份事实、财务不变性及不同PID重开均通过独立复核。
归档12155420006，ZIP SHA-256为6ef8774118ef285804691ea4d0aba824ebe9de2157f127f7730f5018b009ebd2。
API29的产物来自attempt1；GitHub复用该成功结果，API35于23:41 UTC以同源码开始attempt2完整任务。
该轮构建产物同样来自attempt1；所有归档逐项记录实际runAttempt。

API35 attempt2完成了148项原生和原22次额外交互，四配置首页/统计及计划/复盘/账户事实独立校验通过。
新增regular-light旅程的五笔成交、策略/风险/规则保存及五个键盘测量通过；随后系统Save窗口已打开，
驱动读取缓存UiObject2.applicationPackage时对象失效。首次原图读取255没有在此轮复现，底层原因仍未知。
[原始截图、JUnit、完整22份通过及1份失败日志、JSON、财务与来源哈希](v210-consistency/failures/document-window-package-probe/FAILURE.json)。
第八轮改用实时取得的原生窗口根节点快照确认真实DocumentsUI包名，保留原20秒等待及真实文件操作。
同时为两处已有采集失败路径保存退出码、命令、原始stdout/stderr和哈希，不重试读取或UI、不改变失败结果。
两项对应回归加入原有脚本套件，本地176项全部通过；AndroidTest编译成功。

## 财务事实核验

独立Python Decimal按真实成交顺序重新计算，不调用App匹配/统计实现。
以下是新增旅程固定合成场景，不能与原有旅程的其他合成金额混用。

| 事实 | 预期 |
| --- | --- |
| 股票FIFO | 先平首批1000股，再平次批500股，剩余500股 |
| 股票已实现净盈亏 | 2487.65＝毛盈亏2500－分配开仓费1.55－平仓费10.80 |
| 总费用 / 剩余成本 / 均价 | 12.90 / 5500.55 / 11（均价不含费，成本包含未分配费用） |
| 期货 | 剩余1手，毛盈亏1000；历史费用、净盈亏、保证金仍为null，UI待估算 |
| 新参考规则 | 生效日早于旧成交，仍不回填旧快照的未知费用/保证金 |
| 配置及恢复 | 交易事实不变；完整ZIP数据、旧表、配置及附件亦核对 |

完整备份比较仅规范化每次导出变化的exportedAt和checksumSha256。
独立检查ZIP CRC、清单、逐项SHA-256、JSON v8/ZIP v1，以及财务文字至少14sp、无溢出、国内盈利为红色。
原有亏损、图表、日历等回归保留。
校验器曾误把含费单位成本当作平均开仓价，已按原引擎定义纠正，产品公式没有变化。
输入可读性改为实际可见交集至少24dp；保存触达至少48dp且完整高于IME的严格要求始终保留。

## 交付计划

1. 已完成入口main、完整CI、版本、签名及源码检查。
2. 已复现产品问题并完成最小UI修复，保留全部失败，必要驱动修正已编译成功。
3. 已完成第七轮API29完整验收及原始归档复核；正在准备第八轮双API完整矩阵，成功后核对其实际产物。
4. 将成功原始PNG、日志、JUnit XML、Lint、ZIP、来源SHA、逐文件哈希及离线复核脚本/报告提交本PR，核验最终提交CI后等待用户与ChatGPT审核。

FIFO、费用、保证金和统计公式、Room schema、正式包名、备份格式、长期签名和版本保持原状。
不自动合并、不发布Stable、不操作真实手机数据。
