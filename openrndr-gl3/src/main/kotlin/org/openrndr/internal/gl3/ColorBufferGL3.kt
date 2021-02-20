package org.openrndr.internal.gl3

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.opengl.ARBTextureCompressionBPTC.*
import org.lwjgl.opengl.EXTTextureCompressionS3TC.*
import org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT
import org.lwjgl.opengl.EXTTextureSRGB.*
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.opengl.GL42C.glTexStorage2D
import org.lwjgl.opengl.GL43C
import org.lwjgl.opengl.GL43C.glCopyImageSubData
import org.lwjgl.opengl.GL43C.glTexStorage2DMultisample
import org.lwjgl.opengl.GL44C
import org.lwjgl.stb.STBIWriteCallback
import org.lwjgl.stb.STBImageWrite
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyexr.EXRChannelInfo
import org.lwjgl.util.tinyexr.EXRHeader
import org.lwjgl.util.tinyexr.EXRImage
import org.lwjgl.util.tinyexr.TinyEXR.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.draw.BufferMultisample.Disabled
import org.openrndr.draw.BufferMultisample.SampleCount
import org.openrndr.internal.Driver
import org.openrndr.shape.IntRectangle
import java.io.File
import java.io.InputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

internal data class ConversionEntry(val format: ColorFormat, val type: ColorType, val glFormat: Int, val glType: Int)

private val logger = KotlinLogging.logger {}

enum class TextureStorageModeGL {
    IMAGE,
    STORAGE
}

