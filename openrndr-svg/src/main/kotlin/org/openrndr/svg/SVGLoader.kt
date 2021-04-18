package org.openrndr.svg

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.LineCap
import org.openrndr.draw.LineJoin
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.YPolarity
import org.openrndr.math.transforms.scale
import org.openrndr.math.transforms.translate
import org.openrndr.shape.*
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.util.regex.Pattern
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Load a [Composition] from a filename, url or svg string
 * @param fileOrUrlOrSvg a filename, a url or an svg document
 */
fun loadSVG(fileOrUrlOrSvg: String): Composition {
    return if (fileOrUrlOrSvg.endsWith(".svg")) {
        try {
            val url = URL(fileOrUrlOrSvg)
            parseSVG(url.readText())
        } catch (e: MalformedURLException) {
            parseSVG(File(fileOrUrlOrSvg).readText())
        }
    } else {
        parseSVG(fileOrUrlOrSvg)
    }
}

/**
 * Load a [Composition] from a file, url or svg string
 * @param file a filename, a url or an svg document
 */
fun loadSVG(file: File): Composition {
    return parseSVG(file.readText())
}

/**
 * Parses an svg document and creates a [Composition]
 * @param svgString xml-like svg document
 */
fun parseSVG(svgString: String): Composition {
    val document = SVGLoader().loadSVG(svgString)
    return document.composition()
}

internal class Command(val op: String, vararg val operands: Double) {
    fun vector(i0: Int, i1: Int): Vector2 {
        val x = if (i0 == -1) 0.0 else operands[i0]
        val y = if (i1 == -1) 0.0 else operands[i1]
        return Vector2(x, y)
    }

    fun vectors(): List<Vector2> = (0 until operands.size / 2).map { Vector2(operands[it * 2], operands[it * 2 + 1]) }
}

internal sealed class SVGElement {
    var transform = Matrix44.IDENTITY
    var id: String? = null
    var attributes = mutableMapOf<String, String?>()

    fun parseAttributes(e: Element) {
        for (attribute in e.attributes()) {
            if (attribute.key.contains(":") || attribute.key.startsWith("data-")) {
                if (attribute.hasDeclaredValue()) {
                    attributes[attribute.key] = attribute.value
                } else {
                    attributes[attribute.key] = null
                }
            }
        }
    }

    fun parseTransform(e: Element) {
        val p = Pattern.compile("(matrix|translate|scale|rotate|skewX|skewY)\\([\\d\\.,\\-\\s]+\\)")
        val m = p.matcher(e.attr("transform"))

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
    }
}

internal class SVGImage(val url: String, val x: Double?, val y: Double?, val width: Double?, val height: Double?) :
    SVGElement()

internal class SVGGroup(val elements: MutableList<SVGElement> = mutableListOf()) : SVGElement()

