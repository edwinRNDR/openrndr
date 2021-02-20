package org.openrndr.math

import org.amshove.kluent.`should be greater than`
import org.openrndr.application
import org.openrndr.resourceUrl
import org.openrndr.svg.loadSVG
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TestSVGDraw : Spek({
    describe("a complex SVG file") {
        it ("it doesn't break the drawer") {
            application {
                program {
                    val composition = loadSVG(resourceUrl("/svg/text-template-2.svg"))
                    for (shape in composition.findShapes().map { it.shape  }) {
                        for (c in shape.contours) {
                            for (s in c.segments) {
                                s.length `should be greater than` 10E-6
                                val ap = s.adaptivePositions()
                                for (pair in ap.zipWithNext()) {
                                    val d = pair.second - pair.first
                                    d.length `should be greater than` 10E-6
                                }
                            }
                            val p = c.adaptivePositions()
                            for (pair in p.zipWithNext()) {
                                val d = pair.second - pair.first
                                d.length `should be greater than` 10E-6
                            }
                        }
                    }
                    extend {
                        drawer.composition(composition)
                        application.exit()
                    }
                }
            }
        }
    }
})