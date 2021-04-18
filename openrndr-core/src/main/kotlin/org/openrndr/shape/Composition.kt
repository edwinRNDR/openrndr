package org.openrndr.shape

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.LineCap
import org.openrndr.draw.LineJoin
import org.openrndr.draw.ShadeStyle
import org.openrndr.math.Matrix44
import javax.sound.sampled.*
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

    /**
     * parent node
     */
    var parent: CompositionNode? = null

    /**
     * local transform
     */
    open var transform = Matrix44.IDENTITY

    var fill: CompositionColor = InheritColor
    var stroke: CompositionColor = InheritColor
    var strokeWeight: CompositionStrokeWeight = InheritStrokeWeight
    var lineCap: CompositionLineCap = InheritLineCap
    var lineJoin: CompositionLineJoin = InheritLineJoin
    var miterlimit: CompositionMiterlimit = InheritMiterlimit
    var strokeOpacity: CompositionStrokeOpacity = InheritStrokeOpacity
    var fillOpacity: CompositionFillOpacity = InheritFillOpacity
    var opacity: CompositionOpacity = InheritOpacity

    /**
     * node attributes, these are used for loading and saving to SVG
     */
    var attributes = mutableMapOf<String, String?>()

    var shadeStyle: CompositionShadeStyle = InheritShadeStyle

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

// TODO: Where on earth do I declare default values for these?

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
            it.fill = fill
            it.stroke = stroke
            it.strokeWeight = strokeWeight
            it.transform = transform(this)
            it.id = id
        }
    }

    /**
     * apply transforms of all ancestor nodes and return a new detached shape node with identity transform and transformed Shape
     */
    fun flatten(): ShapeNode {
        return ShapeNode(shape.transform(transform(this))).also {
            it.fill = Color(effectiveFill)
            it.stroke = Color(effectiveStroke)
            it.strokeWeight = StrokeWeight(effectiveStrokeWeight ?: 0.0)
            it.transform = Matrix44.IDENTITY
            it.id = id
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
            it.shape = shape
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
    override val bounds: Rectangle
        get() = Rectangle.EMPTY
}

/**
 * A [CompositionNode] that functions as a group node
 */
open class GroupNode(val children: MutableList<CompositionNode> = mutableListOf()) : CompositionNode() {
    override val bounds: Rectangle
        get() {
            val b = children.map { it.bounds }.bounds
            return b
        }

    fun copy(id: String? = this.id, parent: CompositionNode? = null, transform: Matrix44 = this.transform, fill: CompositionColor = this.fill, stroke: CompositionColor = this.stroke, children: MutableList<CompositionNode> = this.children): GroupNode {
        return GroupNode(children).also {
            it.id = id
            it.parent = parent
            it.transform = transform
            it.fill = fill
            it.stroke = stroke
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

/**
 * default composition bounds
 */
val DefaultCompositionBounds = Rectangle(0.0, 0.0, 2676.0, 2048.0)

@Deprecated("complicated semantics")
class GroupNodeStop(children: MutableList<CompositionNode>) : GroupNode(children)

/**
 * A vector composition.
 * @param root the root node of the composition
 * @param documentBounds the document bounds [Rectangle] of the composition, serves as a hint only
 */
class Composition(val root: CompositionNode, var documentBounds: Rectangle = DefaultCompositionBounds) {
    /**
     * svg/xml namespaces
     */
    val namespaces = mutableMapOf<String, String>()

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