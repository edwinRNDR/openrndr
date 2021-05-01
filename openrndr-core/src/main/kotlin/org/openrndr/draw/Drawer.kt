@file:Suppress("unused")

package org.openrndr.draw

import mu.KotlinLogging
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.internal.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.YPolarity
import org.openrndr.math.transforms.rotate
import org.openrndr.math.transforms.rotateZ
import org.openrndr.math.transforms.scale
import org.openrndr.math.transforms.translate
import org.openrndr.shape.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.*
import kotlin.math.abs
import kotlin.math.log2
import kotlin.reflect.KMutableProperty0
import org.openrndr.math.transforms.lookAt as _lookAt
import org.openrndr.math.transforms.ortho as _ortho
import org.openrndr.math.transforms.perspective as _perspective
private val logger = KotlinLogging.logger {}


data class VertexElement(val attribute: String, val offset: Int, val type: VertexElementType, val arraySize: Int)

@Suppress("MemberVisibilityCanPrivate")

fun codeFromStream(stream: InputStream): String {
    BufferedReader(InputStreamReader(stream)).use {
        return it.readText()
    }
}

fun codeFromURL(url: URL): String {
    url.openStream().use {
        return codeFromStream(it)
    }
}

fun codeFromURL(url: String): String {
    return codeFromURL(URL(url))
}

/**
 * A render target that wraps around the back-buffer
 */
interface ProgramRenderTarget : RenderTarget {
    val program: Program
    override val width get() = program.width
    override val height get() = program.height
}

enum class DrawQuality {
    QUALITY,
    PERFORMANCE
}

enum class TransformTarget {
    MODEL,
    VIEW,
    PROJECTION
}

/**
 * The Drawer
 */
@Suppress("MemberVisibilityCanPrivate", "unused")
class Drawer(val driver: Driver) {

    /**
     * The bounds of the drawable area as a [Rectangle]
     */
    val bounds: Rectangle
        get() = Rectangle(Vector2(0.0, 0.0), width * 1.0, height * 1.0)

    private val drawStyles = Stack<DrawStyle>().apply {
        push(DrawStyle())
    }
    private var rectangleDrawer = RectangleDrawer()
    private var vertexBufferDrawer = VertexBufferDrawer()


    private var circleDrawer = CircleDrawer()
    private var pointDrawer = PointDrawer()
    private var imageDrawer = ImageDrawer()
    private var fastLineDrawer = PerformanceLineDrawer()
    private val meshLineDrawer by lazy { MeshLineDrawer() }
    private var qualityLineDrawer = QualityLineDrawer()
    private var qualityPolygonDrawer = QualityPolygonDrawer()
    internal val fontImageMapDrawer = FontImageMapDrawer()

    private val defaultFontMap by lazy {
        val defaultFontPath = File("data/fonts/default.otf")
        if (defaultFontPath.isFile) {
            logger.info("loading default font from ${defaultFontPath.absolutePath}")
            loadFont(defaultFontPath.path, 16.0)
        } else {
            logger.warn("default font ${defaultFontPath.absolutePath} not found")
            null
        }
    }

    private val modelStack = Stack<Matrix44>()
    private val viewStack = Stack<Matrix44>()
    private val projectionStack = Stack<Matrix44>()

    var width: Int = 0
    var height: Int = 0

    private var modelViewScaling = 1.0

    /** The active model matrix */
    var model: Matrix44 = Matrix44.IDENTITY
        set(value) {
            field = value
            modelViewScaling = ((view * model).matrix33 * Vector3(1.0, 1.0, 1.0).normalized).length
        }

    /** The active view matrix */
    var view: Matrix44 = Matrix44.IDENTITY
        set(value) {
            field = value
            modelViewScaling = ((view * model).matrix33 * Vector3(1.0, 1.0, 1.0).normalized).length
        }


    /** The active projection matrix */
    var projection: Matrix44 = Matrix44.IDENTITY

    /**
     * The draw context holds references to model, view, projection matrices, width, height and content-scale
     */
    val context: DrawContext
        get() = DrawContext(model, view, projection, width, height, RenderTarget.active.contentScale)

    var drawStyle = DrawStyle()
    /** The active draw style */

    /**
     * @see isolatedWithTarget
     * @see isolated
     */
    fun withTarget(target: RenderTarget, action: Drawer.() -> Unit) {
        target.bind()
        this.action()
        target.unbind()
    }

    /**
     *  Resets state stacks and load default values for draw style and transformations.
     *  This destroys the state stacks, consider using defaults() instead of reset()
     *  @see defaults
     */
    @Deprecated("reset is considered harmful, use defaults()")
    fun reset() {
        viewStack.clear()
        modelStack.clear()
        projectionStack.clear()
        drawStyles.clear()
        defaults()
    }

    /**
     * Loads default values for draw style and transformations
     */
    fun defaults() {
        drawStyle = DrawStyle()
        ortho()
        view = Matrix44.IDENTITY
        model = Matrix44.IDENTITY
    }

    /**
     * Sets the [projection] matrix to orthogonal using the sizes of a [RenderTarget]
     * @param renderTarget the render target to take the sizes from
     */
    fun ortho(renderTarget: RenderTarget) {
        ortho(0.0, renderTarget.width.toDouble(), renderTarget.height.toDouble(), 0.0, -1.0, 1.0)
    }

