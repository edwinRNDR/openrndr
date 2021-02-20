package org.openrndr.exceptions

import mu.KotlinLogging
import org.openrndr.platform.Platform
import org.openrndr.platform.PlatformType
import java.lang.Thread.UncaughtExceptionHandler

private val logger = KotlinLogging.logger(name = "")


private class SanitizingUncaughtExceptionHandler : UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) { // Write the custom logic here
        findUserCause(e)
    }
}

fun installUncaughtExceptionHandler() {
    if (Platform.type != PlatformType.WINDOWS) {
        System.err.print(color(0x7f, 0x7f, 0x7f))
        if (System.getProperty("org.openrndr.exceptions") != "JVM") {
            Thread.setDefaultUncaughtExceptionHandler(SanitizingUncaughtExceptionHandler())
        }
    }
}


private fun color(r: Int, g: Int, b: Int): String {
    return String.format("\u001b[38;2;%d;%d;%dm", r, g, b)
}

private fun colorReset(): String {
    return "\u001b[0m"
}

private fun cleanClassName(name: String): String {
    return name.replace(("Kt$"), ".").replace(Regex("\\$[0-9]+"), ".{ }").replace("$", ".")
}

private fun cleanMethodName(name: String): String {
    return if (name == "invoke") {
        ".{ }"
    } else {
        ".$name"
    }
}

fun findUserCause(throwable: Throwable) {
    logger.info("Set -Dorg.openrndr.exceptions=JVM for conventional exception-handling")

    val bestSolution = throwable.stackTrace.reversed().indexOfLast { !(it.className.contains("org.openrndr")) }

    System.err.println()
    System.err.println()
    throwable.stackTrace.reversed().forEachIndexed { index, it ->
        throwable.stackTrace
        val parts = it.className.split("$")

        //println("--- decomposing ${it.className}")
        var query = parts[0]
        val lambdaReceiverTypes = mutableListOf<String>()

        for (part in parts.drop(1)) {
            query += "$$part"
            try {
                val cl = Class.forName(query)
                if (cl != null) {
                    if (cl.superclass.typeName == "kotlin.jvm.internal.Lambda") {
                        val annotation = cl.getAnnotation(kotlin.Metadata::class.java)
                        if (annotation != null) {
                            val types = annotation.data2[2].split(";").filter { it.isNotBlank() }.map {
                                it.split("/").last()
                            }.joinToString(", ")

                            lambdaReceiverTypes.add(types)
                        }
                    }
                } else {
                    //println("no class for $query")
                }
            } catch (e: ClassNotFoundException) {
                //println("no such class $query")
            }
        }

        var filledName = cleanClassName(it.className)
        for (receiver in lambdaReceiverTypes) {
            filledName = filledName.replaceFirst("{ }", "{ :$receiver }")
        }

        val cleanName = "${filledName}${cleanMethodName(it.methodName)}"
        if (!it.className.contains("org.openrndr") && (it.lineNumber >= 0)) {
            if (index == bestSolution) {
                System.err.println("${color(0x7f, 0x7f, 0x7f)}├─ ${color(0xff, 0xc0, 0xcb)}${cleanName}(${it.fileName}:${it.lineNumber})${colorReset()}")
            } else {
                System.err.println("${color(0x7f, 0x7f, 0x7f)}├─ ${color(0x7f, 0x7f, 0x7f)}${cleanName}(${it.fileName}:${it.lineNumber})${colorReset()}")
            }
        } else {
            System.err.println("${color(0x7f, 0x7f, 0x7f)}│  ${color(0x4f, 0x4f, 0x4f)}${cleanName}(${it.fileName}:${it.lineNumber})${colorReset()}")
        }
    }
    System.err.println("${color(0x7f, 0x7f, 0x7f)}│${colorReset()}")
    System.err.println("${color(0x7f, 0x7f, 0x7f)}↑ ${color(0xff, 0xc0, 0xcb)}${throwable.message} (${throwable::class.simpleName})${colorReset()} ")

    var cause = throwable.cause
    while (cause != null) {
        val line = cause.stackTrace.first()
        val filledName = cleanClassName(line.className)
        val cleanName = "${filledName}${cleanMethodName(line.methodName)}"
        System.err.println("${color(0x7f, 0x7f, 0x7f)}├─ ${color(0x7f, 0x7f, 0x7f)}${cleanName}(${line.fileName}:${line.lineNumber})${colorReset()}")
        cause = cause.cause
    }

    if (throwable is NoSuchMethodError || throwable is ClassNotFoundException) {
        if (throwable.message?.contains("org.openrndr") == true) {
            System.err.println()
            logger.error {
                "You are likely using incompatible versions of OPENRNDR and ORX. Fix imports and make sure to clean and rebuild your project."
            }
        }
    }
}