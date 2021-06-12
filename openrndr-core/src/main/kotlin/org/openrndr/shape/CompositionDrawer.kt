package org.openrndr.shape

import org.openrndr.*
import org.openrndr.collections.pflatMap
import org.openrndr.collections.pforEach
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.LineCap
import org.openrndr.draw.LineJoin
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.YPolarity
import org.openrndr.math.transforms.*
import java.util.*

enum class ClipOp {
    DISABLED,
    DIFFERENCE,
    REVERSE_DIFFERENCE,
    INTERSECT,
    UNION
}

enum class TransformMode {
    KEEP,
    APPLY
}

enum class ClipMode(val grouped: Boolean, val op: ClipOp) {
    DISABLED(false, ClipOp.DISABLED),
    DIFFERENCE(false, ClipOp.DIFFERENCE),
    DIFFERENCE_GROUP(true, ClipOp.DIFFERENCE),
    REVERSE_DIFFERENCE(false, ClipOp.REVERSE_DIFFERENCE),
    REVERSE_DIFFERENCE_GROUP(true, ClipOp.REVERSE_DIFFERENCE),
    INTERSECT(false, ClipOp.INTERSECT),
    INTERSECT_GROUP(true, ClipOp.INTERSECT),
    UNION(false, ClipOp.UNION),
    UNION_GROUP(true, ClipOp.UNION)
}

private data class CompositionDrawStyle(
    var fill: ColorRGBa? = null,
    var fillOpacity: Double = 1.0,
    var stroke: ColorRGBa? = ColorRGBa.BLACK,
    var strokeOpacity: Double = 1.0,
    var strokeWeight: Double = 1.0,
    var opacity: Double = 1.0,
    var clipMode: ClipMode = ClipMode.DISABLED,
    var mask: Shape? = null,
    var transformMode: TransformMode = TransformMode.APPLY,
    var lineCap: LineCap = LineCap.BUTT,
    var lineJoin: LineJoin = LineJoin.MITER,
    var miterlimit: Double = 4.0,
    var visibility: Visibility = Visibility.Visible
)

data class ShapeNodeIntersection(val node: ShapeNode, val intersection: ContourIntersection)
data class ShapeNodeNearestContour(val node: ShapeNode, val point: ContourPoint, val distanceDirection: Vector2, val distance: Double)

fun List<ShapeNodeIntersection>.merge(threshold: Double = 0.5): List<ShapeNodeIntersection> {
    val result = mutableListOf<ShapeNodeIntersection>()
    for (i in this) {
        val nearest = result.minByOrNull { it.intersection.position.squaredDistanceTo(i.intersection.position) }
        if (nearest == null) {
            result.add(i)
        } else if (nearest.intersection.position.squaredDistanceTo(i.intersection.position) >= threshold * threshold) {
            result.add(i)
        }
    }
    return result
}


/**
 * A Drawer-like interface for the creation of Compositions
 * This should be easier than creating Compositions manually
 */
