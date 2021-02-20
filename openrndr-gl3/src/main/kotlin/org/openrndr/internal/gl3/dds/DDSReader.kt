package org.openrndr.internal.gl3.dds

/**
 * DDS reader
 * This started as a copy of https://github.com/Mudbill/dds-lwjgl @ c16616c07c79c38a552cbbb4f46011c3ee601223
 * I subsequently converted it to Kotlin, simplified loader flow and added partial support for DXGI formats
 * E. Jakobs
 */

import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import java.io.InputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val DDPF_ALPHAPIXELS = 0x1
private const val DDPF_ALPHA = 0x2
private const val DDPF_FOURCC = 0x4
private const val DDPF_RGB = 0x40
private const val DDPF_YUV = 0x200
private const val DDPF_LUMINANCE = 0x20000

class DDSPixelFormat(header: ByteBuffer) {
    var dwSize: Int = 0
    var dwFlags: Int = 0
    var dwFourCC: Int = 0
    var dwRGBBitCount: Int = 0
    var dwRBitMask: Int = 0
    var dwGBitMask: Int = 0
    var dwBBitMask: Int = 0
    var dwABitMask: Int = 0
    var sFourCC: String
    var isCompressed: Boolean = false
    var hasFlagAlphaPixels: Boolean = false
    var hasFlagAlpha: Boolean = false
    var hasFlagFourCC: Boolean = false
    var hasFlagRgb: Boolean = false
    var hasFlagYuv: Boolean = false
    var hasFlagLuminance: Boolean = false

    init {
        dwSize = header.int
        dwFlags = header.int
        dwFourCC = header.int
        dwRGBBitCount = header.int
        dwRBitMask = header.int
        dwGBitMask = header.int
        dwBBitMask = header.int
        dwABitMask = header.int

        if (dwSize != 32) throw RuntimeException("size is not 32 bytes (is $dwSize bytes)")

        hasFlagAlphaPixels = dwFlags and DDPF_ALPHAPIXELS == DDPF_ALPHAPIXELS
        hasFlagAlpha = dwFlags and DDPF_ALPHA == DDPF_ALPHA
        hasFlagFourCC = (dwFlags and DDPF_FOURCC) == DDPF_FOURCC
        hasFlagRgb = dwFlags and DDPF_RGB == DDPF_RGB
        hasFlagYuv = dwFlags and DDPF_YUV == DDPF_YUV
        hasFlagLuminance = dwFlags and DDPF_LUMINANCE == DDPF_LUMINANCE

        sFourCC = if (hasFlagFourCC) createFourCCString(dwFourCC) else ""

        if (hasFlagFourCC) {
            isCompressed = true
        } else if (hasFlagRgb) {
            isCompressed = false
        }
    }

    private fun createFourCCString(fourCC: Int): String {
        val fourCCString = ByteArray(DDPF_FOURCC)
        for (i in fourCCString.indices) fourCCString[i] = (fourCC shr i * 8).toByte()
        return String(fourCCString)
    }
}


private const val DDS_RESOURCE_MISC_TEXTURECUBE = 0x4

private const val DDS_DIMENSION_TEXTURE1D = 2
private const val DDS_DIMENSION_TEXTURE2D = 3
private const val DDS_DIMENSION_TEXTURE3D = 4

private const val DDS_ALPHA_MODE_UNKNOWN = 0x0
private const val DDS_ALPHA_MODE_STRAIGHT = 0x1
private const val DDS_ALPHA_MODE_PREMULTIPLIED = 0x2
private const val DDS_ALPHA_MODE_OPAQUE = 0x3
private const val DDS_ALPHA_MODE_CUSTOM = 0x4

