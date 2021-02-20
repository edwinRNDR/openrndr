@file:Suppress("unused")

package org.openrndr.binpack

import org.openrndr.math.IntVector2
import org.openrndr.shape.IntRectangle
import java.util.*
import kotlin.comparisons.compareBy
import kotlin.math.max

interface Clipper {
    fun inside(node: IntRectangle, rectangle: IntRectangle, data:Any?): Boolean
}

interface Splitter {
    fun split(node: PackNode, rectangle: IntRectangle): List<PackNode>
}

interface Orderer {
    fun order(node: PackNode, rectangle: IntRectangle): List<Int>
}

class DefaultClipper : Clipper {
    override fun inside(node: IntRectangle, rectangle: IntRectangle, data: Any?): Boolean = true
}

class DefaultOrderer : Orderer {
    override fun order(node: PackNode, rectangle: IntRectangle): List<Int> = (0 until node.children.size).toList()
}

class RandomOrderer : Orderer {
    override fun order(node: PackNode, rectangle: IntRectangle): List<Int> {
        return (0 until node.children.size).toList().apply {
            Collections.shuffle(this)
        }
    }
}

class CenterOrderer(val enclosement: IntRectangle, val reverse: Boolean = false) : Orderer {
    data class SortOption(val index: Int, val distance: Double)

    override fun order(node: PackNode, rectangle: IntRectangle): List<Int> {
        return node.children.mapIndexed { i, packNode -> SortOption(i, packNode.area.center.minus(enclosement.center).length) }.sortedWith(compareBy { it.distance })
                .map { it.index }
                .let {
                    if (reverse) it.reversed() else it
                }
    }
}

class OptimizingOrderer(val reverse: Boolean = false) : Orderer {

    data class SortOption(val index: Int, val area: Int)

    override fun order(node: PackNode, rectangle: IntRectangle): List<Int> {
        return node.children.mapIndexed { i, packNode -> SortOption(i, max(packNode.freeArea.x, packNode.freeArea.y)) }
                .sortedWith(compareBy { it.area })
                .map { it.index }
                .let {
                    if (reverse) it.reversed() else it
                }
    }
}


class DefaultSplitter : Splitter {
    override fun split(node: PackNode, rectangle: IntRectangle): List<PackNode> {
        val dw = node.area.width - rectangle.width
        val dh = node.area.height - rectangle.height

        val sanding = 0

        val lrect: IntRectangle
        val rrect: IntRectangle
        if (dw > dh) {
            lrect = IntRectangle(node.area.x, node.area.y, rectangle.width, node.area.height)
            rrect = IntRectangle(node.area.x + rectangle.width + sanding, node.area.y, node.area.width - rectangle.width - sanding, node.area.height)
        } else {
            lrect = IntRectangle(node.area.x, node.area.y, node.area.width, rectangle.height)
            rrect = IntRectangle(node.area.x, node.area.y + rectangle.height + sanding, node.area.width, node.area.height - rectangle.height - sanding)
        }
        return listOf(PackNode(lrect, node), PackNode(rrect, node))
    }
}

class CenteredBinarySplitter(
        val enclosement: IntRectangle,
        val invert: Boolean = true,
        val constraints: (node:PackNode, rectangle:IntRectangle) -> Boolean = { _, _ -> true },
        val xcon: (node:PackNode, rectangle:IntRectangle) -> Boolean = { _, _ -> true},
        val ycon: (node:PackNode, rectangle:IntRectangle) -> Boolean = { _, _ -> true}
)
    : Splitter {
    override fun split(node: PackNode, rectangle: IntRectangle): List<PackNode> {

        if (!constraints.invoke(node, rectangle)) {
            return emptyList()
        }

        // dimension deltas
        val dw = node.area.width - rectangle.width
        val dh = node.area.height - rectangle.height

        val s = (if (invert) 1 else -1).toFloat()


        val lrect: IntRectangle
        val rrect: IntRectangle
        if (dw > dh) {

            if (xcon(node, rectangle)) {
                if (s * (node.area.x - enclosement.x) > s * enclosement.width / 2) {
                    lrect = IntRectangle(node.area.x, node.area.y, rectangle.width, node.area.height)
                    rrect = IntRectangle(node.area.x + rectangle.width, node.area.y, node.area.width - rectangle.width, node.area.height)
                } else {
                    val ew = node.area.width - rectangle.width
                    rrect = IntRectangle(node.area.x, node.area.y, ew, node.area.height)
                    lrect = IntRectangle(node.area.x + ew, node.area.y, rectangle.width, node.area.height)
                }
            } else {
                return emptyList()
            }
        } else {
            if (ycon(node, rectangle)) {
                if (s * (node.area.y - enclosement.y) > s * enclosement.height / 2) {
                    lrect = IntRectangle(node.area.x, node.area.y, node.area.width, rectangle.height)
                    rrect = IntRectangle(node.area.x, node.area.y + rectangle.height, node.area.width, node.area.height - rectangle.height)
                } else {
                    val eh = node.area.height - rectangle.height
                    rrect = IntRectangle(node.area.x, node.area.y, node.area.width, eh)
                    lrect = IntRectangle(node.area.x, node.area.y + eh, node.area.width, rectangle.height)
                }
            } else {
                return emptyList()
            }
        }
        return listOf(PackNode(lrect, node), PackNode(rrect, node))
    }
}