class CompositionDrawer(documentBounds: CompositionDimensions = defaultCompositionDimensions,
                        composition: Composition? = null,
                        cursor: GroupNode? = composition?.root as? GroupNode
) {
    val root = (composition?.root as? GroupNode) ?: GroupNode()
    val composition = composition ?: Composition(root, documentBounds)

    var cursor = cursor ?: root
        private set

    private val modelStack = Stack<Matrix44>()
    private val styleStack = Stack<CompositionDrawStyle>().apply { }
    private var drawStyle = CompositionDrawStyle()

    var model = Matrix44.IDENTITY

    var fill
        get() = drawStyle.fill?.opacify(drawStyle.fillOpacity)?.opacify(drawStyle.opacity)
        set(value) = run {
            drawStyle.fill = value?.copy(a = 1.0)
            drawStyle.fillOpacity = value?.a ?: 1.0
        }

    var fillOpacity
        get() = drawStyle.fillOpacity
        set(value) = run { drawStyle.fillOpacity = value }

    var stroke
        get() = drawStyle.fill?.opacify(drawStyle.strokeOpacity)?.opacify(drawStyle.opacity)
        set(value) = run {
            drawStyle.stroke = value?.copy(a = 1.0)
            drawStyle.strokeOpacity = value?.a ?: 1.0
        }

    var strokeOpacity
        get() = drawStyle.strokeOpacity
        set(value) = run { drawStyle.strokeOpacity = value }

    var strokeWeight
        get() = drawStyle.strokeWeight
        set(value) = run { drawStyle.strokeWeight = value }

    var miterlimit
        get() = drawStyle.miterlimit
        set(value) = run { drawStyle.miterlimit = value }

    var lineCap
        get() = drawStyle.lineCap
        set(value) = run { drawStyle.lineCap = value }

    var lineJoin
        get() = drawStyle.lineJoin
        set(value) = run { drawStyle.lineJoin = value }

    var opacity
        get() = drawStyle.opacity
        set(value) = run { drawStyle.opacity = value }

    var visibility
        get() = drawStyle.visibility
        set(value) = run { drawStyle.visibility = value }

    var clipMode
        get() = drawStyle.clipMode
        set(value) = run { drawStyle.clipMode = value }

    var mask: Shape?
        get() = drawStyle.mask
        set(value) = run { drawStyle.mask = value }

    var transformMode
        get() = drawStyle.transformMode
        set(value) = run { drawStyle.transformMode = value }

    fun pushModel() {
        modelStack.push(model)
    }

    fun popModel() {
        model = modelStack.pop()
    }

    fun pushStyle() {
        styleStack.push(drawStyle.copy())
    }

    fun popStyle() {
        drawStyle = styleStack.pop()
    }

    fun isolated(draw: CompositionDrawer.() -> Unit) {
        pushModel()
        pushStyle()
        draw()
        popModel()
        popStyle()
    }

    fun GroupNode.with(builder: CompositionDrawer.() -> Unit): GroupNode {
        val oldCursor = cursor
        cursor = this
        builder()
        cursor = oldCursor
        return this
    }

    /**
     * Create a group node and run `builder` inside its context
     * @param insert if true the created group will be inserted at [cursor]
     * @param id an optional identifier
     * @param builder the function that is executed inside the group context
     */
    fun group(insert: Boolean = true, id: String? = null, builder: CompositionDrawer.() -> Unit): GroupNode {
        val group = GroupNode()
        group.id = id
        val oldCursor = cursor

        if (insert) {
            cursor.children.add(group)
            group.parent = cursor
        }
        cursor = group
        builder()

        cursor = oldCursor
        return group
    }

    fun translate(x: Double, y: Double) = translate(Vector2(x, y))

    fun rotate(rotationInDegrees: Double) {
        model *= Matrix44.rotateZ(rotationInDegrees)
    }

    fun scale(s: Double) {
        model *= Matrix44.scale(s, s, s)
    }

    fun scale(x: Double, y: Double) {
        model *= Matrix44.scale(x, y, 1.0)
    }

    fun translate(t: Vector2) {
        model *= Matrix44.translate(t.vector3())
    }

    fun contour(contour: ShapeContour, insert: Boolean = true): ShapeNode? {
        if (contour.empty) {
            return null
        }
        val shape = Shape(listOf(contour))
        return shape(shape, insert)
    }

    fun contours(contours: List<ShapeContour>, insert: Boolean = true) = contours.map { contour(it, insert) }

    /**
     * Search for a point on a contour in the composition tree that's nearest to `point`
     * @param point the query point
     * @param searchFrom a node from which the search starts, defaults to composition root
     * @return an optional ShapeNodeNearestContour instance
     */
    fun nearest(
            point: Vector2,
            searchFrom: CompositionNode = composition.root as GroupNode
    ): ShapeNodeNearestContour? {
        return distances(point, searchFrom).firstOrNull()
    }

    fun CompositionNode.nearest(point: Vector2) = nearest(point, searchFrom = this)

    fun difference(
            shape: Shape,
            searchFrom: CompositionNode = composition.root as GroupNode
    ): Shape {
        val shapes = searchFrom.findShapes()
        var from = shape

        for (subtract in shapes) {
            if (shape.bounds.intersects(subtract.shape.bounds)) {
                from = difference(from, subtract.shape)
            }
        }
        return from
    }

    /**
     * Find distances to each contour in the composition tree (or starting node)
     * @param point the query point
     * @param searchFrom a node from which the search starts, defaults to composition root
     * @return a sorted list of [ShapeNodeNearestContour] describing distance to every contour
     */
    fun distances(
            point: Vector2,
            searchFrom: CompositionNode = composition.root as GroupNode
    ): List<ShapeNodeNearestContour> {
        return searchFrom.findShapes().flatMap { node ->
            node.shape.contours.filter { !it.empty }
                    .map { it.nearest(point) }
                    .map { ShapeNodeNearestContour(node, it, point - it.position, it.position.distanceTo(point)) }
        }.sortedBy { it.distance }
    }

    fun CompositionNode.distances(point: Vector2): List<ShapeNodeNearestContour> = distances(point, searchFrom = this)

    /**
     * Test a given `contour` against contours in the composition tree
     * @param contour the query contour
     * @param searchFrom a node from which the search starts, defaults to composition root
     * @param mergeThreshold minimum distance between intersections before they are merged together,
     * 0.0 or lower means no merge
     * @return a list of `ShapeNodeIntersection`
     */
    fun intersections(
        contour: ShapeContour,
        searchFrom: CompositionNode = composition.root as GroupNode,
        mergeThreshold: Double = 0.5
    ): List<ShapeNodeIntersection> {
        return searchFrom.findShapes().pflatMap { node ->
            if (intersects(node.bounds, contour.bounds)) {
                node.shape.contours.flatMap {
                    intersections(contour, it).map {
                        ShapeNodeIntersection(node, it)
                    }
                }
            } else {
                emptyList()
            }
        }.let {
            if (mergeThreshold > 0.0) {
                it.merge(mergeThreshold)
            } else {
                it
            }
        }
    }

    fun CompositionNode.intersections(contour: ShapeContour, mergeThreshold: Double = 0.5) =
            intersections(contour, this, mergeThreshold)

    /**
     * Test a given `shape` against contours in the composition tree
     * @param shape the query shape
     * @param searchFrom a node from which the search starts, defaults to composition root
     * @return a list of `ShapeNodeIntersection`
     */
    fun intersections(
            shape: Shape,
            searchFrom: CompositionNode = composition.root as GroupNode,
            mergeThreshold: Double = 0.5
    ): List<ShapeNodeIntersection> {
        return shape.contours.flatMap {
            intersections(it, searchFrom, mergeThreshold)
        }
    }

    fun CompositionNode.intersections(shape: Shape, mergeThreshold: Double = 0.5) =
            intersections(shape, this, mergeThreshold)


    fun shape(shape: Shape, insert: Boolean = true): ShapeNode? {
        if (shape.empty) {
            return null
        }

        val inverseModel = model.inversed
        val postShape = mask?.let { intersection(shape, it.transform(inverseModel)) } ?: shape

        if (postShape.empty) {
            return null
        }

        // only use clipping for open shapes
        val clipMode = if (postShape.topology == ShapeTopology.CLOSED) clipMode else ClipMode.DISABLED

        return when (clipMode.op) {
            ClipOp.DISABLED, ClipOp.REVERSE_DIFFERENCE -> {
                val shapeNode = ShapeNode(postShape)

                val shapeTransform: Matrix44 = when (transformMode) {
                    TransformMode.KEEP -> {
                        shapeNode.transform = model
                        Matrix44.IDENTITY
                    }
                    TransformMode.APPLY -> {
                        shapeNode.transform = Matrix44.IDENTITY
                        model
                    }
                }
                shapeNode.shape = when (clipMode.op) {
                    ClipOp.DISABLED -> postShape.transform(shapeTransform)
                    ClipOp.REVERSE_DIFFERENCE -> {
                        val shapeNodes = (if (!clipMode.grouped) composition.findShapes() else cursor.findShapes())
                        var toInsert = shape
                        val inverse = model.inversed
                        for (shapeNode in shapeNodes) {
                            if (toInsert.empty) {
                                break
                            } else {
                                toInsert = difference(toInsert,shapeNode.effectiveShape.transform(inverse))
                            }
                        }
                        toInsert
                    }
                    else -> error("unreachable")
                }
                shapeNode.stroke = stroke
                shapeNode.strokeOpacity = strokeOpacity
                shapeNode.strokeWeight = strokeWeight
                shapeNode.miterLimit = miterlimit
                shapeNode.lineCap = lineCap
                shapeNode.lineJoin = lineJoin
                shapeNode.fill = fill
                shapeNode.fillOpacity = fillOpacity

                if (insert) {
                    cursor.children.add(shapeNode)
                    shapeNode.parent = cursor
                }
                shapeNode
            }
            else -> {
                val shapeNodes = (if (!clipMode.grouped) composition.findShapes() else cursor.findShapes())
                val toRemove = mutableListOf<CompositionNode>()
                shapeNodes.pforEach { shapeNode ->
                    val inverse = shapeNode.effectiveTransform.inversed
                    val transformedShape = postShape.transform(inverse * model)
                    val operated =
                            when (clipMode.op) {
                                ClipOp.INTERSECT -> intersection(shapeNode.shape, transformedShape)
                                ClipOp.UNION -> union(shapeNode.shape, transformedShape)
                                ClipOp.DIFFERENCE -> difference(shapeNode.shape, transformedShape)
                                else -> error("unsupported base op ${clipMode.op}")
                            }
                    if (!operated.empty) {
                        shapeNode.shape = operated
                    } else {
                        synchronized(toRemove) {
                            toRemove.add(shapeNode)
                        }
                    }
                }
                for (node in toRemove) {
                    node.remove()
                }
                null
            }
        }
    }

    fun shapes(shapes: List<Shape>, insert: Boolean = true) = shapes.map { shape(it, insert) }

    fun rectangle(rectangle: Rectangle, closed: Boolean = true, insert: Boolean = true) = contour(rectangle.contour.let {
        if (closed) {
            it
        } else {
            it.open
        }
    }, insert = insert)

    fun rectangle(x: Double, y: Double, width: Double, height: Double, closed: Boolean = true, insert: Boolean = true) = rectangle(Rectangle(x, y, width, height), closed, insert)

    fun rectangles(rectangles: List<Rectangle>, insert: Boolean = true) = rectangles.map { rectangle(it, insert) }

    fun rectangles(positions: List<Vector2>, width: Double, height: Double, insert: Boolean = true) = rectangles(positions.map {
        Rectangle(it, width, height)
    }, insert)

    fun rectangles(positions: List<Vector2>, dimensions: List<Vector2>, insert: Boolean) = rectangles((positions zip dimensions).map {
        Rectangle(it.first, it.second.x, it.second.y)
    }, insert)

    fun circle(x: Double, y: Double, radius: Double, closed: Boolean = true, insert: Boolean = true) = circle(Circle(Vector2(x, y), radius), closed, insert)

    fun circle(position: Vector2, radius: Double, closed: Boolean = true, insert: Boolean = true) = circle(Circle(position, radius), closed, insert)

    fun circle(circle: Circle, closed: Boolean = true, insert: Boolean = true) = contour(circle.contour.let {
        if (closed) {
            it
        } else {
            it.open
        }
    }, insert)

    fun circles(circles: List<Circle>, insert: Boolean = true) = circles.map { circle(it, insert) }

    fun circles(positions: List<Vector2>, radius: Double, insert: Boolean = true) = circles(positions.map { Circle(it, radius) }, insert)

    fun circles(positions: List<Vector2>, radii: List<Double>, insert: Boolean = true) = circles((positions zip radii).map { Circle(it.first, it.second) }, insert)

    fun ellipse(
            x: Double,
            y: Double,
            xRadius: Double,
            yRadius: Double,
            rotationInDegrees: Double = 0.0,
            closed: Boolean = true,
            insert: Boolean = true
    ) = ellipse(Vector2(x, y), xRadius, yRadius, rotationInDegrees, closed, insert)

    fun ellipse(
            center: Vector2,
            xRadius: Double,
            yRadius: Double,
            rotationInDegrees: Double,
            closed: Boolean = true,
            insert: Boolean = true
    ) = contour(OrientedEllipse(center, xRadius, yRadius, rotationInDegrees).contour.let {
        if (closed) {
            it
        } else {
            it.open
        }
    }, insert)

    fun lineSegment(
            startX: Double,
            startY: Double,
            endX: Double,
            endY: Double,
            insert: Boolean = true
    ) = lineSegment(LineSegment(startX, startY, endX, endY), insert)

    fun lineSegment(
            start: Vector2,
            end: Vector2,
            insert: Boolean = true
    ) = lineSegment(LineSegment(start, end), insert)

    fun lineSegment(
            lineSegment: LineSegment,
            insert: Boolean = true
    ) = contour(lineSegment.contour, insert)

    fun lineSegments(
            lineSegments: List<LineSegment>,
            insert: Boolean = true
    ) = lineSegments.map {
        lineSegment(it, insert)
    }

    fun lineStrip(
            points: List<Vector2>,
            insert: Boolean = true
    ) = contour(ShapeContour.fromPoints(points, false, YPolarity.CW_NEGATIVE_Y), insert)

    fun lineLoop(
            points: List<Vector2>,
            insert: Boolean = true
    ) = contour(ShapeContour.fromPoints(points, true, YPolarity.CW_NEGATIVE_Y), insert)

    fun text(
            text: String,
            position: Vector2,
            insert: Boolean = true
    ): TextNode {
        val g = GroupNode()
        g.style.transform = Transform.Matrix(transform { translate(position.xy0) })
        val textNode = TextNode(text, null).apply {
            this.style.fill = when (val f = this@CompositionDrawer.fill) {
                is ColorRGBa -> Paint.RGB(f)
                else -> Paint.None
            }

        }
        g.children.add(textNode)
        if (insert) {
            cursor.children.add(g)
        }
        return textNode
    }

    fun textOnContour(
            text: String,
            contour: ShapeContour,
            insert: Boolean = true
    ): TextNode {
        val textNode = TextNode(text, contour)
        if (insert) {
            cursor.children.add(textNode)
        }
        return textNode
    }

    fun texts(text: List<String>, positions: List<Vector2>) =
            (text zip positions).map {
                text(it.first, it.second)
            }

    /**
     * Adds an image to the composition tree
     */
    fun image(
            image: ColorBuffer,
            x: Double = 0.0,
            y: Double = 0.0,
            insert: Boolean = true
    ): ImageNode {
        val node = ImageNode(image, x, y, width = image.width.toDouble(), height = image.height.toDouble())
        node.style.transform = Transform.Matrix(this.model)
        if (insert) {
            cursor.children.add(node)
        }
        return node
    }

    fun composition(composition: Composition): CompositionNode {
        pushStyle()

        fun node(compositionNode: CompositionNode) {
            pushModel()
            pushStyle()

            model *= compositionNode.style.transform.value

            // (skip compositionNode.shadeStyle here)
            // TODO: Handle Style.display
            when (compositionNode) {
                is ShapeNode -> {
                    val cs = compositionNode.effectiveStyle

                    cs.stroke.let {
                        stroke = when (it) {
                            is Paint.RGB -> it.value
                            Paint.None -> null
                            Paint.CurrentColor -> null
                        }
                    }
                    cs.strokeOpacity.let {
                        strokeOpacity = when (it) {
                            is Numeric.Rational -> it.value
                        }
                    }
                    cs.strokeWeight.let {
                        strokeWeight = when (it) {
                            is Length.Pixels -> it.value
                            is Length.Percent -> composition.normalizedDiagonalLength() * it.value / 100.0
                        }
                    }
                    cs.miterLimit.let {
                        miterlimit = when (it) {
                            is Numeric.Rational -> it.value
                        }
                    }
                    cs.lineCap.let {
                        lineCap = it.value
                    }
                    cs.lineJoin.let {
                        lineJoin = it.value
                    }
                    cs.fill.let {
                        fill = when (it) {
                            is Paint.RGB -> it.value
                            is Paint.None -> null
                            // TODO: This should grab the value of the `color` property in the CSS
                            // but that's so irrelevant in our case.
                            is Paint.CurrentColor -> null
                        }
                    }
                    cs.fillOpacity.let {
                        fillOpacity = when (it) {
                            is Numeric.Rational -> it.value
                        }
                    }
                    cs.opacity.let {
                        opacity = when (it) {
                            is Numeric.Rational -> it.value
                        }
                    }
                    cs.visibility.let {
                        visibility = it
                    }
                    shape(compositionNode.shape)
                }
                is ImageNode -> {
                    image(compositionNode.image)
                }
                is TextNode -> {
                    text(compositionNode.text, Vector2.ZERO)
                }
                is GroupNode -> compositionNode.children.forEach { node(it) }
            }

            popStyle()
            popModel()
        }
        node(composition.root)
        popStyle()

        return composition.root
    }

    fun CompositionNode.translate(x: Double, y: Double, z: Double = 0.0) {
        transform = transform.transform {
            translate(x, y, z)
        }
    }

    fun CompositionNode.rotate(angleInDegrees: Double, pivot: Vector2 = Vector2.ZERO) {
        transform = transform.transform {
            translate(pivot.xy0)
            rotate(Vector3.UNIT_Z, angleInDegrees)
            translate(-pivot.xy0)
        }
    }

    fun CompositionNode.scale(scale: Double, pivot: Vector2 = Vector2.ZERO) {
        transform = transform.transform {
            translate(pivot.xy0)
            scale(scale, scale, scale)
            translate(-pivot.xy0)
        }
    }

    fun CompositionNode.transform(builder: TransformBuilder.() -> Unit) {
        return this.transform(builder)
    }

    /**
     * Copy node and insert copy at [cursor]
     * @param insert when true the copy is inserted at [cursor]
     * @return a deep copy of the node
     */
    // TODO: Include new features
    fun CompositionNode.duplicate(insert: Boolean = true): CompositionNode {
        fun nodeCopy(node: CompositionNode): CompositionNode {
            val copy = when (node) {
                is ImageNode -> {
                    ImageNode(node.image, node.x, node.y, node.width, node.height)
                }
                is ShapeNode -> {
                    ShapeNode(node.shape)
                }
                is TextNode -> {
                    TextNode(node.text, node.contour)
                }
                is GroupNode -> {
                    val children = node.children.map { nodeCopy(it) }.toMutableList()
                    val groupNode = GroupNode(children)
                    groupNode.children.forEach {
                        it.parent = groupNode
                    }
                    groupNode
                }
            }
            copy.style = node.style
            return copy
        }

        val copy = nodeCopy(this)
        if (insert) {
            this@CompositionDrawer.cursor.children.add(copy)
            copy.parent = cursor
        }
        return copy
    }
}

