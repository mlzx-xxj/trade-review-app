package com.tradereview.app.v200

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TradeDatabase::class.java)
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="nullable-fees-migration.db"
    @After fun cleanup(){context.deleteDatabase(name)}
    private fun SupportSQLiteDatabase.insert(table:String,value:Any){
        val fields=LedgerJson.element(value).asJsonObject
        val columns=fields.keySet().toList()
        val args=columns.map<String,Any?> { k -> val v=fields[k];when{v.isJsonNull->null;v.asJsonPrimitive.isBoolean->if(v.asBoolean)1 else 0;v.asJsonPrimitive.isNumber->v.asLong;else->v.asString} }.toTypedArray()
        execSQL("INSERT INTO `$table` (${columns.joinToString(","){"`$it`"}}) VALUES (${columns.joinToString(","){"?"}})",args)
    }
    @Test fun v8ToV9PreservesAllKnownFactsCachesReviewPlanAuditAndLegacyImage(){
        val a=LedgerExecution("a","stock","600000","迁移合成",20000,600,20000,1,"buy","open","10","100","0.1",strategyId="s",planId="p",second=1)
        val b=a.copy(id="b",date=20001,tradingDay=20001,ordinal=2,side="sell",effect="close",price="11",quantity="40",fees="0.264")
        val result=MatchingEngine.replay(listOf(a,b))
        helper.createDatabase(name,8).apply{
            LedgerSchema.seed(this)
            insert("ledger_strategies",LedgerStrategy("s","稳定策略"))
            insert("ledger_plans",LedgerPlan("p","stock","600000","迁移合成",20000,"s",note="计划不能成为成交"))
            insert("ledger_executions",a);insert("ledger_executions",b)
            result.cycles.forEach{insert("ledger_cycles",it)};result.lots.forEach{insert("ledger_lots",it)};result.matches.forEach{insert("ledger_matches",it)}
            insert("ledger_reviews",LedgerReview("r",result.cycles.single().id,20001,note="独立复盘"))
            insert("ledger_execution_audits",ExecutionAudit("audit","b","edit",LedgerJson.text(b.copy(price="10")),LedgerJson.text(b),200000))
            execSQL("INSERT INTO trades(id,recordKey,date,market,symbol,direction,currency,netPnl,positionPercent,strategy,emotion,mistakes,discipline,reviewed,note) VALUES(7,'0123456789abcdef0123456789abcdef',20000,'forex','旧美元','short','USD','-123.45',25,'trend','calm','','',0,'原值')")
            execSQL("INSERT INTO attachments VALUES('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','0123456789abcdef0123456789abcdef','before','旧截图引用','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',100,10,10,20000,0)")
            close()
        }
        helper.runMigrationsAndValidate(name,9,true,MIGRATION_8_9).apply{
            query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)}
            query("SELECT netPnl,recordKey FROM trades WHERE id=7").use{assertTrue(it.moveToFirst());assertEquals("-123.45",it.getString(0));assertEquals("0123456789abcdef0123456789abcdef",it.getString(1))}
            query("SELECT count(*) FROM attachments").use{it.moveToFirst();assertEquals(1,it.getInt(0))}
            close()
        }
        val db=Room.databaseBuilder(context,TradeDatabase::class.java,name).build()
        try{runBlocking{
            val s=LedgerRepository(db).read();assertEquals(listOf(a,b),s.executions.sortedBy{it.id});assertEquals(result.matches,s.matches);assertEquals(result.cycles,s.cycles);assertEquals(result.lots,s.lots)
            assertEquals("39.696",s.matches.single().net);assertEquals("独立复盘",s.reviews.single().note);assertEquals("p",s.executions.first().planId);assertEquals("audit",s.audits.single().id);LedgerValidation.snapshot(s)
            // Real DAO inserts verify null is representable after migration, not a fake zero.
            val day=AutomaticEntry.today().toEpochDay();val pending=LedgerExecution("pending","futures","i2701","铁矿石",day,600,day,0,"buy","open","800","2",null,feeSource=AutomaticEntry.SOURCE)
            val repo=LedgerRepository(db);repo.confirm(repo.preview(pending));assertNull(repo.read().executions.single{it.id=="pending"}.fees)
        }}finally{db.close()}
    }
    @Test fun entireV1ToV9ChainKeepsOriginalAmountAndStableIdentity(){
        helper.createDatabase(name,1).apply{execSQL("INSERT INTO trades VALUES(7,20000,'stock','原记录','long','CNY','99.24',25,'trend','calm','','',1)");close()}
        helper.runMigrationsAndValidate(name,9,true,MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9).apply{
            query("SELECT netPnl,recordKey FROM trades WHERE id=7").use{assertTrue(it.moveToFirst());assertEquals("99.24",it.getString(0));assertEquals(32,it.getString(1).length)}
            query("SELECT count(*) FROM ledger_executions").use{it.moveToFirst();assertEquals(0,it.getInt(0))}
            query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)};close()
        }
    }
}
