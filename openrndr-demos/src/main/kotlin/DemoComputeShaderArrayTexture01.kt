
import org.intellij.lang.annotations.Language
import org.openrndr.application
import org.openrndr.draw.*

fun main() {
    application {
        val textureSize = 200
        val numLayers = 60

        configure {
            width = textureSize
            height = textureSize
        }

        program {
            val arrayTex = arrayTexture(
                textureSize,
                textureSize,
                numLayers,
                ColorFormat.RGBa,
                ColorType.FLOAT32
            )

            @Language("GLSL")
            val arrayComp = ComputeShader.fromCode("""
#version 450
layout(local_size_x = 1) in;
layout(rgba32f) uniform writeonly image2DArray writeTex;
uniform int numLayers;
void main() {
    for (int i = 0; i < numLayers; i++) {
        imageStore(
            writeTex,
            ivec3(gl_GlobalInvocationID.x, gl_GlobalInvocationID.y, i),
            vec4((gl_GlobalInvocationID.x + i) % numLayers == 0 ? 1.0 : 0.0,
            0.0, 0.0, 1.0));
    }
}""".trimMargin(), "arrayTextureComp")

            arrayComp.uniform("numLayers", numLayers)
            arrayComp.image(
                "writeTex",
                0,
                arrayTex.imageBinding(0, ImageAccess.WRITE))
            arrayComp.execute(textureSize, textureSize)

            extend {
                val currentLayer = frameCount % numLayers
                drawer.image(arrayTex, currentLayer)
            }
        }
    }
}