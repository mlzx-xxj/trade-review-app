# V0.5 实际验证记录

基础 main be22c68（已合并 PR #4），独立分支 codex/v05-risk-analytics。Base 无 SDK，未运行本地 Android 编译或真机测试，未安装 Android Studio 或新建环境。

已实际本地执行：签名门禁 6、配置完整性 3、公开签名 payload 4、模拟器交付门禁 3，共 16 项通过。只验证发布脚本，不代替 Android 编译/测试。

已写入 JVM 计算/规则/筛选/R/旧备份/新格式 golden 与 CSV 回归；原 49 项 JVM 用例保留并更新当前格式的预期。原 43 项模拟器保留，扩展正式 v4→v5 和 v1→v5、九表失败回滚、风险确认/过期拒绝、专业编辑、草稿、规则/模板、R 与深色界面。预计数量不作为通过结果；后续以 Actions 的实际 XML 更新。

全部编译、JVM、Lint 与 connectedDebugAndroidTest 显式 --no-build-cache。截图由模拟器 UI 实际生成，使用合成数据；发布脚本要求原八张和新增五张齐全，保持模拟器原失败码。V0.4 第四张采集限制保留（列表/筛选入口），本轮专业窗口采用系统窗口截图及渲染帧同步，结果需逐图确认。

当前尚未得到本分支的 Android 编译、JVM、Lint 或模拟器结果，不能视为通过。最终提交含文档更新也必须完整 CI，不使用 skip ci。最终提交状态、完整结果、APK 与截图会在独立 PR 中记录。

签名：main 构建日志实际未配置固定密钥，管理 API 403，保留 V0.3 pin；本轮不读写长期私钥。待本轮实际 APK 与 SIGNING.txt 核对后报告。计算与兼容口径见 V05-CALCULATIONS-AND-UPGRADES.md。
