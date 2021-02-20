package org.openrndr.internal.gl3

import org.lwjgl.opengl.GL33C.*
import org.lwjgl.opengl.GL40C.GL_TEXTURE_CUBE_MAP_ARRAY
import org.openrndr.draw.*
import java.nio.ByteBuffer

class ArrayCubemapGL4(val target: Int,
                      val texture: Int,
                      override val width: Int,
                      override val layers: Int,
                      override val format: ColorFormat,
                      override val type: ColorType,
                      levels: Int,
                      override val session: Session?) : ArrayCubemap {


    override var levels: Int = levels
        private set(value) {
            if (field != value) {
                field = value
                bound {
                    glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_MAX_LEVEL, levels - 1)
                }
            }
        }

    companion object {
        fun create(width: Int, layers: Int, format: ColorFormat, type: ColorType, levels: Int, session: Session?): ArrayCubemapGL4 {
            val maximumLayers = glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS)
            if (layers > maximumLayers / 6) {
                throw IllegalArgumentException("layers ($layers) exceeds maximum of ${maximumLayers / 6}")
            }
            val texture = glGenTextures()
            glBindTexture(GL_TEXTURE_CUBE_MAP_ARRAY, texture)
            checkGLErrors {
                println("problem in glBindTexture")
                null
            }

            for (level in 0 until levels) {
                val div = 1 shr level
                glTexImage3D(GL_TEXTURE_CUBE_MAP_ARRAY,
                        level, internalFormat(format, type).first,
                        width / div, width / div, layers * 6,
                        0, GL_RGB, GL_UNSIGNED_BYTE, null as ByteBuffer?)
                checkGLErrors {
                    when (it) {
                        GL_INVALID_VALUE -> "level ($level) is less than 0 ($level < false), or level > max mip level, or width (${width / div}) < 0, or ($layers}) < 0  "
                        else -> null
                    }
                }
            }
            if (levels > 1) {
                glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_MAX_LEVEL, levels - 1)
                checkGLErrors()
            }

            glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_MIN_FILTER, MinifyingFilter.LINEAR.toGLFilter())
            glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_MAG_FILTER, MagnifyingFilter.LINEAR.toGLFilter())
            checkGLErrors()
            return ArrayCubemapGL4(GL_TEXTURE_CUBE_MAP_ARRAY, texture, width, layers, format, type, levels, session)
        }
    }

    internal fun glFormat(): Int {
        return internalFormat(format, type).first
    }

    override fun destroy() {
        session?.untrack(this)
        glDeleteTextures(texture)
        checkGLErrors()
    }

    override fun bind(unit: Int) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(target, texture)
    }

    override fun write(side: CubemapSide, layer: Int, buffer: ByteBuffer, sourceFormat: ColorFormat, sourceType: ColorType, level: Int) {
        bound {
            val div = 1 shl level
            if (sourceType.compressed) {
                glCompressedTexSubImage3D(target, level, 0, 0, layer * 6 + side.ordinal, width / div, width / div, 1, compressedType(sourceFormat, sourceType), buffer)
                debugGLErrors()
            } else {
                glTexSubImage3D(target, level, 0, 0, layer * 6 + side.ordinal, width / div, width / div, 1, sourceFormat.glFormat(), sourceType.glType(), buffer)
                debugGLErrors()
            }
            debugGLErrors()
        }
    }

    override fun copyTo(layer: Int, target: Cubemap, fromLevel: Int, toLevel: Int) {
        val fromDiv = 1 shl fromLevel
        val toDiv = 1 shl toLevel

        for (side in CubemapSide.values()) {
            val readTarget = renderTarget(width / fromDiv, width / fromDiv) {
                arrayCubemap(this@ArrayCubemapGL4, side, layer, fromLevel)
            } as RenderTargetGL3

            target as CubemapGL3
            readTarget.bind()
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            target.bound {
                glCopyTexSubImage2D(side.glTextureTarget, toLevel, 0, 0, 0, 0, target.width / toDiv, target.width / toDiv)
                debugGLErrors()
            }
            readTarget.unbind()

            readTarget.detachColorAttachments()
            readTarget.destroy()
        }
    }

    override fun copyTo(layer: Int, target: ArrayCubemap, targetLayer: Int, fromLevel: Int, toLevel: Int) {
        val fromDiv = 1 shl fromLevel
        val toDiv = 1 shl toLevel

        for (side in CubemapSide.values()) {
            val readTarget = renderTarget(width / fromDiv, width / fromDiv) {
                arrayCubemap(this@ArrayCubemapGL4, side, layer, fromLevel)
            } as RenderTargetGL3

            target as ArrayCubemapGL4
            readTarget.bind()
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            target.bound {
                glCopyTexSubImage3D(target.target, toLevel, 0, 0, targetLayer, 0, 0, target.width / toDiv, target.width / toDiv)
                debugGLErrors()
            }
            readTarget.unbind()
            readTarget.detachColorAttachments()
            readTarget.destroy()
        }
    }


    override fun read(layer: Int, buffer: ByteBuffer, level: Int) {
        TODO()
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

    override var flipV: Boolean = false

    override fun generateMipmaps() {
        bound {
            glGenerateMipmap(target)
        }
    }

    internal fun bound(f: ArrayCubemapGL4.() -> Unit) {
        glActiveTexture(GL_TEXTURE0)
        val current = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY)
        glBindTexture(target, texture)
        this.f()
        glBindTexture(target, current)
    }
}