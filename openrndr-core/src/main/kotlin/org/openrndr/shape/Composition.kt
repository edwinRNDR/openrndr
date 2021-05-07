package org.openrndr.shape

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.LineCap
import org.openrndr.draw.LineJoin
import org.openrndr.draw.ShadeStyle
import org.openrndr.math.*
import org.openrndr.math.transforms.*
import kotlin.math.*
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

/**
 * Describes a node in a composition
 */
sealed class CompositionNode {
    /**
     * node identifier
     */
    var id: String? = null

    var parent: CompositionNode? = null

    var transform = Matrix44.IDENTITY

    var fill: CompositionColor = InheritColor
    var stroke: CompositionColor = InheritColor
    var strokeWeight: CompositionStrokeWeight = InheritStrokeWeight
    var lineCap: CompositionLineCap = InheritLineCap
    var lineJoin: CompositionLineJoin = InheritLineJoin
    var miterlimit: CompositionMiterlimit = InheritMiterlimit
    var strokeOpacity: CompositionStrokeOpacity = InheritStrokeOpacity
    var fillOpacity: CompositionFillOpacity = InheritFillOpacity
    var opacity: CompositionOpacity = InheritOpacity

    var shadeStyle: CompositionShadeStyle = InheritShadeStyle

    /**
     * node attributes, these are used for loading and saving to SVG
     */
    var attributes = mutableMapOf<String, String?>()

    /**
     * a map that stores user data
     */
    val userData = mutableMapOf<String, Any>()

    /**
     * a [Rectangle] that describes the bounding box of the contents
     */
    abstract val bounds: Rectangle

    // The effective styles inherited/calculated from the parent nodes and the current node.
    // Nodes essentially inherit style attributes from their parent by default,
    // unless explicitly defined otherwise in the node.

    val effectiveShadeStyle: ShadeStyle?
        get() {
            return shadeStyle.let {
                when (it) {
                    is InheritShadeStyle -> parent?.effectiveShadeStyle
                    is CShadeStyle -> it.shadeStyle
                }
            }
        }

    val effectiveStroke: ColorRGBa?
        get() {
            return stroke.let {
                when (it) {
                    is InheritColor -> parent?.effectiveStroke
                    is Color -> it.color
                }
            }
        }

    val effectiveStrokeWeight: Double?
        get() {
            return strokeWeight.let {
                when (it) {
                    is InheritStrokeWeight -> parent?.effectiveStrokeWeight
                    is StrokeWeight -> it.weight
                }
            }
        }

    val effectiveLineCap: LineCap?
        get() {
            return lineCap.let {
                when (it) {
                    is InheritLineCap -> parent?.effectiveLineCap
                    is org.openrndr.shape.LineCap -> it.cap
                }
            }
        }

    val effectiveLineJoin: LineJoin?
        get() {
            return lineJoin.let {
                when (it) {
                    is InheritLineJoin -> parent?.effectiveLineJoin
                    is org.openrndr.shape.LineJoin -> it.join
                }
            }
        }

    val effectiveMiterlimit: Double?
        get() {
            return miterlimit.let {
                when (it) {
                    is InheritMiterlimit -> parent?.effectiveMiterlimit
                    is Miterlimit -> it.limit
                }
            }
        }

    val effectiveStrokeOpacity: Double?
        get() {
            return strokeOpacity.let {
                when (it) {
                    is InheritStrokeOpacity -> parent?.effectiveStrokeOpacity
                    is StrokeOpacity -> it.strokeOpacity
                }
            }
        }

    val effectiveFill: ColorRGBa?
        get() {
            return fill.let {
                when (it) {
                    is InheritColor -> parent?.effectiveFill ?: ColorRGBa.BLACK
                    is Color -> it.color
                }
            }
        }

    val effectiveFillOpacity: Double?
        get() {
            return fillOpacity.let {
                when (it) {
                    is InheritFillOpacity -> parent?.effectiveFillOpacity
                    is FillOpacity -> it.fillOpacity
                }
            }
        }