internal fun internalFormat(format: ColorFormat, type: ColorType): Pair<Int, Int> {
    val entries = arrayOf(
            ConversionEntry(ColorFormat.R, ColorType.UINT8, GL_R8, GL_RED),
            ConversionEntry(ColorFormat.R, ColorType.UINT8_INT, GL_R8UI, GL_RED_INTEGER),
            ConversionEntry(ColorFormat.R, ColorType.SINT8_INT, GL_R8I, GL_RED_INTEGER),
            ConversionEntry(ColorFormat.R, ColorType.UINT16, GL_R16, GL_RED),
            ConversionEntry(ColorFormat.R, ColorType.UINT16_INT, GL_R16UI, GL_RED_INTEGER),
            ConversionEntry(ColorFormat.R, ColorType.SINT16_INT, GL_RG16I, GL_RED_INTEGER),
            ConversionEntry(ColorFormat.R, ColorType.UINT32_INT, GL_R32UI, GL_RED_INTEGER),
            ConversionEntry(ColorFormat.R, ColorType.SINT32_INT, GL_RG32I, GL_RED_INTEGER),

            ConversionEntry(ColorFormat.R, ColorType.FLOAT16, GL_R16F, GL_RED),
            ConversionEntry(ColorFormat.R, ColorType.FLOAT32, GL_R32F, GL_RED),

            ConversionEntry(ColorFormat.RG, ColorType.UINT8, GL_RG8, GL_RG),
            ConversionEntry(ColorFormat.RG, ColorType.UINT8_INT, GL_RG8UI, GL_RG_INTEGER),
            ConversionEntry(ColorFormat.RG, ColorType.SINT16_INT, GL_RG16I, GL_RG_INTEGER),
            ConversionEntry(ColorFormat.RG, ColorType.SINT32_INT, GL_RG32I, GL_RG_INTEGER),
            ConversionEntry(ColorFormat.RG, ColorType.UINT16, GL_RG16, GL_RG),
            ConversionEntry(ColorFormat.RG, ColorType.UINT16_INT, GL_RG16UI, GL_RG_INTEGER),
            ConversionEntry(ColorFormat.RG, ColorType.UINT32_INT, GL_RG32UI, GL_RG_INTEGER),

            ConversionEntry(ColorFormat.RG, ColorType.FLOAT16, GL_RG16F, GL_RG),
            ConversionEntry(ColorFormat.RG, ColorType.FLOAT32, GL_RG32F, GL_RG),

            ConversionEntry(ColorFormat.RGB, ColorType.UINT8, GL_RGB8, GL_RGB),
            ConversionEntry(ColorFormat.RGB, ColorType.UINT8_INT, GL_RGB8UI, GL_RGB_INTEGER),
            ConversionEntry(ColorFormat.RGB, ColorType.UINT16, GL_RGB16, GL_RGB),
            ConversionEntry(ColorFormat.RGB, ColorType.UINT16_INT, GL_RGB16UI, GL_RGB_INTEGER),
            ConversionEntry(ColorFormat.RGB, ColorType.UINT32_INT, GL_RGB32UI, GL_RGB_INTEGER),
            ConversionEntry(ColorFormat.RGB, ColorType.SINT16_INT, GL_RGB16I, GL_RGB_INTEGER),
            ConversionEntry(ColorFormat.RGB, ColorType.SINT32_INT, GL_RGB32I, GL_RGB_INTEGER),
            ConversionEntry(ColorFormat.RGB, ColorType.FLOAT16, GL_RGB16F, GL_RGB),
            ConversionEntry(ColorFormat.RGB, ColorType.FLOAT32, GL_RGB32F, GL_RGB),
            ConversionEntry(ColorFormat.BGR, ColorType.UINT8, GL_RGB8, GL_BGR),

            ConversionEntry(ColorFormat.RGBa, ColorType.UINT8, GL_RGBA8, GL_RGBA),
            ConversionEntry(ColorFormat.RGBa, ColorType.UINT8_INT, GL_RGBA8UI, GL_RGBA_INTEGER),
            ConversionEntry(ColorFormat.RGBa, ColorType.UINT16, GL_RGBA16, GL_RGBA),
            ConversionEntry(ColorFormat.RGBa, ColorType.UINT16_INT, GL_RGBA16UI, GL_RGBA_INTEGER),
            ConversionEntry(ColorFormat.RGBa, ColorType.SINT16_INT, GL_RGBA16I, GL_RGBA_INTEGER),
            ConversionEntry(ColorFormat.RGBa, ColorType.UINT32_INT, GL_RGBA32UI, GL_RGBA_INTEGER),
            ConversionEntry(ColorFormat.RGBa, ColorType.SINT32_INT, GL_RGBA32I, GL_RGBA_INTEGER),
            ConversionEntry(ColorFormat.RGBa, ColorType.FLOAT16, GL_RGBA16F, GL_RGBA),
            ConversionEntry(ColorFormat.RGBa, ColorType.FLOAT32, GL_RGBA32F, GL_RGBA),

            ConversionEntry(ColorFormat.sRGB, ColorType.UINT8, GL_SRGB8, GL_RGB),
            ConversionEntry(ColorFormat.sRGBa, ColorType.UINT8, GL_SRGB8_ALPHA8, GL_RGBA),
            ConversionEntry(ColorFormat.RGBa, ColorType.DXT1, GL_COMPRESSED_RGBA_S3TC_DXT1_EXT, GL_RGBA),
            ConversionEntry(ColorFormat.RGBa, ColorType.DXT3, GL_COMPRESSED_RGBA_S3TC_DXT3_EXT, GL_RGBA),
            ConversionEntry(ColorFormat.RGBa, ColorType.DXT5, GL_COMPRESSED_RGBA_S3TC_DXT5_EXT, GL_RGBA),
            ConversionEntry(ColorFormat.RGB, ColorType.DXT1, GL_COMPRESSED_RGB_S3TC_DXT1_EXT, GL_RGBA),
            ConversionEntry(ColorFormat.sRGBa, ColorType.DXT1, GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT1_EXT, GL_RGBA),
            ConversionEntry(ColorFormat.sRGBa, ColorType.DXT3, GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT3_EXT, GL_RGBA),
            ConversionEntry(ColorFormat.sRGBa, ColorType.DXT5, GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT, GL_RGBA),
            ConversionEntry(ColorFormat.sRGB, ColorType.DXT1, GL_COMPRESSED_SRGB_S3TC_DXT1_EXT, GL_RGBA),
            ConversionEntry(ColorFormat.RGBa, ColorType.BPTC_UNORM, GL_COMPRESSED_RGBA_BPTC_UNORM_ARB, GL_RGBA),
            ConversionEntry(ColorFormat.sRGBa, ColorType.BPTC_UNORM, GL_COMPRESSED_SRGB_ALPHA_BPTC_UNORM_ARB, GL_RGBA),
            ConversionEntry(ColorFormat.RGB, ColorType.BPTC_FLOAT, GL_COMPRESSED_RGB_BPTC_SIGNED_FLOAT_ARB, GL_RGBA),
            ConversionEntry(ColorFormat.RGB, ColorType.BPTC_UFLOAT, GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT_ARB, GL_RGBA)
    )
    for (entry in entries) {
        if (entry.format === format && entry.type === type) {
            return Pair(entry.glFormat, entry.glType)
        }
    }
    throw Exception("no conversion entry for $format/$type")
}

private val IntProgression.size: Int
    get() {
        return 1 + (this.last - this.first) / this.step
    }

