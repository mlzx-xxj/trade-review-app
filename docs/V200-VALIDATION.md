# V2.0 分阶段真实验证

基线main f0323e38，PR #10开发草稿，不自动合并。未执行的测试不记为通过。

阶段1/2：首轮Actions [38359482356](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38359482356)，源abdf05f，KSP发现重复ledger DAO getter（三次生成追加），Android编译失败，JVM和模拟器测试未执行。修复为单一DAO入口，保留全部33项新增核心测试及2项迁移测试，重新运行完整CI。

Room v8生成schema仍待CI核对；不把静态SQL检查当成迁移通过。

签名延用原V0.3 pin；此前V1.0 configured=false不等于本轮已验证，新版本最终通过实际工作流核对。未读取或生成替代长期密钥。
