package com.vmax.sniper.core.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

object TimeSyncManager {

    private const val TAG = "TimeSync"
    private const val NTP_SERVER = "time.nplindia.org"

    @Volatile private var offset: Long = 0L
    private val synced = AtomicBoolean(false)

    fun syncWithNetwork() {
        Thread {
            var socket: DatagramSocket? = null
            try {
                val address = InetAddress.getByName(NTP_SERVER)

                val buffer = ByteArray(48).apply { this[0] = 0x1B }

                socket = DatagramSocket().apply {
                    soTimeout = 3000
                }

                val request = DatagramPacket(buffer, buffer.size, address, 123)

                val t1 = System.currentTimeMillis()
                socket.send(request)

                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                val t4 = System.currentTimeMillis()

                val t2 = parseNtpTimestamp(buffer, 32) // receive time
                val t3 = parseNtpTimestamp(buffer, 40) // transmit time

                // ✅ Correct NTP formula
                val delay = t4 - t1
                val calculatedOffset = ((t2 - t1) + (t3 - t4)) / 2

                offset = calculatedOffset
                synced.set(true)

                Log.d(TAG, "✅ Synced | Offset: $offset ms | RTT: $delay ms")

            } catch (e: Exception) {
                synced.set(false)
                Log.e(TAG, "❌ Sync Failed: ${e.message}")
            } finally {
                socket?.close()
            }
        }.start()
    }

    // ✅ ADDED: Alias for syncWithNetwork() - WorkflowEngine calls this
    fun syncTime() = syncWithNetwork()

    // ✅ ADDED: Get current offset value
    fun getOffset(): Long = offset

    fun currentTimeMillis(): Long {
        return System.currentTimeMillis() + offset
    }

    fun isSynced(): Boolean = synced.get()

    private fun parseNtpTimestamp(buffer: ByteArray, offset: Int): Long {
        var seconds = 0L
        var fraction = 0L

        for (i in 0..3) {
            seconds = (seconds shl 8) or (buffer[offset + i].toLong() and 0xFF)
        }
        for (i in 0..3) {
            fraction = (fraction shl 8) or (buffer[offset + 4 + i].toLong() and 0xFF)
        }

        return (seconds - 2208988800L) * 1000 +
                (fraction * 1000L) / 0x100000000L
    }

    fun getPreciseTimeString(): String {
        val time = currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = time }

        return String.format(
            "%02d:%02d:%02d.%03d",
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            calendar.get(java.util.Calendar.SECOND),
            calendar.get(java.util.Calendar.MILLISECOND)
        )
    }
}
