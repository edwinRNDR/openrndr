package org.openrndr.draw

import org.openrndr.color.ColorRGBa
import org.openrndr.internal.Driver
import org.openrndr.math.Vector2
import org.openrndr.shape.IntRectangle
import org.openrndr.shape.Rectangle
import java.io.File
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer

/**
 * File format used while saving to file
 */
enum class ImageFileFormat(val mimeType: String, val extensions: List<String>) {
    JPG("image/jpeg", listOf("jpg", "jpeg")),
    PNG("image/png", listOf("png")),
    DDS("image/vnd.ms-dds", listOf("dds")),
    EXR("image/x-exr", listOf("exr"));

    companion object {
        fun guessFromExtension(file: File): ImageFileFormat {
            return when (val extension = file.extension.toLowerCase()) {
                "jpg", "jpeg" -> JPG
                "png" -> PNG
                "dds" -> DDS
                "exr" -> EXR
                else -> throw IllegalArgumentException("unsupported format: \"$extension\"")
            }
        }
    }
}

/**
 * Texture wrapping mode
 */
enum class WrapMode {
    CLAMP_TO_EDGE,
    REPEAT,
    MIRRORED_REPEAT
}

/**
 * Texture filters used for minification
 */
enum class MinifyingFilter {
    NEAREST,
    LINEAR,
    NEAREST_MIPMAP_NEAREST,
    LINEAR_MIPMAP_NEAREST,
    NEAREST_MIPMAP_LINEAR,
    LINEAR_MIPMAP_LINEAR
}

/**
 * Texture filters for magnification
 */
enum class MagnifyingFilter {
    /** nearest neighbour, blocky */
    NEAREST,
    LINEAR
}

/**
 * Buffer multisample options
 */
sealed class BufferMultisample {
    /**
     * Disable multisampling
     */
    object Disabled : BufferMultisample() {
        override fun toString(): String {
            return "Disabled()"
        }
    }

    /**
     * Enable multisampling with a given [sampleCount]
     */
    data class SampleCount(val sampleCount: Int) : BufferMultisample()
}

/**
 * representation for simple images stored on GPU memory
 *
 * [ColorBuffer] is a unmanaged GPU resource, the user is responsible for destroying a [ColorBuffer] once it is no
 * longer used.
 */
interface ColorBuffer {
    val session: Session?

    /** the width of the [ColorBuffer] in device units */
    val width: Int

    /** the height of the [ColorBuffer] in device units */
    val height: Int

    /** the content scale of the [ColorBuffer] */
    val contentScale: Double

    /**
     * the [ColorFormat] of the image stored in the [ColorBuffer]
     */
    val format: ColorFormat

    /**
     * the [ColorType] of the image stored in the [ColorBuffer]
     */
    val type: ColorType

    /** the number of mipmap levels */
    val levels: Int

    /** the multisampling method used for this [ColorBuffer] */
    val multisample: BufferMultisample
    val bounds: Rectangle get() = Rectangle(Vector2.ZERO, width * 1.0, height * 1.0)

    /** the width of the [ColorBuffer] in pixels */
    val effectiveWidth: Int get() = (width * contentScale).toInt()

    /** the height of the [ColorBuffer] in pixels */
    val effectiveHeight: Int get() = (height * contentScale).toInt()

    /** return the buffer size in bytes */
    fun bufferSize(level: Int = 0) : Long  {
        val baseSize = ((effectiveWidth * effectiveHeight) shr level).toLong()
        return when (type) {
            ColorType.DXT1 -> (baseSize) / 2
            ColorType.DXT3, ColorType.DXT5 -> baseSize
            ColorType.BPTC_FLOAT -> TODO()
            ColorType.BPTC_UFLOAT -> TODO()
            ColorType.BPTC_UNORM -> TODO()
            else -> baseSize * format.componentCount * type.componentSize
        }
    }

    /** save the [ColorBuffer] to [File] */
    fun saveToFile(file: File, imageFileFormat: ImageFileFormat = ImageFileFormat.guessFromExtension(file), async: Boolean = true)

    /** return a base64 data url representation */
    fun toDataUrl(imageFileFormat: ImageFileFormat = ImageFileFormat.JPG): String

