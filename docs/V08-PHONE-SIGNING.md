# 仅用安卓手机配置既有长期签名

固定身份必须仍是 V0.3，公开证书 SHA-256：

```
30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c
```

本轮不读取、生成或替换私钥。管理 API 无 Secrets/Variables 权限，真实是否启用由构建和 SIGNING.txt 验证。以下只能由仓库所有者在可信手机及 GitHub 官方网页操作；不要把任何密钥、Base64 或密码发给 Codex、聊天、Issue、PR 或日志。

## 先确认你拥有原材料

必须有原 V0.3 keystore 文件、keystore 密码、正确别名及该别名私钥密码。证书指纹和已安装 APK 都不能代替私钥。别名或密码不确定时，使用你信任的本机 keystore 工具核实，不能猜测或生成替代密钥。

如果材料遗失或无法取得，应明确记录“固定签名配置阻塞”；当前只用临时测试包，不能承诺覆盖升级。仅重新配置 Secret 也不能使不同证书的已安装临时包变成固定身份。不要为解决安装冲突直接卸载含数据的旧应用；先保留可读的完整 ZIP 备份，升级或迁移操作由你决定。

## 手机本地转换，GitHub 网页保存

1. 在 GitHub 查看并下载仓库 [tools/keystore-base64-offline.html](../tools/keystore-base64-offline.html)，使用手机文件管理器在可信浏览器本地打开。它是独立、自包含的小工具，无外部脚本，CSP 禁止网络连接；不是 App 功能。代码只有本地 FileReader/Base64 和复制，不上传、不使用 localStorage。先审查文件，断网后选原 keystore，点击复制。若浏览器不能本地运行 HTML，可使用你已经信任的离线 Base64/keystore 工具；不能使用在线转换站点。
2. 恢复网络，在浏览器打开 `https://github.com/mlzx-xxj/trade-review-app/settings/secrets/actions`，核对域名与仓库。手机界面看不到 Settings 时可启用浏览器“桌面版网站”。必须登录具备仓库管理权限的所有者账号；Codex 的集成 403 不表示你的账号也无权限。
3. 点击 New repository secret，分别建立下列四项。Base64 是完整私钥文件的编码，按密钥保密处理；保存后 GitHub 不显示原值，需要更正时由你重新填写。

| Secret 名称 | 由所有者填入的值 |
| --- | --- |
| TRADE_REVIEW_KEYSTORE_BASE64 | 本机离线转换的原 V0.3 keystore Base64 |
| TRADE_REVIEW_KEYSTORE_PASSWORD | 原 keystore 密码 |
| TRADE_REVIEW_KEY_ALIAS | 原私钥别名 |
| TRADE_REVIEW_KEY_PASSWORD | 原别名私钥密码 |

4. 打开同一页面的 Variables → New repository variable，设置 `TRADE_REVIEW_UPGRADE_CERT_SHA256` 为上面的公开 64 位指纹。变量只存公开 pin，不能存密码或 keystore。仓库中 docs/V03-CERTIFICATE-SHA256.txt 必须保持原值。
5. 清除转换页内容、关闭它并清除剪贴板；保存原文件和密码的安全备份。不要把 keystore 上传为仓库文件、Actions artifact 或 Release 资产。
6. 在 Actions → Android CI → Run workflow 中选择 `codex/v08-stability`（合并后可选择 main）。保持可选 signed_apk_blob_sha 为空。重跑构建，不能仅重跑发布任务复用旧临时 APK。

## 如何确认真正成功

所有检查成功后，下载页应明确显示“固定签名”及 `TradeReview-v0.8-stable.apk`。实际 SIGNING.txt 必须是 0.8.0 / code 8 / com.tradereview.app、`GitHubActionsSecretsConfigured=true`、`upgradeVerified=true`，且 `certificateSha256` 与原 pin 完全一致。发布任务会再次使用官方 apksigner 对 APK 本身验签，并校验摘要、版本、来源及发布分类；仅改文件名或变量不能通过。

四项不齐、别名/密码无效、原证书与 pin 不匹配，应停止固定包发布并保留失败日志，不更换 pin 来“修好”。若仍是临时包，签名尚未配置成功；不能视为长期升级已解决。

以后保持原私钥、applicationId 与递增 versionCode。同签名是覆盖升级的必要条件；仍需在测试手机验证数据保留。仅在具备真正原签名的旧 APK 与新 APK 后才做 V0.3 身份的升级验收，不用临时测试密钥冒充。没有原材料时此项明确“未执行”。

本指南和转换工具尚需你在实际手机验证浏览器支持；Base/模拟器测试不代表手机已完成 Secrets 配置。
