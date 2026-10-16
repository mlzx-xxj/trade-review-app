# V2.1 UI/UX 真实模拟器体验报告

本轮将成交录入和持仓摘要重组为统一蓝灰视觉，保留全部财务计算与数据格式。实际对照结果：**点击、必填字段和必要滚动没有增加，也没有减少**；主要收益是选择、数量、均价和金额的视觉层次。部分卡片因补充已实现净盈亏与费用而更高，不能宣称所有卡片都变得更矮。

本文固定数据和原图来自完整成功的 [Actions 38909249672](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38909249672)，源码 `8341c8406ddbd247bacb2ab82b189301281e4e7d`。最终文档提交及两处语义文案校正另接受完整 CI，结果和最新原始截图见 [PR #16 最新检查](https://github.com/mlzx-xxj/trade-review-app/pull/16/checks)。不把此处固定图源冒称其他提交。

## 实际验证

| 检查 | 实际结果 |
|---|---|
| Python 安全／报告测试 | 116 项通过 |
| JVM 全量 | 241 项，0 失败／错误／跳过 |
| Debug、RC、Release 编译 | 成功；Release 仅编译，不在 PR 签永久证书 |
| Lint Debug／RC／Release | 0 Error/Fatal；原有 Warning 20／21／21 |
| API 29 全量 instrumentation | 136 项，0 失败／错误／跳过 |
| API 35 全量 instrumentation | 136 项，0 失败／错误／跳过 |
| 附加实际操作／API | 原实录及进程重开 2 项，四种视觉配置各 1 项，共 6 项 |
| 金额与数据独立核对 | Decimal 费用／部分平仓、未知净额、ZIP、重启记录通过 |
| 身份与安装 | 同一非 debuggable RC 在两个 API 安装、启动、重开通过；无 INTERNET |
| 永久签名与发布 | PR 安全隔离，相关作业跳过；不代表 Secrets 未配置 |
| 用户手机 | 未执行，不能以模拟器替代 |

已独立下载校验归档 SHA-256、来源 SHA 与逐文件清单，再解析原始 JUnit、Lint、JSON 和实际截图。未用静态检查、Compose Preview 或直接写入成交替代录入测试。[API29 原始证据](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38909249672/artifacts/12049904411) · [API35 原始证据](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38909249672/artifacts/12050293110)（Actions 临时产物需登录且保留 7 天；文中选图随代码保留）。

## 基线与配置

- 最新 main 与不可变 `v2.0.0`：`dc4e20259eeb4248b66cedbedc644e9e559c98a9`。
- 先只增加测试，基线测试提交 `9b6e7e256e591f54e761cc1ea4cce71bacd3006b` 的生产树 `349e16cb8f1f313fdf3123542fe3ff160303e306` 与标签完全相同。取得原图后才开始生产修改。
- [基线 Actions 38897172571](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38897172571)：110 Python、241 JVM、各 API 135 项完整测试及附加实录通过。[来源与计数](v210/baseline-provenance.json)。
- 两个 API 均为 Nexus 4 配置的 x86_64 真实 AVD；360dp = 720×1280px、320dpi、字体 1.0；320dp = 640×1136px、320dpi、字体 1.3；各自浅色和深色，共 8 组。
- 同一生产 MainActivity、同一录入步骤和合成价格／数量。视觉样本仅注入空账本及 `2026-10-14T10:00:00+08:00` 时钟；成交全由界面填写保存。状态栏时间随真实运行不同，不做像素完全相同的承诺。
- 每版每组 11 张指定场景图，共 88 张／版，另有全量回归和压力图。原 PNG 未编辑；没有生成式重绘或静态假 App。[并排总览 HTML](v210/gallery/index.html) · [40 条逐项比较与图片摘要](v210/gallery/comparison.json) · [源提交、产物与原图 SHA](v210/gallery/provenance.json)。GitHub 手机直接查看以下 Markdown 对照，HTML 可下载随原图离线打开。

## 设计与改动范围

[完整设计规范](V210-DESIGN.md)：浅色背景 `#F3F5F9`、白卡、深蓝灰主色 `#304C75`；深色背景 `#0F1622`、卡片 `#182230`、主色 `#B4CBF7`。红盈绿亏，同时显示正负含义与文字；零值及未知中性显示。主要文字对表面的对比度浅色 15.56:1、深色 13.38:1，辅助文字均超过 6:1。

页面标题／关键数值 24sp、模块 18sp、输入 16sp、次级数字 14sp、辅助文字 12sp；金额启用 `tnum`，必要时缩放但不低于 14sp，不省略关键数字。人民币仍按原 HALF_UP 显示两位，内部 BigDecimal 与数据库值保持原精度。间距 4/8/12/16/24dp，圆角 12/16dp，主要控件至少 48dp，保存至少 52dp。

仅五个生产文件变化：Theme、V200Components、V200Trade、V200Home、V200App。市场／方向分段选择、常用入口进入代码框、价格数量并排；费用／保证金的小标签与金额分开，保存留在安全区和 IME 上方。可选信息默认收起，未加入日期、秒、序号、乘数或费率输入，也没有新增普通保存确认。常用列表区分名称、代码和最近使用。

持仓按身份／方向、数量与均价、已实现净盈亏与参考保证金或可卖数量排列；费用和股票持仓成本为次级行，首次日期在详情查看，已设置止盈止损弱化显示。库存成本不是市值，已实现净额不是浮动盈亏。危险删除用警告色，保存成功沿用现有反馈。

复用检查另外校正已平仓股票均价标签和平仓保证金的“预计”措辞，均不改变值或公式；这两个条件不涉及本文开仓录入与未平仓卡片主图。最终原图仍可在 PR 最新 CI 获取。

## 操作效率

计数从**交易页内**开始，包含“记一笔”和保存，不包含底部切页、键盘字符按键及保存后的验证导航；从首页开始两版均另加一次底部“交易”。滚动只计输入控件不可见时实际执行的命令，不等同滑动距离。新增的有界 IME／视口稳定等待仅用于测试同步，不增加用户操作，不用于宣称真人录入速度。

以下两个 API 实测相同；没有额外确认。

| 交易页内路径 | 控件点击 V2.0 → V2.1 | 输入字段 | 必要滚动 V2.0 → V2.1 | 新增确认 |
|---|---|---|---|---|
| 股票买入 | 5 → 5 | 3 | 1 → 1 | 无 |
| 股票隔日卖出 | 5 → 5 | 3 | 1 → 1 | 无 |
| 期货首次买开 | 6 → 6 | 3 | 1 → 1 | 无 |
| 复用最近期货 | 6 → 6 | 2 | 0 → 0 | 无 |
| 期货卖平 | 6 → 6 | 3 | 2 → 2 | 无 |
| 期货卖开 | 6 → 6 | 3 | 2 → 2 | 无 |
| 期货买平 | 6 → 6 | 3 | 2 → 2 | 无 |
| 搜索沪铝买开 | 7 → 7 | 3 | 0 → 0 | 无 |

四组视觉配置每个 API 各五笔、共 40 条前后比较，财务事实一致，点击／字段／滚动均未增加。视觉探针的首笔期货额外聚焦一次以拍真实输入法，因此是 7 次；两版相同，不能与上表日常 6 次混算。分批第二笔和卖平在 IME 仍显示时直接点击保存并成功落库（`keyboardVisibleAtSave=true`），同屏金额与保存按钮可见。

保存后自动切到历史成交并找到新记录；持仓、统计与复盘使用原有同一数据源。界面不能证明真人单手速度或其他输入法的字符切换次数。

## 实际显示、保存与计算

下表来自额外完整实录，两个 API 的金额一致。费用列依次为独立预期／界面读数／落库值；净额列保留原内部精度，界面按两位显示。

| 品种／操作 | 价格 × 数量 | 费用：预期／显示／保存 | 显示保证金 | 同方向剩余 | 本次已实现净额（原值） | 结果 |
|---|---|---|---|---|---|---|
| 600000 buy | 10 × 1000 | 1.00 / ¥1.00 / 1.00 | 不适用 | 1000 | 0 | PASS |
| 600000 sell | 10 × 1000 | 6.00 / ¥6.00 / 6.00 | 不适用 | 0 | -7 | PASS |
| rb2701 buy-open | 3500 × 2 | 7.01 / ¥7.01 / 7.01 | ¥4,900.00 | 2 | 0 | PASS |
| rb2701 buy-open | 3510 × 3 | 10.54 / ¥10.54 / 10.54 | ¥7,371.00 | 5 | 0 | PASS |
| rb2701 sell-close | 3530 × 4 | 14.13 / ¥14.13 / 14.13 | ¥2,457.00 | 1 | 971.833333333333 | PASS |
| rb2701 sell-open | 4000 × 1 | 4.01 / ¥4.01 / 4.01 | ¥2,800.00 | 1 | 0 | PASS |
| rb2701 buy-close | 3990 × 1 | 4.00 / ¥4.00 / 4.00 | ¥2,457.00 | 0 | 91.99 | PASS |
| al2701 buy-open | 20000 × 2 | 6.01 / ¥6.01 / 6.01 | ¥22,000.00 | 2 | 0 | PASS |

保证金为离线参考估算，不是期货公司实时结算；买平后 2,457.00 是仍在持有的另一多头周期占用，不是新增占用。样本使用原 V2.0 已确认离线参数：rb 乘数 10、保证金率 7%、成交额费率 0.0001，al 每手基础费 3 元；期货完整成交另加 0.01 元一次。合成价格不代表行情，不重新声称费率实时有效。

- FIFO：2 手 3500、3 手 3510，3530 平 4，第一批归零、第二批余 1；已实现原值 `971.833333333333`，界面 `￥971.83`。周期费用包含尚未归属平仓的部分，不能再从净额重复扣除。
- 修改为 3540 平 3：费用 10.63、余 2、原净额 `1078.846666666667`，显示 `￥1078.85`，原顺序不变；删除平仓后余 5，删除第二笔开仓后余 2，保留 3 条审计。
- i2701：800 开 2、810 平 1；已核实乘数 100，费用与参考保证金为空，毛额 1000、净额仍为空，界面待估算／待计算；不当作零费用或零净额。最终统计已知净额 84.99，另 1 笔待计算。
- 股票同日卖出在真实表单被 T+1 拒绝，再用测试专用时钟跨日保存成功，未要求用户修改手机日期。
- 大金额纯合成压力：`123456.78 × 123456 × 0.0001 = 1524148.023168`，费用 HALF_UP 为 `1,524,148.02`。实际布局 8 次检查／API，数字字号 14–24sp，宽高与逐行字符边界均无溢出；长名称最多两行视觉省略，完整无障碍文本与数据保留。

## 退出、备份与兼容

实际 SAF ZIP 导出、回读摘要验证、删除一笔、预览及二次确认覆盖恢复后，8 条成交与全部账本事实一致。强制停止后从不同 PID 重开，8 条成交、3 条审计及持仓／统计再次核对：

- API 29：PID 12104 → 12240，`allFactsIdentical=true`。
- API 35：PID 6200 → 6401，`allFactsIdentical=true`。

另有三次进程探针，28 张表和截图文件摘要一致。原 Room 完整迁移、非法备份、事务回滚和旧备份回归保留并执行。本轮 Room 仍 v9、JSON 8、ZIP 1；没有新增数据结构或 Migration，没有修改 recordKey、FIFO、费率、保证金或统计口径。导航保存事件仅是可恢复界面状态，不复制业务数据。

## 同设备实图对照

以下均为 API35、360dp、字体1.0、浅色；点击查看原图。API29同配置完整原始图位于前述产物。

| 场景 | V2.0 原图 | V2.1 原图 |
|---|---|---|
| 股票录入 | [![V2.0](v210/gallery/before/v210-regular-light-stock-filled.png)](v210/gallery/before/v210-regular-light-stock-filled.png) | [![V2.1](v210/gallery/after/v210-regular-light-stock-filled.png)](v210/gallery/after/v210-regular-light-stock-filled.png) |
| 期货录入 | [![V2.0](v210/gallery/before/v210-regular-light-futures-filled.png)](v210/gallery/before/v210-regular-light-futures-filled.png) | [![V2.1](v210/gallery/after/v210-regular-light-futures-filled.png)](v210/gallery/after/v210-regular-light-futures-filled.png) |
| 真实输入法 | [![V2.0](v210/gallery/before/v210-regular-light-keyboard.png)](v210/gallery/before/v210-regular-light-keyboard.png) | [![V2.1](v210/gallery/after/v210-regular-light-keyboard.png)](v210/gallery/after/v210-regular-light-keyboard.png) |
| 股票持仓 | [![V2.0](v210/gallery/before/v210-regular-light-stock-position.png)](v210/gallery/before/v210-regular-light-stock-position.png) | [![V2.1](v210/gallery/after/v210-regular-light-stock-position.png)](v210/gallery/after/v210-regular-light-stock-position.png) |
| 期货持仓 | [![V2.0](v210/gallery/before/v210-regular-light-futures-position.png)](v210/gallery/before/v210-regular-light-futures-position.png) | [![V2.1](v210/gallery/after/v210-regular-light-futures-position.png)](v210/gallery/after/v210-regular-light-futures-position.png) |
| 部分平仓 | [![V2.0](v210/gallery/before/v210-regular-light-partial-position.png)](v210/gallery/before/v210-regular-light-partial-position.png) | [![V2.1](v210/gallery/after/v210-regular-light-partial-position.png)](v210/gallery/after/v210-regular-light-partial-position.png) |
| 待估算录入 | [![V2.0](v210/gallery/before/v210-regular-light-pending-filled.png)](v210/gallery/before/v210-regular-light-pending-filled.png) | [![V2.1](v210/gallery/after/v210-regular-light-pending-filled.png)](v210/gallery/after/v210-regular-light-pending-filled.png) |
| 待估算持仓 | [![V2.0](v210/gallery/before/v210-regular-light-pending-position.png)](v210/gallery/before/v210-regular-light-pending-position.png) | [![V2.1](v210/gallery/after/v210-regular-light-pending-position.png)](v210/gallery/after/v210-regular-light-pending-position.png) |
| 空持仓 | [![V2.0](v210/gallery/before/v210-regular-light-empty.png)](v210/gallery/before/v210-regular-light-empty.png) | [![V2.1](v210/gallery/after/v210-regular-light-empty.png)](v210/gallery/after/v210-regular-light-empty.png) |
| 成交列表 | [![V2.0](v210/gallery/before/v210-regular-light-saved-list.png)](v210/gallery/before/v210-regular-light-saved-list.png) | [![V2.1](v210/gallery/after/v210-regular-light-saved-list.png)](v210/gallery/after/v210-regular-light-saved-list.png) |
| 统计回归 | [![V2.0](v210/gallery/before/v210-regular-light-statistics.png)](v210/gallery/before/v210-regular-light-statistics.png) | [![V2.1](v210/gallery/after/v210-regular-light-statistics.png)](v210/gallery/after/v210-regular-light-statistics.png) |

### 深色、小屏与大字体

以下为真正系统深色及320dp/1.3字体，不是把浅色图调黑。IME 图取自真实系统输入法窗口。

| 场景 | V2.0 原图 | V2.1 原图 |
|---|---|---|
| 深色录入 | [![V2.0](v210/gallery/before/v210-regular-dark-futures-filled.png)](v210/gallery/before/v210-regular-dark-futures-filled.png) | [![V2.1](v210/gallery/after/v210-regular-dark-futures-filled.png)](v210/gallery/after/v210-regular-dark-futures-filled.png) |
| 深色部分平仓 | [![V2.0](v210/gallery/before/v210-regular-dark-partial-position.png)](v210/gallery/before/v210-regular-dark-partial-position.png) | [![V2.1](v210/gallery/after/v210-regular-dark-partial-position.png)](v210/gallery/after/v210-regular-dark-partial-position.png) |
| 320dp／1.3字体／IME | [![V2.0](v210/gallery/before/v210-narrow-dark-keyboard.png)](v210/gallery/before/v210-narrow-dark-keyboard.png) | [![V2.1](v210/gallery/after/v210-narrow-dark-keyboard.png)](v210/gallery/after/v210-narrow-dark-keyboard.png) |
| 320dp／1.3字体／持仓 | [![V2.0](v210/gallery/before/v210-narrow-dark-partial-position.png)](v210/gallery/before/v210-narrow-dark-partial-position.png) | [![V2.1](v210/gallery/after/v210-narrow-dark-partial-position.png)](v210/gallery/after/v210-narrow-dark-partial-position.png) |

[大金额竖屏原图](v210/gallery/after/v210-layout-long-name-large-money-portrait.png) · [大金额横屏原图](v210/gallery/after/v210-layout-long-name-large-money-landscape.png)。横屏和重建后仍选中持仓，实际记录与费用保留。窄屏大字体、长卡片和横屏需要滚动，不承诺全部字段同时在一屏。

## 密度、问题修复与局限

实测卡片高度（两 API、两主题相同，单位 dp）：

| 配置 | 股票 V2.0 → V2.1 | 期货 V2.0 → V2.1 |
|---|---|---|
| 360dp／字体1.0 | 200 → 236.5 | 208 → 208.5 |
| 320dp／字体1.3 | 300 → 293.5 | 247 → 258.5 |

常规股票增加了已实现净额及费用信息，卡片更高；窄屏期货也略增高。数字层级改善不等于所有卡片更紧凑。成功反馈条显示期间可视区更小，下部费用可稍后或滚动查看；用户仍需判断密度是否舒适。浅／深色无关键金额溢出，极窄屏加 IME 时上方辅助信息在滚动区之外，金额与保存操作可用。

真实修复记录：

1. 测试首次编译暴露 DpRect 宽高扩展使用错误，改为边界差，保留48dp断言。压力样本初版手算预期 .13 有误，独立 Decimal 复算后改 .02，未动生产计算。
2. [38901009552](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38901009552)：API29 136项中3失败、API35中2失败。兼容档案移至列表尾部后旧测试未滚动，改为实际滚动再点击；可选字段展开使用可见点击及有界等待，超时仍失败留图。
3. [38905734383](https://github.com/mlzx-xxj/trade-review-app/actions/runs/38905734383)：原134项均通过，新增测试仍失败。最初合并字体样式及保留余量并未解决。布局记录随后证明字符右边界245px已在控件内，但自然Text宽245px与段落260px不一致；让数字控件占满列宽，继续保留原溢出检查，并增加逐行字符边界断言，没有删失败用例。
4. API29另发现 IME 展开时用旧视口判断滚动，现等待真实IME和视口稳定，不重试点击、不直接写成交替代UI。全屏录入的系统栏背景与明暗图标也按实际截图修正。
5. 保存事件在重建时只处理一次，避免强制跳回历史；实际旋转／重建后选中持仓与数值检查已通过。最终复测以上所有用例，不把取消的过时运行当通过。

## 发布边界与手机验收

本轮为 UI 开发 PR，不发布 V2.1、不改 `v2.0.0` 标签或已有 APK。App 元数据仍 2.0.0/code22/com.tradereview.app；CI 独立 `.rc.v200` / 2.0.0-rc.3 / 非 debuggable 仅用于隔离自动安装验证，临时签名不能保证覆盖已有 RC，更不冒充正式签名。下一真正安装候选必须递增 versionCode，通过可信 main 与既有永久 V2 证书门禁；本 PR 不读取签名 Secrets、不生成密钥、不合并或发布 Stable。源码和 Environment 隔离保持不变。

本轮没有真实手机、TalkBack朗读或正式同证书升级实测。后续取得版本递增的可信候选后，由所有者逐项确认，不自动标通过：

- 在不删除既有数据的前提下，交易页录入股票及期货，比较单手选择、价格／数量切换和保存触感。
- 从卡片辨认数量、均价、方向、已实现净额，进入详情看日期与批次；判断卡片密度和颜色是否舒适。
- 用自己手机的输入法、系统深色、约1.3字体和横屏检查金额、滚动与保存；用TalkBack确认朗读顺序。
- 关闭重开核对同一笔；ZIP恢复只在隔离合成测试账本验证，不覆盖真实账本试验。

自动化能证明记录、布局边界与控件状态；审美、真实输入速度、单手可达性和不同厂商输入法仍由用户评估。

## PR #16 验收收尾：方向与持仓密度

发现的问题：此前多头标签调用盈利红色、空头调用亏损绿色，容易把方向与实际盈亏混淆。现统一为主色蓝灰标签，用“多头／空头”文字明确区分；红盈绿亏只用于真正的盈亏。生产修改仅这一处配色，不删除字段、不调整卡片尺寸或财务计算。

追加实际界面场景：原五笔基线比较之后，再录入 rb2701 卖开 2 手 4000、买平 1 手 4010，形成股票、rb 多头、i 待估算多头、rb 空头共四个持仓。两笔追加操作不混入原五笔点击计数。记录标签实际渲染色、卡片高度和原始截图，要求浅／深色、360dp／320dp 的所有配置通过；未获得新模拟器结果前不将本节标记通过。

versionCode 22 仅用于本阶段验证。真正准备 V2.1 安装升级包时必须递增 versionCode，并同步维护 CI 身份检查，不能以 V2.0.0/code22 作为正式 V2.1 升级包。本轮不改版本、正式包名、永久签名或 V2.0.0 标签，不合并、不发布 Stable。
