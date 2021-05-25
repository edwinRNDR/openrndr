package org.openrndr.svg

import org.jsoup.nodes.*
import org.openrndr.color.*
import org.openrndr.math.*
import org.openrndr.math.transforms.*
import org.openrndr.shape.*
import java.util.regex.*
import kotlin.math.*
import kotlin.text.MatchResult

internal sealed interface PropertyRegex {

    val regex: Regex

    companion object {
        const val wsp = "(?:\\s|\\A|\\Z)+"
        const val commaWsp = "(?:\\s*,\\s*|\\s+)"
        const val align = "(?<align>[xy](?:Min|Mid|Max)[XY](?:Min|Mid|Max))*"
        const val meetOrSlice = "(?<meetOrSlice>meet|slice)*"
        const val unitIdentifier = "in|pc|pt|px|cm|mm|Q"
        val opts = RegexOption.IGNORE_CASE
    }

    object Any : PropertyRegex {
        override val regex = ".+".toRegex()
    }

    object Number : PropertyRegex {
        override val regex = "[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?".toRegex()
    }

    object NumberList : PropertyRegex {
        override val regex = "(?:${Number.regex}$commaWsp${Number.regex}$commaWsp?)+".toRegex()
    }

    object Length : PropertyRegex {
        override val regex = "(?<number>${Number.regex})(?<ident>$unitIdentifier)?".toRegex(opts)
    }

    object Percentage : PropertyRegex {
        override val regex = "${Number.regex}%".toRegex()
    }

    object LengthOrPercentage : PropertyRegex {
        override val regex = "${Length.regex}|${Percentage.regex}".toRegex(opts)
    }

    object PreserveAspectRatio : PropertyRegex {
        override val regex = "$wsp${align}$wsp${meetOrSlice}$wsp".toRegex(opts)
    }

    object RGBHex : PropertyRegex {
        override val regex = "#?([0-9a-f]{3,6})".toRegex(opts)
    }

    object RGBFunctional : PropertyRegex {
        // Matches rgb(255, 255, 255)
        private val rgb8BitRegex = "(${Number.regex})${commaWsp}(${Number.regex})${commaWsp}(${Number.regex})"

        // Matches rgb(100%, 100%, 100%)
        private val rgbPercentageRegex = "(${Number.regex})%${commaWsp}(${Number.regex})%${commaWsp}(${Number.regex})%"

        override val regex = "${wsp}rgb\\(\\s*(?>$rgb8BitRegex\\s*|\\s*$rgbPercentageRegex)\\s*\\)$wsp".toRegex(opts)
    }
}

internal object SVGParse {
    fun viewBox(element: Element): ViewBox {
        val viewBoxValue = element.attr(Attr.VIEW_BOX)

        val (minX, minY, width, height) = PropertyRegex.NumberList.regex.matches(viewBoxValue).let {
            val list = viewBoxValue.split(PropertyRegex.commaWsp.toRegex())
            when (list.size) {
                // Early return and signal that the element should not be rendered at all
                1 -> if (list[0].toDoubleOrNull() == 0.0 || list[0].toDoubleOrNull() == null) {
                    return ViewBox.None
                } else {
                    // Interpret as height
                    listOf(0.0, 0.0, 0.0, list[0].toDouble())
                }
                2 -> listOf(0.0, 0.0, list[0].toDouble(), list[1].toDouble())
                3 -> listOf(0.0, list[0].toDouble(), list[1].toDouble(), list[2].toDouble())
                4 -> list.map { item -> item.toDouble() }
                else -> return ViewBox.Initial
            }
        }

        return ViewBox.Value(Rectangle(minX, minY, width.coerceAtLeast(0.0), height.coerceAtLeast(0.0)))
    }

