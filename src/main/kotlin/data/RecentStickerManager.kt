package data

import androidx.compose.runtime.mutableStateListOf
import java.io.File

object RecentStickerManager {
    val recentStickers = mutableStateListOf<String>()
    private val storageFile = File(System.getProperty("user.home"), ".visuallog/recent_stickers.txt")

    init {
        loadRecents()
    }

    fun add(path: String) {
        // Remove if exists to move to top
        recentStickers.remove(path)
        recentStickers.add(0, path)
        
        // Limit to 24 items
        if (recentStickers.size > 24) {
            recentStickers.removeRange(24, recentStickers.size)
        }
        
        saveRecents()
    }

    private fun loadRecents() {
        if (storageFile.exists()) {
            try {
                val lines = storageFile.readLines()
                recentStickers.clear()
                // Filter out lines that are not valid files or empty
                recentStickers.addAll(lines.filter { it.isNotBlank() && File(it).exists() })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveRecents() {
        try {
            if (!storageFile.parentFile.exists()) storageFile.parentFile.mkdirs()
            val content = recentStickers.joinToString("\n")
            storageFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
