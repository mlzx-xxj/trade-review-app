# V1.0 实际验证记录

开发基线：已合并 V0.8 PR #8，main `7f8c60ea9394f179ed8da48241ab46581d2b6238`。main [Actions 38303977043](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38303977043) 已实际完成 success。独立 [PR #9](https://github.com/mlzx-xxj/trade-review-app/pull/9)，不自动合并。

## 已执行

- Base 36 项 Python 发布/交付/故障决策测试通过，其中新增 V1.0 门禁 7 项。Python/shell 语法、YAML 解析、git diff --check 已执行；这些不是 Android 编译。
- 首轮 [Actions 38308733961](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38308733961)，源提交 `0510db73fab6d2c155f5a55a08751c957afc9fff`：Debug、Android测试、RC 和 unsigned Release 编译成功；151 JVM 测试，0 失败/错误/跳过；Debug/RC/Release 三种 Lint 均 0 错误、20 警告。
- 警告包含依赖升级建议、target35未采用最新SDK、平台Exif/存储建议、动态语言AppBundle及图标资源变体/保留旧资源等。本轮没有为了消除警告批量升级依赖、提升 target 或关闭检查。
- CI 生成的 Room7 schema 与仓库原文件字节一致。保留完整迁移链，未改变数据库结构。
- Base 下载真实 RC APK，用官方 apksigner 与独立二进制 Manifest 读取器核对：com.tradereview.app.rc / 1.0.0-rc.1 / code10 / non-debuggable；无 INTERNET、网络状态或广泛存储权限。实际摘要及证书随每次候选构建变化，以对应下载页 SIGNING/RC-IDENTITY/SHA256SUMS 为准。
- 原 V0.3 固定签名公开 APK 再次实际验签：证书 SHA-256 与原 pin 一致；包 com.tradereview.app / 0.3.0 / code3。旧 APK 不能替代原私钥。

## 首次真实失败与修复

首轮 API35 在 103 项开始后只完成54项，其中2项失败、测试进程崩溃；不是通过。新增 AndroidTest 备份 Provider 在独立测试 APK 进程加载时缺少 Kotlin Intrinsics（目标 APK 运行时不会自动进入该独立进程）。将该合成 Provider 改为仅 Android/Java 类，保留同样的损坏/写入失败/只写条件及全部原断言。修复后重新执行完整 CI，不删除失败测试。

第二轮 [Actions 38311222575](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38311222575)，源提交 `e20b76516366619def966747f831d2c779002eae`：三种编译/JVM/Lint 再次通过。API35 实际完成103项、0跳过，100通过/3失败；新增5项备份数据测试均通过，Provider崩溃消除。3项 UI 失败是对尚未组成的 LazyColumn 子节点直接 performScrollTo：home-plan、home-post-review、show-help。加入列表定位并从 LazyColumn performScrollToNode 搜索滚动；保留原保存/恢复/历史数据断言，月报切换先滚回控制项再点击。

## 正在执行

API29/35 全回归、新手引导/备份故障/完整操作链、实际交付 RC 安装及重启、截图和发布验证正在执行；结果完成后补充。最终提交必须另有完整绿色 CI，见 PR Checks，不使用 skip ci。

## 正式发布阻塞

首轮实际 SIGNING-STATUS.json：configured=false、originalPinVerified=false。管理 Secrets/Variables API 权限不足，不能读取其值；实际工作流尚无法使用原私钥。没有读取、生成或替换长期私钥。

真正固定签名 non-debuggable Release 签署/安装、真实原证书旧版→V1.0 install-r，以及用户手机验收尚未执行。RC 独立包名仅提供测试，不保证长期 RC 覆盖升级；正式状态仍为候选。所有者按[手机签名指南](V100-PHONE-SIGNING.md)配置原材料，再执行[真机清单](V100-PHONE-ACCEPTANCE.md)。无法取得原私钥时必须等待明确迁移选择，不能自行创建替代身份。