    fun preserveAspectRatio(element: Element): AspectRatio {
        val aspectRatioValue = element.attr(Attr.PRESERVE_ASPECT_RATIO)

        val (alignmentValue, meetValue) = PropertyRegex.PreserveAspectRatio.regex.matchEntire(aspectRatioValue).let {
            val value = (it?.groups as? MatchNamedGroupCollection)?.get("align")?.value
            val type = (it?.groups as? MatchNamedGroupCollection)?.get("meetOrSlice")?.value

            value to type
        }

        val meet = when (meetValue) {
            "slice" -> MeetOrSlice.SLICE
            // Lacuna value
            else -> MeetOrSlice.MEET
        }

        return when (alignmentValue) {
            "none" -> AspectRatio(Align.NONE, meet)
            "xMinYMin" -> AspectRatio(Align.X_MIN_Y_MIN, meet)
            "xMidYMin" -> AspectRatio(Align.X_MID_Y_MIN, meet)
            "xMaxYMin" -> AspectRatio(Align.X_MAX_Y_MIN, meet)
            "xMinYMid" -> AspectRatio(Align.X_MIN_Y_MID, meet)
            "xMidYMid" -> AspectRatio(Align.X_MID_Y_MID, meet)
            "xMaxYMid" -> AspectRatio(Align.X_MAX_Y_MID, meet)
            "xMinYMax" -> AspectRatio(Align.X_MIN_Y_MAX, meet)
            "xMidYMax" -> AspectRatio(Align.X_MID_Y_MAX, meet)
            "xMaxYMax" -> AspectRatio(Align.X_MAX_Y_MAX, meet)
            else -> AspectRatio(Align.X_MID_Y_MID, meet)
        }
    }

    fun bounds(element: Element): CompositionDimensions {
        val values = listOf(Attr.X, Attr.Y, Attr.WIDTH, Attr.HEIGHT).map { attribute ->
            element.attr(attribute).let {
                it.ifEmpty { "0" }
            }
        }

        // There's no way this'll throw an OOB, right?
        val (x, y, width, height) = values.map { str ->
            PropertyRegex.Length.regex.matchEntire(str).let {
                val value = (it?.groups as? MatchNamedGroupCollection)?.get("number")?.value?.toDouble() ?: 0.0
                val type = Length.UnitIdentifier.valueOf(
                    (it?.groups as? MatchNamedGroupCollection)?.get("ident")?.value?.uppercase() ?: "PX"
                )

                when (type) {
                    Length.UnitIdentifier.IN -> Length.Pixels.fromInches(value)
                    Length.UnitIdentifier.PC -> Length.Pixels.fromPicas(value)
                    Length.UnitIdentifier.PT -> Length.Pixels.fromPoints(value)
                    Length.UnitIdentifier.PX -> Length.Pixels(value)
                    Length.UnitIdentifier.CM -> Length.Pixels.fromCentimeters(value)
                    Length.UnitIdentifier.MM -> Length.Pixels.fromMillimeters(value)
                    Length.UnitIdentifier.Q -> Length.Pixels.fromQuarterMillimeters(value)
                }
            }
        }

        return CompositionDimensions(x, y, width, height)
    }

    fun lineJoin(value: String): LineJoin {
        return when (value) {
            "miter" -> LineJoin.Miter
            "bevel" -> LineJoin.Bevel
            "round" -> LineJoin.Round
            else -> LineJoin.Miter
        }
    }

    fun lineCap(value: String): LineCap {
        return when (value) {
            "round" -> LineCap.Round
            "butt" -> LineCap.Butt
            "square" -> LineCap.Square
            else -> LineCap.Butt
        }
    }

    fun number(value: String): Numeric {
        return when (val match = PropertyRegex.Number.regex.matchEntire(value)) {
            is MatchResult -> Numeric.Rational(match.groups[0]?.value?.toDouble() ?: 0.0)
            else -> Numeric.Rational(0.0)
        }
    }

    fun length(value: String): Length {
        val (number, ident) = PropertyRegex.Length.regex.matchEntire(value).let {
            val number = (it?.groups as? MatchNamedGroupCollection)?.get("number")?.value?.toDouble() ?: 0.0
            val ident = Length.UnitIdentifier.valueOf(
                (it?.groups as? MatchNamedGroupCollection)?.get("ident")?.value?.uppercase() ?: "PX"
            )

            number to ident
        }

        return when (ident) {
            Length.UnitIdentifier.IN -> Length.Pixels.fromInches(number)
            Length.UnitIdentifier.PC -> Length.Pixels.fromPicas(number)
            Length.UnitIdentifier.PT -> Length.Pixels.fromPoints(number)
            Length.UnitIdentifier.PX -> Length.Pixels(number)
            Length.UnitIdentifier.CM -> Length.Pixels.fromCentimeters(number)
            Length.UnitIdentifier.MM -> Length.Pixels.fromMillimeters(number)
            Length.UnitIdentifier.Q -> Length.Pixels.fromQuarterMillimeters(number)
        }
    }

