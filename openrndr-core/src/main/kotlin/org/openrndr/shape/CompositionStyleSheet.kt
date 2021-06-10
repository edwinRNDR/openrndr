@file:Suppress("RemoveExplicitTypeArguments")

package org.openrndr.shape

import org.openrndr.color.*
import org.openrndr.draw.*
import org.openrndr.math.*
import org.openrndr.shape.AttributeOrPropertyKey.*
import org.openrndr.shape.Inheritance.*
import kotlin.reflect.*

enum class Inheritance {
    INHERIT,
    RESET
}

sealed interface AttributeOrPropertyValue {
    val value: Any?
    override fun toString(): String
}

sealed interface Paint : AttributeOrPropertyValue {
    override val value: ColorRGBa?

    class RGB(override val value: ColorRGBa) : Paint {
        override fun toString(): String {
            val ir = (value.r.coerceIn(0.0, 1.0) * 255.0).toInt()
            val ig = (value.g.coerceIn(0.0, 1.0) * 255.0).toInt()
            val ib = (value.b.coerceIn(0.0, 1.0) * 255.0).toInt()
            return String.format("#%02x%02x%02x", ir, ig, ib)
        }
    }

    // This one is kept just in case, it's not handled in any way yet
    object CurrentColor : Paint {
        override val value: ColorRGBa
            get() = TODO("Not yet implemented")
        override fun toString(): String = "currentcolor"
    }

    object None : Paint {
        override val value: ColorRGBa? = null
        override fun toString(): String = "none"
    }
}

sealed interface Shade : AttributeOrPropertyValue {
    class Value(override val value: ShadeStyle) : Shade {
        override fun toString(): String = ""
    }
}

sealed interface Length : AttributeOrPropertyValue {
    override val value: Double

    class Pixels(override val value: Double) : Length {
        companion object {
            fun fromInches(value: Double) = Pixels(value * 96.0)
            fun fromPicas(value: Double) = Pixels(value * 16.0)
            fun fromPoints(value: Double) = Pixels(value * (4.0 / 3.0))
            fun fromCentimeters(value: Double) = Pixels(value * (96.0 / 2.54))
            fun fromMillimeters(value: Double) = Pixels(value * (96.0 / 25.4))
            fun fromQuarterMillimeters(value: Double) = Pixels(value * (96.0 / 101.6))
        }

        override fun toString(): String = "$value"
    }

    class Percent(override val value: Double) : Length {
        override fun toString(): String {
            return "${value}%"
        }
    }

    enum class UnitIdentifier {
        IN,
        PC,
        PT,
        PX,
        CM,
        MM,
        Q
    }
}

inline val Double.pixels: Length.Pixels
    get() = Length.Pixels(this)
inline val Double.percent: Length.Percent
    get() = Length.Percent(this)

sealed interface Numeric : AttributeOrPropertyValue {
    override val value: Double

    class Rational(override val value: Double) : Numeric {
        override fun toString(): String = "$value"
    }
}

sealed interface Transform : AttributeOrPropertyValue {
    override val value: Matrix44

    class Matrix(override val value: Matrix44) : Transform {
        override fun toString(): String {
            return if (value == Matrix44.IDENTITY) {
                ""
            } else {
                "matrix(${value.c0r0} ${value.c0r1} " +
                    "${value.c1r0} ${value.c1r1} " +
                    "${value.c3r0} ${value.c3r1})"
            }
        }
    }

    object None : Transform {
        override val value = Matrix44.IDENTITY
        override fun toString(): String = ""
    }
}

sealed interface Visibility : AttributeOrPropertyValue {
    override val value: Boolean

    object Visible : Visibility {
        override val value = true
        override fun toString() = "visible"
    }

    object Hidden : Visibility {
        override val value = false
        override fun toString() = "hidden"
    }

