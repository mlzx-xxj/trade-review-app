package com.tradereview.app.v200

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(Triple("week","本周",state.closed.filter{it.execution.tradingDay in week..(week+6)}),Triple("month","本月",state.closed.filter{LocalDate.ofEpochDay(it.execution.tradingDay).let{d->d.year==today.year && d.monthValue==today.monthValue}}),Triple("all","历史累计",state.closed)).forEach{(key,label,values)->Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.labelMedium);NetText(completeTotal(values.map{exactOrNull(it.net)}),Modifier.testTag("home-realized-$key"))}}
            }
            if(state.closed.any{it.net==null})Text("含 ${state.closed.count{it.net==null}} 笔费用待估算的平仓；涉及周期的完整合计待估算，统计可查看已知样本。",style=MaterialTheme.typography.bodySmall)
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
@Composable private fun DayCell(day:Long,value:BigDecimal?,onDay:(Long)->Unit,compact:Boolean=false,pending:Boolean=false){Column(Modifier.widthIn(min=48.dp).heightIn(min=64.dp).clickable{onDay(day)}.padding(3.dp).semantics{contentDescription="${LocalDate.ofEpochDay(day)}，${value?.let{"${if(pending)"已知部分"else"已实现"}净盈亏${AutomaticEntry.cents(it)}元${if(pending)"，另有费用待估算的平仓"else""}"}?:if(pending)"费用待估算，净盈亏待计算"else"无平仓记录"}"}.testTag("v2-day-$day")){Text(if(compact)LocalDate.ofEpochDay(day).dayOfMonth.toString()else"${LocalDate.ofEpochDay(day).monthValue}/${LocalDate.ofEpochDay(day).dayOfMonth}",style=MaterialTheme.typography.labelMedium);Text(if(pending)if(value==null)"待计算"else"已知 ${calendarLabel(value)}"else calendarLabel(value),color=value?.let{pnlColor(it)}?:MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)}}
@Composable fun PositionCard(state:LedgerSnapshot,c:LedgerCycle,onClick:(String)->Unit){
    val stock=c.accountId=="stock";val unit=if(stock)"股"else"手"
    val average=if(stock && decimal(c.remaining)>BigDecimal.ZERO)amount(exact(ratio(decimal(c.openCost),decimal(c.remaining))))else amount(c.averagePrice)
    Card(onClick={onClick(c.id)},modifier=Modifier.fillMaxWidth().testTag("position-${c.id}"),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.padding(LedgerUi.inset),verticalArrangement=Arrangement.spacedBy(LedgerUi.gap)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(c.name,Modifier.weight(1f,fill=false).testTag("position-name-${c.id}"),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(c.name!=c.code)Text(c.code,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Direction describes the position, never its financial result.
                StatusPill(directionLabel(c),color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("position-direction-${c.id}"))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                PositionMetric("剩余持仓", "${c.remaining} $unit","position-quantity-${c.id}",Modifier.weight(1f),large=true)
                PositionMetric(if(stock && decimal(c.remaining)>BigDecimal.ZERO)"持仓成本均价"else"平均开仓价",average,"position-price-${c.id}",Modifier.weight(1f),large=true)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    val status=if(state.matches.none{it.cycleId==c.id})"尚无平仓"else c.realized?.let{when(decimal(it).signum()){1->"+ 盈利";-1->"亏损";else->"持平"}}?:"待计算"
                    Text("已实现净盈亏 · $status",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    NetText(c.realized?.let(::decimal),Modifier.testTag("position-net-${c.id}"),showState=false)
                }
                PositionMetric(if(stock)"当前可卖"else"参考保证金",if(stock)"${sellable(state,c,AutomaticEntry.today().toEpochDay())} 股"else amount(c.referenceMargin),if(stock)"position-sellable-${c.id}"else"position-margin-${c.id}",Modifier.weight(1f))
            }
            PositionMoneyLine("已记录手续费",amount(c.fees),"position-fees-${c.id}")
            if(stock)PositionMoneyLine("持仓成本",amount(c.openCost),"position-cost-${c.id}")
            if(c.takeProfit!=null || c.stopLoss!=null)Text("止盈 ${c.takeProfit?.let(::amount)?:"未设置"} · 止损 ${c.stopLoss?.let(::amount)?:"未设置"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun ClosedDayDialog(state:LedgerScreenState,day:Long,onDismiss:()->Unit){val values=state.closed.filter{it.execution.tradingDay==day};AlertDialog(onDismissRequest=onDismiss,title={Text("${LocalDate.ofEpochDay(day)} 已实现")},text={Column{if(values.isEmpty())EmptyLedger("当日没有平仓记录")else{NetText(completeTotal(values.map{exactOrNull(it.net)}));if(values.any{it.net==null})Text("含待计算平仓，明细未作为零计入");LazyColumn(Modifier.heightIn(max=350.dp)){items(values,key={it.execution.id}){v->Column(Modifier.padding(vertical=8.dp)){Text("${v.execution.name} ${v.execution.code} · ${actionLabel(v.execution)}");Text("${v.execution.quantity}${if(v.execution.accountId=="stock")"股"else"手"} · 成交价${amount(v.execution.price)}");NetText(v.net)}}}}}},confirmButton={TextButton(onDismiss,modifier=Modifier.heightIn(min=48.dp)){Text("关闭")}})}
