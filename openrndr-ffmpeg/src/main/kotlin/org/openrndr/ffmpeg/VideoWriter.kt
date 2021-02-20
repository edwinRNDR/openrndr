package org.openrndr.ffmpeg

import mu.KotlinLogging
import org.lwjgl.BufferUtils
import org.openrndr.draw.ColorBuffer
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import java.util.*

private val logger = KotlinLogging.logger {}

abstract class VideoWriterProfile {
    abstract fun arguments(): Array<String>
    abstract val fileExtension: String
}


val File.fileWithoutExtension: File
    get() {
        val p = parent
        return if (p == null) {
            File(nameWithoutExtension)
        } else {
            File(p, nameWithoutExtension)
        }
    }

class MP4Profile : VideoWriterProfile() {
    private var mode = WriterMode.Normal
    private var constantRateFactor = null as Int?

    override val fileExtension = "mp4"

    enum class WriterMode {
        Normal,
        Lossless
    }

    fun mode(mode: WriterMode): MP4Profile {
        this.mode = mode
        return this
    }

    /**
     * Sets the constant rate factor
     * @param constantRateFactor the constant rate factor (default is 23)
     * @return
     */
    fun constantRateFactor(constantRateFactor: Int?): MP4Profile {
        this.constantRateFactor = constantRateFactor
        return this
    }

    var highPrecisionChroma = true

    val CODEC_LIBX264 = "libx264"
    val CODEC_H264_NVENC = "h264_nvenc"

    var videoCodec = CODEC_LIBX264 as String?
    var hwaccel = null as String?
    var preset = null as String?
    var pixelFormat = "yuv420p" as String?
    var userArguments = emptyArray<String>()

    override fun arguments(): Array<String> {
        val filters = mutableListOf<String>()

        filters.add("vflip")

        val chromaArguments = if (highPrecisionChroma) {
            arrayOf("-sws_flags", "spline+accurate_rnd+full_chroma_int", "-color_range", "1", "-colorspace", "1", "-color_primaries", "1", "-color_trc", "1")
        } else {
            emptyArray()
        }

        if (highPrecisionChroma) {
            filters.add("colorspace=bt709:iall=bt601-6-625:fast=1")
        }

        val hwaccelArguments = hwaccel?.let { arrayOf("-hwaccel", it) } ?: emptyArray()
        val pixelFormatArguments = pixelFormat?.let { arrayOf("-pix_fmt", it) } ?: emptyArray()
        val constantRateArguments = constantRateFactor?.let { arrayOf("-crf", it.toString()) } ?: emptyArray()
        val presetArguments = preset?.let { arrayOf("-preset", it) } ?: emptyArray()
        val videoCodecArguments = videoCodec?.let { arrayOf("-vcodec", it) } ?: emptyArray()
        val filterArguments = arrayOf("-vf", filters.joinToString(","))


        return hwaccelArguments + pixelFormatArguments + chromaArguments +
                filterArguments + videoCodecArguments + constantRateArguments +
                presetArguments + userArguments

//        return when (mode) {
//            WriterMode.Normal -> arrayOf("-pix_fmt", "yuv420p", // this will produce videos that are playable by quicktime
//                    "-an", "-vcodec", codec, "-crf", "" + constantRateFactor) + chromaArguments
//            WriterMode.Lossless -> {
//                arrayOf("-pix_fmt", "yuv420p", // this will produce videos that are playable by quicktime
//                        "-an", "-vcodec", codec, "-preset", "llhp") + chromaArguments
//            }
//        }
    }
}

class VideoWriter {
    internal var ffmpegOutput = File("ffmpegOutput.txt")
    private var stopped = false

    private var frameRate = 25
    private var width = -1
    private var height = -1

    private var filename: String = "openrndr.mp4"

    private lateinit var frameBuffer: ByteBuffer
    private lateinit var channel: WritableByteChannel
    private var ffmpeg: Process? = null
    private var movieStream: OutputStream? = null

    private var profile: VideoWriterProfile = MP4Profile()

    var inputFormat = "rgba"

    fun profile(profile: VideoWriterProfile): VideoWriter {
        this.profile = profile
        return this
    }

    fun width(): Int {
        return width
    }

    fun height(): Int {
        return height
    }