    // Syntax should map to https://www.w3.org/TR/css-transforms-1/#svg-syntax
    fun transform(element: Element): Transform {
        var transform = Matrix44.IDENTITY

        val transformValue = element.attr(Attr.TRANSFORM).let {
            it.ifEmpty {
                return Transform.None
            }
        }

        // TODO: Number regex accepts `-` as a number lol
        val p = Pattern.compile("(matrix|translate|scale|rotate|skewX|skewY)\\([\\d\\.,\\-\\s]+\\)")
        val m = p.matcher(transformValue)

        // TODO: This looks to be making far too many assumptions about the well-formedness of its input
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

        return if (transform != Matrix44.IDENTITY) {
            Transform.Matrix(transform)
        } else {
            Transform.None
        }
    }

    private fun pointsToCommands(pointsValues: String): List<Command> {
        val commands = mutableListOf<Command>()
        val tokens = pointsValues.split(PropertyRegex.commaWsp.toRegex())
            .map(String::trim)
            .filter(String::isNotEmpty)
        val points = (0 until tokens.size / 2).map { Vector2(tokens[it * 2].toDouble(), tokens[it * 2 + 1].toDouble()) }
        commands.add(Command("M", points[0].x, points[0].y))
        (1 until points.size).mapTo(commands) { Command("L", points[it].x, points[it].y) }

        return commands
    }

    fun polygon(element: Element): List<Command> {
        val pointsValues = element.attr(Attr.POINTS)

        PropertyRegex.NumberList.regex.matchEntire(pointsValues) ?: return emptyList()
        val list = pointsValues.split(PropertyRegex.commaWsp.toRegex()).let {
            if (it.size % 2 != 0) {
                it.dropLast(1)
            } else {
                it
            }
        }

        val commands = pointsToCommands(list.reduce(String::plus)) as MutableList
        commands.add(Command("Z"))

        return commands
    }

    fun polyline(element: Element): List<Command> {
        val pointsValues = element.attr(Attr.POINTS)

        PropertyRegex.NumberList.regex.matchEntire(pointsValues) ?: return emptyList()
        val list = pointsValues.split(PropertyRegex.commaWsp.toRegex()).let {
            if (it.size % 2 != 0) {
                it.dropLast(1)
            } else {
                it
            }
        }

        return pointsToCommands(list.reduce(String::plus)) as MutableList
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

        return listOf(
            Command("M", dx, y),
            Command("C", dx, y - oy, x - ox, dy, x, dy),
            Command("C", x + ox, dy, xe, y - oy, xe, y),
            Command("C", xe, y + oy, x + ox, ye, x, ye),
            Command("C", x - ox, ye, dx, y + oy, dx, y),
            Command("z")
        )
    }

    fun circle(element: Element): List<Command> {
        val cx = element.attr(Attr.CX).let {
            if (it.isEmpty()) 0.0 else it.toDoubleOrNull()
        } ?: return emptyList()
        val cy = element.attr(Attr.CY).let {
            if (it.isEmpty()) 0.0 else it.toDoubleOrNull()
        } ?: return emptyList()
        val r = element.attr(Attr.R).let {
            if (it.isEmpty()) 0.0 else it.toDoubleOrNull()?.times(2.0)
        } ?: return emptyList()

        return ellipsePath(cx, cy, r, r)
    }

    fun ellipse(element: Element): List<Command> {
        val cx = element.attr(Attr.CX).let {
            if (it.isEmpty()) 0.0 else it.toDoubleOrNull()
        } ?: return emptyList()
        val cy = element.attr(Attr.CY).let {
            if (it.isEmpty()) 0.0 else it.toDoubleOrNull()
        } ?: return emptyList()
        val rx = element.attr(Attr.RX).let {
            if (it.isEmpty()) 0.0 else it.toDoubleOrNull()?.times(2.0)
        } ?: return emptyList()
        val ry = element.attr(Attr.RY).let {
            if (it.isEmpty()) 0.0 else it.toDoubleOrNull()?.times(2.0)
        } ?: return emptyList()

        return ellipsePath(cx, cy, rx, ry)
    }