    val effectiveOpacity: Double?
        get() {
            return opacity.let {
                when (it) {
                    is InheritOpacity -> parent?.effectiveOpacity
                    is Opacity -> it.opacity
                }
            }
        }

    val effectiveTransform: Matrix44
        get() {
            return if (transform === Matrix44.IDENTITY) {
                parent?.effectiveTransform ?: Matrix44.IDENTITY
            } else {
                transform * (parent?.effectiveTransform ?: Matrix44.IDENTITY)
            }
        }
}

infix fun KMutableProperty0<CompositionShadeStyle>.`=`(shadeStyle: ShadeStyle?) = this.set(CShadeStyle(shadeStyle))
infix fun KMutableProperty0<CompositionColor>.`=`(color: ColorRGBa?) = this.set(Color(color))
@JvmName("=CompositionStrokeWeight")
infix fun KMutableProperty0<CompositionStrokeWeight>.`=`(weight: Double) = this.set(StrokeWeight(weight))
infix fun KMutableProperty0<CompositionLineCap>.`=`(cap: LineCap) = this.set(LineCap(cap))
infix fun KMutableProperty0<CompositionLineJoin>.`=`(join: LineJoin) = this.set(LineJoin(join))
@JvmName("=CompositionMiterlimit")
infix fun KMutableProperty0<CompositionMiterlimit>.`=`(limit: Double) = this.set(Miterlimit(limit))
@JvmName("=CompositionStrokeOpacity")
infix fun KMutableProperty0<CompositionStrokeOpacity>.`=`(strokeOpacity: Double) = this.set(StrokeOpacity(strokeOpacity))
@JvmName("=CompositionFillOpacity")
infix fun KMutableProperty0<CompositionFillOpacity>.`=`(fillOpacity: Double) = this.set(FillOpacity(fillOpacity))
@JvmName("=CompositionOpacity")
infix fun KMutableProperty0<CompositionOpacity>.`=`(opacity: Double) = this.set(Opacity(opacity))

operator fun KMutableProperty0<CompositionShadeStyle>.setValue(thisRef: Any?, property: KProperty<*>, value: ShadeStyle) {
    this.set(CShadeStyle(value))
}

// Cascading classes

sealed class CompositionColor
object InheritColor : CompositionColor()
data class Color(val color: ColorRGBa?) : CompositionColor()

sealed class CompositionShadeStyle
object InheritShadeStyle : CompositionShadeStyle()
data class CShadeStyle(val shadeStyle: ShadeStyle?) : CompositionShadeStyle()

sealed class CompositionStrokeWeight
object InheritStrokeWeight : CompositionStrokeWeight()
data class StrokeWeight(val weight: Double) : CompositionStrokeWeight()

sealed class CompositionLineCap
object InheritLineCap : CompositionLineCap()
data class LineCap(val cap: LineCap) : CompositionLineCap()

sealed class CompositionLineJoin
object InheritLineJoin : CompositionLineJoin()
data class LineJoin(val join: LineJoin) : CompositionLineJoin()

sealed class CompositionMiterlimit
object InheritMiterlimit : CompositionMiterlimit()
data class Miterlimit(val limit: Double) : CompositionMiterlimit()

sealed class CompositionStrokeOpacity
object InheritStrokeOpacity : CompositionStrokeOpacity()
data class StrokeOpacity(val strokeOpacity: Double) : CompositionStrokeOpacity()

sealed class CompositionFillOpacity
object InheritFillOpacity : CompositionFillOpacity()
data class FillOpacity(val fillOpacity: Double) : CompositionFillOpacity()

sealed class CompositionOpacity
object InheritOpacity : CompositionOpacity()
data class Opacity(val opacity: Double) : CompositionOpacity()

private fun transform(node: CompositionNode): Matrix44 =
        (node.parent?.let { transform(it) } ?: Matrix44.IDENTITY) * node.transform

/**
 * a [CompositionNode] that holds a single image [ColorBuffer]
 */
class ImageNode(var image: ColorBuffer, var x: Double, var y: Double, var width: Double, var height: Double) : CompositionNode() {
    override val bounds: Rectangle
        get() = Rectangle(0.0, 0.0, width, height).contour.transform(transform(this)).bounds
}

