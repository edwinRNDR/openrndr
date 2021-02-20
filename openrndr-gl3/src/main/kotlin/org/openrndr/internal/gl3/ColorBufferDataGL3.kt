package org.openrndr.internal.gl3

import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyexr.EXRHeader
import org.lwjgl.util.tinyexr.EXRImage
import org.lwjgl.util.tinyexr.EXRVersion
import org.lwjgl.util.tinyexr.TinyEXR
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import org.openrndr.draw.ImageFileFormat
import org.openrndr.internal.gl3.dds.loadDDS
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.*

class ColorBufferDataGL3(val width: Int, val height: Int, val format: ColorFormat, val type: ColorType, val flipV: Boolean, var data: ByteBuffer?, var destroyFunction: ((ByteBuffer) -> Unit)? = null) {
    fun destroy() {
        val localData = data
        if (localData != null) {
            destroyFunction?.invoke(localData)
            //STBImage.stbi_image_free(localData)
            data = null
        }
    }

    companion object {
        fun fromUrl(urlString: String, formatHint: ImageFileFormat?): ColorBufferDataGL3 {
            if (urlString.startsWith("data:")) {
                val decoder = Base64.getDecoder()
                val commaIndex = urlString.indexOf(",")
                val base64Data = urlString.drop(commaIndex + 1).replace("\n","")
                val decoded = decoder.decode(base64Data)
                val buffer = ByteBuffer.allocateDirect(decoded.size)
                buffer.put(decoded)
                (buffer as Buffer).rewind()
                return fromByteBuffer(buffer, "data-url", formatHint = formatHint)
            } else {
                val url = URL(urlString)
                url.openStream().use {
                    val byteArray = url.readBytes()
                    if (byteArray.isEmpty()) {
                        throw RuntimeException("read 0 bytes from stream $urlString")
                    }
                    val buffer = BufferUtils.createByteBuffer(byteArray.size)
                    (buffer as Buffer).rewind()
                    buffer.put(byteArray)
                    (buffer as Buffer).rewind()
                    return fromByteBuffer(buffer, urlString)
                }
            }
        }

        fun fromStream(stream: InputStream, name: String? = null, formatHint: ImageFileFormat? = null): ColorBufferDataGL3 {
            val byteArray = stream.readBytes()
            val buffer = BufferUtils.createByteBuffer(byteArray.size)
            (buffer as Buffer).rewind()
            buffer.put(byteArray)
            (buffer as Buffer).rewind()
            return fromByteBuffer(buffer, name, formatHint)
        }

        fun fromArray(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size, name: String? = null, formatHint: ImageFileFormat? = null): ColorBufferDataGL3 {
            val buffer = ByteBuffer.allocateDirect(length)
            buffer.put(bytes, offset, length)
            return fromByteBuffer(buffer, name, formatHint)
        }

        fun fromByteBuffer(buffer: ByteBuffer, name: String? = null, formatHint: ImageFileFormat? = null): ColorBufferDataGL3 {
            var assumedFormat = ImageFileFormat.PNG

            if (formatHint != null) {
                assumedFormat = formatHint
            }
            (buffer as Buffer).mark()

            if (assumedFormat == ImageFileFormat.PNG || assumedFormat == ImageFileFormat.JPG) {
                val wa = IntArray(1)
                val ha = IntArray(1)
                val ca = IntArray(1)

                STBImage.stbi_set_flip_vertically_on_load(true)
                STBImage.stbi_set_unpremultiply_on_load(false)

                val bitsPerChannel = if (assumedFormat == ImageFileFormat.PNG) {
                    if (STBImage.stbi_is_16_bit_from_memory(buffer)) {
                        16
                    } else {
                        8
                    }
                } else {
                    8
                }
                (buffer as Buffer).reset()

                val targetType: ColorType
                val mask: Int
                val alphaOffset = 3
                val redOffset = 0
                val greenOffset = 1
                val blueOffset = 2
                when (bitsPerChannel) {
                    8 -> {
                        targetType = ColorType.UINT8
                        mask = 0xff
                    }
                    16 -> {
                        targetType = ColorType.UINT16
                        mask = 0xffff
                    }
                    else -> error("unsupported bits per channel: $bitsPerChannel")
                }

                val (tdata8, tdata16) = when (bitsPerChannel) {
                    8 -> Pair(STBImage.stbi_load_from_memory(buffer, wa, ha, ca, 0)
                            ?:error("stbi_load returned null"), null as ShortBuffer?)
                    16 -> Pair(null as ByteBuffer?, STBImage.stbi_load_16_from_memory(buffer, wa, ha, ca, 0)
                            ?:error("stbi_load returned null"))
                    else -> error("unsupported bits per channel: $bitsPerChannel")
                }

                if (tdata8 != null) {
                    var offset = 0
                    if (ca[0] == 4) {
                        for (y in 0 until ha[0]) {
                            for (x in 0 until wa[0]) {
                                val a = (tdata8.get(offset + alphaOffset).toInt() and mask).toDouble() / mask.toDouble()
                                val r = ((tdata8.get(offset + redOffset).toInt() and mask) * a)
                                val g = ((tdata8.get(offset + greenOffset).toInt() and mask) * a)
                                val b = ((tdata8.get(offset + blueOffset).toInt() and mask) * a)
                                tdata8.put(offset + redOffset, r.toInt().toByte())
                                tdata8.put(offset + greenOffset, g.toInt().toByte())
                                tdata8.put(offset + blueOffset, b.toInt().toByte())
                                offset += 4
                            }
                        }
                    }
                }
                val data8 = tdata8?.let {
                    if (ca[0] == 1) {
                        var roffset = 0
                        var woffset = 0
                        val data8 = ByteBuffer.allocateDirect(tdata8.capacity() * 3)
                        for (y in 0 until ha[0]) {
                            for (x in 0 until wa[0]) {
                                val r = tdata8.get(roffset)
                                data8.put(woffset + 0, r)
                                data8.put(woffset + 1, r)
                                data8.put(woffset + 2, r)
                                roffset ++
                                woffset += 3
                            }
                        }
                        data8
                    } else {
                        tdata8
                    }
                }

                if (tdata16 != null) {
                    var offset = 0
                    if (ca[0] == 4) {
                        for (y in 0 until ha[0]) {
                            for (x in 0 until wa[0]) {
                                val a = (tdata16.get(offset + alphaOffset).toInt() and mask).toDouble() / mask.toDouble()
                                val r = ((tdata16.get(offset + redOffset).toInt() and mask) * a)
                                val g = ((tdata16.get(offset + greenOffset).toInt() and mask) * a)
                                val b = ((tdata16.get(offset + blueOffset).toInt() and mask) * a)
                                tdata16.put(offset + redOffset, r.toInt().toShort())
                                tdata16.put(offset + greenOffset, g.toInt().toShort())
                                tdata16.put(offset + blueOffset, b.toInt().toShort())
                                offset += 4
                            }
                        }
                    }
                }

                val data16 = tdata16

                val copyData = (data8?.let { MemoryUtil.memAlloc(it.capacity()) } ?: data16?.let { MemoryUtil.memAlloc(it.capacity() * 2) })
                        ?: error("alloc failed, data8: ${data8}, data16: ${data16}, $assumedFormat, $bitsPerChannel")

                val source = data8?.let { MemoryUtil.memAddress(it) } ?: data16?.let { MemoryUtil.memAddress(it) }
                ?: error("get address failed")
                val dest = MemoryUtil.memAddress(copyData)
                MemoryUtil.memCopy(source, dest, copyData.capacity().toLong())

                tdata8?.let { STBImage.stbi_image_free(it) }
                tdata16?.let { STBImage.stbi_image_free(it) }

                return ColorBufferDataGL3(wa[0], ha[0],
                        when (ca[0]) {
                            1 -> ColorFormat.RGB
                            2 -> ColorFormat.RG
                            3 -> ColorFormat.RGB
                            4 -> ColorFormat.RGBa
                            else -> throw Exception("invalid component count ${ca[0]}")
                        },
                        targetType, false, copyData) { b -> MemoryUtil.memFree(b) }

            } else if (assumedFormat == ImageFileFormat.DDS) {
                val data = loadDDS(buffer)
                val buffer = data.image(0)
                require(buffer.remaining() > 0) {
                    "image buffer $buffer has no remaining bytes"
                }
                return ColorBufferDataGL3(data.width, data.height,data.format, data.type, data.flipV, data.image(0))

            } else if (assumedFormat == ImageFileFormat.EXR) {
                val exrHeader = EXRHeader.create()
                val exrVersion = EXRVersion.create()
                val versionResult = TinyEXR.ParseEXRVersionFromMemory(exrVersion, buffer)
                (buffer as Buffer).rewind()

                if (versionResult != TinyEXR.TINYEXR_SUCCESS) {
                    error("failed to get version")
                }

                val errors = PointerBuffer.allocateDirect(1)

                val parseResult = TinyEXR.ParseEXRHeaderFromMemory(exrHeader, exrVersion, buffer, errors)
                if (parseResult != TinyEXR.TINYEXR_SUCCESS) {
                    error("failed to parse file")
                }

                for (i in 0 until exrHeader.num_channels()) {
                    exrHeader.requested_pixel_types().put(i, exrHeader.pixel_types().get(i))
                }

                val exrImage = EXRImage.create()
                TinyEXR.InitEXRImage(exrImage)
                TinyEXR.LoadEXRImageFromMemory(exrImage, exrHeader, buffer, errors)

                val format =
                        when (val c = exrImage.num_channels()) {
                            1 -> ColorFormat.R
                            3 -> ColorFormat.RGB
                            4 -> ColorFormat.RGBa
                            else -> error("unsupported number of channels $c")
                        }

                val type = when (val t = exrHeader.requested_pixel_types().get(0)) {
                    TinyEXR.TINYEXR_PIXELTYPE_HALF -> ColorType.FLOAT16
                    TinyEXR.TINYEXR_PIXELTYPE_FLOAT -> ColorType.FLOAT32
                    else -> error("unsupported pixel type [type=$t]")
                }

                val height = exrImage.height()
                val width = exrImage.width()
                val channels = exrImage.num_channels()

                val data = ByteBuffer.allocateDirect(format.componentCount * type.componentSize * exrImage.width() * exrImage.height()).order(ByteOrder.nativeOrder())
                val channelNames = (0 until exrHeader.num_channels()).map { exrHeader.channels().get(it).nameString() }
                val images = exrImage.images()!!
                val channelImages = (0 until exrHeader.num_channels()).map { images.getByteBuffer(it, width * height * type.componentSize) }

                val order = when (format) {
                    ColorFormat.R -> listOf("R").map { channelNames.indexOf(it) }
                    ColorFormat.RGB -> listOf("B", "G", "R").map { channelNames.indexOf(it) }
                    ColorFormat.RGBa -> listOf("B", "G", "R", "A").map { channelNames.indexOf(it) }
                    else -> error("unsupported channel layout")
                }
                require(order.none { it == -1 }) { "some channels are not found" }

                val orderedImages = order.map { channelImages[it] }
                orderedImages.forEach { (it as Buffer).rewind() }

                for (y in 0 until exrImage.height()) {
                    val offset = (height - 1 - y) * format.componentCount * type.componentSize * width
                    (data as Buffer).position(offset)
                    for (x in 0 until exrImage.width()) {
                        for (c in 0 until channels) {
                            for (i in 0 until type.componentSize) {
                                data.put(orderedImages[c].get())
                            }
                        }
                    }
                }
                (data as Buffer).rewind()

                TinyEXR.FreeEXRHeader(exrHeader)
                TinyEXR.FreeEXRImage(exrImage)
                return ColorBufferDataGL3(exrImage.width(), exrImage.height(), format, type, false, data)
            } else {
                error("format not supported")
            }
        }

        fun fromFile(filename: String): ColorBufferDataGL3 {
            val file = File(filename)

            val byteArray = file.readBytes()
            if (byteArray.isEmpty()) {
                throw RuntimeException("read 0 bytes from stream $filename")
            }
            val buffer = BufferUtils.createByteBuffer(byteArray.size)
            (buffer as Buffer).rewind()
            buffer.put(byteArray)
            (buffer as Buffer).rewind()

            return fromByteBuffer(buffer, filename, formatHint = ImageFileFormat.guessFromExtension(file))
        }
    }
}