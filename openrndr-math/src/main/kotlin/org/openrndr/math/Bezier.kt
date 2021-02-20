package org.openrndr.math

import kotlin.math.sqrt


/* quadratic bezier */
fun bezier(x0: Double, c0: Double, x1: Double, t: Double): Double {
    val it = 1.0 - t
    val it2 = it * it
    val t2 = t * t
    return it2 * x0 + 2.0 * it * t * c0 + t2 * x1
}

fun derivative(x0: Double, c0: Double, x1: Double, t: Double): Double {
    val it = 1.0 - t
    return 2.0 * it * (c0 - x0) + 2.0 * t * (x1 - c0)
}

fun derivative(x0: Vector2, c0: Vector2, x1: Vector2, t: Double): Vector2 {
    val it = 1.0 - t
    return Vector2(2 * it * (c0.x - x0.x) + 2 * t * (x1.x - c0.x), 2 * it * (c0.y - x0.y) + 2 * t * (x1.y - c0.y))
}

/**
 * Similar to [derivative] but handles cases in which [p0] and [p1] coincide.
 */
fun safeDerivative(p0: Vector2, c0: Vector2, p1: Vector2, t: Double): Vector2 {
    val epsilon = 10E-6
    var u = t

    val d10 = c0 - p0
    val d21 = c0 - p1

    if (u < epsilon && d10.squaredLength < epsilon) {
        u = epsilon
    }

    if (u > (1.0 - epsilon) && d21.squaredLength < epsilon) {
        u = 1.0 - epsilon
    }

    val iu = 1.0 - u
    return Vector2(2 * iu * (c0.x - p0.x) + 2 * u * (p1.x - c0.x), 2 * iu * (c0.y - p0.y) + 2 * u * (p1.y - c0.y))
}

fun derivative(x0: Vector3, c0: Vector3, x1: Vector3, t: Double): Vector3 {
    val it = 1.0 - t
    return Vector3(2 * it * (c0.x - x0.x) + 2 * t * (x1.x - c0.x),
            2 * it * (c0.y - x0.y) + 2 * t * (x1.y - c0.y),
            2 * it * (c0.z - x0.z) + 2 * t * (x1.z - c0.z)
    )
}


fun roots(p: List<Double>): List<Double> {
    //https://github.com/Pomax/bezierjs/blob/gh-pages/lib/utils.js
    if (p.size == 3) {
        val a = p[0]
        val b = p[1]
        val c = p[2]
        val d = a - 2 * b + c
        if (d != 0.0) {
            val m1 = -sqrt(b * b - a * c)
            val m2 = -a + b
            val v1 = -(m1 + m2) / d
            val v2 = -(-m1 + m2) / d
            return listOf(v1, v2)
        } else if (b != c && d == 0.0) {
            return listOf((2 * b * c) / (2 * (b - c)))
        }
        return emptyList()
    } else if (p.size == 2) {
        val a = p[0]
        val b = p[1]
        return if (a != b) {
            listOf(a / (a - b))
        } else {
            emptyList()
        }
    }
    return emptyList()

}

fun derivative(p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, t: Double): Vector3 {
    val it = 1.0 - t
    return p1.minus(p0).times(3.0 * it * it).plus(p2.minus(p1).times(6.0 * it * t)).plus(p3.minus(p2).times(3.0 * t * t))
}


fun derivative(p0: Vector2, p1: Vector2, p2: Vector2, p3: Vector2, t: Double): Vector2 {
    val it = 1.0 - t
    return p1.minus(p0).times(3.0 * it * it).plus(p2.minus(p1).times(6.0 * it * t)).plus(p3.minus(p2).times(3.0 * t * t))
}

/**
 * Similar to [derivative] but handles cases in which [p0] and [p1] or [p2] and [p3] coincide.
 */
fun safeDerivative(p0: Vector2, p1: Vector2, p2: Vector2, p3: Vector2, t: Double): Vector2 {
    val epsilon = 10E-6
    var u = t

    val d10 = p1 - p0
    val d32 = p3 - p2

    if (u < epsilon && d10.squaredLength < epsilon) {
        u = epsilon
    }

    if (u > (1.0 - epsilon) && d32.squaredLength < epsilon) {
        u = 1.0 - epsilon
    }

    val iu = 1.0 - u
    return ((d10 * (3.0 * iu * iu)) + (p2 - p1) * (6.0 * iu * u)) + d32 * (3.0 * u * u)
}

