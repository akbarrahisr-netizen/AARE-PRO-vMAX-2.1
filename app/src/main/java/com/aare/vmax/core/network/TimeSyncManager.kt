package com.aare.vmax.core.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object TimeSyncManager {
    private var offset: Long = 0
    private var isSynced = false
    private var lastSyncTime = 0L

    // 🇮🇳 भारत का सबसे सटीक NTP सर्वर (NPL India)
    private val PRIMARY_NTP_SERVER = "time.nplindia.org"
    
    // Backup servers
    private val BACKUP_NTP_SERVERS = listOf(
        "time.google.com",
        "pool.ntp.org",
        "time.windows.com",
        "in.pool.ntp.org"
    )

    // 🚀 NTP Server से मिलीसेकंड सटीकता के साथ टाइम सिंक करना
    fun syncWithNetwork(onComplete: ((Boolean) -> Unit)? = null) {
        Thread {
            try {
                val success = syncWithServer(PRIMARY_NTP_SERVER)
                if (!success) {
                    // अगर primary fail हो तो backup servers try करें
                    for (server in BACKUP_NTP_SERVERS) {
                        if (syncWithServer(server)) {
                            println("✅ Time synced with backup server: $server")
                            break
                        }
                        Thread.sleep(200)
                    }
                }
                onComplete?.invoke(isSynced)
            } catch (e: Exception) {
                println("❌ NTP sync failed: ${e.message}")
                isSynced = false
                onComplete?.invoke(false)
            }
        }.start()
    }
    
    private fun syncWithServer(server: String): Boolean {
        return try {
            val address = InetAddress.getByName(server)
            val buffer = ByteArray(48).apply { this[0] = 0x1B }
            val socket = DatagramSocket().apply { soTimeout = 3000 }
            
            val request = DatagramPacket(buffer, buffer.size, address, 123)
            val requestTime = System.currentTimeMillis()
            socket.send(request)

            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            
            val responseTime = System.currentTimeMillis()
            
            // NTP timestamp से मिलीसेकंड सटीकता के साथ टाइम निकालें
            val originateTime = parseNtpTimestamp(buffer, 24)
            val receiveTime = parseNtpTimestamp(buffer, 32)
            val transmitTime = parseNtpTimestamp(buffer, 40)
            
            // Precision Calculation
            val t1 = originateTime
            val t2 = receiveTime
            val t3 = transmitTime
            val t4 = responseTime
            
            offset = ((t2 - t1) + (t3 - t4)) / 2
            val delay = (t4 - t1) - (t3 - t2)
            
            lastSyncTime = System.currentTimeMillis()
            isSynced = true
            
            println("✅ NPL Time synced | Offset: ${offset}ms | Delay: ${delay}ms | Server: $server")
            true
            
        } catch (e: Exception) {
            println("⚠️ Failed to sync with $server: ${e.message}")
            false
        }
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
        
        // NTP epoch (1900) से Unix epoch (1970) में बदलो
        val unixSeconds = seconds - 2208988800L
        
        // Fraction को milliseconds में बदलो (1 sec = 2^32 fractions)
        val milliseconds = (fraction * 1000) / 0x100000000L
        
        return (unixSeconds * 1000) + milliseconds
    }

    // 🎯 मिलीसेकंड सटीकता के साथ current time
    fun currentTimeMillis(): Long {
        if (!isSynced) {
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
    
    // 🎯 Indian Standard Time (IST) में समय दिखाने के लिए
    fun getISTTimeString(): String {
        val time = currentTimeMillis()
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
            timeInMillis = time
        }
        return String.format(
            "%02d:%02d:%02d.%03d IST",
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            calendar.get(java.util.Calendar.SECOND),
            calendar.get(java.util.Calendar.MILLISECOND)
        )
    }
    
    fun isSynced(): Boolean = isSynced
    
    // 🎯 Force re-sync (Tatkal से पहले call करें)
    fun forceResync(): Boolean {
        isSynced = false
        syncWithNetwork()
        Thread.sleep(1000)
        return isSynced
    }
}
