package org.openrndr.svg

import org.jsoup.nodes.*
import org.openrndr.math.*
import org.openrndr.shape.*

internal abstract class SVGElement(element: Element?) {
    val tag: String = element?.tagName() ?: ""
    val id: String = element?.id() ?: ""
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