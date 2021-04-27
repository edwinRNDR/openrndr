package org.openrndr.svg

import org.jsoup.nodes.*
import org.openrndr.color.*
import org.openrndr.math.*
import org.openrndr.math.transforms.*
import org.openrndr.shape.*
import java.util.regex.*
import kotlin.math.*

internal object SVGParse {

    // Matches a single numeric value
    private val numRegex = Regex("[+-]?\\d+")

    // Used in combination with another pattern as just it's a positive lookahead
    // and you generally don't actually want to match whitespace
    private val sepRegex = Regex("(?=\\s*,?\\s*)")

    // Captures a length value and its unit type if present
    private val lenRegex = Regex("(?<value>$numRegex)(?<type>in|pc|pt|px|cm|mm|q|em|ex|ch|%)?")
    private val numListRegex = Regex("$numRegex$sepRegex")

    // Captures alignment value and/or the meet value
    private val aspecRatioRegex = Regex("(?<align>[xy](?:Min|Mid|Max)[XY](?:Min|Mid|Max))|(?<meet>(?<=\\s|\\A)meet|slice)")

    fun viewBox(element: Element): Rectangle? {
        val viewBoxValue = element.attr(Attr.VIEW_BOX)

        val (minX, minY, width, height) = numListRegex.findAll(viewBoxValue).let {
            val list = it.toList()
            when (list.size) {
                2 -> listOf(list[0].value.toDouble(), list[1].value.toDouble(), 0.0, 0.0)
                4 -> list.map { item -> item.value.toDouble() }
                else -> return null
            }
        }

        return Rectangle(minX, minY, width.coerceAtLeast(0.0), height.coerceAtLeast(0.0))
    }

    fun preserveAspectRatio(element: Element): Alignment {
        val aspectRatioValue = element.attr(Attr.VIEW_BOX)

        val (alignmentValue, meetValue) = aspecRatioRegex.matchEntire(aspectRatioValue).let {
            val value = it?.groups?.get("align")?.value ?: "xMidYMid"
            val type = it?.groups?.get("meet")?.value ?: "meet"

            value to type
        }

        val meet = when (meetValue) {
            "slice" -> MeetOrSlice.SLICE
            // Lacuna value
            else -> MeetOrSlice.MEET
        }

        return when (alignmentValue) {
            "none" -> Alignment(Align.NONE, Align.NONE, meet)
            "xMinYMin" -> Alignment(Align.MIN, Align.MIN, meet)
            "xMidYMin" -> Alignment(Align.MID, Align.MIN, meet)
            "xMaxYMin" -> Alignment(Align.MAX, Align.MIN, meet)
            "xMinYMid" -> Alignment(Align.MIN, Align.MID, meet)
            "xMaxYMid" -> Alignment(Align.MAX, Align.MID, meet)
            "xMinYMax" -> Alignment(Align.MIN, Align.MAX, meet)
            "xMidYMax" -> Alignment(Align.MID, Align.MAX, meet)
            "xMaxYMax" -> Alignment(Align.MAX, Align.MAX, meet)
            // The lacuna value, "xMidYMid"
            else -> Alignment(Align.MID, Align.MID, meet)
        }
    }

    fun dimensions(element: Element): CompositionDimensions {
        val values = listOf(Attr.WIDTH, Attr.HEIGHT).map { attribute ->
            element.attr(attribute).let {
                it.ifEmpty { "0" }
            }
        }

        val lengths = values.map { str ->
            lenRegex.matchEntire(str).let {
                val value = it?.groups?.get("value")?.value?.toDouble() ?: 0.0
                val type = Length.UnitType.valueOf(
                    it?.groups?.get("type")?.value?.toUpperCase() ?: "PX"
                )

                Length(value, type)
            }
        }

        return CompositionDimensions(lengths[0], lengths[1])
    }

    fun position(element: Element): CompositionVector2 {
        val values = listOf(Attr.X, Attr.Y).map { attribute ->
            element.attr(attribute).let {
                it.ifEmpty { "0" }
            }
        }

        val lengths = values.map { str ->
            lenRegex.matchEntire(str).let {
                val value = it?.groups?.get("value")?.value?.toDouble() ?: 0.0
                val type = Length.UnitType.valueOf(
                    it?.groups?.get("type")?.value?.toUpperCase() ?: "PX"
                )

                Length(value, type)
            }
        }

        return CompositionVector2(lengths[0], lengths[1])
    }


