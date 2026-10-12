package com.tradereview.app.domain

import com.tradereview.app.data.*
import com.tradereview.app.stability.baseline.LegacyAdvancedAnalytics
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class AdvancedParityTest {
    private val today=LocalDate.of(2026,1,31)
    @Test fun exactFinancialReportsIncludingDrawdownCurvesGroupOrderingAndFiltersMatchOriginal(){
        val random=Random(805);val rows=List(2000){i->Trade(id=(i+1).toLong(),recordKey=(i+1).toString(16).padStart(32,'0'),date=today.toEpochDay()-random.nextInt(400),
            currency=listOf("CNY","USD","USDT")[i%3],market=if(i%2==0)"futures"else "stock",symbol=if(i%2==0)"螺纹钢"else "沪深300",
            netPnl=listOf("0.00","-25.35","100.10")[random.nextInt(3)],strategy=if(i%2==0)"trend"else "breakout",mistakes=if(i%4==0)listOf("chase","oversize")else emptyList(),
            discipline=if(i%3==0)listOf("follow_plan")else emptyList(),reviewed=i%2==0,professional=if(i%2==0)ProfessionalTrade(riskMode="amount",manualRisk="200.00")else null)}
        val logs=List(50){DailyReview(date=today.toEpochDay()-it,rating=it%5+1,reviewSaved=true)}
        for(currency in listOf("CNY","USD","USDT"))for(filter in listOf(TradeFilter(),TradeFilter(period="month"),TradeFilter(directions=listOf("short")),TradeFilter(mistakes=listOf("chase")))) {
            assertEquals(LegacyAdvancedAnalytics.calculate(rows,logs,currency,filter,today),AdvancedAnalytics.calculate(rows,logs,currency,filter,today).copy(source=null))
        }
    }
    @Test fun emptyFlatNegativePrecisionAndSameDateKeyTieOrderingMatchOriginal(){
        for(rows in listOf(emptyList(),listOf(Trade(date=today.toEpochDay(),netPnl="0.00")),List(200){Trade(id=0,date=today.toEpochDay()-it%7,recordKey=(it+1).toString(16).padStart(32,'0'),netPnl=if(it%2==0)"0.01"else "-0.02")})) {
            assertEquals(LegacyAdvancedAnalytics.performance(rows),AdvancedAnalytics.performance(rows))
            assertEquals(LegacyAdvancedAnalytics.calculate(rows,emptyList(),"CNY",TradeFilter(),today),AdvancedAnalytics.calculate(rows,emptyList(),"CNY",TradeFilter(),today).copy(source=null))
        }
    }
}
