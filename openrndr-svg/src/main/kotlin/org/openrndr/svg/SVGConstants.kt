package org.openrndr.svg

/** Element tag constants */
internal object Tag {
    const val CIRCLE = "circle"
    const val DEFS = "defs"
    const val ELLIPSE = "ellipse"
    const val G = "g"
    const val IMAGE = "image"
    const val LINE = "line"
    const val LINEAR_GRADIENT = "linearGradient"
    const val PATH = "path"
    const val POLYGON = "polygon"
    const val POLYLINE = "polyline"
    const val RADIAL_GRADIENT = "radialGradient"
    const val RECT = "rect"
    const val STOP = "stop"
    const val SVG = "svg"
    const val TBREAK = "tbreak"
    const val TEXT = "text"
    const val TEXT_AREA = "textArea"
    const val TSPAN = "tspan"
    const val USE = "use"

    val containerList = listOf(
        DEFS,
        G,
        SVG,
        USE
    )

    val graphicsList = listOf(
        CIRCLE,
        ELLIPSE,
        IMAGE,
        LINE,
        PATH,
        POLYGON,
        POLYLINE,
        RECT,
        STOP,
        TBREAK,
        TEXT,
        TEXT_AREA,
        TSPAN
    )
}

/** Attribute key constants */
internal object Attr {
    const val BASE_PROFILE = "baseProfile"
    const val CLASS = "class"
    const val CX = "cx"
    const val CY = "cy"
    const val D = "d"
    const val DX = "dx"
    const val DY = "dy"
    const val GRADIENT_UNITS = "gradientUnits"
    const val HEIGHT = "height"
    const val ID = "id"
    const val OFFSET = "offset"
    const val PATH_LENGTH = "pathLength"
    const val POINTS = "points"
    const val PRESERVE_ASPECT_RATIO = "preserveAspectRatio"
    const val R = "r"
    const val ROTATE = "rotate"
    const val RX = "rx"
    const val RY = "ry"
    const val SPACE = "xml:space"
    const val STYLE = "style"
    const val TRANSFORM = "transform"
    const val VERSION = "version"
    const val VIEW_BOX = "viewBox"
    const val WIDTH = "width"
    const val X = "x"
    const val X1 = "x1"
    const val X2 = "x2"
    const val Y = "y"
    const val Y1 = "y1"
    const val Y2 = "y2"
}

/**
 * Property key constants
 * These can also be defined in a style sheet/attribute
 */
internal object Prop {
    const val COLOR = "color"
    const val DIRECTION = "direction"
    const val DISPLAY = "display"
    const val DISPLAY_ALIGN = "display-align"
    const val FILL = "fill"
    const val FILL_OPACITY = "fill-opacity"
    const val FILL_RULE = "fill-rule"
    const val FONT_FAMILY = "font-family"
    const val FONT_SIZE = "font-size"
    const val FONT_STYLE = "font-style"
    const val FONT_VARIANT = "font-variant"
    const val FONT_WEIGHT = "font-weight"
    const val OPACITY = "opacity"
    const val STOP_COLOR = "stop-color"
    const val STOP_OPACITY = "stop-opacity"
    const val STROKE = "stroke"
    const val STROKE_DASHARRAY = "stroke-dasharray"
    const val STROKE_DASHOFFSET = "stroke-dashoffset"
    const val STROKE_LINECAP = "stroke-linecap"
    const val STROKE_LINEJOIN = "stroke-linejoin"
    const val STROKE_MITERLIMIT = "stroke-miterlimit"
    const val STROKE_OPACITY = "stroke-opacity"
    const val STROKE_WIDTH = "stroke-width"
    const val TEXT_ALIGN = "text-align"
    const val TEXT_ANCHOR = "text-anchor"
    const val UNICODE_BIDI = "unicode-bidi"
    const val VECTOR_EFFECT = "vector-effect"
    const val VISIBILITY = "visibility"

    val list = listOf(
        COLOR,
        DIRECTION,
        DISPLAY,
        DISPLAY_ALIGN,
        FILL,
        FILL_OPACITY,
        FILL_RULE,
        FONT_FAMILY,
        FONT_SIZE,
        FONT_STYLE,
        FONT_VARIANT,
        FONT_WEIGHT,
        OPACITY,
        STOP_COLOR,
        STOP_OPACITY,
        STROKE,
        STROKE_DASHARRAY,
        STROKE_DASHOFFSET,
        STROKE_LINECAP,
        STROKE_LINEJOIN,
        STROKE_MITERLIMIT,
        STROKE_OPACITY,
        STROKE_WIDTH,
        TEXT_ALIGN,
        TEXT_ANCHOR,
        UNICODE_BIDI,
        VECTOR_EFFECT,
        VISIBILITY
    )
}