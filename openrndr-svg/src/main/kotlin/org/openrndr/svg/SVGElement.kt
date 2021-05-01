package org.openrndr.svg

import org.jsoup.nodes.*
import org.openrndr.math.*
import org.openrndr.math.min
import org.openrndr.math.transforms.*
import org.openrndr.shape.*
import kotlin.math.*

internal sealed class SVGElement(element: Element?) {
    var tag: String = element?.tagName() ?: ""
    var id: String = element?.id() ?: ""
    val className: String = element?.className() ?: ""

    var transform: Matrix44 = Matrix44.IDENTITY

    var fill: CompositionColor = InheritColor
    var stroke: CompositionColor = InheritColor
    var strokeWeight: CompositionStrokeWeight = InheritStrokeWeight
    var lineCap: CompositionLineCap = InheritLineCap
    var lineJoin: CompositionLineJoin = InheritLineJoin
    var miterlimit: CompositionMiterlimit = InheritMiterlimit
    var strokeOpacity: CompositionStrokeOpacity = InheritStrokeOpacity
    var fillOpacity: CompositionFillOpacity = InheritFillOpacity
    var opacity: CompositionOpacity = InheritOpacity

    private val delimiter = "\\d*".toRegex()
    val classList: List<String> = if (className.isBlank()) {
        emptyList()
    } else {
        className.split(delimiter)
    }

    abstract fun handleAttribute(attribute: Attribute)

    // Any element can have a style attribute to pass down properties
    fun styleProperty(key: String, value: String) {
        when (key) {
            Prop.FILL -> fill = Color(SVGParse.color(value))
            Prop.STROKE -> stroke = Color(SVGParse.color(value))
            Prop.STROKE_LINECAP -> lineCap = LineCap(org.openrndr.draw.LineCap.valueOf(value.toUpperCase()))
            Prop.STROKE_LINEJOIN -> lineJoin = LineJoin(org.openrndr.draw.LineJoin.valueOf(value.toUpperCase()))
            Prop.STROKE_MITERLIMIT -> miterlimit = Miterlimit(value.toDouble())
            Prop.STROKE_OPACITY -> strokeOpacity = StrokeOpacity(value.toDouble())
            Prop.STROKE_WIDTH -> strokeWeight = StrokeWeight(value.toDouble())
            Prop.FILL_OPACITY -> fillOpacity = FillOpacity(value.toDouble())
        }
    }

    /** Special case of parsing an inline style attribute. */
    fun inlineStyles(attribute: Attribute) {
        attribute.value.split(";").forEach {
            val result = it.split(":").map { s -> s.trim() }

            if (result.size >= 2) {
                styleProperty(result[0], result[1])
            }
        }
    }
}

/** <svg> element */
internal class SVGSVGElement(element: Element): SVGGroup(element), SVGFitToViewBox, SVGDimensions {
    override var viewBox: Rectangle? = SVGParse.viewBox(this.element)
    override var preserveAspectRatio: Alignment = SVGParse.preserveAspectRatio(this.element)

    override var bounds = SVGParse.bounds(this.element)
    /** Represents the scale and translate applied to the viewport */
    override var currentTransform = calculateViewportTransform()

    /**
     * Calculates effective viewport transformation using [viewBox] and [preserveAspectRatio].
     * As per [the SVG 2.0 spec](https://svgwg.org/svg2-draft/single-page.html#coords-ComputingAViewportsTransform)
     */
    fun calculateViewportTransform(): Matrix44 {
        return when {
            viewBox != null -> {
                // TODO! Someone tell me how to shorten this
                val vbCorner = viewBox!!.corner
                val vbDims = viewBox!!.dimensions
                // TODO! Do we need to know DPI at this point?
                // Should this function be in Composition.kt instead?
                val eCorner = bounds.position.vector2
                val eDims = bounds.dimensions.vector2
                val (xAlign, yAlign, meetOrSlice) = preserveAspectRatio

                var scale = eDims / vbDims

                if (xAlign != Align.NONE && yAlign != Align.NONE) {
                    scale = if (meetOrSlice == MeetOrSlice.MEET) {
                        Vector2(min(scale.x, scale.y))
                    } else {
                        Vector2(max(scale.x, scale.y))
                    }
                }

                var translate = eCorner - (vbCorner * scale)

                translate = when (xAlign) {
                    Align.MID -> translate.copy(x = translate.x + (eDims.x - vbDims.x * scale.x) / 2)
                    Align.MAX -> translate.copy(x = translate.x + (eDims.x - vbDims.x * scale.x))
                    else -> translate
                }

                translate = when (yAlign) {
                    Align.MID -> translate.copy(y = translate.y + (eDims.y - vbDims.y * scale.y) / 2)
                    Align.MAX -> translate.copy(y = translate.y + (eDims.y - vbDims.y * scale.y))
                    else -> translate
                }

                buildTransform {
                    translate(translate)
                    scale(scale.x, scale.y, 1.0)
                }
            }
            viewBox == Rectangle.EMPTY -> {
                // TODO! Questionable return lmao
                // The intent is to not display the element
                Matrix44.ZERO
            }
            else -> {
                Matrix44.IDENTITY
            }
        }
    }
}

