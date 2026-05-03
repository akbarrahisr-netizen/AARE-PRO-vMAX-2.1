package com.vmax.sniper.engine

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object TimeSyncManager {
    private var offset: Long = 0
    private var isSynced = false
    private const val TAG = "VMAX_NTP"

    fun getNetworkTime(): Long = System.currentTimeMillis() + offset

    fun syncWithNetwork() {
        Thread {
            var attempts = 0
            while (attempts < 3 && !isSynced) {
                try {
                    val address = InetAddress.getByName("time.nplindia.org")
                    val buffer = ByteArray(48).apply { this[0] = 0x1B }
                    val socket = DatagramSocket().apply { soTimeout = 3000 }
                    
                    val t1 = System.currentTimeMillis()
                    socket.send(DatagramPacket(buffer, buffer.size, address, 123))

                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    val t4 = System.currentTimeMillis()
                    
                    val t2 = parseNtpTimestamp(buffer, 32)
                    val t3 = parseNtpTimestamp(buffer, 40)
                    
                    offset = ((t2 - t1) + (t3 - t4)) / 2
                    isSynced = true
                    Log.d(TAG, "✅ NTP Sync Complete! Offset: $offset ms")
                    socket.close()
                } catch (e: Exception) {
                    attempts++
                    Log.e(TAG, "⚠️ Attempt $attempts failed: ${e.message}")
                    Thread.sleep(1000)
                }
            }
        }.start()
    }

    private fun parseNtpTimestamp(buffer: ByteArray, offset: Int): Long {
        // Parse seconds (4 bytes)
        var seconds: Long = 0
        for (i in 0..3) {
            seconds = (seconds shl 8) or (buffer[offset + i].toLong() and 0xFF)
        }
        
        // Parse fractional seconds (4 bytes) - THE MISSING PIECE!
        var fraction: Long = 0
        for (i in 4..7) {
            fraction = (fraction shl 8) or (buffer[offset + i].toLong() and 0xFF)
        }
        
        // Convert fraction to milliseconds
        val milliseconds = (fraction * 1000) / 0x100000000L
        
        // Convert NTP epoch (1900) to Unix epoch (1970)
        val unixSeconds = seconds - 2208988800L
        
        return (unixSeconds * 1000) + milliseconds
    }
}
