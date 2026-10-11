# 交易复盘 MVP 计划

## 范围与结构

最低 Android 8.0（API 26）。单 Activity + Compose Material 3，四个底部导航页面。

```
app/src/main/java/com/tradereview/app/
  MainActivity.kt                 入口与 ViewModel 创建
  TradeReviewApplication.kt       Room 单例
  data/Models.kt                  交易、每日计划/复盘、预设标签
  data/TradeDatabase.kt           DAO、Room、标签转换器
  domain/Analytics.kt             精确金额、胜率、曲线、策略统计
  ui/TradeViewModel.kt            数据流、保存状态与错误处理
  ui/TradeReviewApp.kt            四页面与导航
  ui/TradeEditor.kt               交易表单、日期选择
  ui/Components.kt                卡片、标签、曲线
  ui/Theme.kt                     配色与深色模式
```

1. 项目结构与数据模型：确认版本、数据隔离、输入约束；检查工程静态结构。
2. 可运行最小版本：首页概览、交易增改删、每日盘前计划/盘后复盘、统计。金额用 BigDecimal，按币种统计，不混加 CNY/USD/USDT。预设标签与滑块减少输入；日期可选择；错误与执行纪律可随交易保存。
3. 云端构建：JDK 17、Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose 插件 2.1.20、KSP 2.1.20-1.0.32、Compose BOM 2025.04.01、Room 2.7.2、Android SDK 35。
4. 验证交付：JVM 测试金额、统计与输入；模拟器测试 Room 增改删/重开持久化、每日计划更新和页面导航；Android Lint；上传 Debug APK 和报告；创建 PR 并记录实际云端结果。

## 数据与隐私

- 交易仅记录已平仓交易；盈亏输入为扣除费用后的净盈亏，不计算实时行情或浮动收益。
- 仓位是本笔交易占账户的百分比；数字资产 USDT 不与美元合并。
- 日期用 LocalDate 的 epochDay 存储，跨时区保持交易日。
- Room 私有本地数据库。没有 INTERNET 权限、登录、分析 SDK或联网数据接口；关闭云备份与设备迁移。
- 默认空数据库，演示数据只可手动添加。卸载将删除数据，MVP 暂无导出。
- Debug 包用于试用，后续正式签名发布需独立版本与备份/迁移方案。

## 验证原则

源码检查不等于编译；编译不等于真机测试。每阶段只记实际执行的检查，不预先填写通过状态。Base 缺少 SDK 且网络授权中止时，不安装 Android Studio；由 GitHub Actions 生成官方 Wrapper、编译并测试。
