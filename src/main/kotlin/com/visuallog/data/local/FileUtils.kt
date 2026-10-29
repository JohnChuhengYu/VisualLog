package com.visuallog.data.local

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO

object FileUtils {
    
    suspend fun saveBitmapToDisk(bitmap: ImageBitmap, directory: File, filename: String): String {
        return withContext(Dispatchers.IO) {
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, filename)
            val awtImage = bitmap.toAwtImage()
            ImageIO.write(awtImage, "png", file)
            file.absolutePath
        }
    }
}

