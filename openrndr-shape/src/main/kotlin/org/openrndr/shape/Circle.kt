package org.openrndr.shape

import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

data class Circle(val center: Vector2, val radius: Double) {
    constructor(x: Double, y: Double, radius: Double) : this(Vector2(x, y), radius)

    companion object {
        fun fromPoints(a: Vector2, b: Vector2): Circle {
            val center = (a + b) * 0.5
            return Circle(center, b.minus(center).length)
        }
    }

    /** creates new [Circle] with center offset by [offset] */
    fun moved(offset: Vector2): Circle = Circle(center + offset, radius)

    /** creates new [Circle] with center at [position] */
    fun movedTo(position: Vector2): Circle = Circle(position, radius)

    /** creates new [Circle] with radius scaled by [scale] */
    fun scaled(scale: Double): Circle = Circle(center, radius * scale)

    /** creates new [Circle] with radius set to [fitRadius] */
    fun scaledTo(fitRadius: Double) = Circle(center, fitRadius)

    fun contains(point: Vector2): Boolean = point.minus(center).squaredLength < radius * radius

    /** creates [Shape] representation */
    val shape get() = Shape(listOf(contour))

    /** creates [ShapeContour] representation */
    val contour: ShapeContour
        get() {
            val x = center.x - radius
            val y = center.y - radius
            val width = radius * 2.0
            val height = radius * 2.0
            val kappa = 0.5522848
            val ox = width / 2 * kappa        // control point offset horizontal
            val oy = height / 2 * kappa        // control point offset vertical
            val xe = x + width        // x-end
            val ye = y + height        // y-end
            val xm = x + width / 2        // x-middle
            val ym = y + height / 2       // y-middle

            return contour {
                moveTo(Vector2(x, ym))
                curveTo(Vector2(x, ym - oy), Vector2(xm - ox, y), Vector2(xm, y))
                curveTo(Vector2(xm + ox, y), Vector2(xe, ym - oy), Vector2(xe, ym))
                curveTo(Vector2(xe, ym + oy), Vector2(xm + ox, ye), Vector2(xm, ye))
                curveTo(Vector2(xm - ox, ye), Vector2(x, ym + oy), Vector2(x, ym))
                close()
            }
        }

    /**
     * calculates the tangent lines between two circles
     * by default it returns the outer tangents
     * @param isInner if true returns the inner tangents
     **/
    fun tangents(c: Circle, isInner: Boolean = false): List<Pair<Vector2, Vector2>>? {
        val r1 = radius
        val r2 = if (isInner) -c.radius else c.radius

        val w = (c.center - center) // hypotenuse
        val d = w.length
        val dr = r1 - r2 // adjacent
        val d2 = sqrt(d)
        val h = sqrt(d.pow(2.0) - dr.pow(2.0))

        if (d2 == 0.0) return null

        return listOf(-1.0, 1.0).map { sign ->
            val v = (w * dr + w.perpendicular() * h * sign) / d.pow(2.0)

            Pair(center + v * r1, c.center + v * r2)
        }
    }

    /** calculates the tangent lines to an external point **/
    fun tangents(point: Vector2): Pair<Vector2, Vector2> {
        val v = Polar.fromVector(point - center)
        val b = v.radius
        val theta = Math.toDegrees(acos(radius / b))
        val d1 = v.theta + theta
        val d2 = v.theta - theta

        val tp = center + Polar(d1, radius).cartesian
        val tp2 = center + Polar(d2, radius).cartesian

        return Pair(tp, tp2)
    }
}