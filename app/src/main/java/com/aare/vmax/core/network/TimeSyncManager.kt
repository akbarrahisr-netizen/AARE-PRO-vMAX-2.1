package com.aare.vmax.core.network

import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object TimeSyncManager {
    private var offset: Long = 0
    private var isSynced = false
    private var lastSyncTime = 0L

    // 🚀 NTP Server से मिलीसेकंड सटीकता के साथ टाइम सिंक करना
    fun syncWithNetwork(onComplete: ((Boolean) -> Unit)? = null) {
        Thread {
            try {
                val address = InetAddress.getByName("time.google.com")
                val buffer = ByteArray(48).apply { this[0] = 0x1B }
                val socket = DatagramSocket().apply { soTimeout = 3000 }
                
                val request = DatagramPacket(buffer, buffer.size, address, 123)
                val requestTime = System.currentTimeMillis()  // Local time जब request भेजा
                socket.send(request)

                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                
                val responseTime = System.currentTimeMillis()  // Local time जब response मिला
                
                // NTP packet से टाइम्स निकालें (सेकंड + मिलीसेकंड)
                val originateTime = parseNtpTimestamp(buffer, 24)   // Time when request sent (from server's clock)
                val receiveTime = parseNtpTimestamp(buffer, 32)     // Time when server received
                val transmitTime = parseNtpTimestamp(buffer, 40)    // Time when server sent response
                
                // 🎯 मिलीसेकंड सटीकता के साथ ऑफसेट कैलकुलेशन
                // Formula: θ = (T2 - T1 + T3 - T4) / 2
                // T1 = originateTime, T2 = receiveTime, T3 = transmitTime, T4 = responseTime
                val t1 = originateTime
                val t2 = receiveTime
                val t3 = transmitTime
                val t4 = responseTime
                
                // सर्वर और क्लाइंट के बीच का ऑफसेट
                offset = ((t2 - t1) + (t3 - t4)) / 2
                
                // नेटवर्क डिले (round trip time)
                val delay = (t4 - t1) - (t3 - t2)
                
                lastSyncTime = System.currentTimeMillis()
                isSynced = true
                
                println("✅ Time synced | Offset: ${offset}ms | Delay: ${delay}ms")
                onComplete?.invoke(true)
                
            } catch (e: Exception) {
                println("❌ NTP sync failed: ${e.message}")
                isSynced = false
                onComplete?.invoke(false)
            }
        }.start()
    }

    // 🎯 NTP Timestamp से मिलीसेकंड सटीकता के साथ मिलीसेकंड निकालना
    private fun parseNtpTimestamp(buffer: ByteArray, offset: Int): Long {
        // NTP timestamp: 32-bit seconds + 32-bit fractional seconds
        var seconds: Long = 0
        var fraction: Long = 0
        
        // Seconds part (4 bytes, big-endian)
        for (i in 0..3) {
            seconds = (seconds shl 8) or (buffer[offset + i].toLong() and 0xFF)
        }
        
        // Fractional seconds part (4 bytes)
        for (i in 0..3) {
            fraction = (fraction shl 8) or (buffer[offset + 4 + i].toLong() and 0xFF)
        }
        
        // Convert NTP epoch (1900) to Unix epoch (1970) milliseconds
        // NTP seconds से Unix milliseconds में बदलो
        val unixSeconds = seconds - 2208988800L
        
        // Fraction को milliseconds में बदलो (1 sec = 2^32 fractions)
        val milliseconds = (fraction * 1000) / 0x100000000L
        
        return (unixSeconds * 1000) + milliseconds
    }

    // 🎯 अब यह मिलीसेकंड सटीकता के साथ समय देगा
    fun currentTimeMillis(): Long {
        if (!isSynced) {
            // Fallback: सिंक नहीं हुआ तो local time + approximate offset
            return System.currentTimeMillis() + offset
        }
        return System.currentTimeMillis() + offset
    }
    
    // 🎯 सटीकता जांचने के लिए (debugging)
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
    
    fun isSynced(): Boolean = isSynced
    
    // 🎯 अगर सिंक ठीक नहीं हुआ तो सबसे भरोसेमंद सर्वर से retry
    fun syncWithFallback() {
        val servers = listOf(
            "time.google.com",
            "pool.ntp.org", 
            "time.windows.com",
            "in.pool.ntp.org"
        )
        
        for (server in servers) {
            try {
                syncWithNetwork { success ->
                    if (success) return@syncWithNetwork
                }
                Thread.sleep(500)
                if (isSynced) break
            } catch (e: Exception) {
                continue
            }
        }
    }
}
