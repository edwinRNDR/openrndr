package org.openrndr.shape

import org.openrndr.color.*
import org.openrndr.draw.*
import org.openrndr.math.*
import org.openrndr.math.transforms.*
import kotlin.math.*
import kotlin.reflect.*

/**
 * Describes a node in a composition
 */
sealed class CompositionNode {

    var id: String? = null

    var parent: CompositionNode? = null

    /** This CompositionNode's own style. */
    var style: Style = Style()

    /**
     * This CompositionNode's computed style.
     * Where every style attribute is obtained by
     * overwriting the Style in the following order:
     * 1. Default style attributes.
     * 2. Parent Node's computed style's inheritable attributes.
     * 3. This Node's own style attributes.
     */
    val computedStyle: Style = when (val p = parent) {
        is CompositionNode -> style inherit p.computedStyle
        else -> style
    }

    /**
     * Custom attributes to be applied to the Node in addition to the Style attributes.
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

    /**
     * Calculates the absolute transformation of the current node.
     */
    open val effectiveTransform: Matrix44
        get() = when (val p = parent) {
            is CompositionNode -> style.transform.value * p.effectiveTransform
            else -> style.transform.value
        }

    val effectiveStroke = computedStyle.stroke.value
    val effectiveStrokeOpacity = computedStyle.strokeOpacity.value
    val effectiveStrokeWeight = computedStyle.strokeWeight.value
    val effectiveMiterLimit = computedStyle.miterLimit.value
    val effectiveLineCap = computedStyle.lineCap.value
    val effectiveLineJoin = computedStyle.lineJoin.value
    val effectiveFill = computedStyle.fill.value
    val effectiveFillOpacity = computedStyle.fillOpacity.value
    val effectiveDisplay = computedStyle.display.value
    val effectiveOpacity = computedStyle.opacity.value
    val effectiveVisibility = computedStyle.visibility.value
    val effectiveShadeStyle = computedStyle.shadeStyle.value

    var stroke
        get() = style.stroke.value
        set(value) = run {
            style.stroke = when (value) {
                null -> Paint.None
                else -> Paint.RGB(value)
            }
        }
    var strokeOpacity
        get() = style.strokeOpacity.value
        set(value) = run { style.strokeOpacity = Numeric.Rational(value) }
    var strokeWeight
        get() = style.strokeWeight.value
        set(value) = run { style.strokeWeight = Length.Pixels(value) }
    var miterLimit
        get() = style.miterLimit.value
        set(value) = run { style.miterLimit = Numeric.Rational(value) }
    var lineCap
        get() = style.lineCap.value
        set(value) = run {
            style.lineCap = when (value) {
                org.openrndr.draw.LineCap.BUTT -> LineCap.Butt
                org.openrndr.draw.LineCap.ROUND -> LineCap.Round
                org.openrndr.draw.LineCap.SQUARE -> LineCap.Square
            }
        }
    var lineJoin
        get() = style.lineJoin.value
        set(value) = run {
            style.lineJoin = when (value) {
                org.openrndr.draw.LineJoin.BEVEL -> LineJoin.Bevel
                org.openrndr.draw.LineJoin.MITER -> LineJoin.Miter
                org.openrndr.draw.LineJoin.ROUND -> LineJoin.Round
            }
        }
    var fill
        get() = style.fill.value
        set(value) = run {
            style.fill = when (value) {
                null -> Paint.None
                else -> Paint.RGB(value)
            }
        }
    var fillOpacity
        get() = style.fillOpacity.value
        set(value) = run { style.fillOpacity = Numeric.Rational(value) }
    var opacity
        get() = style.opacity.value
        set(value) = run { style.opacity = Numeric.Rational(value) }
    var shadeStyle
        get() = style.shadeStyle.value
        set(value) = run { style.shadeStyle = Shade.Value(value) }
}

infix fun KMutableProperty0<Paint>.`=`(color: ColorRGBa?) = this.set(when (color) {
    is ColorRGBa -> Paint.RGB(color)
    else -> Paint.None
})
@JvmName("=Numeric")
infix fun KMutableProperty0<Numeric>.`=`(value: Double) = this.set(Numeric.Rational(value))
@JvmName("=Length")
infix fun KMutableProperty0<Length>.`=`(value: Double) = this.set(Length.Pixels(value))
infix fun KMutableProperty0<LineCap>.`=`(cap: org.openrndr.draw.LineCap) = this.set(when (cap) {
    org.openrndr.draw.LineCap.SQUARE -> LineCap.Square
    org.openrndr.draw.LineCap.BUTT -> LineCap.Butt
    org.openrndr.draw.LineCap.ROUND -> LineCap.Round
})
infix fun KMutableProperty0<LineJoin>.`=`(join: org.openrndr.draw.LineJoin) = this.set(when (join) {
    org.openrndr.draw.LineJoin.MITER -> LineJoin.Miter
    org.openrndr.draw.LineJoin.BEVEL -> LineJoin.Bevel
    org.openrndr.draw.LineJoin.ROUND -> LineJoin.Round
})
infix fun KMutableProperty0<Transform>.`=`(value: Matrix44): Unit = this.set(Transform.Matrix(value))

