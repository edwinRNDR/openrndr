package org.openrndr.math

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min

data class Vector2(val x: Double, val y: Double) : Serializable {
    constructor(x: Double) : this(x, x)

    val length: Double
        get() = Math.sqrt(x * x + y * y)

    val squaredLength: Double
        get() = x * x + y * y

    val perpendicular: Vector2 get() = Vector2(-y, x)
    val normalized: Vector2 get() = this / length

    infix fun dot(right: Vector2) = x * right.x + y * right.y
    infix fun reflect(surfaceNormal: Vector2): Vector2 = this - surfaceNormal * (this dot surfaceNormal) * 2.0

    val yx get() = Vector2(y, x)
    val xx get() = Vector2(x, x)
    val yy get() = Vector2(y, y)
    val xy0 get() = Vector3(x, y, 0.0)
    val xy01 get() = Vector4(x, y, 0.0, 1.0)

    fun vector3(x: Double = this.x, y: Double = this.y, z: Double = 0.0): Vector3 {
        return Vector3(x, y, z)
    }

    operator fun invoke(x: Double = this.x, y: Double = this.y): Vector2 {
        return Vector2(x, y)
    }

    operator fun unaryMinus() = Vector2(-x, -y)
    operator fun plus(vector2: Vector2) = Vector2(x + vector2.x, y + vector2.y)
    operator fun minus(vector2: Vector2) = Vector2(x - vector2.x, y - vector2.y)
    operator fun times(d: Double) = Vector2(x * d, y * d)
    operator fun times(v: Vector2) = Vector2(x * v.x, y * v.y)

    operator fun div(d: Double) = Vector2(x / d, y / d)
    operator fun div(d: Vector2) = Vector2(x / d.x, y / d.y)

    companion object {
        val ZERO = Vector2(0.0, 0.0)
        val ONE = Vector2(1.0, 1.0)
        val UNIT_X = Vector2(1.0, 0.0)
        val UNIT_Y = Vector2(0.0, 1.0)
        val INFINITY = Vector2(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
    }
}

operator fun Double.times(v: Vector2) = v * this

fun min(a: Vector2, b: Vector2): Vector2 = Vector2(min(a.x, b.x), min(a.y, b.y))
fun max(a: Vector2, b: Vector2): Vector2 = Vector2(max(a.x, b.x), max(a.y, b.y))