class RandomBinarySplitter(val enclosement: IntRectangle, val invert: Boolean = true, val constraints: (node:PackNode, rectangle:IntRectangle) -> Boolean = { _, _ -> true }) : Splitter {
    override fun split(node: PackNode, rectangle: IntRectangle): List<PackNode> {

        if (!constraints.invoke(node, rectangle)) {
            return emptyList()
        }

        // dimension deltas
        val dw = node.area.width - rectangle.width
        val dh = node.area.height - rectangle.height

        val lrect: IntRectangle
        val rrect: IntRectangle
        if (dw > dh) {
            if (Math.random() < 0.5) {
                lrect = IntRectangle(node.area.x, node.area.y, rectangle.width, node.area.height)
                rrect = IntRectangle(node.area.x + rectangle.width, node.area.y, node.area.width - rectangle.width, node.area.height)
            } else {
                val ew = node.area.width - rectangle.width
                rrect = IntRectangle(node.area.x, node.area.y, ew, node.area.height)
                lrect = IntRectangle(node.area.x + ew, node.area.y, rectangle.width, node.area.height)
            }
        } else {
            if (Math.random() < 0.5) {
                lrect = IntRectangle(node.area.x, node.area.y, node.area.width, rectangle.height)
                rrect = IntRectangle(node.area.x, node.area.y + rectangle.height, node.area.width, node.area.height - rectangle.height)
            } else {
                val eh = node.area.height - rectangle.height
                rrect = IntRectangle(node.area.x, node.area.y, node.area.width, eh)
                lrect = IntRectangle(node.area.x, node.area.y + eh, node.area.width, rectangle.height)
            }
        }
        return listOf(PackNode(lrect, node), PackNode(rrect, node))
    }
}

// -- crappy conversion from Java
class GreedySplitter : Splitter {

    var horizontalBias = 0.0
    var verticalBias = 0.0

    var horizontalDivisions = 1
    var verticalDivisions = 1

    override fun split(node: PackNode, rectangle: IntRectangle): List<PackNode> {
        val hc = horizontalDivisions * 2 + 1
        val vc = verticalDivisions * 2 + 1

        val hb = (horizontalBias + 1) / 2
        val vb = (verticalBias + 1) / 2

        val leftMargin = hb * (node.area.width - rectangle.width) / horizontalDivisions
        val topMargin = vb * (node.area.height - rectangle.height) / verticalDivisions
        val rightMargin = (1 - hb) * (node.area.width - rectangle.width) / horizontalDivisions
        val bottomMargin = (1 - vb) * (node.area.height - rectangle.height) / verticalDivisions


        val rects = arrayOfNulls<IntRectangle>(hc * vc)

        var x = node.area.x.toDouble()
        var y = node.area.y.toDouble()

        val xpos = DoubleArray(hc) //{ x0, x0+leftMargin, x0+leftMargin+rectangle.width };
        val ypos = DoubleArray(vc) // { y0, y0+topMargin, y0 + topMargin + rectangle.height + sanding };
        val widths = DoubleArray(hc)
        val heights = DoubleArray(vc)
        for (i in 0 until hc) {
            xpos[i] = x

            if (i < hc / 2) {
                x += leftMargin
                widths[i] = leftMargin
            } else if (i == hc / 2) {
                x += rectangle.width
                widths[i] = rectangle.width.toDouble()

            } else {
                x += rightMargin
                widths[i] = rightMargin
            }
        }

        //

        for (i in 0 until vc) {
            ypos[i] = y
            if (i < vc / 2) {
                y += topMargin
                heights[i] = topMargin
            } else if (i == vc / 2) {
                y += rectangle.height
                heights[i] = rectangle.height.toDouble()
            } else {
                y += bottomMargin
                heights[i] = bottomMargin
            }
        }

        for (j in 0 until vc) {
            for (i in 0 until hc) {
                rects[j * hc + i] = IntRectangle(xpos[i].toInt(), ypos[j].toInt(), widths[i].toInt(), heights[j].toInt())
            }
        }

        val order = IntArray(hc * vc)
        order[0] = vc / 2 * hc + hc / 2
        var count = 0
        for (i in 1 until order.size) {
            order[i] = count
            count++
            if (count == order[0]) {
                count++
            }
        }
        return (0 until hc * vc).map {
            PackNode(rects[order[it]]!!)
        }
    }
}


