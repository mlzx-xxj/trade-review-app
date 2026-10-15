package com.tradereview.app.v200

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.math.BigDecimal

@Composable fun profitColor()=if(isSystemInDarkTheme())Color(0xffef9a9a)else Color(0xffb3262d)
@Composable fun lossColor()=if(isSystemInDarkTheme())Color(0xff81c784)else Color(0xff087c49)
@Composable fun pnlColor(value:BigDecimal)=when(value.signum()){1->profitColor();-1->lossColor();else->MaterialTheme.colorScheme.onSurfaceVariant}
fun amount(value:String?)=value?.let{"￥$it"}?:"未知"
@Composable fun NetText(value:BigDecimal,modifier:Modifier=Modifier){Text("￥${exact(value)}",modifier,color=pnlColor(value),style=MaterialTheme.typography.titleMedium)}
@Composable fun LedgerPanel(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Card(modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);content()}}}
@Composable fun EntryField(label:String,value:String,onValue:(String)->Unit,tag:String="",number:Boolean=false,modifier:Modifier=Modifier,lines:Int=1,keyboardType:KeyboardType?=null){val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current;OutlinedTextField(value,onValue,modifier=modifier.fillMaxWidth().testTag(tag),label={Text(label)},singleLine=lines==1,minLines=lines,keyboardOptions=KeyboardOptions(keyboardType=keyboardType?:(if(number)KeyboardType.Decimal else KeyboardType.Text),imeAction=if(lines==1)androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={keyboard?.hide()}))}
@Composable fun DateControl(day:Long,onDate:(Long)->Unit,label:String="日期",tag:String=""){val context=LocalContext.current;OutlinedButton(onClick={val d=LocalDate.ofEpochDay(day);DatePickerDialog(context,{_,y,m,n->onDate(LocalDate.of(y,m+1,n).toEpochDay())},d.year,d.monthValue-1,d.dayOfMonth).show()},modifier=Modifier.heightIn(min=48.dp).testTag(tag)){Text("$label ${LocalDate.ofEpochDay(day)}")}}
@Composable fun TimeControl(minute:Int,onTime:(Int)->Unit){val context=LocalContext.current;OutlinedButton(onClick={TimePickerDialog(context,{_,h,m->onTime(h*60+m)},minute/60,minute%60,true).show()},modifier=Modifier.heightIn(min=48.dp)){Text("%02d:%02d".format(minute/60,minute%60))}}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ChoiceRow(value:String,choices:List<Pair<String,String>>,onSelect:(String)->Unit,tagPrefix:String=""){FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){choices.forEach{(id,label)->FilterChip(selected=value==id,onClick={onSelect(id)},label={Text(label)},modifier=Modifier.heightIn(min=48.dp).testTag("$tagPrefix$id"))}}}
@Composable fun StrategySelect(state:LedgerSnapshot,id:String?,onSelect:(String?)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("execution-strategy")){Text("策略：${state.strategies.firstOrNull{it.id==id}?.name?:"不选择"}")};DropdownMenu(open,{open=false}){DropdownMenuItem(text={Text("不选择")},onClick={onSelect(null);open=false});state.strategies.filter{it.active || it.id==id}.sortedWith(compareBy<LedgerStrategy>{it.sortOrder}.thenBy{it.id}).forEach{s->DropdownMenuItem(text={Text(s.name+if(s.active)""else"（停用）")},onClick={onSelect(s.id);open=false})}}}}
@Composable fun EmptyLedger(text:String){Text(text,Modifier.padding(vertical=12.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable fun EditorBody(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}
fun actionLabel(e:LedgerExecution)=when(e.accountId){"stock"->if(e.side=="buy")"买入"else"卖出";else->(if(e.side=="buy")"买入"else"卖出")+when(e.effect){"open"->"开仓";"close_today"->"平今";"close_yesterday"->"平昨";else->"平仓"}}
fun directionLabel(c:LedgerCycle)=if(c.accountId=="stock")"股票"else if(c.direction=="long")"多头"else"空头"
fun strategyLabel(state:LedgerSnapshot,id:String?)=state.strategies.firstOrNull{it.id==id}?.name?:"未选策略"
