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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun calendarLabel(value:BigDecimal?)=when{value==null->"无平仓";value.signum()==0->"持平";value.abs()>=BigDecimal(10000)->AutomaticEntry.cents(value.movePointLeft(4))+"万";else->AutomaticEntry.cents(value)}
fun sellable(state:LedgerSnapshot,cycle:LedgerCycle,day:Long):String {val facts=state.executions.associateBy{it.id};return exact(sum(state.lots.filter{it.cycleId==cycle.id && facts.getValue(it.executionId).tradingDay<day}.map{decimal(it.remaining)}))}
private data class RecentHomeExecutions(val source:List<LedgerExecution>,val rows:List<LedgerExecution>)
@Composable fun V200Home(state:LedgerScreenState,onCycle:(String)->Unit,onDay:(Long)->Unit,onTrades:()->Unit={},onReviews:()->Unit={},onExecution:(String)->Unit={}){
    val ledger=state.ledger?:return;val today=AutomaticEntry.today();val epoch=today.toEpochDay();val week=today.minusDays((today.dayOfWeek.value-1).toLong()).toEpochDay()
    var expanded by rememberSaveable{mutableStateOf(false)};var offset by rememberSaveable{mutableIntStateOf(0)}
    val weekly=remember(state.closed,today){state.closed.filter{it.execution.tradingDay in week..week+6}}
    val monthly=remember(state.closed,today){state.closed.filter{LocalDate.ofEpochDay(it.execution.tradingDay).let{d->d.year==today.year&&d.monthValue==today.monthValue}}}
    val current=remember(ledger.cycles){ledger.cycles.filter{decimal(it.remaining)>BigDecimal.ZERO}}
    val byExecution=remember(state.closed){state.closed.associateBy{it.execution.id}}
    val pendingDays=remember(state.closed){state.closed.filter{it.net==null}.map{it.execution.tradingDay}.toSet()}
    val recentResult by produceState<RecentHomeExecutions?>(null,ledger.executions){val source=ledger.executions;value=withContext(Dispatchers.Default){RecentHomeExecutions(source,MatchingEngine.ordered(source).takeLast(3).reversed())}}
    val recent=recentResult?.takeIf{it.source==ledger.executions}?.rows
    LazyColumn(Modifier.fillMaxSize().testTag("v2-home"),contentPadding=PaddingValues(LedgerUi.inset),verticalArrangement=Arrangement.spacedBy(LedgerUi.inset)){
        item{
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                OverviewHeading("累计表现",action=if(expanded)"收起月历"else"展开月历",onAction={expanded=!expanded},actionTag="calendar-expand")
                BoxWithConstraints(Modifier.fillMaxWidth()){
                    if(maxWidth>=460.dp)Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                        OverviewMoney("本周已实现",completeTotal(weekly.map{exactOrNull(it.net)}),"home-realized-week",Modifier.weight(1f),large=true,empty=weekly.isEmpty(),showState=false)
                        OverviewMoney("本月",completeTotal(monthly.map{exactOrNull(it.net)}),"home-realized-month",Modifier.weight(1f),empty=monthly.isEmpty(),showState=false)
                        OverviewMoney("历史累计",completeTotal(state.closed.map{exactOrNull(it.net)}),"home-realized-all",Modifier.weight(1f),empty=state.closed.isEmpty(),showState=false)
                    }else Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                        OverviewMoney("本周已实现",completeTotal(weekly.map{exactOrNull(it.net)}),"home-realized-week",large=true,empty=weekly.isEmpty())
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                            OverviewMoney("本月",completeTotal(monthly.map{exactOrNull(it.net)}),"home-realized-month",Modifier.weight(1f),empty=monthly.isEmpty(),showState=false)
                            OverviewMoney("历史累计",completeTotal(state.closed.map{exactOrNull(it.net)}),"home-realized-all",Modifier.weight(1f),empty=state.closed.isEmpty(),showState=false)
                        }
                    }
                }
                if(state.closed.any{it.net==null})Text("费用待估算的平仓使相关完整合计保持未知；统计页另列已知样本。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val strip=rememberScrollState()
                LaunchedEffect(strip.maxValue){strip.scrollTo(strip.maxValue)}
                Row(Modifier.fillMaxWidth().horizontalScroll(strip),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    (epoch-6..epoch).forEach{day->DayCell(day,state.daily[day],onDay,pending=day in pendingDays,today=day==epoch)}
                }
                if(expanded){
                    val selected=YearMonth.from(today).plusMonths(offset.toLong())
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        TextButton({offset--},Modifier.heightIn(min=48.dp)){Text("上月")}
                        Text("${selected.year}年${selected.monthValue}月",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        TextButton({offset++},Modifier.heightIn(min=48.dp)){Text("下月")}
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()){
                        val width=maxOf(maxWidth,336.dp)
                        Row(Modifier.horizontalScroll(rememberScrollState())){Column(Modifier.width(width)){
                            Row{listOf("一","二","三","四","五","六","日").forEach{Text(it,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
                            val start=selected.atDay(1).dayOfWeek.value-1;val count=selected.lengthOfMonth()
                            repeat((start+count+6)/7){row->Row{repeat(7){col->val index=row*7+col-start;Box(Modifier.weight(1f),contentAlignment=Alignment.Center){if(index in 0 until count){val day=selected.atDay(index+1).toEpochDay();DayCell(day,state.daily[day],onDay,compact=true,pending=day in pendingDays,today=day==epoch)}}}}}
                        }}
                    }
                    Text("+ 盈利／− 亏损／持平／待算；— 无平仓。点日期看金额，窄屏可左右滑动。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }}
        }
        item{OverviewHeading("当前持仓",if(current.isEmpty())"尚无未平仓持仓"else"${current.size} 个持仓周期","查看交易",onTrades)}
        ledger.accounts.forEach{account->val positions=current.filter{it.accountId==account.id}
            item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(account.name,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${positions.size} 个持仓 · ${exact(sum(positions.map{decimal(it.remaining)}))}${if(account.id=="stock")"股"else"手"}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            if(positions.isEmpty())item{Text("暂无未平仓持仓",Modifier.padding(bottom=4.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(positions,key={it.id}){cycle->PositionCard(ledger,cycle,onCycle)}
        }
        item{OverviewHeading("近期成交",null,"去复盘",onReviews)}
        if(recent==null)item{Text("正在整理近期成交",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        else if(recent.isEmpty())item{EmptyLedger("尚无成交记录，可在交易页记下第一笔真实成交。")}
        items(recent.orEmpty(),key={"recent-${it.id}"}){e->
            Surface(modifier=Modifier.fillMaxWidth().clickable{onExecution(e.id)}.testTag("home-recent-${e.id}"),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface){
                Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(if(e.name==e.code)e.code else "${e.name} ${e.code}",style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${LocalDate.ofEpochDay(e.date)} · ${actionLabel(e)} · ${e.quantity}${if(e.accountId=="stock")"股"else"手"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(.7f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                        Text(if(e.effect=="open")"成交价"else"已实现净盈亏",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(e.effect=="open")NumericText(amount(e.price),style=MaterialTheme.typography.bodyLarge)else NetText(byExecution[e.id]?.net,showState=true)
                    }
                }
            }
        }
        item{Text("人民币 · 仅 V2 实际平仓；兼容历史档案不重复计入。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
@Composable private fun DayCell(day:Long,value:BigDecimal?,onDay:(Long)->Unit,compact:Boolean=false,pending:Boolean=false,today:Boolean=false){
    val description="${LocalDate.ofEpochDay(day)}，${value?.let{"${if(pending)"已知部分"else"已实现"}净盈亏${AutomaticEntry.cents(it)}元，${when(it.signum()){1->"盈利";-1->"亏损";else->"持平"}}${if(pending)"，另有费用待估算的平仓"else""}"}?:if(pending)"费用待估算，净盈亏待计算"else"无平仓记录"}"
    val color=value?.let{pnlColor(it)}?:MaterialTheme.colorScheme.onSurfaceVariant
    val width=if(compact)48.dp else if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.15f)92.dp else 72.dp
    Surface(shape=MaterialTheme.shapes.small,color=if(today)MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent,modifier=Modifier.width(width).heightIn(min=72.dp).clickable{onDay(day)}.semantics{contentDescription=description}.testTag("v2-day-$day")){
        Column(Modifier.padding(horizontal=4.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(if(compact)LocalDate.ofEpochDay(day).dayOfMonth.toString()else"${LocalDate.ofEpochDay(day).monthValue}/${LocalDate.ofEpochDay(day).dayOfMonth}",style=MaterialTheme.typography.labelMedium,color=if(today)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=if(today)FontWeight.SemiBold else FontWeight.Normal)
            if(compact)Text(if(pending)"待算"else if(value==null)"—"else when(value.signum()){1->"+ 盈";-1->"− 亏";else->"持平"},style=MaterialTheme.typography.labelMedium,color=if(pending)MaterialTheme.colorScheme.onSurfaceVariant else color)
            else{NumericText(if(pending&&value==null)"待计算"else if(value==null)"—"else calendarLabel(value),style=MaterialTheme.typography.labelLarge,color=color);Text(if(pending)"费用待估算"else if(value==null)"无平仓"else when(value.signum()){1->"+ 盈利";-1->"− 亏损";else->"持平"},style=MaterialTheme.typography.labelSmall,color=if(pending)MaterialTheme.colorScheme.onSurfaceVariant else color)}
        }
    }
}
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
