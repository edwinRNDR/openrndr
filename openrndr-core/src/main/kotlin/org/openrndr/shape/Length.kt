package org.openrndr.shape

import org.openrndr.math.*
import org.openrndr.shape.Length.UnitType.*

data class CompositionVector2(val x: Length, val y: Length) {
    constructor(x: Double, y: Double) : this(Length(x), Length(y))

    companion object {
        val ZERO = CompositionVector2(0.0, 0.0)
    }

    // TODO! Add actual unit conversion
    val vector2: Vector2
        get() = Vector2(x.toPixels(), y.toPixels())
}

data class CompositionDimensions(val x: Length, val y: Length, val width: Length, val height: Length) {
    constructor(x: Double, y: Double, width: Double, height: Double) : this(
        Length(x),
        Length(y),
        Length(width),
        Length(height)
    )

    constructor(position: CompositionVector2, dimensions: CompositionVector2) : this(
        position.x,
        position.y,
        dimensions.x,
        dimensions.y
    )

    companion object {
        val ZERO = CompositionDimensions(0.0, 0.0, 0.0, 0.0)
    }

    val position: CompositionVector2
        get() = CompositionVector2(x, y)

    val dimensions: CompositionVector2
        get() = CompositionVector2(width, height)

    override fun toString() = "x=\"$x\" y=\"$y\" width=\"$width\" height=\"$height\""
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

    fun toPixels(): Double = units

    override fun toString(): String {
        return when (type) {
            // Pixels are implied when unit type is not present, so why include them?
            PX -> "$units"
            PERCENT -> "$units%"
            else -> "$units${type.name.lowercase()}"
        }
    }
}

// TODO! Add more of these
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