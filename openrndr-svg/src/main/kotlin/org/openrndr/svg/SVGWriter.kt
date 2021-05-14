package org.openrndr.svg

import org.jsoup.nodes.*
import org.openrndr.math.*
import org.openrndr.shape.*
import org.openrndr.shape.TextNode
import java.io.*

fun Composition.saveToFile(file: File) {
    if (file.extension == "svg") {
        val svg = writeSVG(this)
        file.writeText(svg)
    } else {
        throw IllegalArgumentException("can only write svg files, the extension '${file.extension}' is not supported")
    }
}

fun Composition.toSVG() = writeSVG(this)

private val CompositionNode.svgId: String
    get() = if (id != null) {
        "id=\"${id ?: error("id = null")}\""
    } else {
        ""
    }

private val CompositionNode.svgAttributes: String
    get() {
        return attributes.map {
            if (it.value != null) {
                "${it.key}=\"${Entities.escape(it.value)}\""
            } else {
                it.key
            }
        }.joinToString(" ")
    }

private fun Styleable.serialize(): String {
    val sb = StringBuilder()

    this.properties.filter {
        it.key != AttributeOrProperty.SHADESTYLE
    }.forEach { (t, u) ->
        if (u.toString().isNotEmpty()) {
            sb.append("$t=\"${u.toString()}\" ")
        }
    }

    return sb.trim().toString()
}

fun writeSVG(
    composition: Composition,
    topLevelId: String = "openrndr-svg"
): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

    val defaultNamespaces = mapOf(
        "xmlns" to "http://www.w3.org/2000/svg",
        "xmlns:xlink" to "http://www.w3.org/1999/xlink"
    )

    val namespaces = (defaultNamespaces + composition.namespaces).map { (k, v) ->
        "$k=\"$v\""
    }.joinToString(" ")

    val styleSer = composition.style.serialize()
    val docStyleSer = composition.documentStyle.serialize()

    sb.append("<svg version=\"1.2\" baseProfile=\"tiny\" id=\"$topLevelId\" $namespaces $styleSer $docStyleSer>")

    var textPathID = 0
    process(composition.root) { stage ->
        if (stage == VisitStage.PRE) {

            val styleSerialized = this.style.serialize()

            when (this) {
                is GroupNode -> {
                    val attributes = listOf(svgId, styleSerialized, svgAttributes)
                        .filter { a -> a.isNotBlank() }
                        .joinToString(" ")
                    sb.append("<g${" $attributes"}>\n")
                }
                is ShapeNode -> {
                    val pathAttribute = "d=\"${shape.svg}\""

                    val attributes = listOf(
                        svgId,
                        styleSerialized,
                        svgAttributes,
                        pathAttribute
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" ")

                    sb.append("<path $attributes/>\n")
                }

                is TextNode -> {
                    val contour = this.contour
                    val escapedText = Entities.escape(this.text)
                    if (contour == null) {
                        sb.append("<text $svgId $svgAttributes>$escapedText</text>")
                    } else {
                        sb.append("<defs>")
                        sb.append("<path id=\"text$textPathID\" d=\"${contour.svg}\"/>")
                        sb.append("</defs>")
                        sb.append("<text $styleSerialized><textPath href=\"#text$textPathID\">$escapedText</textPath></text>")
                        textPathID++
                    }
                }
                is ImageNode -> {
                    val dataUrl = this.image.toDataUrl()
                    sb.append("""<image xlink:href="$dataUrl" height="${this.image.height}" width="${this.image.width}"/>""")
                }
            }
        } else {
            when (this) {
                is GroupNode -> {
                    sb.append("</g>\n")
                }
            }
        }
    }
    sb.append("</svg>")
    return sb.toString()
}

private val Matrix44.svgTransform get() = if (this == Matrix44.IDENTITY) null else "matrix(${this.c0r0}, ${this.c0r1}, ${this.c1r0}, ${this.c1r1}, ${this.c3r0}, ${this.c3r1})"

private val Shape.svg: String
    get() {
        val sb = StringBuilder()
        contours.forEach {
            it.segments.forEachIndexed { index, segment ->
                if (index == 0) {
                    sb.append("M ${segment.start.x} ${segment.start.y}")
                }
                sb.append(
                    when (segment.control.size) {
                        1 -> "Q${segment.control[0].x} ${segment.control[0].y} ${segment.end.x} ${segment.end.y}"
                        2 -> "C${segment.control[0].x} ${segment.control[0].y} ${segment.control[1].x} ${segment.control[1].y} ${segment.end.x} ${segment.end.y}"
                        else -> "L${segment.end.x} ${segment.end.y}"
                    }
                )
            }
            if (it.closed) {
                sb.append("z")
            }
        }
        return sb.toString()
    }

private val ShapeContour.svg: String
    get() {
        val sb = StringBuilder()
        segments.forEachIndexed { index, segment ->
            if (index == 0) {
                sb.append("M ${segment.start.x} ${segment.start.y}")
            }
            sb.append(
                when (segment.control.size) {
                    1 -> "C${segment.control[0].x}, ${segment.control[0].y} ${segment.end.x} ${segment.end.y}"
                    2 -> "C${segment.control[0].x}, ${segment.control[0].y} ${segment.control[1].x} ${segment.control[1].y} ${segment.end.x} ${segment.end.y}"
                    else -> "L${segment.end.x} ${segment.end.y}"
                }
            )
        }
        if (closed) {
            sb.append("z")
        }
        return sb.toString()
    }

private enum class VisitStage {
    PRE,
    POST
}

private fun process(compositionNode: CompositionNode, visitor: CompositionNode.(stage: VisitStage) -> Unit) {
    compositionNode.visitor(VisitStage.PRE)
    if (compositionNode is GroupNode) {
        compositionNode.children.forEach { process(it, visitor) }
    }
    compositionNode.visitor(VisitStage.POST)
}