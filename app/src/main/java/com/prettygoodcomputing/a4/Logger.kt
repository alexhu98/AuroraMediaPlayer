package com.prettygoodcomputing.a4

import android.os.Looper
import android.os.SystemClock
import android.util.Log
//import com.crashlytics.android.Crashlytics
import java.text.SimpleDateFormat
import java.util.Date


object Logger {

    private val enterTimeStack = arrayListOf<Long>()
    private val simpleTimeFormat = SimpleDateFormat("mm:ss.SSS")

    private val PADDING = 25

    init {
        enterTimeStack.add(SystemClock.elapsedRealtime())
    }

    inline fun <T> logBlock(tag: String, message: String, body: () -> T): T {
        enter(tag, message)
        try {
            return body()
        }
        finally {
            exit(tag, message)
        }
    }

    @Synchronized
    fun enter(tag: String, message: String) {
        v(tag, "enter - " + message)

        // append the current AFTER the message is logged so that we know
        // the elapsed time the function is entered from the calling function
        if (inUIThread()) {
            // don't change the stack unless the caller is in the UI thread
            val enterTime = SystemClock.elapsedRealtime()
            enterTimeStack.add(enterTime)
        }
    }

    @Synchronized
    fun exit(tag: String, message: String) {
        v(tag, "exit - " + message)
        if (enterTimeStack.size == 0) {
            try {
                IllegalStateException("$tag Logger.exit() *** enterTimeStack.size == 0")
            }
            catch (ex: Throwable) {
//                Crashlytics.logException(ex)
            }
        }
        else {
            if (inUIThread()) {
                // don't change the stack unless the caller is in the UI thread
                enterTimeStack.removeAt(enterTimeStack.size - 1)
            }
        }
    }

    fun v(tag: String, message: String) {
        Log.v(tag, prefix(tag) + message)
    }

    fun v(tag: String, message: String, tr: Throwable?) {
        Log.v(tag, prefix(tag) + message, tr)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, prefix(tag) + message)
    }

    fun i(tag: String, message: String, tr: Throwable?) {
        Log.i(tag, prefix(tag) + message, tr)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, prefix(tag) + message)
    }

    fun d(tag: String, message: String, tr: Throwable?) {
        Log.d(tag, prefix(tag) + message, tr)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, prefix(tag) + message)
    }

    fun w(tag: String, message: String, tr: Throwable?) {
        Log.w(tag, prefix(tag) + message, tr)
    }

    fun e(tag: String, message: String) {
        Log.e(tag, prefix(tag) + message)
    }

    fun e(tag: String, message: String, tr: Throwable?) {
        Log.e(tag, prefix(tag) + message, tr)
    }

    @Synchronized
    private fun prefix(tag: String): String {
        val enterTime = when {
            enterTimeStack.size == 0 -> 0
            inUIThread() -> enterTimeStack[enterTimeStack.size - 1]
            else -> SystemClock.elapsedRealtime()
        }
        val currentTime = SystemClock.elapsedRealtime()
        val n = if (PADDING - tag.length - 2 > 0) (PADDING - tag.length - 2) else 0
        val padding = " ".repeat(n)
        return padding + simpleTimeFormat.format(Date()) + " " + (currentTime - enterTime) + "ms * "
    }

    private fun inUIThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }
}