/**
 * a [CompositionNode] that holds a single [Shape]
 */
class ShapeNode(var shape: Shape) : CompositionNode() {
    override val bounds: Rectangle
        get() {
            val t = effectiveTransform
            return if (t === Matrix44.IDENTITY) {
                shape.bounds
            } else {
                shape.bounds.contour.transform(t).bounds
            }
        }

    /**
     * apply transforms of all ancestor nodes and return a new detached ShapeNode with conflated transform
     */
    fun conflate(): ShapeNode {
        return ShapeNode(shape).also {
            it.id = id
            it.parent = parent
            it.transform = transform(this)
            it.fill = fill
            it.stroke = stroke
            it.strokeWeight = strokeWeight
            it.lineCap = lineCap
            it.lineJoin = lineJoin
            it.miterlimit = miterlimit
            it.strokeOpacity = strokeOpacity
            it.fillOpacity = fillOpacity
            it.opacity = opacity
            it.shadeStyle = shadeStyle
            it.attributes = attributes
        }
    }

    /**
     * apply transforms of all ancestor nodes and return a new detached shape node with identity transform and transformed Shape
     */
    fun flatten(): ShapeNode {
        return ShapeNode(shape.transform(transform(this))).also {
            it.id = id
            it.parent = parent
            it.transform = Matrix44.IDENTITY
            it.fill = Color(effectiveFill)
            it.stroke = Color(effectiveStroke)
            it.strokeWeight = StrokeWeight(effectiveStrokeWeight ?: 0.0)
            // TODO! Use effective values?
            it.lineCap = lineCap
            it.lineJoin = lineJoin
            it.miterlimit = miterlimit
            it.strokeOpacity = strokeOpacity
            it.fillOpacity = fillOpacity
            it.opacity = opacity
            it.shadeStyle = shadeStyle
            it.attributes = attributes
        }
    }

    fun copy(id: String? = this.id, parent: CompositionNode? = null, transform: Matrix44 = this.transform, fill: CompositionColor = this.fill, stroke: CompositionColor = this.stroke, shape: Shape = this.shape): ShapeNode {
        return ShapeNode(shape).also {
            it.id = id
            it.parent = parent
            it.transform = transform
            it.fill = fill
            it.stroke = stroke
            it.strokeWeight = strokeWeight
            it.lineCap = lineCap
            it.lineJoin = lineJoin
            it.miterlimit = miterlimit
            it.strokeOpacity = strokeOpacity
            it.fillOpacity = fillOpacity
            it.opacity = opacity
            it.shadeStyle = shadeStyle
            it.attributes = attributes
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShapeNode) return false
        if (shape != other.shape) return false
        return true
    }

    override fun hashCode(): Int {
        return shape.hashCode()
    }

    /**
     * the local [Shape] with the [effectiveTransform] applied to it
     */
    val effectiveShape
        get() = shape.transform(effectiveTransform)
}

/**
 * a [CompositionNode] that holds a single text
 */
data class TextNode(var text: String, var contour: ShapeContour?) : CompositionNode() {
    // TODO: This should not be Rectangle.EMPTY
    override val bounds: Rectangle
        get() = Rectangle.EMPTY
}

/**
 * A [CompositionNode] that functions as a group node
 */
open class GroupNode(open val children: MutableList<CompositionNode> = mutableListOf()) : CompositionNode() {
    override val bounds: Rectangle
        get() {
            return children.map { it.bounds }.bounds
        }

    // TODO! Should this use a data class instead?
    fun copy(id: String? = this.id, parent: CompositionNode? = null, transform: Matrix44 = this.transform, fill: CompositionColor = this.fill, stroke: CompositionColor = this.stroke, children: MutableList<CompositionNode> = this.children): GroupNode {
        return GroupNode(children).also {
            it.id = id
            it.parent = parent
            it.transform = transform
            it.fill = fill
            it.stroke = stroke
            it.strokeWeight = strokeWeight
            it.lineCap = lineCap
            it.lineJoin = lineJoin
            it.miterlimit = miterlimit
            it.strokeOpacity = strokeOpacity
            it.fillOpacity = fillOpacity
            it.opacity = opacity
            it.shadeStyle = shadeStyle
            it.attributes = attributes
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupNode) return false

        if (children != other.children) return false
        return true
    }

