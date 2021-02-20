package org.openrndr.shape

import org.openrndr.math.*
import org.openrndr.shape.internal.BezierCubicSampler3D
import org.openrndr.shape.internal.BezierQuadraticSampler3D

private fun sumDifferences(points: List<Vector3>) =
        (0 until points.size - 1).sumByDouble { (points[it] - points[it + 1]).length }


class SegmentProjection3D(val segment: Segment3D, val projection: Double, val distance: Double, val point: Vector3)

class Segment3D {
    val start: Vector3
    val end: Vector3

    /**
     * control points, zero-length iff the segment is linear
     */
    val control: Array<Vector3>

    val linear: Boolean get() = control.isEmpty()

    private var lut: List<Vector3>? = null

    /**
     * Linear segment constructor
     * @param start starting point of the segment
     * @param end end point of the segment
     */
    constructor(start: Vector3, end: Vector3) {
        this.start = start
        this.end = end
        this.control = emptyArray()
    }

    /**
     * Quadratic bezier segment constructor
     * @param start starting point of the segment
     * @param c0 control point
     * @param end end point of the segment
     */
    constructor(start: Vector3, c0: Vector3, end: Vector3) {
        this.start = start
        this.control = arrayOf(c0)
        this.end = end
    }

    /**
     * Cubic bezier segment constructor
     * @param start starting point of the segment
     * @param c0 first control point
     * @param c1 second control point
     * @param end end point of the segment
     */
    constructor(start: Vector3, c0: Vector3, c1: Vector3, end: Vector3) {
        this.start = start
        this.control = arrayOf(c0, c1)
        this.end = end
    }

    constructor(start: Vector3, control: Array<Vector3>, end: Vector3) {
        this.start = start
        this.control = control
        this.end = end
    }

    fun lut(size: Int = 100): List<Vector3> {
        if (lut == null || lut!!.size != size) {
            lut = (0..size).map { position((it.toDouble() / size)) }
        }
        return lut!!
    }

    fun on(point: Vector3, error: Double = 5.0): Double? {
        val lut = lut()
        var hits = 0
        var t = 0.0
        for (i in 0 until lut.size) {
            if ((lut[i] - point).squaredLength < error * error) {
                hits++
                t += i.toDouble() / lut.size
            }
        }
        return if (hits > 0) t / hits else null
    }

    private fun closest(points: List<Vector3>, query: Vector3): Pair<Int, Vector3> {
        var closestIndex = 0
        var closestValue = points[0]

        var closestDistance = Double.POSITIVE_INFINITY
        for (i in 0 until points.size) {
            val distance = (points[i] - query).squaredLength
            if (distance < closestDistance) {
                closestIndex = i
                closestValue = points[i]
                closestDistance = distance
            }
        }
        return Pair(closestIndex, closestValue)
    }

    fun project(point: Vector3): SegmentProjection3D {
        // based on bezier.js
        val lut = lut()
        val l = (lut.size - 1).toDouble()
        val closest = closest(lut, point)

        var closestDistance = (point - closest.second).squaredLength

        if (closest.first == 0 || closest.first == lut.size - 1) {
            val t = closest.first.toDouble() / l
            return SegmentProjection3D(this, t, closestDistance, closest.second)
        } else {
            val t1 = (closest.first - 1) / l
            val t2 = (closest.first + 1) / l
            val step = 0.1 / l

            var t = t1
            var ft = t1

            while (t < t2 + step) {
                val p = position(t)
                val d = (p - point).squaredLength
                if (d < closestDistance) {
                    closestDistance = d
                    ft = t
                }
                t += step
            }
            val p = position(ft)
            return SegmentProjection3D(this, ft, closestDistance, p)
        }
    }

    fun transform(transform: Matrix44): Segment3D {
        val tstart = (transform * (start.xyz1)).div
        val tend = (transform * (end.xyz1)).div
        val tcontrol = when (control.size) {
            2 -> arrayOf((transform * control[0].xyz1).div, (transform * control[1].xyz1).div)
            1 -> arrayOf((transform * control[0].xyz1).div)
            else -> emptyArray()
        }
        return Segment3D(tstart, tcontrol, tend)
    }

    fun sampleAdaptive(distanceTolerance: Double = 0.5): List<Vector3> = when (control.size) {
        0 -> listOf(start, end)
        1 -> BezierQuadraticSampler3D().apply { this.distanceTolerance = distanceTolerance }.sample(start, control[0], end).first
        2 -> BezierCubicSampler3D().apply { this.distanceTolerance = distanceTolerance }.sample(start, control[0], control[1], end).first
        else -> throw RuntimeException("unsupported number of control points")
    }

