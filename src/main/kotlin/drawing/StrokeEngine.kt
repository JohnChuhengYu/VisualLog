package drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Represents a raw input point from the OS.
 */
data class RawPoint(
    val x: Float,
    val y: Float,
    val time: Long
)

/**
 * Hardware-agnostic input manager that decouples the UI thread from the Render thread.
 * Stores events in a lock-free queue.
 */
class InkInputManager {
    private val eventQueue = ConcurrentLinkedQueue<RawPoint>()

    fun onPointerEvent(changes: List<PointerInputChange>) {
        changes.forEach { change ->
            // In a real high-perf app, we would loop through historical data if available
            // standard Compose PointerInputChange might not expose raw history easily without deeper access
            // but we take the latest for now.
            eventQueue.offer(
                RawPoint(
                    x = change.position.x,
                    y = change.position.y,
                    time = change.uptimeMillis
                )
            )
        }
    }

    fun onRawPoint(point: RawPoint) {
        eventQueue.offer(point)
    }

    fun pollEvents(): List<RawPoint> {
        val list = ArrayList<RawPoint>()
        while (!eventQueue.isEmpty()) {
            eventQueue.poll()?.let { list.add(it) }
        }
        return list
    }

    fun clear() {
        eventQueue.clear()
    }
}

/**
 * Handles the mathematics of smoothing raw points into curves.
 */
class StrokeEngine {
    val rawPoints = ArrayList<RawPoint>()
    
    // The current visual path being built
    var currentPath: Path = Path()
        private set
        
    // Stroke settings
    var strokeWidth: Float = 5f
    
    // Dynamic style based on current settings
    val currentStyle: Stroke
        get() = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

    fun startStroke(startPoint: RawPoint) {
        rawPoints.clear()
        rawPoints.add(startPoint)
        currentPath = Path()
        currentPath.moveTo(startPoint.x, startPoint.y)
    }

    /**
     * Processes new raw points and updates the path.
     * Returns true if the path was updated.
     */
    fun process(newPoints: List<RawPoint>): Boolean {
        if (newPoints.isEmpty()) return false
        
        rawPoints.addAll(newPoints)
        
        // Basic smoothing: Catmull-Rom Spline or QuadTo
        // For efficiency in this demo, we'll use a standard QuadTo approach which is very fast and smooth enough
        // Ideally we rewrite the whole path or append. Appending is O(1).
        
        if (rawPoints.size < 3) return false

        // We process from the last known stable point
        // A simple trick for smooth drawing:
        // Midpoint between P1 and P2 is the control point for the quad curve.
        
        val pPrev = rawPoints[rawPoints.size - 2]
        val pCurr = rawPoints[rawPoints.size - 1]
        
        // val midX = (pPrev.x + pCurr.x) / 2
        // val midY = (pPrev.y + pCurr.y) / 2
        
        // This is a simplified incremental smoothing. 
        // For "Procreate" quality, you'd want to recalculate the last few segments with a spline.
        // But QuadTo to midpoint is the standard fast smoothing.
        
        if (rawPoints.size > 2) {
             val pLast = rawPoints[rawPoints.size - 2]
             // We can't easily "undo" the last lineTo in standard Path without resetting.
             // For a truly incremental engine, we just append curvature.
             // Strategy: Line to the geometric center? 
             // Better Strategy: Quadratic Bezier from Prev to Midpoint of (Prev, Curr) using Prev as anchor?
             // Actually, standard approach:
             // Path.quadTo(prev.x, prev.y, (prev.x + curr.x)/2, (prev.y + curr.y)/2)
        }
        
        // Rebuilding path for simple "perfect" smoothing (expensive on simple loop, but okay for N < 1000)
        // For optimization, we only append. 
        
        currentPath.reset()
        if (rawPoints.isNotEmpty()) {
            currentPath.moveTo(rawPoints[0].x, rawPoints[0].y)
            
            for (i in 1 until rawPoints.size) {
                // To make it smoother, we can use the midpoint technique
                // But for now, let's stick to simple lineTo for the raw engine structure validation
                // then upgrade to Quad.
                
                val p1 = rawPoints[i-1]
                val p2 = rawPoints[i]
                
                // Midpoint smoothing
                val cx = (p1.x + p2.x) / 2
                val cy = (p1.y + p2.y) / 2
                
                if (i == 1) {
                    currentPath.lineTo(cx, cy)
                } else {
                    currentPath.quadraticBezierTo(p1.x, p1.y, cx, cy)
                }
            }
            // Connect the very last point
            val last = rawPoints.last()
            currentPath.lineTo(last.x, last.y)
        }
        
        return true
    }
    
    fun clear() {
        rawPoints.clear()
        currentPath.reset()
    }
    
    // For "Baking": get the final high-quality path (could hold more data)
    fun getFinalPath(): Path {
        return currentPath
    }
}
