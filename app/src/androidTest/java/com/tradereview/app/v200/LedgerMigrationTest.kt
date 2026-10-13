package com.tradereview.app.v200

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import com.tradereview.app.data.*

@RunWith(AndroidJUnit4::class)
class LedgerMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TradeDatabase::class.java)
    private val name="v200-migration.db"
    @After fun cleanup(){InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)}
    @Test fun v7ToV8PreservesUnknownMarketsAmountsKeysAttachmentsAndStrategyIdentity(){
        helper.createDatabase(name,7).apply{
            execSQL("INSERT INTO trades(id,recordKey,date,market,symbol,direction,currency,netPnl,positionPercent,strategy,emotion,mistakes,discipline,reviewed,note) VALUES(7,'0123456789abcdef0123456789abcdef',20000,'forex','旧美元记录','short','USD','-123.45',25,'custom_old','calm','','',0,'保留')")
            execSQL("INSERT INTO tags VALUES('strategy','custom_old','旧策略',1,1,0,0)")
            execSQL("INSERT INTO attachments VALUES('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','0123456789abcdef0123456789abcdef','before','合成图片引用','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',100,10,10,20000,0)")
            close()
        }
        helper.runMigrationsAndValidate(name,8,true,MIGRATION_7_8).apply{
            query("SELECT recordKey,currency,netPnl,note FROM trades WHERE id=7").use{assertTrue(it.moveToFirst());assertEquals("0123456789abcdef0123456789abcdef",it.getString(0));assertEquals("USD",it.getString(1));assertEquals("-123.45",it.getString(2));assertEquals("保留",it.getString(3))}
            query("SELECT count(*) FROM attachments").use{it.moveToFirst();assertEquals(1,it.getInt(0))}
            query("SELECT name FROM ledger_strategies WHERE id='custom_old'").use{assertTrue(it.moveToFirst());assertEquals("旧策略",it.getString(0))}
            query("SELECT count(*) FROM ledger_executions").use{it.moveToFirst();assertEquals(0,it.getInt(0))}
            query("SELECT initialCapital FROM ledger_accounts").use{assertEquals(2,it.count);while(it.moveToNext())assertTrue(it.isNull(0))}
            query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)};close()
        }
    }
    @Test fun entireV1ToV8ChainPreservesOriginalPnlAndDoesNotCreatePositions(){helper.createDatabase(name,1).apply{execSQL("INSERT INTO trades VALUES(7,20000,'stock','旧股票','long','CNY','99.24',25,'trend','calm','','',1)");close()};helper.runMigrationsAndValidate(name,8,true,MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8).apply{query("SELECT netPnl,recordKey FROM trades WHERE id=7").use{assertTrue(it.moveToFirst());assertEquals("99.24",it.getString(0));assertEquals(32,it.getString(1).length)};query("SELECT count(*) FROM ledger_cycles").use{it.moveToFirst();assertEquals(0,it.getInt(0))};close()}}
}
