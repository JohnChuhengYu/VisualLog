package com.visuallog.engine.spatial

import androidx.compose.ui.geometry.Offset

object GeometryUtils {
    /**
     * Returns true if line segment (p1, p2) intersects with (p3, p4).
     */
    fun lineSegmentsIntersect(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Boolean {
        fun ccw(a: Offset, b: Offset, c: Offset): Boolean {
            return (c.y - a.y) * (b.x - a.x) > (b.y - a.y) * (c.x - a.x)
        }
        return ccw(p1, p3, p4) != ccw(p2, p3, p4) && ccw(p1, p2, p3) != ccw(p1, p2, p4)
    }
}

