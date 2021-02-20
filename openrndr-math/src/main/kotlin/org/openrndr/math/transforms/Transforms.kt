package org.openrndr.math.transforms

import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 *  Creates an perspective projection matrix
 *
 *  For more on projection matrices please visit http://learnwebgl.brown37.net/08_projections/projections_perspective.html
 *
 *  [fovY] Y field of view in degrees
 *  [aspectRatio] lens aspect ratio
 *  [zNear] The distance to the near clipping plane along the -Z axis.
 *  [zFar]The distance to the far clipping plane along the -Z axis.
 */
fun perspective(fovY: Double, aspectRatio: Double, zNear: Double, zFar: Double): Matrix44 {

    // https://github.com/KhronosGroup/glTF/tree/master/specification/2.0#finite-perspective-projection

    val y = Math.toRadians(fovY)

    return Matrix44(
            1.0 / (aspectRatio * tan(0.5 * y)), 0.0, 0.0, 0.0,
            0.0, 1.0 / tan(0.5 * y), 0.0, 0.0,
            0.0, 0.0, (zFar + zNear) / (zNear - zFar), 2 * zFar * zNear / (zNear - zFar),
            0.0, 0.0, -1.0, 0.0)
}

/**
 *  Creates an perspective projection matrix with infinite clipping depth
 *
 *  For more on projection matrices please visit http://learnwebgl.brown37.net/08_projections/projections_perspective.html
 *
 *  [fovY] Y field of view in degrees
 *  [aspectRatio] lens aspect ratio
 *  [zNear] The distance to the near clipping plane along the -Z axis.
 */
fun perspective(fovY: Double, aspectRatio: Double, zNear: Double): Matrix44 {

    // https://github.com/KhronosGroup/glTF/tree/master/specification/2.0#infinite-perspective-projection

    val y = Math.toRadians(fovY)

    return Matrix44(
            1.0 / (aspectRatio * tan(0.5 * y)), 0.0, 0.0, 0.0,
            0.0, 1.0 / tan(0.5 * y), 0.0, 0.0,
            0.0, 0.0, -1.0, -2.0 * zNear,
            0.0, 0.0, -1.0, 0.0)
}

/**
 *  Creates an perspective projection matrix with a shifted apex
 *
 *  For more on projection matrices please visit http://learnwebgl.brown37.net/08_projections/projections_perspective.html
 *
 *  [fovY] Y field of view in degrees
 *  [aspectRatio] lens aspect ratio
 *  [zNear] The distance to the near clipping plane along the -Z axis.
 *  [zFar]The distance to the far clipping plane along the -Z axis.
 */
fun perspective(fovY: Double, aspectRatio: Double, zNear: Double, zFar: Double, xOffset: Double, yOffset: Double): Matrix44 {

    val fW = tan(Math.toRadians(fovY) / 2) * zNear
    val fH = fW * aspectRatio

    return frustum(-fH + xOffset, fH + xOffset, -fW + yOffset, fW + yOffset, zNear, zFar)

}

/**
 *  Creates an perspective projection matrix locking the horizontal view angle
 *
 *  [fovY] Y field of view in degrees
 *  [aspectRatio] lens aspect ratio
 *  [zNear] The distance to the near clipping plane along the -Z axis.
 *  [zFar]The distance to the far clipping plane along the -Z axis.
 */
fun perspectiveHorizontal(fovY: Double, aspectRatio: Double, zNear: Double, zFar: Double, xOffset: Double, yOffset: Double): Matrix44 {

    val fW = tan(Math.toRadians(fovY) / 2) * zNear
    val fH = fW / aspectRatio

    return frustum(-fW + xOffset, fW + xOffset, -fH + yOffset, fH + yOffset, zNear, zFar)
}

/**
 * Creates frustum matrix with the given bounds
 *
 * [left] Left bound of the frustum
 * [right] Right bound of the frustum
 * [bottom] Bottom bound of the frustum
 * [top] Top bound of the frustum
 * [zNear] Near bound of the frustum
 * [zFar] Far bound of the frustum
 */