@Suppress("MemberVisibilityCanBePrivate")
class ColorBufferGL3(val target: Int,
                     val texture: Int,
                     val storageMode: TextureStorageModeGL,
                     override val width: Int,
                     override val height: Int,
                     override val contentScale: Double,
                     override val format: ColorFormat,
                     override val type: ColorType,
                     override val levels: Int,
                     override val multisample: BufferMultisample,
                     override val session: Session?) : ColorBuffer {


    private var destroyed = false
    override var flipV: Boolean = false

    internal fun glFormat(): Int {
        return internalFormat(format, type).first
    }

    companion object {
        fun fromColorBufferData(data: ColorBufferDataGL3, session: Session?): ColorBuffer {
            val cb = create(data.width, data.height, 1.0, data.format, data.type, Disabled, 1, session)
            return cb.apply {
                this.flipV = data.flipV
                val d = data.data
                if (d != null) {
                    cb.write(d)
                    cb.generateMipmaps()
                } else {
                    throw RuntimeException("data is null")
                }
                data.destroy()
                glFlush()
                glFinish()
            }
        }

        fun fromUrl(url: String, formatHint: ImageFileFormat?, session: Session?): ColorBuffer {
            val data = ColorBufferDataGL3.fromUrl(url, formatHint)
            return fromColorBufferData(data, session)
        }

        fun fromFile(filename: String, formatHint: ImageFileFormat?, session: Session?): ColorBuffer {
            val data = ColorBufferDataGL3.fromFile(filename)
            return fromColorBufferData(data, session)
        }

        fun fromStream(stream: InputStream, name: String?, formatHint: ImageFileFormat?, session: Session?): ColorBuffer {
            val data = ColorBufferDataGL3.fromStream(stream, name)
            return fromColorBufferData(data, session)
        }

        fun fromArray(array: ByteArray, offset: Int = 0, length: Int = array.size, name: String?, formatHint: ImageFileFormat?, session: Session?): ColorBuffer {
            val data = ColorBufferDataGL3.fromArray(array, offset, length, name, formatHint)
            return fromColorBufferData(data, session)
        }

        fun fromBuffer(buffer: ByteBuffer, name: String?, formatHint: ImageFileFormat?, session: Session?): ColorBuffer {
            val data = ColorBufferDataGL3.fromByteBuffer(buffer, name, formatHint)
            return fromColorBufferData(data, session)
        }

        fun create(width: Int,
                   height: Int,
                   contentScale: Double = 1.0,
                   format: ColorFormat = ColorFormat.RGBa,
                   type: ColorType = ColorType.FLOAT32,
                   multisample: BufferMultisample,
                   levels: Int,
                   session: Session?): ColorBufferGL3 {
            val (internalFormat, internalType) = internalFormat(format, type)
            if (width <= 0 || height <= 0) {
                throw Exception("cannot create ColorBuffer with dimensions: ${width}x$height")
            }
            checkGLErrors()

            val storageMode = when {
                (Driver.instance as DriverGL3).version >= DriverVersionGL.VERSION_4_3 -> TextureStorageModeGL.STORAGE
                else -> TextureStorageModeGL.IMAGE
            }

            val texture = glGenTextures()
            checkGLErrors()

            glActiveTexture(GL_TEXTURE0)

            when (multisample) {
                Disabled -> glBindTexture(GL_TEXTURE_2D, texture)
                is SampleCount -> glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, texture)
            }

            checkGLErrors()

            val effectiveWidth = (width * contentScale).toInt()
            val effectiveHeight = (height * contentScale).toInt()

            val nullBB: ByteBuffer? = null

            if (levels > 1) {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, levels - 1)
            }

            when (storageMode) {
                TextureStorageModeGL.IMAGE -> {
                    for (level in 0 until levels) {
                        val div = 1 shl level
                        when (multisample) {
                            Disabled ->
                                glTexImage2D(GL_TEXTURE_2D, level, internalFormat, effectiveWidth / div, effectiveHeight / div, 0, internalType, GL_UNSIGNED_BYTE, nullBB)
                            is SampleCount -> glTexImage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, multisample.sampleCount.coerceAtMost(glGetInteger(GL_MAX_COLOR_TEXTURE_SAMPLES)), internalFormat, effectiveWidth / div, effectiveHeight / div, true)
                        }
                    }
                }
                TextureStorageModeGL.STORAGE -> {
                    when (multisample) {
                        Disabled ->
                            glTexStorage2D(GL_TEXTURE_2D, levels, internalFormat, effectiveWidth, effectiveHeight)
                        is SampleCount -> glTexStorage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, multisample.sampleCount.coerceAtMost(glGetInteger(GL_MAX_COLOR_TEXTURE_SAMPLES)), internalFormat, effectiveWidth, effectiveHeight, true)
                    }
                }
            }
            checkGLErrors {
                when (it) {
                    GL_INVALID_OPERATION -> """format is GL_DEPTH_COMPONENT (${format.glFormat() == GL_DEPTH_COMPONENT}) and internalFormat is not GL_DEPTH_COMPONENT, GL_DEPTH_COMPONENT16, GL_DEPTH_COMPONENT24, or GL_DEPTH_COMPONENT32F"""
                    GL_INVALID_FRAMEBUFFER_OPERATION -> "buh?"
                    else -> null
                }
            }

            val target = when (multisample) {
                Disabled -> GL_TEXTURE_2D
                is SampleCount -> GL_TEXTURE_2D_MULTISAMPLE
            }

            if (multisample == Disabled) {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
                checkGLErrors()
            }

            return ColorBufferGL3(target, texture, storageMode, width, height, contentScale, format, type, levels, multisample, session)
        }
    }

    fun bound(f: ColorBufferGL3.() -> Unit) {
        checkDestroyed()
        glActiveTexture(GL_TEXTURE0)
        val current = when (multisample) {
            Disabled -> glGetInteger(GL_TEXTURE_BINDING_2D)
            is SampleCount -> glGetInteger(GL_TEXTURE_BINDING_2D_MULTISAMPLE)
        }
        glBindTexture(target, texture)
        this.f()
        glBindTexture(target, current)
    }

    fun destroyShadow() {
        realShadow = null
    }

    override fun generateMipmaps() {
        checkDestroyed()
        if (multisample == Disabled) {
            bound {
                glGenerateMipmap(target)
            }
        } else {
            throw IllegalArgumentException("generating Mipmaps for multisample targets is not possible")
        }
    }

    override fun copyTo(
            target: ColorBuffer,
            fromLevel: Int,
            toLevel: Int,
            sourceRectangle: IntRectangle,
            targetRectangle: IntRectangle,
            filter: MagnifyingFilter
    ) {
        checkDestroyed()

        val fromDiv = 1 shl fromLevel
        val toDiv = 1 shl toLevel
        val refRectangle = IntRectangle(0, 0, effectiveWidth / fromDiv, effectiveHeight / fromDiv)

        val useTexSubImage = target.type.compressed || (refRectangle == sourceRectangle && refRectangle == targetRectangle && multisample == target.multisample)

        if (!useTexSubImage) {
            val readTarget = renderTarget(
                    width / fromDiv,
                    height / fromDiv,
                    contentScale,
                    multisample = multisample
            ) {
                colorBuffer(this@ColorBufferGL3, fromLevel)
            } as RenderTargetGL3

            val writeTarget = renderTarget(
                    target.width / toDiv,
                    target.height / toDiv,
                    target.contentScale,
                    multisample = target.multisample
            ) {
                colorBuffer(target, toLevel)
            } as RenderTargetGL3

            writeTarget.bind()
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readTarget.framebuffer)

            val ssx = sourceRectangle.x
            val ssy = sourceRectangle.y
            val sex = sourceRectangle.width + ssx
            val sey = sourceRectangle.height + ssy

            val tsx = targetRectangle.x
            val tsy = targetRectangle.y
            val tex = targetRectangle.width + tsx
            val tey = targetRectangle.height + tsy

            fun sflip(y: Int): Int {
                return this.effectiveHeight / fromDiv - y
            }

            fun tflip(y: Int): Int {
                return target.effectiveHeight / toDiv - y
            }

            glBlitFramebuffer(ssx, sflip(ssy), sex, sflip(sey), tsx, tflip(tsy), tex, tflip(tey), GL_COLOR_BUFFER_BIT, filter.toGLFilter())
            writeTarget.unbind()

            writeTarget.detachColorAttachments()
            writeTarget.destroy()

            readTarget.detachColorAttachments()
            readTarget.destroy()
        } else {
            require(sourceRectangle == refRectangle && targetRectangle == refRectangle) {
                "cropped or scaled copyTo is not allowed with the selected color buffers: $this -> $target"
            }

            val useFrameBufferCopy = true // Driver.glVersion < DriverVersionGL.VERSION_4_3 || (type != target.type || format != target.format)

            if (useFrameBufferCopy) {
                checkDestroyed()
                val readTarget = renderTarget(width / fromDiv, height / fromDiv, contentScale) {
                    colorBuffer(this@ColorBufferGL3, fromLevel)
                } as RenderTargetGL3

                target as ColorBufferGL3
                readTarget.bind()
                glReadBuffer(GL_COLOR_ATTACHMENT0)
                debugGLErrors()
                target.bound {
                    glCopyTexSubImage2D(target.target, toLevel, 0, 0, 0, 0, target.effectiveWidth / toDiv, target.effectiveHeight / toDiv)
                    debugGLErrors {
                        when (it) {
                            GL_INVALID_VALUE -> "level ($toLevel) less than 0, effective target is GL_TEXTURE_RECTANGLE (${target.target == GL_TEXTURE_RECTANGLE} and level is not 0"
                            else -> null
                        }
                    }
                }
                readTarget.unbind()

                readTarget.detachColorAttachments()
                readTarget.destroy()
            } else {
                target as ColorBufferGL3
                GL43C.glCopyImageSubData(
                        texture,
                        this.target,
                        fromLevel,
                        0,
                        0,
                        0,
                        target.texture,
                        target.target,
                        toLevel,
                        0,
                        0,
                        0,
                        effectiveWidth,
                        effectiveHeight,
                        1
                )
                debugGLErrors()
            }
        }
    }

    override fun copyTo(target: ArrayTexture, layer: Int, fromLevel: Int, toLevel: Int) {
        debugGLErrors {
            "leaking error"
        }

        if (!type.compressed) {
            checkDestroyed()
            val fromDiv = 1 shl fromLevel
            val toDiv = 1 shl toLevel
            val readTarget = renderTarget(width / fromDiv, height / fromDiv, contentScale) {
                colorBuffer(this@ColorBufferGL3, fromLevel)
            } as RenderTargetGL3
            debugGLErrors()

            target as ArrayTextureGL3
            readTarget.bind()
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            debugGLErrors()

            target.bound {
                glCopyTexSubImage3D(target.target, toLevel, 0, 0, layer, 0, 0, target.width / toDiv, target.height / toDiv)
                debugGLErrors {
                    when (it) {
                        GL_INVALID_FRAMEBUFFER_OPERATION -> "the object bound to GL_READ_FRAMEBUFFER_BINDING is not framebuffer complete."
                        else -> null
                    }
                }
            }
            readTarget.unbind()

            readTarget.detachColorAttachments()
            readTarget.destroy()
        } else {
            if (type == target.type && format == target.format) {
                if (Driver.glVersion >= DriverVersionGL.VERSION_4_3) {
                    val tgl = target as ArrayTextureGL3
                    val fromDiv = 1 shl fromLevel
                    glCopyImageSubData(texture, this.target, fromLevel, 0, 0, 0, tgl.texture, tgl.target, toLevel, 0, 0, layer, effectiveWidth / fromDiv, effectiveHeight / fromDiv, 1)
                } else {
                    val copyBuffer = MemoryUtil.memAlloc(bufferSize(fromLevel).toInt())
                    try {
                        read(copyBuffer, level = fromLevel)
                        copyBuffer.rewind()
                        target.write(layer = layer, copyBuffer, level = toLevel)
                        copyBuffer.rewind()
                    } finally {
                        MemoryUtil.memFree(copyBuffer)
                    }
                }
            } else {
                error("can't copy from compressed source ${format}/${type} to compressed target ${target.format}/${target.type}")
            }
        }
    }

    override fun fill(color: ColorRGBa) {
        checkDestroyed()

        val floatColorData = floatArrayOf(color.r.toFloat(), color.g.toFloat(), color.b.toFloat(), color.a.toFloat())
        when {
            (Driver.glVersion < DriverVersionGL.VERSION_4_4) -> {
                val writeTarget = renderTarget(width, height, contentScale) {
                    colorBuffer(this@ColorBufferGL3)
                } as RenderTargetGL3

                writeTarget.bind()
                glClearBufferfv(GL_COLOR, 0, floatColorData)
                debugGLErrors()
                writeTarget.unbind()

                writeTarget.detachColorAttachments()
                writeTarget.destroy()
            }
            else -> {
                GL44C.glClearTexImage(texture, 0, ColorFormat.RGBa.glFormat(), ColorType.FLOAT32.glType(), floatColorData)
                debugGLErrors()
            }
        }
    }

    override var wrapU: WrapMode
        get() = TODO("not implemented")
        set(value) {
            bound {
                glTexParameteri(target, GL_TEXTURE_WRAP_S, value.glWrap())
            }
        }

    override var wrapV: WrapMode
        get() = TODO("not implemented")
        set(value) {
            bound {
                glTexParameteri(target, GL_TEXTURE_WRAP_T, value.glWrap())
            }
        }

    override var filterMin: MinifyingFilter
        get() = TODO("not implemented")
        set(value) {
            bound {
                glTexParameteri(target, GL_TEXTURE_MIN_FILTER, value.toGLFilter())
            }
        }

    override var filterMag: MagnifyingFilter
        get() = TODO("not implemented")
        set(value) {
            bound {
                glTexParameteri(target, GL_TEXTURE_MAG_FILTER, value.toGLFilter())
            }
        }

    override var anisotropy: Double
        get() = TODO("not implemented")
        set(value) {
            bound {
                glTexParameterf(target, GL_TEXTURE_MAX_ANISOTROPY_EXT, value.toFloat())
            }
        }

    override val shadow: ColorBufferShadow
        get() {
            if (multisample == Disabled) {
                if (realShadow == null) {
                    realShadow = ColorBufferShadowGL3(this)
                }
                return realShadow!!
            } else {
                throw IllegalArgumentException("multisample targets cannot be shadowed")
            }
        }

    var realShadow: ColorBufferShadow? = null

    override fun write(sourceBuffer: ByteBuffer, sourceFormat: ColorFormat, sourceType: ColorType, level: Int) {

        require(sourceBuffer.remaining() > 0) {
            "sourceBuffer $sourceBuffer has no remaining data"
        }

        val div = 1 shl level
        checkDestroyed()
        if (!sourceBuffer.isDirect) {
            throw IllegalArgumentException("buffer is not a direct buffer.")
        }
        if (!sourceType.compressed) {
            val bytesNeeded = sourceFormat.componentCount * sourceType.componentSize * (effectiveWidth / div) * (effectiveHeight / div)
            require(bytesNeeded <= sourceBuffer.remaining()) {
                "write requires $bytesNeeded bytes, buffer only has ${sourceBuffer.remaining()} bytes left, buffer capacity is ${sourceBuffer.capacity()}"
            }
        }

        if (multisample == Disabled) {
            bound {
                debugGLErrors()
                logger.trace {
                    "Writing to color buffer in: $format ${format.glFormat()}, $type ${type.glType()}"
                }
                (sourceBuffer as Buffer).rewind()
                sourceBuffer.order(ByteOrder.nativeOrder())
                val currentPack = intArrayOf(0)
                glGetIntegerv(GL_UNPACK_ALIGNMENT, currentPack)
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1)

                if (sourceType.compressed) {
                    glCompressedTexSubImage2D(target, level, 0, 0, width / div, height / div, compressedType(sourceFormat, sourceType), sourceBuffer)
                    debugGLErrors {
                        when (it) {
                            GL_INVALID_VALUE -> "data size mismatch? ${sourceBuffer.remaining()}"
                            else -> null
                        }
                    }
                } else {
                    glTexSubImage2D(target, level, 0, 0, width / div, height / div, sourceFormat.glFormat(), sourceType.glType(), sourceBuffer)
                    debugGLErrors()
                }
                glPixelStorei(GL_UNPACK_ALIGNMENT, currentPack[0])
                debugGLErrors()
                (sourceBuffer as Buffer).rewind()
            }
        } else {
            throw IllegalArgumentException("multisample targets cannot be written to")
        }
    }


    override fun read(targetBuffer: ByteBuffer, targetFormat: ColorFormat, targetType: ColorType, level: Int) {
        checkDestroyed()
        if (!targetBuffer.isDirect) {
            throw IllegalArgumentException("buffer is not a direct buffer.")
        }
        if (multisample == Disabled) {
            bound {
                logger.trace {
                    "Reading from color buffer in: $format ${format.glFormat()}, $type ${type.glType()} "
                }
                debugGLErrors()
                glPixelStorei(GL_PACK_ALIGNMENT, 1)
                debugGLErrors()
                val packAlignment = glGetInteger(GL_PACK_ALIGNMENT)
                targetBuffer.order(ByteOrder.nativeOrder())
                (targetBuffer as Buffer).rewind()
                if (!targetType.compressed) {
                    glGetTexImage(target, level, targetFormat.glFormat(), targetType.glType(), targetBuffer)
                } else {
                    require(targetType == type && targetFormat == format) {
                        "source format/type (${format}/${type}) and target format/type ${targetFormat}/${targetType}) must match"
                    }
                    glGetCompressedTexImage(target, level, targetBuffer)
                }
                debugGLErrors()
                (targetBuffer as Buffer).rewind()
                glPixelStorei(GL_PACK_ALIGNMENT, packAlignment)
                debugGLErrors()
            }
        } else {
            throw IllegalArgumentException("multisample targets cannot be read from")
        }
    }

    override fun saveToFile(file: File, imageFileFormat: ImageFileFormat, async: Boolean) {
        checkDestroyed()
        if (multisample == Disabled) {
            if (type == ColorType.UINT8) {
                var pixels = BufferUtils.createByteBuffer(effectiveWidth * effectiveHeight * format.componentCount)
                (pixels as Buffer).rewind()
                read(pixels)
                (pixels as Buffer).rewind()

                runBlocking {
                    val job = GlobalScope.launch {
                        if (!flipV) {
                            val flippedPixels = BufferUtils.createByteBuffer(effectiveWidth * effectiveHeight * format.componentCount)
                            (flippedPixels as Buffer).rewind()
                            val stride = effectiveWidth * format.componentCount
                            val row = ByteArray(stride)

                            for (y in 0 until effectiveHeight) {
                                (pixels as Buffer).position((effectiveHeight - y - 1) * stride)
                                pixels.get(row)
                                flippedPixels.put(row)
                            }

                            (flippedPixels as Buffer).rewind()
                            pixels = flippedPixels
                        }

                        when (imageFileFormat) {
                            ImageFileFormat.JPG -> STBImageWrite.stbi_write_jpg(
                                    file.absolutePath,
                                    effectiveWidth, effectiveHeight,
                                    format.componentCount, pixels, 90)
                            ImageFileFormat.PNG -> STBImageWrite.stbi_write_png(
                                    file.absolutePath,
                                    effectiveWidth, effectiveHeight,
                                    format.componentCount, pixels, effectiveWidth * format.componentCount)
                            else -> error("format not supported")
                        }
                    }
                    if (!async) {
                        job.join()
                    }
                }
            } else if (type == ColorType.FLOAT16 || type == ColorType.FLOAT32) {
                require(imageFileFormat == ImageFileFormat.EXR) { "can only save floating point color buffers to EXR" }
                require(format == ColorFormat.RGB || format == ColorFormat.RGBa) { "can only save RGB and RGBA formats" }

                val exrType = if (type == ColorType.FLOAT16) TINYEXR_PIXELTYPE_HALF else TINYEXR_PIXELTYPE_FLOAT

                val exrImage = EXRImage.create()
                InitEXRImage(exrImage)

                val exrHeader = EXRHeader.create()
                InitEXRHeader(exrHeader)

                exrHeader.num_channels(3)

                val exrChannels = EXRChannelInfo.calloc(3)
                exrChannels[0].name(ByteBuffer.allocateDirect(2).apply { put('B'.toByte()); put(0.toByte()); (this as Buffer).rewind() })
                exrChannels[1].name(ByteBuffer.allocateDirect(2).apply { put('G'.toByte()); put(0.toByte()); (this as Buffer).rewind() })
                exrChannels[2].name(ByteBuffer.allocateDirect(2).apply { put('R'.toByte()); put(0.toByte()); (this as Buffer).rewind() })

                exrHeader.channels(exrChannels)

                val data = ByteBuffer.allocateDirect(type.componentSize * 3 * effectiveWidth * effectiveHeight).order(ByteOrder.nativeOrder())
                (data as Buffer).rewind()
                read(data, targetFormat = ColorFormat.RGB)
                (data as Buffer).rewind()
                val bBuffer = ByteBuffer.allocateDirect(effectiveWidth * effectiveHeight * 4).order(ByteOrder.nativeOrder())
                val gBuffer = ByteBuffer.allocateDirect(effectiveWidth * effectiveHeight * 4).order(ByteOrder.nativeOrder())
                val rBuffer = ByteBuffer.allocateDirect(effectiveWidth * effectiveHeight * 4).order(ByteOrder.nativeOrder())

                // -- de-interleave and flip data
                for (y in 0 until height) {
                    val row = if (!flipV) effectiveHeight - 1 - y else y
                    val offset = row * effectiveWidth * type.componentSize * 3

                    (data as Buffer).position(offset)

                    for (x in 0 until effectiveWidth) {
                        for (i in 0 until type.componentSize) {
                            val b = data.get()
                            bBuffer.put(b)
                        }
                        for (i in 0 until type.componentSize) {
                            val g = data.get()
                            gBuffer.put(g)
                        }
                        for (i in 0 until type.componentSize) {
                            val r = data.get()
                            rBuffer.put(r)
                        }
                    }
                }

                (bBuffer as Buffer).rewind()
                (gBuffer as Buffer).rewind()
                (rBuffer as Buffer).rewind()


                val pixelTypes = BufferUtils.createIntBuffer(4 * 3).apply {
                    put(exrType); put(exrType); put(exrType); (this as Buffer).rewind()
                }
                exrHeader.pixel_types(pixelTypes)
                (pixelTypes as Buffer).rewind()
                exrHeader.requested_pixel_types(pixelTypes)

                exrImage.width(width)
                exrImage.height(height)
                exrImage.num_channels(3)

                val images = PointerBuffer.allocateDirect(3)
                images.put(0, bBuffer)
                images.put(1, gBuffer)
                images.put(2, rBuffer)
                images.rewind()

                exrImage.images(images)

                val errors = PointerBuffer.allocateDirect(1)
                val result = SaveEXRImageToFile(exrImage, exrHeader, file.path, errors)

                require(result == 0) {
                    "failed to save to ${file.path}, [result=$result]"
                }
                //FreeEXRHeader(exrHeader)
                FreeEXRImage(exrImage)
            }

        } else {
            throw IllegalArgumentException("multisample targets cannot be saved to file")
        }
    }

    override fun toDataUrl(imageFileFormat: ImageFileFormat): String {
        checkDestroyed()

        require(multisample == Disabled)
        require(type == ColorType.UINT8)

        val saveBuffer = ByteBuffer.allocate(1_024 * 1_024 * 2)
        val writeFunc = object : STBIWriteCallback() {
            override fun invoke(context: Long, data: Long, size: Int) {
                val sourceBuffer = MemoryUtil.memByteBuffer(data, size)
                saveBuffer?.put(sourceBuffer)
            }
        }

        var pixels = BufferUtils.createByteBuffer(effectiveWidth * effectiveHeight * format.componentCount)
        (pixels as Buffer).rewind()
        read(pixels)
        (pixels as Buffer).rewind()
        if (!flipV) {
            val flippedPixels = BufferUtils.createByteBuffer(effectiveWidth * effectiveHeight * format.componentCount)
            (flippedPixels as Buffer).rewind()
            val stride = effectiveWidth * format.componentCount
            val row = ByteArray(stride)

            for (y in 0 until effectiveHeight) {
                (pixels as Buffer).position((effectiveHeight - y - 1) * stride)
                pixels.get(row)
                flippedPixels.put(row)
            }

            (flippedPixels as Buffer).rewind()
            pixels = flippedPixels
        }

        when (imageFileFormat) {
            ImageFileFormat.JPG -> STBImageWrite.stbi_write_jpg_to_func(
                    writeFunc, 0L,
                    effectiveWidth, effectiveHeight,
                    format.componentCount, pixels, 90)
            ImageFileFormat.PNG -> STBImageWrite.stbi_write_png_to_func(
                    writeFunc, 0L,
                    effectiveWidth, effectiveHeight,
                    format.componentCount, pixels, effectiveWidth * format.componentCount)
            else -> {
                // do nothing
            }
        }

        val byteArray = ByteArray((saveBuffer as Buffer).position())
        (saveBuffer as Buffer).rewind()
        saveBuffer.get(byteArray)
        val base64Data = Base64.getEncoder().encodeToString(byteArray)

        return "data:${imageFileFormat.mimeType};base64,$base64Data"
    }

    override fun destroy() {
        if (!destroyed) {
            session?.untrack(this)
            glDeleteTextures(texture)
            destroyed = true
            checkGLErrors()
        }
    }

    override fun bind(unit: Int) {
        checkDestroyed()
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(target, texture)
    }

    private fun checkDestroyed() {
        if (destroyed) {
            throw IllegalStateException("colorbuffer is destroyed")
        }
    }

    override fun toString(): String {
        return "ColorBufferGL3(target=$target, texture=$texture, width=$width, height=$height, contentScale=$contentScale, format=$format, type=$type, levels=$levels, multisample=$multisample)"
    }
}

