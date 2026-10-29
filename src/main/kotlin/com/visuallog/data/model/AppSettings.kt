package com.visuallog.data.model

import org.jetbrains.exposed.dao.id.IntIdTable

object AppSettings : IntIdTable() {
    val key = varchar("key", 100).uniqueIndex()
    val value = varchar("value", 500)
}

