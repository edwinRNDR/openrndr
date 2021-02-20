package org.openrndr.math

import kotlin.math.acos
import kotlin.math.atan2

/**
 * Ref: https://en.wikipedia.org/wiki/Spherical_coordinate_system
 *
 * The poles (phi) are at the positive and negative y axis.
 * The equator starts at positive z.
 */

private const val EPS = 0.000001

data class Spherical(val theta: Double, val phi: Double, val radius: Double) : LinearType<Spherical> {

    fun makeSafe() = Spherical(
            theta,
            clamp(phi, EPS, 180 - EPS),
            radius
    )

    companion object {
        fun fromVector(vector: Vector3): Spherical {
            val r = vector.length
            return Spherical(
                    if (r == 0.0) 0.0 else Math.toDegrees(atan2(vector.x, vector.z)),
                    if (r == 0.0) 0.0 else Math.toDegrees(acos(clamp(vector.y / r, -1.0, 1.0))),
                    r)
        }
    }

    val cartesian: Vector3
        get() {
            return Vector3.fromSpherical(this)
        }

    override operator fun plus(s: Spherical) = Spherical(theta + s.theta, phi + s.phi, radius + s.radius)
    override operator fun minus(s: Spherical) = Spherical(theta - s.theta, phi - s.phi, radius - s.radius)
    operator fun times(s: Spherical) = Spherical(theta * s.theta, phi * s.phi, radius * s.radius)
    override operator fun times(s: Double) = Spherical(theta * s, phi * s, radius * s)
    override operator fun div(s: Double) = Spherical(theta / s, phi / s, radius / s)
}