/** <g> element but practically works with everything that has child elements */
internal open class SVGGroup(val element: Element, val elements: MutableList<SVGElement> = mutableListOf()) : SVGElement(element) {

    init {
        this.element.attributes().forEach {
            if (it.key == Attr.STYLE) {
                inlineStyles(it)
            } else {
                handleAttribute(it)
            }
        }

        handleChildren()
    }

    private fun handleChildren() {
        this.element.children().forEach { child ->
            when (child.tagName()) {
                in Tag.graphicsList -> elements.add(SVGPath(child))
                else -> elements.add(SVGGroup(child))
            }
        }
    }

    override fun handleAttribute(attribute: Attribute) {
        when (attribute.key) {
            // Attributes can also be style properties, in which case they're passed on
            in Prop.list -> styleProperty(attribute.key, attribute.value)
            Attr.TRANSFORM -> transform = SVGParse.transform(this.element)
        }
    }
}

internal class SVGPath(val element: Element? = null) : SVGElement(element) {
    val commands = mutableListOf<Command>()

    private fun compounds(): List<SVGPath> {
        val compounds = mutableListOf<SVGPath>()
        val compoundIndices = mutableListOf<Int>()

        commands.forEachIndexed { index, it ->
            if (it.op == "M" || it.op == "m") {
                compoundIndices.add(index)
            }
        }

        compoundIndices.forEachIndexed { index, _ ->
            val cs = compoundIndices[index]
            val ce = if (index + 1 < compoundIndices.size) (compoundIndices[index + 1]) else commands.size

            // TODO: We shouldn't be making new SVGPaths without Elements to provide
            val path = SVGPath()
            path.commands.addAll(commands.subList(cs, ce))

            compounds.add(path)
        }
        return compounds
    }

