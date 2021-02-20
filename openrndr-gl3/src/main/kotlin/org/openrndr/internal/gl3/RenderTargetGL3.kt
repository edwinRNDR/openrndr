package org.openrndr.internal.gl3

import mu.KotlinLogging
import org.lwjgl.glfw.GLFW.glfwGetCurrentContext
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.opengl.GL40C.glBlendEquationi
import org.lwjgl.opengl.GL40C.glBlendFunci
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import java.util.*

private val logger = KotlinLogging.logger {}

private val active = mutableMapOf<Long, Stack<RenderTargetGL3>>()

class NullRenderTargetGL3 : RenderTargetGL3(0, 640, 480, 1.0, BufferMultisample.Disabled, Session.root)

class ProgramRenderTargetGL3(override val program: Program) : ProgramRenderTarget, RenderTargetGL3(glGetInteger(GL_FRAMEBUFFER_BINDING), 0, 0, 1.0, BufferMultisample.Disabled, Session.root) {
    override val width: Int
        get() = program.window.size.x.toInt()

    override val height: Int
        get() = program.window.size.y.toInt()

    override val contentScale: Double
        get() = program.window.scale.x

    override val hasColorAttachments = true
    override val hasDepthBuffer = true
}

open class RenderTargetGL3(val framebuffer: Int,
                           override val width: Int,
                           override val height: Int,
                           override val contentScale: Double,
                           override val multisample: BufferMultisample,
                           override val session: Session?,
                           private val thread: Thread = Thread.currentThread()
) : RenderTarget {
    var destroyed = false

    override val colorAttachments: MutableList<ColorAttachment> = mutableListOf()
    override var depthBuffer: DepthBuffer? = null

    companion object {
        fun create(width: Int, height: Int, contentScale: Double = 1.0, multisample: BufferMultisample = BufferMultisample.Disabled, session: Session?): RenderTargetGL3 {
            logger.trace { "created new render target ($width*$height) @ ${contentScale}x $multisample" }
            val framebuffer = glGenFramebuffers()
            return RenderTargetGL3(framebuffer, width, height, contentScale, multisample, session)
        }

        val activeRenderTarget: RenderTargetGL3
            get() {
                val stack = active.getOrPut(glfwGetCurrentContext()) { Stack() }
                return stack.peek()
            }
    }

    private var bound = false

    override val hasColorAttachments: Boolean get() = colorAttachments.isNotEmpty()
    override val hasDepthBuffer: Boolean get() = depthBuffer != null

    override fun colorBuffer(index: Int): ColorBuffer {
        require(!destroyed)
        return (colorAttachments[index] as? ColorBufferAttachment)?.colorBuffer ?: error("attachment at $index is not a ColorBuffer")
    }

    override fun bind() {
        require(!destroyed)
        if (bound) {
            throw RuntimeException("already bound")
        } else {
            val stack = active.getOrPut(glfwGetCurrentContext()) { Stack() }
            stack.push(this)
            bindTarget()
        }
    }

    private fun bindTarget() {
        debugGLErrors { null }
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)

        if (Thread.currentThread() != thread) {
            throw IllegalStateException("this render target is created by $thread and cannot be bound to ${Thread.currentThread()}")
        }

        debugGLErrors { null }
        if (colorAttachments.isNotEmpty()) {
            val drawBuffers = colorAttachments.mapIndexed { index, _ -> GL_COLOR_ATTACHMENT0 + index }.toIntArray()
            glDrawBuffers(drawBuffers)
            debugGLErrors {
                when (it) {
                    GL_INVALID_ENUM -> "1. one of the values in bufs is not an accepted value\n2. the API call refers to the default framebuffer and one or more of the values in bufs is one of the GL_COLOR_ATTACHMENTn tokens\n3. the API call refers to a framebuffer object and one or more of the values in bufs is anything other than GL_NONE or one of the GL_COLOR_ATTACHMENTn tokens\n4. n is less than 0"
                    GL_INVALID_OPERATION -> "a symbolic constant other than GL_NONE appears more than once in bufs."
                    GL_INVALID_VALUE -> "1. n is greater than GL_MAX_DRAW_BUFFERS\n 2. any of the entries in bufs (other than GL_NONE ) indicates a color buffer that does not exist in the current GL context\n 3. any value in bufs is GL_BACK, and n is not one"
                    else -> null
                }
            }
        }

        val effectiveWidth = (width * contentScale).toInt()
        val effectiveHeight = (height * contentScale).toInt()

        logger.trace { "setting viewport to (0, 0, $effectiveWidth, $effectiveHeight)" }
        glViewport(0, 0, effectiveWidth, effectiveHeight)
        debugGLErrors { null }
    }

    override fun unbind() {
        if (!bound) {
            val previous = active.getOrPut(glfwGetCurrentContext()) { Stack() }.let {
                it.pop()
                it.peek()
            }
            previous as RenderTargetGL3
            logger.trace { "restoring to previous render target $previous" }
            previous.bindTarget()
        } else {
            throw RuntimeException("target not bound")
        }
    }

    override fun attach(colorBuffer: ColorBuffer, level: Int, name: String?) {
        require(!destroyed)

        val context = glfwGetCurrentContext()
        bindTarget()

        val div = 1 shl level
        val effectiveWidth = (width * contentScale).toInt()
        val effectiveHeight = (height * contentScale).toInt()

        if (!(colorBuffer.effectiveWidth / div == effectiveWidth && colorBuffer.effectiveHeight / div == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch. expected: ($width x $height @${colorBuffer.contentScale}x, got: (${colorBuffer.width / div} x ${colorBuffer.height / div} @${colorBuffer.contentScale}x level:${level})")
        }
        colorBuffer as ColorBufferGL3
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + colorAttachments.size, colorBuffer.target, colorBuffer.texture, level)
        debugGLErrors { null }

        colorAttachments.add(ColorBufferAttachment(colorAttachments.size, name, colorBuffer, level))

        if (active[context]?.peek() != null)
            (active[context]?.peek() as RenderTargetGL3).bindTarget()
    }

    override fun attach(arrayCubemap: ArrayCubemap, side: CubemapSide, layer: Int, level: Int, name:String?) {
        require(!destroyed)
        val context = glfwGetCurrentContext()
        bindTarget()
        val effectiveWidth = (width * contentScale).toInt()
        if (!(arrayCubemap.width == effectiveWidth && arrayCubemap.width == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch. expected: ($effectiveWidth x $effectiveHeight), got: (${arrayCubemap.width} x ${arrayCubemap.width}")
        }
        arrayCubemap as ArrayCubemapGL4
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + colorAttachments.size, arrayCubemap.texture, level, layer * 6 + side.ordinal)
        checkGLErrors { null }

        colorAttachments.add(ArrayCubemapAttachment(colorAttachments.size, name, arrayCubemap, side, layer, level))

        if (active[context]?.peek() != null)
            (active[context]?.peek() as RenderTargetGL3).bindTarget()
    }

    override fun attach(cubemap: Cubemap, side: CubemapSide, level: Int, name: String?) {
        val div = 1 shl level
        require(!destroyed)
        val context = glfwGetCurrentContext()
        bindTarget()
        val effectiveWidth = (width * contentScale).toInt()
        if (!(cubemap.width/div == effectiveWidth && cubemap.width/div == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch. expected: ($effectiveWidth x $effectiveHeight), got: (${cubemap.width} x ${cubemap.width})")
        }
        cubemap as CubemapGL3

        glFramebufferTexture2D(
                GL_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0 + colorAttachments.size,
                side.glTextureTarget,
                cubemap.texture,
                level)

        checkGLErrors { null }

        colorAttachments.add(CubemapAttachment(colorAttachments.size, name, cubemap, side, level))

        if (active[context]?.peek() != null)
            (active[context]?.peek() as RenderTargetGL3).bindTarget()
    }

    override fun attach(volumeTexture: VolumeTexture, layer: Int, level: Int, name: String?) {
        require(!destroyed)
        require(level >= 0 && level < volumeTexture.depth)

        val context = glfwGetCurrentContext()
        bindTarget()

        val effectiveWidth = (width * contentScale).toInt()
        val effectiveHeight = (height * contentScale).toInt()

        if (!(volumeTexture.width == effectiveWidth && volumeTexture.height == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch. expected: ($effectiveWidth x $effectiveHeight), got: (${volumeTexture.width} x ${volumeTexture.height}")
        }
        volumeTexture as VolumeTextureGL3
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + colorAttachments.size, volumeTexture.texture, level, layer)
        debugGLErrors { null }

        colorAttachments.add(VolumeTextureAttachment(colorAttachments.size, name, volumeTexture, layer, level))

        if (active[context]?.peek() != null)
            (active[context]?.peek() as RenderTargetGL3).bindTarget()
    }

    override fun attachLayered(arrayTexture: ArrayTexture, level: Int, name: String?) {
        require(!destroyed)
        val context = glfwGetCurrentContext()
        bindTarget()

        val effectiveWidth = (width * contentScale).toInt()
        val effectiveHeight = (height * contentScale).toInt()

        if (!(arrayTexture.width == effectiveWidth && arrayTexture.height == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch. expected: ($effectiveWidth x $effectiveHeight), got: (${arrayTexture.width} x ${arrayTexture.height}")
        }
        arrayTexture as ArrayTextureGL3
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + colorAttachments.size, arrayTexture.texture, level)
        debugGLErrors { null }

        colorAttachments.add(LayeredArrayTextureAttachment(colorAttachments.size, name, arrayTexture, level))

        if (active[context]?.peek() != null)
            (active[context]?.peek() as RenderTargetGL3).bindTarget()
    }

    override fun attachLayered(arrayCubemap: ArrayCubemap, level: Int, name: String?) {
        require(!destroyed)
        val context = glfwGetCurrentContext()
        bindTarget()
        val effectiveWidth = (width * contentScale).toInt()
        if (!(arrayCubemap.width == effectiveWidth && arrayCubemap.width == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch. expected: ($effectiveWidth x $effectiveHeight), got: (${arrayCubemap.width} x ${arrayCubemap.width}")
        }
        arrayCubemap as ArrayCubemapGL4
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + colorAttachments.size, arrayCubemap.texture, level)
        checkGLErrors { null }

        colorAttachments.add(LayeredArrayCubemapAttachment(colorAttachments.size, name, arrayCubemap, level))

        if (active[context]?.peek() != null)
            (active[context]?.peek() as RenderTargetGL3).bindTarget()
    }

    override fun attachLayered(cubemap: Cubemap, level: Int, name: String?) {
        val div = 1 shl level
        require(!destroyed)
        val context = glfwGetCurrentContext()
        bindTarget()
        val effectiveWidth = (width * contentScale).toInt()
        if (!(cubemap.width/div == effectiveWidth && cubemap.width/div == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch. expected: ($effectiveWidth x $effectiveHeight), got: (${cubemap.width} x ${cubemap.width})")
        }
        cubemap as CubemapGL3

        glFramebufferTexture(
                GL_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0 + colorAttachments.size,
                cubemap.texture,
                level)

        checkGLErrors { null }

        colorAttachments.add(LayeredCubemapAttachment(colorAttachments.size, name, cubemap, level))

        if (active[context]?.peek() != null)
            (active[context]?.peek() as RenderTargetGL3).bindTarget()
    }

    override fun attachLayered(volumeTexture: VolumeTexture, level: Int, name: String?) {
        require(!destroyed)
        require(level >= 0 && level < volumeTexture.depth)

        val context = glfwGetCurrentContext()
        bindTarget()

        val effectiveWidth = (width * contentScale).toInt()
        val effectiveHeight = (height * contentScale).toInt()

        if (!(volumeTexture.width == effectiveWidth && volumeTexture.height == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch. expected: ($effectiveWidth x $effectiveHeight), got: (${volumeTexture.width} x ${volumeTexture.height}")
        }
        volumeTexture as VolumeTextureGL3
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + colorAttachments.size, volumeTexture.texture, level)
        debugGLErrors { null }

        colorAttachments.add(LayeredVolumeTextureAttachment(colorAttachments.size, name, volumeTexture, level))

        if (active[context]?.peek() != null)
            (active[context]?.peek() as RenderTargetGL3).bindTarget()
    }

    override fun attach(arrayTexture: ArrayTexture, layer: Int, level: Int, name:String?) {
        require(!destroyed)
        val context = glfwGetCurrentContext()
        bindTarget()

        val effectiveWidth = (width * contentScale).toInt()
        val effectiveHeight = (height * contentScale).toInt()

        if (!(arrayTexture.width == effectiveWidth && arrayTexture.height == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch. expected: ($effectiveWidth x $effectiveHeight), got: (${arrayTexture.width} x ${arrayTexture.height}")
        }
        arrayTexture as ArrayTextureGL3
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + colorAttachments.size, arrayTexture.texture, level, layer)
        debugGLErrors { null }

        colorAttachments.add(ArrayTextureAttachment(colorAttachments.size, name, arrayTexture, layer, level))

        if (active[context]?.peek() != null)
            (active[context]?.peek() as RenderTargetGL3).bindTarget()
    }

    override fun blendMode(index: Int, blendMode: BlendMode) {
        when (blendMode) {
            BlendMode.OVER -> {
                glEnable(GL_BLEND)
                glBlendEquationi(index, GL_FUNC_ADD)
                glBlendFunci(index, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            }
            BlendMode.BLEND -> {
                glEnable(GL_BLEND)
                glBlendEquationi(index, GL_FUNC_ADD)
                glBlendFunci(index, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            }
            BlendMode.REPLACE -> {
                glEnable(GL_BLEND)
                glBlendEquationi(index, GL_FUNC_ADD)
                glBlendFunci(index, GL_ONE, GL_ZERO)
            }
            BlendMode.ADD -> {
                glEnable(GL_BLEND)
                glBlendEquationi(index, GL_FUNC_ADD)
                glBlendFunci(index, GL_ONE, GL_ONE)
            }
            else -> {
                error("unsupported blend mode: $blendMode")
            }
        }
    }

    private fun bound(function: () -> Unit) {
        bind()
        function()
        unbind()
    }

    override fun clearColor(index: Int, color: ColorRGBa) {
        require(!destroyed)
        bound {
            val ca = floatArrayOf(color.r.toFloat(), color.g.toFloat(), color.b.toFloat(), color.a.toFloat())
            glClearBufferfv(GL_COLOR, index, ca)
        }
    }

    override fun clearDepth(depth: Double, stencil: Int) {
        require(!destroyed)
        bound {
            glClearBufferfi(GL_DEPTH_STENCIL, 0, depth.toFloat(), stencil)
            checkGLErrors()
        }
    }

    override fun attach(depthBuffer: DepthBuffer) {
        require(!destroyed)
        if (!(depthBuffer.width == effectiveWidth && depthBuffer.height == effectiveHeight)) {
            throw IllegalArgumentException("buffer dimension mismatch")
        }

        if (depthBuffer.multisample != multisample) {
            throw IllegalArgumentException("buffer multisample mismatch")
        }
        bound {
            depthBuffer as DepthBufferGL3

            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthBuffer.target, depthBuffer.texture, 0)
            checkGLErrors { null }

            if (depthBuffer.hasStencil) {
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, depthBuffer.target, depthBuffer.texture, 0)
                checkGLErrors { null }
            }
            this.depthBuffer = depthBuffer
            checkFramebufferStatus()
        }
    }

    override fun detachDepthBuffer() {
        require(!destroyed)
        this.depthBuffer?.let {
            it as DepthBufferGL3
            bound {
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, it.target, 0, 0)
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, it.target, 0, 0)
                checkGLErrors()
            }
        }
    }

    private fun checkFramebufferStatus() {
        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            when (status) {
                GL_FRAMEBUFFER_UNDEFINED -> throw GL3Exception("Framebuffer undefined")
                GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> throw GL3Exception("Attachment incomplete")
                GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> throw GL3Exception("Attachment missing")
                GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER -> throw GL3Exception("Incomplete draw buffer")
            }
            throw GL3Exception("error creating framebuffer $status")
        }
        checkGLErrors()
    }

    override fun detachColorAttachments() {
        require(!destroyed)
        bound {
            for ((index, attachment) in colorAttachments.withIndex()) {
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, GL_TEXTURE_2D, 0, 0)
            }
        }
        colorAttachments.clear()
    }

    override fun detachColorBuffers() {
        detachColorAttachments()
    }

    override fun destroy() {
        session?.untrack(this)
        destroyed = true
        glDeleteFramebuffers(framebuffer)
    }

    override fun toString(): String {
        return "RenderTargetGL3(framebuffer=$framebuffer, width=$width, height=$height, contentScale=$contentScale, multisample=$multisample, session=$session, destroyed=$destroyed, colorAttachments=$colorAttachments, depthBuffer=$depthBuffer)"
    }
}