    fun size(width: Int, height: Int): VideoWriter {
        if (width % 2 != 0 || height % 2 != 0) {
            throw IllegalArgumentException("width ($width) and height ($height) should be divisible by 2")
        }
        this.width = width
        this.height = height
        return this
    }

    /**
     * Set the output file, should be set before calling start()
     *
     * @param filename the filename of the output file
     */
    fun output(filename: String): VideoWriter {
        this.filename = filename
        return this
    }

    /**
     * Sets the framerate of the output video
     *
     * @param frameRate the frame rate in frames per second
     * @return this
     */
    fun frameRate(frameRate: Int): VideoWriter {
        this.frameRate = frameRate
        return this
    }

    /**
     * Start writing to the video file
     */
    fun start(): VideoWriter {
        logger.debug { "starting video writer with $width x $height output using $inputFormat writing to $filename" }

        val file = File(filename)
        val finalFilename = if (file.extension != profile.fileExtension) {
            "${file.fileWithoutExtension}.${profile.fileExtension}"
        } else {
            filename
        }

        if (width <= 0) {
            throw RuntimeException("invalid width or width not set $width")
        }
        if (height <= 0) {
            throw RuntimeException("invalid height or height not set $height")
        }

        //frameBufferArray = ByteArray(width * height * 4)
        frameBuffer = when (inputFormat) {
            "rgba" -> BufferUtils.createByteBuffer(width * height * 4)
            "rgba64le" -> BufferUtils.createByteBuffer(width * height * 8)
            else -> throw RuntimeException("unsupported format $inputFormat")
        }

        val preamble = arrayOf("-y", "-f", "rawvideo", "-vcodec", "rawvideo",
                "-s", String.format("%dx%d", width, height), "-pix_fmt", inputFormat, "-r", "" + frameRate, "-i", "-")

        val codec = profile.arguments()
        val arguments = ArrayList<String>()

        if (System.getProperty("os.name").contains("Windows")) {
            arguments.add("ffmpeg.exe")
        } else {
            arguments.add("ffmpeg")
        }
        arguments.addAll(listOf(*preamble))
        arguments.addAll(listOf(*codec))

        arguments.add(finalFilename)

        logger.debug {
            "using arguments: ${arguments.joinToString()}"
        }

        val pb = ProcessBuilder().command(*arguments.toTypedArray())
        pb.redirectErrorStream(true)
        pb.redirectOutput(ffmpegOutput)

        try {
            ffmpeg = pb.start()
            movieStream = ffmpeg!!.outputStream
            channel = Channels.newChannel(movieStream)
            return this
        } catch (e: IOException) {
            System.err.println("system path: ${System.getenv("path")}")
            System.err.println("command: ${arguments.joinToString(" ")}")
            throw RuntimeException("failed to launch ffmpeg", e)
        }
    }

    /**
     * Feed a frame to the video encoder
     *
     * @param frame a ColorBuffer (RGBA, 8bit) holding the image data to be written to the video. The ColorBuffer should have the same resolution as the VideoWriter.
     */
    fun frame(frame: ColorBuffer): VideoWriter {
        if (!stopped) {
            val frameBytes = frame.width * frame.height * frame.format.componentCount * frame.type.componentSize
            require(frameBytes == frameBuffer.capacity()) {
                "frame size/format/type mismatch"
            }
            (frameBuffer as Buffer).rewind()
            //frameBuffer.order(ByteOrder.nativeOrder())
            frame.read(frameBuffer)
            (frameBuffer as Buffer).rewind()
            try {
                channel.write(frameBuffer)

                //movieStream!!.flush()
            } catch (e: IOException) {
                e.printStackTrace()
                throw RuntimeException("failed to write frame", e)
            }
        } else {
            logger.warn { "ignoring frame after VideoWriter stop" }
        }
        return this
    }

    /**
     * Stop writing to the video file. This closes the video, after calling stop() it is no longer possible to provide new frames.
     */
    fun stop(): VideoWriter {
        stopped = true
        try {
            movieStream!!.close()
            try {
                logger.info("waiting for ffmpeg to finish")
                ffmpeg!!.waitFor()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        } catch (e: IOException) {
            throw RuntimeException("failed to close the movie stream")
        }

        return this
    }

    companion object {
        fun create(): VideoWriter {
            return VideoWriter()
        }
    }
}
