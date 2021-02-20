package org.openrndr.internal.gl3

import mu.KotlinLogging
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.opengl.GL43C
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.internal.Driver
import org.openrndr.math.*
import org.openrndr.measure
import java.io.File
import java.io.FileWriter
import java.nio.Buffer

private val logger = KotlinLogging.logger {}

internal fun checkShaderInfoLog(`object`: Int, code: String, sourceFile: String) {
    logger.debug { "getting shader info log" }
    val logLength = IntArray(1)
    glGetShaderiv(`object`, GL_INFO_LOG_LENGTH, logLength)
    logger.debug { "log length: ${logLength[0]}" }

    if (logLength[0] > 0) {
        logger.debug { "getting log" }
        val infoLog = BufferUtils.createByteBuffer(logLength[0])
        (infoLog as Buffer).rewind()
        glGetShaderInfoLog(`object`, logLength, infoLog)

        val infoBytes = ByteArray(logLength[0])
        infoLog.get(infoBytes)
        println("GLSL compilation problems in\n ${String(infoBytes)}")

        val temp = File("ShaderError.txt")
        FileWriter(temp).use {
            it.write(code)
        }
        System.err.println("click.to.see.shader.code(ShaderError.txt:1)")
        logger.error { "GLSL shader compilation failed for $sourceFile" }
        throw Exception("Shader error: $sourceFile")
    }
}

fun checkProgramInfoLog(`object`: Int, sourceFile: String) {
    val logLength = IntArray(1)
    glGetProgramiv(`object`, GL_INFO_LOG_LENGTH, logLength)

    if (logLength[0] > 1) {
        val infoLog = BufferUtils.createByteBuffer(logLength[0])
        glGetProgramInfoLog(`object`, logLength, infoLog)
        val linkInfoBytes = ByteArray(logLength[0])
        infoLog.get(linkInfoBytes)
        println("GLSL link problems in\n ${String(linkInfoBytes)}")

        logger.warn {
            val infoBytes = ByteArray(logLength[0])
            infoLog.get(infoBytes)
            "GLSL link problems in\n ${String(infoBytes)}"
        }
        throw Exception("Shader error: $sourceFile")
    }
}

