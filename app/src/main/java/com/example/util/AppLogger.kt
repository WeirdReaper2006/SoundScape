package com.example.util

import android.util.Log

/**
 * Centralized error logging. Always writes full exception details (message + stack trace)
 * to logcat under a consistent tag so issues remain debuggable, without ever exposing
 * those details to end users - callers must show a separate, generic message in the UI.
 */
object AppLogger {
    private const val DEFAULT_TAG = "SoundScape"

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$DEFAULT_TAG:$tag", message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$DEFAULT_TAG:$tag", message, throwable)
    }
}

/** Generic, user-safe exception carrying no internal details (paths, SQL, IO messages). */
class UserFacingException(message: String) : Exception(message)
