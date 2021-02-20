package org.openrndr.internal.gl3

import mu.KotlinLogging
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL43C
import org.lwjgl.opengl.GL43C.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.internal.Driver
import org.openrndr.math.*
import java.nio.Buffer

private val logger = KotlinLogging.logger {}

fun ImageAccess.gl(): Int {
    return when (this) {
        ImageAccess.READ -> GL_READ_ONLY
        ImageAccess.WRITE -> GL_WRITE_ONLY
        ImageAccess.READ_WRITE -> GL_READ_WRITE
    }
}

class ComputeShaderGL43(val programObject: Int, val name: String = "compute_shader") : ComputeShader {

    private var destroyed = false
    private val uniforms: MutableMap<String, Int> = hashMapOf()
    private fun uniformIndex(uniform: String, query: Boolean = false): Int =
            uniforms.getOrPut(uniform) {
                val location = glGetUniformLocation(programObject, uniform)
                debugGLErrors()
                if (location == -1 && !query) {
                    logger.warn {
                        "Shader '${name}' does not have a uniform called '$uniform'"
                    }
                }
                location
            }

    override fun execute(width: Int, height: Int, depth: Int) {

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo)
        checkGLErrors()
        glUseProgram(programObject)
        checkGLErrors()
        glDispatchCompute(width, height, depth)
        checkGLErrors()
        //glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        glMemoryBarrier(GL_ALL_BARRIER_BITS)
        checkGLErrors()
    }

    private fun bound(f: () -> Unit) {
        glUseProgram(programObject)
        f()

    }

    override fun image(name: String, image: Int, imageBinding: ImageBinding) {
        require((Driver.instance as DriverGL3).version >= DriverVersionGL.VERSION_4_3)

        when (imageBinding) {
            is ColorBufferImageBinding -> {
                val colorBuffer = imageBinding.colorBuffer as ColorBufferGL3
                require(colorBuffer.format.componentCount != 3) {
                    "color buffer has unsupported format (${imageBinding.colorBuffer.format}), only formats with 1, 2 or 4 components are supported"
                }
                GL43C.glBindImageTexture(image, colorBuffer.texture, 0, false, 0, imageBinding.access.gl(), colorBuffer.glFormat())
            }
            else -> error("unsupported binding")
        }
        val index = uniformIndex(name)
        GL43C.glUniform1i(index, image)
        checkGLErrors()
    }



    private val ssbo = glGenBuffers()
    private val storageIndex = mutableMapOf<String, Int>()

    override fun buffer(name: String, vertexBuffer: VertexBuffer) {
        val index = storageIndex.getOrPut(name) { storageIndex.size + 8 }
        vertexBuffer as VertexBufferGL3
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo)
        checkGLErrors()
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, vertexBuffer.buffer)
        checkGLErrors()

        val blockIndex = glGetProgramResourceIndex(programObject, GL_SHADER_STORAGE_BLOCK, name)
        if (blockIndex >= 0) {
            glShaderStorageBlockBinding(programObject, blockIndex, index)
        } else {
            logger.warn { "no such program resource: $name" }
        }


    }

    override fun counters(bindingIndex: Int, counterBuffer: AtomicCounterBuffer) {
        counterBuffer as AtomicCounterBufferGL43
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo)
        checkGLErrors()
        glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER, bindingIndex, counterBuffer.buffer)
        checkGLErrors()
    }

    override fun uniform(name: String, value: ColorRGBa) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform4f(index, value.r.toFloat(), value.g.toFloat(), value.b.toFloat(), value.a.toFloat())
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Vector3) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform3f(index, value.x.toFloat(), value.y.toFloat(), value.z.toFloat())
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Vector4) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform4f(index, value.x.toFloat(), value.y.toFloat(), value.z.toFloat(), value.w.toFloat())
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, x: Float, y: Float, z: Float, w: Float) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform4f(index, x, y, z, w)
            }
        }
    }

    override fun uniform(name: String, x: Float, y: Float, z: Float) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform3f(index, x, y, z)
            }
        }
    }

    override fun uniform(name: String, x: Float, y: Float) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform2f(index, x, y)
            }
        }
    }

    override fun uniform(name: String, value: Int) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform1i(index, value)
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Boolean) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform1i(index, if (value) 1 else 0)
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Vector2) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform2f(index, value.x.toFloat(), value.y.toFloat())
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Float) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform1f(index, value)
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Double) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform1f(index, value.toFloat())
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Matrix33) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                logger.trace { "Setting uniform '$name' to $value" }
                glUniformMatrix3fv(index, false, value.toFloatArray())
                postUniformCheck(name, index, value)
            }
        }
    }


    override fun uniform(name: String, value: Matrix44) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                logger.trace { "Setting uniform '$name' to $value" }
                glUniformMatrix4fv(index, false, value.toFloatArray())
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Array<Vector2>) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                logger.trace { "Setting uniform '$name' to $value" }

                val floatValues = FloatArray(value.size * 2)
                for (i in value.indices) {
                    floatValues[i * 2] = value[i].x.toFloat()
                    floatValues[i * 2 + 1] = value[i].y.toFloat()
                }

                glUniform2fv(index, floatValues)
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Array<Vector3>) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                logger.trace { "Setting uniform '$name' to $value" }

                val floatValues = FloatArray(value.size * 3)
                for (i in value.indices) {
                    floatValues[i * 3] = value[i].x.toFloat()
                    floatValues[i * 3 + 1] = value[i].y.toFloat()
                    floatValues[i * 3 + 2] = value[i].z.toFloat()
                }
                glUniform3fv(index, floatValues)
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Array<Vector4>) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                logger.trace { "Setting uniform '$name' to $value" }

                val floatValues = FloatArray(value.size * 4)
                for (i in value.indices) {
                    floatValues[i * 4] = value[i].x.toFloat()
                    floatValues[i * 4 + 1] = value[i].y.toFloat()
                    floatValues[i * 4 + 2] = value[i].z.toFloat()
                    floatValues[i * 4 + 3] = value[i].w.toFloat()
                }

                glUniform4fv(index, floatValues)
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: FloatArray) {
        bound {
            val index = uniformIndex(name)
            if (index != -1) {
                logger.trace { "Setting uniform '$name' to $value" }
                glUniform1fv(index, value)
                postUniformCheck(name, index, value)
            }
        }
    }

    private fun postUniformCheck(name: String, index: Int, value: Any) {
        debugGLErrors {
            val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)

            fun checkUniform(): String {
                if (currentProgram > 0) {
                    val lengthBuffer = BufferUtils.createIntBuffer(1)
                    val sizeBuffer = BufferUtils.createIntBuffer(1)
                    val typeBuffer = BufferUtils.createIntBuffer(1)
                    val nameBuffer = BufferUtils.createByteBuffer(256)

                    glGetActiveUniform(currentProgram, index, lengthBuffer, sizeBuffer, typeBuffer, nameBuffer)
                    val nameBytes = ByteArray(lengthBuffer[0])
                    (nameBuffer as Buffer).rewind()
                    nameBuffer.get(nameBytes)
                    val retrievedName = String(nameBytes)
                    return "($name/$retrievedName): ${sizeBuffer[0]} / ${typeBuffer[0]}}"
                }
                return "no program"
            }

            when (it) {
                GL_INVALID_OPERATION -> "no current program object ($currentProgram), or uniform type mismatch (${checkUniform()}"
                else -> null
            }
        }
    }


    override fun destroy() {
        if (!destroyed) {
            glDeleteProgram(programObject)
            checkGLErrors()
        }
    }

    companion object {
        fun createFromCode(code: String): ComputeShaderGL43 {
            val shaderObject = glCreateShader(GL_COMPUTE_SHADER)
            glShaderSource(shaderObject, code)
            checkGLErrors()
            glCompileShader(shaderObject)
            checkGLErrors()

            val compileStatus = IntArray(1)
            glGetShaderiv(shaderObject, GL_COMPILE_STATUS, compileStatus)
            checkGLErrors()
            if (compileStatus[0] != GL_TRUE) {
                checkShaderInfoLog(shaderObject, code, "compute shader")
                throw Exception("could not compile vertex shader")
            }

            val program = glCreateProgram()
            glAttachShader(program, shaderObject)
            checkGLErrors()
            glLinkProgram(program)
            checkGLErrors()
            checkProgramInfoLog(program, "compute shader")

            return ComputeShaderGL43(program)
        }
    }


}