    /**
     * Sets the [projection] matrix to orthogonal using the drawer's current size
     */
    fun ortho() {
        ortho(0.0, width.toDouble(), height.toDouble(), 0.0, -1.0, 1.0)
    }

    /**
     * Sets the [projection] matrix to orthogonal using [left], [right], [bottom], [top], [near], [far]
     * @param left left value
     * @param right right value
     * @param bottom bottom value
     * @param top top value
     * @param near near value
     * @param far far value
     * @see perspective
     */
    fun ortho(left: Double, right: Double, bottom: Double, top: Double, near: Double, far: Double) {
        projection = _ortho(left, right, bottom, top, near, far)
    }

    /**
     *  Sets the projection to a perspective projection matrix
     *
     *  [fovY] Y field of view in degrees
     *  [aspectRatio] lens aspect aspectRatio
     *  [zNear] The distance to the zNear clipping plane along the -Z axis.
     *  [zFar]The distance to the zFar clipping plane along the -Z axis.
     *  @see ortho
     */
    fun perspective(fovY: Double, aspectRatio: Double, zNear: Double, zFar: Double) {
        projection = _perspective(fovY, aspectRatio, zNear, zFar)
    }

    fun lookAt(from: Vector3, to: Vector3, up: Vector3 = Vector3.UNIT_Y, target: TransformTarget = TransformTarget.VIEW) {
        transform(target) *= _lookAt(from, to, up)
    }

    /**
     * Apply a uniform scale to the model matrix
     * @param s the scaling factor
     */
    fun scale(s: Double, target: TransformTarget = TransformTarget.MODEL) {
        transform(target) *= Matrix44.scale(s, s, s)
    }

    /**
     * Applies non-uniform scale to the model matrix
     * @param x the scaling factor for the x-axis
     * @param y the scaling factor for the y-axis
     */
    fun scale(x: Double, y: Double, target: TransformTarget = TransformTarget.MODEL) {
        transform(target) *= Matrix44.scale(x, y, 1.0)
    }

    /**
     * Applies non-uniform scale to the model matrix
     * @param x the scaling factor for the x-axis
     * @param y the scaling factor for the y-axis
     * @param z the scaling factor for the y-axis
     * @see translate
     * @see scale
     */
    fun scale(x: Double, y: Double, z: Double, target: TransformTarget = TransformTarget.MODEL) {
        transform(target) *= Matrix44.scale(x, y, z)
    }

    /**
     * Applies a two-dimensional translation to the model matrix
     */
    fun translate(t: Vector2, target: TransformTarget = TransformTarget.MODEL) {
        transform(target) *= Matrix44.translate(t.vector3())
    }

    /**
     * Applies three-dimensional translation to the model matrix
     */
    fun translate(t: Vector3, target: TransformTarget = TransformTarget.MODEL) {
        transform(target) *= Matrix44.translate(t)
    }

    /**
     * Applies a two-dimensional translation to the model matrix
     */
    fun translate(x: Double, y: Double, target: TransformTarget = TransformTarget.MODEL) {
        translate(x, y, 0.0, target)
    }

    /**
     * Applies a three-dimensional translation to the model matrix
     */
    fun translate(x: Double, y: Double, z: Double, target: TransformTarget = TransformTarget.MODEL) {
        transform(target) *= Matrix44.translate(Vector3(x, y, z))
    }

    /**
     * Applies a rotation over the z-axis to the model matrix
     * @param rotationInDegrees the rotation in degrees
     */
    fun rotate(rotationInDegrees: Double, target: TransformTarget = TransformTarget.MODEL) {
        transform(target) *= Matrix44.rotateZ(rotationInDegrees)
    }

    /**
     * Applies a rotation over an arbitrary axis to the model matrix
     * @param axis the axis to rotate over, will be normalized
     * @param rotationInDegrees the rotation in degrees
     */
    fun rotate(axis: Vector3, rotationInDegrees: Double, target: TransformTarget = TransformTarget.MODEL) {
        transform(target) *= Matrix44.rotate(axis, rotationInDegrees)
    }

    fun clear(r: Double, g: Double, b: Double, a: Double) {
        driver.clear(r, g, b, a)
    }

    fun clear(color: ColorRGBa) {
        driver.clear(color)
    }

    @Deprecated("background will be replaced by clear", replaceWith = ReplaceWith("clear(r,g,b,a)"))
    fun background(r: Double, g: Double, b: Double, a: Double) {
        driver.clear(r, g, b, a)
    }

    @Deprecated("background will be replaced by clear", replaceWith = ReplaceWith("clear(color)"))
    fun background(color: ColorRGBa) {
        driver.clear(color)
    }

    /**
     * Push the active draw style on the draw style stack
     * @see drawStyle
     * @see popStyle
     */
    fun pushStyle(): DrawStyle = drawStyles.push(drawStyle.copy())

    /**
     * Pop the draw style from the draw style stack
     * @see drawStyle
     * @see popStyle
     */
    fun popStyle() {
        drawStyle = drawStyles.pop().copy()
    }

    fun pushView(): Matrix44 = viewStack.push(view)
    fun popView() {
        view = viewStack.pop()
    }

