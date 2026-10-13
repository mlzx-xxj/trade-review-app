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
