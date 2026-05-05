package com.vmax.sniper.core.network

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*

object TimeSyncManager {

    private const val TAG = "TimeSync"
    private const val NTP_SERVER = "time.google.com"
    private const val RESYNC_INTERVAL = 60_000L // 1 minute

    @Volatile private var offsetMs: Long = 0L
    @Volatile private var isSynced: Boolean = false
    @Volatile private var lastSyncTime: Long = 0L
    @Volatile private var cachedAddress: InetAddress? = null
    
    private val syncScope = CoroutineScope(Dispatchers.IO)

    fun isSynced(): Boolean = isSynced

    fun currentTimeMillis(): Long {
        return System.currentTimeMillis() + offsetMs
    }

    fun shouldResync(): Boolean {
        return System.currentTimeMillis() - lastSyncTime > RESYNC_INTERVAL
    }

    fun getPreciseTimeString(): String {
        val date = Date(currentTimeMillis())
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(date)
    }

    // ✅ syncTime() - WorkflowEngine calls this
    fun syncTime() {
        syncScope.launch {
            syncWithNetwork()
        }
    }

    // ✅ getOffset() - WorkflowEngine needs this
    fun getOffset(): Long = offsetMs

    suspend fun syncWithNetwork(samples: Int = 3): Boolean = withContext(Dispatchers.IO) {

        var socket: DatagramSocket? = null

        try {
            val address = cachedAddress ?: InetAddress.getByName(NTP_SERVER).also {
                cachedAddress = it
            }

            socket = DatagramSocket().apply {
                soTimeout = 1200
            }

            var bestOffset = 0L
            var bestDelay = Long.MAX_VALUE

            val buffer = ByteArray(48)

            repeat(samples) { index ->

                buffer.fill(0)
                buffer[0] = 0x1B

                val request = DatagramPacket(buffer, buffer.size, address, 123)

                val t1Wall = System.currentTimeMillis()
                val t1Mono = SystemClock.elapsedRealtime()

                socket.send(request)

                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                val t4Mono = SystemClock.elapsedRealtime()
                val t4Wall = t1Wall + (t4Mono - t1Mono)

                val seconds = readTimeStamp(buffer, 40)
                val fraction = readTimeStamp(buffer, 44)

                val ntpEpochOffset = 2208988800000L
                val t3 = (seconds * 1000) +
                        ((fraction * 1000L) / 0x100000000L) -
                        ntpEpochOffset

                val delay = t4Mono - t1Mono
                val offset = (t3 - t4Wall) + (delay / 2)

                if (delay < bestDelay) {
                    bestDelay = delay
                    bestOffset = offset
                }

                if (bestDelay < 20) {
                    applyOffset(bestOffset)
                    Log.d(TAG, "⚡ Ultra-fast sync @ sample $index | RTT: ${bestDelay}ms")
                    return@withContext true
                }
            }

            applyOffset(bestOffset)
            Log.d(TAG, "✅ Sync OK | Offset: ${offsetMs}ms | RTT: ${bestDelay}ms")

            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync Failed: ${e.message}")
            false
        } finally {
            socket?.close()
        }
    }

    private fun applyOffset(newOffset: Long) {
        val alpha = if (kotlin.math.abs(newOffset - offsetMs) > 50) 0.5 else 0.3

        offsetMs = if (isSynced) {
            ((offsetMs * (1 - alpha)) + (newOffset * alpha)).toLong()
        } else {
            newOffset
        }

        isSynced = true
        lastSyncTime = System.currentTimeMillis()
    }

    private fun readTimeStamp(buffer: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0..3) {
            value = (value shl 8) or (buffer[offset + i].toLong() and 0xFFL)
        }
        return value
    }
}
