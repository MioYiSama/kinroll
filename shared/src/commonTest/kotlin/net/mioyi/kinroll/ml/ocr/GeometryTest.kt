package net.mioyi.kinroll.ml.ocr

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.random.Random

class GeometryTest {
    @Test fun rotatedRectangleArea() {
        val angle = PI / 6
        val points = mutableListOf<Point>()
        for (x in 0..40) for (y in 0..12) {
            points.add(Point(70 + x * cos(angle) - y * sin(angle), 20 + x * sin(angle) + y * cos(angle)))
        }
        val rect = assertNotNull(Geometry.minimumAreaRect(Geometry.convexHull(points)))
        assertTrue(abs(rect.width * rect.height - 480) / 480 < 0.02)
        val corners = Geometry.orderedCorners(rect.corners())
        assertTrue(corners[0].x <= corners[1].x)
        assertTrue(corners[0].y <= corners[3].y)
    }

    @Test fun collinearPointsHaveNoRectangle() {
        assertEquals(null, Geometry.minimumAreaRect(Geometry.convexHull(listOf(Point(0.0, 0.0), Point(1.0, 1.0), Point(2.0, 2.0)))))
    }

    @Test fun calipersMatchesExhaustiveEdgeProjections() {
        val random = Random(42)
        repeat(60) {
            val hull = Geometry.convexHull(List(40) { Point(random.nextDouble() * 100, random.nextDouble() * 100) })
            val rect = assertNotNull(Geometry.minimumAreaRect(hull))
            val expected = hull.indices.minOf { edge ->
                val a = hull[edge]
                val b = hull[(edge + 1) % hull.size]
                val length = kotlin.math.hypot(b.x - a.x, b.y - a.y)
                val ux = (b.x - a.x) / length
                val uy = (b.y - a.y) / length
                val u = hull.map { it.x * ux + it.y * uy }
                val v = hull.map { -it.x * uy + it.y * ux }
                (u.max() - u.min()) * (v.max() - v.min())
            }
            assertTrue(abs(expected - rect.width * rect.height) < 1e-6)
        }
    }
}
