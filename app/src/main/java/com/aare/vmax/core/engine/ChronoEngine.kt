package com.aare.vmax.core.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ChronoEngine {

    private var timeOffset: Long = 0

    // -----------------------------
    // NTP SYNC ENGINE (SAFE VERSION)
    // -----------------------------
    suspend fun syncWithNetworkTime() = withContext(Dispatchers.IO) {

        var socket: DatagramSocket? = null

        try {
            val ntpServer = "time.google.com"
            val address = InetAddress.getByName(ntpServer)

            socket = DatagramSocket().apply {
                soTimeout = 3000
            }

            val buffer = ByteArray(48)

            // NTP request header
            buffer[0] = 0x1B

            val requestPacket = DatagramPacket(buffer, buffer.size, address, 123)

            val t1 = System.currentTimeMillis()
            socket.send(requestPacket)

            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)

            val t2 = System.currentTimeMillis()

            val serverTime = extractTime(buffer)

            // Improved offset calculation (basic NTP approximation)
            val roundTripDelay = t2 - t1
            timeOffset = serverTime - (t1 + roundTripDelay / 2)

            Log.d("VMAX_CHRONO", "Sync Success! Offset: $timeOffset ms")

        } catch (e: Exception) {
            Log.e("VMAX_CHRONO", "Sync Failed: ${e.message}")

        } finally {
            socket?.close()
        }
    }

    // -----------------------------
    // CURRENT EXACT TIME
    // -----------------------------
    fun getCurrentExactTime(): Long {
        return System.currentTimeMillis() + timeOffset
    }

    // -----------------------------
    // NTP TIME PARSER
    // -----------------------------
    private fun extractTime(buffer: ByteArray): Long {

        var seconds: Long = 0

        for (i in 40..43) {
            seconds = (seconds shl 8) or (buffer[i].toLong() and 0xFF)
        }

        val msFrom1900 = (seconds - 2_208_988_800L) * 1000

        return msFrom1900
    }
}
