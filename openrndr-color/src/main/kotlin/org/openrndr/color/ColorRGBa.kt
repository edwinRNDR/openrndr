package org.openrndr.color

import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import java.lang.NumberFormatException
import kotlin.math.pow


enum class Linearity {
    UNKNOWN,
    LINEAR,
    SRGB,
    ASSUMED_LINEAR,
    ASSUMED_SRGB
}

/**
 * color in RGBa space
 *
 * @param r red between 0 and 1
 * @param g green between 0 and 1
 * @param b blue between 0 and 1
 * @param a alpha between 0 and 1
 */
data class ColorRGBa(val r: Double, val g: Double, val b: Double, val a: Double = 1.0, val linearity: Linearity = Linearity.UNKNOWN) :
        ConvertibleToColorRGBa,
        OpacifiableColor<ColorRGBa>,
        ShadableColor<ColorRGBa>,
        AlgebraicColor<ColorRGBa> {

    operator fun invoke(r: Double = this.r, g: Double = this.g, b: Double = this.b, a: Double = this.a) = ColorRGBa(r, g, b, a)

    enum class Component {
        R,
        G,
        B,
        a
    }

    companion object {
        fun fromHex(hex: Int): ColorRGBa {
            val r = hex and (0xff0000) shr 16
            val g = hex and (0x00ff00) shr 8
            val b = hex and (0x0000ff)
            return ColorRGBa(r / 255.0, g / 255.0, b / 255.0, 1.0, Linearity.SRGB)
        }

        fun fromHex(hex: String): ColorRGBa {
            val parsedHex = hex.replace("#", "")
            val len = parsedHex.length
            val mult = len / 3

            val colors = (0..2).map { idx ->
                var c = parsedHex.substring(idx * mult, (idx + 1) * mult)

                c = if (len == 3) c + c else c

                try {
                    c.toInt(16)
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Cannot convert input '$hex' to an RGBa color value.")
                }
            }

            val (r, g, b) = colors

            return ColorRGBa(r / 255.0, g / 255.0, b / 255.0, 1.0, Linearity.SRGB)
        }

        /** @suppress */
        val PINK = fromHex(0xffc0cb)

        /** @suppress */
        val BLACK = ColorRGBa(0.0, 0.0, 0.0, 1.0)

        /** @suppress */
        val WHITE = ColorRGBa(1.0, 1.0, 1.0, 1.0)

        /** @suppress */
        val RED = ColorRGBa(1.0, 0.0, 0.0, 1.0)

        /** @suppress */
        val BLUE = ColorRGBa(0.0, 0.0, 1.0)

        /** @suppress */
        val GREEN = ColorRGBa(0.0, 1.0, 0.0)

        /** @suppress */
        val YELLOW = ColorRGBa(1.0, 1.0, 0.0)

        /** @suppress */
        val GRAY = ColorRGBa(0.5, 0.5, 0.5)

        /** @suppress */
        val TRANSPARENT = ColorRGBa(0.0, 0.0, 0.0, 0.0)

        /**
         * Create a ColorRGBa object from a [Vector3]
         * @param vector input vector, `[x, y, z]` is mapped to `[r, g, b]`
         * @param alpha optional alpha value, default is 1.0
         */
        fun fromVector(vector: Vector3, alpha: Double = 1.0): ColorRGBa {
            return ColorRGBa(vector.x, vector.y, vector.z, alpha)
        }


        /**
         * Create a ColorRGBa object from a [Vector4]
         * @param vector input vector, `[x, y, z, w]` is mapped to `[r, g, b, a]`
         */
        fun fromVector(vector: Vector4): ColorRGBa {
            return ColorRGBa(vector.x, vector.y, vector.z, vector.w)
        }
    }

    /**
     * Creates a copy of color with adjusted opacity
     * @param factor a scaling factor used for the opacity
     * @return A [ColorRGBa] with scaled opacity
     * @see shade
     */
    override fun opacify(factor: Double): ColorRGBa = ColorRGBa(r, g, b, a * factor)

    /**
     * Creates a copy of color with adjusted color
     * @param factor a scaling factor used for the opacity
     * @return A [ColorRGBa] with scaled colors
     * @see opacify
     */
    override fun shade(factor: Double): ColorRGBa = ColorRGBa(r * factor, g * factor, b * factor, a)

    /**
     * Copy of the the color with all of its fields clamped to `[0, 1]`
     */
    val saturated get() = ColorRGBa(r.coerceIn(0.0, 1.0), g.coerceIn(0.0, 1.0), b.coerceIn(0.0, 1.0), a.coerceIn(0.0, 1.0))
    val alphaMultiplied get() = ColorRGBa(r * a, g * a, b * a, a)

    /**
     * The minimum value over `r`, `g`, `b`
     * @see maxValue
     */
    val minValue get() = r.coerceAtMost(g).coerceAtMost(b)

    /**
     * The maximum value over `r`, `g`, `b`
     * @see minValue
     */
    val maxValue get() = r.coerceAtLeast(g).coerceAtLeast(b)

    /**
     * calculate luminance value
     * luminance value is according to <a>https://www.w3.org/TR/2008/REC-WCAG20-20081211/#relativeluminancedef</a>
     */
    val luminance: Double
        get() = when(linearity) {
        Linearity.SRGB -> toLinear().luminance
        else -> 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * calculate the contrast value between this color and the given color
     * contrast value is accordingo to <a>// see http://www.w3.org/TR/2008/REC-WCAG20-20081211/#contrast-ratiodef</a>
     */
    fun getContrastRatio(other: ColorRGBa): Double {
        val l1 = luminance
        val l2 = other.luminance
        return if (l1 > l2) (l1 + 0.05) / (l2 + 0.05) else (l2 + 0.05) / (l1 + 0.05)
    }

    fun toHSVa(): ColorHSVa = ColorHSVa.fromRGBa(this.toSRGB())
    fun toHSLa(): ColorHSLa = ColorHSLa.fromRGBa(this.toSRGB())
    fun toXSVa(): ColorXSVa = ColorHSVa.fromRGBa(this.toSRGB()).toXSVa()
    fun toXSLa(): ColorXSLa = ColorHSLa.fromRGBa(this.toSRGB()).toXSLa()
    fun toXYZa(): ColorXYZa = ColorXYZa.fromRGBa(this.toLinear())
    @JvmOverloads
    fun toLABa(ref: ColorXYZa = ColorXYZa.NEUTRAL): ColorLABa = ColorLABa.fromRGBa(this.toLinear(), ref)
    @JvmOverloads
    fun toLUVa(ref: ColorXYZa = ColorXYZa.NEUTRAL): ColorLUVa = ColorLUVa.fromRGBa(this.toLinear(), ref)
    @JvmOverloads
    fun toLCHABa(ref: ColorXYZa = ColorXYZa.NEUTRAL): ColorLCHABa = toXYZa().toLABa(ref).toLCHABa()
    @JvmOverloads
    fun toLCHUVa(ref: ColorXYZa = ColorXYZa.NEUTRAL): ColorLCHUVa = toLUVa(ref).toLCHUVa()

    /**
     * Convert to linear RGB
     * @see toSRGB
     */
    fun toLinear(): ColorRGBa {
        fun t(x: Double): Double {
            return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / (1 + 0.055)).pow(2.4)
        }
        return when (linearity) {
            Linearity.SRGB -> ColorRGBa(t(r), t(g), t(b), a, Linearity.LINEAR)
            Linearity.UNKNOWN, Linearity.ASSUMED_SRGB -> ColorRGBa(t(r), t(g), t(b), a, Linearity.ASSUMED_LINEAR)
            Linearity.ASSUMED_LINEAR, Linearity.LINEAR -> this
        }
    }

    /**
     * Convert to SRGB
     * @see toLinear
     */
    fun toSRGB(): ColorRGBa {
        fun t(x: Double): Double {
            return if (x <= 0.0031308) 12.92 * x else (1 + 0.055) * x.pow(1.0 / 2.4) - 0.055
        }
        return when (linearity) {
            Linearity.LINEAR -> ColorRGBa(t(r), t(g), t(b), a, Linearity.SRGB)
            Linearity.UNKNOWN, Linearity.ASSUMED_LINEAR -> ColorRGBa(t(r), t(g), t(b), a, Linearity.ASSUMED_SRGB)
            Linearity.ASSUMED_SRGB, Linearity.SRGB -> this
        }
    }

    override fun toRGBa(): ColorRGBa = this

    // This is here because the default hashing of enums on the JVM is not stable.
    override fun hashCode(): Int {
        var result = r.hashCode()
        result = 31 * result + g.hashCode()
        result = 31 * result + b.hashCode()
        result = 31 * result + a.hashCode()
        // here we overcome the unstable hash by using the ordinal value
        result = 31 * result + linearity.ordinal.hashCode()
        return result
    }

    override fun plus(other: ColorRGBa) = copy(r = r + other.r, g = g + other.g, b = b + other.b, a = a + other.a)

    override fun minus(other: ColorRGBa) = copy(r = r - other.r, g = g - other.g, b = b - other.b, a = a - other.a)

    override fun times(factor: Double) = copy(r = r * factor, g = g * factor, b = b * factor, a = a * factor)

}

