package com.tradereview.app.backup

import com.tradereview.app.data.Trade
import org.junit.Assert.*
import org.junit.Test

class TradeCsvTest {
    @Test fun quotesChineseCommasQuotesAndNewlinesWithCrLfRows() {
        assertEquals("\"中文,品种\",\"一\"\"二\",\"第一行\n第二行\"\r\n", TradeCsv.row(listOf("中文,品种", "一\"二", "第一行\n第二行")))
    }
    @Test fun exportsAllCurrenciesAndTagsWithBomAndExactAmounts() {
        val csv = TradeCsv.encode(listOf(Trade(id = 1, currency = "USD", netPnl = "-0.01", symbol = "中文,品种\n换行", reviewed = true,
            mistakes = listOf("chase", "no_stop"), discipline = listOf("respect_stop")), Trade(id = 2, currency = "USDT", netPnl = "12.34")))
        assertTrue(csv.startsWith("\uFEFF\"交易编号\""))
        assertTrue(csv.contains("\"中文,品种\n换行\""))
        assertTrue(csv.contains("\"USD\",\"-0.01\"")); assertTrue(csv.contains("\"USDT\",\"12.34\""))
        assertTrue(csv.contains("\"追涨杀跌、未设止损\"")); assertTrue(csv.contains("\"按计划止损\""))
        assertTrue(csv.endsWith("\r\n"))
    }
    @Test fun customSymbolsDoNotBecomeSpreadsheetFormulas() {
        assertTrue(TradeCsv.encode(listOf(Trade(symbol = "=1+1"))).contains("\"'=1+1\""))
        assertTrue(TradeCsv.encode(emptyList()).contains("\"稳定标识\""))
    }
}
