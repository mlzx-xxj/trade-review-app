package com.tradereview.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TradeDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-v1-v2.db"
    @After fun clean() { context.deleteDatabase(name) }

    @Test fun officialV1MigrationPreservesEveryFieldAndStableKeysAcrossReopen() = runBlocking {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO trades VALUES(7,20000,'stock','沪深300','short','CNY','-123.45',25,'trend','anxious','chase|oversize','respect_stop',1)")
            execSQL("INSERT INTO trades VALUES(8,20000,'stock','AAPL','long','USD','1.00',10,'range','calm','','',0)")
            execSQL("INSERT INTO daily_reviews VALUES(20000,'bullish','trend|breakout','risk',25,1,'anxious','respect_stop','chase','stop|rest',4,1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).close()
        fun open() = Room.databaseBuilder(context, TradeDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, com.tradereview.app.v200.MIGRATION_7_8).build()
        var db = open()
        try {
            val trade = requireNotNull(db.trades().findTrade(7))
            assertEquals(Trade(id = 7, date = 20000, direction = "short", netPnl = "-123.45", positionPercent = 25, emotion = "anxious",
                mistakes = listOf("chase", "oversize"), discipline = listOf("respect_stop"), reviewed = true, recordKey = trade.recordKey), trade)
            assertTrue(Regex("[0-9a-f]{32}").matches(trade.recordKey))
            assertNotEquals(trade.recordKey, db.trades().findTrade(8)!!.recordKey)
            assertEquals(DailyReview(date = 20000, outlook = "bullish", strategies = listOf("trend", "breakout"), preparations = listOf("risk"),
                maxPosition = 25, planSaved = true, emotion = "anxious", discipline = listOf("respect_stop"), mistakes = listOf("chase"),
                lessons = listOf("stop", "rest"), rating = 4, reviewSaved = true), db.trades().findReview(20000))
            db.trades().save(trade.copy(netPnl = "1.23"))
            db.close(); db = open()
            assertEquals(trade.recordKey, db.trades().findTrade(7)!!.recordKey)
            assertEquals("1.23", db.trades().findTrade(7)!!.netPnl)
            assertEquals(2, db.trades().allTrades().size)
        } finally { db.close() }
    }
    @Test fun emptyV1DatabaseMigratesAndAcceptsNewRecords() = runBlocking {
        helper.createDatabase(name, 1).close()
        helper.runMigrationsAndValidate(name, 7, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).close()
        val db = Room.databaseBuilder(context, TradeDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, com.tradereview.app.v200.MIGRATION_7_8).build()
        try {
            val record = Trade()
            val id = db.trades().insert(record)
            assertEquals(record.copy(id = id), db.trades().findTrade(id))
        } finally { db.close() }
    }
    @Test fun officialV2ToV3PreservesRecordsSeedsAllStableTagsAndKeepsCustomSymbols() = runBlocking {
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO trades VALUES(7,20000,'futures','老品种文字','short','CNY','-123.45',25,'trend','anxious','chase|oversize','respect_stop',1,'0123456789abcdef0123456789abcdef')")
            execSQL("INSERT INTO daily_reviews VALUES(20000,'bullish','trend','risk',25,1,'anxious','respect_stop','chase','stop',4,1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).close()
        val db = Room.databaseBuilder(context, TradeDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, com.tradereview.app.v200.MIGRATION_7_8).build()
        try {
            val trade = requireNotNull(db.trades().findTrade(7))
            assertEquals("老品种文字", trade.symbol); assertEquals("", trade.note)
            assertEquals("0123456789abcdef0123456789abcdef", trade.recordKey)
            assertEquals(listOf("chase", "oversize"), trade.mistakes)
            assertTrue(db.trades().findReview(20000)!!.reviewSaved)
            assertEquals(CatalogSeeds.tags.map { it.kind to it.id }.toSet(), db.trades().allTags().map { it.kind to it.id }.toSet())
            assertTrue(db.trades().allInstruments().any { it.name == "老品种文字" })
            assertEquals(16, db.trades().allInstruments().count { it.market == "futures" })
            assertEquals(AppSettings(), db.trades().settings()); assertTrue(db.trades().allDrafts().isEmpty())
        } finally { db.close() }
    }

    @Test fun v3ToV4PreservesEveryTableAndAddsNonDestructiveDefaults() = runBlocking {
        helper.createDatabase(name, 3).apply {
            CatalogSeeds.insert(this)
            execSQL("UPDATE app_settings SET theme='dark', lastMarket='futures'")
            execSQL("UPDATE tags SET name='改名的历史策略', hidden=1 WHERE kind='strategy' AND id='trend'")
            execSQL("UPDATE instruments SET favorite=1, sortOrder=42, lastUsed=123 WHERE id='preset_futures_0'")
            execSQL("INSERT INTO trades VALUES(7,20000,'stock','沪深300','short','CNY','-123.45',25,'trend','anxious','chase|oversize','respect_stop',1,'旧备注','0123456789abcdef0123456789abcdef')")
            execSQL("INSERT INTO daily_reviews VALUES(20000,'bullish','trend','risk',25,1,'anxious','respect_stop','chase','stop',4,1)")
            execSQL("INSERT INTO entry_drafts VALUES('new','1123456789abcdef0123456789abcdef',0,20000,'stock','沪深300','long','CNY','12.','gain',10,'trend','calm','','',0,'草稿',0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4).close()
        val db = Room.databaseBuilder(context, TradeDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, com.tradereview.app.v200.MIGRATION_7_8).build()
        try {
            assertEquals("旧备注", db.trades().findTrade(7)!!.note); assertEquals("-123.45", db.trades().findTrade(7)!!.netPnl)
            assertEquals("0123456789abcdef0123456789abcdef", db.trades().findTrade(7)!!.recordKey)
            assertEquals(4, db.trades().findReview(20000)!!.rating)
            assertTrue(db.trades().allTags().first { it.id == "trend" && it.kind == "strategy" }.hidden)
            assertEquals(42, db.trades().allInstruments().first { it.id == "preset_futures_0" }.sortOrder)
            assertEquals("12.", db.trades().allDrafts().single().amount)
            assertEquals(AppSettings(lastMarket = "futures", theme = "dark"), db.trades().settings()); assertTrue(db.trades().allFilters().isEmpty())
            val filter = SavedFilter(name = "新筛选", criteria = com.tradereview.app.domain.TradeFilter(strategies = listOf("trend")))
            db.trades().saveFilter(filter); assertEquals(filter, db.trades().allFilters().single())
            db.trades().saveSettings(db.trades().settings()!!.copy(pnlPalette = "red_profit"))
            db.close()
            val reopened = Room.databaseBuilder(context, TradeDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, com.tradereview.app.v200.MIGRATION_7_8).build()
            try { assertEquals(filter, reopened.trades().allFilters().single()); assertEquals("red_profit", reopened.trades().settings()!!.pnlPalette) }
            finally { reopened.close() }
        } finally { db.close() }
    }
    @Test fun officialV4ToV5PreservesEveryOldTableAndNullableProfessionalDefaults() = runBlocking {
        helper.createDatabase(name, 4).apply {
            CatalogSeeds.insert(this)
            execSQL("INSERT INTO trades VALUES(7,20000,'futures','螺纹钢','short','CNY','-123.45',25,'trend','anxious','chase','respect_stop',1,'旧备注','0123456789abcdef0123456789abcdef')")
            execSQL("INSERT INTO daily_reviews VALUES(20000,'bullish','trend','risk',25,1,'anxious','respect_stop','chase','stop',4,1)")
            execSQL("INSERT INTO entry_drafts VALUES('new','1123456789abcdef0123456789abcdef',0,20000,'stock','沪深300','long','CNY','12.','gain',10,'trend','calm','','',0,'草稿',0)")
            execSQL("UPDATE app_settings SET pnlPalette='red_profit', theme='dark'")
            execSQL("UPDATE tags SET hidden=1 WHERE kind='strategy' AND id='trend'")
            execSQL("UPDATE instruments SET favorite=1, sortOrder=42 WHERE id='preset_futures_0'")
            val criteria=com.tradereview.app.domain.FilterCodec.encode(com.tradereview.app.domain.TradeFilter(strategies=listOf("trend")))
            execSQL("INSERT INTO saved_filters VALUES(?,?,?,?)",arrayOf("ab".repeat(16),"旧筛选","CNY",criteria))
            close()
        }
        helper.runMigrationsAndValidate(name,5,true,MIGRATION_4_5).close()
        fun openCurrent()=Room.databaseBuilder(context,TradeDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, com.tradereview.app.v200.MIGRATION_7_8).build()
        var db=openCurrent()
        try {
            val old=db.trades().findTrade(7)!!
            assertEquals("-123.45",old.netPnl);assertEquals("0123456789abcdef0123456789abcdef",old.recordKey)
            assertNull(old.professional);assertNull(old.riskRecord);assertEquals("12.",db.trades().allDrafts().single().amount);assertNull(db.trades().allDrafts().single().professional)
            assertEquals(4,db.trades().findReview(20000)!!.rating);assertEquals("red_profit",db.trades().settings()!!.pnlPalette)
            assertTrue(db.trades().allTags().first { it.kind=="strategy" && it.id=="trend" }.hidden);assertEquals(42,db.trades().allInstruments().first { it.id=="preset_futures_0" }.sortOrder)
            assertEquals("旧筛选",db.trades().allFilters().single().name);assertTrue(db.trades().allRiskRules().isEmpty());assertTrue(db.trades().allTemplates().isEmpty())
            val rules=RiskRules(accountEquity="10000.00",maxRiskPercent="1")
            val template=ContractTemplate(name="用户核实参数",market="futures",symbol="螺纹钢",quantityUnit="futures_lot",multiplier="10",userVerified=true)
            db.trades().saveRiskRules(rules);db.trades().saveTemplate(template)
            db.trades().save(old.copy(professional=ProfessionalTrade(riskMode="amount",manualRisk="200.00")))
            db.close();db=openCurrent()
            assertEquals(rules,db.trades().allRiskRules().single());assertEquals(template,db.trades().allTemplates().single());assertEquals("200.00",db.trades().findTrade(7)!!.professional!!.manualRisk)
            assertEquals("-123.45",db.trades().findTrade(7)!!.netPnl);assertEquals(old.recordKey,db.trades().findTrade(7)!!.recordKey)
        } finally { db.close() }
    }

    @Test fun officialV5ToV6KeepsProfessionalRiskAndOldNineTablesAndAddsEvidenceTargets() = runBlocking {
        helper.createDatabase(name,5).apply {
            CatalogSeeds.insert(this)
            val professional=ProfessionalConverters().encodeProfessional(ProfessionalTrade(riskMode="amount",manualRisk="200.00"))
            execSQL("INSERT INTO trades VALUES(7,20000,'futures','螺纹钢','short','CNY','-123.45',25,'trend','anxious','chase','respect_stop',1,'旧备注','0123456789abcdef0123456789abcdef',?,NULL)",arrayOf(professional))
            execSQL("INSERT INTO daily_reviews VALUES(20000,'bullish','trend','risk',25,1,'anxious','respect_stop','chase','stop',4,1)")
            execSQL("INSERT INTO risk_rules VALUES('CNY','10000.00','1','20',3,'500.00')")
            execSQL("INSERT INTO contract_templates VALUES('ab${"cd".repeat(15)}','旧参数','futures','螺纹钢','futures_lot','10',NULL,'已核实',1)")
            close()
        }
        helper.runMigrationsAndValidate(name,6,true,MIGRATION_5_6).close()
        val db=Room.databaseBuilder(context,TradeDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6, MIGRATION_6_7, com.tradereview.app.v200.MIGRATION_7_8).build()
        try {
            val old=db.trades().findTrade(7)!!;assertEquals("-123.45",old.netPnl);assertEquals("0123456789abcdef0123456789abcdef",old.recordKey);assertEquals("200.00",old.professional!!.manualRisk)
            assertEquals(4,db.trades().findReview(20000)!!.rating);assertEquals("10000.00",db.trades().allRiskRules().single().accountEquity);assertEquals("旧参数",db.trades().allTemplates().single().name)
            assertTrue(db.trades().allAttachments().isEmpty());assertTrue(db.trades().allGoals().isEmpty());assertNull(db.trades().evidenceSettings())
            val goal=ReviewGoal(title="保留历史",kind="text",startDate=com.tradereview.app.domain.ReviewPeriod.at("week",java.time.LocalDate.now()).start)
            db.trades().saveGoal(goal);db.trades().saveEvidenceSettings(EvidenceSettings(maxInputMiB=5));assertEquals(goal,db.trades().allGoals().single());assertEquals(5,db.trades().evidenceSettings()!!.maxInputMiB)
        } finally { db.close() }
    }
    @Test fun officialV6ToV7PreservesEvidenceGoalsAndAllOldFactsAndCreatesOnlyNewTables() = runBlocking {
        val key="0123456789abcdef0123456789abcdef"
        helper.createDatabase(name,6).apply {
            CatalogSeeds.insert(this)
            val professional=ProfessionalConverters().encodeProfessional(ProfessionalTrade(riskMode="amount",manualRisk="200.00"))
            execSQL("INSERT INTO trades VALUES(7,20000,'futures','螺纹钢','short','CNY','-123.45',25,'trend','anxious','chase','respect_stop',1,'旧备注',?, ?,NULL)",arrayOf(key,professional))
            execSQL("INSERT INTO daily_reviews VALUES(20000,'bullish','trend','risk',25,1,'anxious','respect_stop','chase','stop',4,1)")
            execSQL("INSERT INTO attachments VALUES(?,?,'analysis','旧证据',?,100,10,10,1000,0)",arrayOf("ab".repeat(16),key,"0".repeat(64)))
            execSQL("INSERT INTO review_goals VALUES(?,'旧文字目标','text','week',19997,NULL,NULL,NULL)",arrayOf("cd".repeat(16)))
            execSQL("INSERT INTO evidence_settings VALUES(1,5)")
            close()
        }
        helper.runMigrationsAndValidate(name,7,true,MIGRATION_6_7).close()
        val db=Room.databaseBuilder(context,TradeDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7, com.tradereview.app.v200.MIGRATION_7_8).build()
        try {
            val t=db.trades().findTrade(7)!!;assertEquals(key,t.recordKey);assertEquals("-123.45",t.netPnl);assertEquals("200.00",t.professional!!.manualRisk)
            assertEquals("旧证据",db.trades().allAttachments().single().caption);assertEquals("旧文字目标",db.trades().allGoals().single().title)
            assertEquals(5,db.trades().evidenceSettings()!!.maxInputMiB);assertTrue(db.trades().findReview(20000)!!.reviewSaved)
            assertNull(db.trades().smartSettings());assertTrue(db.trades().allPreflightChecks().isEmpty());assertTrue(db.trades().allOcrDrafts().isEmpty())
            db.trades().saveSmartSettings(SmartSettings());assertEquals(SmartSettings(),db.trades().smartSettings())
        }finally {db.close()}
    }

}