/**
 * Mixes two colors in RGBa space
 */
fun mix(left: ColorRGBa, right: ColorRGBa, x: Double): ColorRGBa {
    val sx = x.coerceIn(0.0, 1.0)
    return ColorRGBa(
            (1.0 - sx) * left.r + sx * right.r,
            (1.0 - sx) * left.g + sx * right.g,
            (1.0 - sx) * left.b + sx * right.b,
            (1.0 - sx) * left.a + sx * right.a)
}

/**
 * Color in RGBa space. Specify one value only to obtain a shade of gray.
 * @param r red in `[0,1]`
 * @param g green in `[0,1]`
 * @param b blue in `[0,1]`
 */
fun rgb(r: Double, g: Double = r, b: Double = r) = ColorRGBa(r, g, b)

/**
 * Create a color in RGBa space
 * This function is a short-hand for using the ColorRGBa constructor
 * @param r red in `[0,1]`
 * @param g green in `[0,1]`
 * @param b blue in `[0,1]`
 * @param a alpha in `[0,1]`
 */
fun rgba(r: Double, g: Double, b: Double, a: Double) = ColorRGBa(r, g, b, a)

/**
 * Create color from a string encoded hex value
 * @param hex string encoded hex value, for example `"ffc0cd"`
 */
fun rgb(hex: String) = ColorRGBa.fromHex(hex)