    fun pushModel(): Matrix44 = modelStack.push(model)
    fun popModel() {
        model = modelStack.pop()
    }

    /**
     * Push the active projection matrix on the projection state stack
     */
    fun pushProjection(): Matrix44 = projectionStack.push(projection)

    /**
     * Pop the active projection matrix from the projection state stack
     */
    fun popProjection() {
        projection = projectionStack.pop()
    }

    /**
     * Push the active model, view and projection matrices on their according stacks
     * @see pushTransforms
     * @see popTransforms
     * @see isolated
     * @see isolatedWithTarget
     */
    fun pushTransforms() {
        pushModel()
        pushView()
        pushProjection()
    }

    /**
     * Pop the active the model, view and projection matrices from their according stacks
     * @see pushTransforms
     * @see popTransforms
     * @see isolated
     * @see isolatedWithTarget
     */
    fun popTransforms() {
        popModel()
        popView()
        popProjection()
    }

    var depthWrite: Boolean
        set(value) {
            drawStyle.depthWrite = value
        }
        get() = drawStyle.depthWrite

    var cullTestPass: CullTestPass
        set(value) {
            drawStyle.cullTestPass = value
        }
        get() = drawStyle.cullTestPass

    var depthTestPass: DepthTestPass
        set(value) {
            drawStyle.depthTestPass = value
        }
        get() = drawStyle.depthTestPass


    var shadeStyle: ShadeStyle?
        set(value) {
            drawStyle.shadeStyle = value
        }
        get() = drawStyle.shadeStyle

    /**
     * The active fill color
     * @see stroke
     */
    var fill: ColorRGBa?
        set(value) {
            drawStyle.fill = value
        }
        get() = drawStyle.fill

    /**
     * The active stroke color
     * @see fill
     * @see strokeWeight
     */
    var stroke: ColorRGBa?
        set(value) {
            drawStyle.stroke = value
        }
        get() = drawStyle.stroke

    /**
     * The active stroke weight
     * @see stroke
     * @see lineCap
     * @see lineJoin
     */
    var strokeWeight: Double
        set(value) {
            drawStyle.strokeWeight = value
        }
        get() = drawStyle.strokeWeight

    /**
     * The active line cap method
     * @see strokeWeight
     * @see stroke
     * @see lineJoin
     */
    var lineCap: LineCap
        set(value) {
            drawStyle.lineCap = value
        }
        get() = drawStyle.lineCap


    /**
     * The active line join method
     * @see strokeWeight
     * @see stroke
     * @see lineCap
     */
    var lineJoin: LineJoin
        set(value) {
            drawStyle.lineJoin = value
        }
        get() = drawStyle.lineJoin

    var miterlimit: Double
        set(value) {
            drawStyle.miterLimit = value
        }
        get() = drawStyle.miterLimit

    /**
     * The active fontmap, default is null
     */
    var fontMap: FontMap?
        set(value) {
            drawStyle.fontMap = value
        }
        get() {
            if (drawStyle.fontMap == null) {
                drawStyle.fontMap = defaultFontMap
            }
            return drawStyle.fontMap
        }

    fun rectangle(rectangle: Rectangle) {
        rectangleDrawer.drawRectangle(context, drawStyle, rectangle.x, rectangle.y, rectangle.width, rectangle.height)
    }

    fun rectangle(x: Double, y: Double, width: Double, height: Double) {
        rectangleDrawer.drawRectangle(context, drawStyle, x, y, width, height)
    }

    fun rectangle(corner: Vector2, width: Double, height: Double) {
        rectangleDrawer.drawRectangle(context, drawStyle, corner.x, corner.y, width, height)
    }

    fun rectangles(positions: List<Vector2>, width: Double, height: Double) {
        rectangleDrawer.drawRectangles(context, drawStyle, positions, width, height)
    }

    fun rectangles(positions: List<Vector2>, dimensions: List<Vector2>) {
        rectangleDrawer.drawRectangles(context, drawStyle, positions, dimensions)
    }

    fun rectangles(rectangles: List<Rectangle>) {
        rectangleDrawer.drawRectangles(context, drawStyle, rectangles)
    }

    fun rectangles(batch: RectangleBatch, count: Int = batch.size) {
        rectangleDrawer.drawRectangles(context, drawStyle, batch, count)
    }

    /**
     * Create and draw batched rectangles
     */
    fun rectangles(build: RectangleBatchBuilder.() -> Unit) {
        val batchBuilder = RectangleBatchBuilder(this)
        batchBuilder.build()
        rectangleDrawer.ensureBatchSize(batchBuilder.entries.size)
        batchBuilder.batch(rectangleDrawer.batch)
        rectangleDrawer.drawRectangles(context, drawStyle, rectangleDrawer.batch, batchBuilder.entries.size)
    }

    /**
     * Draw a single point
     * @see points
     * @see circle
     */
    fun point(x: Double, y: Double, z: Double = 0.0) {
        pointDrawer.drawPoint(context, drawStyle, x, y, z)
    }

    /**
     * Draw a single point
     * @see points
     * @see circle
     */
    fun point(vector: Vector2) {
        pointDrawer.drawPoint(context, drawStyle, vector.x, vector.y, 0.0)
    }