internal fun parseColor(scolor: String): ColorRGBa? {
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
        scolor.startsWith("rgb(") -> parseRgbFunction(scolor)
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
fun parseRgbFunction(rgbValue: String): ColorRGBa {
    // Matches either rgb(0, 0, 0) or rgb(0%, 0%, 0%) and captures the component values (without '%').
    // Shorten it if you dare (probably not possible unless you can discard all whitespace easily).
    val rgbRegex =
        "rgb\\(\\s*(?>(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)|\\s*(\\d+)%\\s*,\\s*(\\d+)%\\s*,\\s*(\\d+)%)\\s*\\)".toRegex()

    val result = rgbRegex.matchEntire(rgbValue) ?: error("The provided rgb functional notation '$rgbValue' is invalid.")

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

internal fun Double.toBoolean() = this.toInt() == 1

internal fun parseArcCommand(p: String): List<List<String>> {
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

internal class SVGPath : SVGElement() {
    val commands = mutableListOf<Command>()
    var fill: CompositionColor = InheritColor
    var stroke: CompositionColor = InheritColor
    var strokeWeight: CompositionStrokeWeight = InheritStrokeWeight
    var lineCap: CompositionLineCap = InheritLineCap
    var lineJoin: CompositionLineJoin = InheritLineJoin
    var miterlimit: CompositionMiterlimit = InheritMiterlimit
    var strokeOpacity: CompositionStrokeOpacity = InheritStrokeOpacity
    var fillOpacity: CompositionFillOpacity = InheritFillOpacity
    var opacity: CompositionOpacity = InheritOpacity

    companion object {
        fun fromSVGPathString(svgPath: String): SVGPath {
            val path = SVGPath()
            val rawCommands = svgPath.split("(?=[MmZzLlHhVvCcSsQqTtAa])".toRegex()).dropLastWhile { it.isEmpty() }
            val numbers = Pattern.compile("[-+]?[0-9]*[.]?[0-9]+(?:[eE][-+]?[0-9]+)?")
            val arcOpReg = Pattern.compile("[aA]")

            for (rawCommand in rawCommands) {
                if (rawCommand.isNotEmpty()) {
                    // Special case for arcTo command where the "numbers" RegExp breaks
                    if (arcOpReg.matcher(rawCommand[0].toString()).find()) {
                        parseArcCommand(rawCommand.substring(1)).forEach {
                            val operands = it.map { operand -> operand.toDouble() }

                            path.commands.add(Command(rawCommand[0].toString(), *(operands.toDoubleArray())))
                        }
                    } else {
                        val numberMatcher = numbers.matcher(rawCommand)
                        val operands = mutableListOf<Double>()
                        while (numberMatcher.find()) {
                            operands.add(numberMatcher.group().toDouble())
                        }
                        path.commands.add(Command(rawCommand[0].toString(), *(operands.toDoubleArray())))
                    }
                }
            }
            return path
        }
    }

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

    fun parseDrawAttributes(e: Element) {
        e.attributes().forEach {
            when (it.key) {
                At.FILL -> fill = Color(parseColor(e.attr(At.FILL)))
                At.STROKE -> stroke = Color(parseColor(e.attr(At.STROKE)))
                At.STROKE_WIDTH -> strokeWeight = StrokeWeight(e.attr(At.STROKE_WIDTH).toDouble())
                At.STROKE_LINECAP -> lineCap = LineCap(LineCap.valueOf(e.attr(At.STROKE_LINECAP)))
                At.STROKE_LINEJOIN -> lineJoin = LineJoin(LineJoin.valueOf(e.attr(At.STROKE_LINEJOIN)))
                At.STROKE_MITERLIMIT -> miterlimit = Miterlimit(e.attr(At.STROKE_MITERLIMIT).toDouble())
                At.STROKE_OPACITY -> strokeOpacity = StrokeOpacity(e.attr(At.STROKE_OPACITY).toDouble())
                At.FILL_OPACITY -> fillOpacity = FillOpacity(e.attr(At.FILL_OPACITY).toDouble())
                At.OPACITY -> opacity = Opacity(e.attr(At.OPACITY).toDouble())
            }
        }

        // TODO: Handle above
        e.attr(At.STYLE).split(";").forEach {
            val tokens = it.split(":")
            val attribute = tokens[0].toLowerCase().trim()

            fun value(): String = if (tokens.size >= 2) {
                tokens[1].trim()
            } else {
                ""
            }

            // TODO: Can this repetition be reduced?
            when (attribute) {
                At.FILL -> fill = Color(parseColor(value()))
                At.STROKE -> stroke = Color(parseColor(value()))
                At.STROKE_WIDTH -> strokeWeight = StrokeWeight(value().toDouble())
                At.STROKE_LINECAP -> lineCap = LineCap(LineCap.valueOf(e.attr(At.STROKE_LINECAP)))
                At.STROKE_LINEJOIN -> lineJoin = LineJoin(LineJoin.valueOf(e.attr(At.STROKE_LINEJOIN)))
                At.STROKE_MITERLIMIT -> miterlimit = Miterlimit(e.attr(At.STROKE_MITERLIMIT).toDouble())
                At.STROKE_OPACITY -> strokeOpacity = StrokeOpacity(e.attr(At.STROKE_OPACITY).toDouble())
                At.FILL_OPACITY -> fillOpacity = FillOpacity(e.attr(At.FILL_OPACITY).toDouble())
                At.OPACITY -> opacity = Opacity(e.attr(At.OPACITY).toDouble())
            }
        }
    }
}

internal class SVGDocument(private val root: SVGElement, val namespaces: Map<String, String>) {
    fun composition(): Composition = Composition(convertElement(root)).apply {
        namespaces.putAll(this@SVGDocument.namespaces)
    }

    private fun convertElement(e: SVGElement): CompositionNode = when (e) {
        is SVGGroup -> GroupNode().apply {
            this.id = e.id
            e.elements.mapTo(children) { convertElement(it).also { x -> x.parent = this@apply } }
        }
        is SVGPath -> {
            ShapeNode(e.shape()).apply {
                fill = e.fill
                stroke = e.stroke
                strokeWeight = e.strokeWeight
                lineJoin = e.lineJoin
                miterlimit = e.miterlimit
                strokeOpacity = e.strokeOpacity
                fillOpacity = e.fillOpacity
                opacity = e.opacity
                this.id = e.id
            }
        }
        is SVGImage -> {
            val image = ColorBuffer.fromUrl(e.url)
            ImageNode(image, e.x ?: 0.0, e.y ?: 0.0, e.width ?: 0.0, e.height ?: 0.0).apply {
                this.id = e.id
            }
        }

    }.apply {
        transform = e.transform
        attributes.putAll(e.attributes)
    }
}

internal class SVGLoader {
    fun loadSVG(svg: String): SVGDocument {
        val doc = Jsoup.parse(svg, "", Parser.xmlParser())
        val root = doc.select(El.SVG).first()
        val namespaces = root.attributes().filter { it.key.startsWith("xmlns") }.associate {
            Pair(it.key, it.value)
        }

        // Deprecated in SVG 2.0 and not a popular attribute anyway
        // but if it is present and is not 1.2, we can notify the user
        // that it's UB from here on out
        val version = root.attr(At.VERSION)
        val unsupportedVersions = setOf("1.0", "1.1")

        if (version in unsupportedVersions) {
            println("SVG version \"$version\" is not supported!")
        }

        // Deprecated in SVG 2.0
        val baseProfile = root.attr(At.BASE_PROFILE)
        val unsupportProfiles = setOf("full", "basic")

        if (baseProfile in unsupportProfiles) {
            println("SVG baseProfile \"$baseProfile\" is not supported!")
        }

        val rootGroup = SVGGroup()
        handleGroup(rootGroup, root)
        return SVGDocument(rootGroup, namespaces)
    }

    private fun handlePolygon(group: SVGGroup, e: Element) {
        val tokens = e.attr(At.POINTS).split("[ ,\n]+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        val points = (0 until tokens.size / 2).map { Vector2(tokens[it * 2].toDouble(), tokens[it * 2 + 1].toDouble()) }
        val path = SVGPath().apply {
            id = e.id()
            parseDrawAttributes(e)
            parseTransform(e)
            parseAttributes(e)
            commands.add(Command("M", points[0].x, points[0].y))
            (1 until points.size).mapTo(commands) { Command("L", points[it].x, points[it].y) }
            commands.add(Command("Z"))
        }
        group.elements.add(path)
    }

    private fun handlePolyline(group: SVGGroup, e: Element) {
        val tokens = e.attr(At.POINTS).split("[ ,\n]+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        val points = (0 until tokens.size / 2).map { Vector2(tokens[it * 2].toDouble(), tokens[it * 2 + 1].toDouble()) }
        val path = SVGPath().apply {
            id = e.id()
            parseDrawAttributes(e)
            parseTransform(e)
            parseAttributes(e)
            commands.add(Command("M", points[0].x, points[0].y))
            (1 until points.size).mapTo(commands) { Command("L", points[it].x, points[it].y) }
        }
        group.elements.add(path)
    }

    private fun handleGroup(parent: SVGGroup, e: Element) {
        val group = SVGGroup().apply {
            id = e.id()
            parseTransform(e)
            parseAttributes(e)
        }
        parent.elements.add(group)
        e.children().forEach { c ->
            when (c.tagName()) {
                El.G -> handleGroup(group, c)
                El.PATH -> handlePath(group, c)
                El.LINE -> handleLine(group, c)
                El.RECT -> handleRectangle(group, c)
                El.ELLIPSE -> handleEllipse(group, c)
                El.CIRCLE -> handleCircle(group, c)
                El.POLYGON -> handlePolygon(group, c)
                El.POLYLINE -> handlePolyline(group, c)
                El.IMAGE -> handleImage(group, c)
            }
        }
    }

    private fun handleImage(group: SVGGroup, e: Element) {
        val width = e.attr(At.WIDTH).toDoubleOrNull()
        val height = e.attr(At.HEIGHT).toDoubleOrNull()
        val x = e.attr("x").toDoubleOrNull()
        val y = e.attr("y").toDoubleOrNull()
        val imageData = e.attr("xlink:href")
//        val image = ColorBuffer.fromUrl(imageData)
//        val imageNode = ImageNode(image, width ?: image.width.toDouble(), height ?: image.height.toDouble())
        val image = SVGImage(imageData, x, y, width, height)
        image.parseTransform(e)
        group.elements.add(image)
    }

    private fun handleEllipse(group: SVGGroup, e: Element) {
        var x = e.attr(At.CX).let { if (it.isEmpty()) 0.0 else it.toDouble() }
        var y = e.attr(At.CY).let { if (it.isEmpty()) 0.0 else it.toDouble() }
        val width = e.attr(At.RX).let { if (it.isEmpty()) 0.0 else it.toDouble() } * 2.0
        val height = e.attr(At.RY).let { if (it.isEmpty()) 0.0 else it.toDouble() } * 2.0
        x -= width / 2
        y -= height / 2

        val kappa = 0.5522848
        // control point offset horizontal
        val ox = width / 2 * kappa
        // control point offset vertical
        val oy = height / 2 * kappa
        // x-end
        val xe = x + width
        // y-end
        val ye = y + height
        // x-middle
        val xm = x + width / 2
        // y-middle
        val ym = y + height / 2

        val path = SVGPath()
        path.id = e.id()
        path.parseDrawAttributes(e)
        path.parseTransform(e)
        path.parseAttributes(e)
        path.commands.add(Command("M", x, ym))
        path.commands.add(Command("C", x, ym - oy, xm - ox, y, xm, y))
        path.commands.add(Command("C", xm + ox, y, xe, ym - oy, xe, ym))
        path.commands.add(Command("C", xe, ym + oy, xm + ox, ye, xm, ye))
        path.commands.add(Command("C", xm - ox, ye, x, ym + oy, x, ym))
        path.commands.add(Command("z"))
        group.elements.add(path)
    }

    private fun handleCircle(group: SVGGroup, e: Element) {
        var x = e.attr(At.CX).let { if (it.isEmpty()) 0.0 else it.toDouble() }
        var y = e.attr(At.CY).let { if (it.isEmpty()) 0.0 else it.toDouble() }
        val width = e.attr(At.R).let { if (it.isEmpty()) 0.0 else it.toDouble() } * 2.0
        val height = e.attr(At.R).let { if (it.isEmpty()) 0.0 else it.toDouble() } * 2.0
        x -= width / 2
        y -= height / 2

        val kappa = 0.5522848
        // control point offset horizontal
        val ox = width / 2 * kappa
        // control point offset vertical
        val oy = height / 2 * kappa
        // x-end
        val xe = x + width
        // y-end
        val ye = y + height
        // x-middle
        val xm = x + width / 2
        // y-middle
        val ym = y + height / 2

        val path = SVGPath()
        path.id = e.id()
        path.parseDrawAttributes(e)
        path.parseTransform(e)
        path.parseAttributes(e)
        path.commands.add(Command("M", x, ym))
        path.commands.add(Command("C", x, ym - oy, xm - ox, y, xm, y))
        path.commands.add(Command("C", xm + ox, y, xe, ym - oy, xe, ym))
        path.commands.add(Command("C", xe, ym + oy, xm + ox, ye, xm, ye))
        path.commands.add(Command("C", xm - ox, ye, x, ym + oy, x, ym))
        path.commands.add(Command("z"))
        group.elements.add(path)
    }

//    private fun handleImage(group: SVGGroup, e: Element) {
//        val width = e.attr("width").toDouble()
//        val height = e.attr("height").toDouble()
//        val url = e.attr("xlink:href")
//        val image = SVGImage(url).apply {
//            id = e.id()
//            parseTransform(e)
//        }
//        image.id = e.id()
//    }

    private fun handleRectangle(group: SVGGroup, e: Element) {
        val x = e.attr(At.X).let { if (it.isEmpty()) 0.0 else it.toDouble() }
        val y = e.attr(At.Y).let { if (it.isEmpty()) 0.0 else it.toDouble() }
        val width = e.attr(At.WIDTH).toDouble()
        val height = e.attr(At.HEIGHT).toDouble()

        val path = SVGPath().apply {
            id = e.id()
            parseTransform(e)
            parseDrawAttributes(e)
            parseAttributes(e)
            commands.add(Command("M", x, y))
            commands.add(Command("h", width))
            commands.add(Command("v", height))
            commands.add(Command("h", -width))
            commands.add(Command("z"))
        }
        group.elements.add(path)
    }

    private fun handleLine(group: SVGGroup, e: Element) {
        val x1 = e.attr(At.X1).toDouble()
        val x2 = e.attr(At.X2).toDouble()
        val y1 = e.attr(At.Y1).toDouble()
        val y2 = e.attr(At.Y2).toDouble()

        val path = SVGPath().apply {
            parseDrawAttributes(e)
            parseAttributes(e)
            commands.add(Command("M", x1, y1))
            commands.add(Command("L", x2, y2))
        }
        group.elements.add(path)
    }

    private fun handlePath(group: SVGGroup, e: Element) {
        val path = SVGPath.fromSVGPathString(e.attr(At.D)).apply {
            id = e.id()
            parseDrawAttributes(e)
            parseTransform(e)
            parseAttributes(e)
        }
        group.elements.add(path)
    }
}