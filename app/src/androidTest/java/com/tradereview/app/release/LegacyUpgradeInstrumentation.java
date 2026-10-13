package com.tradereview.app.release;

import android.app.Instrumentation;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.os.Bundle;
import java.time.LocalDate;

/** Explicit custom instrumentation, not a skipped JUnit test. Same fixed certificate required.
 * Only seeds two synthetic records in an actually installed old APK on a disposable emulator.
 * No project model class is linked: the old application's class loader may predate V1.0.
 */
public final class LegacyUpgradeInstrumentation extends Instrumentation {
    private Bundle arguments;
    private static final String KEY="00000000000000000000000000001001";
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            if(!("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE)))throw new IllegalStateException("Disposable emulator only");
            String phase=arguments.getString("phase");
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("trade-review.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                int version=db.getVersion();
                if("prepare".equals(phase)){
                    if(version<3||version>7)throw new IllegalStateException("Supported old Room schema 3..7 required");
                    db.beginTransaction();
                    try{
                        ContentValues t=new ContentValues();t.put("recordKey",KEY);t.put("date",LocalDate.now().toEpochDay());t.put("market","stock");t.put("symbol","V1.0合成升级样本");t.put("direction","long");t.put("currency","CNY");t.put("netPnl","456.78");t.put("positionPercent",10);t.put("strategy","trend");t.put("emotion","calm");t.put("mistakes","[]");t.put("discipline","[]");t.put("reviewed",1);t.put("note","synthetic same-certificate installation upgrade only");
                        db.insertOrThrow("trades",null,t);
                        ContentValues r=new ContentValues();r.put("date",LocalDate.now().toEpochDay());r.put("outlook","wait");r.put("strategies","[]");r.put("preparations","[]");r.put("maxPosition",20);r.put("planSaved",1);r.put("emotion","calm");r.put("discipline","[]");r.put("mistakes","[]");r.put("lessons","[]");r.put("rating",4);r.put("reviewSaved",1);db.insertOrThrow("daily_reviews",null,r);
                        db.setTransactionSuccessful();
                    }finally{db.endTransaction();}
                }else if("verify".equals(phase)){
                    int expected=Integer.parseInt(arguments.getString("target_schema","7"));if((expected!=7 && expected!=8)||version!=expected)throw new IllegalStateException("Expected target Room schema required");
                    try(Cursor t=db.rawQuery("SELECT netPnl,note FROM trades WHERE recordKey=?",new String[]{KEY})){
                        if(!t.moveToFirst()||!"456.78".equals(t.getString(0))||!"synthetic same-certificate installation upgrade only".equals(t.getString(1))||t.moveToNext())throw new IllegalStateException("Stable trade facts not preserved");
                    }
                    try(Cursor r=db.rawQuery("SELECT planSaved,reviewSaved,rating FROM daily_reviews WHERE date=?",new String[]{Long.toString(LocalDate.now().toEpochDay())})){
                        if(!r.moveToFirst()||r.getInt(0)!=1||r.getInt(1)!=1||r.getInt(2)!=4)throw new IllegalStateException("Diary facts not preserved");
                    }
                    try(Cursor fk=db.rawQuery("PRAGMA foreign_key_check",null)){if(fk.moveToFirst())throw new IllegalStateException("Foreign key failure");}
                }else throw new IllegalStateException("Explicit prepare/verify phase required");
                result.putString("schema",Integer.toString(version));
            }
            result.putString("fixturePhase",phase);result.putString("stream","V100_UPGRADE_FIXTURE_OK="+phase+"\n");finish(-1,result);
        }catch(Exception failure){result.putString("stream","V100_UPGRADE_FIXTURE_FAILED="+failure.getClass().getSimpleName()+"\n");finish(0,result);}
    }
}
