# 只用安卓手机配置原固定签名

只复用原 V0.3 私钥，公开证书 SHA-256：
`30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c`。

本轮不读取、生成或替换长期私钥。Secrets/Variables 管理 API 权限不足时，实际 Actions 分支与校验证据才决定状态，不把 403 当作没有配置。正式版不能从临时 Debug 身份自动升级。

1. 在自己可信手机/设备上确认确实拥有原 keystore、别名和两个密码，并有安全离线副本。不要发送到聊天、Issue、PR、Release、artifact 或未知转换网站。
2. 仅在可信本地工具上把原 keystore 转成单行 Base64；Base64 仍是私钥，不是加密。已有本地工具可参考仓库既有签名指南；没有可信工具或原材料则先停止配置，继续使用隔离 RC。
3. 手机浏览器打开 GitHub 官方仓库 → Settings → Secrets and variables → Actions（手机可开启桌面网站）。在 Repository secrets 添加：
   - `TRADE_REVIEW_KEYSTORE_BASE64`：原 keystore 的 Base64。
   - `TRADE_REVIEW_KEYSTORE_PASSWORD`：原仓库密码。
   - `TRADE_REVIEW_KEY_ALIAS`：原别名。
   - `TRADE_REVIEW_KEY_PASSWORD`：原私钥密码。
4. Repository variables 添加 `TRADE_REVIEW_UPGRADE_CERT_SHA256`，值为上面的公开 64 位小写指纹。不要把密码放 Variables。
5. Actions → Android CI → Run workflow，选择 V1.0 分支。四个 Secrets 必须齐全；部分配置或 pin 不匹配会失败，不能降级冒充固定签名。CI 临时写原 keystore、仅通过环境引用密码，完成后删除；不上传签名材料。
6. 只有真实 `FIXED-IDENTITY.json`、`SIGNING-STATUS.json`、原 pin 与 apksigner 校验一致，才得到 `TradeReview-v1.0-fixed-candidate.apk`：com.tradereview.app / 1.0.0 / code10 / non-debuggable。CI 另行实际安装此 APK。
7. 若保存了真实原固定签名旧 APK 的 GitHub Release，可在手动工作流填写 `old_fixed_release_tag` 和精确 `old_fixed_asset_name`。CI 再次验证原证书、真实 install -r、合成交易/日志数据保留；不填时明确“未执行”，不以 Debug 密钥模拟成功。
8. 由所有者完成手机真机验收后再决定正式发布；本工作流始终创建预发布候选，不自动宣称稳定发布或合并 PR。

若原私钥不可用，证书指纹本身不能恢复私钥。正式签名处于阻塞状态。可继续隔离 RC；或由所有者以后明确批准建立新身份并先备份迁移。后者不能覆盖原固定签名，涉及重新安装及数据迁移，当前未授权也未实施。