internal fun MinifyingFilter.toGLFilter(): Int {
    return when (this) {
        MinifyingFilter.NEAREST -> GL_NEAREST
        MinifyingFilter.LINEAR -> GL_LINEAR
        MinifyingFilter.LINEAR_MIPMAP_LINEAR -> GL_LINEAR_MIPMAP_LINEAR
        MinifyingFilter.LINEAR_MIPMAP_NEAREST -> GL_LINEAR_MIPMAP_NEAREST
        MinifyingFilter.NEAREST_MIPMAP_LINEAR -> GL_NEAREST_MIPMAP_LINEAR
        MinifyingFilter.NEAREST_MIPMAP_NEAREST -> GL_NEAREST_MIPMAP_NEAREST
    }
}

internal fun MagnifyingFilter.toGLFilter(): Int {
    return when (this) {
        MagnifyingFilter.NEAREST -> GL_NEAREST
        MagnifyingFilter.LINEAR -> GL_LINEAR
    }
}

internal fun WrapMode.glWrap(): Int {
    return when (this) {
        WrapMode.CLAMP_TO_EDGE -> GL_CLAMP_TO_EDGE
        WrapMode.MIRRORED_REPEAT -> GL_MIRRORED_REPEAT
        WrapMode.REPEAT -> GL_REPEAT
    }
}

