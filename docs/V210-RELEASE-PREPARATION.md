# V2.1.0 发布准备

入口 main：`0120b43fe91f1deffa6188a4df047d81c58c7a1f`。
[CI 39208803310](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39208803310) 在开始修改前已 completed/success，七个任务全部成功。

## 最小修改范围

- 正式版本：`2.1.0 / versionCode 23`；正式包名仍为 `com.tradereview.app`。
- 永久 V2 证书仍为 `29b2f82887a0591d5b1d1f9ab148b90c0b658088530e3bcc1bd7e5fee64f2af9`；不创建、轮换或在 PR 中读取密钥。
- 身份校验严格要求新版本；交付报告使用实际 APK 身份，候选标题显示 V2.1.0。
- 为减少传递协议变更，既有 `v200` 脚本名、`TradeReview-v2.0-*.apk` 文件名、CI 候选 tag 前缀及隔离 RC 包名保留；它们不是实际 APK 版本的依据。RC 仍独立为 `com.tradereview.app.rc.v200`，版本 `2.1.0-rc.3 / 23`，不能用于正式覆盖升级。
- 不改变业务 Kotlin、UI、FIFO、费用、保证金、统计、Room schema 或备份格式。原完整测试、固定 action SHA、事件门禁、权限和签名环境保持；正式安装任务也绑定既有 `v03-signing-main` 环境。

## 新增的正式覆盖升级检查

仅在受保护 main 的正式签名安装任务运行，API29/API35 各执行一次；PR 不执行这一阶段。

1. 从 GitHub 非草稿、非预发布的 Stable `v2.0.0` 下载原始 `TradeReview-v2.0-fixed-candidate.1.apk`。同时核对 Release 身份、服务器 digest 和实际文件 SHA：`d157327870baa1dcd6d777de8ffcc95b1c504020a7c450ad615c7f56a6f92b5d`。
2. 实际 `aapt` / `apksigner` 核对旧包 `2.0.0 / 22`、新包 `2.1.0 / 23`、同正式包名、non-debuggable、同永久证书。错误证书、RC、旧文件变化或版本码未递增立即失败。
3. 在可丢弃模拟器安装原 Stable，通过真实系统 DocumentsUI 导入固定合成 ZIP，再由旧 App 实际导出 `before.zip`。它含五笔成交、未知费用、部分平仓、策略/规则/账户、旧档案及一张附件；必须与 fixture 全部事实相等。
4. force-stop 后 `adb install -r` 正式候选，不在这一区间卸载或清数据；验证 Android 安装成功、实际安装版本码23，启动生产 MainActivity，再实际导出 `after.zip`。
5. 比较升级前后整个结构化备份及附件哈希，只忽略导出时间和随之变化的 checksum。任何业务字段丢失都失败。
6. 仅在上述保留检查成功后，清空该模拟器的合成应用数据。导出 `empty.zip` 确认成交、旧交易和附件确实为空，再通过真实预览、覆盖选择及两次确认恢复旧 App 的 `before.zip`，导出 `restored.zip` 并再次比较全部事实及附件。
7. 成功标记必须为 `samePermanentSignerUpgrade=passed-stable-v2.0.0-to-v2.1.0`。打包及候选交付阶段重新核对 API、签名身份、候选 APK SHA、四个 ZIP 的原始哈希和业务内容；缺少材料或检查失败阻断交付，不退回“跳过”。

原有可选 V0.3 异证书拒绝安装检查保留，与此同证书成功升级检查分开。
`FIXED-IDENTITY.json` 在签名阶段生成，其 `sameSignerUpgrade=not-executed` 只描述当时尚未安装；最终升级结果以两个 API 的正式安装证据为准。

## 验证范围与结果

开发阶段仅定向验证：81项 Python 安全/身份/签名/升级边界测试通过，包括拒绝错误包/证书/版本、拒绝损坏 Stable 下载、拒绝缺失或被改动的恢复证据、拒绝在未清空数据时冒充恢复成功，以及覆盖安装失败后不清数据。
工作流结构检查、私钥文件扫描、Python/Bash语法检查通过。上述模拟测试不代表真实签名或设备升级成功。

公开 Stable 原始 APK 已实际检查：正式包名、`2.0.0 / 22`、non-debuggable、永久证书及上述文件 SHA 均相符。
本地隔离 RC 与未签名 Release 构建成功（4分55秒）。实际 APK 检查确认：Release 为 `com.tradereview.app / 2.1.0 / 23 / non-debuggable` 且未签名；RC 为 `com.tradereview.app.rc.v200 / 2.1.0-rc.3 / 23 / non-debuggable`。
本地 RC 临时证书 SHA 为 `92ef36bf522b7a657c1dab2bc1cbc0edcc5d2a2cb46a565c66429418e27b390e`，与永久证书不同。未执行永久签名或把 RC 当成升级包。
最终提交后仅由 PR 自动运行一次原有完整 CI；CI 链接及实时结论见 PR 描述。不额外手动 dispatch 或循环重跑。

## 合并后审核清单

由 ChatGPT 和所有者先独立审核本 PR。审核合并后，在该合并 SHA 的既有 main CI 中检查：

- 完整构建、JVM、Lint、API29/API35原有及新增回归全部成功后，才进入受保护永久签名阶段。
- 正式安装的两个任务均成功；检查 `signed-install-api29-attemptN`、`signed-install-api35-attemptN` 的 `MANIFEST.json`、`package-probes-api*.json`、`same-signer-upgrade/upgrade.json`、操作日志和四个 ZIP。
- 来源必须对应合并 SHA；旧 APK SHA、同永久证书、22→23、实际安装及数据保留/恢复全部核实。PR CI 的签名任务跳过不能当成这些检查已通过。
- main 自动 CI 若已完成，不重复触发完整流水线。若正式升级检查失败，保留失败产物并报告，不能直接发布候选或 Stable。

现有主分支流程只允许预发布候选；本任务不合并、不发布 Stable，也不访问用户手机。

## 合成 fixture 来源

`scripts/fixtures/v210-upgrade.zip` 是此前生产 MainActivity 在 API29 实际导出的原始合成备份，包含附件原始文件，并未由 Python 重建账本或配对结果。
来源：source `9143d5d256e440b27361a45d62dd45115672fc61`，[run39173642117](https://github.com/mlzx-xxj/trade-review-app/actions/runs/39173642117)，artifact `12155420006` 的 `synthetic-backups/regular-light.zip`。
fixture SHA-256：`8494964bb73c6539b0e079ebbf904f313021ff7622f928682adc39a4921a45ac`，20,430字节。它是导入测试数据；升级起点 APK 始终是上述原始 Stable，二者不混为一谈。