fun frustum(left: Double, right: Double, bottom: Double, top: Double, zNear: Double, zFar: Double): Matrix44 {
    val rl = 1.0 / (right - left)
    val tb = 1.0 / (top - bottom)

    return Matrix44(
            (zNear * 2) * rl, 0.0, 0.0, (right + left) * rl,
            0.0, (zNear * 2) * tb, 0.0, (top + bottom) * tb,
            0.0, 0.0, (zFar + zNear) / (zNear - zFar), (2 * zFar * zNear) / (zNear - zFar),
            0.0, 0.0, -1.0, 0.0)
}

/**
 *  Creates an orthographic projection matrix
 *
 *  [xMag] The horizontal magnification of the view
 *  [yMag] The vertical magnification of the view
 *  [zNear] The distance to the near clipping plane
 *  [zFar] The distance to the far clipping plane
 */
fun ortho(xMag: Double, yMag: Double, zNear: Double, zFar: Double): Matrix44 =

// https://github.com/KhronosGroup/glTF/tree/master/specification/2.0#orthographic-projection

        Matrix44(
                1.0 / xMag, 0.0, 0.0, 0.0,
                0.0, 1.0 / yMag, 0.0, 0.0,
                0.0, 0.0, 2.0 / (zNear - zFar), (zFar + zNear) / (zNear - zFar),
                0.0, 0.0, 0.0, 1.0)

/**
 *  Creates an orthographic projection matrix with the given bounds
 *
 * [left] left plane of the clipping volume
 * [right] right plane of the clipping volume
 * [bottom] bottom plane of the clipping volume
 * [top] top plane of the clipping volume
 * [zNear] The distance to the near clipping plane
 * [zFar] The distance to the far clipping plane
 */
fun ortho(left: Double, right: Double, bottom: Double, top: Double, zNear: Double, zFar: Double): Matrix44 {

    val tx = -(right + left) / (right - left)
    val ty = -(top + bottom) / (top - bottom)
    val tz = -(zFar + zNear) / (zFar - zNear)

    return Matrix44(
            2.0 / (right - left), 0.0, 0.0, tx,
            0.0, 2.0 / (top - bottom), 0.0, ty,
            0.0, 0.0, -2.0 / (zFar - zNear), tz,
            0.0, 0.0, 0.0, 1.0)
}

/**
 * Create a view matrix from a camera at position [eye] look at [target]
 *
 * [eye] the position of the camera
 * [target] the target the camera looks at
 * [up] direction of up, default direction is Vector3.UNIT_Y
 */
fun lookAt(eye: Vector3, target: Vector3, up: Vector3 = Vector3.UNIT_Y): Matrix44 {

    val f = target.minus(eye).normalized
    var u = up.normalized
    val s = f.cross(u).normalized

    u = s.cross(f)

    return Matrix44(
            s.x, s.y, s.z, -s.dot(eye),
            u.x, u.y, u.z, -u.dot(eye),
            -f.x, -f.y, -f.z, f.dot(eye),
            0.0, 0.0, 0.0, 1.0)
}

/**
 * Create a rotation matrix around the given axes
 *
 * [axis] the axis to rotate around
 * [angle] the angle in degrees
 */
fun Matrix44.Companion.rotate(axis: Vector3, angle: Double): Matrix44 {

    val r = Math.toRadians(angle)
    val cosa = cos(r)
    val sina = sin(r)
    val _axis = axis.normalized

    return Matrix44(

            cosa + (1 - cosa) * _axis.x * _axis.x,
            (1 - cosa) * _axis.x * _axis.y - _axis.z * sina,
            (1 - cosa) * _axis.x * _axis.z + _axis.y * sina,
            0.0,

            (1 - cosa) * _axis.x * _axis.y + _axis.z * sina,
            cosa + (1 - cosa) * _axis.y * _axis.y,
            (1 - cosa) * _axis.y * _axis.z - _axis.x * sina,
            0.0,

            (1 - cosa) * _axis.x * _axis.z - _axis.y * sina,
            (1 - cosa) * _axis.y * _axis.z + _axis.x * sina,
            cosa + (1 - cosa) * _axis.z * _axis.z,
            0.0,

            0.0,
            0.0,
            0.0,
            1.0)

}

/**
 * Create a rotation matrix around the X axes
 *
 * [angle] the angle in degrees
 */
