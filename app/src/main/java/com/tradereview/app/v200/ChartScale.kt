package com.tradereview.app.v200

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

data class ChartAxis(val minimum:BigDecimal,val maximum:BigDecimal,val step:BigDecimal,val ticks:List<BigDecimal>,val unitPower:Int) {
    val unitLabel get()=when{unitPower==8->"亿元";unitPower==4->"万元";unitPower<0->"×10^${unitPower}元";else->"元"}
    fun label(value:BigDecimal)=AutomaticEntry.cents(value.movePointLeft(unitPower))
}
object ChartScale {
    fun axis(values:List<BigDecimal>):ChartAxis {
        val low=(values.minOrNull()?:BigDecimal.ZERO).min(BigDecimal.ZERO);val high=(values.maxOrNull()?:BigDecimal.ZERO).max(BigDecimal.ZERO)
        val absolute=low.abs().max(high.abs())
        if(absolute.signum()==0)return ChartAxis(BigDecimal(-1),BigDecimal.ONE,BigDecimal.ONE,listOf(BigDecimal(-1),BigDecimal.ZERO,BigDecimal.ONE),0)
        val target=absolute.divide(BigDecimal(4)).stripTrailingZeros();val power=target.precision()-target.scale()-1;val normalized=target.movePointLeft(power)
        val mantissa=listOf(1,2,5,10).first{normalized<=BigDecimal(it)};val step=BigDecimal(mantissa).scaleByPowerOfTen(power)
        val min=low.divide(step,0,RoundingMode.FLOOR)*step;val max=high.divide(step,0,RoundingMode.CEILING)*step
        val ticks=mutableListOf<BigDecimal>();var value=min;while(value<=max){ticks+=value;value+=step;check(ticks.size<=12)}
        return ChartAxis(min,max,step,ticks,if(absolute>=BigDecimal("100000000"))8 else if(absolute>=BigDecimal(10000))4 else if(absolute<BigDecimal("0.01"))absolute.precision()-absolute.scale()-1 else 0)
    }
    /** Decimal arithmetic until the last pixel projection; Float never determines a financial result. */
    fun y(value:BigDecimal,axis:ChartAxis,height:Float):Float = ((axis.maximum-value).divide(axis.maximum-axis.minimum,24,RoundingMode.HALF_EVEN)).toFloat()*height
    fun x(value:BigDecimal,axis:ChartAxis,width:Float):Float = ((value-axis.minimum).divide(axis.maximum-axis.minimum,24,RoundingMode.HALF_EVEN)).toFloat()*width
    /** Canvas-local X already includes Compose's horizontal scroll transform. Right boundary excluded. */
    fun hitIndex(logicalX:Float,cellWidth:Float,count:Int):Int? {if(!logicalX.isFinite() || !cellWidth.isFinite() || cellWidth<=0 || logicalX<0 || count<=0)return null;val index=(logicalX/cellWidth).toInt();return index.takeIf{it in 0 until count}}
}
data class LedgerPeriod(val start:Long,val end:Long) {
    val days get()=(start..end).toList()
    companion object {
        fun week(date:LocalDate,offset:Int=0):LedgerPeriod{val start=date.minusDays((date.dayOfWeek.value-1).toLong()).plusWeeks(offset.toLong());return LedgerPeriod(start.toEpochDay(),start.plusDays(6).toEpochDay())}
        fun month(date:LocalDate,offset:Int=0):LedgerPeriod{val month=YearMonth.from(date).plusMonths(offset.toLong());return LedgerPeriod(month.atDay(1).toEpochDay(),month.atEndOfMonth().toEpochDay())}
    }
}
data class PeriodNumbers(val records:List<ClosedEvent>,val wins:Int,val losses:Int,val flats:Int,val net:BigDecimal?,val average:BigDecimal?,val winRate:BigDecimal?,val daily:Map<Long,BigDecimal>,val distribution:List<Pair<String,BigDecimal>>,val pending:Int=0)
fun periodNumbers(values:List<ClosedEvent>,period:LedgerPeriod,market:String="all"):PeriodNumbers {
    val selected=values.filter{it.execution.tradingDay in period.start..period.end && (market=="all" || it.execution.accountId==market)}
    val known=selected.filter{it.net!=null};val total=sum(known.map{requireNotNull(it.net)});val wins=known.count{requireNotNull(it.net).signum()>0}
    return PeriodNumbers(selected,wins,known.count{requireNotNull(it.net).signum()<0},known.count{requireNotNull(it.net).signum()==0},if(selected.isNotEmpty() && known.isEmpty())null else total,if(known.isEmpty())null else ratio(total,BigDecimal(known.size)),if(known.isEmpty())null else ratio(BigDecimal(wins)*BigDecimal(100),BigDecimal(known.size)),LedgerStatistics.days(known),known.groupBy{it.execution.accountId to it.execution.code}.map{(key,records)->("${records.first().execution.name} ${key.second}") to sum(records.map{requireNotNull(it.net)})}.sortedWith(compareByDescending<Pair<String,BigDecimal>>{it.second.abs()}.thenBy{it.first}),selected.size-known.size)
}