    /** permanently destroy the underlying [ColorBuffer] resources, [ColorBuffer] can not be used after it is destroyed */
    fun destroy()

    /** bind the colorbuffer to a texture unit, internal API */
    fun bind(unit: Int)
    /**
     * write the contents from [sourceBuffer] to the [ColorBuffer], potentially with format and type conversions
     *
     * The [sourceBuffer] should be allocated using [ByteBuffer.allocateDirect] and have an amount of remaining bytes
     * that matches with the dimensions, [sourceFormat] and [sourceType].
     * @param sourceBuffer a [ByteBuffer] holding raw image data
     * @param sourceFormat the [ColorFormat] that is used for the image data stored in [sourceBuffer], default is [ColorBuffer.format]
     * @param sourceType the [ColorType] that is used for the image data stored in [sourceBuffer], default is [ColorBuffer.type]
     * @param level the mipmap-level of [ColorBuffer] to write to
     */
    fun write(sourceBuffer: ByteBuffer, sourceFormat: ColorFormat = format, sourceType: ColorType = type, level: Int = 0)
    /**
     * read the contents of the [ColorBuffer] and write to [targetBuffer], potentially with format and type conversions
     * @param targetBuffer a [ByteBuffer] to which the contents of the [ColorBuffer] will be written
     * @param targetFormat the [ColorFormat] that is used for the image data stored in [targetBuffer], default is [ColorBuffer.format]
     * @param targetType the [ColorType] that is used for the image data stored in [targetBuffer], default is [ColorBuffer.type]
     * @param level the mipmap-level of [ColorBuffer] to read from
     */
    fun read(targetBuffer: ByteBuffer, targetFormat: ColorFormat = format, targetType: ColorType = type, level: Int = 0)
    /**
     * create a cropped copy of the [ColorBuffer]
     * @param sourceRectangle
     */
    fun crop(sourceRectangle: IntRectangle): ColorBuffer {
        val cropped = createEquivalent(width = sourceRectangle.width, height = sourceRectangle.height)
        copyTo(cropped, sourceRectangle = sourceRectangle)
        return cropped
    }
    /** generates mipmaps from the top-level mipmap */
    fun generateMipmaps()

    /**
     * resolves contents to a non-multisampled color buffer
     */
    @Deprecated("functionality is merged into copyTo",
        ReplaceWith("copyTo(target, fromLevel, toLevel)")
    )
    fun resolveTo(target: ColorBuffer, fromLevel: Int = 0, toLevel: Int = 0) {
        copyTo(target, fromLevel, toLevel)
    }

    /**
     * copies contents to a target color buffer
     * @param target the color buffer to which contents will be copied
     * @param fromLevel the mip-map level from which will be copied
     * @param toLevel the mip-map level of [target] to which will be copied
     * @param sourceRectangle rectangle in pixel units that specifies where to read from
     * @param targetRectangle rectangle in pixel units that specifies where to write to
     * @param filter filter to use for copying
     */
    fun copyTo(
            target: ColorBuffer,
            fromLevel: Int = 0,
            toLevel: Int = 0,
            sourceRectangle: IntRectangle = IntRectangle(
                    0,
                    0,
                    this.effectiveWidth / (1 shl fromLevel),
                    this.effectiveHeight / (1 shl fromLevel)
            ),
            targetRectangle: IntRectangle = IntRectangle(
                    0,
                    0,
                    sourceRectangle.width,
                    sourceRectangle.height
            ),
            filter: MagnifyingFilter = MagnifyingFilter.NEAREST
    )

    /**
     * copies contents to a target array texture
     * @param target the color buffer to which contents will be copied
     * @param layer the array layer from which will be copied
     * @param fromLevel the mip-map level from which will be copied
     * @param toLevel the mip-map level of [target] to which will be copied
     */
    fun copyTo(target: ArrayTexture, layer: Int, fromLevel: Int = 0, toLevel: Int = 0)

    /**
     * sets all pixels in the color buffer to [color]
     * @param color the color used for filling
     */
    fun fill(color: ColorRGBa)

