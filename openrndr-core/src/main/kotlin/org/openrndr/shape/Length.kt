package org.openrndr.shape

import org.openrndr.math.*
import org.openrndr.shape.Length.UnitType.*


open class CompositionVector2(val x: Length, val y: Length) {
    constructor(x: Double, y: Double) : this(Length(x), Length(y))

    companion object {
        val ZERO = CompositionVector2(0.0, 0.0)
    }

    override fun toString() = "x=\"$x\" y=\"$y\""

    val toVector2: Vector2
        get() = Vector2(x.pixels, y.pixels)
}

/**
 * Composition dimensions, the difference from [CompositionVector2]
 * is mostly semantic.
 * It also has a different `toString()` implementation.
 */
data class CompositionDimensions(val width: Length, val height: Length) :
    CompositionVector2(width, height) {
    constructor(x: Double, y: Double) : this(Length(x), Length(y))
    override fun toString() = "width=\"$width\" height=\"$height\""
}

/**
 * Intermediate representation of SVG/CSS units.
 * Does not convert or validate anything.
 * Without the unit type, pixels are assumed.
 */
data class Length(val units: Double, val type: UnitType = PX) {
    // It could've been lowercase but `in` is a reserved keyword :/
    enum class UnitType {
        IN,
        PC,
        PT,
        PX,
        CM,
        MM,
        Q,
        EM,
        EX,
        CH,
        PERCENT
    }

    override fun toString(): String {
        return when (type) {
            // Pixels are implied when unit type is not present, so why include them?
            PX -> "$units"
            PERCENT -> "$units%"
            else -> "$units${type.name.toLowerCase()}"
        }
    }

    val pixels: Double
        get() = units
}

// TODO: Add more of these
val Double.inches: Length
    get() = Length(this, IN)
val Double.millimeters: Length
    get() = Length(this, MM)
val Double.centimeters: Length
    get() = Length(this, CM)
val Double.pixels: Length
    get() = Length(this, PX)
val Double.percent: Length
    get() = Length(this, PERCENT)