fun prune(node: PackNode) {
    if (!node.taken) {
        if (!node.hasTakenDecendents()) {
            node.freeArea = IntVector2(node.area.width, node.area.height)
            node.children = emptyList()
            node.parent?.let { prune(it) }
        } else {
            val mx = node.children.map { it.freeArea.x }.maxOrNull() ?: node.freeArea.x
            val my = node.children.map { it.freeArea.y }.maxOrNull() ?: node.freeArea.y

            if (mx != node.freeArea.x || my != node.freeArea.y) {
                node.freeArea = IntVector2(mx, my)
                node.parent?.let { prune(it) }
            }
        }
    }
}

class IntPacker(
        private val clipper: Clipper = DefaultClipper(),
        private val splitter: Splitter = DefaultSplitter(),
        private val orderer: Orderer = DefaultOrderer()) {

    fun insert(node: PackNode, rectangle: IntRectangle, data: Any? = null): PackNode? {
        if (node.freeArea.x < rectangle.width || node.freeArea.y < rectangle.height) {
            return null
        }

        if (!node.isLeaf()) {
            if (!(rectangle.width <= node.area.width && rectangle.height <= node.area.height)) {
                return null
            }

            var newNode: PackNode?

            val order = orderer.order(node, rectangle)

            for (i in order) {
                if (node.children[i].freeArea.x >= rectangle.width && node.children[i].freeArea.y >= rectangle.height) {
                    newNode = insert(node.children[i], rectangle, data) //children[i].insert(rect, id);

                    val mx = node.children.map { it.freeArea.x }.maxOrNull() ?: node.freeArea.x
                    val my = node.children.map { it.freeArea.y }.maxOrNull() ?: node.freeArea.y
                    node.freeArea = IntVector2(mx, my)

                    if (newNode != null)
                        return newNode
                }
            }

            val mx = node.children.map { it.freeArea.x }.maxOrNull() ?: node.freeArea.x
            val my = node.children.map { it.freeArea.y }.maxOrNull() ?: node.freeArea.y
            node.freeArea = IntVector2(mx, my)

            return null
        } else { // -- this is a leaf node
            if (node.taken) { // -- the node is taken
                return null
            } else { // -- the node is not taken
                if (node.area.width < rectangle.width || node.area.height < rectangle.height) {
                    // area too small. cannot fit
                    return null
                } else if (node.area.width == rectangle.width && node.area.height == rectangle.height) {
                    // perfect fit!
                    if (clipper.inside(node.area, rectangle, data)) {
                        node.populate(data)
                        node.freeArea = IntVector2(0, 0)
                        return node
                    } else {
                        //node.freeArea = IntVector2(node.area.width, node.area.height)
                        return null
                    }
                } else if (node.area.width >= rectangle.width && node.area.height >= rectangle.height) {
                    // fits, but area too big => split area
                    val children = splitter.split(node, rectangle)

                    return if (children.isEmpty()) {
                        null
                    } else {
                        node.children = splitter.split(node, rectangle)
                        val result = insert(node.children[0], rectangle, data)
                        val mx = node.children.map { it.freeArea.x }
                            .maxOrNull() ?: node.freeArea.x
                        val my = node.children.map { it.freeArea.y }
                            .maxOrNull() ?: node.freeArea.y
                        node.freeArea = IntVector2(mx, my)
                        result
                    }
                } else {
                    throw RuntimeException("where am I?")
                }
            }
        }
    }
}

