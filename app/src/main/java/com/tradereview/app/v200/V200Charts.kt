package com.tradereview.app.v200

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** ChartScale and the underlying Decimal series are unchanged; only their presentation changes. */
@Composable fun DailyClosedBars(days:List<Long>,values:Map<Long,BigDecimal>,pending:Set<Long> = emptySet()){
    val axis=remember(values){ChartScale.axis(values.values.toList())}
    val positive=profitColor();val negative=lossColor();val neutral=MaterialTheme.colorScheme.onSurfaceVariant;val grid=MaterialTheme.colorScheme.outlineVariant
    val density=LocalDensity.current;val measurer=rememberTextMeasurer();val axisStyle=MaterialTheme.typography.labelSmall.copy(fontSize=11.sp,letterSpacing=0.sp)
    val axisWidth=with(density){(axis.ticks.maxOf{measurer.measure(AnnotatedString(axis.label(it)),axisStyle,softWrap=false).size.width}).toDp()+8.dp}.coerceAtLeast(48.dp)
    val cell=(52f*max(1f,density.fontScale/1.3f)).dp;val scroll=rememberScrollState()
    var selected by remember(days){mutableStateOf<Long?>(null)}
    LaunchedEffect(days){scroll.scrollTo(0)}
    LedgerPanel("每日已实现净盈亏（${axis.unitLabel}）"){
        if(values.isEmpty())Text(if(pending.isEmpty())"本周期暂无已实现结果"else"本周期仅有费用待估算的平仓，净盈亏未计算",style=MaterialTheme.typography.bodySmall,color=neutral)
        Row(Modifier.fillMaxWidth()){
            Canvas(Modifier.width(axisWidth).height(256.dp).semantics{contentDescription="金额轴，${axis.unitLabel}，刻度 ${axis.ticks.joinToString { axis.label(it) }}，含零轴"}){
                val top=12.dp.toPx();val height=208.dp.toPx()
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=neutral.toArgb();textSize=11.sp.toPx();textAlign=android.graphics.Paint.Align.RIGHT}
                axis.ticks.forEach{tick->val y=top+ChartScale.y(tick,axis,height);drawIntoCanvas{it.nativeCanvas.drawText(axis.label(tick),size.width-6.dp.toPx(),y+paint.textSize/3,paint)}}
            }
            Box(Modifier.weight(1f).horizontalScroll(scroll).testTag("v2-daily-chart-scroll")){
                Canvas(Modifier.width(cell*days.size).height(256.dp).testTag("v2-daily-chart").semantics{
                    contentDescription="${days.size}天每日净盈亏，红盈绿亏；左右滚动，点击日期柱查看精确金额；持平、无平仓与待估算分别标记"
                    customActions=days.map{day->CustomAccessibilityAction("查看${LocalDate.ofEpochDay(day)}准确数值"){selected=day;true}}
                }.pointerInput(days,values,cell){detectTapGestures{point->ChartScale.hitIndex(point.x,cell.toPx(),days.size)?.let{selected=days[it]}}}){
                    val top=12.dp.toPx();val height=208.dp.toPx();val width=cell.toPx();val zero=top+ChartScale.y(BigDecimal.ZERO,axis,height)
                    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=neutral.toArgb();textSize=12.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER}
                    axis.ticks.forEach{tick->val y=top+ChartScale.y(tick,axis,height);drawLine(if(tick.signum()==0)neutral else grid,Offset(0f,y),Offset(size.width,y),if(tick.signum()==0)2.dp.toPx()else 1.dp.toPx())}
                    days.forEachIndexed{i,day->
                        val center=width*(i+.5f);val value=values[day]
                        if(value==null){
                            if(day in pending){val radius=5.dp.toPx();val diamond=Path().apply{moveTo(center,zero-radius);lineTo(center+radius,zero);lineTo(center,zero+radius);lineTo(center-radius,zero);close()};drawPath(diamond,neutral,style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))}
                            else drawCircle(neutral,3.dp.toPx(),Offset(center,zero),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        }else if(value.signum()==0)drawLine(neutral,Offset(center-8.dp.toPx(),zero),Offset(center+8.dp.toPx(),zero),3.dp.toPx())
                        else{val y=top+ChartScale.y(value,axis,height);drawRect(if(value.signum()>0)positive else negative,Offset(center-width*.27f,min(zero,y)),Size(width*.54f,max(abs(y-zero),1f)))}
                        drawIntoCanvas{it.nativeCanvas.drawText("${LocalDate.ofEpochDay(day).dayOfMonth}${if(day in pending)"待"else"日"}",center,top+height+24.dp.toPx(),paint)}
                    }
                }
            }
        }
        Text("红 ↑ 盈利 · 绿 ↓ 亏损 · 横线持平\n空心圆无平仓 · ◇ 待估算；“待”日可能含已知部分",style=MaterialTheme.typography.labelSmall,color=neutral)
        Text("左右滑动 · 点柱形或用无障碍操作查看准确金额",style=MaterialTheme.typography.bodySmall,color=neutral)
    }
    selected?.let{day->AlertDialog(onDismissRequest={selected=null},title={Text(LocalDate.ofEpochDay(day).toString())},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        values[day]?.let{if(day in pending)Text("已知部分净盈亏；另有费用待估算的平仓",style=MaterialTheme.typography.bodySmall);NetText(it)}?:Text(if(day in pending)"费用待估算，净盈亏待计算"else"没有平仓成交")
    }},confirmButton={TextButton({selected=null},modifier=Modifier.heightIn(min=48.dp)){Text("关闭")}})}
}

