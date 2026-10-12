package com.tradereview.app.domain

import com.tradereview.app.data.*
import com.tradereview.app.stability.baseline.LegacySmartInsights
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class SmartInsightsParityTest {
    private val today=LocalDate.of(2026,1,31).toEpochDay()
    private fun same(rows:List<Trade>,settings:SmartSettings=SmartSettings(minimumSamples=3),period:ReviewPeriod?=null){
        val tags=CatalogSeeds.tags.map{if(it.id=="chase")it.copy(name="自定义改名历史标签",hidden=true)else it}
        val args=emptyList<DailyReview>();val goals=emptyList<ReviewGoal>()
        assertEquals(LegacySmartInsights.calculate(rows,args,goals,settings,tags,today,period),SmartInsights.calculate(rows,args,goals,settings,tags,today,period))
    }
    @Test fun emptyLowSampleAndDisabledCategoriesMatchLegacyExactly(){
        same(emptyList());same(listOf(Trade(date=today,netPnl="-10.00")))
        same(List(20){Trade(id=it.toLong()+1,date=today-it%7,netPnl="-10.00")},SmartSettings(enabledCategories=emptyList(),minimumSamples=3))
    }
    @Test fun randomMultiCurrencyHiddenTagsValidRiskAndPeriodBoundariesMatchLegacy(){
        val random=Random(808);val rows=List(1500){i->Trade(id=i.toLong()+1,recordKey=i.toString(16).padStart(32,'0'),date=today-random.nextInt(50),currency=listOf("CNY","USD","USDT")[random.nextInt(3)],
            netPnl=listOf("-25.35","0.00","100.10")[random.nextInt(3)],mistakes=if(i%4==0)listOf("chase")else emptyList(),reviewed=i%2==0,
            professional=if(i%2==0)ProfessionalTrade(riskMode="amount",manualRisk="200.00",planStatus=if(i%4==0)"in"else "out")else null)}
        for(days in listOf(7,14,30))same(rows,SmartSettings(minimumSamples=3,lookbackDays=days))
        same(rows,period=ReviewPeriod.at("week",LocalDate.ofEpochDay(today)));same(rows,period=ReviewPeriod.at("month",LocalDate.ofEpochDay(today)))
    }
    @Test fun flatResetsFirstTiesAndSameDayInputOrderingArePreserved(){
        val values=listOf("-1.00","-2.00","-3.00","0.00","-4.00","-5.00","-6.00","10.00")
        val rows=values.mapIndexed{i,n->Trade(id=0,date=today-i%4,netPnl=n,recordKey=(i+1).toString(16).padStart(32,'0'))}
        same(rows);same(rows.reversed());same(rows+rows.map{it.copy(currency="USD",recordKey=newKey())})
    }
    @Test fun tenThousandLossesMatchAllOldEvidenceAndTradeKeysWithoutQuadraticCopies(){
        same(List(10000){Trade(id=it.toLong()+1,recordKey=(it+1).toString(16).padStart(32,'0'),date=today-it%7,netPnl="-10.01")})
    }
}
