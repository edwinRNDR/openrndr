package org.openrndr.svg

import mu.*
import org.jsoup.nodes.*
import org.openrndr.math.*
import org.openrndr.shape.*

private val logger = KotlinLogging.logger {}

internal sealed class SVGElement(element: Element?) {
    var tag: String = element?.tagName() ?: ""
    var id: String = element?.id() ?: ""

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

    abstract fun handleAttribute(attribute: Attribute)

    // Any element can have a style attribute to pass down properties
    fun styleProperty(key: String, value: String) {
        when (key) {
            Prop.FILL -> fill = Color(SVGParse.color(value))
            Prop.STROKE -> stroke = Color(SVGParse.color(value))
            Prop.STROKE_LINECAP -> lineCap = LineCap(org.openrndr.draw.LineCap.valueOf(value.uppercase()))
            Prop.STROKE_LINEJOIN -> lineJoin = LineJoin(org.openrndr.draw.LineJoin.valueOf(value.uppercase()))
            Prop.STROKE_MITERLIMIT -> miterlimit = Miterlimit(value.toDouble())
            Prop.STROKE_OPACITY -> strokeOpacity = StrokeOpacity(value.toDouble())
            Prop.STROKE_WIDTH -> strokeWeight = StrokeWeight(value.toDouble())
            Prop.FILL_OPACITY -> fillOpacity = FillOpacity(value.toDouble())
            Prop.OPACITY -> opacity = Opacity(value.toDouble())
            else -> logger.error("Unknown property: $key")
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
internal class SVGSVGElement(element: Element) : SVGGroup(element) {
    var viewBox: Rectangle? = SVGParse.viewBox(this.element)
    var preserveAspectRatio: Alignment = SVGParse.preserveAspectRatio(this.element)
    var bounds = SVGParse.bounds(this.element)
}

/** <g> element but practically works with anything that has child elements */
internal open class SVGGroup(val element: Element, val elements: MutableList<SVGElement> = mutableListOf()) :
    SVGElement(element) {

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

internal class Command(val op: String, vararg val operands: Double) {
    fun asVectorList(): List<Vector2>? {
        return if (operands.size % 2 == 0) {
            operands.asList().chunked(2) { Vector2(it[0], it[1]) }
        } else {
            null
        }
    }
}

// For evaluating elliptical arc arguments according to the SVG spec
internal fun Double.toBoolean(): Boolean? = when (this) {
    0.0 -> false
    1.0 -> true
    else -> null
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

            // TODO: We shouldn't be making new SVGPaths without Elements to provide.
            // Then we could make SVGPath's constructor non-nullable
            val path = SVGPath()
            path.commands.addAll(commands.subList(cs, ce))

            compounds.add(path)
        }
        return compounds
    }

    fun shape(): Shape {
        var cursor = Vector2.ZERO
        var anchor = Vector2.ZERO
        // Still problematic
        var prevCubicCtrlPoint: Vector2? = null
        var prevQuadCtrlPoint: Vector2? = null

        val contours = compounds().map { compound ->
            val segments = mutableListOf<Segment>()
            var closed = false
            // If an argument is invalid, an error is logged,
            // further interpreting is stopped and compound is returned as-is.
            compound.commands.forEach { command ->

                if (command.op !in listOf("z", "Z") && command.operands.isEmpty()) {
                    logger.error("Invalid amount of arguments provided for: ${command.op}")
                    return@forEach
                }

                val points = command.asVectorList()

                // TODO: Rethink this check
                if (points == null && command.op.lowercase() !in listOf("a", "h", "v")) {
                    logger.error("Invalid amount of arguments provided for: ${command.op}")
                    return@forEach
                }

                when (command.op) {
                    "A", "a" -> {
                        // If size == step, only the last window can be partial
                        // Special case as it also has boolean values
                        val contours = command.operands.toList().windowed(7, 7, true).map m@{
                            if (it.size != 7) {
                                logger.error("Invalid amount of arguments provided for: ${command.op}")
                                return@forEach
                            } else {
                                val rx = it[0]
                                val ry = it[1]
                                val xAxisRot = it[2]
                                val largeArcFlag = it[3].toBoolean()
                                val sweepFlag = it[4].toBoolean()

                                if (largeArcFlag == null || sweepFlag == null || rx == 0.0 || ry == 0.0) {
                                    logger.error("Invalid values provided for: ${command.op}")
                                    return@forEach
                                }

                                val end = Vector2(it[5], it[6]).let { v ->
                                    if (command.op == "a") {
                                        v + cursor
                                    } else {
                                        v
                                    }
                                }

                                contour {
                                    moveTo(cursor)
                                    arcTo(rx, ry, xAxisRot, largeArcFlag, sweepFlag, end)
                                    cursor = end
                                }
                            }
                        }

                        // I don't know why we can't just have segments from the above map,
                        // but this is the only way this works.
                        segments += contours.flatMap { it.segments}
                    }
                    "M" -> {
                        // TODO: Log an error when this nulls
                        cursor = points!!.firstOrNull() ?: return@forEach
                        anchor = cursor

                        // Following points are implicit lineto arguments
                        segments += points.drop(1).map {
                            Segment(cursor, it).apply {
                                cursor = it
                            }
                        }
                    }
                    "m" -> {
                        // TODO: Log an error when this nulls
                        cursor += points!!.firstOrNull() ?: return@forEach
                        anchor = cursor

                        // Following points are implicit lineto arguments
                        segments += points.drop(1).map {
                            Segment(cursor, cursor + it).apply {
                                cursor += it
                            }
                        }
                    }
                    "L" -> {
                        segments += points!!.map {
                            Segment(cursor, it).apply {
                                cursor = it
                            }
                        }
                    }
                    "l" -> {
                        segments += points!!.map {
                            Segment(cursor, cursor + it).apply {
                                cursor += it
                            }
                        }
                    }
                    "H" -> {
                        segments += command.operands.map {
                            val target = Vector2(it, cursor.y)
                            Segment(cursor, target).apply {
                                cursor = target
                            }
                        }
                    }
                    "h" -> {
                        segments += command.operands.map {
                            val target = cursor + Vector2(it, 0.0)
                            Segment(cursor, target).apply {
                                cursor = target
                            }
                        }
                    }
                    "V" -> {
                        segments += command.operands.map {
                            val target = Vector2(cursor.x, it)
                            Segment(cursor, target).apply {
                                cursor = target
                            }
                        }
                    }
                    "v" -> {
                        segments += command.operands.map {
                            val target = cursor + Vector2(0.0, it)
                            Segment(cursor, target).apply {
                                cursor = target
                            }
                        }
                    }
                    "C" -> {
                        segments += points!!.windowed(3, 3, true).map {
                            if (it.size != 3) {
                                logger.error("Invalid amount of arguments provided for: ${command.op}")
                                return@forEach
                            } else {
                                val (cp1, cp2, target) = it
                                Segment(cursor, cp1, cp2, target).also {
                                    cursor = target
                                    prevCubicCtrlPoint = cp2
                                }
                            }
                        }
                    }
                    "c" -> {
                        segments += points!!.windowed(3, 3, true).map {
                            if (it.size != 3) {
                                logger.error("Invalid amount of arguments provided for: ${command.op}")
                                return@forEach
                            } else {
                                val (cp1, cp2, target) = it.map { v -> cursor + v }
                                Segment(cursor, cp1, cp2, target).apply {
                                    cursor = target
                                    prevCubicCtrlPoint = cp2
                                }
                            }
                        }
                    }
                    "S" -> {
                        segments += points!!.windowed(2, 2, true).map {
                            if (it.size != 2) {
                                logger.error("Invalid amount of arguments provided for: ${command.op}")
                                return@forEach
                            } else {
                                val cp1 = 2.0 * cursor - (prevCubicCtrlPoint ?: cursor)
                                val (cp2, target) = it
                                Segment(cursor, cp1, cp2, target).also {
                                    cursor = target
                                    prevCubicCtrlPoint = cp2
                                }
                            }
                        }
                    }
                    "s" -> {
                        segments += points!!.windowed(2, 2, true).map {
                            if (it.size != 2) {
                                logger.error("Invalid amount of arguments provided for: ${command.op}")
                                return@forEach
                            } else {
                                val cp1 = 2.0 * cursor - (prevCubicCtrlPoint ?: cursor)
                                val (cp2, target) = it.map { v -> cursor + v }
                                Segment(cursor, cp1, cp2, target).also {
                                    cursor = target
                                    prevCubicCtrlPoint = cp2
                                }
                            }
                        }
                    }
                    "Q" -> {
                        segments += points!!.windowed(2, 2, true).map {
                            if (it.size != 2) {
                                logger.error("Invalid amount of arguments provided for: ${command.op}")
                                return@forEach
                            } else {
                                val (cp, target) = it
                                Segment(cursor, cp, target).also {
                                    cursor = target
                                    prevQuadCtrlPoint = cp
                                }
                            }
                        }
                    }
                    "q" -> {
                        segments += points!!.windowed(2, 2, true).map {
                            if (it.size != 2) {
                                logger.error("Invalid amount of arguments provided for: ${command.op}")
                                return@forEach
                            } else {
                                val (cp, target) = it.map { v -> cursor + v }
                                Segment(cursor, cp, target).also {
                                    cursor = target
                                    prevQuadCtrlPoint = cp
                                }
                            }
                        }
                    }
                    "T" -> {
                        points!!.forEach {
                            val cp = 2.0 * cursor - (prevQuadCtrlPoint ?: cursor)
                            Segment(cursor, cp, it).also { _ ->
                                cursor = it
                                prevQuadCtrlPoint = cp
                            }
                        }
                    }
                    "t" -> {
                        points!!.forEach {
                            val cp = 2.0 * cursor - (prevQuadCtrlPoint ?: cursor)
                            Segment(cursor, cp, cursor + it).also { _ ->
                                cursor = it
                                prevQuadCtrlPoint = cp
                            }
                        }
                    }
                    "Z", "z" -> {
                        if ((cursor - anchor).length >= 0.001) {
                            segments += Segment(cursor, anchor)
                        }
                        closed = true
                    }
                    else -> {
                        // The spec declares we should still attempt to render
                        // the path up until the erroneous command as to visually
                        // signal the user where the error occurred.
                        logger.error("Invalid path operator: ${command.op}")
                        return@forEach
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