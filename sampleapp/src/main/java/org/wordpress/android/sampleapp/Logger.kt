package org.wordpress.android.sampleapp

import org.wordpress.android.mediapicker.util.Log
import javax.inject.Inject

class Logger @Inject constructor() : Log {
    override fun e(message: String) {
        println(message)
    }

    override fun e(message: String, throwable: Throwable) {
        println(message)
        throwable.printStackTrace()
    }

    override fun e(e: Throwable) {
        println(e)
    }

    override fun w(message: String) {
        println(message)
    }

    override fun d(message: String) {
        println(message)
    }

    override fun i(message: String) {
        println(message)
    }
}