fun normal(x0: Vector2, c0: Vector2, x1: Vector2, t: Double): Vector2 {
    val (x, y) = derivative(x0, c0, x1, t)
    return Vector2(-y, x).normalized
}

fun bezier(x0: Vector2, c0: Vector2, x1: Vector2, t: Double): Vector2 {
    val it = 1.0 - t
    val it2 = it * it
    val t2 = t * t

    return Vector2(
            it2 * x0.x + 2 * it * t * c0.x + t2 * x1.x,
            it2 * x0.y + 2 * it * t * c0.y + t2 * x1.y
    )
}

fun bezier(x0: Vector3, c0: Vector3, x1: Vector3, t: Double): Vector3 {
    val it = 1.0 - t
    val it2 = it * it
    val t2 = t * t

    return Vector3(
            it2 * x0.x + 2 * it * t * c0.x + t2 * x1.x,
            it2 * x0.y + 2 * it * t * c0.y + t2 * x1.y,
            it2 * x0.z + 2 * it * t * c0.z + t2 * x1.z)
}

/* cubic bezier */
fun bezier(x0: Double, c0: Double, c1: Double, x1: Double, t: Double): Double {
    val it = 1.0 - t
    val it2 = it * it
    val it3 = it2 * it
    val t2 = t * t
    val t3 = t2 * t

    return it3 * x0 + 3.0 * it2 * t * c0 + 3.0 * it * t2 * c1 + t3 * x1
}

fun bezier(x0: Vector2, c0: Vector2, c1: Vector2, x1: Vector2, t: Double): Vector2 {
    val it = 1.0 - t
    val it2 = it * it
    val it3 = it2 * it
    val t2 = t * t
    val t3 = t2 * t

    return Vector2(
            it3 * x0.x + 3 * it2 * t * c0.x + 3 * it * t2 * c1.x + t3 * x1.x,
            it3 * x0.y + 3 * it2 * t * c0.y + 3 * it * t2 * c1.y + t3 * x1.y)
}

/**
 * Samples a single point based on the provided
 * [t](https://pomax.github.io/bezierinfo/#explanation) value
 * from given 3D cubic Bézier curve.
 *
 * @param x0 The starting anchor point of the curve.
 * @param c0 The first control point.
 * @param c1 The second control point.
 * @param x1 The ending anchor point of the curve.
 * @param t The value of *t* in the range of `0.0` to `1.0`.
 * @return A sample on the curve.
 */
fun bezier(x0: Vector3, c0: Vector3, c1: Vector3, x1: Vector3, t: Double): Vector3 {
    val it = 1.0 - t
    val it2 = it * it
    val it3 = it2 * it
    val t2 = t * t
    val t3 = t2 * t

    return Vector3(
            it3 * x0.x + 3 * it2 * t * c0.x + 3 * it * t2 * c1.x + t3 * x1.x,
            it3 * x0.y + 3 * it2 * t * c0.y + 3 * it * t2 * c1.y + t3 * x1.y,
            it3 * x0.z + 3 * it2 * t * c0.z + 3 * it * t2 * c1.z + t3 * x1.z)
}

// linear type bezier

/* quadratic bezier */
fun <T: LinearType<T>> bezier(x0: T, c0: T, x1: T, t: Double): T {
    val it = 1.0 - t
    val it2 = it * it
    val t2 = t * t
    return x0 * it2 + c0 * (2.0 * it * t) + x1 * t2
}

fun <T : LinearType<T>> bezier(x0: T, c0: T, c1: T, x1: T, t: Double): T {
    val it = 1.0 - t
    val it2 = it * it
    val it3 = it2 * it
    val t2 = t * t
    val t3 = t2 * t
    return x0 * (it3) + c0 * (3 * it2 * t) + c1 * (3 * it * t2) + x1 * (t3)
}
