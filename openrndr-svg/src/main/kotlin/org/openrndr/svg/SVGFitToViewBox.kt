package org.openrndr.svg

import org.openrndr.math.*
import org.openrndr.shape.*

/** Should apply to <svg> */
interface SVGFitToViewBox {
    var viewBox: Rectangle?
    var preserveAspectRatio: Alignment
    var currentTransform: Matrix44
}