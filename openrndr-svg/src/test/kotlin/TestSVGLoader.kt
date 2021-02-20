package org.openrndr.math

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should have key`
import org.openrndr.resourceUrl
import org.openrndr.shape.ShapeTopology
import org.openrndr.shape.Winding
import org.openrndr.shape.triangulate
import org.openrndr.svg.loadSVG
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TestSVGLoader : Spek({

    describe("a simple SVG file") {
        val composition = loadSVG(resourceUrl("/svg/closed-shapes.svg"))

        it ("it has namespaces") {
            composition.namespaces.`should have key`("xmlns")
        }

        it("has only closed shapes") {
            composition.findShapes().all { it.shape.contours.all { it.closed } } `should be equal to` true
        }

        it("has only clockwise shapes") {
            composition.findShapes().all { node -> node.shape.contours.all { it.winding == Winding.CLOCKWISE } } `should be equal to` true
        }
    }

    describe("the svg file 'star.svg'") {
        val composition = loadSVG(resourceUrl("/svg/star.svg"))

        it("has only open shapes") {
            composition.findShapes().all { it.shape.contours.all { !it.closed } } `should be equal to` true
            composition.findShapes().size `should be equal to` 37
        }
    }

    describe("the svg file 'text-001.svg'") {
        val composition = loadSVG(resourceUrl("/svg/text-001.svg"))
        composition.findShapes().all { it.shape.contours.all { it.closed } } `should be equal to` true
    }
    describe("the svg file 'open-compound.svg'") {
        val composition = loadSVG(resourceUrl("/svg/open-compound.svg"))
        composition.findShapes()[0].shape.topology `should be equal to` ShapeTopology.OPEN
        composition.findShapes().all { node -> node.shape.contours.all { it.closed } } `should be equal to` false
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
                    Matrix44(0.2, 0.0, 0.0, 8.0,
                             0.0, 0.2, 0.0, 21.888,
                             0.0, 0.0, 0.0, 0.0,
                             0.0, 0.0, 0.0, 1.0,)
        }
    }
})