package org.openrndr.kartifex.utils.regions

import io.lacuna.artifex.utils.Scalars
import org.openrndr.kartifex.*
import org.openrndr.kartifex.utils.Intersections
import org.openrndr.kartifex.utils.SweepQueue

object Split {
    fun split(a: Region2, b: Region2): Result {
        val queues: Array<SweepQueue<Curve2>> = arrayOf(SweepQueue(), SweepQueue())
        add(a, queues[0])
        add(b, queues[1])
        val union = VertexUnion()
        val intersections: MutableMap<Curve2, utils.DoubleAccumulator> =
            mutableMapOf()
        val cs: Array<Curve2?> = arrayOfNulls(2)
        while (true) {
            val idx: Int = SweepQueue.next(*queues)
            cs[idx] = queues[idx].take()
            if (cs[idx] == null) {
                break
            }
            intersections.put(cs[idx]?:error("null"), utils.DoubleAccumulator())
            for (c in queues[1 - idx].active()) {
                cs[1 - idx] = c
                val ts: Array<Vec2> = cs[0]!!.intersections(cs[1]!!)
                for (i in ts.indices) {
                    //System.out.println(ts.length + " " + ts[i] + " " + cs[0].position(ts[i].x) + " " + cs[0].position(ts[i].x).sub(cs[1].position(ts[i].y)).length() + " " + ts[i].sub(ts[max(0, i - 1)]));
                    val t0: Double = ts[i].x
                    val t1: Double = ts[i].y
                    intersections.get(cs[0])?.add(t0)
                    intersections.get(cs[1])?.add(t1)
                    val p0: Vec2 = cs[0]!!.position(t0)
                    val p1: Vec2 = cs[1]!!.position(t1)
                    union.join(p0, p1)
                }
            }
        }
        val deduped: Map<Curve2, utils.DoubleAccumulator> =
            intersections.mapValues { (c: Curve2, acc: utils.DoubleAccumulator) ->
                dedupe(
                    c,
                    acc,
                    union
                )
            }
        return Result(
            split(a, deduped, union),
            split(b, deduped, union),
            union.roots()
        )
    }

    private fun split(
        region: Region2,
        splits: Map<Curve2, utils.DoubleAccumulator>,
        union: VertexUnion
    ): Region2 {
        return Region2(
            region.rings.mapNotNull { ring: Ring2 ->
                split(
                    ring,
                    splits,
                    union
                )
            }.toTypedArray()
        )
    }

    private fun dedupe(
        c: Curve2,
        acc: utils.DoubleAccumulator,
        union: VertexUnion
    ): utils.DoubleAccumulator {
        val ts: DoubleArray = acc.toArray()
        ts.sort()
        val result: utils.DoubleAccumulator = utils.DoubleAccumulator()
        for (i in ts.indices) {
            val t0: Double = if (result.size() == 0) 0.0 else result.last()
            val t1 = ts[i]
            if (Scalars.equals(t0, t1, Intersections.PARAMETRIC_EPSILON)
                || Vec.equals(
                    c.position(t0),
                    c.position(t1),
                    Intersections.SPATIAL_EPSILON
                )
            ) {
                union.join(c.position(t0), c.position(t1))
            } else if (Scalars.equals(t1, 1.0, Intersections.PARAMETRIC_EPSILON)
                || Vec.equals(
                    c.position(t1),
                    c.end(),
                    Intersections.SPATIAL_EPSILON
                )
            ) {
                union.join(c.position(t1), c.end())
            } else {
                result.add(t1)
            }
        }
        return result
    }

    private fun split(
        r: Ring2,
        splits: Map<Curve2, utils.DoubleAccumulator>,
        union: VertexUnion
    ): Ring2? {
        val curves: MutableList<Curve2> = mutableListOf()
        for (c in r.curves) {
            val acc: utils.DoubleAccumulator = splits[c]!!
            for (cp in c.split(acc.toArray())) {
                val cpa = union.adjust(cp)
                if (cpa != null) {
                    curves.add(cpa)
                }
            }
        }
        return if (curves.size == 0) null else Ring2(curves)
    }

    private fun add(region: Region2, queue: SweepQueue<Curve2>) {
        for (r in region.rings) {
            for (c in r.curves) {
                queue.add(c, c.start().x, c.end().x)
            }
        }
    }

    internal class VertexUnion {
        private val parent: MutableMap<Vec2, Vec2> = mutableMapOf()
        private val roots: MutableSet<Vec2> = mutableSetOf()
        fun join(a: Vec2, b: Vec2) {
            @Suppress("NAME_SHADOWING") var a: Vec2 = a
            @Suppress("NAME_SHADOWING") var b: Vec2 = b
            a = adjust(a)
            b = adjust(b)
            val cmp: Int = a.compareTo(b)
            if (cmp < 0) {
                parent[b] = a
                roots.add(a)
            } else if (cmp > 0) {
                parent[a] = b
                roots.add(b)
            } else {
                roots.add(b)
            }
        }

        fun adjust(p: Vec2): Vec2 {
            var curr: Vec2 = p
            while (true) {
                val next: Vec2? = parent.get(curr)
                if (next == null) {
                    if (curr != p) {
                        parent.put(p, curr)
                    }
                    return curr
                }
                curr = next
            }
        }

        fun adjust(c: Curve2): Curve2? {
            val start: Vec2 = adjust(c.start())
            val end: Vec2 = adjust(c.end())
            return if (start == end) null else c.endpoints(start, end)
        }

        fun roots(): Set<Vec2> {
            return (roots - parent.keys)
        }
    }

    class Result(a: Region2, b: Region2, splits: Set<Vec2>) {
        val a: Region2
        val b: Region2
        val splits: Set<Vec2>

        init {
            this.a = a
            this.b = b
            this.splits = splits
        }
    }
}