    fun transform(element: Element): Matrix44 {
        var transform = Matrix44.IDENTITY

        val transformValue = element.attr(Attr.TRANSFORM).let {
            it.ifEmpty {
                return transform
            }
        }

        val p = Pattern.compile("(matrix|translate|scale|rotate|skewX|skewY)\\([\\d\\.,\\-\\s]+\\)")
        val m = p.matcher(transformValue)

        fun getTransformOperands(token: String): List<Double> {
            val number = Pattern.compile("-?[0-9.eE\\-]+")
            val nm = number.matcher(token)
            val operands = mutableListOf<Double>()
            while (nm.find()) {
                val n = nm.group().toDouble()
                operands.add(n)
            }
            return operands
        }
        while (m.find()) {
            val token = m.group()
            if (token.startsWith("matrix")) {
                val operands = getTransformOperands(token)
                val mat = Matrix44(
                    operands[0], operands[2], 0.0, operands[4],
                    operands[1], operands[3], 0.0, operands[5],
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0
                )
                transform *= mat
            }
            if (token.startsWith("scale")) {
                val operands = getTransformOperands(token.substring(5))
                val mat = Matrix44.scale(operands[0], operands.elementAtOrElse(1) { operands[0] }, 0.0)
                transform *= mat
            }
            if (token.startsWith("translate")) {
                val operands = getTransformOperands(token.substring(9))
                val mat = Matrix44.translate(operands[0], operands.elementAtOrElse(1) { 0.0 }, 0.0)
                transform *= mat
            }
            if (token.startsWith("rotate")) {
                val operands = getTransformOperands(token.substring(6))
                val angle = Math.toRadians(operands[0])
                val sina = sin(angle)
                val cosa = cos(angle)
                val x = operands.elementAtOrElse(1) { 0.0 }
                val y = operands.elementAtOrElse(2) { 0.0 }
                val mat = Matrix44(
                    cosa, -sina, 0.0, -x * cosa + y * sina + x,
                    sina, cosa, 0.0, -x * sina - y * cosa + y,
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0
                )
                transform *= mat
            }
            if (token.startsWith("skewX")) {
                val operands = getTransformOperands(token.substring(5))
                val mat = Matrix44(
                    1.0, tan(Math.toRadians(operands[0])), 0.0, 0.0,
                    0.0, 1.0, 0.0, 0.0,
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0
                )
                transform *= mat
            }
            if (token.startsWith("skewY")) {
                val operands = getTransformOperands(token.substring(5))
                val mat = Matrix44(
                    1.0, 0.0, 0.0, 0.0,
                    tan(Math.toRadians(operands[0])), 1.0, 0.0, 0.0,
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0
                )
                transform *= mat
            }
        }

        return transform
    }

