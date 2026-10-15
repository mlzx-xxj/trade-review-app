# 只用安卓手机配置原固定签名

只复用原 V0.3 私钥，公开证书 SHA-256：
`30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c`。

公开仓库以 [Public CI 安全说明](PUBLIC-CI-SECURITY.md) 为当前配置依据，旧 Repository Secrets 上传脚本已停用。本轮不读取、生成或替换长期私钥。Secrets/Variables 管理 API 权限不足时，实际 Actions 分支与校验证据才决定状态，不把 403 当作没有配置。正式版不能从临时 Debug 身份自动升级。

1. 在自己可信手机/设备上确认确实拥有原 keystore、别名和两个密码，并有安全离线副本。不要发送到聊天、Issue、PR、Release、artifact 或未知转换网站。
2. 从 GitHub 查看并下载既有 [离线转换工具](../tools/keystore-base64-offline.html)，审查后用手机文件管理器在可信浏览器本地打开，断网使用。自包含代码、CSP 禁止连接、无外部脚本、无 localStorage。仅把原 keystore 转成单行 Base64；Base64 仍是私钥，不是加密。若浏览器无法本地运行 HTML，只用自己已信任的离线工具，不用在线转换站点。没有原材料则先停止配置，继续隔离 RC。
3. 手机浏览器打开 GitHub 官方仓库 → Settings → Environments（手机可开启桌面网站），创建 `v03-signing-main`。Deployment branches/tags 选择 Selected branches and tags，只允许类型 Branch、名称 main；不添加通配符或 tag main。在此 Environment 的 Secrets 添加：
   - `TRADE_REVIEW_KEYSTORE_BASE64`：原 keystore 的 Base64。
   - `TRADE_REVIEW_KEYSTORE_PASSWORD`：原仓库密码。
   - `TRADE_REVIEW_KEY_ALIAS`：原别名。
   - `TRADE_REVIEW_KEY_PASSWORD`：原私钥密码。
4. 移除同名 Repository Secrets 及其他可供 PR 读取的签名副本；不得向不可信贡献者授予同仓写权限。建议开启 main 分支保护和 CODEOWNERS 审核。Environment variables（或仅存公开 pin 的 Repository variables）添加 `TRADE_REVIEW_UPGRADE_CERT_SHA256`，值为上面的公开 64 位小写指纹。不要把密码放 Variables。
5. 清除转换页、关闭并清除剪贴板；保留原材料安全副本。本轮 CI 加固 PR 合并后，Actions → Android CI → Run workflow，只选择 main；PR 或非 main 手动仅构建隔离 RC，不签名、不发布。四个 Secrets 必须齐全；部分配置或 pin 不匹配会失败，不能降级冒充固定签名。CI 临时写原 keystore、仅通过环境引用密码，完成后删除；不上传签名材料。
6. 默认只读构建及 API29/35 完整测试成功、Environment 保护检查成功后才进入独立签名 Job；随后无 Secrets 的模拟器任务实际验证固定包安装。只有真实 `FIXED-IDENTITY.json`、`SIGNING-STATUS.json`、原 pin 与 apksigner 校验一致，才得到 `TradeReview-v2.0-fixed-candidate.apk`：com.tradereview.app / 2.0.0 / code20 / non-debuggable。CI 另行实际安装此 APK。
7. 已找到[原固定证书 V0.3 公开包](https://github.com/mlzx-xxj/trade-review-app/releases/tag/v03-debug-38133182112-1)：手动工作流填写 `old_fixed_release_tag=v03-debug-38133182112-1`、`old_fixed_asset_name=TradeReview-v0.3-stable.apk`。原包是历史固定签名 Debug 试用版，不是本轮正式 Release；本机保留的公开 APK 已用 apksigner 核实原 pin，code3，SHA-256 `4b7422e1f9c03f6cafb166014dd306265fcf10aee49959ce7284d1f62c6db995`。CI 会对实际下载文件再次验签，执行真实 install -r、合成交易/日志数据保留；原私钥未配置或不提供旧包时明确“未执行”，不以临时 Debug 密钥模拟成功。
8. 由所有者完成手机真机验收后再决定正式发布；本工作流始终创建预发布候选，不自动宣称稳定发布或合并 PR。

若原私钥不可用，证书指纹本身不能恢复私钥。正式签名处于阻塞状态。可继续隔离 RC；或由所有者以后明确批准建立新身份并先备份迁移。后者不能覆盖原固定签名，涉及重新安装及数据迁移，当前未授权也未实施。
