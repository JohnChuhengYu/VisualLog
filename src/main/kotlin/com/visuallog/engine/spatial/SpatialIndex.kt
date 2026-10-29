package com.visuallog.engine.spatial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.visuallog.data.model.Stroke

interface SpatialIndex {
    fun insert(stroke: Stroke)
    fun remove(stroke: Stroke)
    fun query(range: Rect): List<Stroke>
    fun hitTest(point: Offset, threshold: Float): List<Stroke>
    fun clear()
    fun getAll(): List<Stroke>
}

class QuadTree(
    private val boundary: Rect,
    private val capacity: Int = 4,
    private val maxDepth: Int = 10
) : SpatialIndex {
    
    private val strokes = ArrayList<Stroke>()
    private var children: Array<QuadTree>? = null
    private var depth: Int = 0
    
    private constructor(boundary: Rect, capacity: Int, depth: Int, maxDepth: Int) : this(boundary, capacity, maxDepth) {
        this.depth = depth
    }

    override fun insert(stroke: Stroke) {
        if (!boundary.overlaps(stroke.bounds)) {
            return
        }

        if (children != null) {
            val index = getIndex(stroke.bounds)
            if (index != -1) {
                children!![index].insert(stroke)
                return
            }
        }

        strokes.add(stroke)

        if (strokes.size > capacity && children == null && depth < maxDepth) {
            subdivide()
            val iterator = strokes.iterator()
            while (iterator.hasNext()) {
                val s = iterator.next()
                val index = getIndex(s.bounds)
                if (index != -1) {
                    children!![index].insert(s)
                    iterator.remove()
                }
            }
        }
    }

    override fun remove(stroke: Stroke) {
        if (strokes.remove(stroke)) return

        if (children != null) {
            val index = getIndex(stroke.bounds)
            if (index != -1) {
                children!![index].remove(stroke)
                return
            }
        }
    }
    
    override fun query(range: Rect): List<Stroke> {
        val found = ArrayList<Stroke>()
        if (!boundary.overlaps(range)) return found

        for (s in strokes) {
            if (range.overlaps(s.bounds)) {
                found.add(s)
            }
        }

        if (children != null) {
            for (child in children!!) {
                if (child.boundary.overlaps(range)) {
                    found.addAll(child.query(range))
                }
            }
        }
        return found
    }

    override fun hitTest(point: Offset, threshold: Float): List<Stroke> {
        val queryBox = Rect(
            point.x - threshold,
            point.y - threshold,
            point.x + threshold,
            point.y + threshold
        )
        return query(queryBox)
    }

    override fun clear() {
        strokes.clear()
        children = null
    }

    override fun getAll(): List<Stroke> {
        val all = ArrayList<Stroke>(strokes)
        if (children != null) {
            for (child in children!!) {
                all.addAll(child.getAll())
            }
        }
        return all
    }

    private fun subdivide() {
        val x = boundary.left
        val y = boundary.top
        val w = boundary.width / 2f
        val h = boundary.height / 2f

        children = arrayOf(
            QuadTree(Rect(x + w, y, x + 2 * w, y + h), capacity, depth + 1, maxDepth),     // Top-Right (0)
            QuadTree(Rect(x, y, x + w, y + h), capacity, depth + 1, maxDepth),             // Top-Left (1)
            QuadTree(Rect(x, y + h, x + w, y + 2 * h), capacity, depth + 1, maxDepth),     // Bottom-Left (2)
            QuadTree(Rect(x + w, y + h, x + 2 * w, y + 2 * h), capacity, depth + 1, maxDepth) // Bottom-Right (3)
        )
    }

    private fun getIndex(bounds: Rect): Int {
        val midX = boundary.left + (boundary.width / 2f)
        val midY = boundary.top + (boundary.height / 2f)

        val topQuadrant = bounds.bottom < midY
        val bottomQuadrant = bounds.top > midY

        if (bounds.right < midX) {
            if (topQuadrant) return 1    // Top-Left
            if (bottomQuadrant) return 2 // Bottom-Left
        } else if (bounds.left > midX) {
            if (topQuadrant) return 0    // Top-Right
            if (bottomQuadrant) return 3 // Bottom-Right
        }
        return -1
    }
}

