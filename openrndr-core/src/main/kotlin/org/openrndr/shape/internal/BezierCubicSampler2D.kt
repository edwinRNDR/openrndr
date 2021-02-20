package org.openrndr.shape.internal

import org.openrndr.math.Vector2
import kotlin.math.abs
import kotlin.math.atan2

internal class BezierCubicSampler2D {
    private val recursionLimit = 8
    private val points = mutableListOf<Vector2>()
    private val direction = mutableListOf<Vector2>()

    private var distanceToleranceSquare = 0.0
    private val angleTolerance = 0.0
    private val cuspLimit = 0.0
    var distanceTolerance = 0.5

    fun sample(x1: Vector2, x2: Vector2, x3: Vector2, x4: Vector2): Pair<List<Vector2>, List<Vector2>> {
        distanceToleranceSquare = distanceTolerance * distanceTolerance
        points.clear()
        direction.clear()
        points.add(x1); direction.add(x2 - x1)
        sample(x1, x2, x3, x4, 0)
        if (points.last().squaredDistanceTo(x4) > 0.0) {
            points.add(x4); direction.add(x4 - x3)
        }
        return Pair(points, direction)
    }

    private fun sample(x1: Vector2, x2: Vector2, x3: Vector2, x4: Vector2, level: Int) {
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

        var d2 = abs((x2.x - x4.x) * dy - (x2.y - x4.y) * dx)
        var d3 = abs((x3.x - x4.x) * dy - (x3.y - x4.y) * dx)

        val p1 = d2 > colinearityEpsilon
        val p0 = d3 > colinearityEpsilon
        val p = (if (p1) 2 else 0) + if (p0) 1 else 0

        var k: Double
        var da1: Double
        var da2: Double
        when (p) {
            0 -> {
                k = dx * dx + dy * dy
                if (k == 0.0) {
                    d2 = squareDistance(x1.x, x1.y, x2.x, x2.y)
                    d3 = squareDistance(x4.x, x4.y, x3.x, x3.y)
                } else {
                    k = 1 / k
                    da1 = x2.x - x1.x
                    da2 = x2.y - x1.y
                    d2 = k * (da1 * dx + da2 * dy)
                    da1 = x3.x - x1.x
                    da2 = x3.y - x1.y
                    d3 = k * (da1 * dx + da2 * dy)
                    if (d2 > 0 && d2 < 1 && d3 > 0 && d3 < 1) {
                        // Simple collinear case, 1---2---3---4
                        // We can leave just two endpoints
                        return
                    }

                    d2 = when {
                        d2 <= 0 -> squareDistance(x2.x, x2.y, x1.x, x1.y)
                        d2 >= 1 -> squareDistance(x2.x, x2.y, x4.x, x4.y)
                        else -> squareDistance(x2.x, x2.y, x1.x + d2 * dx, x1.y + d2 * dy)
                    }

                    d3 = when {
                        d3 <= 0 -> squareDistance(x3.x, x3.y, x1.x, x1.y)
                        d3 >= 1 -> squareDistance(x3.x, x3.y, x4.x, x4.y)
                        else -> squareDistance(x3.x, x3.y, x1.x + d3 * dx, x1.y + d3 * dy)
                    }

                }
                if (d2 > d3) {
                    if (d2 < distanceToleranceSquare) {
                        if (points.last().squaredDistanceTo(x2) > 0.0) {
                            points.add(x2); direction.add(x4 - x1)
                        }
                        return
                    }
                } else {
                    if (d3 < distanceToleranceSquare) {
                        if (points.last().squaredDistanceTo(x3) > 0.0) {
                            points.add(x3); direction.add(x4 - x1)
                        }
                        return
                    }
                }
            }
            1 ->
                // p1,p2,p4 are collinear, p3 is significant
                //----------------------
                if (d3 * d3 <= distanceToleranceSquare * (dx * dx + dy * dy)) {
                    if (angleTolerance < angleToleranceEpsilon) {
                        if (points.last().squaredDistanceTo(x23) > 0.0) {
                            points.add(x23); direction.add(x4 - x1)
                        }
                        return
                    }

                    // Angle Condition
                    //----------------------
                    da1 = abs(atan2(x4.y - x3.y, x4.x - x3.x) - atan2(x3.y - x2.y, x3.x - x2.x))
                    if (da1 >= Math.PI) da1 = 2 * Math.PI - da1

                    if (da1 < angleTolerance) {
                        if (points.last().squaredDistanceTo(x2) > 0.0) {
                            points.add(x2); direction.add(x4 - x1)
                        }
                        if (points.last().squaredDistanceTo(x3) > 0.0) {
                            points.add(x3); direction.add(x4 - x1)
                        }
                        return
                    }

                    if (cuspLimit != 0.0) {
                        if (da1 > cuspLimit) {
                            if (points.last().squaredDistanceTo(x3) > 0.0) {
                                points.add(x3); direction.add(x4 - x1)
                            }
                            return
                        }
                    }
                }
            2 ->
                // p1,p3,p4 are collinear, p2 is significant
                //----------------------
                if (d2 * d2 <= distanceToleranceSquare * (dx * dx + dy * dy)) {
                    if (angleTolerance < angleToleranceEpsilon) {
                        if (points.last().squaredDistanceTo(x23) > 0.0) {
                            points.add(x23); direction.add(x4 - x1)
                        }
                        return
                    }

                    // Angle Condition
                    //----------------------
                    da1 = abs(atan2(x3.y - x2.y, x3.x - x2.x) - atan2(x2.y - x1.y, x2.x - x1.x))
                    if (da1 >= Math.PI) da1 = 2 * Math.PI - da1

                    if (da1 < angleTolerance) {
                        if (points.last().squaredDistanceTo(x2) > 0.0) {
                            points.add(x2); direction.add(x4 - x1)
                        }
                        if (points.last().squaredDistanceTo(x3) > 0.0) {
                            points.add(x3); direction.add(x4 - x1)
                        }
                        return
                    }

                    if (cuspLimit != 0.0) {
                        if (da1 > cuspLimit) {
                            if (points.last().squaredDistanceTo(x2) > 0.0) {
                                points.add(x2); direction.add(x4 - x1)
                            }
                            return
                        }
                    }
                }
            3 ->
                // Regular case
                //-----------------
                if ((d2 + d3) * (d2 + d3) <= distanceToleranceSquare * (dx * dx + dy * dy)) {
                    // If the curvature doesn't exceed the distance_tolerance value
                    // we tend to finish subdivisions.
                    //----------------------
                    if (angleTolerance < angleToleranceEpsilon) {
                        if (points.last().squaredDistanceTo(x23) > 0.0) {
                            points.add(x23); direction.add(x4 - x1)
                        }
                        return
                    }

                    // Angle & Cusp Condition
                    //----------------------
                    k = atan2(x3.y - x2.y, x3.x - x2.x)
                    da1 = abs(k - atan2(x2.y - x1.y, x2.x - x1.x))
                    da2 = abs(atan2(x4.y - x3.y, x4.x - x3.x) - k)
                    if (da1 >= Math.PI) da1 = 2 * Math.PI - da1
                    if (da2 >= Math.PI) da2 = 2 * Math.PI - da2

                    if (da1 + da2 < angleTolerance) {
                        // Finally we can stop the recursion
                        //----------------------
                        if (points.last().squaredDistanceTo(x23) > 0.0) {
                            points.add(x23); direction.add(x4 - x1)
                        }
                        return
                    }

                    if (cuspLimit != 0.0) {
                        if (da1 > cuspLimit) {
                            if (points.last().squaredDistanceTo(x2) > 0.0) {
                                points.add(x2); direction.add(x4 - x1)
                            }
                            return
                        }

                        if (da2 > cuspLimit) {
                            if (points.last().squaredDistanceTo(x3) > 0.0) {
                                points.add(x3); direction.add(x4 - x1)
                            }
                            return
                        }
                    }
                }
        }
        sample(x1, x12, x123, x1234, level + 1)
        sample(x1234, x234, x34, x4, level + 1)
    }

    companion object {
        private const val colinearityEpsilon = 1e-30
        private const val angleToleranceEpsilon = 0.01

        private fun squareDistance(x: Double, y: Double, x1: Double, y1: Double): Double {
            val dx = x1 - x
            val dy = y1 - y
            return dx * dx + dy * dy
        }
    }
}