@Composable fun ContractDistribution(values:List<Pair<String,BigDecimal>>){
    val axis=remember(values){ChartScale.axis(values.map{it.second})};val positive=profitColor();val negative=lossColor();val neutral=MaterialTheme.colorScheme.onSurfaceVariant;val grid=MaterialTheme.colorScheme.outlineVariant
    var selected by remember(values){mutableStateOf<Pair<String,BigDecimal>?>(null)}
    val density=LocalDensity.current
    LedgerPanel("本月品种／合约净盈亏（${axis.unitLabel}）"){
        if(values.isEmpty())EmptyLedger("本月暂无可计算净盈亏的平仓结果")
        values.forEach{item->
            Column(Modifier.fillMaxWidth().heightIn(min=48.dp).pointerInput(item){detectTapGestures{selected=item}}.semantics{
                contentDescription="${item.first}，${when(item.second.signum()){1->"盈利";-1->"亏损";else->"持平"}}，已实现净盈亏${AutomaticEntry.cents(item.second)}元，点击查看"
                onClick("查看精确值"){selected=item;true}
            }.testTag("distribution-${item.first.substringAfterLast(' ')}"),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(item.first.let{val code=it.substringAfterLast(' ');if(it.substringBeforeLast(' ')==code)code else it},Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Column(Modifier.weight(1f)){
                        NumericText(moneyText(item.second),Modifier.testTag("distribution-net-${item.first.substringAfterLast(' ')}"),style=MaterialTheme.typography.titleSmall,color=pnlColor(item.second))
                        Text(when(item.second.signum()){1->"+ 盈利";-1->"− 亏损";else->"持平"},style=MaterialTheme.typography.labelSmall,color=pnlColor(item.second))
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(36.dp)){
                    val zero=ChartScale.x(BigDecimal.ZERO,axis,size.width);val x=ChartScale.x(item.second,axis,size.width)
                    drawLine(grid,Offset(0f,size.height/2),Offset(size.width,size.height/2),1.dp.toPx())
                    drawLine(neutral,Offset(zero,0f),Offset(zero,size.height),2.dp.toPx())
                    if(item.second.signum()==0)drawCircle(neutral,4.dp.toPx(),Offset(zero,size.height/2))
                    else drawRect(if(item.second.signum()>0)positive else negative,Offset(min(zero,x),8.dp.toPx()),Size(max(abs(x-zero),1f),20.dp.toPx()))
                }
            }
        }
        if(values.isNotEmpty()){
            // Endpoints and zero use separate lanes; a small negative beside a large
            // positive must not make their labels overlap. No tick value is altered.
            Canvas(Modifier.fillMaxWidth().height((48f*max(1f,density.fontScale)).dp).semantics{contentDescription="统一金额轴：最小${axis.label(axis.minimum)}，零，最大${axis.label(axis.maximum)}，${axis.unitLabel}"}){
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=neutral.toArgb();textSize=11.sp.toPx()}
                val zero=ChartScale.x(BigDecimal.ZERO,axis,size.width)
                paint.textAlign=when{zero<1f->android.graphics.Paint.Align.LEFT;zero>=size.width-1f->android.graphics.Paint.Align.RIGHT;else->android.graphics.Paint.Align.CENTER}
                drawIntoCanvas{it.nativeCanvas.drawText("0.00",zero,paint.textSize,paint)}
                listOf(axis.minimum,axis.maximum).filter{it.signum()!=0}.forEach{tick->
                    val x=ChartScale.x(tick,axis,size.width);paint.textAlign=if(x<1f)android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                    drawIntoCanvas{it.nativeCanvas.drawText(axis.label(tick),x,paint.textSize*2.5f,paint)}
                }
            }
            Text("统一零轴 · 左亏损／右盈利 · 点击查看精确值",style=MaterialTheme.typography.labelSmall,color=neutral)
        }
    }
    selected?.let{item->AlertDialog(onDismissRequest={selected=null},title={Text(item.first)},text={NetText(item.second)},confirmButton={TextButton({selected=null},modifier=Modifier.heightIn(min=48.dp)){Text("关闭")}})}
}