private const val DXGI_FORMAT_UNKNOWN = 0
private const val DXGI_FORMAT_R32G32B32A32_TYPELESS = 1
private const val DXGI_FORMAT_R32G32B32A32_FLOAT = 2
private const val DXGI_FORMAT_R32G32B32A32_UINT = 3
private const val DXGI_FORMAT_R32G32B32A32_SINT = 4
private const val DXGI_FORMAT_R32G32B32_TYPELESS = 5
private const val DXGI_FORMAT_R32G32B32_FLOAT = 6
private const val DXGI_FORMAT_R32G32B32_UINT = 7
private const val DXGI_FORMAT_R32G32B32_SINT = 8
private const val DXGI_FORMAT_R16G16B16A16_TYPELESS = 9
private const val DXGI_FORMAT_R16G16B16A16_FLOAT = 10
private const val DXGI_FORMAT_R16G16B16A16_UNORM = 11
private const val DXGI_FORMAT_R16G16B16A16_UINT = 12
private const val DXGI_FORMAT_R16G16B16A16_SNORM = 13
private const val DXGI_FORMAT_R16G16B16A16_SINT = 14
private const val DXGI_FORMAT_R32G32_TYPELESS = 15
private const val DXGI_FORMAT_R32G32_FLOAT = 16
private const val DXGI_FORMAT_R32G32_UINT = 17
private const val DXGI_FORMAT_R32G32_SINT = 18
private const val DXGI_FORMAT_R32G8X24_TYPELESS = 19
private const val DXGI_FORMAT_D32_FLOAT_S8X24_UINT = 20
private const val DXGI_FORMAT_R32_FLOAT_X8X24_TYPELESS = 21
private const val DXGI_FORMAT_X32_TYPELESS_G8X24_UINT = 22
private const val DXGI_FORMAT_R10G10B10A2_TYPELESS = 23
private const val DXGI_FORMAT_R10G10B10A2_UNORM = 24
private const val DXGI_FORMAT_R10G10B10A2_UINT = 25
private const val DXGI_FORMAT_R11G11B10_FLOAT = 26
private const val DXGI_FORMAT_R8G8B8A8_TYPELESS = 27
private const val DXGI_FORMAT_R8G8B8A8_UNORM = 28
private const val DXGI_FORMAT_R8G8B8A8_UNORM_SRGB = 29
private const val DXGI_FORMAT_R8G8B8A8_UINT = 30
private const val DXGI_FORMAT_R8G8B8A8_SNORM = 31
private const val DXGI_FORMAT_R8G8B8A8_SINT = 32
private const val DXGI_FORMAT_R16G16_TYPELESS = 33
private const val DXGI_FORMAT_R16G16_FLOAT = 34
private const val DXGI_FORMAT_R16G16_UNORM = 35
private const val DXGI_FORMAT_R16G16_UINT = 36
private const val DXGI_FORMAT_R16G16_SNORM = 37
private const val DXGI_FORMAT_R16G16_SINT = 38
private const val DXGI_FORMAT_R32_TYPELESS = 39
private const val DXGI_FORMAT_D32_FLOAT = 40
private const val DXGI_FORMAT_R32_FLOAT = 41
private const val DXGI_FORMAT_R32_UINT = 42
private const val DXGI_FORMAT_R32_SINT = 43
private const val DXGI_FORMAT_R24G8_TYPELESS = 44
private const val DXGI_FORMAT_D24_UNORM_S8_UINT = 45
private const val DXGI_FORMAT_R24_UNORM_X8_TYPELESS = 46
private const val DXGI_FORMAT_X24_TYPELESS_G8_UINT = 47
private const val DXGI_FORMAT_R8G8_TYPELESS = 48
private const val DXGI_FORMAT_R8G8_UNORM = 49
private const val DXGI_FORMAT_R8G8_UINT = 50
private const val DXGI_FORMAT_R8G8_SNORM = 51
private const val DXGI_FORMAT_R8G8_SINT = 52
private const val DXGI_FORMAT_R16_TYPELESS = 53
private const val DXGI_FORMAT_R16_FLOAT = 54
private const val DXGI_FORMAT_D16_UNORM = 55
private const val DXGI_FORMAT_R16_UNORM = 56
private const val DXGI_FORMAT_R16_UINT = 57
private const val DXGI_FORMAT_R16_SNORM = 58
private const val DXGI_FORMAT_R16_SINT = 59
private const val DXGI_FORMAT_R8_TYPELESS = 60
private const val DXGI_FORMAT_R8_UNORM = 61
private const val DXGI_FORMAT_R8_UINT = 62
private const val DXGI_FORMAT_R8_SNORM = 63
private const val DXGI_FORMAT_R8_SINT = 64
private const val DXGI_FORMAT_A8_UNORM = 65
private const val DXGI_FORMAT_R1_UNORM = 66
private const val DXGI_FORMAT_R9G9B9E5_SHAREDEXP = 67
private const val DXGI_FORMAT_R8G8_B8G8_UNORM = 68
private const val DXGI_FORMAT_G8R8_G8B8_UNORM = 69
private const val DXGI_FORMAT_BC1_TYPELESS = 70
private const val DXGI_FORMAT_BC1_UNORM = 71
private const val DXGI_FORMAT_BC1_UNORM_SRGB = 72
private const val DXGI_FORMAT_BC2_TYPELESS = 73
private const val DXGI_FORMAT_BC2_UNORM = 74
private const val DXGI_FORMAT_BC2_UNORM_SRGB = 75
private const val DXGI_FORMAT_BC3_TYPELESS = 76
private const val DXGI_FORMAT_BC3_UNORM = 77
private const val DXGI_FORMAT_BC3_UNORM_SRGB = 78
private const val DXGI_FORMAT_BC4_TYPELESS = 79
private const val DXGI_FORMAT_BC4_UNORM = 80
private const val DXGI_FORMAT_BC4_SNORM = 81
private const val DXGI_FORMAT_BC5_TYPELESS = 82
private const val DXGI_FORMAT_BC5_UNORM = 83
private const val DXGI_FORMAT_BC5_SNORM = 84
private const val DXGI_FORMAT_B5G6R5_UNORM = 85
private const val DXGI_FORMAT_B5G5R5A1_UNORM = 86
private const val DXGI_FORMAT_B8G8R8A8_UNORM = 87
private const val DXGI_FORMAT_B8G8R8X8_UNORM = 88
private const val DXGI_FORMAT_R10G10B10_XR_BIAS_A2_UNORM = 89
private const val DXGI_FORMAT_B8G8R8A8_TYPELESS = 90
private const val DXGI_FORMAT_B8G8R8A8_UNORM_SRGB = 91
private const val DXGI_FORMAT_B8G8R8X8_TYPELESS = 92
private const val DXGI_FORMAT_B8G8R8X8_UNORM_SRGB = 93
private const val DXGI_FORMAT_BC6H_TYPELESS = 94
private const val DXGI_FORMAT_BC6H_UF16 = 95
private const val DXGI_FORMAT_BC6H_SF16 = 96
private const val DXGI_FORMAT_BC7_TYPELESS = 97
private const val DXGI_FORMAT_BC7_UNORM = 98
private const val DXGI_FORMAT_BC7_UNORM_SRGB = 99
private const val DXGI_FORMAT_AYUV = 100
private const val DXGI_FORMAT_Y410 = 101
private const val DXGI_FORMAT_Y416 = 102
private const val DXGI_FORMAT_NV12 = 103
private const val DXGI_FORMAT_P010 = 104
private const val DXGI_FORMAT_P016 = 105
private const val DXGI_FORMAT_420_OPAQUE = 106
private const val DXGI_FORMAT_YUY2 = 107
private const val DXGI_FORMAT_Y210 = 108
private const val DXGI_FORMAT_Y216 = 109
private const val DXGI_FORMAT_NV11 = 110
private const val DXGI_FORMAT_AI44 = 111
private const val DXGI_FORMAT_IA44 = 112
private const val DXGI_FORMAT_P8 = 113
private const val DXGI_FORMAT_A8P8 = 114
private const val DXGI_FORMAT_B4G4R4A4_UNORM = 115
private const val DXGI_FORMAT_P208 = 130
private const val DXGI_FORMAT_V208 = 131
private const val DXGI_FORMAT_V408 = 132
private const val DXGI_FORMAT_FORCE_UINT = 0xffffffff

