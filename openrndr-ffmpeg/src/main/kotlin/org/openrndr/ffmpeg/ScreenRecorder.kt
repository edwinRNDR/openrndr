package org.openrndr.ffmpeg

import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.draw.*
import java.io.File
import java.time.LocalDateTime

/**
 * ScreenRecorder extension can be used to record to contents of a `Program` to a video
 */
class ScreenRecorder : Extension {
    override var enabled: Boolean = true

    private lateinit var videoWriter: VideoWriter
    private lateinit var frame: RenderTarget
    private var resolved: ColorBuffer? = null
    private var frameIndex: Long = 0

    /** the output file, auto-determined if left null */
    var outputFile: String? = null

    /** the framerate of the output video */
    var frameRate = 30

    /**
     * what time the video recorder should start recording at
     */
    var timeOffset = 0.0

    /** the profile to use for the output video */
    var profile: VideoWriterProfile = MP4Profile()

    /** should a frameclock be installed, if false system clock is used */
    var frameClock = true

    /** should multisampling be used? */
    var multisample: BufferMultisample = BufferMultisample.Disabled

    /** the maximum duration in frames */
    var maximumFrames = Long.MAX_VALUE

    /** the maximum duration in seconds */
    var maximumDuration = Double.POSITIVE_INFINITY

    /** when set to true, `program.application.exit()` will be issued after the maximum duration has been reached */
    var quitAfterMaximum = true

    var contentScale: Double = 1.0

    override fun setup(program: Program) {
        if (frameClock) {
            program.clock = {
                frameIndex / frameRate.toDouble() + timeOffset
            }
        }

        fun Int.z(zeroes: Int = 2): String {
            val sv = this.toString()
            var prefix = ""
            for (i in 0 until Math.max(zeroes - sv.length, 0)) {
                prefix += "0"
            }
            return "$prefix$sv"
        }

        val effectiveWidth = (program.width * contentScale).toInt()
        val effectiveHeight = (program.height * contentScale).toInt()

        frame = renderTarget(effectiveWidth, effectiveHeight, multisample = multisample) {
            colorBuffer()
            depthBuffer()
        }

        if (multisample != BufferMultisample.Disabled) {
            resolved = colorBuffer(effectiveWidth, effectiveHeight)
        }

        val dt = LocalDateTime.now()
        val basename = program.name.ifBlank { program.window.title.ifBlank { "untitled" } }
        val filename = outputFile
                ?: "video/$basename-${dt.year.z(4)}-${dt.month.value.z()}-${dt.dayOfMonth.z()}-${dt.hour.z()}.${dt.minute.z()}.${dt.second.z()}.${profile.fileExtension}"

        File(filename).parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        videoWriter = VideoWriter().profile(profile).output(filename).size(effectiveWidth, effectiveHeight).frameRate(frameRate).start()
    }

    override fun beforeDraw(drawer: Drawer, program: Program) {
        frame.bind()
        program.backgroundColor?.let {
            drawer.clear(it)
        }
    }

    override fun afterDraw(drawer: Drawer, program: Program) {
        frame.unbind()
        if (frameIndex < maximumFrames && frameIndex / frameRate.toDouble() < maximumDuration) {
            val lresolved = resolved
            if (lresolved != null) {
                frame.colorBuffer(0).resolveTo(lresolved)
                videoWriter.frame(lresolved)
            } else {
                videoWriter.frame(frame.colorBuffer(0))
            }

            drawer.isolated {
                drawer.defaults()

                if (lresolved != null) {
                    drawer.image(lresolved)
                } else {
                    drawer.image(frame.colorBuffer(0), 0.0, 0.0, frame.width / contentScale, frame.height / contentScale)
                }
            }
        } else {
            if (quitAfterMaximum) {
                videoWriter.stop()
                program.application.exit()
            }
        }
        frameIndex++
    }

    override fun shutdown(program: Program) {
        videoWriter.stop()
    }
}