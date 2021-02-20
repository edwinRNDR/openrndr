package org.openrndr.shape.internal

import org.openrndr.math.Vector3
import org.openrndr.shape.LineSegment3D

internal class BezierCubicSampler3D {
    private val recursionLimit = 8
    private val points = mutableListOf<Vector3>()
    private val direction = mutableListOf<Vector3>()
    private var distanceToleranceSquare = 0.0
    var distanceTolerance = 0.5

    fun sample(x1: Vector3, x2: Vector3, x3: Vector3, x4: Vector3): Pair<List<Vector3>, List<Vector3>> {
        distanceToleranceSquare = distanceTolerance * distanceTolerance
        points.clear()
        points.add(x1)
        sample(x1, x2, x3, x4, 0)
        points.add(x4); direction.add(x4 - x3)
        return Pair(points, direction)
    }

    private fun sample(x1: Vector3, x2: Vector3, x3: Vector3, x4: Vector3, level: Int) {
        if (level > recursionLimit) {
            return
        }

        val x12 = (x1 + x2) * 0.5
        val x23 = (x2 + x3) * 0.5
        val x34 = (x3 + x4) * 0.5

        val x123 = (x12 + x23) * 0.5
        val x234 = (x23 + x34) * 0.5

        val x1234 = (x123 + x234) * 0.5

        val dx = x4.x - x1.x
        val dy = x4.y - x1.y
        val dz = x4.z - x1.z

        // TODO: fix this
        var d2 = LineSegment3D(x1, x4).squaredDistance(x2)
        var d3 = LineSegment3D(x1, x4).squaredDistance(x3)

        val p1 = d2 > colinearityEpsilon
        val p0 = d3 > colinearityEpsilon
        val p = (if (p1) 2 else 0) + if (p0) 1 else 0

        var k: Double
        var da1: Double
        var da2: Double
        var da3: Double
        when (p) {
            0 -> {
                k = dx * dx + dy * dy + dz * dz
                if (k == 0.0) {
                    d2 = (x1-x2).squaredLength //squareDistance(x1.x, x1.y, x2.x, x2.y, x2)
                    d3 = (x4-x3).squaredLength//squareDistance(x4.x, x4.y, x3.x, x3.y)
                } else {
                    k = 1 / k
                    da1 = x2.x - x1.x
                    da2 = x2.y - x1.y
                    da3 = x2.z - x1.z
                    d2 = k * (da1 * dx + da2 * dy + da3 * dz)
                    da1 = x3.x - x1.x
                    da2 = x3.y - x1.y
                    da3 = x3.z - x1.z
                    d3 = k * (da1 * dx + da2 * dy + da3 * dz)
                    if (d2 > 0 && d2 < 1 && d3 > 0 && d3 < 1) {
                        // Simple collinear case, 1---2---3---4
                        // We can leave just two endpoints
                        return
                    }

                    d2 = when {
                        d2 <= 0 -> (x2-x1).squaredLength//squareDistance(x2.x, x2.y, x1.x, x1.y)
                        d2 >= 1 -> (x2-x4).squaredLength//squareDistance(x2.x, x2.y, x4.x, x4.y)
                        else -> squareDistance(x2.x, x2.y, x2.z,
                                x1.x + d2 * dx, x1.y + d2 * dy, x1.z + d2 * dz)
                    }

                    d3 = when {
                        d3 <= 0 -> squareDistance(x3.x, x3.y, x3.z, x1.x, x1.y, x1.z)
                        d3 >= 1 -> squareDistance(x3.x, x3.y, x3.z, x4.x, x4.y, x4.z)
                        else -> squareDistance(x3.x, x3.y, x3.z, x1.x + d3 * dx, x1.y + d3 * dy, x1.z + d3 * dz)
                    }

                }
                if (d2 > d3) {
                    if (d2 < distanceToleranceSquare) {
                        points.add(Vector3(x2.x, x2.y, x2.z)); direction.add(x4 - x1)
                        return
                    }
                } else {
                    if (d3 < distanceToleranceSquare) {
                        points.add(Vector3(x3.x, x3.y, x3.z)); direction.add(x4 - x1)
                        return
                    }
                }
            }
            1 ->
                // p1,p2,p4 are collinear, p3 is significant
                //----------------------
                if (d3 * d3 <= distanceToleranceSquare * (dx * dx + dy * dy + dz * dz)) {
                    points.add(x23); direction.add(x4 - x1)
                    return

                }
            2 ->
                // p1,p3,p4 are collinear, p2 is significant
                //----------------------
                if (d2 * d2 <= distanceToleranceSquare * (dx * dx + dy * dy + dz * dz)) {
                    points.add(x23); direction.add(x4 - x1)
                    return
                }
            3 ->
                // Regular case
                //-----------------
                if ((d2 + d3) * (d2 + d3) <= distanceToleranceSquare * (dx * dx + dy * dy + dz * dz)) {
                    // If the curvature doesn't exceed the distance_tolerance value
                    // we tend to finish subdivisions.
                    //----------------------
                    points.add(x23); direction.add(x4 - x1)
                    return
                }
        }
        sample(x1, x12, x123, x1234, level + 1)
        sample(x1234, x234, x34, x4, level + 1)
    }

    companion object {
        private const val colinearityEpsilon = 1e-30
        private const val angleToleranceEpsilon = 0.01

        private fun squareDistance(x: Double, y: Double, z: Double,
                                   x1: Double, y1: Double, z1:Double): Double {
            val dx = x1 - x
            val dy = y1 - y
            val dz = z1 - z
            return dx * dx + dy * dy + dz * dz
        }
    }
}
