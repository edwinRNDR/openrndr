package org.openrndr.math

import org.amshove.kluent.`should be equal to`
import org.openrndr.shape.CompositionDrawer
import org.openrndr.shape.TransformMode
import org.openrndr.shape.drawComposition
import org.openrndr.svg.loadSVG
import org.openrndr.svg.toSVG
import org.openrndr.svg.writeSVG
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TestSVGWriter : Spek({

    describe("a composition") {
        val cd = CompositionDrawer()
        cd.group(id = "Layer_2") {
            val circle = cd.circle(Vector2(100.0, 100.0), 40.0)
            circle?.attributes?.set("openrndr:test", "5")
        }
        val comp = cd.composition
        it("can be written to SVG string") {
            val t = writeSVG(comp)
            println(t)
        }
    }
    describe("a composition with transforms") {
        val comp = drawComposition {
            transformMode = TransformMode.KEEP
            group {
                translate(Vector2(50.0, 50.0))
                circle(Vector2(100.0, 100.0), 50.0)?.id = "circle"
            }
        }
        it("can be written and loaded") {
            val svgString = comp.toSVG()
            val loaded = loadSVG(svgString)
            val shape = loaded.findShape("circle")
            shape?.effectiveTransform?.get(3) `should be equal to` Vector4(50.0, 50.0, 0.0, 1.0)
        }
    }

})