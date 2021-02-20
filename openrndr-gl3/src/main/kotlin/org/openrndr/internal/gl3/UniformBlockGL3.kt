package org.openrndr.internal.gl3

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33C.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.UniformBlock
import org.openrndr.draw.UniformBlockLayout
import org.openrndr.draw.UniformType
import org.openrndr.internal.Driver
import org.openrndr.math.*
import java.nio.Buffer
import java.nio.ByteBuffer

internal fun ByteBuffer.safePosition(offset: Int) {
    (this as Buffer).position(offset)
}

internal fun ByteBuffer.safeRewind() {
    (this as Buffer).rewind()
}


private var blockBindings = 0
class UniformBlockGL3(override val layout: UniformBlockLayout, val blockBinding: Int, val ubo: Int, val shadowBuffer: ByteBuffer) : UniformBlock {

    internal val thread = Thread.currentThread()
    private val lastValues = mutableMapOf<String, Any>()
    var realDirty: Boolean = true
    override val dirty: Boolean
        get() = realDirty

    companion object {
        fun create(layout: UniformBlockLayout): UniformBlockGL3 {
            synchronized(Driver.instance) {
                val ubo = glGenBuffers()
                glBindBuffer(GL_UNIFORM_BUFFER, ubo)
                glBufferData(GL_UNIFORM_BUFFER, layout.sizeInBytes.toLong(), GL_DYNAMIC_DRAW)
                glBindBuffer(GL_UNIFORM_BUFFER, 0)

                glBindBufferBase(GL_UNIFORM_BUFFER, blockBindings, ubo)
                blockBindings++
                val buffer = BufferUtils.createByteBuffer(layout.sizeInBytes)
                return UniformBlockGL3(layout, blockBindings - 1, ubo, buffer)
            }
        }
    }

    override fun uniform(name: String, value: Float) {
        if (lastValues[name] != value) {
            val entry = layout.entries[name]
            if (entry != null) {
                if (entry.type == UniformType.FLOAT32 && entry.size == 1) {
                    shadowBuffer.putFloat(entry.offset, value)
                } else {
                    throw RuntimeException("uniform mismatch")
                }
            } else {
                throw RuntimeException("uniform not found $name")
            }
            lastValues[name] = value
            realDirty = true
        }
    }

    override fun uniform(name: String, value: Vector2) {
        if (lastValues[name] != value) {
            val entry = layout.entries[name]
            if (entry != null) {
                if (entry.type == UniformType.VECTOR2_FLOAT32 && entry.size == 1) {
                    shadowBuffer.putFloat(entry.offset, value.x.toFloat())
                    shadowBuffer.putFloat(entry.offset + 4, value.y.toFloat())
                } else {
                    throw RuntimeException("uniform mismatch")
                }
            } else {
                throw RuntimeException("uniform not found $name")
            }
            lastValues[name] = value
            realDirty = true
        }
    }

    override fun uniform(name: String, value: Vector3) {
        if (lastValues[name] != value) {
            val entry = layout.entries[name]
            if (entry != null) {
                if (entry.type == UniformType.VECTOR3_FLOAT32 && entry.size == 1) {
                    shadowBuffer.putFloat(entry.offset, value.x.toFloat())
                    shadowBuffer.putFloat(entry.offset + 4, value.y.toFloat())
                    shadowBuffer.putFloat(entry.offset + 8, value.z.toFloat())
                } else {
                    throw RuntimeException("uniform mismatch")
                }
            } else {
                throw RuntimeException("uniform not found $name")
            }
            lastValues[name] = value
            realDirty = true
        }
    }

    override fun uniform(name: String, value: ColorRGBa) {
        if (lastValues[name] != value) {
            val entry = layout.entries[name]
            if (entry != null) {
                if (entry.type == UniformType.VECTOR4_FLOAT32 && entry.size == 1) {
                    shadowBuffer.putFloat(entry.offset, value.r.toFloat())
                    shadowBuffer.putFloat(entry.offset + 4, value.g.toFloat())
                    shadowBuffer.putFloat(entry.offset + 8, value.b.toFloat())
                    shadowBuffer.putFloat(entry.offset + 12, value.a.toFloat())
                } else {
                    throw RuntimeException("uniform mismatch")
                }
            } else {
                throw RuntimeException("uniform not found $name")
            }
            lastValues[name] = value
            realDirty = true
        }
    }

    override fun uniform(name: String, value: Vector4) {
        if (lastValues[name] != value) {
            val entry = layout.entries[name]
            if (entry != null) {
                if (entry.type == UniformType.VECTOR4_FLOAT32 && entry.size == 1) {
                    shadowBuffer.putFloat(entry.offset, value.x.toFloat())
                    shadowBuffer.putFloat(entry.offset + 4, value.y.toFloat())
                    shadowBuffer.putFloat(entry.offset + 8, value.z.toFloat())
                    shadowBuffer.putFloat(entry.offset + 12, value.w.toFloat())
                } else {
                    throw RuntimeException("uniform mismatch")
                }
            } else {
                throw RuntimeException("uniform not found $name")
            }
            lastValues[name] = value
            realDirty = true
        }
    }