private class DDSHeaderDXT10(header: ByteBuffer) {
    var dxgiFormat: Int = 0
    var resourceDimension: Int = 0
    var miscFlag: Int = 0
    var arraySize: Int = 0
    var miscFlags2: Int = 0

    init {
        dxgiFormat = header.int
        resourceDimension = header.int
        miscFlag = header.int
        arraySize = header.int
        miscFlags2 = header.int
    }
}

private const val DDSD_CAPS = 0x000001
private const val DDSD_HEIGHT = 0x000002
private const val DDSD_WIDTH = 0x000004
private const val DDSD_PITCH = 0x000008
private const val DDSD_PIXELFORMAT = 0x001000
private const val DDSD_MIPMAPCOUNT = 0x020000
private const val DDSD_LINEARSIZE = 0x080000
private const val DDSD_DEPTH = 0x800000

private const val DDSCAPS_COMPLEX = 0x8
private const val DDSCAPS_MIPMAP = 0x400000
private const val DDSCAPS_TEXTURE = 0x1000

private const val DDSCAPS2_CUBEMAP = 0x200
private const val DDSCAPS2_CUBEMAP_POSITIVEX = 0x400
private const val DDSCAPS2_CUBEMAP_NEGATIVEX = 0x800
private const val DDSCAPS2_CUBEMAP_POSITIVEY = 0x1000
private const val DDSCAPS2_CUBEMAP_NEGATIVEY = 0x2000
private const val DDSCAPS2_CUBEMAP_POSITIVEZ = 0x4000
private const val DDSCAPS2_CUBEMAP_NEGATIVEZ = 0x8000
private const val DDSCAPS2_VOLUME = 0x200000

