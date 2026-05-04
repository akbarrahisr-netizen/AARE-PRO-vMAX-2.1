package com.vmax.sniper.core.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object TimeSyncManager {
    private var offset: Long = 0
    private var isSynced = false

    fun syncWithNetwork() {
        Thread {
            try {
                val address = InetAddress.getByName("time.nplindia.org")
                val buffer = ByteArray(48).apply { this[0] = 0x1B }
                val socket = DatagramSocket().apply { soTimeout = 3000 }
                
                val request = DatagramPacket(buffer, buffer.size, address, 123)
                val requestTime = System.currentTimeMillis()
                socket.send(request)

                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val responseTime = System.currentTimeMillis()
                
                val originateTime = parseNtpTimestamp(buffer, 24)
                val receiveTime = parseNtpTimestamp(buffer, 32)
                val transmitTime = parseNtpTimestamp(buffer, 40)
                
                offset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2
                isSynced = true
                android.util.Log.d("TimeSync", "NTP Synced! Offset: $offset ms")
            } catch (e: Exception) { 
                android.util.Log.e("TimeSync", "NTP Failed: ${e.message}")
                isSynced = false 
            }
        }.start()
    }

    fun currentTimeMillis(): Long = System.currentTimeMillis() + offset
    fun isSynced(): Boolean = isSynced

    private fun parseNtpTimestamp(buffer: ByteArray, offset: Int): Long {
        var seconds: Long = 0
        var fraction: Long = 0
        for (i in 0..3) seconds = (seconds shl 8) or (buffer[offset + i].toLong() and 0xFF)
        for (i in 0..3) fraction = (fraction shl 8) or (buffer[offset + 4 + i].toLong() and 0xFF)
        return (seconds - 2208988800L) * 1000 + (fraction * 1000) / 0x100000000L
    }
    
    fun getPreciseTimeString(): String {
        val time = currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = time }
        return String.format("%02d:%02d:%02d.%03d",
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            calendar.get(java.util.Calendar.SECOND),
            calendar.get(java.util.Calendar.MILLISECOND))
    }
}