    // This exists because the spec specifies so,
    // it is effectively Hidden.
    object Collapse : Visibility {
        override val value = false
        override fun toString() = "collapse"
    }
}

sealed interface Display : AttributeOrPropertyValue {
    override val value: Boolean

    object Inline : Display {
        override val value = true
        override fun toString() = "inline"
    }

    object Block : Display {
        override val value = true
        override fun toString() = "block"
    }

    object None : Display {
        override val value = false
        override fun toString() = "none"
    }
}

sealed interface LineCap : AttributeOrPropertyValue {
    override val value: org.openrndr.draw.LineCap

    object Round : LineCap {
        override val value = org.openrndr.draw.LineCap.ROUND
        override fun toString() = "round"
    }

    object Butt : LineCap {
        override val value = org.openrndr.draw.LineCap.BUTT
        override fun toString() = "butt"
    }

    object Square : LineCap {
        override val value = org.openrndr.draw.LineCap.SQUARE
        override fun toString() = "square"
    }
}

sealed interface LineJoin : AttributeOrPropertyValue {
    override val value: org.openrndr.draw.LineJoin

    object Miter : LineJoin {
        override val value = org.openrndr.draw.LineJoin.MITER
        override fun toString() = "miter"
    }

    object Bevel : LineJoin {
        override val value = org.openrndr.draw.LineJoin.BEVEL
        override fun toString() = "bevel"
    }

    object Round : LineJoin {
        override val value = org.openrndr.draw.LineJoin.ROUND
        override fun toString() = "round"
    }
}

enum class Align {
    NONE,
    X_MIN_Y_MIN,
    X_MID_Y_MIN,
    X_MAX_Y_MIN,
    X_MIN_Y_MID,
    X_MID_Y_MID,
    X_MAX_Y_MID,
    X_MIN_Y_MAX,
    X_MID_Y_MAX,
    X_MAX_Y_MAX
}

enum class MeetOrSlice {
    MEET,
    SLICE
}

data class AspectRatio(val align: Align, val meetOrSlice: MeetOrSlice) : AttributeOrPropertyValue {
    override val value = this

    companion object {
        val DEFAULT = AspectRatio(Align.X_MID_Y_MID, MeetOrSlice.MEET)
    }

    override fun toString(): String {
        if (this == DEFAULT) {
            return ""
        }

        val a = when (align) {
            Align.NONE -> "none"
            Align.X_MIN_Y_MIN -> "xMinYMin"
            Align.X_MID_Y_MIN -> "xMidYMin"
            Align.X_MAX_Y_MIN -> "xMaxYMin"
            Align.X_MIN_Y_MID -> "xMinYMid"
            Align.X_MID_Y_MID -> "xMidYMid"
            Align.X_MAX_Y_MID -> "xMaxYMid"
            Align.X_MIN_Y_MAX -> "xMinYMax"
            Align.X_MID_Y_MAX -> "xMidYMax"
            Align.X_MAX_Y_MAX -> "xMaxYMax"
        }
        val m = when (meetOrSlice) {
            MeetOrSlice.MEET -> "meet"
            MeetOrSlice.SLICE -> "slice"
        }

        return "$a $m"
    }
}

sealed interface ViewBox : AttributeOrPropertyValue {
    override val value: Rectangle?

    class Value(override val value: Rectangle) : ViewBox {
        override fun toString(): String =
            "${value.x.toInt()} ${value.y.toInt()} ${value.width.toInt()} ${value.height.toInt()}"
    }

    /**
     * The viewBox has not been defined,
     * **not** that it doesn't exist.
     */
    object None : ViewBox {
        override val value: Rectangle? = null
        override fun toString(): String = ""
    }
}

private data class PropertyBehavior(val inherit: Inheritance, val initial: AttributeOrPropertyValue)

private object PropertyBehaviors {
    val behaviors = HashMap<AttributeOrPropertyKey, PropertyBehavior>()
}

