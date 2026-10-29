package com.visuallog.data.local

import com.visuallog.data.model.AppSettings
import com.visuallog.data.model.DayEntries
import com.visuallog.data.model.Stickers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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

