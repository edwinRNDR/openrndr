@file:Suppress("unused")

package org.openrndr.color

import org.openrndr.math.mixAngle
import org.openrndr.math.mod
import kotlin.math.floor


/**
 * A color respresentation in HSVa space
 *
 * @param h hue in n * [0, 360)
 * @param s value in [0, 1]
 * @param v value in [0, 1]
 * @param a alpha in [0, 1]
 */
@Suppress("unused")
data class ColorHSVa(val h: Double, val s: Double, val v: Double, val a: Double = 1.0) :
        ConvertibleToColorRGBa,
        ShadableColor<ColorHSVa>,
        HueShiftableColor<ColorHSVa>,
        SaturatableColor<ColorHSVa>,
        AlgebraicColor<ColorHSVa> {

    operator fun invoke(h: Double = this.h, s: Double = this.s, v: Double = this.v, a: Double = this.a) =
            ColorHSVa(h, s, v, a)

    companion object {
        fun fromRGBa(rgb: ColorRGBa): ColorHSVa {
            var min = Double.POSITIVE_INFINITY
            var max = Double.NEGATIVE_INFINITY

            var h: Double
            var maxArg: ColorRGBa.Component? = null

            if (rgb.r <= rgb.b && rgb.r <= rgb.g) {
                min = rgb.r
            }
            if (rgb.g <= rgb.b && rgb.g <= rgb.r) {
                min = rgb.g
            }
            if (rgb.b <= rgb.r && rgb.b <= rgb.g) {
                min = rgb.b
            }

            if (rgb.r >= rgb.b && rgb.r >= rgb.g) {
                maxArg = ColorRGBa.Component.R
                max = rgb.r
            }
            if (rgb.g >= rgb.b && rgb.g >= rgb.r) {
                maxArg = ColorRGBa.Component.G
                max = rgb.g
            }
            if (rgb.b >= rgb.r && rgb.b >= rgb.g) {
                maxArg = ColorRGBa.Component.B
                max = rgb.b
            }

            val s: Double
            val v = max
            val delta = max - min
            if (max != 0.0) {
                s = delta / max
            } else {
                // r = g = b = 0		// s = 0, v is undefined
                s = 0.0
                h = 0.0
                return ColorHSVa(h, s, v, rgb.a)
            }
            if (maxArg == ColorRGBa.Component.R) {
                h = (rgb.g - rgb.b) / delta        // between yellow & magenta
            } else if (maxArg == ColorRGBa.Component.G) {
                h = 2 + (rgb.b - rgb.r) / delta    // between cyan & yellow
            } else {
                h = 4 + (rgb.r - rgb.g) / delta    // between magenta & cyan
            }
            h *= 60.0                // degrees
            if (h < 0) {
                h += 360.0
            }
            return ColorHSVa(h, s, v, rgb.a)
        }
    }


    override fun shiftHue(shiftInDegrees: Double) = copy(h = (h + shiftInDegrees))
    override fun saturate(factor: Double) = copy(s = s * factor)
    override fun shade(factor: Double): ColorHSVa = copy(v = v * factor)

    override fun mix(other: ColorHSVa, factor: Double) = mix(this, other, factor)

    /**
     * a unit presentation of this ColorHSVa, essentially brings the hue back in [0, 360)
     * @return a copy with the hue value in [0, 360)
     */
    val unit get() = copy(h = ((h % 360) + 360) % 360)

    override fun toRGBa(): ColorRGBa {
        val i: Int
        val f: Double

        val r: Double
        val g: Double
        val b: Double
        val hsv = this


        val sh = mod(hsv.h, 360.0) / 60            // sector 0 to 5
        i = floor(sh).toInt()
        f = sh - i            // factorial part of h
        val p = hsv.v * (1 - hsv.s)
        val q = hsv.v * (1 - hsv.s * f)
        val t = hsv.v * (1 - hsv.s * (1 - f))
        if (s > 0.00001) {
            when (i) {
                0 -> {
                    r = hsv.v
                    g = t
                    b = p
                }
                1 -> {
                    r = q
                    g = hsv.v
                    b = p
                }
                2 -> {
                    r = p
                    g = hsv.v
                    b = t
                }
                3 -> {
                    r = p
                    g = q
                    b = hsv.v
                }
                4 -> {
                    r = t
                    g = p
                    b = hsv.v
                }
                else        // case 5:
                -> {
                    r = hsv.v
                    g = p
                    b = q
                }
            }
        } else {
            r = hsv.v
            g = hsv.v
            b = hsv.v
        }
        return ColorRGBa(r, g, b, hsv.a, Linearity.SRGB)

    }

    override fun plus(other: ColorHSVa) = copy(h = h + other.h, s = s + other.s, v = v + other.v, a = a + other.a)
    override fun minus(other: ColorHSVa) = copy(h = h - other.h, s = s - other.s, v = v - other.v, a = a - other.a)
    override fun times(factor: Double) = copy(h = h * factor, s = s * factor, v = v * factor, a = a * factor)

    fun toXSVa(): ColorXSVa {
        return ColorXSVa.fromHSVa(this)
    }
}

fun hsv(h: Double, s: Double, v: Double) = ColorHSVa(h, s, v)
fun hsva(h: Double, s: Double, v: Double, a: Double) = ColorHSVa(h, s, v, a)

/**
 * Mixes two colors in HSVa space
 * @param left the left hand ColorHSVa color
 * @param right the right hand ColorHSVa
 * @param x the mix amount
 * @return a mix of [left] and [right], x == 0.0 corresponds with left, x == 1.0 corresponds with right
 */
fun mix(left: ColorHSVa, right: ColorHSVa, x: Double): ColorHSVa {
    val sx = x.coerceIn(0.0, 1.0)
    return ColorHSVa(
            mixAngle(left.h, right.h, sx),
            (1.0 - sx) * left.s + sx * right.s,
            (1.0 - sx) * left.v + sx * right.v,
            (1.0 - sx) * left.a + sx * right.a)
}
