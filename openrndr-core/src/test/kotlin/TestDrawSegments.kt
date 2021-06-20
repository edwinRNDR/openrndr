import org.openrndr.application
import org.openrndr.applicationSynchronous
import org.openrndr.math.Vector2
import org.openrndr.shape.Segment
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TestDrawSegments : Spek({
    describe("drawing multiple segments using a List of Segment") {
        it("should not throw exceptions") {
            applicationSynchronous {
                program {
                    val segments = List(10) {
                        Segment(
                            Vector2(
                                (it * 1001.0) % width,
                                (it * 1337.0) % height
                            ),
                            Vector2(
                                (it * 3333.0) % width,
                                (it * 5555.0) % height
                            ),
                            Vector2(
                                (it * 6502.0) % width,
                                (it * 4004.0) % height
                            )
                        )
                    }
                    extend {
                        drawer.segments(segments)
                        application.exit()
                    }
                }
            }
        }
    }

    describe("drawing one segment using a List of Segment") {
        it("should not throw exceptions") {
            applicationSynchronous {
                program {
                    val segments = List(1) {
                        Segment(
                            Vector2(
                                (it * 1001.0) % width,
                                (it * 1337.0) % height
                            ),
                            Vector2(
                                (it * 3333.0) % width,
                                (it * 5555.0) % height
                            ),
                            Vector2(
                                (it * 6502.0) % width,
                                (it * 4004.0) % height
                            )
                        )
                    }
                    extend {
                        drawer.segments(segments)
                        application.exit()
                    }
                }
            }
        }
    }

})
