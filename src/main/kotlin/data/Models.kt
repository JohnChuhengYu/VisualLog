package data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow

// Tables
object DayEntries : IntIdTable() {
    val date = varchar("date", 20).uniqueIndex() // Format: "YYYY-MM-DD"
    val backgroundPath = varchar("background_path", 500).nullable()
    val drawingPaths = text("drawing_paths").default("[]") // JSON string of coordinates
    val journalText = text("journal_text").default("") // Rich text or markdown content
    val thumbnailPath = varchar("thumbnail_path", 500).nullable()
    val mood = varchar("mood", 50).nullable()
    val weather = varchar("weather", 50).nullable()
}

object Stickers : IntIdTable() {
    val dayEntryId = reference("day_entry_id", DayEntries)
    val xPosition = float("x_position")
    val yPosition = float("y_position")
    val scale = float("scale").default(1.0f)
    val rotation = float("rotation").default(0.0f)
    val contentPath = varchar("content_path", 500).default("") // Path to image resource/file
    val type = varchar("type", 50) // e.g., "star", "heart" - keeping for backward compat or type id
    val layer = integer("layer").default(1) // 0=Bottom, 1=Middle, 2=Top
}

object AppSettings : IntIdTable() {
    val key = varchar("key", 100).uniqueIndex()
    val value = varchar("value", 500)
}

// Data Classes
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

data class Sticker(
    val id: Int = -1,
    val dayEntryId: Int = -1,
    val x: Float,           // Normalized X (0.0 - 1.0)
    val y: Float,           // Normalized Y (0.0 - 1.0)
    val scale: Float = 1.0f,
    val rotation: Float = 0.0f,
    val contentPath: String = "",
    val type: String,
    val layer: Int = 1
)

// Extension functions to map from ResultRow
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

fun ResultRow.toSticker(): Sticker {
    val rawX = this[Stickers.xPosition]
    val rawY = this[Stickers.yPosition]
    
    // Legacy Migration: if coordinates are > 1.1, assume they are 0-1000 and normalize
    val isLegacy = rawX > 1.1f || rawY > 1.1f
    val x = if (isLegacy) rawX / 1000f else rawX
    val y = if (isLegacy) rawY / 1000f else rawY
    
    return Sticker(
        id = this[Stickers.id].value,
        dayEntryId = this[Stickers.dayEntryId].value,
        x = x,
        y = y,
        scale = this[Stickers.scale],
        rotation = this[Stickers.rotation],
        contentPath = this[Stickers.contentPath],
        type = this[Stickers.type],
        layer = this[Stickers.layer]
    )
}