    override fun hashCode(): Int {
        return children.hashCode()
    }

}

val defaultCompositionDimensions = CompositionDimensions(0.0, 0.0, 768.0, 576.0)

/** Alignment options for aspect ratio */
enum class Align {
    NONE,
    MIN,
    MID,
    MAX,
}

enum class MeetOrSlice {
    MEET,
    SLICE
}

/** Alignment for each axis */
data class Alignment(val x: Align, val y: Align, val meetOrSlice: MeetOrSlice) {
    init {
        // This is how we're handling the situation where only one of the Align values is NONE but the other isn't.
        // We're just doing this to get away with the simplicity of having an alignment option for both axes
        // while also conforming to the SVG spec.
        require(
            x != Align.NONE && y != Align.NONE
                || x == Align.NONE && y == Align.NONE
        ) { "Either both or none of the Alignment values must be Align.NONE!" }    }
}

@Deprecated("complicated semantics")
class GroupNodeStop(children: MutableList<CompositionNode>) : GroupNode(children)

/**
 * A vector composition.
 * @param root the root node of the composition
 * @param bounds the dimensions of the composition
 */
class Composition(val root: CompositionNode, var bounds: CompositionDimensions = defaultCompositionDimensions) {

    /** SVG/XML namespaces */
    val namespaces = mutableMapOf<String, String>()

    /** Unitless viewbox */
    var viewBox: Rectangle? = null

    /** Specifies how the [Composition] scales up */
    var preserveAspectRatio: Alignment = Alignment(Align.MID, Align.MID, MeetOrSlice.MEET)

    fun findShapes() = root.findShapes()
    fun findShape(id: String): ShapeNode? {
        return (root.findTerminals { it is ShapeNode && it.id == id }).firstOrNull() as? ShapeNode
    }

    fun findImages() = root.findImages()
    fun findImage(id: String): ImageNode? {
        return (root.findTerminals { it is ImageNode && it.id == id }).firstOrNull() as? ImageNode
    }

    fun findGroups(): List<GroupNode> = root.findGroups()
    fun findGroup(id: String): GroupNode? {
        return (root.findTerminals { it is GroupNode && it.id == id }).firstOrNull() as? GroupNode
    }

    fun clear() = (root as? GroupNode)?.children?.clear()

    /**
     * Calculates effective viewport transformation using [viewBox] and [preserveAspectRatio].
     * As per [the SVG 2.0 spec](https://svgwg.org/svg2-draft/single-page.html#coords-ComputingAViewportsTransform)
     */
    internal fun calculateViewportTransform(): Matrix44 {
        return when (viewBox) {
            Rectangle.EMPTY, null -> {
                // The intent is to not display the element
                Matrix44.ZERO
            }
            is Rectangle -> {
                val vbCorner = viewBox!!.corner
                val vbDims = viewBox!!.dimensions
                val eCorner = bounds.position.vector2
                val eDims = bounds.dimensions.vector2
                val (xAlign, yAlign, meetOrSlice) = preserveAspectRatio

                val scale = (eDims / vbDims).let {
                    if (xAlign != Align.NONE && yAlign != Align.NONE) {
                        if (meetOrSlice == MeetOrSlice.MEET) {
                            Vector2(min(it.x, it.y))
                        } else {
                            Vector2(max(it.x, it.y))
                        }
                    } else {
                        it
                    }
                }

                val translate = (eCorner - (vbCorner * scale)).let {
                    val dx = when (xAlign) {
                        Align.MAX -> eDims.x - vbDims.x * scale.x
                        Align.MID -> (eDims.x - vbDims.x * scale.x) / 2
                        else -> 0.0
                    }
                    val dy = when (yAlign) {
                        Align.MAX -> eDims.y - vbDims.y * scale.y
                        Align.MID -> (eDims.y - vbDims.y * scale.y) / 2
                        else -> 0.0
                    }
                    it + Vector2(dx, dy)
                }

                buildTransform {
                    translate(translate)
                    scale(scale.x, scale.y, 1.0)
                }
            }
            else -> {
                Matrix44.IDENTITY
            }
        }
    }
}