fun Matrix44.Companion.rotateX(angle: Double): Matrix44 {
    val r = Math.toRadians(angle)

    val cr = cos(r)
    val sr = sin(r)
    return Matrix44(
            1.0, 0.0, 0.0, 0.0,
            0.0, cr, -sr, 0.0,
            0.0, sr, cr, 0.0,
            0.0, 0.0, 0.0, 1.0
    )
}

/**
 * Create a rotation matrix around the Y axes
 *
 * [angle] the angle in degrees
 */
fun Matrix44.Companion.rotateY(angle: Double): Matrix44 {
    val r = Math.toRadians(angle)

    val cr = cos(r)
    val sr = sin(r)
    return Matrix44(
            cr, 0.0, sr, 0.0,
            0.0, 1.0, 0.0, 0.0,
            -sr, 0.0, cr, 0.0,
            0.0, 0.0, 0.0, 1.0)
}

/**
 * Create a rotation matrix around the Z axes
 *
 * [angle] the angle in degrees
 */
fun Matrix44.Companion.rotateZ(angle: Double): Matrix44 {
    val r = Math.toRadians(angle)
    val cr = cos(r)
    val sr = sin(r)
    return Matrix44(
            cr, -sr, 0.0, 0.0,
            sr, cr, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0)
}

/**
 * Create a translate matrix with the given vector
 *
 * [scale] scale vector
 */
fun Matrix44.Companion.translate(translation: Vector3): Matrix44 {
    return translate(translation.x, translation.y, translation.z)
}

/**
 * Create a scaling matrix with the given components
 *
 * [x] translate direction
 * [y] translate direction
 * [z] translate direction
 */
fun Matrix44.Companion.translate(x: Double, y: Double, z: Double): Matrix44 {
    return Matrix44.fromColumnVectors(
            Vector4.UNIT_X,
            Vector4.UNIT_Y,
            Vector4.UNIT_Z,
            Vector4(x, y, z, 1.0))
}

/**
 * Create a scaling matrix with the given vector
 *
 * [scale] scale vector
 */
fun Matrix44.Companion.scale(scaleFactor: Vector3): Matrix44 {
    return scale(scaleFactor.x, scaleFactor.y, scaleFactor.z)
}

/**
 * Create a scaling matrix with the given components
 *
 * [x] direction scale factor
 * [y] direction scale factor
 * [z] direction scale factor
 */
fun Matrix44.Companion.scale(x: Double, y: Double, z: Double): Matrix44 {
    return Matrix44(
            x, 0.0, 0.0, 0.0,
            0.0, y, 0.0, 0.0,
            0.0, 0.0, z, 0.0,
            0.0, 0.0, 0.0, 1.0)
}

/**
 * Project a 3D point on a 2D surface
 *
 * [point] the point to project
 * [projection] the projection matrix
 * [view] the view matrix
 * [width] the width of the projection surface
 * [height] the height of the projection surface
 */
fun project(point: Vector3, projection: Matrix44, view: Matrix44, width: Int, height: Int): Vector3 {

    val homo = Vector4(point.x, point.y, point.z, 1.0)
    val projectedHomo = (projection * view) * homo

    // Homogeneous division
    val rhw = 1 / projectedHomo.w

    return Vector3(
            (projectedHomo.x * rhw + 1) * width / 2,
            (1 - projectedHomo.y * rhw) * height / 2,
            rhw)
}


fun unproject(point: Vector3, projection: Matrix44, view: Matrix44, width: Int, height: Int): Vector3 {
    val ipm = (projection * view).inversed
    val v = Vector3(2 * point.x / width - 1, 2 * point.y / height - 1, 2 * point.z - 1)
    return (ipm * v.xyz1).xyz
}

/**
 * Construct a normal matrix from the give view matrix
 */
fun normalMatrix(view: Matrix44): Matrix44 {

    val subView = Matrix44(
            view.c0r0, view.c1r0, view.c2r0, 0.0,
            view.c0r1, view.c1r1, view.c2r1, 0.0,
            view.c0r2, view.c1r2, view.c2r2, 0.0,
            0.0, 0.0, 0.0, 1.0)

    return subView.inversed.transposed.copy(c3r3 = 0.0)
}

