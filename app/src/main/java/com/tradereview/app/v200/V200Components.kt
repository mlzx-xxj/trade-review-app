package com.tradereview.app.v200

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.math.BigDecimal

object LedgerUi { val gap=8.dp;val inset=12.dp;val section=16.dp;val radius=16.dp;val touch=48.dp }
@Composable fun profitColor()=if(isSystemInDarkTheme())Color(0xffffb4b0)else Color(0xffaf273c)
@Composable fun lossColor()=if(isSystemInDarkTheme())Color(0xff80d5af)else Color(0xff087044)
@Composable fun pnlColor(value:BigDecimal)=when(value.signum()){1->profitColor();-1->lossColor();else->MaterialTheme.colorScheme.onSurfaceVariant}
/** Presentation only: HALF_UP to fen, preserving all persisted/replay precision. */
fun amount(value:String?)=value?.let{"￥${AutomaticEntry.cents(decimal(it))}"}?:"待估算"
fun moneyText(value:BigDecimal?)=value?.let{amount(exact(it))}?:"待估算"
fun historicalMoney(currency:String,value:String)=if(currency=="CNY")amount(value)else value
/** Fit visible numbers to available width; never ellipsize or change their precision. */
@Composable fun NumericText(value:String,modifier:Modifier=Modifier,style:TextStyle=MaterialTheme.typography.titleMedium,color:Color=MaterialTheme.colorScheme.onSurface){
    val measurer=rememberTextMeasurer();val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val width=with(density){maxWidth.toPx()};val fontScale=density.fontScale
        val size=remember(value,width,fontScale,density.density,style){
            var size=style.fontSize.value
            while(size>14f && measurer.measure(AnnotatedString(value),style.copy(fontSize=size.sp,fontFeatureSettings="tnum"),softWrap=false).size.width>width)size-=1f
            size.coerceAtLeast(14f).sp
        }
        Text(value,modifier,color=color,style=style.copy(fontSize=size,lineHeight=(size.value*1.3f).sp,fontFeatureSettings="tnum"),softWrap=true)
    }
}
@Composable fun NetText(value:BigDecimal?,modifier:Modifier=Modifier,stateLabel:String?=null){Column(verticalArrangement=Arrangement.spacedBy(2.dp)){
    NumericText(moneyText(value),modifier,color=value?.let{pnlColor(it)}?:MaterialTheme.colorScheme.onSurfaceVariant)
    if(value!=null)Text(stateLabel?:when(value.signum()){1->"+ 盈利";-1->"亏损";else->"持平"},color=pnlColor(value),style=MaterialTheme.typography.labelSmall)
}}
@Composable fun LedgerPanel(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Card(modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Column(Modifier.padding(LedgerUi.inset),verticalArrangement=Arrangement.spacedBy(LedgerUi.gap)){Text(title,style=MaterialTheme.typography.titleMedium);content()}}}
@Composable fun EntryField(label:String,value:String,onValue:(String)->Unit,tag:String="",number:Boolean=false,modifier:Modifier=Modifier,lines:Int=1,keyboardType:KeyboardType?=null,trailing:(@Composable ()->Unit)?=null){val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current;OutlinedTextField(value,onValue,modifier=modifier.fillMaxWidth().testTag(tag),label={Text(label,style=MaterialTheme.typography.bodyMedium)},textStyle=MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings=if(number)"tnum"else null),shape=RoundedCornerShape(12.dp),trailingIcon=trailing,singleLine=lines==1,minLines=lines,keyboardOptions=KeyboardOptions(keyboardType=keyboardType?:(if(number)KeyboardType.Decimal else KeyboardType.Text),imeAction=if(lines==1)androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={keyboard?.hide()}))}
@Composable fun SegmentedChoices(value:String,choices:List<Pair<String,String>>,onSelect:(String)->Unit,tagPrefix:String=""){
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Row(Modifier.fillMaxWidth().selectableGroup()){
            choices.forEach{(id,label)->val selected=value==id
                Surface(color=if(selected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent,modifier=Modifier.weight(1f)){
                    Box(Modifier.heightIn(min=LedgerUi.touch).selectable(selected=selected,role=Role.RadioButton,onClick={onSelect(id)}).testTag("$tagPrefix$id").padding(horizontal=4.dp,vertical=12.dp),contentAlignment=Alignment.Center){Text(label,color=if(selected)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelLarge,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)}
                }
            }
        }
    }
}
@Composable fun StatusPill(label:String,color:Color=MaterialTheme.colorScheme.onSurfaceVariant,modifier:Modifier=Modifier){Surface(modifier,shape=RoundedCornerShape(8.dp),color=color.copy(alpha=0.10f)){Text(label,Modifier.padding(horizontal=8.dp,vertical=4.dp),color=color,style=MaterialTheme.typography.labelMedium)}}
@Composable fun PositionMetric(label:String,value:String,tag:String,modifier:Modifier=Modifier,large:Boolean=false){Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);NumericText(value,Modifier.testTag(tag),style=if(large)MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge.copy(fontSize=14.sp,fontWeight=FontWeight.Medium))}}
@Composable fun DateControl(day:Long,onDate:(Long)->Unit,label:String="日期",tag:String=""){val context=LocalContext.current;OutlinedButton(onClick={val d=LocalDate.ofEpochDay(day);DatePickerDialog(context,{_,y,m,n->onDate(LocalDate.of(y,m+1,n).toEpochDay())},d.year,d.monthValue-1,d.dayOfMonth).show()},modifier=Modifier.heightIn(min=48.dp).testTag(tag)){Text("$label ${LocalDate.ofEpochDay(day)}")}}
@Composable fun TimeControl(minute:Int,onTime:(Int)->Unit){val context=LocalContext.current;OutlinedButton(onClick={TimePickerDialog(context,{_,h,m->onTime(h*60+m)},minute/60,minute%60,true).show()},modifier=Modifier.heightIn(min=48.dp)){Text("%02d:%02d".format(minute/60,minute%60))}}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ChoiceRow(value:String,choices:List<Pair<String,String>>,onSelect:(String)->Unit,tagPrefix:String=""){FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){choices.forEach{(id,label)->FilterChip(selected=value==id,onClick={onSelect(id)},label={Text(label)},modifier=Modifier.heightIn(min=48.dp).testTag("$tagPrefix$id"))}}}
@Composable fun StrategySelect(state:LedgerSnapshot,id:String?,onSelect:(String?)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("execution-strategy")){Text("策略：${state.strategies.firstOrNull{it.id==id}?.name?:"不选择"}")};DropdownMenu(open,{open=false}){DropdownMenuItem(text={Text("不选择")},onClick={onSelect(null);open=false});state.strategies.filter{it.active || it.id==id}.sortedWith(compareBy<LedgerStrategy>{it.sortOrder}.thenBy{it.id}).forEach{s->DropdownMenuItem(text={Text(s.name+if(s.active)""else"（停用）")},onClick={onSelect(s.id);open=false})}}}}
@Composable fun EmptyLedger(text:String){Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(LedgerUi.radius),color=MaterialTheme.colorScheme.surfaceContainerLow,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Box(Modifier.heightIn(min=96.dp).padding(24.dp),contentAlignment=Alignment.Center){Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}}}
@Composable fun EditorBody(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}
fun actionLabel(e:LedgerExecution)=when(e.accountId){"stock"->if(e.side=="buy")"买入"else"卖出";else->(if(e.side=="buy")"买入"else"卖出")+when(e.effect){"open"->"开仓";"close_today"->"平今";"close_yesterday"->"平昨";else->"平仓"}}
fun directionLabel(c:LedgerCycle)=if(c.accountId=="stock")"股票"else if(c.direction=="long")"多头"else"空头"
fun strategyLabel(state:LedgerSnapshot,id:String?)=state.strategies.firstOrNull{it.id==id}?.name?:"未选策略"
