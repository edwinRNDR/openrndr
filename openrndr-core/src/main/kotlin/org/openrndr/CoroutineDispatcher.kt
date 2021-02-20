package org.openrndr


import kotlinx.coroutines.*
import mu.KotlinLogging
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger {}


class Dispatcher : MainCoroutineDispatcher(), Delay {
    private val toRun = mutableListOf<Runnable>()
    private val toRunAfter = mutableListOf<Pair<Long, Runnable>>()
    private val toContinueAfter = mutableListOf<Pair<Long, CancellableContinuation<Unit>>>()

    @ExperimentalCoroutinesApi
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        synchronized(toContinueAfter) {
            logger.trace { "scheduleResume $timeMillis $continuation" }
            toContinueAfter.add(Pair(System.currentTimeMillis() + timeMillis, continuation))
        }
    }

    @ExperimentalCoroutinesApi
    override val immediate: MainCoroutineDispatcher
        get() = this

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(toRun) {
            logger.trace { "dispatching $block" }
            toRun.add(block)
        }
    }


    fun execute() {
        synchronized(toRun) {
            val copy = toRun + emptyList()
            toRun.clear()
            copy.forEach {
                logger.trace { "running $it" }
                it.run()
            }
        }

        val time = System.currentTimeMillis()
        synchronized(toRunAfter) {
            val toDo = toRunAfter.filter { it.first <= time }
            if (toDo.isNotEmpty()) {
                toRunAfter.removeAll { it.first <= time }
            }
            for ((_, runnable) in toDo) {
                logger.trace { "running $runnable" }
                runnable.run()
            }
        }

        synchronized(toContinueAfter) {
            val toDo = toContinueAfter.filter { it.first <= time }
            if (toDo.isNotEmpty()) {
                toContinueAfter.removeAll { it.first <= time }
            }
            for ((_, continuation) in toDo) {
                with(continuation) {
                    logger.trace { "resuming $continuation" }
                    resumeUndispatched(Unit)
                }
            }
        }
    }
}
fun Dispatcher.launch(start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
): Job = GlobalScope.launch(this, start, block)

