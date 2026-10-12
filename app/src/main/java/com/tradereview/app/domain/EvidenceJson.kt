package com.tradereview.app.domain

import com.google.gson.*
import com.tradereview.app.data.*
import com.tradereview.app.evidence.EvidenceLimits

object EvidenceJson {
    private val gson=GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    fun json(value: Any): JsonElement = ProfessionalJson.sorted(gson.toJsonTree(value))
    private fun obj(e: JsonElement,keys: String): JsonObject {
        require(e.isJsonObject && e.asJsonObject.keySet()==keys.split(' ').toSet()) { "附件/目标字段缺失或含未知字段" };return e.asJsonObject
    }
    private fun JsonObject.text(k: String): String { val v=get(k);require(v is JsonPrimitive && v.isString);return v.asString }
    private fun JsonObject.nullable(k: String) = if(get(k).isJsonNull)null else text(k)
    private fun JsonObject.number(k: String): Long { val v=get(k);require(v is JsonPrimitive && v.isNumber && Regex("-?(0|[1-9][0-9]*)").matches(v.asString));return v.asString.toLong() }
    private fun JsonObject.small(k: String): Int = number(k).also { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) }.toInt()
    fun attachment(e: JsonElement): Attachment {
        val o=obj(e,"id recordKey kind caption sha256 byteCount width height createdAt sortOrder")
        return Attachment(o.text("id"),o.text("recordKey"),o.text("kind"),o.text("caption"),o.text("sha256"),o.number("byteCount"),o.small("width"),o.small("height"),o.number("createdAt"),o.small("sortOrder")).also(EvidenceLimits::validate)
    }
    fun settings(e: JsonElement): EvidenceSettings { val o=obj(e,"id maxInputMiB");return EvidenceSettings(o.small("id"),o.small("maxInputMiB")).also(EvidenceLimits::settings) }
    fun goal(e: JsonElement): ReviewGoal { val o=obj(e,"id title kind period startDate threshold mistakeId archivedDay")
        return ReviewGoal(o.text("id"),o.text("title"),o.text("kind"),o.text("period"),o.number("startDate"),o.nullable("threshold"),o.nullable("mistakeId"),if(o["archivedDay"].isJsonNull)null else o.number("archivedDay")) }
}
