package org.wordpress.android.mediapicker.util

interface Log {
    fun e(message: String)
    fun e(message: String, throwable: Throwable)
    fun e(e: Throwable)
    fun w(message: String)
    fun d(message: String)
    fun i(message: String)
}
