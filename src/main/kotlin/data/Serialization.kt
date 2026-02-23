package data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

data class Stroke(
    val points: List<Offset>,
    val color: Int = 0xFF000000.toInt(), // ARGB
    val width: Float = 5f,
    val isEraser: Boolean = false,
    val layer: Int = 1 // 0=Bottom, 1=Middle, 2=Top
) {
    val bounds: Rect by lazy {
        if (points.isEmpty()) Rect.Zero else {
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            
            // Loop manually for performance (avoid iterators if possible, though forEach is inline)
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            Rect(minX, minY, maxX, maxY)
        }
    }
    
    val path: Path by lazy {
        Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, points.first().y)
                
                if (points.size < 3) {
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                } else {
                    // Use quadratic bezier smoothing
                    for (i in 1 until points.size) {
                        val p1 = points[i - 1]
                        val p2 = points[i]
                        
                        val midX = (p1.x + p2.x) / 2
                        val midY = (p1.y + p2.y) / 2
                        
                        if (i == 1) {
                            lineTo(midX, midY)
                        } else {
                            quadraticBezierTo(p1.x, p1.y, midX, midY)
                        }
                    }
                    // Connect the very last point
                    val last = points.last()
                    lineTo(last.x, last.y)
                }
            }
        }
    }
}

object SerializationUtils {
    
    // Format: x,y;x,y|color|width|isEraser|layer # NEXT_STROKE
    
    fun serializeStrokes(strokes: List<Stroke>): String {
        if (strokes.isEmpty()) return "[]"
        val sb = StringBuilder()
        strokes.forEachIndexed { index, stroke ->
            if (index > 0) sb.append("#")
            
            // Points
            val pointsStr = stroke.points.joinToString(";") { "${it.x},${it.y}" }
            sb.append(pointsStr)
            sb.append("|")
            sb.append(stroke.color)
            sb.append("|")
            sb.append(stroke.width)
            sb.append("|")
            sb.append(if (stroke.isEraser) "1" else "0")
            sb.append("|")
            sb.append(stroke.layer)
        }
        return sb.toString()
    }

    fun deserializeStrokes(data: String): List<Stroke> {
        if (data.isBlank() || data == "[]") return emptyList()
        val strokes = mutableListOf<Stroke>()
        try {
            val strokeParts = data.split("#")
            for (part in strokeParts) {
                if (part.isBlank()) continue
                val segments = part.split("|")
                if (segments.size >= 3) {
                    val pointsStr = segments[0]
                    val color = segments[1].toIntOrNull() ?: 0xFF000000.toInt()
                    val width = segments[2].toFloatOrNull() ?: 5f
                    val isEraser = if (segments.size >= 4) (segments[3] == "1") else false
                    val layer = if (segments.size >= 5) (segments[4].toIntOrNull() ?: 1) else 1
                    
                    var points = pointsStr.split(";").mapNotNull { p ->
                        val coords = p.split(",")
                        if (coords.size == 2) {
                            val x = coords[0].toFloatOrNull()
                            val y = coords[1].toFloatOrNull()
                            if (x != null && y != null) Offset(x, y) else null
                        } else null
                    }
                    
                    val finalWidth = width
                    
                    if (points.isNotEmpty()) {
                        strokes.add(Stroke(points, color, finalWidth, isEraser, layer))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
        return strokes
    }
    
    fun strokesToPaths(strokes: List<Stroke>): List<Path> {
        return strokes.map { stroke ->
            Path().apply {
                if (stroke.points.isNotEmpty()) {
                    moveTo(stroke.points.first().x, stroke.points.first().y)
                    
                    if (stroke.points.size < 3) {
                        for (i in 1 until stroke.points.size) {
                            lineTo(stroke.points[i].x, stroke.points[i].y)
                        }
                    } else {
                        // Use quadratic bezier smoothing (same as StrokeEngine)
                        for (i in 1 until stroke.points.size) {
                            val p1 = stroke.points[i - 1]
                            val p2 = stroke.points[i]
                            
                            val midX = (p1.x + p2.x) / 2
                            val midY = (p1.y + p2.y) / 2
                            
                            if (i == 1) {
                                lineTo(midX, midY)
                            } else {
                                quadraticBezierTo(p1.x, p1.y, midX, midY)
                            }
                        }
                        // Connect the very last point
                        val last = stroke.points.last()
                        lineTo(last.x, last.y)
                    }
                }
            }
        }
    }
}