internal fun ColorFormat.glFormat(): Int {
    return when (this) {
        ColorFormat.R -> GL_RED
        ColorFormat.RG -> GL_RG
        ColorFormat.RGB -> GL_RGB
        ColorFormat.RGBa -> GL_RGBA
        ColorFormat.sRGB -> GL_RGB
        ColorFormat.sRGBa -> GL_RGBA
        ColorFormat.BGR -> GL_BGR
        ColorFormat.BGRa -> GL_BGRA
    }
}

internal fun ColorType.glType(): Int {
    return when (this) {
        ColorType.UINT8, ColorType.UINT8_INT -> GL_UNSIGNED_BYTE
        ColorType.SINT8_INT -> GL_BYTE
        ColorType.UINT16, ColorType.UINT16_INT -> GL_UNSIGNED_SHORT
        ColorType.SINT16_INT -> GL_SHORT
        ColorType.UINT32_INT -> GL_UNSIGNED_INT
        ColorType.SINT32_INT -> GL_INT
        ColorType.FLOAT16 -> GL_HALF_FLOAT
        ColorType.FLOAT32 -> GL_FLOAT
        ColorType.DXT1, ColorType.DXT3, ColorType.DXT5,
        ColorType.BPTC_UNORM, ColorType.BPTC_FLOAT, ColorType.BPTC_UFLOAT -> throw RuntimeException("gl type of compressed types cannot be queried")
    }
}


