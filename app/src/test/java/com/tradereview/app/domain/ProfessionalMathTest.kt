package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ProfessionalMathTest {
    private fun p() = ProfessionalTrade(pnlMode = "auto", quantityUnit = "futures_lot", entryPrice = "3500.1", exitPrice = "3520.35", quantity = "2", multiplier = "10", initialStop = "3490.1", fees = "3.00", otherCosts = "2.00", costsConfirmed = true, linearConfirmed = true)
    private fun equal(expected: String, actual: BigDecimal?) { assertNotNull(actual); assertEquals(0, BigDecimal(expected).compareTo(actual)) }
    @Test fun longAndShortUseExactPricesQuantityMultiplierAndCosts() {
        equal("405", ProfessionalMath.gross(p(), "long")); equal("-405", ProfessionalMath.gross(p(), "short"))
        assertEquals("400.00", ProfessionalMath.netForSave(p(), "long")); assertEquals("-410.00", ProfessionalMath.netForSave(p(), "short"))
        equal("200", ProfessionalMath.risk(p(), "long")); equal("2", ProfessionalMath.r(Trade(netPnl = "400.00", professional = p())))
    }
    @Test fun fractionalPricesLotsAndMultipliersDoNotUseBinaryFloatingPoint() {
        val value = p().copy(entryPrice = "0.1", exitPrice = "0.3", quantity = "2.5", multiplier = "1.25", fees = "0.10", otherCosts = "0.02")
        equal("0.625", ProfessionalMath.gross(value, "long")); equal("0.505", ProfessionalMath.net(value, "long"))
        assertEquals("0.51", ProfessionalMath.netForSave(value, "long"))
        assertEquals("-0.51", ProfessionalMath.netForSave(value.copy(entryPrice = "0.3", exitPrice = "0.1", quantity = "2.02", multiplier = "1.25", fees = "0.00", otherCosts = "0.00"), "long"))
    }
    @Test fun unknownFeesAndUnconfirmedConversionNeverBecomeZeroOrAutomaticResults() {
        assertNull(ProfessionalMath.net(p().copy(fees = null), "long")); assertNull(ProfessionalMath.net(p().copy(costsConfirmed = false), "long"))
        assertNull(ProfessionalMath.gross(p().copy(linearConfirmed = false), "long"))
        assertThrows(IllegalArgumentException::class.java) { ProfessionalMath.netForSave(p().copy(multiplier = null), "long") }
        val partial = ProfessionalMath.professional(ProfessionalDraft())
        assertNull(partial.entryPrice); assertNull(partial.fees); assertNull(partial.manualRisk)
        equal("402", ProfessionalMath.net(p().copy(otherCosts = null), "long"))
    }
    @Test fun plannedCostsAreAddedOnlyToPriceRiskAndManualRiskIsAnExplicitAlternative() {
        equal("207.5", ProfessionalMath.risk(p().copy(stopExecutionCost = "7.50"), "long"))
        equal("200", ProfessionalMath.risk(p().copy(riskMode = "amount", manualRisk = "200.00", stopExecutionCost = "7.50"), "long"))
        equal("-2", ProfessionalMath.r(Trade(netPnl = "-400.00", professional = p().copy(riskMode = "amount", manualRisk = "200.00"))))
        equal("0", ProfessionalMath.r(Trade(netPnl = "0.00", professional = p())))
    }
    @Test fun zeroMissingAndWrongSidedStopAreNotValidRSamples() {
        assertNull(ProfessionalMath.risk(p().copy(initialStop = "3500.1"), "long")); assertNull(ProfessionalMath.risk(p(), "short"))
        assertNull(ProfessionalMath.risk(p().copy(quantity = null), "long")); assertNull(ProfessionalMath.r(Trade()))
        assertNull(ProfessionalMath.risk(p().copy(riskMode = "amount", manualRisk = "0.00"), "long"))
        equal("200", ProfessionalMath.risk(p().copy(initialStop = "3510.1"), "short"))
    }
    @Test fun manualModeKeepsAuthoritativeAmountWhenProfessionalFieldsAreCompletedOrEdited() {
        val raw = ProfessionalDraft.from(p().copy(pnlMode = "manual"))
        val d = EntryDraft(amount = "12.34", professional = raw, market = "futures", symbol = "螺纹钢")
        assertEquals("12.34", FastEntry.trade(d).netPnl)
        assertEquals("12.34", FastEntry.trade(d.copy(professional = raw.copy(exitPrice = "9999"))).netPnl)
        assertEquals("400.00", FastEntry.trade(d.copy(professional = raw.copy(pnlMode = "auto"))).netPnl)
        assertEquals("12.34", d.amount)
    }
    @Test fun historyAndCopyKeepIdentityAndOnlyReusableProfessionalParameters() {
        val original = Trade(id = 42, market = "futures", netPnl = "400.00", professional = p(), reviewed = true)
        val edit = EntryDraft.from(original); assertEquals(original.professional, FastEntry.trade(edit).professional)
        assertEquals(original.recordKey, FastEntry.trade(edit).recordKey)
        val next = EntryDraft.copyReusable(original); assertEquals("", next.amount); assertFalse(next.reviewed); assertNotEquals(original.recordKey, next.recordKey)
        assertEquals("10", next.professional!!.multiplier); assertEquals("", next.professional!!.entryPrice); assertEquals("", next.professional!!.fees)
        assertEquals("manual", next.professional!!.pnlMode); assertFalse(next.professional!!.linearConfirmed)
    }
    @Test fun invalidDecimalsNegativeCostsAndZeroMultiplierAreRejected() {
        listOf(ProfessionalDraft(multiplier = "0"), ProfessionalDraft(fees = "-1"), ProfessionalDraft(quantity = "1E5"), ProfessionalDraft(entryPrice = "1.123456789"), ProfessionalDraft(plannedPositionPercent = "101"))
            .forEach { d -> assertThrows(IllegalArgumentException::class.java) { val v = ProfessionalMath.professional(d); ProfessionalMath.validate(v, "stock") } }
        assertThrows(IllegalArgumentException::class.java) { ProfessionalMath.validate(p().copy(multiplier = "0"), "futures") }
    }
    @Test fun historicalNegativeFuturesPricesAndSubcentRiskAreHandledExactly() {
        val v = p().copy(entryPrice = "-35", exitPrice = "-30", initialStop = "-36", fees = "0.00", otherCosts = null)
        equal("100", ProfessionalMath.gross(v, "long")); equal("20", ProfessionalMath.risk(v, "long"))
        val tiny = p().copy(entryPrice = "1", initialStop = "0.99999999", quantity = "0.00000001", multiplier = "0.00000001")
        equal("0.000000000000000000000001", ProfessionalMath.risk(tiny, "long"))
    }
    @Test fun templateDoesNotApplyWithoutExplicitConfirmationOrToAnotherSymbol() {
        val t = ContractTemplate(name = "用户参数", market = "futures", symbol = "螺纹钢", quantityUnit = "futures_lot", multiplier = "10")
        assertThrows(IllegalArgumentException::class.java) { ProfessionalMath.applyTemplate(ProfessionalDraft(), t, "futures", "螺纹钢", false) }
        assertThrows(IllegalArgumentException::class.java) { ProfessionalMath.applyTemplate(ProfessionalDraft(), t, "futures", "铁矿石", true) }
        val raw = ProfessionalDraft(entryPrice = "42", fees = "1", manualRisk = "200", pnlMode = "manual")
        val applied = ProfessionalMath.applyTemplate(raw, t, "futures", "螺纹钢", true)
        assertEquals("10", applied.multiplier); assertEquals("42", applied.entryPrice); assertEquals("1", applied.fees); assertEquals("200", applied.manualRisk)
        assertFalse(applied.linearConfirmed)
    }
    @Test fun tinyPositiveAndNegativeRDoNotUnderflowIntoFlatSamples() {
        val professional=ProfessionalTrade(riskMode="amount",manualRisk="999999999999.99")
        val positive=ProfessionalMath.r(Trade(netPnl="0.01",professional=professional))!!
        val negative=ProfessionalMath.r(Trade(netPnl="-0.01",professional=professional))!!
        assertTrue(positive.signum()>0);assertTrue(negative.signum()<0)
        assertEquals(0,positive.add(negative).compareTo(BigDecimal.ZERO))
    }
}
