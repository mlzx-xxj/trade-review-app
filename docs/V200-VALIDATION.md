# V2.0 分阶段真实验证

基线main f0323e38，PR #10开发草稿，不自动合并。未执行的测试不记为通过。

阶段1/2：首轮Actions [38359482356](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38359482356)，源abdf05f，KSP发现重复ledger DAO getter（三次生成追加），Android编译失败，JVM和模拟器测试未执行。修复为单一DAO入口，保留全部33项新增核心测试及2项迁移测试，重新运行完整CI。

Room v8生成schema仍待CI核对；不把静态SQL检查当成迁移通过。

签名延用原V0.3 pin；此前V1.0 configured=false不等于本轮已验证，新版本最终通过实际工作流核对。未读取或生成替代长期密钥。

修复后的Actions [38360365350](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38360365350)，源20a0141：KSP冲突消除，JVM实际执行184项，183通过/1失败。成交额收费测试把原按手模板的平仓3元/平今10元直接当作费率，严格校验正确拒绝大于1的费率。改为完整且合法的成交额费率模板，保留1.05元精确结果原断言；未关闭费率范围校验。模拟器未执行，重新完整验证。

核心修复Actions [38361328708](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38361328708)，源e7c1e20：构建/JVM184项全部成功；API29原始XML实际105项全部成功，包括新增v7→v8及v1→v8两项迁移。随后进程探针仍硬编码旧schema7，拒绝v8，整个API29任务失败（不能记为全套通过）。探针更新到正式schema8，保留原十六表检查并增加全部新账本表一致性，未降低检查。API35在应用测试之前被平台启动ANR拒绝，回归未执行，日志保留。
