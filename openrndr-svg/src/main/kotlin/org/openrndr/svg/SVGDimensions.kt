package org.openrndr.svg

import org.openrndr.shape.*

/** Should apply to <rect>, <svg>, <image> */
interface SVGDimensions {
    var bounds: CompositionDimensions
}