    /** the wrapping mode to use in the horizontal direction */
    var wrapU: WrapMode

    /** the wrapping mode to use in the vertical direction */
    var wrapV: WrapMode

    /** the filter to use when displaying at sizes smaller than the original */
    var filterMin: MinifyingFilter

    /** the filter to use when display at sizes larger than the original */
    var filterMag: MagnifyingFilter

    val shadow: ColorBufferShadow

    /** the (unitless?) degree of anisotropy to be used in filtering */
    var anisotropy: Double

    /**
     * should the v coordinate be flipped because the [ColorBuffer] contents are stored upside-down?
     */
    var flipV: Boolean

    /**
     * sets the [ColorBuffer] filter for minifying and magnification
     */
    fun filter(filterMin: MinifyingFilter, filterMag: MagnifyingFilter) {
        this.filterMin = filterMin
        this.filterMag = filterMag
    }

    /**
     * check if this [ColorBuffer] is equivalent to [other]
     * @param other the [ColorBuffer] to check against
     * @param ignoreWidth ignore [ColorBuffer.width] in check when true
     * @param ignoreHeight ignore [ColorBuffer.height] in check when true
     * @param ignoreLevels ignore [ColorBuffer.levels] in check when true
     * @param ignoreContentScale ignore [ColorBuffer.contentScale] when true
     * @param ignoreMultisample ignore [ColorBuffer.multisample] in check when true
     * @param ignoreLevels ignore [ColorBuffer.levels] in check when true
     */
    fun isEquivalentTo(other: ColorBuffer,
                       ignoreWidth: Boolean = false,
                       ignoreHeight: Boolean = false,
                       ignoreContentScale: Boolean = false,
                       ignoreFormat: Boolean = false,
                       ignoreType: Boolean = false,
                       ignoreMultisample: Boolean = false,
                       ignoreLevels: Boolean = false): Boolean {
        return (ignoreWidth || width == other.width) &&
                (ignoreHeight || height == other.height) &&
                (ignoreContentScale || contentScale == other.contentScale) &&
                (ignoreFormat || format == other.format) &&
                (ignoreType || type == other.type) &&
                (ignoreMultisample || multisample == other.multisample) &&
                (ignoreLevels || levels == other.levels)
    }

    /**
     * create an equivalent [ColorBuffer], with the option to override attributes
     */
    fun createEquivalent(width: Int = this.width,
                         height: Int = this.height,
                         contentScale: Double = this.contentScale,
                         format: ColorFormat = this.format,
                         type: ColorType = this.type,
                         multisample: BufferMultisample = this.multisample,
                         levels: Int = this.levels): ColorBuffer {
        return colorBuffer(width, height, contentScale, format, type, multisample, levels)
    }

