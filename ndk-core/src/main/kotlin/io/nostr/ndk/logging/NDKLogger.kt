package io.nostr.ndk.logging

/**
 * Logging interface for NDK.
 *
 * This abstraction allows NDK to work in both Android and JVM environments,
 * and enables logging to be disabled or redirected for testing.
 *
 * Default implementation uses Android's Log class when available,
 * falling back to println for JVM/test environments.
 */
interface NDKLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * Default logger that uses Android Log when available, println otherwise.
 */
object DefaultNDKLogger : NDKLogger {
    private val androidLogClass: Class<*>? = try {
        Class.forName("android.util.Log")
    } catch (e: ClassNotFoundException) {
        null
    }

    private val androidLogD = androidLogClass?.getMethod("d", String::class.java, String::class.java)
    private val androidLogI = androidLogClass?.getMethod("i", String::class.java, String::class.java)
    private val androidLogW = androidLogClass?.getMethod("w", String::class.java, String::class.java)
    private val androidLogE = androidLogClass?.getMethod("e", String::class.java, String::class.java, Throwable::class.java)
    private val androidLogENoThrowable = androidLogClass?.getMethod("e", String::class.java, String::class.java)

    override fun d(tag: String, message: String) {
        if (androidLogD != null) {
            androidLogD.invoke(null, tag, message)
        } else {
            println("D/$tag: $message")
        }
    }

    override fun i(tag: String, message: String) {
        if (androidLogI != null) {
            androidLogI.invoke(null, tag, message)
        } else {
            println("I/$tag: $message")
        }
    }

    override fun w(tag: String, message: String) {
        if (androidLogW != null) {
            androidLogW.invoke(null, tag, message)
        } else {
            println("W/$tag: $message")
        }
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null && androidLogE != null) {
            androidLogE.invoke(null, tag, message, throwable)
        } else if (androidLogENoThrowable != null) {
            androidLogENoThrowable.invoke(null, tag, message)
        } else {
            println("E/$tag: $message")
            throwable?.printStackTrace()
        }
    }
}

/**
 * Silent logger that discards all log messages.
 * Useful for testing.
 */
object SilentNDKLogger : NDKLogger {
    override fun d(tag: String, message: String) {}
    override fun i(tag: String, message: String) {}
    override fun w(tag: String, message: String) {}
    override fun e(tag: String, message: String, throwable: Throwable?) {}
}

/**
 * Global logger instance used by NDK.
 * Can be replaced at application startup to redirect logs.
 */
object NDKLogging {
    @Volatile
    var logger: NDKLogger = DefaultNDKLogger

    fun d(tag: String, message: String) = logger.d(tag, message)
    fun i(tag: String, message: String) = logger.i(tag, message)
    fun w(tag: String, message: String) = logger.w(tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = logger.e(tag, message, throwable)
}
