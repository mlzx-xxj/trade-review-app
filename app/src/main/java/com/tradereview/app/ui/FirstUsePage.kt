package com.tradereview.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstUsePage(firstRun:Boolean,onDone:()->Unit) {
    BackHandler(onBack=onDone)
    Dialog(onDismissRequest=onDone,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Scaffold(modifier=Modifier.fillMaxSize().testTag("first-use"),topBar={TopAppBar(title={Text(if(firstRun)"开始使用交易复盘"else"使用指南")})},
            bottomBar={Surface(shadowElevation=4.dp){Column(Modifier.navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick=onDone,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("onboarding-start")){Text(if(firstRun)"开始记录"else"返回应用")}
                if(firstRun)TextButton(onClick=onDone,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("onboarding-skip")){Text("跳过，稍后在设置查看")}
            }}}) {padding->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("first-use-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item{SectionCard("你的记录，留在本机","无需账号，不上传交易记录或截图。"){Text("交易、专业风控、截图和复盘由本机数据库与私有文件保存。应用没有联网权限。")}}
                item{SectionCard("一笔交易，快速完成") {Text("首页点“快捷记账”，点击日期、市场、品种、方向和盈亏状态，输入金额即可保存。专业价格与风险信息按需展开；未保存输入作为本地草稿。")}}
                item{SectionCard("复盘与统计，按节奏查看") {Text("底部切换交易、复盘、统计和日历。盘前勾选计划、盘后记录执行；统计页查看周月报告，首页进入本地智能洞察。不同币种独立统计。")}}
                item{SectionCard("截图识别，始终由你确认") {Text("从系统选择器选本地截图，中文、英文和数字在本机识别。逐字段核对后才创建交易；委托、持仓浮盈与K线图不能当作已平仓记录。可选择保留原图证据。")}}
                item{SectionCard("备份先行，长期使用") {Text("设置中的“完整 ZIP”包含业务数据和截图，JSON不含图片。导出完成后尽可能回读校验；备份是明文，SHA-256检测损坏，不是加密。")}}
                item{SectionCard("卸载与换机前，先确认备份") {Text("卸载、清除数据或手机丢失可能失去记录。先保存并检查完整ZIP，再在安全测试环境恢复；应用不会自动上传云盘。引导与本机备份历史不作为另一台手机已备份的证明。")}}
            }
        }
    }
}

@Composable
fun PrivacyDataCard(){SectionCard("隐私与数据说明"){
    Text("交易数据与截图在本机；OCR在本机运行，无交易数据自动上传。")
    Text("ZIP／JSON是明文文件。SHA-256用于检测损坏，不提供加密或身份认证。")
    Text("卸载或清除应用数据可能导致记录丢失。请先保存可读的完整ZIP；JSON不含图片。")
    Text("统计与智能洞察用于描述已有记录和复盘执行，不保证未来表现，也不提供交易指令。")
    Text("完整备份包含全部业务设置。引导完成状态、本机导出历史与提醒延后属于设备操作状态，不随业务恢复迁移。",style=MaterialTheme.typography.bodySmall)
}}
