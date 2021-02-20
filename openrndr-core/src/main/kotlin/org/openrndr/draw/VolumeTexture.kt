package org.openrndr.draw

import org.openrndr.color.ColorRGBa
import org.openrndr.internal.Driver
import java.nio.ByteBuffer

interface VolumeTexture {

    companion object {
        fun create(
                width: Int,
                height: Int,
                depth: Int,
                format: ColorFormat = ColorFormat.RGBa,
                type: ColorType = ColorType.UINT8,
                levels: Int = 1,
                session: Session? = Session.active
        ): VolumeTexture {
            return Driver.instance.createVolumeTexture(width, height, depth,
                format, type, levels, session)
        }
    }

    val session: Session?

    val width: Int
    val height: Int
    val depth: Int
    val format: ColorFormat
    val type: ColorType
    val levels: Int

    fun copyTo(target: ColorBuffer, layer: Int, fromLevel: Int = 0, toLevel: Int = 0)

    fun filter(min: MinifyingFilter, mag: MagnifyingFilter)
    fun bind(textureUnit: Int = 0)
    fun generateMipmaps()
    fun destroy()

    fun read(layer: Int, target: ByteBuffer, targetFormat: ColorFormat = format, targetType: ColorType = type, level: Int = 0)
    fun write(layer: Int, source: ByteBuffer, sourceFormat: ColorFormat = format, sourceType: ColorType = type, level: Int = 0)
    fun fill(color: ColorRGBa)
}

fun volumeTexture(
        width: Int,
        height: Int,
        depth: Int,
        format: ColorFormat = ColorFormat.RGBa,
        type: ColorType = ColorType.UINT8,
        levels: Int = 1,
        session: Session? = Session.active
): VolumeTexture {
    return VolumeTexture.create(width, height, depth, format, type, levels, session)
}