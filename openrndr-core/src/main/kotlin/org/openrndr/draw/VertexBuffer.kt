package org.openrndr.draw

import org.openrndr.internal.Driver
import java.nio.ByteBuffer

interface VertexBuffer {
    val session: Session?

    companion object {
        fun createDynamic(format: VertexFormat, vertexCount: Int, session: Session? = Session.active): VertexBuffer {
            return Driver.instance.createDynamicVertexBuffer(format, vertexCount, session)
        }

        fun createFromFloats(format: VertexFormat, data: FloatArray, session: Session?): VertexBuffer {
            require((data.size * 4) % format.size == 0) {
                "supplied data size doesn't match format size"
            }
            val vertexBuffer = createDynamic(format, (data.size * 4) / format.size, session)
            vertexBuffer.put {
                write(data)
            }
            return vertexBuffer
        }
    }

    val vertexFormat: VertexFormat
    val vertexCount: Int

    /**
     * Gives a read/write shadow for the vertex buffer
     */
    val shadow: VertexBufferShadow

    fun write(data: ByteBuffer, offset: Int = 0)
    fun read(data: ByteBuffer, offset: Int = 0)

    /**
     * Destroy the vertex buffer
     */
    fun destroy()

    fun put(elementOffset: Int = 0, putter: BufferWriter.() -> Unit): Int {
        val w = shadow.writer()
        w.rewind()
        w.positionElements = elementOffset
        w.putter()
        if (w.position % vertexFormat.size != 0) {
            throw RuntimeException("incomplete vertices written. likely violating the specified vertex format $vertexFormat")
        }
        val count = w.positionElements
        shadow.uploadElements(elementOffset, count)
        w.rewind()
        return count
    }
}

/**
 * VertexBuffer builder function.
 * @param vertexFormat a VertexFormat object that describes the vertex layout
 * @param vertexCount the number of vertices the vertex buffer should hold
 */
fun vertexBuffer(vertexFormat: VertexFormat, vertexCount: Int, session: Session? = Session.active): VertexBuffer {
    return VertexBuffer.createDynamic(vertexFormat, vertexCount, session)
}