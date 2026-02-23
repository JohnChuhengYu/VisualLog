package data

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object DatabaseFactory {
    fun init() {
        val databaseDir = File(System.getProperty("user.home"), ".visuallog")
        if (!databaseDir.exists()) {
            databaseDir.mkdirs()
        }
        val dbPath = File(databaseDir, "visuallog.db").absolutePath
        
        Database.connect("jdbc:sqlite:$dbPath", "org.sqlite.JDBC")

        transaction {
            SchemaUtils.createMissingTablesAndColumns(DayEntries, Stickers, AppSettings)
        }
    }
}

class VisualLogRepository {
    
    fun getDayEntry(date: String): DayEntry? {
        return transaction {
            val entryRow = DayEntries.select { DayEntries.date eq date }.singleOrNull()
            entryRow?.let { row ->
                val id = row[DayEntries.id].value
                val stickers = Stickers.select { Stickers.dayEntryId eq id }
                    .map { it.toSticker() }
                row.toDayEntry(stickers)
            }
        }
    }

    fun saveOrUpdateDayEntry(
        date: String, 
        backgroundPath: String?, 
        drawingPaths: String = "[]", 
        journalText: String = "", 
        thumbnailPath: String? = null,
        mood: String? = null,
        weather: String? = null
    ): DayEntry {
        return transaction {
            val existing = DayEntries.select { DayEntries.date eq date }.singleOrNull()
            
            if (existing != null) {
                DayEntries.update({ DayEntries.date eq date }) {
                    it[DayEntries.backgroundPath] = backgroundPath
                    it[DayEntries.drawingPaths] = drawingPaths
                    it[DayEntries.journalText] = journalText
                    it[DayEntries.thumbnailPath] = thumbnailPath
                    it[DayEntries.mood] = mood
                    it[DayEntries.weather] = weather
                }
                getDayEntry(date)!! // Reload to send back full object
            } else {
                val id = DayEntries.insertAndGetId {
                    it[DayEntries.date] = date
                    it[DayEntries.backgroundPath] = backgroundPath
                    it[DayEntries.drawingPaths] = drawingPaths
                    it[DayEntries.journalText] = journalText
                    it[DayEntries.thumbnailPath] = thumbnailPath
                    it[DayEntries.mood] = mood
                    it[DayEntries.weather] = weather
                }
                DayEntry(
                    id = id.value, 
                    date = date, 
                    backgroundPath = backgroundPath, 
                    drawingPaths = drawingPaths, 
                    journalText = journalText, 
                    thumbnailPath = thumbnailPath,
                    mood = mood,
                    weather = weather
                )
            }
        }
    }

    fun addSticker(dayEntryId: Int, x: Float, y: Float, scale: Float, rotation: Float, contentPath: String, type: String, layer: Int = 1): Sticker {
        return transaction {
            val id = Stickers.insertAndGetId {
                it[Stickers.dayEntryId] = dayEntryId
                it[Stickers.xPosition] = x
                it[Stickers.yPosition] = y
                it[Stickers.scale] = scale
                it[Stickers.rotation] = rotation
                it[Stickers.contentPath] = contentPath
                it[Stickers.type] = type
                it[Stickers.layer] = layer
            }
            Sticker(id.value, dayEntryId, x, y, scale, rotation, contentPath, type, layer)
        }
    }

    fun updateStickerPosition(stickerId: Int, x: Float, y: Float, scale: Float, rotation: Float, layer: Int) {
        transaction {
            Stickers.update({ Stickers.id eq stickerId }) {
                it[Stickers.xPosition] = x
                it[Stickers.yPosition] = y
                it[Stickers.scale] = scale
                it[Stickers.rotation] = rotation
                it[Stickers.layer] = layer
            }
        }
    }

    fun deleteSticker(stickerId: Int) {
        transaction {
            Stickers.deleteWhere { Stickers.id eq stickerId }
        }
    }

    // App Settings Methods
    fun getAppSetting(key: String): String? {
        return transaction {
            AppSettings.select { AppSettings.key eq key }
                .singleOrNull()
                ?.get(AppSettings.value)
        }
    }

    fun setAppSetting(key: String, value: String) {
        transaction {
            val existing = AppSettings.select { AppSettings.key eq key }.singleOrNull()
            if (existing != null) {
                AppSettings.update({ AppSettings.key eq key }) {
                    it[AppSettings.value] = value
                }
            } else {
                AppSettings.insert {
                    it[AppSettings.key] = key
                    it[AppSettings.value] = value
                }
            }
        }
    }

    fun cleanupOrphanedStickers(): Int {
        val stickersDir = File(System.getProperty("user.home"), ".visuallog/stickers")
        if (!stickersDir.exists()) return 0

        val allFiles = stickersDir.listFiles() ?: return 0
        
        return transaction {
            // Get all used paths (Absolute paths stored in DB)
            val usedPaths = Stickers.slice(Stickers.contentPath)
                .selectAll()
                .withDistinct()
                .map { it[Stickers.contentPath] }
                .toSet()

            var deletedCount = 0
            allFiles.forEach { file ->
                if (file.isFile && !usedPaths.contains(file.absolutePath)) {
                     // Only delete if it looks like one of our stickers (optional safety, but strict path check is good enough)
                     try {
                         if (file.delete()) {
                             deletedCount++
                         }
                     } catch (e: Exception) {
                         e.printStackTrace()
                     }
                }
            }
            deletedCount
        }
    }

    fun getOrCreateInitDate(): java.time.LocalDate {
        val dateStr = getAppSetting("init_date")
        return if (dateStr != null) {
            java.time.LocalDate.parse(dateStr)
        } else {
            val today = java.time.LocalDate.now()
            setAppSetting("init_date", today.toString())
            today
        }
    }
}
