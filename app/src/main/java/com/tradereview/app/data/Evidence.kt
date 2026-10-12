package com.tradereview.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "attachments", foreignKeys = [ForeignKey(entity = Trade::class,
    parentColumns = ["recordKey"], childColumns = ["recordKey"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordKey")])
data class Attachment(@PrimaryKey val id: String = newKey(), val recordKey: String,
    val kind: String = "analysis", val caption: String = "", val sha256: String,
    val byteCount: Long, val width: Int, val height: Int,
    val createdAt: Long = System.currentTimeMillis(), val sortOrder: Int = 0)

fun attachmentFileName(a: Attachment) = "${a.id}-${a.sha256}.jpg"

object EvidenceOptions {
    val kinds = listOf(Option("before", "入场前"), Option("holding", "持仓中"),
        Option("after", "出场后"), Option("analysis", "复盘分析"))
    val goals = listOf(Option("mistake_count", "减少某种错误"), Option("review_ratio", "提高交易复盘比例"),
        Option("plan_ratio", "提高计划内比例"), Option("risk_count", "控制风险违规次数"),
        Option("review_days", "提高盘后复盘天数"), Option("text", "文字改善目标"))
    val periods = listOf(Option("week", "每周"), Option("month", "每月"))
}

@Entity(tableName = "evidence_settings")
data class EvidenceSettings(@PrimaryKey val id: Int = 1, val maxInputMiB: Int = 10)

@Entity(tableName = "review_goals")
data class ReviewGoal(@PrimaryKey val id: String = newKey(), val title: String,
    val kind: String, val period: String = "week", val startDate: Long = LocalDate.now().toEpochDay(),
    val threshold: String? = null, val mistakeId: String? = null,
    val archivedDay: Long? = null)