    fun shape(): Shape {
        var cursor = Vector2(0.0, 0.0)
        var anchor = cursor.copy()
        var relativeControl = Vector2(0.0, 0.0)

        val contours = compounds().map { compound ->
            val segments = mutableListOf<Segment>()
            var closed = false
            compound.commands.forEach { command ->
                when (command.op) {
                    "a", "A" -> {
                        command.operands.let {
                            val rx = it[0]
                            val ry = it[1]
                            val xAxisRot = it[2]
                            val largeArcFlag = it[3].toBoolean()
                            val sweepFlag = it[4].toBoolean()

                            var end = Vector2(it[5], it[6])

                            if (command.op == "a") end += cursor

                            val c = contour {
                                moveTo(cursor)
                                arcTo(rx, ry, xAxisRot, largeArcFlag, sweepFlag, end)
                            }.segments

                            segments += c
                            cursor = end
                        }
                    }
                    "M" -> {
                        cursor = command.vector(0, 1)
                        anchor = cursor

                        val allPoints = command.vectors()

                        for (i in 1 until allPoints.size) {
                            val point = allPoints[i]
                            segments += Segment(cursor, point)
                            cursor = point
                        }
                    }
                    "m" -> {
                        val allPoints = command.vectors()
                        cursor += command.vector(0, 1)
                        anchor = cursor

                        for (i in 1 until allPoints.size) {
                            val point = allPoints[i]
                            segments += Segment(cursor, cursor + point)
                            cursor += point
                        }
                    }
                    "L" -> {
                        val allPoints = command.vectors()

                        for (point in allPoints) {
                            segments += Segment(cursor, point)
                            cursor = point
                        }
                    }
                    "l" -> {
                        val allPoints = command.vectors()

                        for (point in allPoints) {
                            val target = cursor + point
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "h" -> {
                        for (operand in command.operands) {
                            val startCursor = cursor
                            val target = startCursor + Vector2(operand, 0.0)
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "H" -> {
                        for (operand in command.operands) {
                            val target = Vector2(operand, cursor.y)
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "v" -> {
                        for (operand in command.operands) {
                            val target = cursor + Vector2(0.0, operand)
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "V" -> {
                        for (operand in command.operands) {
                            val target = Vector2(cursor.x, operand)
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "C" -> {
                        val allPoints = command.vectors()
                        allPoints.windowed(3, 3).forEach { points ->
                            segments += Segment(cursor, points[0], points[1], points[2])
                            cursor = points[2]
                            relativeControl = points[1] - points[2]
                        }
                    }
                    "c" -> {
                        val allPoints = command.vectors()
                        allPoints.windowed(3, 3).forEach { points ->
                            segments += Segment(cursor, cursor + points[0], cursor + points[1], cursor.plus(points[2]))
                            relativeControl = (cursor + points[1]) - (cursor + points[2])
                            cursor += points[2]
                        }
                    }
                    "Q" -> {
                        val allPoints = command.vectors()
                        if ((allPoints.size) % 2 != 0) {
                            error("invalid number of operands for Q-op (operands=${allPoints.size})")
                        }
                        for (c in 0 until allPoints.size / 2) {
                            val points = allPoints.subList(c * 2, c * 2 + 2)
                            segments += Segment(cursor, points[0], points[1])
                            cursor = points[1]
                            relativeControl = points[0] - points[1]
                        }
                    }
                    "q" -> {
                        val allPoints = command.vectors()
                        if ((allPoints.size) % 2 != 0) {
                            error("invalid number of operands for q-op (operands=${allPoints.size})")
                        }
                        for (c in 0 until allPoints.size / 2) {
                            val points = allPoints.subList(c * 2, c * 2 + 2)
                            val target = cursor + points[1]
                            segments += Segment(cursor, cursor + points[0], target)
                            relativeControl = (cursor + points[0]) - (cursor + points[1])
                            cursor = target
                        }
                    }
                    "s" -> {
                        val reflected = relativeControl * -1.0
                        val cp0 = cursor + reflected
                        val cp1 = cursor + command.vector(0, 1)
                        val target = cursor + command.vector(2, 3)
                        segments += Segment(cursor, cp0, cp1, target)
                        cursor = target
                        relativeControl = cp1 - target
                    }
                    "S" -> {
                        val reflected = relativeControl * -1.0
                        val cp0 = cursor + reflected
                        val cp1 = command.vector(0, 1)
                        val target = command.vector(2, 3)
                        segments += Segment(cursor, cp0, cp1, target)
                        cursor = target
                        relativeControl = cp1 - target
                    }
                    "Z", "z" -> {
                        if ((cursor - anchor).length >= 0.001) {
                            segments += Segment(cursor, anchor)
                        }
                        closed = true
                    }
                    else -> {
                        error("unsupported path operand: ${command.op}")
                    }
                }
            }
            ShapeContour(segments, closed, YPolarity.CW_NEGATIVE_Y)
        }
        return Shape(contours)
    }

    override fun handleAttribute(attribute: Attribute) {
        if (this.element is Element) {
            when (attribute.key) {
                // Attributes can also be style properties, in which case they're passed on
                in Prop.list -> styleProperty(attribute.key, attribute.value)
                Attr.TRANSFORM -> transform = SVGParse.transform(this.element)
            }
        }
    }

    init {
        if (this.element is Element) {
            commands += when (tag) {
                Tag.PATH -> SVGParse.path(this.element)
                Tag.LINE -> SVGParse.line(this.element)
                Tag.RECT -> SVGParse.rectangle(this.element)
                Tag.ELLIPSE -> SVGParse.ellipse(this.element)
                Tag.CIRCLE -> SVGParse.circle(this.element)
                Tag.POLYGON -> SVGParse.polygon(this.element)
                Tag.POLYLINE -> SVGParse.polyline(this.element)
                else -> emptyList()
            }

            element.attributes().forEach {
                if (it.key == Attr.STYLE) {
                    inlineStyles(it)
                } else {
                    handleAttribute(it)
                }
            }
        }
    }
}