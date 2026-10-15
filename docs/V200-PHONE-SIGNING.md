# 安卓手机：V2 永久签名配置与旧版迁移

当前政策是 [Issue #12](https://github.com/mlzx-xxj/trade-review-app/issues/12) 的所有者授权。旧 V0.3 私钥已丢失；所有者已独立生成并保管新的永久 PKCS#12，本项目不生成、索取、代传或替换私钥。

| 身份 | 公开信息 | 含义 |
| --- | --- | --- |
| 新 V2 永久证书 | `29b2f82887a0591d5b1d1f9ab148b90c0b658088530e3bcc1bd7e5fee64f2af9` | 正式包 `com.tradereview.app`，alias `trade-review-release-2026`，PKCS#12、RSA 3072 |
| 历史 V0.3 证书 | `30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c` | 仅保留历史证据；不能作为新正式版门禁，不能覆盖升级到新证书 |
| 隔离 RC | `com.tradereview.app.rc.v200` | 临时签名，数据独立，不承诺 RC 间持续升级 |

## 在可信设备上配置（不要在聊天输入）

1. 确保已有新 keystore、正确 alias、仓库密码和私钥密码，安全离线备份至少两份。证书指纹不能恢复私钥。不要使用在线转换网站，不把 Base64、密码发给 Codex、Issue、PR、源码、日志、Release 或 Artifact。Base64 是私钥的编码，不是加密。
2. 如需转换，可审查并下载仓库的 [离线 Base64 工具](../tools/keystore-base64-offline.html)，使用可信浏览器从手机本地文件打开、断网运行。工具无外部脚本或存储；浏览器不支持本地运行时，只用自己已经信任的离线工具。不要再生成另一把密钥。
3. 手机浏览器打开 GitHub 官方仓库 → Settings → Environments → **已有 `v03-signing-main`**，必要时开启“桌面网站”。保留这个历史环境名。Deployment branches/tags 应只有类型 Branch、名称 `main`，不允许通配符、tag main 或开发分支。main 保护和审核应继续启用；本 PR 不修改这些设置。
4. 在此 Environment 的 **Environment secrets** 中自行添加四项：
   - `TRADE_REVIEW_KEYSTORE_BASE64`：新 PKCS#12 的完整单行 Base64。
   - `TRADE_REVIEW_KEYSTORE_PASSWORD`：该 keystore 的密码。
   - `TRADE_REVIEW_KEY_ALIAS`：`trade-review-release-2026`。
   - `TRADE_REVIEW_KEY_PASSWORD`：该私钥的实际密码；PKCS#12 常与库密码相同，但以自己保存的材料为准。
5. 在同一 Environment 的 **Environment variables** 保留 `TRADE_REVIEW_SIGNING_CERT_SHA256`，值为上面新 V2 的 64 位小写指纹。不要使用旧 `TRADE_REVIEW_UPGRADE_CERT_SHA256`；它不会被当前 V2 签名流程读取。不要把密码放 Variables。清理同名 Repository/Organization Secrets 或其他可供 PR 读取的副本，所有私钥仅限受保护 Environment。
6. 关闭离线转换页并清除剪贴板，继续安全保管原文件和密码。不要把密钥作为“备份附件”上传本项目。GitHub 管理 API 返回 403 只能说明不可读，不证明配置齐全。
7. 审核并自行决定合并本 PR 后，Actions → Android CI → Run workflow，**只选择 main**。PR、Fork PR、非 main 手动运行只验证和生成 RC，不能进入密钥或发布 Job。默认不填写旧包输入；需要下述旧证书拒绝测试时再填写。
8. 必须看到 build、209 项 JVM、API29/35 各 124 项全部成功。main Environment 保护通过后，签名 Job 再核对公开变量与提交 pin、alias、输入 Artifact/source SHA，使用已有密钥签名，apksigner 验实际证书，比较签名前后应用 payload。临时私钥 0600、finally 删除，密码只经环境引用，错误不回显私有诊断。签名不运行 Gradle，也不执行 Artifact 内脚本。
9. 全无 Secrets：只允许明确标注的隔离 RC。部分 Secrets、错误 pin/alias/证书、失败测试或错误 Artifact：失败，不降级冒充正式包。新固定包还需 API29/35 真实安装才可发布候选。检查 Release 的 `FIXED-IDENTITY.json`、`SIGNING-STATUS.json`、`SIGNING.txt`、`SHA256SUMS.txt` 和 CI 来源，确认正式包名、2.0.0/code20、non-debuggable、新证书和 APK SHA 一致。所有 Release 仍是预发布候选，不自动 Stable。

## 旧签名应用：先验证备份，再由所有者决定重装

同包名、不同证书，Android 应拒绝覆盖安装；不存在无需旧私钥的“无损覆盖”。脚本不自动操作用户手机、不卸载、不清除数据。

1. 保留旧 App，先导出完整 ZIP 到用户可访问的位置并保留第二份。确认文件可读、备份日期、记录和截图数量、校验均有效。
2. 在隔离 RC 或其他安全测试环境恢复副本，核对旧交易原始盈亏/ID、图片和计划。不能只看“文件存在”就卸载。若旧 App 不支持完整 ZIP，先停止卸载，保留其可导出的 JSON 与所有图片，确认一个可验证的迁移方案；不能假装 JSON 含图片。
3. 只有所有者确认备份与恢复均可靠后，才手动卸载旧同包名应用、安装新证书正式候选并恢复。卸载会删除旧本机数据；失败或有疑问时先停下，不自动清除。
4. 核对数据和截图，关闭重开后再确认。RC 与正式包的数据空间独立，需要人工导出/恢复；不能宣称 RC 自动迁移数据。

可选历史 APK 兼容性测试：main 手动输入 `old_fixed_release_tag=v03-debug-38133182112-1`、`old_fixed_asset_name=TradeReview-v0.3-stable.apk`。只接收本仓库精确公开文件名。该旧包 code3、原证书、Debug，历史 SHA `4b7422e1f9c03f6cafb166014dd306265fcf10aee49959ce7284d1f62c6db995`。CI 对实际文件再验签；仅在 disposable qemu 安装旧包、写合成私有文件标记，真实 `install -r` 必须返回 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 且保留旧安装及标记。之后清理该合成模拟器 fixture，另做新包**首次安装**。这不是真机迁移，也不是成功覆盖升级，不需要旧私钥或伪造旧签名。

## 以后持续升级

新证书启用后，每次使用同一份 keystore/alias、同一证书和 `com.tradereview.app`，versionCode 必须递增。V2.1 等版本不能重新生成签名或使用 RC/Debug 替代。证书 pin 变化应拒绝发布。当前没有较早的新永久证书 APK，真正的新永久身份旧版→新版保留数据测试尚未执行；同版本重装或静态策略测试不算覆盖升级验收。

本轮未取得/使用真实私钥，未验证新正式签名或用户手机。所有者配置后须重新跑 main 的真实签名、安装和兼容性测试，并逐项完成 [手机验收](V200-PHONE-ACCEPTANCE.md)，再决定正式稳定发布。
