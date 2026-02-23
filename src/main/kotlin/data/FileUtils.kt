package data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object FileUtils {
    
    suspend fun saveBitmapToDisk(bitmap: ImageBitmap, directory: File, filename: String): String {
        return withContext(Dispatchers.IO) {
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, filename)
            
            // Convert to AWT Image (BufferedImage)
            val awtImage = bitmap.toAwtImage()
            
            // Generate Thumbnail (Optional: Resize if needed, here we save full resolution)
            // Ideally we resizing for thumbnails, but maintaining quality is key.
            // Let's at least ensure it's RGBA (PNG)
            
            ImageIO.write(awtImage, "png", file)
            
            file.absolutePath
        }
    }
}
