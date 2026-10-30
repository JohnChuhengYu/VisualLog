package com.visuallog.data.repository

import com.visuallog.data.model.AppSettings
import com.visuallog.data.model.DayEntries
import com.visuallog.data.model.DayEntry
import com.visuallog.data.model.Sticker
import com.visuallog.data.model.Stickers
import com.visuallog.data.model.toDayEntry
import com.visuallog.data.model.toSticker
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.LocalDate

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

    fun getAllDayEntries(): List<DayEntry> {
        return transaction {
            DayEntries.selectAll().map { row ->
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
                getDayEntry(date)!!
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

    fun addSticker(
        dayEntryId: Int, 
        x: Float, 
        y: Float, 
        scale: Float, 
        rotation: Float, 
        contentPath: String, 
        type: String, 
        layer: Int = 1
    ): Sticker {
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

    /**
     * Atomically saves or updates a DayEntry and reconciles its stickers (deletes removed ones,
     * inserts new ones, updates moved ones) and cleans up orphaned files.
     */
    fun saveDayEntryWithStickers(updatedEntry: DayEntry, stickers: List<Sticker>): DayEntry {
        val savedEntry = saveOrUpdateDayEntry(
            date = updatedEntry.date,
            backgroundPath = updatedEntry.backgroundPath,
            drawingPaths = updatedEntry.drawingPaths,
            journalText = updatedEntry.journalText,
            thumbnailPath = updatedEntry.thumbnailPath,
            mood = updatedEntry.mood,
            weather = updatedEntry.weather
        )

        val existingStickers = getDayEntry(updatedEntry.date)?.stickers ?: emptyList()
        val currentIds = stickers.map { it.id }.toSet()

        // 1. Delete removed stickers
        existingStickers.forEach { s ->
            if (s.id !in currentIds) {
                deleteSticker(s.id)
            }
        }

        // 2. Insert or update stickers
        stickers.filter { it.contentPath != "LOADING" }.forEach { s ->
            if (s.id < 0) {
                addSticker(savedEntry.id, s.x, s.y, s.scale, s.rotation, s.contentPath, s.type, s.layer)
            } else {
                updateStickerPosition(s.id, s.x, s.y, s.scale, s.rotation, s.layer)
            }
        }

        return getDayEntry(updatedEntry.date) ?: savedEntry
    }

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
            val usedPaths = Stickers.slice(Stickers.contentPath)
                .selectAll()
                .withDistinct()
                .map { it[Stickers.contentPath] }
                .toSet()

            var deletedCount = 0
            allFiles.forEach { file ->
                if (file.isFile && !usedPaths.contains(file.absolutePath)) {
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

    fun getOrCreateInitDate(): LocalDate {
        val dateStr = getAppSetting("init_date")
        return if (dateStr != null) {
            LocalDate.parse(dateStr)
        } else {
            val today = LocalDate.now()
            setAppSetting("init_date", today.toString())
            today
        }
    }
}