private class DDSHeader(header: ByteBuffer) {
    var size: Int = 0
    var flags: Int = 0
    var height: Int = 0
    var width: Int = 0
    var pitchOrLinearSize: Int = 0
    var depth: Int = 0
    var mipmapCount: Int = 0
    var reserved = IntArray(11)
    var pixelFormat: DDSPixelFormat
    var caps: Int = 0
    var caps2: Int = 0
    var caps3: Int = 0
    var caps4: Int = 0
    var reserved2: Int = 0
    var hasFlagMipMapCount: Boolean = false
    var hasFlagCaps: Boolean = false
    var hasFlagHeight: Boolean = false
    var hasFlagWidth: Boolean = false
    var hasFlagPitch: Boolean = false
    var hasFlagPixelFormat: Boolean = false
    var hasFlagLinearSize: Boolean = false
    var hasFlagDepth: Boolean = false
    var hasCapsComplex: Boolean = false
    var hasCapsMipMap: Boolean = false
    var hasCapsTexture: Boolean = false
    var hasCaps2CubeMap: Boolean = false
    var hasCaps2CubeMapPX: Boolean = false
    var hasCaps2CubeMapNX: Boolean = false
    var hasCaps2CubeMapPY: Boolean = false
    var hasCaps2CubeMapNY: Boolean = false
    var hasCaps2CubeMapPZ: Boolean = false
    var hasCaps2CubeMapNZ: Boolean = false
    var hasCaps2Volume: Boolean = false

    init {
        if (header.capacity() != 124) {
            throw RuntimeException("Expected header size of 124 bytes (is ${header.capacity()} bytes")
        }

        size = header.getInt()
        flags = header.getInt()
        height = header.getInt()
        width = header.getInt()
        pitchOrLinearSize = header.getInt()
        depth = header.getInt()
        mipmapCount = header.getInt()

        for (i in reserved.indices) {
            reserved[i] = header.getInt()
        }

        pixelFormat = DDSPixelFormat(header)

        caps = header.getInt()
        caps2 = header.getInt()
        caps3 = header.getInt()
        caps4 = header.getInt()
        reserved2 = header.getInt()

        hasFlagCaps = flags and DDSD_CAPS == DDSD_CAPS
        hasFlagHeight = flags and DDSD_HEIGHT == DDSD_HEIGHT
        hasFlagWidth = flags and DDSD_WIDTH == DDSD_WIDTH
        hasFlagPitch = flags and DDSD_PITCH == DDSD_PITCH
        hasFlagPixelFormat = flags and DDSD_PIXELFORMAT == DDSD_PIXELFORMAT
        hasFlagMipMapCount = flags and DDSD_MIPMAPCOUNT == DDSD_MIPMAPCOUNT
        hasFlagLinearSize = flags and DDSD_LINEARSIZE == DDSD_LINEARSIZE
        hasFlagDepth = flags and DDSD_DEPTH == DDSD_DEPTH

        hasCapsComplex = caps and DDSCAPS_COMPLEX == DDSCAPS_COMPLEX
        hasCapsMipMap = caps and DDSCAPS_MIPMAP == DDSCAPS_MIPMAP
        hasCapsTexture = caps and DDSCAPS_TEXTURE == DDSCAPS_TEXTURE

        hasCaps2CubeMap = caps2 and DDSCAPS2_CUBEMAP == DDSCAPS2_CUBEMAP
        hasCaps2CubeMapPX = caps2 and DDSCAPS2_CUBEMAP_POSITIVEX == DDSCAPS2_CUBEMAP_POSITIVEX
        hasCaps2CubeMapNX = caps2 and DDSCAPS2_CUBEMAP_NEGATIVEX == DDSCAPS2_CUBEMAP_NEGATIVEX
        hasCaps2CubeMapPY = caps2 and DDSCAPS2_CUBEMAP_POSITIVEY == DDSCAPS2_CUBEMAP_POSITIVEY
        hasCaps2CubeMapNY = caps2 and DDSCAPS2_CUBEMAP_NEGATIVEY == DDSCAPS2_CUBEMAP_NEGATIVEY
        hasCaps2CubeMapPZ = caps2 and DDSCAPS2_CUBEMAP_POSITIVEZ == DDSCAPS2_CUBEMAP_POSITIVEZ
        hasCaps2CubeMapNZ = caps2 and DDSCAPS2_CUBEMAP_NEGATIVEZ == DDSCAPS2_CUBEMAP_NEGATIVEZ
        hasCaps2Volume = caps2 and DDSCAPS2_VOLUME == DDSCAPS2_VOLUME

        if (!hasFlagCaps || !hasFlagHeight || !hasFlagWidth || !hasFlagPixelFormat) {
            throw  RuntimeException("missing required flags")
        }
        if (!hasCapsTexture) {
            throw RuntimeException("missing required caps")
        }
    }

