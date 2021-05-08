@file:Suppress("RemoveExplicitTypeArguments")

package org.openrndr.shape

import org.openrndr.color.*
import org.openrndr.draw.*
import org.openrndr.math.*
import org.openrndr.shape.PropertyInheritance.*
import kotlin.reflect.*

enum class PropertyInheritance {
    INHERIT,
    RESET
}

data class Property(
    val name: String,
    val value: Any?
)

open class PropertyValue(val inherit: Boolean = false)

sealed class Paint(inherit: Boolean = false) : PropertyValue(inherit) {
    class RGB(val color: ColorRGBa) : Paint()
    object None : Paint()
    object Inherit : Paint(inherit = true)
}

sealed class Length(inherit: Boolean = false) : PropertyValue(inherit) {
    open class Pixels(val value: Double) : Length() {
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
    class Percent(val value: Double) : Length() {
        override fun toString(): String {
            return "${value}%"
        }
    }

    object Auto : Length()
    object Inherit : Length(inherit = true)
}

inline val Double.pixels: Length.Pixels
    get() = Length.Pixels(this)
inline val Double.percent: Length.Percent
    get() = Length.Percent(this)

sealed class RealNumber(inherit: Boolean = false) : PropertyValue(inherit) {
    class Value(val value: Double) : RealNumber() {
        override fun toString(): String = "$value"
    }

    object Inherit : RealNumber(inherit = true)
}

// TODO! This needs to be special cased
sealed class Transform(inherit: Boolean = false) : PropertyValue(inherit) {
    class Matrix(val value: Matrix44) : Transform()
    object None : Transform()
}

data class PropertyBehavior(val inherit: PropertyInheritance, val initial: Any)

private object PropertyBehaviors {
    val behaviors = HashMap<String, PropertyBehavior>()
}

class PropertyHandler<T>(val name: String, val inheritance: PropertyInheritance, val initial: T) {
    init {
        PropertyBehaviors.behaviors[name] = PropertyBehavior(inheritance, initial as Any)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(style: Style, property: KProperty<*>): T? {
        return style.getProperty(name)?.value as T?
    }

    operator fun setValue(style: Style, property: KProperty<*>, value: T?) {
        style.setProperty(name, value)
    }
}

class Style {
    val children = mutableListOf<Style>()
    val properties = HashMap<String, Property>()

    fun getProperty(name: String) = properties[name]

    fun setProperty(name: String, value: Any?) {
        properties[name] = Property(name, value)
    }
}

enum class Visibility {
    VISIBLE,
    HIDDEN,
    COLLAPSE
}

// CSS2 spec has a lot of possible values for display,
// but these are the most common
enum class Display {
    INLINE,
    BLOCK,
    NONE
}

var Style.stroke by PropertyHandler<Paint>("stroke", INHERIT, Paint.None)
var Style.strokeOpacity by PropertyHandler<Length>("stroke-opacity", INHERIT, 1.0.pixels)
var Style.strokeWeight by PropertyHandler<Length>("stroke-width", INHERIT, 1.0.pixels)
var Style.miterlimit by PropertyHandler<RealNumber>("miterlimit", INHERIT, RealNumber.Value(4.0))
var Style.lineCap by PropertyHandler<LineCap>("stroke-linecap", INHERIT, LineCap.BUTT)
var Style.lineJoin by PropertyHandler<LineJoin>("stroke-linejoin", INHERIT, LineJoin.MITER)

var Style.fill by PropertyHandler<Paint>("fill", INHERIT, Paint.RGB(ColorRGBa.BLACK))
var Style.fillOpacity by PropertyHandler<Length>("fill-opacity", INHERIT, 1.0.pixels)

var Style.transform by PropertyHandler<Transform>("transform", RESET, Transform.None)

// Okay so the spec says `display` isn't inheritable, but effectively acts so
// when the element and its children are excluded from the rendering tree.
var Style.display by PropertyHandler<Display>("display", RESET, Display.INLINE)
var Style.opacity by PropertyHandler<Length>("opacity", RESET, 1.0.pixels)
var Style.visibility by PropertyHandler<Visibility>("visibility", INHERIT, Visibility.VISIBLE)