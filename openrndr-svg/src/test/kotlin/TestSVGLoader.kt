package org.openrndr.math

import org.amshove.kluent.*
import org.openrndr.*
import org.openrndr.color.*
import org.openrndr.draw.LineCap
import org.openrndr.draw.LineJoin
import org.openrndr.shape.*
import org.openrndr.svg.*
import org.spekframework.spek2.*
import org.spekframework.spek2.style.specification.*

object TestSVGLoader : Spek({

    describe("a simple SVG file") {
        val composition = loadSVG(resourceUrl("/svg/closed-shapes.svg"))

        it("it has namespaces") {
            composition.namespaces.`should have key`("xmlns")
        }

        it("has only closed shapes") {
            composition.findShapes().all { it.shape.contours.all(ShapeContour::closed) } `should be equal to` true
        }

        it("has only clockwise shapes") {
            composition.findShapes()
                .all { node -> node.shape.contours.all { it.winding == Winding.CLOCKWISE } } `should be equal to` true
        }
    }

    describe("the svg file 'star.svg'") {
        val composition = loadSVG(resourceUrl("/svg/star.svg"))

        it("has only open shapes") {
            composition.findShapes().all { it.shape.contours.none(ShapeContour::closed) } `should be equal to` true
            composition.findShapes().size `should be equal to` 37
        }
    }

    describe("the svg file 'text-001.svg'") {
        val composition = loadSVG(resourceUrl("/svg/text-001.svg"))
        composition.findShapes().all { it.shape.contours.all(ShapeContour::closed) } `should be equal to` true
    }
    describe("the svg file 'open-compound.svg'") {
        val composition = loadSVG(resourceUrl("/svg/open-compound.svg"))
        composition.findShapes()[0].shape.topology `should be equal to` ShapeTopology.OPEN
        composition.findShapes().all { it.shape.contours.all(ShapeContour::closed) } `should be equal to` false
    }

    describe("the svg file 'patterns-2.svg'") {
        val composition = loadSVG(resourceUrl("/svg/patterns-2.svg"))
        triangulate(composition.findShapes()[0].shape)
//        composition.findShapes()[0].shape.topology `should be equal to` ShapeTopology.OPEN
//        composition.findShapes().all { node -> node.shape.contours.all { it.closed } } `should be equal to` false
    }

    describe("the svg file 'delta.svg'") {
        it("correctly parsed a list of transforms") {
            val composition = loadSVG(resourceUrl("/svg/delta.svg"))
            composition.findShapes()[1].effectiveTransform `should be equal to`
                Matrix44(
                    0.2, 0.0, 0.0, 8.0,
                    0.0, 0.2, 0.0, 21.888,
                    0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 1.0,
                )
        }
    }

    describe("the svg file 'viewBox-and-graphics.svg'") {
        val composition = loadSVG(resourceUrl("/svg/viewBox-and-graphics.svg"))
        val shape0 = composition.findShapes()[0]
        val shape1 = composition.findShapes()[1]

        it("has the specified viewBox") {
            (composition.documentStyle.viewBox as ViewBox.Value).value `should be equal to` Rectangle(
                8.0,
                0.0,
                16.0,
                16.0
            )
        }

        it("has the specified dimensions") {
            composition.bounds `should be equal to` CompositionDimensions(
                6.0.pixels,
                7.0.pixels,
                32.0.pixels,
                32.0.pixels
            )
        }
        it("can calculate the viewBox transformation along with the element transformation") {
            composition.calculateViewportTransform() `should be equal to` Matrix44(
                2.0, 0.0, 0.0, -10.0,
                0.0, 2.0, 0.0, 7.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0
            )
            shape0.effectiveTransform `should be equal to` Matrix44(
                1.0, 0.0, 0.0, 16.0,
                0.0, 1.0, 0.0, -8.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0
            )
        }

        it("has correctly parsed the functional RGB notation in fill") {
            shape0.fill `should be equal to` ColorRGBa(0.75, 0.14, 0.33, linearity = Linearity.SRGB)
        }

        it("has correctly parsed various stroke properties") {
            shape1.lineCap `should be equal to` LineCap.ROUND
            shape1.lineJoin `should be equal to` LineJoin.ROUND
            shape1.miterLimit `should be equal to` 50.0
        }

        it("has correctly parsed the opacity properties") {
            shape1.opacity `should be equal to` 0.8
            shape1.strokeOpacity `should be equal to` 0.5
            shape1.fillOpacity `should be equal to` 0.25
        }
    }
})