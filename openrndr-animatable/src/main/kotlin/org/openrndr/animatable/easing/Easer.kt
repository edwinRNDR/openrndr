package org.openrndr.animatable.easing

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

interface Easer {
    fun velocity(t: Double, b: Double, c: Double, d: Double): Double
    fun ease(t: Double, b: Double, c: Double, d: Double): Double
}

enum class Easing(val easer:Easer) {
    None(Linear()),
    SineIn(SineIn()),
    SineOut(SineOut()),
    SineInOut(SineInOut()),
    CubicIn(CubicIn()),
    CubicOut(CubicOut()),
    CubicInOut(CubicInOut()),
    QuadIn(QuadIn()),
    QuadOut(QuadOut()),
    QuadInOut(QuadInOut()),
    QuartIn(QuartIn()),
    QuartOut(QuartOut()),
    QuartInOut(QuartInOut())
}

class Linear : Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double = c / d
    override fun ease(t: Double, b: Double, c: Double, d: Double): Double = b + c * (t / d)
}

@Suppress("unused")
class BackIn : Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double {
        return c * t * (8.10474 * t - 3.40316 * d) / (d * d * d)
    }

    override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
        val s = 1.70158
        val td = t/d
        return c * (td) * td * ((s + 1) * td - s) + b
    }
}

@Suppress("unused")
class ExpoIn : Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double {
        return if (t == 0.0) {
            0.0
        } else {
            c * ln(32.0) * 2.0.pow(10 * t / d - 9) / d
        }
    }

    override fun ease(t: Double, b: Double, c: Double, d: Double): Double =
            if (t == 0.0) b else c * 2.0.pow(10 * (t / d - 1)) + b
}

class SineOut : Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double = Math.PI * c * cos(Math.PI * t) / (2 * d) / (2 * d)
    override fun ease(t: Double, b: Double, c: Double, d: Double): Double = c * sin(t / d * (Math.PI / 2)) + b
}

class SineIn : Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double = -Math.PI * c * sin(Math.PI * t / (2 * d)) / (2 * d)
    override fun ease(t: Double, b: Double, c: Double, d: Double): Double = -c * cos(t / d * (Math.PI / 2)) + c + b
}

class SineInOut : Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double = Math.PI * c * sin(Math.PI * t) / d / (2 * d)
    override fun ease(t: Double, b: Double, c: Double, d: Double): Double = -c / 2 * (cos(Math.PI * t / d) - 1) + b
}

class CubicIn: Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double = 3.0 * c * t * t / (d * d * d)

    override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
        val td = t / d
        return c * td * td * td + b
    }
}

class CubicOut: Easer {
    override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
        val td = t / d - 1.0
        return c * (td * td *td  + 1) + b
    }

    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double {
        return 3.0 * c * (d - t) * (d - t) / (d * d * d)
    }
}

class CubicInOut: Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double {
        return if (t / (d / 2) < 1) {
            12.0 * c * t * t / (d * d * d)
        } else {
            12 * c * (d - t) * (d - t) / (d * d * d)
        }
    }

    override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
        val td = t / (d/2)
        val td2 = td - 2.0
        return if (td < 1) c / 2 * td * td * td + b else c / 2 * (td2 * td2 * td2 + 2) + b
    }
}

class QuadIn: Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double = 2.0 * c * t / (d * d)
    override fun ease(t: Double, b: Double, c: Double, d: Double): Double = c * (t/d) * (t/d) + b
}

class QuadOut: Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double {
        return -2.0 * c * (d - t) / (d * d)    }

    override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
        return -c * (t/d) * (t/d - 2) + b
    }
}

class QuadInOut: Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double {
        return if (t / (d / 2) < 1) {
            4.0 * c * t / (d * d)
        } else {
            4.0 * c * (d - t) / (d * d)
        }
    }

    override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
        val td = t/(d/2)
        return if (td < 1) {
            c / 2 * td * td + b
        } else {
            -c / 2 * ((td-1) * (td - 3) - 1) + b
        }
    }
}

class QuartIn: Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double {
        return 4.0 * c * (t * t * t) / (d * d * d * d)
    }

    override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
        val n = t / d
        return c * n * n * n * n + b
    }
}

class QuartOut: Easer {
    override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
        val td = t / d - 1
        return -c * (td * td * td * td - 1) + b
    }

    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double {
        return (4.0 * c * (d - t) * (d - t) * (d - t)) / (d * d * d * d)
    }
}

class QuartInOut: Easer {
    override fun velocity(t: Double, b: Double, c: Double, d: Double): Double {
        return if (t / (d / 2) < 1) {
            32.0 * c * t * t * t / (d * d * d * d)
        } else {
            32.0 * c * (d - t) * (d - t) * (d - t) / (d * d * d * d)
        }
    }

    override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
        val td = t/(d/2)
        val td2 = td - 2.0

        return if(td < 1) c / 2 * td * td * td * td + b else -c / 2 * (td2 * td2 * td2 * td2 - 2) + b
    }

}