internal fun compressedType(format: ColorFormat, type: ColorType): Int {
    when (format) {
        ColorFormat.RGBa -> return when (type) {
            ColorType.DXT1 -> GL_COMPRESSED_RGBA_S3TC_DXT1_EXT
            ColorType.DXT3 -> GL_COMPRESSED_RGBA_S3TC_DXT3_EXT
            ColorType.DXT5 -> GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
            ColorType.BPTC_UNORM -> GL_COMPRESSED_RGBA_BPTC_UNORM_ARB
            else -> throw IllegalArgumentException()
        }
        ColorFormat.sRGBa -> return when (type) {
            ColorType.DXT1 -> GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT1_EXT
            ColorType.DXT3 -> GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT3_EXT
            ColorType.DXT5 -> GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT
            else -> throw IllegalArgumentException()
        }
        ColorFormat.RGB -> return when (type) {
            ColorType.DXT1 -> GL_COMPRESSED_RGB_S3TC_DXT1_EXT
            else -> throw IllegalArgumentException()
        }
        ColorFormat.sRGB -> return when (type) {
            ColorType.DXT1 -> GL_COMPRESSED_SRGB_S3TC_DXT1_EXT
            else -> throw IllegalArgumentException()
        }
        else -> throw IllegalArgumentException()
    }
}