private class PropertyDelegate<T : AttributeOrPropertyValue>(
    val name: AttributeOrPropertyKey,
    inheritance: Inheritance,
    val initial: T
) {
    init {
        PropertyBehaviors.behaviors[name] = PropertyBehavior(inheritance, initial)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(style: Styleable, property: KProperty<*>): T {
        return (style[name] ?: PropertyBehaviors.behaviors[name]!!.initial) as T
    }

    operator fun setValue(style: Styleable, property: KProperty<*>, value: T?) {
        style[name] = value
    }
}

sealed class Styleable {
    val properties = HashMap<AttributeOrPropertyKey, AttributeOrPropertyValue?>()

    operator fun get(name: AttributeOrPropertyKey) = properties[name]

    operator fun set(name: AttributeOrPropertyKey, value: AttributeOrPropertyValue?) {
        properties[name] = value
    }

    infix fun inherit(from: Style): Style {
        return Style().also {
            from.properties.forEach { (name, value) ->
                if (PropertyBehaviors.behaviors[name]?.inherit == INHERIT) {
                    it.properties[name] = value
                }
            }
            it.properties.putAll(properties)
        }
    }

    // Because AttributeOrPropertyValue has a toString override,
    // we can abuse it for equality checks.
    fun isInherited(from: Styleable, attributeKey: AttributeOrPropertyKey): Boolean =
        when (this.properties[attributeKey].toString()) {
            from.properties[attributeKey].toString() -> true
            PropertyBehaviors.behaviors[attributeKey]?.initial.toString() -> true
            else -> false
        }
}

class DocumentStyle : Styleable()
class Style : Styleable()

var DocumentStyle.viewBox by PropertyDelegate<ViewBox>(VIEW_BOX, RESET, ViewBox.None)
var DocumentStyle.preserveAspectRatio by PropertyDelegate<AspectRatio>(
    PRESERVE_ASPECT_RATIO,
    RESET, AspectRatio.DEFAULT
)

var Style.stroke by PropertyDelegate<Paint>(STROKE, INHERIT, Paint.None)
var Style.strokeOpacity by PropertyDelegate<Numeric>(STROKE_OPACITY, INHERIT, Numeric.Rational(1.0))
var Style.strokeWeight by PropertyDelegate<Length>(STROKE_WIDTH, INHERIT, 1.0.pixels)
var Style.miterLimit by PropertyDelegate<Numeric>(STROKE_MITERLIMIT, INHERIT, Numeric.Rational(4.0))
var Style.lineCap by PropertyDelegate<LineCap>(STROKE_LINECAP, INHERIT, LineCap.Butt)
var Style.lineJoin by PropertyDelegate<LineJoin>(STROKE_LINEJOIN, INHERIT, LineJoin.Miter)

var Style.fill by PropertyDelegate<Paint>(FILL, INHERIT, Paint.RGB(ColorRGBa.BLACK))
var Style.fillOpacity by PropertyDelegate<Numeric>(FILL_OPACITY, INHERIT, Numeric.Rational(1.0))

var Style.transform by PropertyDelegate<Transform>(TRANSFORM, RESET, Transform.None)

// Okay so the spec says `display` isn't inheritable, but effectively acts so
// when the element and its children are excluded from the rendering tree.
var Style.display by PropertyDelegate<Display>(DISPLAY, RESET, Display.Inline)
var Style.opacity by PropertyDelegate<Numeric>(OPACITY, RESET, Numeric.Rational(1.0))
var Style.visibility by PropertyDelegate<Visibility>(VISIBILITY, INHERIT, Visibility.Visible)

var Style.x by PropertyDelegate<Length>(X, RESET, 0.0.pixels)
var Style.y by PropertyDelegate<Length>(Y, RESET, 0.0.pixels)
var Style.width by PropertyDelegate<Length>(WIDTH, RESET, 768.0.pixels)
var Style.height by PropertyDelegate<Length>(HEIGHT, RESET, 576.0.pixels)

var Style.shadeStyle by PropertyDelegate<Shade>(SHADESTYLE, INHERIT, Shade.Value(ShadeStyle()))

enum class AttributeOrPropertyKey {
    // @formatter:off
    // Attributes
    BASE_PROFILE { override fun toString() = "baseProfile" },
    CLASS { override fun toString() = "class" },
    CX { override fun toString() = "cx" },
    CY { override fun toString() = "cy" },
    D { override fun toString() = "d" },
    DX { override fun toString() = "dx" },
    DY { override fun toString() = "dy" },
    GRADIENT_UNITS { override fun toString() = "gradientUnits" },
    HEIGHT { override fun toString() = "height" },
    ID { override fun toString() = "id" },
    OFFSET { override fun toString() = "offset" },
    PATH_LENGTH { override fun toString() = "pathLength" },
    POINTS { override fun toString() = "points" },
    PRESERVE_ASPECT_RATIO { override fun toString() = "preserveAspectRatio" },
    R { override fun toString() = "r" },
    ROTATE { override fun toString() = "rotate" },
    RX { override fun toString() = "rx" },
    RY { override fun toString() = "ry" },
    SPACE { override fun toString() = "xml:space" },
    STYLE { override fun toString() = "style" },
    TRANSFORM { override fun toString() = "transform" },
    VERSION { override fun toString() = "version" },
    VIEW_BOX { override fun toString() = "viewBox" },
    WIDTH { override fun toString() = "width" },
    X { override fun toString() = "x" },
    X1 { override fun toString() = "x1" },
    X2 { override fun toString() = "x2" },
    Y { override fun toString() = "y" },
    Y1 { override fun toString() = "y1" },
    Y2 { override fun toString() = "y2" },

    // Properties
    COLOR { override fun toString() = "color" },
    DIRECTION { override fun toString() = "direction" },
    DISPLAY { override fun toString() = "display" },
    DISPLAY_ALIGN { override fun toString() = "display-align" },
    FILL { override fun toString() = "fill" },
    FILL_OPACITY { override fun toString() = "fill-opacity" },
    FILL_RULE { override fun toString() = "fill-rule" },
    FONT_FAMILY { override fun toString() = "font-family" },
    FONT_SIZE { override fun toString() = "font-size" },
    FONT_STYLE { override fun toString() = "font-style" },
    FONT_VARIANT { override fun toString() = "font-variant" },
    FONT_WEIGHT { override fun toString() = "font-weight" },
    OPACITY { override fun toString() = "opacity" },
    STOP_COLOR { override fun toString() = "stop-color" },
    STOP_OPACITY { override fun toString() = "stop-opacity" },
    STROKE { override fun toString() = "stroke" },
    STROKE_DASHARRAY { override fun toString() = "stroke-dasharray" },
    STROKE_DASHOFFSET { override fun toString() = "stroke-dashoffset" },
    STROKE_LINECAP { override fun toString() = "stroke-linecap" },
    STROKE_LINEJOIN { override fun toString() = "stroke-linejoin" },
    STROKE_MITERLIMIT { override fun toString() = "stroke-miterlimit" },
    STROKE_OPACITY { override fun toString() = "stroke-opacity" },
    STROKE_WIDTH { override fun toString() = "stroke-width" },
    TEXT_ALIGN { override fun toString() = "text-align" },
    TEXT_ANCHOR { override fun toString() = "text-anchor" },
    UNICODE_BIDI { override fun toString() = "unicode-bidi" },
    VECTOR_EFFECT { override fun toString() = "vector-effect" },
    VISIBILITY { override fun toString() = "visibility" },

    // Made-up properties
    // because "Compositions aren't SVGs and yadda yadda"
    // this one's for you, edwin
    SHADESTYLE { override fun toString() = "" };

    abstract override fun toString(): String
    // @formatter:on
}