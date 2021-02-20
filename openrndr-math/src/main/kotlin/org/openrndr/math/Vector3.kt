package org.openrndr.math

import java.io.Serializable
import kotlin.math.*

/** Double-precision 3D vector. */
data class Vector3(val x: Double, val y: Double, val z: Double) : Serializable, LinearType<Vector3> {
    constructor(x: Double) : this(x, x, x)

    companion object {
        val ZERO = Vector3(0.0, 0.0, 0.0)
        val ONE = Vector3(1.0, 1.0, 1.0)
        val UNIT_X = Vector3(1.0, 0.0, 0.0)
        val UNIT_Y = Vector3(0.0, 1.0, 0.0)
        val UNIT_Z = Vector3(0.0, 0.0, 1.0)
        val INFINITY = Vector3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)

        fun fromSpherical(s: Spherical): Vector3 {

            val phi = Math.toRadians(s.phi)
            val theta = Math.toRadians(s.theta)

            val sinPhiRadius = sin(phi) * s.radius
            return Vector3(
                    sinPhiRadius * sin(theta),
                    cos(phi) * s.radius,
                    sinPhiRadius * cos(theta))
        }
    }

    val xyz0 get() = Vector4(x, y, z, 0.0)
    val xyz1 get() = Vector4(x, y, z, 1.0)

    val xy get() = Vector2(x, y)
    val yx get() = Vector2(y, x)
    val zx get() = Vector2(z, x)
    val xz get() = Vector2(x, z)

    /** Returns a normalized version of the vector. (i.e. unit vector) */
    val normalized: Vector3
        get() {
            val l = 1.0 / length
            if (l.isNaN() || l.isInfinite()) {
                return ZERO
            }
            return this * l
        }

    infix fun reflect(surfaceNormal: Vector3) = this - surfaceNormal * (this dot surfaceNormal) * 2.0

    operator fun get(i: Int): Double {
        return when (i) {
            0 -> x
            1 -> y
            2 -> z
            else -> throw RuntimeException("unsupported index")
        }
    }

    operator fun unaryMinus() = Vector3(-x, -y, -z)
    override operator fun plus(v: Vector3) = Vector3(x + v.x, y + v.y, z + v.z)
    operator fun plus(d: Double) = Vector3(x + d, y + d, z + d)
    override operator fun minus(v: Vector3) = Vector3(x - v.x, y - v.y, z - v.z)
    operator fun minus(d: Double) = Vector3(x - d, y - d, z - d)
    operator fun times(v: Vector3) = Vector3(x * v.x, y * v.y, z * v.z)
    override operator fun times(s: Double) = Vector3(x * s, y * s, z * s)
    override operator fun div(s: Double) = Vector3(x / s, y / s, z / s)
    operator fun div(v: Vector3) = Vector3(x / v.x, y / v.y, z / v.z)

    /** Calculates a dot product between this [Vector2] and [v]. */
    infix fun dot(v: Vector3) = x * v.x + y * v.y + z * v.z

    /** Calculates a cross product between this [Vector2] and [v]. */
    infix fun cross(v: Vector3) = Vector3(
            y * v.z - z * v.y,
            -(x * v.z - z * v.x),
            x * v.y - y * v.x)

    infix fun projectedOn(v: Vector3) = (this dot v) / (v dot v) * v

    /** The Euclidean length of the vector. */
    val length: Double get() = sqrt(x * x + y * y + z * z)

    /** The squared Euclidean length of the vector. */
    val squaredLength get() = x * x + y * y + z * z

    /** Casts to [DoubleArray]. */
    fun toDoubleArray() = doubleArrayOf(x, y, z)

    /** Calculates the Euclidean distance to [other]. */
    fun distanceTo(other: Vector3): Double {
        val dx = other.x - x
        val dy = other.y - y
        val dz = other.z - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Calculates the squared Euclidean distance to [other]. */
    fun squaredDistanceTo(other: Vector3): Double {
        val dx = other.x - x
        val dy = other.y - y
        val dz = other.z - z
        return dx * dx + dy * dy + dz * dz
    }

    fun mix(o: Vector3, mix: Double): Vector3 = this * (1 - mix) + o * mix

    val spherical: Spherical
        get() {
            return Spherical.fromVector(this)
        }

    /** Casts to [IntVector3]. */
    fun toInt() = IntVector3(x.toInt(), y.toInt(), z.toInt())
}

operator fun Double.times(v: Vector3) = v * this

fun min(a: Vector3, b: Vector3) = Vector3(min(a.x, b.x), min(a.y, b.y), min(a.z, b.z))
fun max(a: Vector3, b: Vector3) = Vector3(max(a.x, b.x), max(a.y, b.y), max(a.z, b.z))

fun mix(a: Vector3, b: Vector3, mix:Double): Vector3 = a * (1 - mix) + b * mix
