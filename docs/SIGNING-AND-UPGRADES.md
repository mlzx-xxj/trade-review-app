# 稳定签名与持续升级

> Public 仓库安全更新：本文的旧 Repository Secrets/旧分支签名步骤仅作历史记录，请勿执行。当前必须使用仅允许 main 的受保护 Environment，并移除 Repository 签名副本；见 [当前 CI 安全及手机操作指南](PUBLIC-CI-SECURITY.md)。旧 configure-v03-signing.sh 已停用，不读取私有材料。

## 已验证的现状和阻碍

V0.1 是 Debug APK。2026-10-11 实际下载并解析 APK Signing Block 得到：

| V0.1 Release | 签名证书 SHA-256 |
| --- | --- |
| debug-38114644226-1 | 5ce5c0b1fb3b7e0c17963a041a94faf0e805888bbfff44f0fe75f503ba42c86d |
| debug-38115853995-1 | 8272cb328cfdbc2e585ce88b71c1425f18abc5a57d6082bc69b8c305a154eb82 |

两次证书不同。旧 Actions 日志显示签名缓存 `trade-review-debug-keystore-v1` 未命中，任务末尾出现 `Path Validation Error: Path(s) ... do(es) not exist`，所以原缓存路径没有保存密钥。查询全部现有缓存也没有该条目。用户已确认安装 V0.1，且没有保存旧密钥。APK 中只有公钥证书，无法从 APK 恢复私钥；既有远端运行机器结束后也不能取回未保存文件。

当前 GitHub 身份读取 Actions Secrets 清单返回 403，因此无法确认或设置仓库密钥。没有生成正式发布密钥，没有更换已有应用的签名，没有把密钥提交、放入产物或日志。

**无法保证 V0.1 原地更新。** Android 要求相同应用 ID、同一签名私钥（或有旧私钥授权的合法轮换）和更高 versionCode。Room Migration 只能保留允许安装后的数据，不能解决签名不匹配。卸载 V0.1 会删除本地记录；此处不提供卸载操作。

## 已实现的保护

Gradle 仅在构建环境提供 `TRADE_REVIEW_KEYSTORE_PATH` 时使用指定的已有密钥；不生成正式发布密钥、不配置未经授权的 release 签名。

Actions 可从 Secrets 临时解码旧 keystore，文件权限 0600，仅放 runner 临时目录，构建后 always 删除。对 APK 执行 `apksigner verify --print-certs`，与所选旧证书比较：

- 已配置旧密钥且证书匹配：产物 `TradeReview-v0.2-upgrade.apk`；所有验证任务成功后才发布。
- 已配置但不匹配：签名检查失败，禁止上传下载 APK 或发布升级包。
- 未配置：允许常规 Debug 编译与设备测试。产物 `TradeReview-v0.2-test.apk`、SIGNING.txt 和 Release 均明确标记「仅供新设备测试，不能升级 V0.1」。这是测试临时签名，不是正式发布方案。不会发布伪称可覆盖升级的 APK。

证书摘要是公开信息；密码与 keystore 从不进入代码。构建签名详情仅含证书摘要。APK 和 SHA-256 一同上传。编译、单元测试、Lint、模拟器测试全部通过才创建预发布下载页。

## 找到旧私钥后的操作（由仓库所有者在 GitHub 配置）

如果其他机器、受信备份或已有 Secret 中找到与已安装 APK 匹配的旧 keystore，在仓库 Settings → Secrets and variables → Actions 配置：

| 类型/名称 | 内容 |
| --- | --- |
| Secret `TRADE_REVIEW_KEYSTORE_BASE64` | 已有 keystore 的 base64；不是新生成的 key |
| Secret `TRADE_REVIEW_KEYSTORE_PASSWORD` | store password，普通旧 Debug 默认为 android |
| Secret `TRADE_REVIEW_KEY_ALIAS` | alias，普通旧 Debug 默认为 androiddebugkey |
| Secret `TRADE_REVIEW_KEY_PASSWORD` | key password，普通旧 Debug 默认为 android |
| Variable `TRADE_REVIEW_UPGRADE_CERT_SHA256` | 已安装 V0.1 的证书摘要；默认选上表最新 Release 的证书 |

请在可信本机准备和配置，**不要在聊天、Issue、PR、日志或产物中提供私钥/密码**。先用已下载的原始 APK 执行 `apksigner verify --print-certs` 确定自己安装的是哪个签名；不要只根据相同的「0.1.0」版本号判断。重新运行新 PR 的 Actions，验证签名一致后在测试设备进行保留数据升级演练，再升级日常手机。

同一包名发布后的 key 应离线加密保存至少两份，限制 Secrets 和发布工作流修改权限。不可依赖有范围和过期限制的 Actions cache 保存正式签名材料。后续发布始终沿用同一 key 并严格递增 versionCode；每次 DB 版本变更必须增加正式 Migration 并保留旧 schema。

## 旧私钥确实丢失时

本轮不能提供覆盖升级包，这是签名私钥缺失导致的实际阻碍。已安装应用可继续离线使用。不要卸载、清除数据或贸然换包名。正式新签名或其他数据转移方式需要另行授权和验证；本轮不实施这些操作，也不承诺 Android 会接受未经旧密钥授权的更新。

备份协议同样需要稳定升级：预设标签 ID 保持永久稳定，中文显示名称可以独立调整；JSON 结构变更时递增 schemaVersion，并为旧格式提供显式转换和测试。不能复用旧 ID 表示不同含义，或删除旧选项后使既有备份无法导入。
