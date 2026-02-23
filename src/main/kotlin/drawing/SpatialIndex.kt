package drawing

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import data.Stroke

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
    
    // Internal constructor for children
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
            } else {
                // If it overlaps multiple quadrants, we unfortunately have to store it here 
                // OR duplicate it (reference) in multiple children.
                // For simplicity: Store in this node if it doesn't fit typically into one child.
                // Better approach for strokes: Store only in leaf nodes that intersect (referenced).
                // But simplified QuadTree usually stores items in the node where they fully fit.
                // Let's stick to "Store in THIS node if it crosses boundaries" strategy for now, 
                // or push to ALL intersecting children.
                // Let's push to ALL intersecting children for faster query, but careful with duplicates on remove.
                // Actually, "Store where it fits" is safer for simple object identity removal.
                // Let's us standard: if (index != -1) insert to child, else add to this.bottom strokes.
            }
        }

        strokes.add(stroke)

        if (strokes.size > capacity && children == null && depth < maxDepth) {
            subdivide()
            // Re-distribute existing
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
        // Try simple removal from shared list in this node
        if (strokes.remove(stroke)) return

        // Delegate to children
        if (children != null) {
            val index = getIndex(stroke.bounds)
            if (index != -1) {
                children!![index].remove(stroke)
                return
            }
            // If it was stored here because it didn't fit children, we already checked strokes.remove
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
                // Optimization: Don't query child if range doesn't overlap child
                if (child.boundary.overlaps(range)) {
                    found.addAll(child.query(range))
                }
            }
        }
        return found
    }

    override fun hitTest(point: Offset, threshold: Float): List<Stroke> {
        val searchRect = Rect(
            point.x - threshold, 
            point.y - threshold, 
            point.x + threshold, 
            point.y + threshold
        )
        // Reuse query logic
        return query(searchRect).filter { stroke ->
            // Refined check
            val inflated = stroke.bounds.inflate(threshold)
            if (!inflated.contains(point)) return@filter false
            
            stroke.points.any { (it - point).getDistance() < threshold }
        }
    }

    override fun clear() {
        strokes.clear()
        children = null
    }

    override fun getAll(): List<Stroke> {
        val all = ArrayList<Stroke>()
        all.addAll(strokes)
        children?.forEach { all.addAll(it.getAll()) }
        return all
    }

    private fun subdivide() {
        val subWidth = boundary.width / 2
        val subHeight = boundary.height / 2
        val x = boundary.left
        val y = boundary.top

        children = arrayOf(
            QuadTree(Rect(x, y, x + subWidth, y + subHeight), capacity, depth + 1, maxDepth), // TL
            QuadTree(Rect(x + subWidth, y, x + boundary.width, y + subHeight), capacity, depth + 1, maxDepth), // TR
            QuadTree(Rect(x, y + subHeight, x + subWidth, y + boundary.height), capacity, depth + 1, maxDepth), // BL
            QuadTree(Rect(x + subWidth, y + subHeight, x + boundary.width, y + boundary.height), capacity, depth + 1, maxDepth) // BR
        )
    }

    /**
     * Returns the index of the child that fully contains the rect.
     * Returns -1 if it doesn't fit in any single child (overlaps split).
     */
    private fun getIndex(rect: Rect): Int {
        val midX = boundary.left + boundary.width / 2
        val midY = boundary.top + boundary.height / 2

        val topQuadrant = rect.top < midY && rect.bottom < midY
        val bottomQuadrant = rect.top > midY
        
        val leftQuadrant = rect.left < midX && rect.right < midX
        val rightQuadrant = rect.left > midX

        if (leftQuadrant) {
            if (topQuadrant) return 0 // TL
            if (bottomQuadrant) return 2 // BL
        } else if (rightQuadrant) {
            if (topQuadrant) return 1 // TR
            if (bottomQuadrant) return 3 // BR
        }
        return -1
    }
}