    override fun toString(): String {
        return "DDSHeader(size=$size, flags=$flags, height=$height, width=$width, pitchOrLinearSize=$pitchOrLinearSize, depth=$depth, mipmapCount=$mipmapCount, reserved=${reserved.contentToString()}, pixelFormat=$pixelFormat, caps=$caps, caps2=$caps2, caps3=$caps3, caps4=$caps4, reserved2=$reserved2, hasFlagMipMapCount=$hasFlagMipMapCount, hasFlagCaps=$hasFlagCaps, hasFlagHeight=$hasFlagHeight, hasFlagWidth=$hasFlagWidth, hasFlagPitch=$hasFlagPitch, hasFlagPixelFormat=$hasFlagPixelFormat, hasFlagLinearSize=$hasFlagLinearSize, hasFlagDepth=$hasFlagDepth, hasCapsComplex=$hasCapsComplex, hasCapsMipMap=$hasCapsMipMap, hasCapsTexture=$hasCapsTexture, hasCaps2CubeMap=$hasCaps2CubeMap, hasCaps2CubeMapPX=$hasCaps2CubeMapPX, hasCaps2CubeMapNX=$hasCaps2CubeMapNX, hasCaps2CubeMapPY=$hasCaps2CubeMapPY, hasCaps2CubeMapNY=$hasCaps2CubeMapNY, hasCaps2CubeMapPZ=$hasCaps2CubeMapPZ, hasCaps2CubeMapNZ=$hasCaps2CubeMapNZ, hasCaps2Volume=$hasCaps2Volume)"
    }

}

class DDSData(val format: ColorFormat, val type: ColorType, val width: Int, val height: Int, val mipmaps: Int, val cubeMap: Boolean, val bdata: List<ByteBuffer>, val bdata2: List<ByteBuffer>, val flipV: Boolean) {
    fun image(level: Int): ByteBuffer {
        return if (level == 0) bdata[0] else bdata2[level - 1]
    }

    fun sidePX(level: Int = 0): ByteBuffer = if (level == 0) bdata[0] else bdata2[(level - 1) + 0 * (mipmaps - 1)]
    fun sideNX(level: Int = 0): ByteBuffer = if (level == 0) bdata[1] else bdata2[(level - 1) + 1 * (mipmaps - 1)]
    fun sidePY(level: Int = 0): ByteBuffer = if (level == 0) bdata[2] else bdata2[(level - 1) + 2 * (mipmaps - 1)]
    fun sideNY(level: Int = 0): ByteBuffer = if (level == 0) bdata[3] else bdata2[(level - 1) + 3 * (mipmaps - 1)]
    fun sidePZ(level: Int = 0): ByteBuffer = if (level == 0) bdata[4] else bdata2[(level - 1) + 4 * (mipmaps - 1)]
    fun sideNZ(level: Int = 0): ByteBuffer = if (level == 0) bdata[5] else bdata2[(level - 1) + 5 * (mipmaps - 1)]
}

private const val DDS_MAGIC = 0x20534444

fun loadDDS(file: InputStream): DDSData {
    val ba = ByteArray(file.available())
    return loadDDS(newByteBuffer(ba))
}

