> 签名政策更新：本文保留 V2 初始开发时的历史状态。Issue #12 已授权新的永久 V2 证书，旧 V0.3 私钥丢失、两者不兼容。当前门禁/验证以 [永久签名迁移记录](PERMANENT-SIGNING-V2-VALIDATION.md) 和 [手机签名指南](V200-PHONE-SIGNING.md) 为准，不能执行本文历史原证书覆盖升级假设。

# V2.0 分阶段真实验证

基线main f0323e38，PR #10开发草稿，不自动合并。未执行的测试不记为通过。

阶段1/2：首轮Actions [38359482356](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38359482356)，源abdf05f，KSP发现重复ledger DAO getter（三次生成追加），Android编译失败，JVM和模拟器测试未执行。修复为单一DAO入口，保留全部33项新增核心测试及2项迁移测试，重新运行完整CI。

Room v8生成schema仍待CI核对；不把静态SQL检查当成迁移通过。

签名延用原V0.3 pin；此前V1.0 configured=false不等于本轮已验证，新版本最终通过实际工作流核对。未读取或生成替代长期密钥。

修复后的Actions [38360365350](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38360365350)，源20a0141：KSP冲突消除，JVM实际执行184项，183通过/1失败。成交额收费测试把原按手模板的平仓3元/平今10元直接当作费率，严格校验正确拒绝大于1的费率。改为完整且合法的成交额费率模板，保留1.05元精确结果原断言；未关闭费率范围校验。模拟器未执行，重新完整验证。

核心修复Actions [38361328708](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38361328708)，源e7c1e20：构建/JVM184项全部成功；API29原始XML实际105项全部成功，包括新增v7→v8及v1→v8两项迁移。随后进程探针仍硬编码旧schema7，拒绝v8，整个API29任务失败（不能记为全套通过）。探针更新到正式schema8，保留原十六表检查并增加全部新账本表一致性，未降低检查。API35在应用测试之前被平台启动ANR拒绝，回归未执行，日志保留。

阶段2 Actions [38365208615](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38365208615)，源0417700：编译成功，191 JVM执行189通过/2失败。两个原非法JSON测试仍查找写死schema6，版本7中替换未命中，实际把原有效JSON送入“应拒绝”断言。改用当前schema常量，并维持未知版本/重复字段/严格类型/深度的全部拒绝断言。新增7项账本备份/统计前提测试通过；本轮模拟器未执行。

后端阶段Actions [38368451775](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38368451775)，源20b9992：构建/JVM/Lint任务成功，API35全任务成功。API29实际114项113通过/1失败：旧周报UI在后台派生结果尚未就绪时开始滚动，找不到report-net-CNY，整体失败；保留原金额断言，增加实际报告就绪语义及等待，未关闭异步计算或删测试。此运行仍为旧主UI，不代表V2界面验证。

V2实际MainActivity首轮Actions [38381713696](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38381713696)，源1890863：Compose编译因dp/drawIntoCanvas缺少导入及ReceiptLong图标接收者错误失败；已修复。不把本地47项Python成功当作Android成功，本轮JVM及模拟器未执行。

界面修复后Actions [38382269969](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38382269969)，源6b72c6a：主Compose代码编译冲突消除，测试APK编译发现新增往返测试对String调用inputStream而非byteInputStream；已改正并保留往返断言。本轮完整任务失败，模拟器未执行。新增实际Main失败截图/界面树采集，不删失败测试。

V2编译与领域完整阶段Actions [38383179786](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38383179786)，源0661583：原始XML确认207 JVM全部通过（0失败/错误/跳过），含Matching35、ChartScale11、LedgerBackup7、LedgerCsv3；Debug/RC/Release全部编译，Lint错误0，Debug警告24、RC/Release各25。

同轮下载RC独立apksigner/BinaryManifest复核：2.0.0-rc.1/code20/com.tradereview.app.rc.v200/non-debuggable；仅自有DYNAMIC_RECEIVER_NOT_EXPORTED权限，无INTERNET/存储媒体权限。APK53012944字节，SHA-256 6b918c25ece0a1176230e7d22dfdd54a7383f74a8805d2750f6053dd2c342861，临时证书1c73196dae5b31835d2e2d74a112057c2515f055d48222fb5206bdcdf1bac31d。实际SIGNING-STATUS configured=false，原pin未验证、原固定签名升级未执行。以上仅此提交，API29/35仍待完整结果。

