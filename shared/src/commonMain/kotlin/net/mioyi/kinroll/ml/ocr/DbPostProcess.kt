package net.mioyi.kinroll.ml.ocr

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class DbPostProcess(
    private val thresh: Float = 0.2f,
    private val boxThresh: Float = 0.45f,
    private val unclipRatio: Double = 1.4,
    private val maxCandidates: Int = 3000,
) {
    fun boxes(probabilities: FloatArray, width: Int, height: Int, originalWidth: Int, originalHeight: Int): List<FloatArray> {
        require(width > 0 && height > 0 && probabilities.size == width * height)
        require(originalWidth > 0 && originalHeight > 0)
        val visited = BooleanArray(probabilities.size)
        val queue = IntArray(probabilities.size)
        val rowMin = IntArray(height) { width }
        val rowMax = IntArray(height) { -1 }
        val boxes = mutableListOf<FloatArray>()
        var candidates = 0
        for (seed in probabilities.indices) {
            if (visited[seed] || probabilities[seed] <= thresh) continue
            if (candidates++ >= maxCandidates) break
            var read = 0
            var count = 1
            queue[0] = seed
            visited[seed] = true
            var minY = height
            var maxY = -1
            while (read < count) {
                val index = queue[read++]
                val x = index % width
                val y = index / width
                minY = min(minY, y)
                maxY = max(maxY, y)
                rowMin[y] = min(rowMin[y], x)
                rowMax[y] = max(rowMax[y], x)
                for (ny in max(0, y - 1)..min(height - 1, y + 1)) {
                    for (nx in max(0, x - 1)..min(width - 1, x + 1)) {
                        val neighbor = ny * width + nx
                        if (!visited[neighbor] && probabilities[neighbor] > thresh) {
                            visited[neighbor] = true
                            queue[count++] = neighbor
                        }
                    }
                }
            }
            // Row extrema of pixel squares have exactly the same hull as all component pixels.
            val boundary = ArrayList<Point>((maxY - minY + 1) * 4)
            for (y in minY..maxY) {
                if (rowMax[y] >= 0) {
                    val left = rowMin[y].toDouble()
                    val right = rowMax[y] + 1.0
                    boundary.add(Point(left, y.toDouble()))
                    boundary.add(Point(right, y.toDouble()))
                    boundary.add(Point(left, y + 1.0))
                    boundary.add(Point(right, y + 1.0))
                    rowMin[y] = width
                    rowMax[y] = -1
                }
            }
            val rect = Geometry.minimumAreaRect(Geometry.convexHull(boundary)) ?: continue
            if (min(rect.width, rect.height) < 3) continue
            if (boxScore(rect, probabilities, width, height) < boxThresh) continue
            val distance = rect.width * rect.height * unclipRatio / (2 * (rect.width + rect.height))
            val expanded = rect.expanded(distance)
            if (min(expanded.width, expanded.height) < 5) continue
            val ordered = Geometry.orderedCorners(expanded.corners())
            val box = FloatArray(8)
            for (i in 0..3) {
                box[i * 2] = (ordered[i].x * originalWidth / width).toFloat().coerceIn(0f, (originalWidth - 1).toFloat())
                box[i * 2 + 1] = (ordered[i].y * originalHeight / height).toFloat().coerceIn(0f, (originalHeight - 1).toFloat())
            }
            boxes.add(box)
        }
        // Paddle's local insertion pass avoids a non-transitive |dy| < 10 comparator.
        boxes.sortWith(compareBy<FloatArray> { it[1] }.thenBy { it[0] })
        for (i in 1 until boxes.size) {
            var j = i
            while (j > 0 && abs(boxes[j][1] - boxes[j - 1][1]) < 10 && boxes[j][0] < boxes[j - 1][0]) {
                val swap = boxes[j]
                boxes[j] = boxes[j - 1]
                boxes[j - 1] = swap
                j--
            }
        }
        return boxes
    }

    private fun boxScore(rect: OrientedRect, probabilities: FloatArray, width: Int, height: Int): Float {
        val corners = rect.corners()
        val minX = floor(corners.minOf { it.x }).toInt().coerceIn(0, width - 1)
        val maxX = ceil(corners.maxOf { it.x }).toInt().coerceIn(0, width - 1)
        val minY = floor(corners.minOf { it.y }).toInt().coerceIn(0, height - 1)
        val maxY = ceil(corners.maxOf { it.y }).toInt().coerceIn(0, height - 1)
        var sum = 0.0
        var count = 0
        for (y in minY..maxY) for (x in minX..maxX) {
            if (rect.contains(x + 0.5, y + 0.5)) { sum += probabilities[y * width + x]; count++ }
        }
        return if (count == 0) 0f else (sum / count).toFloat()
    }
}