    fun rectangle(element: Element): List<Command> {
        val x = element.attr(Attr.X).let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() } ?: return emptyList()
        val y = element.attr(Attr.Y).let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() } ?: return emptyList()
        val width = element.attr(Attr.WIDTH).toDoubleOrNull() ?: return emptyList()
        val height = element.attr(Attr.HEIGHT).toDoubleOrNull() ?: return emptyList()

        return listOf(
            Command("M", x, y),
            Command("h", width),
            Command("v", height),
            Command("h", -width),
            Command("z")
        )
    }

    fun line(element: Element): List<Command> {
        val x1 = element.attr(Attr.X1).toDoubleOrNull() ?: return emptyList()
        val x2 = element.attr(Attr.X2).toDoubleOrNull() ?: return emptyList()
        val y1 = element.attr(Attr.Y1).toDoubleOrNull() ?: return emptyList()
        val y2 = element.attr(Attr.Y2).toDoubleOrNull() ?: return emptyList()

        return listOf(
            Command("M", x1, y1),
            Command("L", x2, y2)
        )
    }

    fun path(element: Element): List<Command> {
        val pathValue = element.attr(Attr.D)

        if (pathValue.trim() == "none") {
            return emptyList()
        }

        val rawCommands = pathValue.split("(?=[MmZzLlHhVvCcSsQqTtAa])".toRegex()).map(String::trim)
        val numbers = Pattern.compile("[-+]?[0-9]*[.]?[0-9]+(?:[eE][-+]?[0-9]+)?")
        val commands = mutableListOf<Command>()

        for (rawCommand in rawCommands) {
            if (rawCommand.isNotEmpty()) {
                val numberMatcher = numbers.matcher(rawCommand)
                val operands = mutableListOf<Double>()
                while (numberMatcher.find()) {
                    operands.add(numberMatcher.group().toDouble())
                }
                commands += Command(rawCommand[0].toString(), *(operands.toDoubleArray()))
            }
        }

        return commands
    }

    fun color(colorValue: String): Paint {
        val col = colorValue.lowercase()

        return when {
            col.isEmpty() -> Paint.None
            col.startsWith("#") -> {
                val normalizedColor = normalizeColorHex(col) ?: return Paint.None
                val v = normalizedColor.toLong(radix = 16)
                val vi = v.toInt()
                val r = vi shr 16 and 0xff
                val g = vi shr 8 and 0xff
                val b = vi and 0xff
                Paint.RGB(ColorRGBa(r / 255.0, g / 255.0, b / 255.0, 1.0))
            }
            col.startsWith("rgb(") -> rgbFunction(col)
            col in cssColorNames -> Paint.RGB(ColorRGBa.fromHex(cssColorNames[col]!!))
            else -> Paint.None
        }
    }

    private fun normalizeColorHex(colorHex: String): String? {
        val matchResult = PropertyRegex.RGBHex.regex.matchEntire(colorHex) ?: return null

        val hexValue = matchResult.groups[1]!!.value.lowercase()
        val normalizedArgb = when (hexValue.length) {
            3 -> expandToTwoDigitsPerComponent("f$hexValue")
            6 -> hexValue
            else -> return null
        }

        return normalizedArgb
    }

    /**
     * Parses rgb functional notation as described in CSS2 spec
     */
    private fun rgbFunction(rgbValue: String): Paint {

        val result =
            PropertyRegex.RGBFunctional.regex.matchEntire(rgbValue) ?: return Paint.None

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
            .filter(String::isNotBlank)
            .map { it.toDouble().coerceIn(0.0..divisor) / divisor }
        return Paint.RGB(ColorRGBa(r, g, b))
    }

    private fun expandToTwoDigitsPerComponent(hexValue: String) =
        hexValue.asSequence()
            .map { "$it$it" }
            .reduce(String::plus)
}