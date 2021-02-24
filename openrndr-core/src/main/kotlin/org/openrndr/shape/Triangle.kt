package org.openrndr.shape

import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Creates a simple three-point polygon.
 */
data class Triangle(val x1: Vector2, val x2: Vector2, val x3: Vector2) {

    /** Returns true if given [v] lies inside the [Triangle]. */
    operator fun contains(v: Vector2): Boolean {
        val x23 = x2 - x3
        val x32 = x3 - x2
        val x31 = x3 - x1
        val x13 = x1 - x3
        val det = x23.y * x13.x - x32.x * x31.y
        val minD = min(det, 0.0) - 10E-6
        val maxD = max(det, 0.0) + 10E-6

        val d = v - x3
        val a = x23.y * d.x + x32.x * d.y
        if (a < minD || a > maxD) {
            return false
        }
        val b = x31.y * d.x + x13.x * d.y
        if (b < minD || b > maxD) {
            return false
        }
        val c = det - a - b
        if (c < minD || c > maxD)
            return false

        return true
    }

    /** Returns [ShapeContour] representation of the [Triangle]. */
    val contour: ShapeContour
        get() = ShapeContour.fromPoints(listOf(x1, x2, x3), closed = true)

    /** Generates a random point that lies inside the [Triangle]. */
    fun randomPoint(random: Random = Random.Default): Vector2 {
        val u = random.nextDouble()
        val v = random.nextDouble()
        val su0 = sqrt(u)
        val b0 = 1.0 - su0
        val b1 = v * su0
        val b = Vector3(b0, b1, 1.0 - b0 - b1)
        return x1 * b.x + x2 * b.y + x3 * b.z
    }

    /** The unitless area covered by this [Triangle]. */
    val area by lazy {
        val u = x2 - x1
        val v = x3 - x1
        abs(u cross v) / 2.0
    }

    /** The centroid of the [Triangle]. */
    val centroid by lazy {
        (x1 + x2 + x3) / 3.0
    }

    companion object {
        /**
         * Creates a triangle from a [centroid] based on the circumradius [radius]
         *
         * @param centroid
         * @param radius
         * @param theta angle of one of the vertices -> equilateral if theta = 60.0 and isosceles otherwise
         * @param rotation
         * @return
         */
        fun fromCentroid(centroid: Vector2, radius: Double, theta: Double = 60.0, rotation: Double = 0.0): Triangle {
            val omega = (180.0 - theta)

            val x1 = centroid + Polar(rotation, radius).cartesian
            val x2 = centroid + Polar(omega + rotation, radius).cartesian
            val x3 = centroid + Polar(-omega + rotation, radius).cartesian

            val c = (x1 + x2 + x3) / 3.0
            val delta = centroid - c

            return Triangle(x1 + delta, x2 + delta, x3 + delta)
        }
    }

    operator fun times(scale: Double): Triangle {
        return Triangle(x1 * scale, x2 * scale, x3 * scale)
    }

    operator fun div(scale: Double): Triangle {
        return Triangle(x1 / scale, x2 / scale, x3 / scale)
    }

    operator fun plus(right: Triangle): Triangle {
        return Triangle(x1 + right.x1, x2 + right.x2, x3 + right.x3)
    }

    operator fun minus(right: Triangle): Triangle {
        return Triangle(x1 - right.x1, x2 - right.x2, x3 - right.x3)
    }

}