fun loadDDS(data: ByteBuffer): DDSData {
    val primarySurfaces = mutableListOf<ByteBuffer>()
    val secondarySurfaces = mutableListOf<ByteBuffer>()

    run {
        val fis = data
        var totalByteCount = fis.capacity()
        val bMagic = ByteArray(4)
        fis.get(bMagic)

        val magic = newByteBuffer(bMagic).int
        if (magic != DDS_MAGIC) {
            throw RuntimeException("mismatch in magic word, not a dds file")
        }

        val bHeader = ByteArray(124)
        fis.get(bHeader)
        val header = DDSHeader(newByteBuffer(bHeader))

        val format: ColorFormat
        val type: ColorType

        if (header.pixelFormat.sFourCC.equals("DXT1", ignoreCase = true)) {
            type = ColorType.DXT1
            format = if (header.pixelFormat.hasFlagAlpha) {
                ColorFormat.RGBa
            } else {
                ColorFormat.RGB
            }
        } else if (header.pixelFormat.sFourCC.equals("DXT3", ignoreCase = true)) {
            type = ColorType.DXT3
            format = ColorFormat.RGBa
        } else if (header.pixelFormat.sFourCC.equals("DXT5", ignoreCase = true)) {
            type = ColorType.DXT5
            format = ColorFormat.RGBa
        } else if (header.pixelFormat.sFourCC.equals("DX10", ignoreCase = true)) {
            val dxt10HeaderArray = ByteArray(20)
            fis.get(dxt10HeaderArray)
            val dxt10Header = DDSHeaderDXT10(newByteBuffer(dxt10HeaderArray))

            when (dxt10Header.dxgiFormat) {
                DXGI_FORMAT_R16G16B16A16_FLOAT -> {
                    format = ColorFormat.RGBa
                    type = ColorType.FLOAT16
                }
                DXGI_FORMAT_R32G32B32A32_FLOAT -> {
                    format = ColorFormat.RGBa
                    type = ColorType.FLOAT32
                }
                else -> {
                    error("unsupported dxgi format: ${dxt10Header.dxgiFormat}")
                }
            }
        } else if (header.pixelFormat.dwRGBBitCount == 24 && header.pixelFormat.dwRBitMask == (0xff0000) && header.pixelFormat.dwGBitMask == 0x00ff00 && header.pixelFormat.dwBBitMask == 0x0000ff) {
            format = ColorFormat.BGR
            type = ColorType.UINT8
        } else {
            throw RuntimeException("unknown and/or unsupported format ${header.pixelFormat.sFourCC}")
        }

        val surfaceCount: Int
        totalByteCount -= 128

        val isCubeMap: Boolean
        if (header.hasCaps2CubeMap) {
            surfaceCount = 6
            isCubeMap = true
        } else {
            surfaceCount = 1
            isCubeMap = false
        }

        fun size(level:Int) : Int {
            val div = (1 shl level)
            val width = header.width / div
            val height = header.height / div
            return when (type) {
                ColorType.DXT1 -> (width * height) / 2
                ColorType.DXT3, ColorType.DXT5 -> (width * height)
                else -> (header.pitchOrLinearSize * header.height) shl level
            }
        }
        val primarySize = size(0)

        require(primarySize > 0) {
            """size of surface is 0 bytes $header"""
        }
        for (i in 0 until surfaceCount) {
            require(fis.remaining() >= primarySize) {
                "source byte buffer only has ${fis.remaining()} bytes left, need $primarySize, $format/$type"
            }
            val bytes = ByteArray(primarySize)
            fis.get(bytes)
            totalByteCount -= bytes.size
            primarySurfaces.add(newByteBuffer(bytes))

            if (header.hasFlagMipMapCount) {
                for (level in 1 until header.mipmapCount) {
                    val secondarySize = size(level)
                    val bytes2 = ByteArray(secondarySize)
                    fis.get(bytes2)
                    totalByteCount -= bytes2.size
                    secondarySurfaces.add(newByteBuffer(bytes2))
                }
            }
        }
        return DDSData(format, type, header.width, header.height, header.mipmapCount, isCubeMap, primarySurfaces, secondarySurfaces, true)
    }
}

private fun newByteBuffer(data: ByteArray): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder())
    buffer.put(data)
    (buffer as Buffer).flip()
    return buffer
}
