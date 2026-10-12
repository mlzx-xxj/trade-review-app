package com.tradereview.app.domain

import com.google.gson.*
import com.tradereview.app.data.*
import com.tradereview.app.backup.BackupCodec
import java.time.LocalDate

object SmartJson {
    private val gson=GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    fun json(value:Any):JsonElement=ProfessionalJson.sorted(gson.toJsonTree(value))
    private fun obj(v:JsonElement,keys:String):JsonObject { require(v.isJsonObject && v.asJsonObject.keySet()==keys.split(' ').toSet());return v.asJsonObject }
    private fun JsonObject.s(k:String):String { val v=get(k);require(v is JsonPrimitive && v.isString);return v.asString }
    private fun JsonObject.n(k:String):Long { val v=get(k);require(v is JsonPrimitive && v.isNumber && Regex("-?(0|[1-9][0-9]*)").matches(v.asString));return v.asString.toLong() }
    private fun JsonObject.i(k:String)=n(k).also { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) }.toInt()
    private fun JsonObject.b(k:String):Boolean { val v=get(k);require(v is JsonPrimitive && v.isBoolean);return v.asBoolean }
    private fun JsonObject.list(k:String):List<String> { val v=get(k);require(v.isJsonArray);return v.asJsonArray.map { require(it is JsonPrimitive && it.isString);it.asString } }
    fun settings(v:JsonElement):SmartSettings { val o=obj(v,"id enabledCategories minimumSamples lookbackDays automaticReminders");return SmartSettings(o.i("id"),o.list("enabledCategories"),o.i("minimumSamples"),o.i("lookbackDays"),o.b("automaticReminders")) }
    fun item(v:JsonElement):PreflightItem { val o=obj(v,"id text archived");return PreflightItem(o.s("id"),o.s("text"),o.b("archived")) }
    fun check(v:JsonElement):PreflightCheck { val o=obj(v,"date itemKey label checked sourceType sourceRefs");return PreflightCheck(o.n("date"),o.s("itemKey"),o.s("label"),o.b("checked"),o.s("sourceType"),o.list("sourceRefs")) }
    fun draft(v:JsonElement):OcrDraft { val o=obj(v,"id fields blocked closedEvidence createdDay");return OcrDraft(o.s("id"),o.s("fields"),o.b("blocked"),o.b("closedEvidence"),o.n("createdDay")) }
    fun fields(values:Map<String,String>):String=gson.toJson(JsonObject().apply { OcrTradeParser.labels.keys.forEach { addProperty(it,values[it].orEmpty()) } })
    fun fields(text:String):Map<String,String> {
        require(text.length<=3000);val o=BackupCodec.strictObject(text);require(o.keySet()==OcrTradeParser.labels.keys)
        return o.keySet().associateWith { k -> o.s(k).also { require(it.length<=80 && '\u0000' !in it) } }.filterValues { it.isNotEmpty() }
    }
    private fun id(v:String)=require(Regex("[0-9a-f]{32}").matches(v))
    private fun text(v:String,max:Int)=require(v.isNotBlank() && v.length<=max && '\u0000' !in v)
    private fun day(v:Long)=require(v in LocalDate.of(1900,1,1).toEpochDay()..LocalDate.of(2100,12,31).toEpochDay())
    fun validate(s:SmartSettings) { require(s.id==1 && s.minimumSamples in 3..100 && s.lookbackDays in listOf(7,14,30));require(s.enabledCategories.distinct()==s.enabledCategories && s.enabledCategories.all { it in SmartOptions.categories.map { o->o.id } }) }
    fun validate(i:PreflightItem) { id(i.id);text(i.text,120) }
    fun validate(c:PreflightCheck) {
        day(c.date);text(c.label,220);require(c.sourceType in listOf("manual","auto"));require(Regex("(?:manual:[0-9a-f]{32}|auto:[A-Za-z0-9_:.-]{1,100})").matches(c.itemKey))
        require(c.sourceRefs.size<=100 && c.sourceRefs.distinct()==c.sourceRefs && c.sourceRefs.all { Regex("(?:trade:[0-9a-f]{32}|goal:[0-9a-f]{32}|review:-?[0-9]{1,6}|rule:(?:CNY|USD|USDT))").matches(it) })
    }
    fun validate(d:OcrDraft) { id(d.id);day(d.createdDay);fields(d.fields) }
}