class ShaderGL3(val program: Int,
                val name: String,
                val vertexShader: VertexShaderGL3,
                val tessellationControlShader: TessellationControlShaderGL3?,
                val tessellationEvaluationShader: TessellationEvaluationShaderGL3?,
                val geometryShader: GeometryShaderGL3?,
                val fragmentShader: FragmentShaderGL3,
                override val session: Session?) : Shader {

    private val lastValues = mutableMapOf<String, Any>()

    override val types: Set<ShaderType> = if (geometryShader != null) setOf(ShaderType.VERTEX, ShaderType.GEOMETRY, ShaderType.FRAGMENT) else
        setOf(ShaderType.VERTEX, ShaderType.FRAGMENT)

    private var destroyed = false
    private var running = false
    private var uniforms: MutableMap<String, Int> = hashMapOf()
    private var attributes: MutableMap<String, Int> = hashMapOf()
    private var blockBindings = hashMapOf<String, Int>()
    private val blocks: MutableMap<String, Int> = hashMapOf()

    /**
     * Is this a shader created by the user, i.e. should we perform extra checking on the inputs
     */
    internal var userShader = true

    companion object {
        fun create(
                vertexShader: VertexShaderGL3,
                tessellationControlShader: TessellationControlShaderGL3?,
                tessellationEvaluationShader: TessellationEvaluationShaderGL3?,
                geometryShader: GeometryShaderGL3?,
                fragmentShader: FragmentShaderGL3,
                name: String,
                session: Session?
        ): ShaderGL3 {
            synchronized(Driver.instance) {
                debugGLErrors()

                val program = glCreateProgram()
                debugGLErrors()

                glAttachShader(program, vertexShader.shaderObject)
                debugGLErrors()



                tessellationControlShader?.let {
                    glAttachShader(program, it.shaderObject)
                    debugGLErrors()
                }

                tessellationEvaluationShader?.let {
                    glAttachShader(program, it.shaderObject)
                    debugGLErrors()
                }

                geometryShader?.let {
                    glAttachShader(program, it.shaderObject)
                    debugGLErrors()
                }


                glAttachShader(program, fragmentShader.shaderObject)
                debugGLErrors()

                glLinkProgram(program)
                debugGLErrors()

                val linkStatus = IntArray(1)
                glGetProgramiv(program, GL_LINK_STATUS, linkStatus)
                debugGLErrors()

                if (linkStatus[0] != GL_TRUE) {
                    checkProgramInfoLog(program, "noname")
                }
                glFinish()
                return ShaderGL3(program, name, vertexShader, tessellationControlShader, tessellationEvaluationShader, geometryShader, fragmentShader, session)
            }
        }
    }

    override fun createBlock(blockName: String): UniformBlock? {
        val layout = blockLayout(blockName)
        return if (layout != null) {
            UniformBlockGL3.create(layout)
        } else {
            null
        }
    }

    override fun blockLayout(blockName: String): UniformBlockLayout? {
        val blockIndex = glGetUniformBlockIndex(program, blockName)

        if (blockIndex == -1) {
            return null
        }

        val blockSize = run {
            val blockSizeBuffer = BufferUtils.createIntBuffer(1)
            glGetActiveUniformBlockiv(program, blockIndex, GL_UNIFORM_BLOCK_DATA_SIZE, blockSizeBuffer)
            blockSizeBuffer[0]
        }

        if (blockSize != 0) {
            val uniformCount = run {
                val uniformCountBuffer = BufferUtils.createIntBuffer(1)
                glGetActiveUniformBlockiv(program, blockIndex, GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS, uniformCountBuffer)
                uniformCountBuffer[0]
            }

            val uniformIndicesBuffer = BufferUtils.createIntBuffer(uniformCount)
            val uniformIndices = run {
                glGetActiveUniformBlockiv(program, blockIndex, GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES, uniformIndicesBuffer)
                (uniformIndicesBuffer as Buffer).rewind()
                val array = IntArray(uniformCount)
                uniformIndicesBuffer.get(array)
                array
            }

            (uniformIndicesBuffer as Buffer).rewind()

            val uniformTypes = run {
                val buffer = BufferUtils.createIntBuffer(uniformCount)
                glGetActiveUniformsiv(program, uniformIndicesBuffer, GL_UNIFORM_TYPE, buffer)
                (buffer as Buffer).rewind()
                val array = IntArray(uniformCount)
                buffer.get(array)
                array
            }
            val uniformSizes = run {
                val buffer = BufferUtils.createIntBuffer(uniformCount)
                glGetActiveUniformsiv(program, uniformIndicesBuffer, GL_UNIFORM_SIZE, buffer)
                (buffer as Buffer).rewind()
                val array = IntArray(uniformCount)
                buffer.get(array)
                array
            }

            val uniformOffsets = run {
                val buffer = BufferUtils.createIntBuffer(uniformCount)
                glGetActiveUniformsiv(program, uniformIndicesBuffer, GL_UNIFORM_OFFSET, buffer)
                (buffer as Buffer).rewind()
                val array = IntArray(uniformCount)
                buffer.get(array)
                array
            }

            val uniformStrides = run {
                val buffer = BufferUtils.createIntBuffer(uniformCount)
                glGetActiveUniformsiv(program, uniformIndicesBuffer, GL_UNIFORM_ARRAY_STRIDE, buffer)
                (buffer as Buffer).rewind()
                val array = IntArray(uniformCount)
                buffer.get(array)
                array
            }

            val uniformNames = uniformIndices.map {
                glGetActiveUniformName(program, it, 128)
            }

            checkGLErrors()

            return UniformBlockLayout(blockSize, (0 until uniformCount).map {
                UniformDescription(uniformNames[it].replace(Regex("\\[.*\\]"), ""), uniformTypes[it].toUniformType(), uniformSizes[it], uniformOffsets[it], uniformStrides[it])
            }.associateBy { it.name })


        } else {
            return null
        }

    }

    override fun block(blockName: String, block: UniformBlock) {
        if (Thread.currentThread() != (block as UniformBlockGL3).thread) {
            throw IllegalStateException("block is created on ${block.thread} and is now used on ${Thread.currentThread()}")
        }

        if (!running) {
            throw IllegalStateException("use begin() before setting blocks")
        }
        val blockIndex = blockIndex(blockName)
        if (blockIndex != -1) {
            if (blockBindings[blockName] != block.blockBinding) {
                //checkGLErrors()
                glUniformBlockBinding(program, blockIndex, block.blockBinding)
                debugGLErrors()
                blockBindings[blockName] = block.blockBinding
            }
        }
    }

    fun blockIndex(block: String): Int {
        return blocks.getOrPut(block) {
            glGetUniformBlockIndex(program, block)
        }
    }

    fun uniformIndex(uniform: String, query: Boolean = false): Int = measure("uniform-index") {
        uniforms.getOrPut(uniform) {
            val location = glGetUniformLocation(program, uniform)
            debugGLErrors()
            if (location == -1 && !query) {
                logger.warn {
                    "Shader '${name}' does not have a uniform called '$uniform'"
                }
            }
            location
        }
    }

    override fun begin() {
        logger.trace { "shader begin $name" }
        running = true
        measure("glUseProgram $program") {
            glUseProgram(program)
        }
        debugGLErrors {
            when (it) {
                GL_INVALID_VALUE -> "program is neither 0 nor a value generated by OpenGL"
                GL_INVALID_OPERATION -> "program ($program) is not a program object / program could not be made part of current state / transform feedback mode is active."
                else -> null
            }
        }
    }

    override fun end() {
        logger.trace { "shader end $name" }
        measure("glUseProgram 0") {
            glUseProgram(0)
        }
        debugGLErrors()
        running = false
    }

    override fun hasUniform(name: String): Boolean {
        return uniformIndex(name, true) != -1
    }

    override fun uniform(name: String, value: ColorRGBa) {
        measure("set-uniform-colorrgba::$name") {
            if (lastValues[name] !== value) {
                measure("miss") {
                    val index = uniformIndex(name)
                    if (index != -1) {
                        glUniform4f(index, value.r.toFloat(), value.g.toFloat(), value.b.toFloat(), value.a.toFloat())
                        postUniformCheck(name, index, value)
                    }
                    lastValues[name] = value
                }
            }
        }
    }

    override fun uniform(name: String, value: Vector3) {
        measure("set-uniform-vector3::$name") {
            if (lastValues[name] !== value) {
                val index = uniformIndex(name)
                if (index != -1) {
                    glUniform3f(index, value.x.toFloat(), value.y.toFloat(), value.z.toFloat())
                    postUniformCheck(name, index, value)
                }
                lastValues[name] = value
            }
        }
    }

    override fun uniform(name: String, value: Vector4) {
        measure("set-uniform-vector4::$name") {
            if (lastValues[name] !== value) {
                val index = uniformIndex(name)
                if (index != -1) {
                    glUniform4f(index, value.x.toFloat(), value.y.toFloat(), value.z.toFloat(), value.w.toFloat())
                    postUniformCheck(name, index, value)
                }
                lastValues[name] = value
            }
        }
    }

    override fun uniform(name: String, value: IntVector3) {
        measure("set-uniform-intvector3::$name") {
            if (lastValues[name] !== value) {
                val index = uniformIndex(name)
                if (index != -1) {
                    glUniform3i(index, value.x, value.y, value.z)
                    postUniformCheck(name, index, value)
                }
                lastValues[name] = value
            }
        }
    }

    override fun uniform(name: String, value: IntVector4) {
        measure("set-uniform-intvector4::$name") {
            if (lastValues[name] !== value) {
                val index = uniformIndex(name)
                if (index != -1) {
                    glUniform4i(index, value.x, value.y, value.z, value.w)
                    postUniformCheck(name, index, value)
                }
                lastValues[name] = value
            }
        }
    }

    override fun uniform(name: String, x: Float, y: Float, z: Float, w: Float) {
        val index = uniformIndex(name)
        if (index != -1) {
            glUniform4f(index, x, y, z, w)
        }
    }

    override fun uniform(name: String, x: Float, y: Float, z: Float) {
        val index = uniformIndex(name)
        if (index != -1) {
            glUniform3f(index, x, y, z)
        }
    }

    override fun uniform(name: String, x: Float, y: Float) {
        val index = uniformIndex(name)
        if (index != -1) {
            glUniform2f(index, x, y)
        }
    }

    override fun uniform(name: String, value: Int) {
        if (lastValues[name] == null || lastValues[name] != value) {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform1i(index, value)
                postUniformCheck(name, index, value)
            }
        }
    }

    override fun uniform(name: String, value: Boolean) {
        if (lastValues[name] == null || lastValues[name] != value) {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform1i(index, if (value) 1 else 0)
                postUniformCheck(name, index, value)
            }
        }
    }


    override fun uniform(name: String, value: Vector2) {
        if (lastValues[name] !== value) {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform2f(index, value.x.toFloat(), value.y.toFloat())
                postUniformCheck(name, index, value)
            }
            lastValues[name] = value
        }
    }

    override fun uniform(name: String, value: IntVector2) {
        if (lastValues[name] !== value) {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform2i(index, value.x, value.y)
                postUniformCheck(name, index, value)
            }
            lastValues[name] = value
        }
    }


    override fun uniform(name: String, value: Float) {
        if (lastValues[name] == null || lastValues[name] != value) {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform1f(index, value)
                postUniformCheck(name, index, value)
            }
            lastValues[name] = value
        }
    }

    override fun uniform(name: String, value: Double) {
        if (lastValues[name] == null || lastValues[name] != value) {
            val index = uniformIndex(name)
            if (index != -1) {
                glUniform1f(index, value.toFloat())
                postUniformCheck(name, index, value)
            }
            lastValues[name] = value
        }
    }

    override fun uniform(name: String, value: Matrix33) {
        measure("set-uniform-matrix33::$name") {
            if (lastValues[name] !== value) {
                val index = uniformIndex(name)
                if (index != -1) {
                    logger.trace { "Setting uniform '$name' to $value" }
                    glUniformMatrix3fv(index, false, value.toFloatArray())
                    postUniformCheck(name, index, value)
                }
                lastValues[name] = value
            }
        }
    }


    override fun uniform(name: String, value: Matrix44) {
        measure("set-uniform-matrix44::$name") {

            val prior = measure("look-up") { lastValues[name] }
            val miss = measure("compare") { prior !== value }

            if (miss) {
                measure("miss") {
                    val index = uniformIndex(name)
                    if (index != -1) {
                        logger.trace { "Setting uniform '$name' to $value" }
                        glUniformMatrix4fv(index, false, value.toFloatArray())
                        postUniformCheck(name, index, value)
                    }
                    lastValues[name] = value
                }
            }
        }
    }

    override fun uniform(name: String, value: Array<Matrix44>) {
        val index = uniformIndex(name)
        if (index != -1) {
            logger.trace { "Setting uniform '$name' to $value" }

            val floatValues = FloatArray(value.size * 4 * 4)
            var offset = 0
            for (j in value.indices) {
                val mf = value[j].toFloatArray()
                for (i in 0 until 16) {
                    floatValues[offset] = mf[i]
                    offset++
                }
            }
            glUniformMatrix4fv(index, false, floatValues)
            postUniformCheck(name, index, value)
        }
    }

    override fun uniform(name: String, value: Array<Vector2>) {
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

    override fun uniform(name: String, value: Array<Vector3>) {
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

    override fun uniform(name: String, value: Array<Vector4>) {
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

    override fun uniform(name: String, value: Array<Double>) {
        val index = uniformIndex(name)
        if (index != -1) {
            logger.trace { "Setting uniform '$name' to $value" }

            val floatValues = FloatArray(value.size) {
                i -> value[i].toFloat()
            }

            glUniform1fv(index, floatValues)
            postUniformCheck(name, index, value)
        }
    }

    override fun uniform(name: String, value: Array<ColorRGBa>) {
        val index = uniformIndex(name)
        if (index != -1) {
            logger.trace { "Setting uniform '$name' to $value" }

            val floatValues = FloatArray(value.size * 4)
            for (i in value.indices) {
                floatValues[i * 4] = value[i].r.toFloat()
                floatValues[i * 4 + 1] = value[i].g.toFloat()
                floatValues[i * 4 + 2] = value[i].b.toFloat()
                floatValues[i * 4 + 3] = value[i].a.toFloat()
            }

            glUniform4fv(index, floatValues)
            postUniformCheck(name, index, value)
        }
    }

    override fun uniform(name: String, value: Array<IntVector2>) {
        val index = uniformIndex(name)
        if (index != -1) {
            logger.trace { "Setting uniform '$name' to $value" }

            val intValues = IntArray(value.size * 2)
            for (i in value.indices) {
                intValues[i * 2] = value[i].x
                intValues[i * 2 + 1] = value[i].y
            }

            glUniform2iv(index, intValues)
            postUniformCheck(name, index, value)
        }
    }

    override fun uniform(name: String, value: Array<IntVector3>) {
        val index = uniformIndex(name)
        if (index != -1) {
            logger.trace { "Setting uniform '$name' to $value" }

            val intValues = IntArray(value.size * 3)
            for (i in value.indices) {
                intValues[i * 3] = value[i].x
                intValues[i * 3 + 1] = value[i].y
                intValues[i * 3 + 2] = value[i].z
            }
            glUniform3iv(index, intValues)
            postUniformCheck(name, index, value)
        }
    }

    override fun uniform(name: String, value: Array<IntVector4>) {
        val index = uniformIndex(name)
        if (index != -1) {
            logger.trace { "Setting uniform '$name' to $value" }

            val intValues = IntArray(value.size * 4)
            for (i in value.indices) {
                intValues[i * 4] = value[i].x
                intValues[i * 4 + 1] = value[i].y
                intValues[i * 4 + 2] = value[i].z
                intValues[i * 4 + 3] = value[i].w
            }
            glUniform4iv(index, intValues)
            postUniformCheck(name, index, value)
        }
    }

    override fun uniform(name: String, value: FloatArray) {
        val index = uniformIndex(name)
        if (index != -1) {
            logger.trace { "Setting uniform '$name' to $value" }
            glUniform1fv(index, value)
            postUniformCheck(name, index, value)
        }
    }

    override fun uniform(name: String, value: IntArray) {
        val index = uniformIndex(name)
        if (index != -1) {
            logger.trace { "Setting uniform '$name' to $value" }
            glUniform1iv(index, value)
            postUniformCheck(name, index, value)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun postUniformCheck(name: String, index: Int, value: Any) {
        val errorCheck = { it: Int ->
            val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)

            fun checkUniform(): String {
                if (currentProgram > 0) {
                    val lengthBuffer = BufferUtils.createIntBuffer(1)
                    val sizeBuffer = BufferUtils.createIntBuffer(1)
                    val typeBuffer = BufferUtils.createIntBuffer(1)
                    val nameBuffer = BufferUtils.createByteBuffer(256)

                    glGetActiveUniform(currentProgram, index, lengthBuffer, sizeBuffer, typeBuffer, nameBuffer)
                    val nameBytes = ByteArray(lengthBuffer[0])
                    nameBuffer.safeRewind()
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

        measure("post-uniform-check") {
            if (userShader) {
                checkGLErrors(errorCheck)
            } else {
                debugGLErrors(errorCheck)
            }
        }
    }

    fun attributeIndex(name: String): Int =
            attributes.getOrPut(name) {
                val location = glGetAttribLocation(program, name)
                debugGLErrors()
                location
            }

    override fun destroy() {
        if (!destroyed) {
            session?.untrack(this)
            glDeleteProgram(program)
            destroyed = true
            Session.active.untrack(this)
        }
    }

    private fun checkUseProgramErrors() {
        checkGLErrors {
            when (it) {
                GL43C.GL_INVALID_OPERATION -> " program ($program) is not a program object / program could not be made part of current state / transform feedback mode is active"
                else -> null
            }
        }
    }

    override fun image(name: String, image: Int, imageBinding: ImageBinding) {
        (Driver.instance as DriverGL3).version.require(DriverVersionGL.VERSION_4_3)

        when (imageBinding) {
            is BufferTextureImageBinding -> {
                val bufferTexture = imageBinding.bufferTexture as BufferTextureGL3
                require(bufferTexture.format.componentCount != 3) {
                    "color buffer has unsupported format (${imageBinding.bufferTexture.format}), only formats with 1, 2 or 4 components are supported"
                }
                GL43C.glBindImageTexture(image, bufferTexture.texture, imageBinding.level, false, 0, imageBinding.access.gl(), bufferTexture.glFormat())
            }

            is ColorBufferImageBinding -> {
                val colorBuffer = imageBinding.colorBuffer as ColorBufferGL3
                require(colorBuffer.format.componentCount != 3) {
                    "color buffer has unsupported format (${imageBinding.colorBuffer.format}), only formats with 1, 2 or 4 components are supported"
                }
                GL43C.glBindImageTexture(image, colorBuffer.texture, imageBinding.level, false, 0, imageBinding.access.gl(), colorBuffer.glFormat())
            }
            is ArrayTextureImageBinding -> {
                val arrayTexture = imageBinding.arrayTexture as ArrayTextureGL3
                require(arrayTexture.format.componentCount != 3) {
                    "color buffer has unsupported format (${imageBinding.arrayTexture.format}), only formats with 1, 2 or 4 components are supported"
                }
                GL43C.glBindImageTexture(image, arrayTexture.texture, imageBinding.level, false, 0, imageBinding.access.gl(), arrayTexture.glFormat())
            }
            is CubemapImageBinding -> {
                val cubemap = imageBinding.cubemap as CubemapGL3
                require(cubemap.format.componentCount != 3) {
                    "color buffer has unsupported format (${imageBinding.cubemap.format}), only formats with 1, 2 or 4 components are supported"
                }
                GL43C.glBindImageTexture(image, cubemap.texture, imageBinding.level, false, 0, imageBinding.access.gl(), cubemap.glFormat())
            }
            is ArrayCubemapImageBinding -> {
                val arrayCubemap = imageBinding.arrayCubemap as ArrayCubemapGL4
                require(arrayCubemap.format.componentCount != 3) {
                    "color buffer has unsupported format (${imageBinding.arrayCubemap.format}), only formats with 1, 2 or 4 components are supported"
                }
                GL43C.glBindImageTexture(image, arrayCubemap.texture, imageBinding.level, false, 0, imageBinding.access.gl(), arrayCubemap.glFormat())
            }
            is VolumeTextureImageBinding -> {
                val volumeTexture = imageBinding.volumeTexture as VolumeTextureGL3
                require(volumeTexture.format.componentCount != 3) {
                    "color buffer has unsupported format (${imageBinding.volumeTexture.format}), only formats with 1, 2 or 4 components are supported"
                }
                GL43C.glBindImageTexture(image, volumeTexture.texture, imageBinding.level, false, 0, imageBinding.access.gl(), volumeTexture.glFormat())
            }

            else -> error("unsupported binding")
        }
        checkGLErrors()
        val index = uniformIndex(name)
        GL43C.glUniform1i(index, image)
        checkGLErrors()
    }
}

private fun Int.toUniformType(): UniformType {
    return when (this) {
        GL_FLOAT -> UniformType.FLOAT32
        GL_FLOAT_VEC2 -> UniformType.VECTOR2_FLOAT32
        GL_FLOAT_VEC3 -> UniformType.VECTOR3_FLOAT32
        GL_FLOAT_VEC4 -> UniformType.VECTOR4_FLOAT32
        GL_INT -> UniformType.INT32
        GL_INT_VEC2 -> UniformType.VECTOR2_INT32
        GL_INT_VEC3 -> UniformType.VECTOR3_INT32
        GL_INT_VEC4 -> UniformType.VECTOR4_INT32
        GL_FLOAT_MAT4 -> UniformType.MATRIX44_FLOAT32
        GL_FLOAT_MAT3 -> UniformType.MATRIX33_FLOAT32
        GL_FLOAT_MAT2 -> UniformType.MATRIX22_FLOAT32
        else -> throw RuntimeException("unsupported uniform type $this")
    }
}

