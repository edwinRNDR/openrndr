package org.openrndr.shape.internal

import org.openrndr.math.Vector3
import org.openrndr.shape.LineSegment3D

internal class BezierQuadraticSampler3D {
    private val recursionLimit = 8

    var distanceTolerance = 0.5
    private var distanceToleranceSquare = 0.0

    internal var points: MutableList<Vector3> = mutableListOf()
    internal var direction = mutableListOf<Vector3>()

    private fun sample(x1: Vector3, x2: Vector3, x3: Vector3, level: Int) {
        if (level > recursionLimit) {
            return
        }

        val x12 = (x1 + x2) * 0.5
        val x23 = (x2 + x3) * 0.5
        val x123 = (x12 + x23) * 0.5

        val dx = x3.x - x1.x
        val dy = x3.y - x1.y
        val dz = x3.z - x1.z
        var d = LineSegment3D(x1, x3).squaredDistance(x2)

        if (d > collinearityEpsilon) {
            // Regular case
            //-----------------
            if (d * d <= distanceToleranceSquare * (dx * dx + dy * dy + dz * dz)) {
                // If the curvature doesn't exceed the distance_tolerance value
                // we tend to finish subdivisions.
                //----------------------
                direction.add(x3-x1)
                points.add(x123)
                return
            }
        } else {
            // Collinear case
            //------------------
            val da = dx * dx + dy * dy + dz * dz
            if (da == 0.0) {
                d = squaredDistance(x1.x, x1.y, x1.z, x2.x, x2.y, x2.z)
            } else {
                d = ((x2.x - x1.x) * dx + (x2.y - x1.y) * dy) / da
                if (d > 0 && d < 1) {
                    // Simple collinear case, 1---2---3
                    // We can leave just two endpoints
                    return
                }
                d = when {
                    d <= 0 -> squaredDistance(x2.x, x2.y, x2.z, x1.x, x1.y, x1.z)
                    d >= 1 -> squaredDistance(x2.x, x2.y, x2.z, x3.x, x3.y, x3.z)
                    else -> squaredDistance(x2.x, x2.y, x2.z, x1.x + d * dx, x1.y + d * dy, x1.z + d * dz)
                }
            }
            if (d < distanceToleranceSquare) {
                direction.add(x3-x1)
                points.add(x2)
                return
            }
        }

        // Continue subdivision
        //----------------------
        sample(x1, x12, x123, level + 1)
        sample(x123, x23, x3, level + 1)
    }

    fun sample(x1: Vector3, x2: Vector3, x3: Vector3): Pair< List<Vector3>, List<Vector3> > {
        distanceToleranceSquare = distanceTolerance * distanceTolerance
        points.clear()
        direction.clear()
        points.add(x1); direction.add(x2-x1)
        sample(x1, x2, x3, 0)
        points.add(x3); direction.add(x3-x2)
        return Pair(points, direction)
    }

    companion object {
        private const val collinearityEpsilon = 1e-30

        private fun squaredDistance(x1: Double, y1: Double, z1:Double, x2: Double, y2: Double, z2:Double): Double {
            val dx = x2 - x1
            val dy = y2 - y1
            val dz = z2 - z1
            return dx * dx + dy * dy + dz * dz
        }
    }
}