    /**
     * Draw a single point
     * @see points
     * @see circle
     */
    fun point(vector: Vector3) {
        pointDrawer.drawPoint(context, drawStyle, vector.x, vector.y, vector.z)
    }

    /**
     * Draw a list of 2D points
     * @see point
     * @see circle
     */
    @JvmName("points2D")
    fun points(points: List<Vector2>) {
        pointDrawer.drawPoints(context, drawStyle, points)
    }

    /**
     * Draw a list of 3D points
     * @see point
     * @see circle
     * @see circles
     */
    @JvmName("points3D")
    fun points(points: List<Vector3>) {
        pointDrawer.drawPoints(context, drawStyle, points)
    }

    /**
     * Create and draw batched points
     */
    fun points(build: PointBatchBuilder.() -> Unit) {
        val batchBuilder = PointBatchBuilder(this)
        batchBuilder.build()
        pointDrawer.ensureBatchSize(batchBuilder.entries.size)
        batchBuilder.batch(pointDrawer.batch)
        pointDrawer.drawPoints(context, drawStyle, pointDrawer.batch, batchBuilder.entries.size)
    }

    /**
     * Draw a stored batch of points
     */
    fun points(batch: PointBatch, count: Int = batch.size) {
        pointDrawer.drawPoints(context, drawStyle, batch, count)
    }

    /**
     * Draw a circle
     */
    fun circle(x: Double, y: Double, radius: Double) {
        circleDrawer.drawCircle(context, drawStyle, x, y, radius)
    }

    fun circle(position: Vector2, radius: Double) {
        circleDrawer.drawCircle(context, drawStyle, position.x, position.y, radius)
    }

    fun circle(circle: Circle) {
        circleDrawer.drawCircle(context, drawStyle, circle.center.x, circle.center.y, circle.radius)
    }

    fun circles(positions: List<Vector2>, radius: Double) {
        circleDrawer.drawCircles(context, drawStyle, positions, radius)
    }

    fun circles(positions: List<Vector2>, radii: List<Double>) {
        circleDrawer.drawCircles(context, drawStyle, positions, radii)
    }

    fun circles(circles: List<Circle>) {
        circleDrawer.drawCircles(context, drawStyle, circles)
    }

    /**
     * Draw stored circle batch
     */
    fun circles(batch: CircleBatch, count: Int = batch.size) {
        circleDrawer.drawCircles(context, drawStyle, batch, count)
    }

    /**
     * Create and draw batched circles
     */
    fun circles(build: CircleBatchBuilder.() -> Unit) {
        val batchBuilder = CircleBatchBuilder(this)
        batchBuilder.build()
        circleDrawer.ensureBatchSize(batchBuilder.entries.size)
        batchBuilder.batch(circleDrawer.batch)
        circleDrawer.drawCircles(context, drawStyle, circleDrawer.batch, batchBuilder.entries.size)
    }


    /**
     * Draws a single [Shape] using [fill], [stroke] and [strokeWeight] settings
     * @see contour
     * @see shapes
     * @see contours
     * @see composition
     */
    fun shape(shape: Shape) {
        val distanceTolerance = 0.5 / (modelViewScaling * log2(strokeWeight).coerceAtLeast(1.0))
        val fringeWidth = 1.0 / modelViewScaling
        if (abs(modelViewScaling) < 1E-6){
            return
        }

        if (RenderTarget.active.hasDepthBuffer) {
            when (shape.topology) {
                ShapeTopology.CLOSED -> {
                    val closedPC = shape.contours.map { it.adaptivePositionsAndCorners(distanceTolerance) }
                    val closedP = closedPC.map { it.first }
                    val closedC = closedPC.map { it.second }
                    qualityPolygonDrawer.drawPolygon(context, drawStyle,
                            closedP, closedC, fringeWidth)
                    qualityLineDrawer.drawLineLoops(context, drawStyle, closedP, closedC, fringeWidth)
                }
                ShapeTopology.OPEN -> {
                    val openPC = shape.openContours.map { it.adaptivePositionsAndCorners(distanceTolerance) }
                    val openP = openPC.map { it.first }
                    val openC = openPC.map { it.second }
                    qualityLineDrawer.drawLineStrips(context, drawStyle, openP, openC, fringeWidth)
                }
                ShapeTopology.MIXED -> {
                    val closedPC = shape.closedContours.map { it.adaptivePositionsAndCorners(distanceTolerance) }
                    val closedP = closedPC.map { it.first }
                    val closedC = closedPC.map { it.second }
                    qualityPolygonDrawer.drawPolygon(context, drawStyle, closedP, closedC, fringeWidth)
                    val openPC = shape.openContours.map { it.adaptivePositionsAndCorners(distanceTolerance) }
                    val openP = openPC.map { it.first }
                    val openC = openPC.map { it.second }
                    qualityLineDrawer.drawLineStrips(context, drawStyle, openP, openC, fringeWidth)
                }
            }
        } else {
            error("drawing shapes requires a render target with a depth buffer attachment")
        }
    }

    /**
     * Draws shapes using [fill], [stroke] and [strokeWeight] settings
     * @see shape
     * @see contour
     * @see contours
     */
    fun shapes(shapes: List<Shape>) {
        shapes.forEach {
            shape(it)
        }
    }

