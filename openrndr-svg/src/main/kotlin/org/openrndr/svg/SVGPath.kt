package org.openrndr.svg

import org.jsoup.nodes.*
import org.openrndr.draw.LineCap
import org.openrndr.draw.LineJoin
import org.openrndr.math.*
import org.openrndr.shape.*

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
                        error("unsupported op: ${command.op}, is this an SVG Tiny 1.x document?")
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
                // Attr.D -> commands.addAll(SVGParse.path(this.element))
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