package com.tradereview.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object SmartOptions {
    val categories=listOf(Option("discipline","交易纪律"),Option("risk","风险"),Option("strategy","策略"),Option("emotion","情绪"),Option("habits","执行习惯"))
}
@Entity(tableName="smart_settings")
data class SmartSettings(@PrimaryKey val id:Int=1,val enabledCategories:List<String> = SmartOptions.categories.map { it.id },
    val minimumSamples:Int=10,val lookbackDays:Int=14,val automaticReminders:Boolean=true)
@Entity(tableName="preflight_items")
data class PreflightItem(@PrimaryKey val id:String=newKey(),val text:String,val archived:Boolean=false)
/** Snapshot of what the user actually checked. Recomputed reminders never rewrite this history. */
@Entity(tableName="preflight_checks",primaryKeys=["date","itemKey"])
data class PreflightCheck(val date:Long,val itemKey:String,val label:String,val checked:Boolean,
    val sourceType:String,val sourceRefs:List<String> = emptyList())
/** Only editable fields persist, never the raw OCR text or temporary source URI. */
@Entity(tableName="ocr_drafts")
data class OcrDraft(@PrimaryKey val id:String=newKey(),val fields:String,val blocked:Boolean,val closedEvidence:Boolean,val createdDay:Long)
