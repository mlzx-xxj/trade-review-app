# V0.3 实际验证记录

从最新 main `9dfd40fee7fe1eb5bcb8322bf01a62727bb4c488` 创建 `codex/v03-fast-entry`，PR #1/#2 已合并。沿用原工程、Wrapper 和稳定技术版本；没有删除原文件。应用 0.3.0 / versionCode 3 / com.tradereview.app。

## 已完成完整 Actions

[运行 38130145142](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38130145142)，源码 `e5eadedd21e7c9a3cc30ea180dee990b773c0537`，build / device-tests / publish 全部 success。

- Debug APK 与 Android 测试 APK 实际编译成功。
- 26 项 JVM 测试通过，0 失败/错误/跳过：统计 6、JSON 9、CSV 3、快速录入 5、V0.3 备份兼容/往返 3。
- 4 项签名发布门禁测试通过；Lint 0 错误、13 警告（OldTargetApi、AppBundleLocaleChanges、11 条依赖更新提示）。
- API 29 x86_64 模拟器 28 项通过，0 失败/错误/跳过，XML 时间 `2026-10-11T10:04:39`：备份恢复 8、正式迁移 3、原持久化 1、V0.3 数据 7、SAF/设置 3、快速录入/管理 UI 4、四页导航/交易增改删 2。
- 覆盖空/零金额拒绝与明确持平、复制清空状态、连续新增、草稿重开与防止延迟写复活、自定义标签稳定 ID/改名隐藏历史关联、品种收藏最近排序、V0.2 fixture 摘要验证、全部六表恢复和真实 SQLite trigger 晚阶段失败回滚。
- Room 编译器产生的 v3 schema 与提交文件逐字节相同；v1/v2 schema 保留。正式 v1→v3、v2→v3 迁移测试保留历史字段与日记。

报告产物已实际下载并检查 XML：JVM/Lint/Schema `11698823558`、Android `11698963943`、APK `11698174360`。本地未安装 Android SDK 或执行 Android 编译；独立 apksigner 不等于完整 SDK。未执行真实手机测试。

## 修复与签名

实际出现并已修复：中文 Context 包装导致 SAF ActivityResult owner 丢失；owner 可空类型导致 Kotlin 编译失败；旧 fixture 应从测试 APK assets 读取；设置懒列表需滚动才能访问下方内容；UI 写入期间按钮禁用需等待；反馈 Snackbar 可能遮挡管理列表底部操作。中间失败与被后续提交取消的运行均不计为通过。

所有者明确授权后，在仓库之外生成 PKCS12 / RSA 3072 长期私钥，证书 SHA-256 `30d5e88ac7f0ade3d8c5f10d7d894681e7953002377c85b5fa094ee7b71e071c`。独立官方 apksigner 对本轮 Debug APK 完成 v2/v3 签名且验证通过；与 Actions 编译产物的应用内容逐项一致。初次签名因同一个密码文件被连续读取到 EOF 失败，改用两个独立密码文件后通过；没有重新生成或更换私钥。

固定包 SHA-256 `4b7422e1f9c03f6cafb166014dd306265fcf10aee49959ce7284d1f62c6db995`，17,727,111 字节。普通 CI 临时包下载页：[38130145142](https://github.com/mlzx-xxj/trade-review-app/releases/tag/v03-debug-38130145142-1)，不作为未来升级身份。

当前集成 Secrets API 403；所有者配置脚本已实际运行并在权限检查处退出，没有写任何 Secrets。Base 直接上传固定包的 uploads.github.com 接口返回 401；普通 API 与 git push 可用，未修改代理或绕过策略。因此补充官方仓库 blob→Actions 交付通路，仅传公开 APK、不提交 APK 文件、不传私钥。新增 4 项 payload 比较测试在 Base 和下述最终 Actions 均实际通过。

## 最终固定签名交付

[最终完整 Actions 38131169246](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38131169246)，源码及工作流 **`fa38c1aef797e6705bd10a354a0c77792fef124f`**，build / device-tests / publish 全部 success。该提交用 `[skip ci]` 避免同时触发普通临时包 PR 构建，随后显式 workflow_dispatch 对此完整提交执行全部编译与测试，并非跳过验证。

| 实际执行项 | 最终结果 |
| --- | --- |
| Debug APK / Android 测试 APK 编译 | 成功 |
| JVM 测试 | 26 通过，0 失败/错误/跳过 |
| 签名门禁 / 外部 APK 内容校验测试 | 4 + 4 通过 |
| Android Lint | 0 错误，13 警告 |
| Room / 数据 / SAF / Compose API 29 模拟器测试 | 28 通过，0 失败/错误/跳过 |
| 正式固定 APK 校验 | Git blob 摘要、apksigner 证书 pin、全部应用 payload 与本次编译一致 |
| APK 自动发布 | 成功，固定包及 SHA256SUMS.txt / SIGNING.txt |

已下载最终 JVM/Lint/schema 报告（`11697994555`）、模拟器报告（`11698164436`）及固定 APK（`11698913439`），检查实际 XML 和文件摘要。v3 schema 与提交文件相同。最终模拟器仍是恢复 8、迁移 3、持久化 1、V0.3 数据 7、SAF/设置 3、快捷/管理 UI 4、导航/增改删 2。没有将编译、静态检查或模拟器结果写成真实手机测试。

[手机浏览器下载页](https://github.com/mlzx-xxj/trade-review-app/releases/tag/v03-debug-38131169246-1)；选择 **TradeReview-v0.3-stable.apk**。仓库为私有，需登录有访问权限的 GitHub 账号。发布端资产 digest 为 `sha256:4b7422e1f9c03f6cafb166014dd306265fcf10aee49959ce7284d1f62c6db995`，与 Base 签名文件和下载的 Actions 产物完全相同。证书与上文长期私钥一致，签名来源/Secrets=false 写入 SIGNING.txt；未谎称 Secrets 已配置。

最后仅补充本验证记录、计划的签名实际状态及私密文件忽略规则；Android 功能代码、测试、签名脚本和工作流没有再改动，以上完整 Actions 为最终代码验证依据。

## 剩余限制

所有者须下载私密签名备份、离线保存并配置 Secrets/证书 pin，才能持续自动签署后续包。旧不同签名测试版需手动卸载再首次安装。没有实际手机安装、未来版本覆盖升级或所有手机厂商 SAF 提供方测试。JSON 是明文、完整性摘要不是加密或身份认证。模拟器通过不能代替真机结果。
