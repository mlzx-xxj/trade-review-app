package com.tradereview.app.domain

import org.junit.Test
import org.junit.Assert.*
import com.tradereview.app.data.*
import java.time.LocalDate

class OcrTradeParserTest {
    private val text="""交易日期: 2024-02-29
市场: 国内期货
品种: 螺纹钢
合约: rb2701
方向: 做空
币种: CNY
入场价: 3500.25
出场价: 3490.125
手数: 2.5
手续费: 3.25
已实现净盈亏: -25.35"""
    @Test fun chineseEnglishDigitsSignedDecimalsAndContractsParseExactly() {
        val c=OcrTradeParser.parse(text).single();assertEquals("2024-02-29",c.fields["date"]);assertEquals("futures",c.fields["market"])
        assertEquals("short",c.fields["direction"]);assertEquals("rb2701",c.fields["contractCode"]);assertEquals("3490.125",c.fields["exitPrice"])
        assertEquals("-25.35",c.fields["realizedPnl"]);assertEquals("2.5",c.fields["quantity"]);assertTrue(c.closedEvidence);assertFalse(c.blocked)
        val e=OcrTradeParser.parse("Date: 2024/02/29\nMarket: futures\nSymbol: RB\nDirection: short\nCurrency: USD\nRealized net P&L: -1,234.50").single()
        assertEquals("-1234.50",e.fields["realizedPnl"]);assertEquals("USD",e.fields["currency"]);assertTrue(e.closedEvidence)
    }
    @Test fun everyRecognizedFieldAndExplicitClosureMustBeConfirmed() {
        val c=OcrTradeParser.parse(text).single();assertFalse(OcrTradeParser.canCreate(c,emptySet(),true));assertFalse(OcrTradeParser.canCreate(c,c.fields.keys,false))
        assertTrue(OcrTradeParser.canCreate(c,c.fields.keys,true));assertFalse(OcrTradeParser.canCreate(c,c.fields.keys-"fees",true))
    }
    @Test fun multipleExplicitTradesStaySeparateAndCanHaveDifferentCurrencies() {
        val list=OcrTradeParser.parse("交易 1\n$text\n交易 2\n${text.replace("CNY","USD").replace("-25.35","99.00")}")
        assertEquals(2,list.size);assertEquals("CNY",list[0].fields["currency"]);assertEquals("USD",list[1].fields["currency"])
        assertEquals(2,OcrTradeParser.parse(text+"\n"+text).size)
    }
    @Test fun ordersPositionsFloatingUnrealizedAndCandlesNeverAutoCreate() {
        for(marker in listOf("委托","持仓","浮动盈亏","未实现","挂单","Orders","Positions","Unrealized","K线","candlestick")) {
            val c=OcrTradeParser.parse(text+"\n"+marker).single();assertTrue(marker,c.blocked);assertFalse(marker,OcrTradeParser.canCreate(c,c.fields.keys,true))
        }
        val candle=OcrTradeParser.parse("K线图 MA20 3500.25").single();assertTrue(candle.fields.isEmpty());assertFalse(candle.closedEvidence)
    }
    @Test fun incompleteOrUnlabelledPnlIsNotGuessed() {
        val c=OcrTradeParser.parse("3500.00 -200.00 买入 卖出 盈亏").single();assertTrue(c.fields.isEmpty());assertFalse(OcrTradeParser.canCreate(c,c.fields.keys,true))
        val missing=OcrTradeParser.parse(text.replace("交易日期: 2024-02-29\n","")).single();assertFalse(OcrTradeParser.canCreate(missing,missing.fields.keys,true))
    }
    @Test fun conflictsAndInvalidFinancialFormatsRemainPending() {
        val c=OcrTradeParser.parse(text+"\n已实现净盈亏: 30.00").single();assertTrue("realizedPnl" in c.conflicts);assertNull(c.fields["realizedPnl"])
        for(v in listOf("1e3","NaN","12,34.56","0.001","−a","100 盈亏"))assertNull(v,OcrTradeParser.normalize("realizedPnl",v))
        assertNull(OcrTradeParser.normalize("quantity","0"));assertNull(OcrTradeParser.normalize("fees","-2"));assertNull(OcrTradeParser.normalize("date","2023-02-29"))
        val future=OcrTradeParser.parse(text.replace("2024-02-29",LocalDate.now().plusDays(1).toString())).single();assertFalse(OcrTradeParser.canCreate(future,future.fields.keys,true))
    }
    @Test fun buySellPricesNeverGuessShortEntryAndExit() {
        val c=OcrTradeParser.parse("买入价: 3.25\n卖出价: 4.50\n方向: 做空").single();assertEquals("3.25",c.fields["buyPrice"]);assertNull(c.fields["entryPrice"]);assertNull(c.fields["exitPrice"])
    }
    @Test fun zeroPnlRequiresExplicitFlatAndCostsDoNotOverwriteManualNet() {
        val c=OcrTradeParser.parse(text.replace("-25.35","0.00")).single();val d=OcrTradeParser.draft(c,"trend",10)
        assertEquals("flat",d.outcome);assertEquals("",d.amount);assertEquals("0.00",FastEntry.trade(d).netPnl)
        val loss=FastEntry.trade(OcrTradeParser.draft(OcrTradeParser.parse(text).single(),"trend",10));assertEquals("-25.35",loss.netPnl);assertEquals("manual",loss.professional!!.pnlMode)
        assertEquals("3.25",loss.professional.fees);assertNull(loss.professional.multiplier);assertNull(ProfessionalMath.r(loss))
    }
    @Test fun suspectedDuplicatesCompareExactFactsAndNeverMerge() {
        val c=OcrTradeParser.parse(text).single();val t=FastEntry.trade(OcrTradeParser.draft(c,"trend",10)).copy(id=1)
        assertEquals(listOf(t),OcrTradeParser.duplicates(c,listOf(t,t.copy(id=2,currency="USD"),t.copy(id=3,netPnl="-25.36"))))
    }
    @Test fun fullWidthCharactersAndOversizedInputsAreHandledSafely() {
        assertEquals("-12.50",OcrTradeParser.parse("已实现盈亏：－１２．５０").single().fields["realizedPnl"])
        try { OcrTradeParser.parse("a".repeat(20001));fail() }catch(_:IllegalArgumentException){}
        try { OcrTradeParser.parse((1..21).joinToString("\n"){"交易 $it\n$text"});fail() }catch(_:IllegalArgumentException){}
    }
}