在途完善：保留失败artifact、按每个API最新尝试选择原始证据且禁止回退旧绿色；ZIP新旧数据及两张截图的事务故障测试；同合约同生效日规则拒绝及旧费快照不重定价测试；移除新关键价位可变集合警告的ListSaver；账户/CSV以索引复用匹配，不改变财务公式。均需下一完整CI验证。

同轮API29/35均实际121项，116通过/5失败/0跳过：旧NavigationTest两项仍绑定新Main，与另一个旧Activity兼容宿主不一致；新主UI两项合成成交ID用连字符，稳定ID校验正确拒绝；一项策略表单字段调用滚动但对话框没有滚动布局。保留全部原断言，原导航绑定Debug兼容宿主、合成ID改合法下划线、策略与账户弹窗加实际滚动。新增10项Repository、2项完整迁移及其他主UI三项通过，不能记为全套成功。

随后进程探针因Main未传clearWork=true而拒绝遗留暂存文件；修复为Application按进程初始化一次，完成前阻止文件UI操作，旋转不清除正在预览的ZIP，失败显示可恢复入口。原V1升级Java探针加显式target_schema=8参数，原V1脚本仍默认7；原长期密钥缺失，真实固定签名升级依然未执行。重新完整CI，保留失败原始XML/日志/截图。

Actions [38386967794](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38386967794)，源1b4c01d：主代码编译通过，新增可访问性数值测试把CustomActions写在SemanticsProperties而非本Compose版本的SemanticsActions，测试APK编译失败；已修正命名空间，保留准确值及空日期断言。模拟器及发布未执行，本地50 Python通过不冒充本轮Android通过。

Actions [38387969242](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38387969242)，源a19a968：构建/JVM/Lint任务成功；API29及35各实际123项，122通过/1失败/0跳过，均为新主UI链在LazyColumn尚未创建new-cashflow按钮时直接performScrollTo。保留资金/恢复全部断言，改用父列表滚动找到按钮后点击。12项账本Repository（含新ZIP整体回滚、规则唯一性/旧费不重价）、2项迁移、另外5项实际Main UI全部通过。API35进程探针三次重启验证28表全部不变/图片SHA不变，实际非调试RC安装重启成功；原固定签名及真机仍未执行。整个任务失败，发布拒绝，不能标为全套成功。

下一提交同时完成：周月结果绑定对应周期/市场/数据后才显示，旧策略历史关联只读档案，合成月图包含正负/持平/空日期及旧USD截图且不混入CNY；单笔乘数核实不能升级未核实手续费规则，规则生效日晚于交易日拒绝自动费用；同秒也须确认序号，不丢弃其他已知秒的实际排序。新增纯/JVM及设备断言，要求下一209 JVM及124设备全量验证。

Actions [38395312906](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38395312906)，源54efbfa：原始XML确认209 JVM全部通过（含Matching37、LedgerBackup7、ChartScale11、LedgerCsv3，0失败/错误/跳过）；Debug/RC/Release编译成功，Lint0错误，Debug20警告、RC/Release各21。实际configured=false，原pin/真实旧签名升级/手机验收未执行。最后截图调整提交触发最终全CI，此阶段尚未执行的模拟器不记为通过。

最终取证调整仅测试滚动位置：月图保留持平/无交易截图57，并把截图48滚动到真实合成盈亏日期；品种图截图49滚动到包含负值的行。金额和算法不改。每次最终提交都执行完整CI，没有skip ci；最终证据以精确HEAD的Actions、Release CI-RESULTS.txt及真实PNG为准。

Actions [38396858142](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38396858142)，源c251d648：209 JVM全部通过，编译及Lint成功（0错误，Debug20警告、RC/Release各21）。API29/35各实际124项，123通过/1失败/0跳过；失败发生在恢复流程，长预览Lazy项中的确认操作未进入可触摸视区，未打开二次确认。真实截图55显示仍是预览，不冒充确认完成；图表48/49已确认真实正负柱和零轴。将恢复操作拆成独立Lazy项、全宽52dp按钮，测试保留ZIP前后完整相等断言，并要求确认弹窗实际可见。发布门禁正确拒绝本轮失败结果，重新执行完整CI。
