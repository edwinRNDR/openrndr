package org.openrndr.ffmpeg

import mu.KotlinLogging
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVBufferRef
import org.bytedeco.ffmpeg.avutil.AVHWDeviceContext
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.PointerPointer
import org.openrndr.platform.Platform
import org.openrndr.platform.PlatformType
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

internal data class CodecInfo(val video: VideoInfo?, val audio: AudioInfo?) {
    val hasVideo = video != null
    val hasAudio = audio != null
}

internal fun Int.checkAVError() {
    if (this != 0) {
        throw Error("AVError $this: ${toAVError()}")
    }
}

internal fun Int.toAVError(): String {
    return if (this != 0) {
        val buffer = BytePointer(1024L)
        av_strerror(this, buffer, 1024L)
        val errorLength = BytePointer.strlen(buffer).toInt()
        buffer.string.substring(0, errorLength)
    } else {
        ""
    }
}

internal val flushPacket = AVPacket().apply {
    data(BytePointer("FLUSH"))
}

internal class Decoder(private val statistics: VideoStatistics,
                       private val configuration: VideoPlayerConfiguration,
                       private val formatContext: AVFormatContext,
                       private val videoStreamIndex: Int,
                       private val audioStreamIndex: Int,
                       private val videoCodecContext: AVCodecContext?,
                       private val audioCodecContext: AVCodecContext?,
                       private val hwType: Int
) {
    companion object {
        fun fromContext(statistics: VideoStatistics, configuration: VideoPlayerConfiguration, context: AVFormatContext, useVideo: Boolean, useAudio: Boolean): Pair<Decoder, CodecInfo> {
            val videoStreamIndex =
                    if (configuration.legacyStreamOpen) {
                        if (useVideo) context.codecs.indexOfFirst { it?.codec_type() == AVMEDIA_TYPE_VIDEO } else -1
                    } else {
                        av_find_best_stream(context, AVMEDIA_TYPE_VIDEO, -1, -1, null as AVCodec?, 0)
                    }
            val audioStreamIndex =
                    if (configuration.legacyStreamOpen) {
                        if (useAudio) context.codecs.indexOfFirst { it?.codec_type() == AVMEDIA_TYPE_AUDIO } else -1
                    } else {
                        av_find_best_stream(context, AVMEDIA_TYPE_AUDIO, -1, -1, null as AVCodec?, 0)
                    }

            val videoStream = context.streamAt(videoStreamIndex)
            val audioStream = if (useAudio) context.streamAt(audioStreamIndex) else null

            val videoContext = videoStream?.openCodec()
            val audioContext = audioStream?.openCodec()
            var hwType = AV_HWDEVICE_TYPE_NONE
            if (configuration.useHardwareDecoding && videoContext != null) {
                val preferredHW = when (Platform.type) {
                    PlatformType.WINDOWS -> arrayListOf(AV_HWDEVICE_TYPE_D3D11VA, AV_HWDEVICE_TYPE_DXVA2, AV_HWDEVICE_TYPE_QSV)
                    PlatformType.MAC -> arrayListOf(AV_HWDEVICE_TYPE_VIDEOTOOLBOX)
                    PlatformType.GENERIC -> arrayListOf(AV_HWDEVICE_TYPE_VAAPI)
                }.reversed()

                val foundHW = mutableListOf<Int>()
                var next = AV_HWDEVICE_TYPE_NONE
                do {
                    next = av_hwdevice_iterate_types(next)
                    if (next != 0) {
                        foundHW.add(next)
                    }
                } while (next != AV_HWDEVICE_TYPE_NONE)

                hwType = (foundHW.map { Pair(it, preferredHW.indexOf(it)) })
                    .filter { it.second >= 0 }
                    .maxByOrNull { it.second }
                    ?.first ?: AV_HWDEVICE_TYPE_NONE

                if (hwType != AV_HWDEVICE_TYPE_NONE) {
                    val hwContextPtr = PointerPointer<AVHWDeviceContext>(1)
                    val name = av_hwdevice_get_type_name(hwType).getString()
                    logger.debug { "creating hw device context (type: $name)" }
                    av_hwdevice_ctx_create(hwContextPtr, hwType, null, null, 0).checkAVError()
                    val hwContext = AVHWDeviceContext(hwContextPtr[0])
                    videoContext.hw_device_ctx(av_buffer_ref(AVBufferRef(hwContext)))
                }
            }

            // Extract video info.
            val video = videoContext?.run {
                VideoInfo(Dimensions(width(), height()), av_q2d((videoStream.r_frame_rate())))
            }
            // Extract audio info.
            val audio = audioContext?.run {
                AudioInfo(sample_rate(), channels())
            }
            return Pair(Decoder(statistics, configuration, context, videoStreamIndex, audioStreamIndex, videoContext, audioContext, hwType), CodecInfo(video, audio))
        }
    }

    private var videoDecoder: VideoDecoder? = null
    private var audioDecoder: AudioDecoder? = null
    private var disposed = false
    private var packetReader: PacketReader? = null

    var displayQueueFull: () -> Boolean = { false }
    var audioOutQueueFull: () -> Boolean = { false }
    var seekCompleted: () -> Unit = { }
    var reachedEndOfFile: () -> Unit = { }
    var lastPacketReceived = 0L

    fun start(videoOutput: VideoDecoderOutput?, audioOutput: AudioDecoderOutput?) {
        videoDecoder = videoCodecContext?.let { ctx ->
            videoOutput?.let { VideoDecoder(statistics, configuration, ctx, it, hwType) }
        }

        audioDecoder = audioCodecContext?.let { ctx ->
            audioOutput?.let {
                AudioDecoder(ctx, it)
            }
        }

        if (configuration.usePacketReaderThread) {
            packetReader = PacketReader(configuration, formatContext, statistics)
            thread(isDaemon = true) {
                packetReader?.start()
            }
        }
        while (!disposed) {
            decodeIfNeeded()
            Thread.sleep(0)
        }
        logger.debug {
            "decoder loop ended"
        }
    }

    fun restart() {
        seek(0.0)
    }

    private var needFlush = false
    private var seekRequested = false
    private var seekPosition: Double = 0.0

    var seekTimedOut = false

    fun seek(positionInSeconds: Double) {
        seekPosition = positionInSeconds
        seekRequested = true
    }

    fun done() = (packetReader?.endOfFile == true) && (packetReader?.isQueueEmpty() == true) && (videoDecoder?.isQueueEmpty()
            ?: true)

    fun dispose() {
        logger.debug { "disposing decoder" }
        disposed = true
        videoDecoder?.dispose()
        audioDecoder?.dispose()
        packetReader?.dispose()
    }

    private fun needMoreFrames(): Boolean =
            !seekRequested && ((videoDecoder?.needMoreFrames() ?: false) || (audioDecoder?.needMoreFrames() ?: false))
                    && !audioOutQueueFull() && !displayQueueFull()

    private fun decodeIfNeeded() {
        val hasSeeked = seekRequested

        if (seekRequested) {
            videoDecoder?.flushQueue()
            audioDecoder?.flushQueue()
            packetReader?.flushQueue()

            logger.debug { "seeking to $seekPosition" }
            val seekTS = (seekPosition * AV_TIME_BASE).toLong()
            val seekMinTS = ((seekPosition + configuration.minimumSeekOffset) * AV_TIME_BASE).toLong()
            val seekMaxTS = ((seekPosition + configuration.maximumSeekOffset) * AV_TIME_BASE).toLong()
            val seekStarted = System.currentTimeMillis()
            val seekResult = avformat_seek_file(formatContext, -1, seekMinTS, seekTS, seekMaxTS, if (configuration.allowArbitrarySeek) AVSEEK_FLAG_ANY else 0)
            logger.debug { "seek completed in ${System.currentTimeMillis() - seekStarted}ms" }
            if (seekResult != 0) {
                logger.error { "seek failed" }
            }
            needFlush = true
            seekRequested = false
        }

        if (needFlush) {
            needFlush = false
            videoDecoder?.flushBuffers()
            audioDecoder?.flushBuffers()
        }

        var packetsReceived = 0
        while (needMoreFrames() && !disposed) {
            if (videoDecoder?.isQueueAlmostFull() == true) {
                logger.warn { "video queue is almost full. [video queue: ${videoDecoder?.queueCount()}, audio queue: ${audioDecoder?.queueCount()}]" }
                Thread.sleep(100)
                return
            }

            if (audioDecoder?.isQueueAlmostFull() == true) {
                logger.warn { "audio queue is almost full. [video queue: ${videoDecoder?.queueCount()}, audio queue: ${audioDecoder?.queueCount()}]" }
                Thread.sleep(100)
                return
            }

            val packet = if (packetReader != null) packetReader?.nextPacket() else av_packet_alloc()
            val packetResult = av_read_frame(formatContext, packet)

            if (packetResult == AVERROR_EOF) {
                reachedEndOfFile()
                Thread.sleep(50)
            }

            if (packet != null) {
                lastPacketReceived = System.currentTimeMillis()
                packetsReceived++
                if (hasSeeked && packetsReceived == 1) {
                    logger.debug { "seek completed" }
                    seekCompleted()
                }

                if (hasSeeked && packetsReceived == 2) {
                    val packetTime = packet.dts() / 1000.0
                    logger.debug { "seek error: ${packetTime - seekPosition}" }
                }


                when (packet.stream_index()) {
                    videoStreamIndex -> videoDecoder?.decodeVideoPacket(packet)
                    audioStreamIndex -> audioDecoder?.decodeAudioPacket(packet)
                }
                av_packet_unref(packet)
            } else {
                if (packetReader?.endOfFile == true) {
                    Thread.sleep(100)
                } else {
                    logger.debug { "more frames are needed but none are received" }
                    Thread.sleep(10)
                }
            }
        }
    }

    fun peekNextVideoFrame(): VideoFrame? {
        return videoDecoder?.peekNextFrame()
    }

    fun nextVideoFrame(): VideoFrame? {
        return videoDecoder?.nextFrame()
    }

    fun nextAudioFrame(): AudioFrame? {
        return audioDecoder?.nextFrame()
    }

    fun audioQueue(): Queue<AudioFrame>? {
        return audioDecoder?.audioQueue
    }

    fun videoQueueSize(): Int {
        return videoDecoder?.queueCount() ?: 0
    }

    fun audioQueueSize(): Int {
        return audioDecoder?.queueCount() ?: 0
    }

    fun audioVideoSynced() = (audioDecoder?.isSynced() ?: true) || done()
}