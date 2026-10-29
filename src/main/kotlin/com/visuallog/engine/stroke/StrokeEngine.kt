package com.visuallog.engine.stroke

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import java.util.concurrent.ConcurrentLinkedQueue

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
 * Handles the mathematics of smoothing raw points into curves using quadratic bezier interpolation.
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
        if (rawPoints.size < 3) return false
        
        currentPath.reset()
        if (rawPoints.isNotEmpty()) {
            currentPath.moveTo(rawPoints[0].x, rawPoints[0].y)
            
            for (i in 1 until rawPoints.size) {
                val p1 = rawPoints[i - 1]
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
    
    fun getFinalPath(): Path {
        return currentPath
    }
}

