
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

// Mock the Utils class (copy paste for standalone test)
object ImageOptimizationUtils {
    private const val MAX_DIMENSION = 1500

    fun optimizeImage(sourceFile: File, destDir: File): File? {
        println("DEBUG: Optimizing image: ${sourceFile.absolutePath}")
        try {
            val originalImage = ImageIO.read(sourceFile)
            if (originalImage == null) {
                println("DEBUG: ImageIO failed to read file")
                return null
            }
            
            val width = originalImage.width
            val height = originalImage.height
            println("DEBUG: Original size: ${width}x$height")
            
            var newWidth = width
            var newHeight = height
            
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                val ratio = width.toFloat() / height.toFloat()
                if (width > height) {
                    newWidth = MAX_DIMENSION
                    newHeight = (MAX_DIMENSION / ratio).toInt()
                } else {
                    newHeight = MAX_DIMENSION
                    newWidth = (MAX_DIMENSION * ratio).toInt()
                }
            }
            
            val resizedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
            val g = resizedImage.createGraphics()
            g.drawImage(originalImage, 0, 0, newWidth, newHeight, null)
            g.dispose()
            
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, "test_result_${System.currentTimeMillis()}.png")
            ImageIO.write(resizedImage, "png", destFile)
            
            println("DEBUG: Saved optimized image to: ${destFile.absolutePath}")
            return destFile
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

// MAIN
val testDir = File("test_images")
testDir.mkdirs()

// 1. Create Dummy Image
val dummyFile = File(testDir, "large_source.png")
val dummyImg = BufferedImage(3000, 2000, BufferedImage.TYPE_INT_ARGB)
val g = dummyImg.createGraphics()
g.color = java.awt.Color.RED
g.fillRect(0, 0, 3000, 2000)
g.dispose()
ImageIO.write(dummyImg, "png", dummyFile)
println("Created dummy file: ${dummyFile.absolutePath}")

// 2. Run Optimization
val outputDir = File(testDir, "optimized")
val result = ImageOptimizationUtils.optimizeImage(dummyFile, outputDir)

if (result != null && result.exists()) {
    println("SUCCESS: Optimized file created at ${result.absolutePath}")
} else {
    println("FAILURE: Optimization returned null")
}
