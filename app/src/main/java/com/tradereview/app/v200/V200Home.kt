package com.tradereview.app.v200

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.math.BigDecimal
import java.math.RoundingMode

fun calendarLabel(value:BigDecimal?)=when{value==null->"无平仓";value.signum()==0->"持平";value.abs()>=BigDecimal(10000)->AutomaticEntry.cents(value.movePointLeft(4))+"万";else->AutomaticEntry.cents(value)}
fun sellable(state:LedgerSnapshot,cycle:LedgerCycle,day:Long):String {val facts=state.executions.associateBy{it.id};return exact(sum(state.lots.filter{it.cycleId==cycle.id && facts.getValue(it.executionId).tradingDay<day}.map{decimal(it.remaining)}))}
@Composable fun V200Home(state:LedgerScreenState,onCycle:(String)->Unit,onDay:(Long)->Unit){
    val ledger=state.ledger?:return;val today=AutomaticEntry.today();val epoch=today.toEpochDay();val week=today.minusDays((today.dayOfWeek.value-1).toLong()).toEpochDay();val month=today.withDayOfMonth(1).toEpochDay()
    var expanded by rememberSaveable{mutableStateOf(false)};var offset by rememberSaveable{mutableIntStateOf(0)}
    LazyColumn(Modifier.fillMaxSize().testTag("v2-home"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{LedgerPanel("累计表现 · 已实现净盈亏"){
            Text("仅V2实际平仓，旧档案不重复计入",style=MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("本周" to sum(state.daily.filterKeys{it in week..(week+6)}.values),"本月" to sum(state.daily.filterKeys{LocalDate.ofEpochDay(it).let{d->d.year==today.year && d.monthValue==today.monthValue}}.values),"历史累计" to sum(state.daily.values)).forEach{(label,value)->Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.labelMedium);NetText(if(state.closed.any{it.net==null} && state.closed.none{it.net!=null})null else value)}}}
            if(state.closed.any{it.net==null})Text("已知净盈亏；另有 ${state.closed.count{it.net==null}} 笔费用待估算的平仓未计入",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){(epoch-6..epoch).forEach{day->DayCell(day,state.daily[day],onDay,pending=state.closed.any{it.execution.tradingDay==day && it.net==null})}}
            TextButton(onClick={expanded=!expanded},modifier=Modifier.heightIn(min=48.dp).testTag("calendar-expand")){Text(if(expanded)"收起日历"else"展开月历")}
            if(expanded){val selected=YearMonth.from(today).plusMonths(offset.toLong());Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){TextButton({offset--},modifier=Modifier.heightIn(min=48.dp)){Text("上月")};Text("${selected.year}年${selected.monthValue}月",Modifier.padding(top=14.dp));TextButton({offset++},modifier=Modifier.heightIn(min=48.dp)){Text("下月")}}
                BoxWithConstraints(Modifier.fillMaxWidth()){val gridWidth=maxOf(maxWidth,336.dp);Row(Modifier.horizontalScroll(rememberScrollState())){Column(Modifier.width(gridWidth)){
                Row{listOf("一","二","三","四","五","六","日").forEach{Text(it,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)}}
                val start=selected.atDay(1).dayOfWeek.value-1;val count=selected.lengthOfMonth()
                repeat((start+count+6)/7){row->Row{repeat(7){col->val index=row*7+col-start;Box(Modifier.weight(1f)){if(index in 0 until count){val day=selected.atDay(index+1).toEpochDay();DayCell(day,state.daily[day],onDay,compact=true,pending=state.closed.any{it.execution.tradingDay==day && it.net==null})}}}}}
                }}}
            }
        }}
        item{Text("当前持仓",style=MaterialTheme.typography.titleLarge)}
        ledger.accounts.forEach{account->val positions=ledger.cycles.filter{it.accountId==account.id && decimal(it.remaining)>BigDecimal.ZERO};item{Text(account.name,style=MaterialTheme.typography.titleMedium)}
            if(positions.isEmpty())item{EmptyLedger("暂无未平仓持仓")}
            items(positions,key={it.id}){cycle->PositionCard(ledger,cycle,onCycle)}
        }
    }
}
@Composable private fun DayCell(day:Long,value:BigDecimal?,onDay:(Long)->Unit,compact:Boolean=false,pending:Boolean=false){Column(Modifier.widthIn(min=48.dp).heightIn(min=64.dp).clickable{onDay(day)}.padding(3.dp).semantics{contentDescription="${LocalDate.ofEpochDay(day)}，${value?.let{"已实现净盈亏${AutomaticEntry.cents(it)}元"}?:if(pending)"费用待估算，净盈亏待计算"else"无平仓记录"}"}.testTag("v2-day-$day")){Text(if(compact)LocalDate.ofEpochDay(day).dayOfMonth.toString()else"${LocalDate.ofEpochDay(day).monthValue}/${LocalDate.ofEpochDay(day).dayOfMonth}",style=MaterialTheme.typography.labelMedium);Text(if(pending && value==null)"待计算"else calendarLabel(value),color=value?.let{pnlColor(it)}?:MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)}}
@Composable fun PositionCard(state:LedgerSnapshot,c:LedgerCycle,onClick:(String)->Unit){LedgerPanel("${c.name} ${c.code} · ${directionLabel(c)}",Modifier.clickable{onClick(c.id)}.testTag("position-${c.id}")){
    Text("首次${if(c.accountId=="stock")"买入"else"开仓"} ${LocalDate.ofEpochDay(c.firstDay)} · ${c.remaining}${if(c.accountId=="stock")"股"else"手"}")
    Text("${if(c.accountId=="stock" && decimal(c.remaining)>BigDecimal.ZERO)"持仓成本均价（含未卖出买入费用）"else"平均开仓价"} ${if(c.accountId=="stock" && decimal(c.remaining)>BigDecimal.ZERO)amount(exact(ratio(decimal(c.openCost),decimal(c.remaining))))else amount(c.averagePrice)}")
    if(c.accountId=="stock")Text("可卖 ${sellable(state,c,AutomaticEntry.today().toEpochDay())}股 · 持仓成本 ${amount(c.openCost)}")
    Text("止盈 ${c.takeProfit?.let(::amount)?:"未设置"} · 止损 ${c.stopLoss?.let(::amount)?:"未设置"}")
    if(c.accountId=="futures"){Text("参考保证金 ${amount(c.referenceMargin)}");Text("累计手续费 ${amount(c.fees)}")}
}}
@Composable fun ClosedDayDialog(state:LedgerScreenState,day:Long,onDismiss:()->Unit){val values=state.closed.filter{it.execution.tradingDay==day};AlertDialog(onDismissRequest=onDismiss,title={Text("${LocalDate.ofEpochDay(day)} 已实现")},text={Column{if(values.isEmpty())EmptyLedger("当日没有平仓记录")else{NetText(completeTotal(values.map{exactOrNull(it.net)}));if(values.any{it.net==null})Text("含待计算平仓，明细未作为零计入");LazyColumn(Modifier.heightIn(max=350.dp)){items(values,key={it.execution.id}){v->Column(Modifier.padding(vertical=8.dp)){Text("${v.execution.name} ${v.execution.code} · ${actionLabel(v.execution)}");Text("${v.execution.quantity}${if(v.execution.accountId=="stock")"股"else"手"} · 成交价${amount(v.execution.price)}");NetText(v.net)}}}}}},confirmButton={TextButton(onDismiss,modifier=Modifier.heightIn(min=48.dp)){Text("关闭")}})}