    fun sampleAdaptiveNormals(distanceTolerance: Double = 0.5): Pair<List<Vector3>, List<Vector3>> = when (control.size) {
        0 -> Pair(listOf(start, end), listOf(end - start, end - start))
        1 -> BezierQuadraticSampler3D().apply { this.distanceTolerance = distanceTolerance }.sample(start, control[0], end)
        2 -> BezierCubicSampler3D().apply { this.distanceTolerance = distanceTolerance }.sample(start, control[0], control[1], end)
        else -> throw RuntimeException("unsupported number of control points")
    }

    val length: Double
        get() = when (control.size) {
            0 -> (end - start).length
            1, 2 -> sumDifferences(sampleAdaptive())
            else -> throw RuntimeException("unsupported number of control points")
        }

    fun position(ut: Double): Vector3 {
        val t = ut.coerceIn(0.0, 1.0)
        return when (control.size) {
            0 -> Vector3(start.x * (1.0 - t) + end.x * t, start.y * (1.0 - t) + end.y * t, start.z * (1.0 - t) + end.z * t)
            1 -> bezier(start, control[0], end, t)
            2 -> bezier(start, control[0], control[1], end, t)
            else -> throw RuntimeException("unsupported number of control points")
        }
    }



    fun direction(): Vector3 {
        return (start - end).normalized
    }

    fun direction(t: Double): Vector3 {
        return derivative(t).normalized
    }

    fun extrema(): List<Double> {
        val dpoints = dpoints()
        return when {
            linear -> emptyList()
            control.size == 1 -> {
                val xRoots = roots(dpoints[0].map { it.x })
                val yRoots = roots(dpoints[0].map { it.y })
                (xRoots + yRoots).distinct().sorted().filter { it in 0.0..1.0 }
            }
            control.size == 2 -> {
                val xRoots = roots(dpoints[0].map { it.x }) + roots(dpoints[1].map { it.x })
                val yRoots = roots(dpoints[0].map { it.y }) + roots(dpoints[1].map { it.y })
                (xRoots + yRoots).distinct().sorted().filter { it in 0.0..1.0 }
            }
            else -> throw RuntimeException("not supported")
        }
    }

    fun extremaPoints(): List<Vector3> = extrema().map { position(it) }


    private fun dpoints(): List<List<Vector3>> {
        val points = listOf(start, *control, end)
        var d = points.size
        var c = d - 1
        val dpoints = mutableListOf<List<Vector3>>()
        var p = points
        while (d > 1) {
            val list = mutableListOf<Vector3>()
            for (j in 0 until c) {
                list.add(Vector3(c * (p[j + 1].x - p[j].x), c * (p[j + 1].y - p[j].y), c * (p[j + 1].z - p[j].z)))
            }
            dpoints.add(list)
            p = list
            d--
            c--
        }
        return dpoints
    }


    /**
     * Cubic version of segment
     */
    val cubic: Segment3D
        get() = when (control.size) {
            2 -> this
            1 -> {
                Segment3D(start, start * (1.0 / 3.0) + control[0] * (2.0 / 3.0), control[0] * (2.0 / 3.0) + end * (1.0 / 3.0), end)
            }
            else -> throw RuntimeException("cannot convert to cubic segment")
        }

    fun derivative(t: Double): Vector3 = when {
        linear -> start - end
        control.size == 1 -> derivative(start, control[0], end, t)
        control.size == 2 -> derivative(start, control[0], control[1], end, t)
        else -> throw RuntimeException("not implemented")
    }

    val reverse: Segment3D
        get() {
            return when (control.size) {
                0 -> Segment3D(end, start)
                1 -> Segment3D(end, control[0], start)
                2 -> Segment3D(end, control[1], control[0], start)
                else -> throw RuntimeException("unsupported number of control points")
            }
        }

    fun sub(t0: Double, t1: Double): Segment3D {
        // ftp://ftp.fu-berlin.de/tex/CTAN/dviware/dvisvgm/src/Bezier.cpp
        var z0 = t0
        var z1 = t1

        if (t0 > t1) {
            z1 = t0
            z0 = t1
        }

        return when {
            z0 == 0.0 -> split(z1)[0]
            z1 == 1.0 -> split(z0)[1]
            else -> split(z0)[1].split(map(z0, 1.0, 0.0, 1.0, z1))[0]
        }
    }

