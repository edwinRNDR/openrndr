package org.openrndr.svg

import org.jsoup.Jsoup
import org.jsoup.nodes.*
import org.jsoup.parser.Parser
import org.openrndr.color.ColorRGBa
import org.openrndr.math.*
import org.openrndr.math.transforms.*
import org.openrndr.shape.*
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.util.regex.Pattern

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

// internal class SVGImage(val url: String, val x: Double?, val y: Double?, val width: Double?, val height: Double?) : SVGElement()

internal fun Double.toBoolean() = this.toInt() == 1

internal class SVGDocument(private val root: SVGElement, val namespaces: Map<String, String>) {
    fun composition(): Composition = Composition(convertElement(root)).apply {
        namespaces.putAll(this@SVGDocument.namespaces)
    }

    private fun convertElement(svgElem: SVGElement): CompositionNode = when (svgElem) {
        is SVGSVGElement -> RootNode().apply {
            this.id = svgElem.id
            this.currentTransform = svgElem.currentTransform
            svgElem.elements.mapTo(children) { convertElement(it) }
        }
        is SVGGroup -> GroupNode().apply {
            this.id = svgElem.id
            svgElem.elements.mapTo(children) { convertElement(it).also { x -> x.parent = this@apply } }
        }
        is SVGPath -> {
            ShapeNode(svgElem.shape()).apply {
                fill = svgElem.fill
                stroke = svgElem.stroke
                strokeWeight = svgElem.strokeWeight
                lineJoin = svgElem.lineJoin
                miterlimit = svgElem.miterlimit
                strokeOpacity = svgElem.strokeOpacity
                fillOpacity = svgElem.fillOpacity
                opacity = svgElem.opacity
                this.id = svgElem.id
            }
        }
    }.apply {
        transform = svgElem.transform
        // attributes.putAll(svgElem.attributes)
    }
}

internal class SVGLoader {
    fun loadSVG(svg: String): SVGDocument {
        val doc = Jsoup.parse(svg, "", Parser.xmlParser())
        val root = doc.select(Tag.SVG).first()
        val namespaces = root.attributes().filter { it.key.startsWith("xmlns") }.associate {
            Pair(it.key, it.value)
        }

        // Deprecated in SVG 2.0 and not a popular attribute anyway
        // but if it is present and is not 1.2, we can notify the user
        // that it's UB from here on out
        val version = root.attr(Attr.VERSION)
        val unsupportedVersions = setOf("1.0", "1.1")

        if (version in unsupportedVersions) {
            println("SVG version \"$version\" is not supported!")
        }

        // Deprecated in SVG 2.0
        val baseProfile = root.attr(Attr.BASE_PROFILE)
        val unsupportProfiles = setOf("full", "basic")

        if (baseProfile in unsupportProfiles) {
            println("SVG baseProfile \"$baseProfile\" is not supported!")
        }

        val rootGroup = SVGSVGElement(root)
        return SVGDocument(rootGroup, namespaces)
    }

//    private fun handleImage(group: SVGGroup, e: Element) {
//        val width = e.attr(Attr.WIDTH).toDoubleOrNull()
//        val height = e.attr(Attr.HEIGHT).toDoubleOrNull()
//        val x = e.attr("x").toDoubleOrNull()
//        val y = e.attr("y").toDoubleOrNull()
//        val imageData = e.attr("xlink:href")
//        val image = ColorBuffer.fromUrl(imageData)
//        val imageNode = ImageNode(image, width ?: image.width.toDouble(), height ?: image.height.toDouble())
//        val image = SVGImage(imageData, x, y, width, height)
//        image.parseTransform(e)
//        group.elements.add(image)
//    }

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
}