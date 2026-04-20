package com.aare.vmax.core.logging

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList

// =========================================================
// ✅ LOGGER
// =========================================================
interface Logger {
    fun info(tag: String, message: String, metadata: Map<String, Any> = emptyMap())
    fun warn(tag: String, message: String, metadata: Map<String, Any> = emptyMap())
    fun error(tag: String, message: String, error: Throwable? = null, metadata: Map<String, Any> = emptyMap())
}

class JsonLogger : Logger {

    private val json = Json { ignoreUnknownKeys = true }

    override fun info(tag: String, message: String, metadata: Map<String, Any>) {
        log("INFO", tag, message, metadata)
    }

    override fun warn(tag: String, message: String, metadata: Map<String, Any>) {
        log("WARN", tag, message, metadata)
    }

    override fun error(
        tag: String,
        message: String,
        error: Throwable?,
        metadata: Map<String, Any>
    ) {
        val extra = metadata.toMutableMap()
        error?.let { extra["error"] = it.message ?: "unknown" }

        log("ERROR", tag, message, extra)
    }

    private fun log(
        level: String,
        tag: String,
        message: String,
        metadata: Map<String, Any>
    ) {
        val logEntry = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "level" to level,
            "tag" to tag,
            "message" to message,
            "metadata" to metadata
        )

        Log.d(tag, json.encodeToString(logEntry))
    }
}