    /**
     * Split the contour
     * @param t the point to split the contour at
     * @return array of parts, depending on the split point this is one or two entries long
     */
    fun split(t: Double): Array<Segment3D> {
        val u = t.coerceIn(0.0, 1.0)

        if (linear) {
            val cut = start + (end.minus(start) * u)
            return arrayOf(Segment3D(start, cut), Segment3D(cut, end))
        } else {
            when (control.size) {
                2 -> {
                    val z = u
                    val z2 = z * z
                    val z3 = z * z * z
                    val iz = 1 - z
                    val iz2 = iz * iz
                    val iz3 = iz * iz * iz

                    val lsm = Matrix44(
                        1.0, 0.0, 0.0, 0.0,
                        iz, z, 0.0, 0.0,
                        iz2, 2.0 * iz * z, z2, 0.0,
                        iz3, 3.0 * iz2 * z, 3.0 * iz * z2, z3)

                    val px = Vector4(start.x, control[0].x, control[1].x, end.x)
                    val py = Vector4(start.y, control[0].y, control[1].y, end.y)
                    val pz = Vector4(start.z, control[0].z, control[1].z, end.z)

                    val plx = lsm * px//.multiply(lsm)
                    val ply = lsm * py// py.multiply(lsm)
                    val plz = lsm * pz// py.multiply(lsm)

                    val pl0 = Vector3(plx.x, ply.x, plz.x)
                    val pl1 = Vector3(plx.y, ply.y, plz.y)
                    val pl2 = Vector3(plx.z, ply.z, plz.z)
                    val pl3 = Vector3(plx.w, ply.w, plz.w)

                    val left = Segment3D(pl0, pl1, pl2, pl3)

                    val rsm = Matrix44(
                        iz3, 3.0 * iz2 * z, 3.0 * iz * z2, z3,
                        0.0, iz2, 2.0 * iz * z, z2,
                        0.0, 0.0, iz, z,
                        0.0, 0.0, 0.0, 1.0
                    )

                    val prx = rsm * px
                    val pry = rsm * py
                    val prz = rsm * pz

                    val pr0 = Vector3(prx.x, pry.x, prz.x)
                    val pr1 = Vector3(prx.y, pry.y, prz.y)
                    val pr2 = Vector3(prx.z, pry.z, prz.z)
                    val pr3 = Vector3(prx.w, pry.w, prz.w)

                    val right = Segment3D(pr0, pr1, pr2, pr3)

                    return arrayOf(left, right)
                }
                1 -> {
                    val z = u
                    val iz = 1 - z
                    val iz2 = iz * iz
                    val z2 = z * z

                    val lsm = Matrix44(
                        1.0, 0.0, 0.0, 0.0,
                        iz, z, 0.0, 0.0,
                        iz2, 2.0 * iz * z, z2, 0.0,
                        0.0, 0.0, 0.0, 0.0)

                    val px = Vector4(start.x, control[0].x, end.x, 0.0)
                    val py = Vector4(start.y, control[0].y, end.y, 0.0)
                    val pz = Vector4(start.z, control[0].z, end.z, 0.0)

                    val plx = lsm * px
                    val ply = lsm * py
                    val plz = lsm * pz

                    val left = Segment3D(
                        Vector3(plx.x, ply.x, plz.x),
                        Vector3(plx.y, ply.y, plz.y),
                        Vector3(plx.z, ply.z, plz.z))

                    val rsm = Matrix44(
                        iz2, 2.0 * iz * z, z2, 0.0,
                        0.0, iz, z, 0.0,
                        0.0, 0.0, 1.0, 0.0,
                        0.0, 0.0, 0.0, 0.0)

                    val prx = rsm * px
                    val pry = rsm * py
                    val prz = rsm * pz

                    val right = Segment3D(
                        Vector3(prx.x, pry.x, prz.x),
                        Vector3(prx.y, pry.y, prz.y),
                        Vector3(prx.z, pry.z, prz.z))

                    return arrayOf(left, right)

                }
                else -> throw RuntimeException("not implemented")
            }
        }
    }

    override fun toString(): String {
        return "Segment(start=$start, end=$end, control=${control.contentToString()})"
    }

    fun copy(start: Vector3 = this.start, control: Array<Vector3> = this.control, end: Vector3 = this.end): Segment3D {
        return Segment3D(start, control, end)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Segment3D

        if (start != other.start) return false
        if (end != other.end) return false
        if (!control.contentEquals(other.control)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = 31 * result + end.hashCode()
        result = 31 * result + control.contentHashCode()
        return result
    }


}