/**
 * remove node from its parent [CompositionNode]
 */
fun CompositionNode.remove() {
    require(parent != null) { "parent is null" }
    val parentGroup = (parent as? GroupNode)
    if (parentGroup != null) {
        val filtered = parentGroup.children.filter {
            it != this
        }
        parentGroup.children.clear()
        parentGroup.children.addAll(filtered)
    }
    parent = null
}

fun CompositionNode.findTerminals(filter: (CompositionNode) -> Boolean): List<CompositionNode> {
    val result = mutableListOf<CompositionNode>()
    fun find(node: CompositionNode) {
        when (node) {
            is GroupNode -> node.children.forEach { find(it) }
            else -> if (filter(node)) {
                result.add(node)
            }
        }
    }
    find(this)
    return result
}

fun CompositionNode.findAll(filter: (CompositionNode) -> Boolean): List<CompositionNode> {
    val result = mutableListOf<CompositionNode>()
    fun find(node: CompositionNode) {
        if (filter(node)) {
            result.add(node)
        }
        if (node is GroupNode) {
            node.children.forEach { find(it) }
        }
    }
    find(this)
    return result
}

/**
 * find all descendant [ShapeNode] nodes, including potentially this node
 * @return a [List] of [ShapeNode] nodes
 */
fun CompositionNode.findShapes(): List<ShapeNode> = findTerminals { it is ShapeNode }.map { it as ShapeNode }

/**
 * find all descendant [ImageNode] nodes, including potentially this node
 * @return a [List] of [ImageNode] nodes
 */
fun CompositionNode.findImages(): List<ImageNode> = findTerminals { it is ImageNode }.map { it as ImageNode }

/**
 * find all descendant [GroupNode] nodes, including potentially this node
 * @return a [List] of [GroupNode] nodes
 */
fun CompositionNode.findGroups(): List<GroupNode> = findAll { it is GroupNode }.map { it as GroupNode }

/**
 * visit this [CompositionNode] and all descendant nodes and execute [visitor]
 */
fun CompositionNode.visitAll(visitor: (CompositionNode.() -> Unit)) {
    visitor()
    if (this is GroupNode) {
        for (child in children) {
            child.visitAll(visitor)
        }
    }
}

/**
 * UserData delegate
 */
class UserData<T : Any>(
        val name: String, val initial: T
) {
    @Suppress("USELESS_CAST", "UNCHECKED_CAST")
    operator fun getValue(node: CompositionNode, property: KProperty<*>): T {
        val value: T? = node.userData[name] as? T
        return value ?: initial
    }

    operator fun setValue(stylesheet: CompositionNode, property: KProperty<*>, value: T) {
        stylesheet.userData[name] = value
    }
}

@Deprecated("complicated semantics")
fun CompositionNode.filter(filter: (CompositionNode) -> Boolean): CompositionNode? {
    val f = filter(this)

    if (!f) {
        return null
    }

    if (this is GroupNode) {
        val copies = mutableListOf<CompositionNode>()
        children.forEach {
            val filtered = it.filter(filter)
            if (filtered != null) {
                when (filtered) {
                    is ShapeNode -> {
                        copies.add(filtered.copy(parent = this))
                    }
                    is GroupNode -> {
                        copies.add(filtered.copy(parent = this))
                    }
                }
            }
        }
        return GroupNode(children = copies)
    } else {
        return this
    }
}

@Deprecated("complicated semantics")
fun CompositionNode.map(mapper: (CompositionNode) -> CompositionNode): CompositionNode {
    val r = mapper(this)
    return when (r) {
        is GroupNodeStop -> {
            r.copy().also { copy ->
                copy.children.forEach {
                    it.parent = copy
                }
            }
        }
        is GroupNode -> {
            val copy = r.copy(children = r.children.map { it.map(mapper) }.toMutableList())
            copy.children.forEach {
                it.parent = copy
            }
            copy
        }
        else -> r
    }
}