package org.openrndr.math.transforms

import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.closeTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.math.atan2
import kotlin.math.tan

object TestTransforms : Spek({

    val maxError = 0.0000001

    describe("Projection Transforms") {

        val near = 0.01
        val far = 100.0
        val fovY = 45.0
        val aspectRatio = 16.0 / 9.0

        it("should create a finite perspective projection matrix") {

            val expected = Matrix44(
                    1.3579951288348662, 0.0, 0.0, 0.0,
                    0.0, 2.414213562373095, 0.0, 0.0,
                    0.0, 0.0, -1.0002000200020003, -0.020002000200020003,
                    0.0, 0.0, -1.0, 0.0)

            val p = perspective(fovY, aspectRatio, near, far)

            p.closeTo(expected, maxError)

            val t1 = p * Vector4(0.0, 0.0, near * -1, 1.0)
            (t1.z / t1.w).closeTo(-1.0, maxError)

            val t2 = p * Vector4(0.0, 0.0, far * -1, 1.0)
            (t2.z / t2.w).closeTo(1.0, maxError)
        }

        it("should create a infinite perspective projection matrix") {
            val expected = Matrix44(
                    1.3579951288348662, 0.0, 0.0, 0.0,
                    0.0, 2.414213562373095, 0.0, 0.0,
                    0.0, 0.0, -1.0, -0.02,
                    0.0, 0.0, -1.0, 0.0)

            val p = perspective(fovY, aspectRatio, near)

            p.closeTo(expected, maxError)

            val t1 = p * Vector4(0.0, 0.0, near * -1, 1.0)
            (t1.z / t1.w).closeTo(-1.0, maxError)

            val t2 = p * Vector4(0.0, 0.0, far * -1, 1.0)
            (t2.z / t2.w).closeTo(0.9998, maxError)
        }

        it("should create a perspective projection matrix locking the horizontal view angle") {
            val expected = Matrix44(
                    2.4142135623730647, 0.0, 0.0, 241.42135623730647,
                    0.0, 4.2919352219967895, 0.0, 858.3870443993579,
                    0.0, 0.0, -1.0002000200020003, -0.020002000200020003,
                    0.0, 0.0, -1.0, 0.0)

            val p = perspectiveHorizontal(fovY, aspectRatio, near, far, 1.0, 2.0)

            p.closeTo(expected, maxError)
        }

        it("should match frustum from perspective") {

            val p = perspective(fovY, aspectRatio, near, far)

            val top = near * tan(Math.toRadians(fovY) / 2)
            val bottom = -top
            val right = top * aspectRatio
            val left = -right
            val f = frustum(left, right, bottom, top, near, far)

            p.closeTo(f, maxError)
        }

        it("should match perspective from frustum") {

            // place the Apex to the Origin
            val bottom = -4.5
            val top = 4.5
            val left = -8.0
            val right = 8.0
            val f = frustum(left, right, bottom, top, near, far)

            val fovy = 2 * Math.toDegrees(atan2(top, near))
            val aspect = right / top
            val p = perspective(fovy, aspect, near, far)

            p.closeTo(f, maxError)
        }

        it("should match a perspective projection matrix with 0.0 Apex offset to perspective") {

            val pA = perspective(fovY, aspectRatio, near, far, 0.0, 0.0)
            val p = perspective(fovY, aspectRatio, near, far)

            pA.closeTo(p, maxError)
        }

        it("should create a perspective projection matrix with Apex offset") {

            val offset = Vector2(9.0, 4.5)
            val pA = perspective(fovY, aspectRatio, near, far, offset.x, offset.y)

            val top = (near * tan(Math.toRadians(fovY) / 2))
            val bottom = -top
            val right = top * aspectRatio
            val left = -right
            val f = frustum(left + offset.x, right + offset.x, bottom + offset.y, top + offset.y, near, far)

            pA.closeTo(f, maxError)
        }

        it("should create a orthographic projection matrix with xMag & yNag scale") {

            val expected = Matrix44(
                    0.06666666666666667, 0.0, 0.0, 0.0,
                    0.0, 0.06666666666666667, 0.0, 0.0,
                    0.0, 0.0, -0.02002002002002002, -1.002002002002002,
                    0.0, 0.0, 0.0, 1.0)

            ortho(15.0, 15.0, 0.1, 100.0).closeTo(expected, maxError)
        }

        it("should create a orthographic projection matrix with bound") {

            val expected = Matrix44(
                    0.0025, 0.0, 0.0, -1.0,
                    0.0, -0.0033333333333333335, 0.0, 1.0,
                    0.0, 0.0, -0.02002002002002002, -1.002002002002002,
                    0.0, 0.0, 0.0, 1.0)

            ortho(0.0, 800.0, 600.0, 0.0, 0.1, 100.0).closeTo(expected, maxError)
        }

        it("should create a orthographic projection with scale that matches the matrix with bound") {

            val xmag = 2.0
            val ymag = 2.0

            //[[-xmag,xmag],[-ymag,ymag]]

            val o1 = ortho(-xmag, xmag, -ymag, ymag, near, far)
            val o2 = ortho(xmag, ymag, near, far)

            o1.closeTo(o2, maxError)
        }

        it("should create a lookAt projection matrix") {

            val expected = Matrix44(
                    0.0, 0.0, 0.0, -0.0,
                    0.0, -0.0, -0.0, -0.0,
                    -0.0, 1.0, -0.0, -1.0,
                    0.0, 0.0, 0.0, 1.0)

            lookAt(Vector3.UNIT_Y, Vector3.ZERO).closeTo(expected, maxError)
        }
    }

    describe("Translation") {

        it("should create a translate matrix with components") {

            val expected = Matrix44(
                    1.0, 0.0, 0.0, 1.0,
                    0.0, 1.0, 0.0, 2.0,
                    0.0, 0.0, 1.0, 3.0,
                    0.0, 0.0, 0.0, 1.0)

            Matrix44.translate(Vector3(1.0, 2.0, 3.0)).closeTo(expected, maxError)
        }

        it("should create a translate matrix with a vector") {
            Matrix44.translate(Vector3(1.0, 2.0, 3.0)).closeTo(Matrix44.translate(1.0, 2.0, 3.0), maxError)
        }
    }

    describe("Scale") {

        it("should create a scale matrix with components") {

            val expected = Matrix44(
                    1.0, 0.0, 0.0, 0.0,
                    0.0, 2.0, 0.0, 0.0,
                    0.0, 0.0, 3.0, 0.0,
                    0.0, 0.0, 0.0, 1.0)

            Matrix44.scale(Vector3(1.0, 2.0, 3.0)).closeTo(expected, maxError)
        }

        it("should create a scale matrix with a vector") {
            Matrix44.scale(Vector3(1.0, 2.0, 3.0)).closeTo(Matrix44.scale(1.0, 2.0, 3.0), maxError)
        }
    }

    describe("Rotation") {

        it("should create a rotation matrix for X") {

            val expected = Matrix44(
                    1.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, -1.0, 0.0,
                    0.0, 1.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 1.0)

            Matrix44.rotateX(90.0).closeTo(expected, maxError)
        }

        it("should create a rotation matrix for Y") {

            val expected = Matrix44(
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 1.0, 0.0, 0.0,
                    -1.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 1.0)

            Matrix44.rotateY(90.0).closeTo(expected, maxError)
        }

        it("should create a rotation matrix for Z") {

            val expected = Matrix44(
                    0.0, -1.0, 0.0, 0.0,
                    1.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0)

            Matrix44.rotateZ(90.0).closeTo(expected, maxError)
        }

        it("should create a rotation matrix for arbitrary axis") {
            Matrix44.rotate(Vector3.UNIT_X, 90.0).closeTo(Matrix44.rotateX(90.0), maxError)
            Matrix44.rotate(Vector3.UNIT_Y, 90.0).closeTo(Matrix44.rotateY(90.0), maxError)
            Matrix44.rotate(Vector3.UNIT_Z, 90.0).closeTo(Matrix44.rotateZ(90.0), maxError)

            val expected = Matrix44(
                    0.3333333, -0.2440169, 0.9106836, 0.0,
                    0.9106836, 0.3333333, -0.2440169, 0.0,
                    -0.2440169, 0.9106836, 0.3333333, 0.0,
                    0.0, 0.0, 0.0, 1.0)

            Matrix44.rotate(Vector3(1.0, 1.0, 1.0), 90.0).closeTo(expected, maxError)
        }
    }

    describe("Projection") {
        it("should project a 3D point to 2D") {

            val p = perspective(45.0, 4.0 / 3, 0.1)
            val l = lookAt(Vector3.UNIT_Y, Vector3.ZERO)
            val r = project(Vector3(10.0, 10.0, 10.0), p, l, 100, 100)

            r.closeTo(Vector3(50.0, 50.0, -0.1111111111111111), maxError)
        }

        xit("should un-project a projected point back into 3D ") {

            val c = Vector3(10.0, 10.0, 10.0)
            val p = perspective(45.0, 4.0 / 3, 0.1)
            val v = lookAt(Vector3(1.0, 2.0, 3.0), Vector3.ZERO)
            val r = project(c, p, v, 100, 100)

            unproject(r, p, v, 100, 100).closeTo(c, maxError)
        }
    }

    describe("Normal matrix") {

        it("should create a normal matrix from a given view matrix") {

            val expected = Matrix44(
                    1.0, 0.0, 0.0, 0.0,
                    0.0, 0.2873478855663454, -0.9578262852211514, 0.0,
                    0.0, 0.9578262852211514, 0.2873478855663454, 0.0,
                    0.0, 0.0, 0.0, 0.0)

            val view = lookAt(Vector3(0.0, 10.0, 3.0), Vector3.ZERO)
            normalMatrix(view).closeTo(expected, maxError)
        }
    }
})