    /**
     * Draws a single [ShapeContour] using [fill], [stroke] and [strokeWeight] settings
     */
    fun contour(contour: ShapeContour) {
        val distanceTolerance = 0.5 / (modelViewScaling * log2(strokeWeight).coerceAtLeast(1.0))
        val fringeWidth = 1.0 / modelViewScaling
        if (abs(modelViewScaling) < 1E-6){
            return
        }

        if (RenderTarget.active.hasDepthBuffer) {
            if (drawStyle.fill != null && contour.closed) {
                val apc = contour.adaptivePositionsAndCorners(distanceTolerance)
                val ap = listOf(apc.first)
                val ac = listOf(apc.second)

                qualityPolygonDrawer.drawPolygon(context, drawStyle, ap, ac, fringeWidth)
            }

            if (drawStyle.stroke != null && drawStyle.strokeWeight > 1E-4) {
                when (drawStyle.quality) {
                    DrawQuality.PERFORMANCE -> when (contour.closed) {
                        true -> fastLineDrawer.drawLineLoops(context, drawStyle, listOf(contour.adaptivePositions(distanceTolerance)))
                        false -> fastLineDrawer.drawLineLoops(context, drawStyle, listOf(contour.adaptivePositions(distanceTolerance)))
                    }
                    DrawQuality.QUALITY -> {
                        val apc = contour.adaptivePositionsAndCorners(distanceTolerance)
                        val ap = listOf(apc.first)
                        val ac = listOf(apc.second)
                        when (contour.closed) {
                            true -> qualityLineDrawer.drawLineLoops(context, drawStyle, ap, ac, fringeWidth)
                            false -> qualityLineDrawer.drawLineStrips(context, drawStyle, ap, ac, fringeWidth)
                        }
                    }
                }
            }
        } else {
            throw RuntimeException("drawing contours requires a render target with a depth buffer attachment")
        }
    }

    /**
     * Draws contours using [fill], [stroke] and [strokeWeight] settings
     */
    fun contours(contours: List<ShapeContour>) {
        for (contour in contours) {
            contour(contour)
        }
        /*
        if (drawStyle.fill != null) {
            qualityPolygonDrawer.drawPolygons(context, drawStyle, contours.filter { it.closed }.map { listOf(it.adaptivePositions()) })
        }

        if (drawStyle.stroke != null) {
            qualityLineDrawer.drawLineStrips(context, drawStyle, contours.map { it.adaptivePositions() })
        }
        */

    }

    fun lineSegment(x0: Double, y0: Double, x1: Double, y1: Double) {
        lineSegment(Vector2(x0, y0), Vector2(x1, y1))
    }

    fun lineSegment(lineSegment: LineSegment) {
        lineSegment(lineSegment.start, lineSegment.end)
    }

