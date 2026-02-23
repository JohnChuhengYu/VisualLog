package drawing

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream

object ImageOptimizationUtils {
    private const val MAX_DIMENSION = 1600 // Increased slightly for better quality on high-res screens
    private const val JPEG_QUALITY = 0.85f 

    fun optimizeImage(sourceFile: File, destDir: File): File? {
        try {
            if (!sourceFile.exists()) return null

            // 1. DECODE BOUNDS ONLY (Avoid OOM)
            val input = ImageIO.createImageInputStream(sourceFile) ?: return null
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) {
                input.close()
                return null
            }
            val reader = readers.next()
            reader.input = input
            
            val originalWidth = reader.getWidth(0)
            val originalHeight = reader.getHeight(0)
            
            // 2. CALCULATE SUBSAMPLING (Like Android inSampleSize)
            // Read rough downsampled version first to save memory
            var subsampling = 1
            var targetW = originalWidth
            var targetH = originalHeight
            
            if (originalWidth > MAX_DIMENSION || originalHeight > MAX_DIMENSION) {
                while (targetW / 2 > MAX_DIMENSION || targetH / 2 > MAX_DIMENSION) {
                    targetW /= 2
                    targetH /= 2
                    subsampling *= 2
                }
            }
            
            val readParam = reader.defaultReadParam
            if (subsampling > 1) {
                readParam.setSourceSubsampling(subsampling, subsampling, 0, 0)
            }
            
            // 3. DECODE PIXELS (Memory Efficient)
            val roughImage: BufferedImage = try {
                reader.read(0, readParam)
            } finally {
                reader.dispose()
                input.close()
            }

            // 4. PRECISE SCALING (High Quality)
            // If subsampling got us close, we might still need a final resize to match exact MAX_DIMENSION
            val finalWidth: Int
            val finalHeight: Int
            if (roughImage.width > MAX_DIMENSION || roughImage.height > MAX_DIMENSION) {
                val ratio = roughImage.width.toFloat() / roughImage.height.toFloat()
                if (ratio > 1) {
                    finalWidth = MAX_DIMENSION
                    finalHeight = (MAX_DIMENSION / ratio).toInt()
                } else {
                    finalHeight = MAX_DIMENSION
                    finalWidth = (MAX_DIMENSION * ratio).toInt()
                }
            } else {
                finalWidth = roughImage.width
                finalHeight = roughImage.height
            }

            // Create compatible image type (Faster rendering)
            // Check for transparency to decide Output Format
            val hasAlpha = roughImage.colorModel.hasAlpha()
            val imageType = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
            
            val resizedImage = BufferedImage(finalWidth, finalHeight, imageType)
            val g = resizedImage.createGraphics()
            
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            g.drawImage(roughImage, 0, 0, finalWidth, finalHeight, null)
            g.dispose()

            // 5. SMART COMPRESSION (JPEG vs PNG)
            // PNG is incredibly slow for photos. JPEG is instant.
            if (!destDir.exists()) destDir.mkdirs()
            
            val format = if (hasAlpha) "png" else "jpg"
            val destFile = File(destDir, "sticker_${System.currentTimeMillis()}.$format")
            
            if (format == "jpg") {
                // Save as Optimized JPEG
                val writers = ImageIO.getImageWritersByFormatName("jpg")
                if (!writers.hasNext()) {
                     // Fallback to PNG if no JPEG writer
                     ImageIO.write(resizedImage, "png", File(destDir, "sticker_${System.currentTimeMillis()}.png"))
                     return destFile
                }
                val writer = writers.next()
                val ios = FileImageOutputStream(destFile)
                writer.output = ios
                
                val writeParam = writer.defaultWriteParam
                writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
                writeParam.compressionQuality = JPEG_QUALITY
                
                writer.write(null, IIOImage(resizedImage, null, null), writeParam)
                writer.dispose()
                ios.close()
            } else {
                // Save as PNG (Only if strictly necessary)
                ImageIO.write(resizedImage, "png", destFile)
            }

            return destFile

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    fun loadDownsampledBitmap(file: File, maxDim: Int = 1024): BufferedImage? {
        try {
            if (!file.exists()) return null

            val input = ImageIO.createImageInputStream(file) ?: return null
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) {
                input.close()
                return null
            }
            val reader = readers.next()
            reader.input = input
            
            val originalWidth = reader.getWidth(0)
            val originalHeight = reader.getHeight(0)
            
            var subsampling = 1
            var targetW = originalWidth
            var targetH = originalHeight
            
            while (targetW / 2 >= maxDim || targetH / 2 >= maxDim) {
                targetW /= 2
                targetH /= 2
                subsampling *= 2
            }
            
            val readParam = reader.defaultReadParam
            if (subsampling > 1) {
                readParam.setSourceSubsampling(subsampling, subsampling, 0, 0)
            }
            
            val roughImage: BufferedImage = try {
                reader.read(0, readParam)
            } finally {
                reader.dispose()
                input.close()
            }
            
            return roughImage
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
