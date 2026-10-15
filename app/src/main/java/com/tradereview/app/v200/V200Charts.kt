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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable fun DailyClosedBars(days:List<Long>,values:Map<Long,BigDecimal>,pending:Set<Long> = emptySet()){val axis=remember(values){ChartScale.axis(values.values.toList())};val positive=profitColor();val negative=lossColor();val neutral=MaterialTheme.colorScheme.onSurfaceVariant;val line=MaterialTheme.colorScheme.outlineVariant;val density=LocalDensity.current;val cell=(52f*max(1f,density.fontScale/1.3f)).dp;var selected by remember{mutableStateOf<Long?>(null)}
    Text("每日已实现净盈亏（${axis.unitLabel}）",style=MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth()){
        Canvas(Modifier.width(64.dp).height(276.dp).semantics{contentDescription="金额轴，${axis.unitLabel}，零轴清晰"}){val height=240.dp.toPx();val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=neutral.toArgb();textSize=11.sp.toPx();textAlign=android.graphics.Paint.Align.RIGHT};axis.ticks.forEach{tick->val y=ChartScale.y(tick,axis,height);drawIntoCanvas{it.nativeCanvas.drawText(axis.label(tick),size.width-4.dp.toPx(),(y+paint.textSize/3).coerceIn(paint.textSize,size.height-36.dp.toPx()),paint)}}}
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("v2-daily-chart-scroll")){
            Canvas(Modifier.width(cell*days.size).height(276.dp).testTag("v2-daily-chart").semantics{contentDescription="${days.size}天每日净盈亏，红盈绿亏；左右滚动，点击日期柱查看精确金额；持平与无平仓分别标记";customActions=days.map{day->CustomAccessibilityAction("查看${LocalDate.ofEpochDay(day)}准确数值"){selected=day;true}}}.pointerInput(days,values,cell){detectTapGestures{position->ChartScale.hitIndex(position.x,cell.toPx(),days.size)?.let{selected=days[it]}}}){
                val height=240.dp.toPx();val width=cell.toPx();val zero=ChartScale.y(BigDecimal.ZERO,axis,height);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=neutral.toArgb();textSize=12.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER}
                axis.ticks.forEach{tick->val y=ChartScale.y(tick,axis,height);drawLine(if(tick.signum()==0)neutral else line,Offset(0f,y),Offset(size.width,y),if(tick.signum()==0)2.dp.toPx()else 1.dp.toPx())}
                days.forEachIndexed{i,day->val center=width*(i+.5f);val value=values[day];if(value==null)drawCircle(neutral,3.dp.toPx(),Offset(center,zero),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))else if(value.signum()==0)drawLine(neutral,Offset(center-8.dp.toPx(),zero),Offset(center+8.dp.toPx(),zero),3.dp.toPx())else{val y=ChartScale.y(value,axis,height);drawRect(if(value.signum()>0)positive else negative,Offset(center-width*.28f,min(zero,y)),Size(width*.56f,max(abs(y-zero),1f)))};drawIntoCanvas{it.nativeCanvas.drawText("${LocalDate.ofEpochDay(day).dayOfMonth}${if(day in pending)"待"else"日"}",center,height+22.dp.toPx(),paint)}}
            }
        }
    }
    Text("红色盈利 · 绿色亏损 · 横线持平 · 空心点无已知结果；标“待”的日期有费用待估算",style=MaterialTheme.typography.bodySmall)
    selected?.let{day->AlertDialog(onDismissRequest={selected=null},title={Text(LocalDate.ofEpochDay(day).toString())},text={values[day]?.let{NetText(it)}?:Text(if(day in pending)"费用待估算，净盈亏待计算"else"没有平仓成交")},confirmButton={TextButton({selected=null},modifier=Modifier.heightIn(min=48.dp)){Text("关闭")}})}
}
@Composable fun ContractDistribution(values:List<Pair<String,BigDecimal>>){val axis=remember(values){ChartScale.axis(values.map{it.second})};val positive=profitColor();val negative=lossColor();val neutral=MaterialTheme.colorScheme.onSurfaceVariant;var selected by remember{mutableStateOf<Pair<String,BigDecimal>?>(null)}
    Text("本月品种／合约净盈亏（${axis.unitLabel}）",style=MaterialTheme.typography.titleMedium)
    if(values.isEmpty())EmptyLedger("本月暂无可计算净盈亏的平仓结果")
    values.forEach{item->Column(Modifier.fillMaxWidth().padding(vertical=6.dp).pointerInput(item){detectTapGestures{selected=item}}.semantics{contentDescription="${item.first}，已实现净盈亏${AutomaticEntry.cents(item.second)}元，点击查看";onClick("查看精确值"){selected=item;true}}.testTag("distribution-${item.first.substringAfterLast(' ')}")){Text(item.first,style=MaterialTheme.typography.bodyMedium);Canvas(Modifier.fillMaxWidth().height(44.dp)){val zero=ChartScale.x(BigDecimal.ZERO,axis,size.width);val value=ChartScale.x(item.second,axis,size.width);drawLine(neutral,Offset(zero,0f),Offset(zero,size.height),2.dp.toPx());if(item.second.signum()==0)drawCircle(neutral,4.dp.toPx(),Offset(zero,size.height/2))else drawRect(if(item.second.signum()>0)positive else negative,Offset(min(zero,value),10.dp.toPx()),Size(max(abs(value-zero),1f),24.dp.toPx()))};Canvas(Modifier.fillMaxWidth().height(24.dp)){val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=neutral.toArgb();textSize=11.sp.toPx()};listOf(axis.minimum,BigDecimal.ZERO,axis.maximum).distinctBy(::exact).forEach{tick->val x=ChartScale.x(tick,axis,size.width);paint.textAlign=if(x<1f)android.graphics.Paint.Align.LEFT else if(x>=size.width-1f)android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.CENTER;drawIntoCanvas{it.nativeCanvas.drawText(axis.label(tick),x,16.dp.toPx(),paint)}}}}}
    selected?.let{item->AlertDialog(onDismissRequest={selected=null},title={Text(item.first)},text={NetText(item.second)},confirmButton={TextButton({selected=null}){Text("关闭")}})}
}
