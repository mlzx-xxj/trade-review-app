package com.tradereview.app.v200

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ContractRulePresentationTest {
    private val rule=LedgerContractRule("rb","rb2701","螺纹钢","10","0.07","turnover_rate","0.0001","0.0001",null,effectiveDay=LocalDate.of(2026,10,15).toEpochDay(),verified=true)

    @Test fun searchAcceptsPrefixFullCodeNameAndCaseWithoutMatchingOtherContracts() {
        for(query in listOf("", " rb ", "RB2701", "螺纹"))assertTrue(contractRuleMatches(rule,query))
        for(query in listOf("rb2705", "铁矿", "i2701"))assertFalse(contractRuleMatches(rule,query))
    }

    @Test fun newRulePrefillsOnlyACompleteContractCode() {
        assertEquals("rb2701",contractCodePrefill(" RB2701 "))
        assertEquals("ta701",contractCodePrefill("ta701"))
        for(query in listOf("rb", "螺纹钢", "rb27010", "600000"))assertEquals("",contractCodePrefill(query))
    }

    @Test fun summaryShowsDatesVerificationAndRawRatesWithoutMoneyRounding() {
        val text=contractRuleSummary(rule).joinToString("\n")
        assertTrue(text.contains("2026-10-15 · 用户已核实"))
        assertTrue(text.contains("乘数 10 · 保证金率 7%"))
        assertTrue(text.contains("手续费类型：成交额费率"))
        assertTrue(text.contains("开仓：0.0001（成交额比例）"))
        assertTrue(text.contains("平昨/普通平仓：0.0001（成交额比例）"))
        assertTrue(text.contains("平今：未设置（待估算）"))
    }

    @Test fun missingValuesAreNeverDisplayedAsZero() {
        val text=contractRuleSummary(rule.copy(multiplier=null,marginRate=null,feeMode="unknown",openFee=null,closeFee=null,verified=false)).joinToString("\n")
        assertTrue(text.contains("未核实"));assertTrue(text.contains("乘数 未设置"))
        assertTrue(text.contains("保证金率 未设置（待估算）"))
        assertTrue(text.contains("手续费类型：未知（待估算）"))
        assertFalse(text.contains("：0"));assertFalse(text.contains("保证金率 0"))
    }

    @Test fun explicitZeroAndLegacyExtraRemainVisible() {
        val text=contractRuleSummary(rule.copy(marginRate="0",feeMode="per_lot",openFee="0",closeFee="2",addMode="per_execution",addValue="0.01")).joinToString("\n")
        assertTrue(text.contains("保证金率 0%"));assertTrue(text.contains("开仓：0 元/手"))
        assertTrue(text.contains("平昨/普通平仓：2 元/手"))
        assertTrue(text.contains("旧加收参数（兼容）：0.01 元/笔"))
    }
}