// Derives Composition dimensions from current Drawer
fun Program.drawComposition(
        documentBounds: CompositionDimensions = CompositionDimensions(0.0.pixels, 0.0.pixels, this.drawer.width.toDouble().pixels, this.drawer.height.toDouble().pixels),
        composition: Composition? = null,
        cursor: GroupNode? = composition?.root as? GroupNode,
        drawFunction: CompositionDrawer.() -> Unit
): Composition = CompositionDrawer(documentBounds, composition, cursor).apply { drawFunction() }.composition

fun Program.drawComposition(
    documentBounds: Rectangle,
    composition: Composition? = null,
    cursor: GroupNode? = composition?.root as? GroupNode,
    drawFunction: CompositionDrawer.() -> Unit
): Composition = CompositionDrawer(CompositionDimensions(documentBounds), composition, cursor).apply { drawFunction() }.composition

fun drawComposition(
    documentBounds: CompositionDimensions = defaultCompositionDimensions,
    composition: Composition? = null,
    cursor: GroupNode? = composition?.root as? GroupNode,
    drawFunction: CompositionDrawer.() -> Unit
): Composition = CompositionDrawer(documentBounds, composition, cursor).apply { drawFunction() }.composition

fun Composition.draw(drawFunction: CompositionDrawer.() -> Unit) {
    drawComposition(composition = this, drawFunction = drawFunction)
}
