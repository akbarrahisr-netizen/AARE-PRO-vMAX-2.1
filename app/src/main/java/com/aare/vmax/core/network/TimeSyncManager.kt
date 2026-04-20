package com.aare.vmax.core.network

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class TimeSyncManager {

    private val TAG = "VMAX_V9_STABLE"

    // ✅ Clock Anchors
    @Volatile private var baseWallTimeMs: Long = System.currentTimeMillis()
    @Volatile private var baseRealTimeMs: Long = SystemClock.elapsedRealtime()

    // ✅ Offset State
    @Volatile private var smoothedOffsetMs: Long = 0
    @Volatile private var lastSyncRealTimeMs: Long = 0

    // ✅ Sync Control
    private val isSyncing = AtomicBoolean(false)

    // ✅ Config
    private val SYNC_INTERVAL = 3 * 60 * 1000L
    private val EMA_ALPHA = 0.4

    // ✅ Weighted Sources
    private val endpoints = mapOf(
        "https://www.irctc.co.in" to 5,
        "https://www.google.com" to 1,
        "https://www.cloudflare.com" to 1
    )

    // =========================================================
    // 🎯 PUBLIC API
    // =========================================================

    fun getAccurateTimeMs(): Long {
        val nowRealtime = SystemClock.elapsedRealtime()

        // 🔁 Controlled background sync
        if (shouldSync(nowRealtime)) {
            triggerSafeSync()
        }

        return baseWallTimeMs +
                (nowRealtime - baseRealTimeMs) +
                smoothedOffsetMs
    }

    fun getStrikeWindow(targetTimeMs: Long): Pair<Long, Long> {
        val adjusted = targetTimeMs - smoothedOffsetMs
        return (adjusted - 100) to (adjusted + 100)
    }

    // =========================================================
    // 🔒 SYNC CONTROL
    // =========================================================

    private fun shouldSync(nowRealtime: Long): Boolean {
        return lastSyncRealTimeMs == 0L ||
                (nowRealtime - lastSyncRealTimeMs > SYNC_INTERVAL)
    }

    private fun triggerSafeSync() {
        if (!isSyncing.compareAndSet(false, true)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncTimeInternal()
            } finally {
                isSyncing.set(false)
            }
        }
    }

    // =========================================================
    // 🧠 CORE SYNC ENGINE
    // =========================================================

    private suspend fun syncTimeInternal() = coroutineScope {

        val offsets = mutableListOf<Long>()

        Log.d(TAG, "📡 Sync Started...")

        endpoints.forEach { (url, weight) ->
            launch {
                repeat(2) {
                    val offset = fetchOffset(url)
                    if (offset != null) {
                        synchronized(offsets) {
                            repeat(weight) { offsets.add(offset) }
                        }
                    }
                }
            }
        }

        if (offsets.isEmpty()) {
            Log.e(TAG, "❌ Sync failed - using old offset")
            return@coroutineScope
        }

        offsets.sort()
        val median = offsets[offsets.size / 2]

        // ✅ EMA smoothing
        smoothedOffsetMs = if (lastSyncRealTimeMs == 0L) {
            median
        } else {
            (smoothedOffsetMs * (1 - EMA_ALPHA) + median * EMA_ALPHA).toLong()
        }

        // ✅ IMPORTANT: Adjust base instead of resetting (no time jump)
        val nowWall = System.currentTimeMillis()
        val nowReal = SystemClock.elapsedRealtime()

        val currentAccurate = baseWallTimeMs +
                (nowReal - baseRealTimeMs) +
                smoothedOffsetMs

        val correction = currentAccurate - nowWall

        baseWallTimeMs = nowWall + correction
        baseRealTimeMs = nowReal

        lastSyncRealTimeMs = nowReal

        Log.d(TAG, "🎯 Sync OK | Offset: $smoothedOffsetMs ms | Samples: ${offsets.size}")
    }

    // =========================================================
    // 🌐 NETWORK OFFSET FETCH
    // =========================================================

    private fun fetchOffset(endpoint: String): Long? {
        return try {
            val start = System.currentTimeMillis()

            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            connection.connect()
            val serverTime = connection.date
            val end = System.currentTimeMillis()

            connection.disconnect()

            if (serverTime <= 0) return null

            val latency = (end - start) / 2
            val clientMid = start + latency

            serverTime - clientMid

        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed: $endpoint")
            null
        }
    }
}
