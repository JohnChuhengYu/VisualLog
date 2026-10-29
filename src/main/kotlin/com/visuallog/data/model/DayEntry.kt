package com.visuallog.data.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow

object DayEntries : IntIdTable() {
    val date = varchar("date", 20).uniqueIndex() // Format: "YYYY-MM-DD"
    val backgroundPath = varchar("background_path", 500).nullable()
    val drawingPaths = text("drawing_paths").default("[]") // JSON string of coordinates
    val journalText = text("journal_text").default("") // Rich text or markdown content
    val thumbnailPath = varchar("thumbnail_path", 500).nullable()
    val mood = varchar("mood", 50).nullable()
    val weather = varchar("weather", 50).nullable()
}

data class DayEntry(
    val id: Int = -1,
    val date: String,
    val backgroundPath: String? = null,
    val drawingPaths: String = "[]",
    val journalText: String = "",
    val thumbnailPath: String? = null,
    val mood: String? = null,
    val weather: String? = null,
    val stickers: List<Sticker> = emptyList()
)

fun ResultRow.toDayEntry(stickers: List<Sticker> = emptyList()): DayEntry {
    return DayEntry(
        id = this[DayEntries.id].value,
        date = this[DayEntries.date],
        backgroundPath = this[DayEntries.backgroundPath],
        drawingPaths = this[DayEntries.drawingPaths],
        journalText = this[DayEntries.journalText],
        thumbnailPath = this[DayEntries.thumbnailPath],
        mood = this[DayEntries.mood],
        weather = this[DayEntries.weather],
        stickers = stickers
    )
}
