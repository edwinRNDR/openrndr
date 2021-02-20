import org.amshove.kluent.`should be equal to`
import org.openrndr.draw.VertexElementType
import org.openrndr.draw.vertexFormat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TestVertexFormat : Spek({

    describe("a vertex format") {
        val vf = vertexFormat {
            position(3)
        }

        it("should have a size of 12 bytes") {
            vf.size `should be equal to` 12
        }
    }

    describe("a vertex format containing arrays") {
        val vf = vertexFormat {
            attribute("someArray", VertexElementType.VECTOR4_FLOAT32, 2)
        }

        it("should have a size of 36 bytes") {
            vf.size `should be equal to` 32
        }
    }

})

