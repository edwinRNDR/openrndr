package org.openrndr

val enableProfiling = !(System.getProperty("org.openrndr.profiling").isNullOrBlank())

@PublishedApi
internal val measuredTime = mutableMapOf<String, Long>()

@PublishedApi
internal val measuredHits = mutableMapOf<String, Long>()

@PublishedApi
internal var profilingStart = -1L

@PublishedApi
internal var measureName = ""

inline fun <T> measure(name: String, crossinline f: () -> T): T {
    if (false) {
        val previousName = measureName
        measureName = "${previousName}/$name"

        if (profilingStart == -1L) {
            profilingStart = System.nanoTime()
        }
        val start = System.nanoTime()

        val result = f()

        val end = System.nanoTime()

        measuredTime[measureName] = measuredTime.getOrDefault(measureName, 0L) + (end - start)
        measuredHits[measureName] = measuredHits.getOrDefault(measureName, 0L) + 1
        measureName = previousName
        return result
    } else {
        return f()
    }
}

fun report() {
    val profilingEnd = System.nanoTime()
    val profilingRuntime = (profilingEnd - profilingStart)/1E6
    println("total runtime: $profilingRuntime")
    println("--------------------------------------")
    for (name in measuredTime.keys) {
        val timeMS = measuredTime[name]!!/1E6
        val timePercentage = 100.0 * (timeMS/profilingRuntime)
        val timePercentageClean = String.format("%.2f", timePercentage)

        val average = (measuredTime[name]!!.toDouble() / measuredHits[name]!!.toDouble()) / 1000000.0

        val spacedName = String.format("%-100s", name)

        println("$spacedName ${measuredTime[name]!! / 1E6}\t${measuredHits[name]}\t${average}ms")
    }
}