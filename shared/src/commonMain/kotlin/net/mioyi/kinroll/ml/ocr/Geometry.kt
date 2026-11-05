package net.mioyi.kinroll.ml.ocr

import kotlin.math.abs
import kotlin.math.hypot

internal data class Point(val x: Double, val y: Double)

internal data class OrientedRect(
    val center: Point, val ux: Double, val uy: Double, val width: Double, val height: Double,
) {
    fun expanded(distance: Double) = copy(width = width + distance * 2, height = height + distance * 2)
    fun corners(): List<Point> {
        val x = ux * width / 2
        val y = uy * width / 2
        val vx = -uy * height / 2
        val vy = ux * height / 2
        return listOf(
            Point(center.x - x - vx, center.y - y - vy),
            Point(center.x + x - vx, center.y + y - vy),
            Point(center.x + x + vx, center.y + y + vy),
            Point(center.x - x + vx, center.y - y + vy),
        )
    }
    fun contains(x: Double, y: Double): Boolean {
        val dx = x - center.x
        val dy = y - center.y
        return abs(dx * ux + dy * uy) <= width / 2 + 1e-7 &&
            abs(-dx * uy + dy * ux) <= height / 2 + 1e-7
    }
}

internal object Geometry {
    private fun cross(o: Point, a: Point, b: Point): Double =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    fun convexHull(points: List<Point>): List<Point> {
        val sorted = points.distinct().sortedWith(compareBy<Point> { it.x }.thenBy { it.y })
        if (sorted.size <= 2) return sorted
        val hull = ArrayList<Point>(sorted.size + 1)
        for (p in sorted) {
            while (hull.size >= 2 && cross(hull[hull.size - 2], hull.last(), p) <= 0) hull.removeAt(hull.lastIndex)
            hull.add(p)
        }
        val lowerSize = hull.size
        for (i in sorted.lastIndex - 1 downTo 0) {
            val p = sorted[i]
            while (hull.size > lowerSize && cross(hull[hull.size - 2], hull.last(), p) <= 0) hull.removeAt(hull.lastIndex)
            hull.add(p)
        }
        hull.removeAt(hull.lastIndex)
        return hull
    }

    /** Four monotone support indices give O(n) rotating calipers on a CCW convex hull. */
    fun minimumAreaRect(hull: List<Point>): OrientedRect? {
        if (hull.size < 3) return null
        val n = hull.size
        val supports = IntArray(4)
        var best: OrientedRect? = null
        var bestArea = Double.POSITIVE_INFINITY
        for (edge in hull.indices) {
            val a = hull[edge]
            val b = hull[(edge + 1) % n]
            val length = hypot(b.x - a.x, b.y - a.y)
            if (length <= 1e-12) continue
            val ux = (b.x - a.x) / length
            val uy = (b.y - a.y) / length
            for (axis in 0..3) {
                val dx = when (axis) { 0 -> ux; 1 -> -ux; 2 -> -uy; else -> uy }
                val dy = when (axis) { 0 -> uy; 1 -> -uy; 2 -> ux; else -> -ux }
                fun dot(index: Int): Double = hull[index].x * dx + hull[index].y * dy
                if (edge == 0) {
                    for (i in 1 until n) if (dot(i) > dot(supports[axis])) supports[axis] = i
                } else {
                    var advances = 0
                    while (advances < n) {
                        val next = (supports[axis] + 1) % n
                        if (dot(next) < dot(supports[axis]) - 1e-10) break
                        supports[axis] = next
                        advances++
                    }
                }
            }
            fun projection(index: Int, dx: Double, dy: Double): Double = hull[index].x * dx + hull[index].y * dy
            val maxU = projection(supports[0], ux, uy)
            val minU = projection(supports[1], ux, uy)
            val maxV = projection(supports[2], -uy, ux)
            val minV = projection(supports[3], -uy, ux)
            val width = maxU - minU
            val height = maxV - minV
            val area = width * height
            if (area < bestArea) {
                bestArea = area
                val midU = (minU + maxU) / 2
                val midV = (minV + maxV) / 2
                best = OrientedRect(Point(midU * ux - midV * uy, midU * uy + midV * ux), ux, uy, width, height)
            }
        }
        return best
    }

    /** Same left/right split convention as Paddle's get_min_boxes: tl, tr, br, bl. */
    fun orderedCorners(points: List<Point>): List<Point> {
        require(points.size == 4)
        val byX = points.sortedWith(compareBy<Point> { it.x }.thenBy { it.y })
        val leftTop = if (byX[0].y <= byX[1].y) byX[0] else byX[1]
        val leftBottom = if (byX[0].y <= byX[1].y) byX[1] else byX[0]
        val rightTop = if (byX[2].y <= byX[3].y) byX[2] else byX[3]
        val rightBottom = if (byX[2].y <= byX[3].y) byX[3] else byX[2]
        return listOf(leftTop, rightTop, rightBottom, leftBottom)
    }
}