operator fun KMutableProperty0<Shade>.setValue(thisRef: Style, property: KProperty<*>, value: ShadeStyle) {
    this.set(Shade.Value(value))
}

fun transform(node: CompositionNode): Matrix44 =
    (node.parent?.let { transform(it) } ?: Matrix44.IDENTITY) * ((node.style.transform as? Transform.Matrix)?.value
        ?: Matrix44.IDENTITY)

/**
 * a [CompositionNode] that holds a single image [ColorBuffer]
 */
class ImageNode(var image: ColorBuffer, var x: Double, var y: Double, var width: Double, var height: Double) :
    CompositionNode() {
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
            it.style = style.also { st ->
                st::transform `=` transform(this)
            }
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
            it.style = computedStyle
            it.attributes = attributes
        }
    }

    fun copy(
        id: String? = this.id,
        parent: CompositionNode? = null,
        style: Style = this.style,
        attributes: MutableMap<String, String?> = this.attributes,
        shape: Shape = this.shape
    ): ShapeNode {
        return ShapeNode(shape).also {
            it.id = id
            it.parent = parent
            it.style = style
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

    fun copy(
        id: String? = this.id,
        parent: CompositionNode? = null,
        style: Style = this.style,
        children: MutableList<CompositionNode> = this.children
    ): GroupNode {
        return GroupNode(children).also {
            it.id = id
            it.parent = parent
            it.style = style
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

data class CompositionDimensions(val x: Length, val y: Length, val width: Length, val height: Length) {
    val position = Vector2((x as Length.Pixels).value, (y as Length.Pixels).value)
    val dimensions = Vector2((width as Length.Pixels).value, (height as Length.Pixels).value)

    // constructor(val rectangle: Rectangle): this(rectangle.corner.x, rectangle.corner.y, rectangle.dimensions.x, rectangle.dimensions.y)

    override fun toString(): String = "$x $y $width $height"

    // I'm not entirely sure why this is needed but
    // but otherwise equality checks will never succeed
    override fun equals(other: Any?): Boolean {
        return other is CompositionDimensions
            && x.value == other.x.value
            && y.value == other.y.value
            && width.value == other.width.value
            && height.value == other.height.value
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        return result
    }
}

val defaultCompositionDimensions = CompositionDimensions(0.0.pixels, 0.0.pixels, 768.0.pixels, 576.0.pixels)

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

    var style: Style = Style()

    /**
     * The style attributes affecting the whole document, such as the viewBox area and aspect ratio.
     */
    var documentStyle: DocumentStyle = DocumentStyle()

    init {
        val (x, y, width, height) = bounds
        style.x = x
        style.y = y
        style.width = width
        style.height = height
    }

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

    /** Calculates the equivalent of `1%` in pixels. */
    internal fun normalizedDiagonalLength(): Double = sqrt(bounds.dimensions.squaredLength / 2.0)

    /**
     * Calculates effective viewport transformation using [viewBox] and [preserveAspectRatio].
     * As per [the SVG 2.0 spec](https://svgwg.org/svg2-draft/single-page.html#coords-ComputingAViewportsTransform).
     */
    fun calculateViewportTransform(): Matrix44 {
        return when (documentStyle.viewBox) {
            ViewBox.None -> Matrix44.IDENTITY
            is ViewBox.Value -> {
                when (val vb = (documentStyle.viewBox as ViewBox.Value).value) {
                    Rectangle.EMPTY -> {
                        // The intent is to not display the element
                        Matrix44.ZERO
                    }
                    else -> {
                        val vbCorner = vb.corner
                        val vbDims = vb.dimensions
                        val eCorner = bounds.position
                        val eDims = bounds.dimensions
                        val (align, meetOrSlice) = documentStyle.preserveAspectRatio!!

                        val scale = (eDims / vbDims).let {
                            if (align != Align.NONE) {
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
                            val cx = eDims.x - vbDims.x * scale.x
                            val cy = eDims.y - vbDims.y * scale.y
                            it + when (align) {
                                // TODO: This first one probably doesn't comply with the spec
                                Align.NONE -> Vector2.ZERO
                                Align.X_MIN_Y_MIN -> Vector2.ZERO
                                Align.X_MID_Y_MIN -> Vector2(cx / 2, 0.0)
                                Align.X_MAX_Y_MIN -> Vector2(cx, 0.0)
                                Align.X_MIN_Y_MID -> Vector2(0.0, cy / 2)
                                Align.X_MID_Y_MID -> Vector2(cx / 2, cy / 2)
                                Align.X_MAX_Y_MID -> Vector2(cx, cy / 2)
                                Align.X_MIN_Y_MAX -> Vector2(0.0, cy)
                                Align.X_MID_Y_MAX -> Vector2(cx / 2, cy)
                                Align.X_MAX_Y_MAX -> Vector2(cx, cy)
                            }
                        }

                        buildTransform {
                            translate(translate)
                            scale(scale.x, scale.y, 1.0)
                        }
                    }
                }
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
    @Suppress("UNCHECKED_CAST")
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