    override fun uniform(name: String, value: Matrix44) {
        if (lastValues[name] !== value) {

            val entry = layout.entries[name]
            if (entry != null) {
                if (entry.type == UniformType.MATRIX44_FLOAT32 && entry.size == 1) {
                    (shadowBuffer as Buffer).position(entry.offset)
                    shadowBuffer.putFloat(value.c0r0.toFloat())
                    shadowBuffer.putFloat(value.c0r1.toFloat())
                    shadowBuffer.putFloat(value.c0r2.toFloat())
                    shadowBuffer.putFloat(value.c0r3.toFloat())

                    shadowBuffer.putFloat(value.c1r0.toFloat())
                    shadowBuffer.putFloat(value.c1r1.toFloat())
                    shadowBuffer.putFloat(value.c1r2.toFloat())
                    shadowBuffer.putFloat(value.c1r3.toFloat())

                    shadowBuffer.putFloat(value.c2r0.toFloat())
                    shadowBuffer.putFloat(value.c2r1.toFloat())
                    shadowBuffer.putFloat(value.c2r2.toFloat())
                    shadowBuffer.putFloat(value.c2r3.toFloat())

                    shadowBuffer.putFloat(value.c3r0.toFloat())
                    shadowBuffer.putFloat(value.c3r1.toFloat())
                    shadowBuffer.putFloat(value.c3r2.toFloat())
                    shadowBuffer.putFloat(value.c3r3.toFloat())

                } else {
                    throw RuntimeException("uniform mismatch")
                }
            } else {
                throw RuntimeException("uniform not found $name")
            }
            realDirty = true
            lastValues[name] = value
        }
    }

    override fun uniform(name: String, value: Matrix55) {
        if (lastValues[name] !== value) {
            val entry = layout.entries[name]
            if (entry != null) {
                val values = value.floatArray
                if (entry.type == UniformType.FLOAT32 && entry.size == 25) {
                    for (i in 0 until 25) {
                        shadowBuffer.putFloat(entry.offset + i * entry.stride, values[i])
                    }
                } else {
                    throw RuntimeException("uniform mismatch")
                }


            } else {
                throw RuntimeException("uniform not found $name")
            }
            realDirty = true
            lastValues[name] = value
        }
    }

    override fun uniform(name: String, value: Array<Float>) {
        val entry = layout.entries[name]
        if (entry != null) {
            if (entry.type == UniformType.FLOAT32 && entry.size == value.size) {
                for (i in value.indices) {
                    shadowBuffer.putFloat(entry.offset + i * entry.stride, value[i])
                }
            } else {
                throw RuntimeException("uniform mismatch")
            }
        } else {
            throw RuntimeException("uniform not found $name")
        }
        realDirty = true
    }

    override fun uniform(name: String, value: Array<Vector2>) {
        val entry = layout.entries[name]
        if (entry != null) {
            if (entry.type == UniformType.VECTOR4_FLOAT32 && entry.size == value.size) {
                shadowBuffer.safePosition(entry.offset)
                for (i in value.indices) {
                    shadowBuffer.putFloat(value[i].x.toFloat())
                    shadowBuffer.putFloat(value[i].y.toFloat())
                }
            } else {
                throw RuntimeException("uniform mismatch")
            }
        } else {
            throw RuntimeException("uniform not found $name")
        }
        realDirty = true
    }

    override fun uniform(name: String, value: Array<Vector3>) {
        val entry = layout.entries[name]
        if (entry != null) {
            if (entry.type == UniformType.VECTOR4_FLOAT32 && entry.size == value.size) {
                shadowBuffer.safePosition(entry.offset)
                for (i in value.indices) {
                    shadowBuffer.putFloat(value[i].x.toFloat())
                    shadowBuffer.putFloat(value[i].y.toFloat())
                    shadowBuffer.putFloat(value[i].z.toFloat())
                }
            } else {
                throw RuntimeException("uniform mismatch")
            }
        } else {
            throw RuntimeException("uniform not found $name")
        }
        realDirty = true
    }

    override fun uniform(name: String, value: Array<Vector4>) {
        val entry = layout.entries[name]
        if (entry != null) {
            if (entry.type == UniformType.VECTOR4_FLOAT32 && entry.size == value.size) {
                shadowBuffer.safePosition(entry.offset)
                for (i in value.indices) {
                    shadowBuffer.putFloat(value[i].x.toFloat())
                    shadowBuffer.putFloat(value[i].y.toFloat())
                    shadowBuffer.putFloat(value[i].z.toFloat())
                }
            } else {
                throw RuntimeException("uniform mismatch")
            }
        } else {
            throw RuntimeException("uniform not found $name")
        }
        realDirty = true
    }


    override fun upload() {
        if (Thread.currentThread() != thread) {
            throw IllegalStateException("current thread ${Thread.currentThread()} is not equal to creation thread $thread")
        }
        realDirty = false
        glBindBuffer(GL_UNIFORM_BUFFER, ubo)
        shadowBuffer.safeRewind()
        glBufferSubData(GL_UNIFORM_BUFFER, 0L, shadowBuffer)
        checkGLErrors()
        glBindBuffer(GL_UNIFORM_BUFFER, 0)
    }
}