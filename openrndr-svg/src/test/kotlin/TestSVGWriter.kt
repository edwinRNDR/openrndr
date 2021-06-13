package org.openrndr.math

import org.amshove.kluent.*
import org.openrndr.color.*
import org.openrndr.shape.*
import org.openrndr.svg.*
import org.spekframework.spek2.*
import org.spekframework.spek2.style.specification.*

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
                fill = ColorRGBa.RED
                translate(Vector2(50.0, 50.0))
                circle(Vector2(100.0, 100.0), 50.0)?.id = "circle"
            }
        }
        it("can be written and loaded") {
            val svgString = comp.toSVG()
            val loaded = loadSVG(svgString)
            val shape = loaded.findShape("circle")
            shape?.effectiveFill `should be equal to` ColorRGBa(1.0, 0.0, 0.0, 1.0, Linearity.SRGB)
            shape?.effectiveTransform?.get(3) `should be equal to` Vector4(50.0, 50.0, 0.0, 1.0)
        }
    }

})