    /**
     * Draws a line segment from [start] to [end] using 2d coordinates
     */
    fun lineSegment(start: Vector2, end: Vector2) {
        val fringeWidth = 1.0 / modelViewScaling
        if (abs(modelViewScaling) < 1E-6){
            return
        }


        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineSegments(context, drawStyle, listOf(start, end))
            DrawQuality.QUALITY -> qualityLineDrawer.drawLineStrips(context, drawStyle, listOf(listOf(start, end)), listOf(listOf(true, true)), fringeWidth)
        }
    }

    /**
     * Draws a line segment from [start] to [end] using 3d coordinates
     */
    fun lineSegment(start: Vector3, end: Vector3) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineSegments(context, drawStyle, listOf(start, end))
            DrawQuality.QUALITY -> meshLineDrawer.drawLineSegments(context, drawStyle, listOf(start, end))
        }
    }

    fun lineSegments(segments: List<Vector2>) {
        val fringeWidth = 0.5 / modelViewScaling
        if (abs(modelViewScaling) < 1E-6){
            return
        }

        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineSegments(context, drawStyle, segments)
            DrawQuality.QUALITY -> {

                val pairs = (0 until segments.size / 2).map {
                    listOf(segments[it * 2], segments[it * 2 + 1])
                }
                val corners = pairs.map { it.map { true } }
                qualityLineDrawer.drawLineStrips(context, drawStyle, pairs, corners, fringeWidth)
            }
        }
    }

    fun lineSegments(segments: List<Vector2>, weights: List<Double>) {
        val fringeWidth = 0.5 / modelViewScaling
        if (abs(modelViewScaling) < 1E-6){
            return
        }

        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineSegments(context, drawStyle, segments)
            DrawQuality.QUALITY -> {
                val pairs = (0 until segments.size / 2).map {
                    listOf(segments[it * 2], segments[it * 2 + 1])
                }
                val corners = pairs.map { it.map { true } }
                qualityLineDrawer.drawLineStrips(context, drawStyle, pairs, corners, weights, fringeWidth)
            }
        }
    }

    @JvmName("lineSegments3d")
    fun lineSegments(segments: List<Vector3>) {
        when (drawStyle.quality) {
            DrawQuality.QUALITY -> meshLineDrawer.drawLineSegments(context, drawStyle, segments)
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineSegments(context, drawStyle, segments)
        }
    }

    @JvmName("lineSegments3d")
    fun lineSegments(segments: List<Vector3>, weights: List<Double>) {
        when (drawStyle.quality) {
            DrawQuality.QUALITY -> meshLineDrawer.drawLineSegments(context, drawStyle, segments, weights)
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineSegments(context, drawStyle, segments)
        }
    }

    @JvmName("lineSegments3d")
    fun lineSegments(segments: List<Vector3>, weights: List<Double>, colors: List<ColorRGBa>) {
        when (drawStyle.quality) {
            DrawQuality.QUALITY -> meshLineDrawer.drawLineSegments(context, drawStyle, segments, weights, colors)
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineSegments(context, drawStyle, segments)
        }
    }

    @JvmName("lineSegmentsFromLineSegmentList")
    fun lineSegments(segments: List<LineSegment>) {
        val fringeWidth = 1.0 / modelViewScaling
        if (abs(modelViewScaling) < 1E-6){
            return
        }

        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> {
                // TODO: a faster version would pass `segments` to
                // fastLineDrawer as is to avoid iterating over the points twice
                val points = segments.map { it.start } + segments.last().end
                fastLineDrawer.drawLineSegments(context, drawStyle, points)
            }
            DrawQuality.QUALITY -> {
                val pairs = segments.map {
                    listOf(it.start, it.end)
                }
                qualityLineDrawer.drawLineStrips(context, drawStyle, pairs, pairs.map { it.map { true } }, fringeWidth)
            }
        }
    }

    fun lineLoop(points: List<Vector2>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, listOf(points))
            DrawQuality.QUALITY -> qualityLineDrawer.drawLineLoops(context, drawStyle, listOf(points), listOf(points.map { true }))
        }
    }

    @JvmName("lineLoop3d")
    fun lineLoop(points: List<Vector3>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, listOf(points))
            DrawQuality.QUALITY -> meshLineDrawer.drawLineStrips(context, drawStyle, listOf(points), closed = listOf(true))
        }
    }

    fun lineLoops(loops: List<List<Vector2>>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, loops)
            DrawQuality.QUALITY -> qualityLineDrawer.drawLineLoops(context, drawStyle, loops, loops.map { it.map { true } })
        }
    }

    @JvmName("lineLoops3d")
    fun lineLoops(loops: List<List<Vector3>>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, loops)
            DrawQuality.QUALITY -> meshLineDrawer.drawLineStrips(context, drawStyle, loops, closed = List(loops.size) { true })
        }
    }

    fun lineLoops(loops: List<List<Vector2>>, weights: List<Double>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, loops)
            DrawQuality.QUALITY -> qualityLineDrawer.drawLineLoops(context, drawStyle, loops, loops.map { it.map { true } }, weights)
        }
    }

    @JvmName("lineLoops3d)")
    fun lineLoops(loops: List<List<Vector3>>, weights: List<Double>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, loops)
            DrawQuality.QUALITY -> meshLineDrawer.drawLineStrips(context, drawStyle, loops, weights, closed = List(loops.size) { true })
        }
    }

    /**
     * Draws a line strip with 2d coordinates
     */
    fun lineStrip(points: List<Vector2>) {
        val fringeWidth = 1.0 / modelViewScaling
        if (abs(modelViewScaling) < 1E-6){
            return
        }

        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, listOf(points))
            DrawQuality.QUALITY -> qualityLineDrawer.drawLineStrips(context, drawStyle, listOf(points), listOf(points.map { true }), fringeWidth)
        }
    }

    /**
     * Draws a line strip with 3d coordinates
     */
    @JvmName("lineStrip3d")
    fun lineStrip(points: List<Vector3>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, listOf(points))
            DrawQuality.QUALITY -> meshLineDrawer.drawLineStrips(context, drawStyle, listOf(points))
        }
    }

    /**
     * Draws line strips with 3d coordinates
     */
    fun lineStrips(strips: List<List<Vector2>>) {
        val fringeWidth = 1.0 / modelViewScaling
        if (abs(modelViewScaling) < 1E-6){
            return
        }

        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, strips)
            DrawQuality.QUALITY -> qualityLineDrawer.drawLineStrips(context, drawStyle, strips, strips.map { it.map { true } }, fringeWidth)
        }
    }

    /**
     * Draws line strips with 3d coordinates
     */
    @JvmName("lineStrips3d")
    fun lineStrips(strips: List<List<Vector3>>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, strips)
            DrawQuality.QUALITY -> meshLineDrawer.drawLineStrips(context, drawStyle, strips)
        }
    }

    /**
     * Draws line strips with 2d coordinates and stroke weights per strip
     */
    fun lineStrips(strips: List<List<Vector2>>, weights: List<Double>) {
        val fringeWidth = 1.0 / modelViewScaling
        if (abs(modelViewScaling) < 1E-6){
            return
        }

        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, strips)
            DrawQuality.QUALITY -> qualityLineDrawer.drawLineStrips(context, drawStyle, strips, strips.map { it.map { true } }, weights, fringeWidth )
        }
    }

    /**
     * Draws line strips with 3d coordinates and stroke weights per strip
     */
    @JvmName("lineStrips3d")
    fun lineStrips(strips: List<List<Vector3>>, weights: List<Double>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, strips)
            DrawQuality.QUALITY -> meshLineDrawer.drawLineStrips(context, drawStyle, strips, weights)
        }
    }

    /**
     * Draws line strips with 3d coordinates and stroke weights per strip
     */
    @JvmName("lineStrips3d")
    fun lineStrips(strips: List<List<Vector3>>, weights: List<Double>, colors: List<ColorRGBa>) {
        when (drawStyle.quality) {
            DrawQuality.PERFORMANCE -> fastLineDrawer.drawLineLoops(context, drawStyle, strips)
            DrawQuality.QUALITY -> meshLineDrawer.drawLineStrips(context, drawStyle, strips, weights, colors)
        }
    }

    /**
     * Draw a single segment
     * @see contour
     */
    fun segment(segment: Segment) {
        contour(ShapeContour(listOf(segment), false, YPolarity.CW_NEGATIVE_Y))
    }

    /**
     * Draws a single 3D segment
     */
    fun segment(segment: Segment3D) {
        lineStrip(segment.sampleAdaptive())
    }

    /**
     * Draws a list of 2D segments
     */
    @JvmName("segments2d")
    fun segments(segments: List<Segment>) {
        lineStrips(segments.map { it.adaptivePositions() })
    }

    /**
     * Draws a list of 3D segments
     */
    fun segments(segments: List<Segment3D>) {
        lineStrips(segments.map { it.sampleAdaptive() })
    }

    /**
     * Draws a list of 3D segments, each with their weight and color
     */
    fun segments(segments: List<Segment3D>, weights: List<Double>, colors: List<ColorRGBa>) {
        lineStrips(segments.map { it.sampleAdaptive() }, weights, colors)
    }

    /**
     * Draws a single 3D path
     * @param path the path to draw
     */
    fun path(path: Path3D) {
        lineStrip(path.adaptivePositions(0.03))
    }

    /**
     * Draws a [Composition]
     * @param composition The composition to draw
     * @see contour
     * @see contours
     * @see shape
     * @see shapes
     */
    fun composition(composition: Composition) {
        pushModel()
        pushStyle()
        fill = ColorRGBa.BLACK
        stroke = null

        // viewBox transformation
        model *= composition.calculateViewportTransform()

        fun node(compositionNode: CompositionNode) {
            pushModel()
            pushStyle()
            model *= compositionNode.effectiveTransform

            @Suppress("NON_EXHAUSTIVE_WHEN_ON_SEALED_CLASS")
            when (compositionNode.shadeStyle) {
                is CShadeStyle -> {
                    shadeStyle.let {
                        compositionNode.shadeStyle
                    }
                }
            }

            when (compositionNode) {
                is ShapeNode -> {
                    compositionNode.fill.let {
                        if (it is Color) fill = it.color
                    }
                    compositionNode.fillOpacity.let {
                        if (it is FillOpacity) fill = fill?.opacify(it.fillOpacity)
                    }
                    compositionNode.stroke.let {
                        if (it is Color) stroke = it.color
                    }
                    compositionNode.strokeOpacity.let {
                        if (it is StrokeOpacity) stroke = stroke?.opacify(it.strokeOpacity)
                    }
                    compositionNode.strokeWeight.let {
                        if (it is StrokeWeight) strokeWeight = it.weight
                    }
                    compositionNode.miterlimit.let {
                        if (it is Miterlimit) miterlimit = it.limit
                    }
                    compositionNode.lineCap.let {
                        if (it is org.openrndr.shape.LineCap) lineCap = it.cap
                    }
                    compositionNode.lineJoin.let {
                        if (it is org.openrndr.shape.LineJoin) lineJoin = it.join
                    }
                    compositionNode.opacity.let {
                        if (it is Opacity) {
                            stroke = stroke?.opacify(it.opacity)
                            fill = fill?.opacify(it.opacity)
                        }
                    }
                    shape(compositionNode.shape)
                }
                is ImageNode -> {
                    image(compositionNode.image)
                }
                is TextNode -> TODO()
                is GroupNode -> compositionNode.children.forEach { node(it) }
            }

            popModel()
            popStyle()
        }
        node(composition.root)
        popModel()
        popStyle()
    }

    /**
     * Draws a [source] area of an image ([ColorBuffer]) into a [target] area
     */
    fun image(colorBuffer: ColorBuffer, source: Rectangle, target: Rectangle) {
        imageDrawer.drawImage(context, drawStyle, colorBuffer, listOf(source to target))
    }

    /**
     * Draws an image with its top-left corner at ([x], [y]) and dimensions ([width], [height])
     */
    fun image(colorBuffer: ColorBuffer, x: Double, y: Double, width: Double = colorBuffer.width.toDouble(), height: Double = colorBuffer.height.toDouble()) {
        imageDrawer.drawImage(context, drawStyle, colorBuffer, x, y, width, height)
    }

    /**
     * Draws an image with its top-left corner at ([position]) and dimensions ([width], [height])
     */
    fun image(colorBuffer: ColorBuffer, position: Vector2, width: Double = colorBuffer.width.toDouble(), height: Double = colorBuffer.height.toDouble()) {
        imageDrawer.drawImage(context, drawStyle, colorBuffer, position.x, position.y, width, height)
    }

    /**
     * Draws an image with its top-left corner at (0,0)
     */
    fun image(colorBuffer: ColorBuffer) = image(colorBuffer, 0.0, 0.0)

    fun image(colorBuffer: ColorBuffer, rectangles: List<Pair<Rectangle, Rectangle>>) {
        imageDrawer.drawImage(context, drawStyle, colorBuffer, rectangles)
    }

    /**
     * Draws an image using an ArrayTexture as source
     */
    fun image(arrayTexture: ArrayTexture, layer: Int = 0, x: Double = 0.0, y: Double = 0.0,
              width: Double = arrayTexture.width.toDouble(), height: Double = arrayTexture.height.toDouble()) {
        imageDrawer.drawImage(context, drawStyle, arrayTexture, layer, x, y, width, height)
    }

    fun image(arrayTexture: ArrayTexture, layer: Int = 0, source: Rectangle, target: Rectangle) {
        imageDrawer.drawImage(context, drawStyle, arrayTexture, listOf(layer), listOf(source to target))
    }

    fun image(arrayTexture: ArrayTexture, layers: List<Int>, rectangles: List<Pair<Rectangle, Rectangle>>) {
        imageDrawer.drawImage(context, drawStyle, arrayTexture, layers, rectangles)
    }

    /**
     * Draws [text] at ([position])
     */
    fun text(text: String, position: Vector2) {
        if (fontMap is FontImageMap) {
            fontImageMapDrawer.drawText(context, drawStyle, text, position.x, position.y)
        }
    }

    /**
     * Draws [text] at ([x], [y])
     */
    fun text(text: String, x: Double = 0.0, y: Double = 0.0) {
        if (fontMap is FontImageMap) {
            fontImageMapDrawer.drawText(context, drawStyle, text, x, y)
        }
    }

    /**
     * Draws [texts] at [positions])
     */
    fun texts(texts: List<String>, positions: List<Vector2>) {
        if (fontMap is FontImageMap) {
            fontImageMapDrawer.drawTexts(context, drawStyle, texts, positions)
        }
    }

    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /**
     * Draws a [VertexBuffer] using [primitive]
     */
    fun vertexBuffer(
            vertexBuffer: VertexBuffer,
            primitive: DrawPrimitive,
            vertexOffset: Int = 0,
            vertexCount: Int = vertexBuffer.vertexCount
    ) {
        vertexBuffer(listOf(vertexBuffer), primitive, vertexOffset, vertexCount)
    }

    fun vertexBuffer(
            vertexBuffers: List<VertexBuffer>,
            primitive: DrawPrimitive,
            offset: Int = 0,
            vertexCount: Int = vertexBuffers[0].vertexCount
    ) {
        vertexBufferDrawer.drawVertexBuffer(context, drawStyle, primitive, vertexBuffers, offset, vertexCount)
    }

    fun vertexBuffer(
            indexBuffer: IndexBuffer,
            vertexBuffers: List<VertexBuffer>,
            primitive: DrawPrimitive,
            offset: Int = 0,
            indexCount: Int = indexBuffer.indexCount
    ) {
        vertexBufferDrawer.drawVertexBuffer(context, drawStyle, primitive, indexBuffer, vertexBuffers, offset, indexCount)
    }

    fun vertexBufferInstances(
            vertexBuffers: List<VertexBuffer>,
            instanceAttributes: List<VertexBuffer>,
            primitive: DrawPrimitive,
            instanceCount: Int,
            offset: Int = 0,
            vertexCount: Int = vertexBuffers[0].vertexCount
    ) {
        vertexBufferDrawer.drawVertexBufferInstances(context, drawStyle, primitive, vertexBuffers, instanceAttributes, offset, vertexCount, instanceCount)
    }

    fun vertexBufferInstances(
            indexBuffer: IndexBuffer,
            vertexBuffers: List<VertexBuffer>,
            instanceAttributes: List<VertexBuffer>,
            primitive: DrawPrimitive,
            instanceCount: Int,
            offset: Int = 0,
            indexCount: Int = indexBuffer.indexCount
    ) {
        vertexBufferDrawer.drawVertexBufferInstances(context, drawStyle, primitive, indexBuffer, vertexBuffers, instanceAttributes, offset, indexCount, instanceCount)
    }


    fun transform(transform: TransformTarget): KMutableProperty0<Matrix44> {
        return when (transform) {
            TransformTarget.PROJECTION -> this::projection
            TransformTarget.MODEL -> this::model
            TransformTarget.VIEW -> this::view
        }
    }
}

/**
 * Pushes style, view- and projection matrix, calls function and pops.
 * @param function the function that is called in the isolation
 */
fun Drawer.isolated(function: Drawer.() -> Unit) {
    pushTransforms()
    pushStyle()
    function()
    popStyle()
    popTransforms()
}

/**
 * Pushes style, view- and projection matrix, sets render target, calls function and pops,
 * @param function the function that is called in the isolation
 */
fun Drawer.isolatedWithTarget(target: RenderTarget, function: Drawer.() -> Unit) {
    target.bind()
    isolated(function)
    target.unbind()
}


private operator fun KMutableProperty0<Matrix44>.timesAssign(matrix: Matrix44) = set(get() * matrix)
