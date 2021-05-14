package org.openrndr.svg

import mu.*
import org.jsoup.*
import org.jsoup.parser.*
import org.openrndr.shape.*
import java.io.*
import java.net.*

private val logger = KotlinLogging.logger {}

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

// internal class SVGImage(val url: String, val x: Double?, val y: Double?, val width: Double?, val height: Double?) : SVGElement()

internal class SVGDocument(private val root: SVGSVGElement, val namespaces: Map<String, String>) {
    fun composition(): Composition = Composition(
        convertElement(root),
        root.bounds

    ).apply {
        namespaces.putAll(this@SVGDocument.namespaces)
        this.documentStyle = this@SVGDocument.root.documentStyle
    }

    private fun convertElement(svgElem: SVGElement): CompositionNode = when (svgElem) {
        is SVGGroup -> GroupNode().apply {
            this.id = svgElem.id
            svgElem.elements.mapTo(children) { convertElement(it).also { x -> x.parent = this@apply } }
        }
        is SVGPath -> {
            ShapeNode(svgElem.shape()).apply {
                style = svgElem.style
                this.id = svgElem.id
            }
        }
    }.apply {
        style::transform `=` svgElem.style.transform.value
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
            logger.warn {
                "SVG version \"$version\" is not supported!"
            }
        }

        // Deprecated in SVG 2.0
        val baseProfile = root.attr(Attr.BASE_PROFILE)
        val unsupportedProfiles = setOf("full", "basic")

        if (baseProfile in unsupportedProfiles) {
            logger.warn {
                "SVG baseProfile \"$baseProfile\" is not supported!"
            }
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