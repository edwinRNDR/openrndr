package org.openrndr.shape

import org.openrndr.color.ColorRGBa
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.rotateZ
import org.openrndr.math.transforms.scale
import org.openrndr.math.transforms.transform
import org.openrndr.math.transforms.translate
import java.util.*


/**
 * A Drawer-like interface for the creation of Compositions
 * This should be easier than creating Compositions manually
 */

class CompositionDrawer {
    val root = GroupNode()
    val composition = Composition(root)

    private var cursor = root
    private val modelStack = Stack<Matrix44>()

    var model = Matrix44.IDENTITY
    var fill: ColorRGBa? = null
    var stroke: ColorRGBa? = ColorRGBa.BLACK
    var strokeWeight = 1.0

    fun pushModel() {
        modelStack.push(model)
    }

    fun popModel() {
        model = modelStack.pop()
    }

    fun group(builder:CompositionDrawer.()->Unit) {

        val g = GroupNode()
        val oldCursor = cursor

        cursor.children.add(g)
        cursor = g
        builder()

        cursor = oldCursor
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

    fun contour(contour: ShapeContour) {
        val shape = Shape(listOf(contour))
        shape(shape)
    }

    fun contours(contours: List<ShapeContour>) = contours.forEach { contour(it) }

    fun shape(shape: Shape) {
        val shapeNode = ShapeNode(shape)
        shapeNode.transform = model
        shapeNode.fill = Color(fill)
        shapeNode.stroke = Color(stroke)
        shapeNode.strokeWeight = StrokeWeight(strokeWeight)
        cursor.children.add(shapeNode)
    }

    fun shapes(shapes: List<Shape>) = shapes.forEach { shape(it) }

    fun rectangle(rectangle: Rectangle) = contour(rectangle.contour)

    fun rectangle(x: Double, y: Double, width: Double, height: Double) = rectangle(Rectangle(x, y, width, height))

    fun rectangles(rectangles: List<Rectangle>) {
        rectangles.forEach {
            rectangle(it)
        }
    }

    fun rectangles(positions: List<Vector2>, width: Double, height: Double) = rectangles(positions.map {
        Rectangle(it, width, height)
    })

    fun rectangles(positions: List<Vector2>, dimensions: List<Vector2>) = rectangles((positions zip dimensions).map {
        Rectangle(it.first, it.second.x, it.second.y)
    })


    fun roundedRectangle(x: Double, y: Double, width: Double, height: Double, radius: Double) = contour(RoundedRectangle(x, y, width, height, radius).contour)

    fun roundedRectangle(position: Vector2, width: Double, height: Double, radius: Double) = contour(RoundedRectangle(position, width, height, radius).contour)

    fun roundedRectangle(roundedRectangle: RoundedRectangle) = contour(roundedRectangle.contour)

    fun circle(position: Vector2, radius: Double) = circle(Circle(position, radius))

    fun circle(circle: Circle) = contour(circle.contour)

    fun circles(circles: List<Circle>) {
        circles.forEach {
            circle(it)
        }
    }

    fun circles(positions: List<Vector2>, radius: Double) = circles(positions.map { Circle(it, radius) })

    fun circles(positions: List<Vector2>, radii: List<Double>) = circles((positions zip radii).map { Circle(it.first, it.second) })

    fun lineSegment(start: Vector2, end: Vector2) = lineSegment(LineSegment(start, end))

    fun lineSegment(lineSegment: LineSegment) = contour(lineSegment.contour)

    fun lineSegments(lineSegments: List<LineSegment>) {
        lineSegments.forEach {
            lineSegment(it)
        }
    }

    fun lineStrip(points: List<Vector2>) {
        contour(ShapeContour.fromPoints(points, false))
    }

    fun lineLoop(points: List<Vector2>) {
        contour(ShapeContour.fromPoints(points, true))
    }

    fun text(text: String, position: Vector2) {
        val g = GroupNode()
        g.transform = transform { translate(position.xy0 ) }
        g.children.add(TextNode(text, null).apply{
            this.fill = Color(this@CompositionDrawer.fill)
        })
        cursor.children.add(g)
    }

    fun textOnContour(text: String,path: ShapeContour) {
        cursor.children.add(TextNode(text, path))
    }

    fun texts(text: List<String>, positions: List<Vector2>) {
        (text zip positions).forEach {
            text(it.first, it.second)
        }
    }
}