    companion object {
        /**
         * create a [ColorBuffer] from a [File] containing a formatted image
         * @param url the location of a formatted image
         * @param formatHint an optional [ImageFileFormat] hint, default is null
         * @param session the [Session] under which the [ColorBuffer] should be created, default is [Session.active]
         * @see loadImage
         */
        fun fromUrl(url: String, formatHint: ImageFileFormat? = null, session: Session? = Session.active): ColorBuffer {
            return Driver.instance.createColorBufferFromUrl(
                url, formatHint, session)
        }

        /**
         * create a [ColorBuffer] from a [File] containing a formatted image
         * @param file a [File] containing a formatted image
         * @param formatHint an optional [ImageFileFormat] hint, default is null
         * @param session the [Session] under which the [ColorBuffer] should be created, default is [Session.active]
         * @see loadImage
         */
        fun fromFile(file: File, formatHint: ImageFileFormat? = null, session: Session? = Session.active): ColorBuffer {
            return Driver.instance.createColorBufferFromFile(file.absolutePath,
                formatHint = formatHint, session)
        }

        /**
         * create a [ColorBuffer] from a file indicated by [filename] containing a formatted image
         * @param filename a file containing a formatted image
         * @param formatHint an optional [ImageFileFormat] hint, default is null
         * @param session the [Session] under which the [ColorBuffer] should be created, default is [Session.active]
         * @see loadImage
         */
        fun fromFile(filename: String, formatHint: ImageFileFormat?, session: Session? = Session.active): ColorBuffer {
            return Driver.instance.createColorBufferFromFile(filename,
                formatHint = formatHint, session)
        }

        /**
         * create a [ColorBuffer] from an [InputStream] containing a formatted image
         * @param stream an [InputStream] holding a formatted image
         * @param formatHint optional [ImageFileFormat] hint, default is null
         * @param session the [Session] under which to create this [ColorBuffer]
         */
        @Suppress("UNUSED_PARAMETER")
        fun fromStream(stream: InputStream, formatHint: ImageFileFormat? = null, session: Session? = Session.active): ColorBuffer {
            return Driver.instance.createColorBufferFromStream(
                    stream,
                    formatHint = formatHint,
                    session = session
            )
        }

        /**
         * create a [ColorBuffer] from a [ByteArray] containing a formatted image (meaning any of the formats in [ImageFileFormat])
         * @param bytes a [ByteArray] containing a formatted image
         * @param offset offset used for reading from [bytes], default is 0
         * @param length number of bytes to be used from [bytes], default is bytes.size
         * @param formatHint an optional [ImageFileFormat] hint, default is null
         */
        @Suppress("UNUSED_PARAMETER")
        fun fromArray(
                bytes: ByteArray,
                offset: Int = 0,
                length: Int = bytes.size,
                formatHint: ImageFileFormat?,
                session: Session? = Session.active
        ): ColorBuffer {
            return Driver.instance.createColorBufferFromArray(bytes,
                    offset,
                    length,
                    formatHint = formatHint,
                    session = session)
        }

        /**
         * create a [ColorBuffer] from a [ByteBuffer] holding a formatted image (meaning any of the formats in [ImageFileFormat]
         * @param bytes a [ByteBuffer] containing a formatted image
         * @param formatHint an optional [ImageFileFormat] hint
         * @param session the [Session] under which this [ColorBuffer] should be created, default is [Session.active]
         */
        fun fromBuffer(bytes: ByteBuffer, formatHint: ImageFileFormat?, session: Session? = Session.active): ColorBuffer {
            return Driver.instance.createColorBufferFromBuffer(
                bytes, session = session)
        }
    }
}

/**
 * create a [ColorBuffer]
 * @param width the width in device units
 * @param height the height in device units
 * @param contentScale content scale used for denoting hi-dpi content
 * @param format the color format
 * @param type the color type
 * @param multisample multisampling mode to use
 * @param format the color format
 * @param levels the number of mip-map levels
 * @param session the [Session] that should track this color buffer
 */
fun colorBuffer(
        width: Int,
        height: Int,
        contentScale: Double = 1.0,
        format: ColorFormat = ColorFormat.RGBa,
        type: ColorType = ColorType.UINT8,
        multisample: BufferMultisample = BufferMultisample.Disabled,
        levels: Int = 1,
        session: Session? = Session.active
): ColorBuffer {
    return Driver.instance.createColorBuffer(width, height, contentScale, format, type, multisample, levels, session)
}

/**
 * load an image from a file or url encoded as [String], also accepts base64 encoded data urls
 */
fun loadImage(fileOrUrl: String, formatHint: ImageFileFormat? = null, session: Session? = Session.active): ColorBuffer {
    return try {
        if (!fileOrUrl.startsWith("data:")) {
            URL(fileOrUrl)
        }
        ColorBuffer.fromUrl(fileOrUrl, formatHint, session)
    } catch (e: MalformedURLException) {
        loadImage(File(fileOrUrl), formatHint, session)
    }
}
/**
 * load an image from [File]
 */
fun loadImage(file: File, formatHint: ImageFileFormat? = null, session: Session? = Session.active): ColorBuffer {
    require(file.exists()) {
        "failed to load image: file '${file.absolutePath}' does not exist."
    }
    return ColorBuffer.fromFile(file, formatHint, session)
}
/**
 * load an image from an [url]
 */
fun loadImage(url: URL, formatHint: ImageFileFormat?, session: Session? = Session.active): ColorBuffer {
    return ColorBuffer.fromUrl(url.toExternalForm(), formatHint, session)
}