    private fun pointsToCommands(pointsValues: String): List<Command> {
        val commands = mutableListOf<Command>()
        val tokens = pointsValues.split("[ ,\n]+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        val points = (0 until tokens.size / 2).map { Vector2(tokens[it * 2].toDouble(), tokens[it * 2 + 1].toDouble()) }
        commands.add(Command("M", points[0].x, points[0].y))
        (1 until points.size).mapTo(commands) { Command("L", points[it].x, points[it].y) }

        return commands
    }

    fun polygon(element: Element): List<Command> {
        // TODO: Add more reliable check if it's a valid collection of points
        val pointsValues = element.attr(Attr.POINTS)

        val commands = pointsToCommands(pointsValues) as MutableList
        commands.add(Command("Z"))

        return commands
    }

    fun polyline(element: Element): List<Command> {
        // TODO: Add more reliable check if it's a valid collection of points
        val pointsValues = element.attr(Attr.POINTS)

        return pointsToCommands(pointsValues) as MutableList
    }

    private fun ellipsePath(x: Double, y: Double, width: Double, height: Double): List<Command> {
        val dx = x - width / 2
        val dy = y - height / 2

        val kappa = 0.5522848
        // control point offset horizontal
        val ox = width / 2 * kappa
        // control point offset vertical
        val oy = height / 2 * kappa
        // x-end
        val xe = dx + width
        // y-end
        val ye = dy + height
        // x-middle
        val xm = dx + width / 2
        // y-middle
        val ym = dy + height / 2

        return listOf(
            Command("M", dy, ym),
            Command("C", dy, ym - oy, xm - ox, dy, xm, dy),
            Command("C", xm + ox, dy, xe, ym - oy, xe, ym),
            Command("C", xe, ym + oy, xm + ox, ye, xm, ye),
            Command("C", xm - ox, ye, dx, ym + oy, dx, ym),
            Command("z")
        )
    }

    fun circle(element: Element): List<Command> {
        val cxValue = element.attr(Attr.CX)
        val cyValue = element.attr(Attr.CY)
        val rValue = element.attr(Attr.R)

        val x = if (cxValue.isEmpty()) 0.0 else cxValue.toDouble()
        val y = if (cyValue.isEmpty()) 0.0 else cyValue.toDouble()
        val r = if (rValue.isEmpty()) 0.0 else rValue.toDouble() * 2.0

        return ellipsePath(x, y, r, r)
    }

    fun ellipse(element: Element): List<Command> {
        val cxValue = element.attr(Attr.CX)
        val cyValue = element.attr(Attr.CY)
        val rxValue = element.attr(Attr.RX)
        val ryValue = element.attr(Attr.RY)

        val x = if (cxValue.isEmpty()) 0.0 else cxValue.toDouble()
        val y = if (cyValue.isEmpty()) 0.0 else cyValue.toDouble()
        val width = if (rxValue.isEmpty()) 0.0 else rxValue.toDouble() * 2.0
        val height = if (ryValue.isEmpty()) 0.0 else ryValue.toDouble() * 2.0

        return ellipsePath(x, y, width, height)
    }

    fun rectangle(element: Element): List<Command> {
        val x = element.attr(Attr.X).let { if (it.isEmpty()) 0.0 else it.toDouble() }
        val y = element.attr(Attr.Y).let { if (it.isEmpty()) 0.0 else it.toDouble() }
        val width = element.attr(Attr.WIDTH).toDouble()
        val height = element.attr(Attr.HEIGHT).toDouble()

        return listOf(
            Command("M", x, y),
            Command("h", width),
            Command("v", height),
            Command("h", -width),
            Command("z")
        )
    }

    fun line(element: Element): List<Command> {
        // TODO: Error handling?
        val x1 = element.attr(Attr.X1).toDouble()
        val x2 = element.attr(Attr.X2).toDouble()
        val y1 = element.attr(Attr.Y1).toDouble()
        val y2 = element.attr(Attr.Y2).toDouble()

        return listOf(
            Command("M", x1, y1),
            Command("L", x2, y2)
        )
    }

    fun path(element: Element): List<Command> {
        val pathValue = element.attr(Attr.D)

        val rawCommands = pathValue.split("(?=[MmZzLlHhVvCcSsQqTtAa])".toRegex()).dropLastWhile { it.isEmpty() }
        val numbers = Pattern.compile("[-+]?[0-9]*[.]?[0-9]+(?:[eE][-+]?[0-9]+)?")
        val arcOpReg = Pattern.compile("[aA]")

        val commands = mutableListOf<Command>()

        for (rawCommand in rawCommands) {
            if (rawCommand.isNotEmpty()) {
                // Special case for arcTo command where the "numbers" RegExp breaks
                if (arcOpReg.matcher(rawCommand[0].toString()).find()) {
                    arcCommand(rawCommand.substring(1)).forEach {
                        val operands = it.map { operand -> operand.toDouble() }

                        commands += Command(rawCommand[0].toString(), *(operands.toDoubleArray()))
                    }
                } else {
                    val numberMatcher = numbers.matcher(rawCommand)
                    val operands = mutableListOf<Double>()
                    while (numberMatcher.find()) {
                        operands.add(numberMatcher.group().toDouble())
                    }
                    commands += Command(rawCommand[0].toString(), *(operands.toDoubleArray()))
                }
            }
        }

        return commands
    }


    internal fun color(scolor: String): ColorRGBa? {
        return when {
            scolor.isEmpty() || scolor == "none" -> null
            scolor.startsWith("#") -> {
                val normalizedColor = normalizeColorHex(scolor).replace("#", "")
                val v = normalizedColor.toLong(radix = 16)
                val vi = v.toInt()
                val r = vi shr 16 and 0xff
                val g = vi shr 8 and 0xff
                val b = vi and 0xff
                ColorRGBa(r / 255.0, g / 255.0, b / 255.0, 1.0)
            }
            scolor.startsWith("rgb(") -> rgbFunction(scolor)
            scolor == "white" -> ColorRGBa.WHITE
            scolor == "silver" -> ColorRGBa.fromHex(0xc0c0c0)
            scolor == "gray" -> ColorRGBa.fromHex(0x808080)
            scolor == "black" -> ColorRGBa.BLACK
            scolor == "red" -> ColorRGBa.RED
            scolor == "maroon" -> ColorRGBa.fromHex(0x800000)
            scolor == "yellow" -> ColorRGBa.fromHex(0xffff00)
            scolor == "olive" -> ColorRGBa.fromHex(0x808000)
            scolor == "lime" -> ColorRGBa.fromHex(0x00ff00)
            scolor == "green" -> ColorRGBa.fromHex(0x008000)
            scolor == "aqua" -> ColorRGBa.fromHex(0x00ffff)
            scolor == "teal" -> ColorRGBa.fromHex(0x008080)
            scolor == "blue" -> ColorRGBa.fromHex(0x0000ff)
            scolor == "navy" -> ColorRGBa.fromHex(0x000080)
            scolor == "fuchsia" -> ColorRGBa.fromHex(0xff00ff)
            scolor == "purple" -> ColorRGBa.fromHex(0x800080)
            scolor == "orange" -> ColorRGBa.fromHex(0xffa500)
            else -> error("could not parse color: $scolor")
        }
    }

    fun normalizeColorHex(colorHex: String): String {
        val colorHexRegex = "#?([0-9a-f]{3,6})".toRegex(RegexOption.IGNORE_CASE)

        val matchResult = colorHexRegex.matchEntire(colorHex)
            ?: error("The provided colorHex '$colorHex' is not a valid color hex for the SVG spec")

        val hexValue = matchResult.groups[1]!!.value.toLowerCase()
        val normalizedArgb = when (hexValue.length) {
            3 -> expandToTwoDigitsPerComponent("f$hexValue")
            6 -> hexValue
            else -> error("The provided colorHex '$colorHex' is not in a supported format")
        }

        return "#$normalizedArgb"
    }

    /**
     * Parses rgb functional notation as described in CSS2 spec
     */
    fun rgbFunction(rgbValue: String): ColorRGBa {
        // Matches either rgb(0, 0, 0) or rgb(0%, 0%, 0%) and captures the component values (without '%').
        // Shorten it if you dare (probably not possible unless you can discard all whitespace easily).
        val rgbRegex =
            "rgb\\(\\s*(?>(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)|\\s*(\\d+)%\\s*,\\s*(\\d+)%\\s*,\\s*(\\d+)%)\\s*\\)".toRegex()

        val result =
            rgbRegex.matchEntire(rgbValue) ?: error("The provided rgb functional notation '$rgbValue' is invalid.")

        // The first three capture groups contain values if the match was without percentages
        // Otherwise the values are in capture groups #4 to #6.
        // Based on this information, we can deduce the divisor.
        val divisor = if (result.groups[1] == null) {
            100.0
        } else {
            255.0
        }

        // Drop full match, filter out empty matches, map it, deconstruct it
        val (r, g, b) = result.groupValues
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.toDouble().coerceIn(0.0..divisor) / divisor }
        return ColorRGBa(r, g, b)
    }

    fun expandToTwoDigitsPerComponent(hexValue: String) =
        hexValue.asSequence()
            .map { "$it$it" }
            .reduce { accumulatedHex, component -> accumulatedHex + component }

    fun arcCommand(p: String): List<List<String>> {
        val sepReg = Pattern.compile(",|\\s")
        val boolReg = Pattern.compile("[01]")

        var cursor = 0
        var group = ""
        val groups = mutableListOf<String>()
        val commands = mutableListOf<List<String>>()

        while (cursor <= p.lastIndex) {
            val token = p[cursor].toString()

            if (sepReg.matcher(token).find()) {
                if (group.isNotEmpty()) {
                    groups.add(group)
                }

                group = ""
            } else {
                group += token

                if ((boolReg.matcher(token).find() && (groups.size in 3..5)) || cursor == p.lastIndex) {
                    if (group.isNotEmpty()) {
                        groups.add(group)
                    }

                    group = ""
                }
            }

            if (groups.size == 7) {
                commands.add(groups.toList())
                groups.clear()
                group = ""
            }

            cursor++
        }

        return commands
    }
}