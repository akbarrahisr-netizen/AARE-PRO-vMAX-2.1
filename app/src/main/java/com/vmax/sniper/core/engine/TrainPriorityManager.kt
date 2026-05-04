package com.vmax.sniper.core.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * VMAX WINNING PRIORITY MANAGER
 * Dynamic Combined Scoring | Tiered Fallbacks | Sleeper Aware | Cache Optimized
 */
object TrainPriorityManager {

    private const val TAG = "TrainPriority"

    data class TrainTarget(
        val trainNumber: String,
        val trainName: String,
        val acClasses: List<String> = listOf("3A", "2A", "3E"),
        val sleeperClass: String = "SL",
        var successCount: Int = 0  // ✅ First Hit Boost
    )

    // Tier 1: Primary attacks
    private val primaryAcTargets = mutableListOf(
        TrainTarget("12424", "RAJDHANI EXP", listOf("2A", "3A")),
        TrainTarget("12236", "SAMPARK KRANTI", listOf("3A")),
        TrainTarget("12505", "NORTH EAST EXP", listOf("3A", "2A", "3E"))
    )

    private val primarySleeperTargets = mutableListOf(
        TrainTarget("12505", "NORTH EAST EXP"),
        TrainTarget("15484", "MAHANANDA EXP")
    )

    // Tier 2: Fallback targets
    private val fallbackTargets = mutableListOf(
        TrainTarget("13240", "INTERCITY EXP", listOf("3A"), "SL"),
        TrainTarget("14004", "MALDA EXPRESS", listOf("3A"), "SL")
    )

    // ✅ FIX 1: Increased cache validity (1500ms instead of 500ms)
    private const val CACHE_VALIDITY_MS = 1500L
    
    // ✅ FIX 5: Thread-safe cache
    private data class AvailabilityData(
        val waitListCount: Int,
        val timestamp: Long
    )
    private val availabilityCache = ConcurrentHashMap<String, AvailabilityData>()
    
    // ✅ Attack cooldown tracking
    private val lastAttackTime = ConcurrentHashMap<String, AtomicLong>()
    private const val COOLDOWN_MS = 300L

    /**
     * Update availability for a specific train-class combination
     */
    fun updateAvailability(trainNumber: String, className: String, waitListCount: Int) {
        val key = "${trainNumber}_$className"
        availabilityCache[key] = AvailabilityData(waitListCount, System.currentTimeMillis())
        android.util.Log.d(TAG, "📊 Updated: $key = WL$waitListCount")
    }

    /**
     * Get availability for a specific train-class combination
     */
    fun getAvailability(trainNumber: String, className: String): Int {
        val key = "${trainNumber}_$className"
        val data = availabilityCache[key]
        val now = System.currentTimeMillis()
        
        return if (data != null && now - data.timestamp < CACHE_VALIDITY_MS) {
            data.waitListCount
        } else {
            999 // Unknown/expired
        }
    }
    
    /**
     * ✅ FIX 3: Efficient snapshot (no rebuilding every time)
     */
    fun getAvailabilitySnapshot(): Map<String, Int> {
        val now = System.currentTimeMillis()
        val snapshot = mutableMapOf<String, Int>()
        
        for ((key, data) in availabilityCache) {
            if (now - data.timestamp < CACHE_VALIDITY_MS) {
                snapshot[key] = data.waitListCount
            }
        }
        return snapshot
    }

    /**
     * ✅ FIX 4: Recency-aware scoring
     * Lower score = better
     */
    fun calculateScore(trainNumber: String, className: String): Int {
        val key = "${trainNumber}_$className"
        val data = availabilityCache[key]
        val now = System.currentTimeMillis()
        
        return if (data != null && now - data.timestamp < CACHE_VALIDITY_MS) {
            // Base WL + recency penalty (newer data = slightly better)
            data.waitListCount + ((now - data.timestamp) / 100).toInt()
        } else {
            999
        }
    }

    /**
     * ✅ FIX 2: Merge + Sort fallbacks (not just append)
     */
    fun getAllTargetsWithFallback(isAc: Boolean): List<TrainTarget> {
        val primary = if (isAc) primaryAcTargets else primarySleeperTargets
        return (primary + fallbackTargets).toList()
    }
    
    /**
     * ✅ Check if train is in cooldown
     */
    private fun isInCooldown(trainNumber: String): Boolean {
        val lastTime = lastAttackTime[trainNumber]?.get() ?: 0
        return System.currentTimeMillis() - lastTime < COOLDOWN_MS
    }
    
    /**
     * ✅ Mark train as attacked (for cooldown)
     */
    fun markAttacked(trainNumber: String) {
        lastAttackTime[trainNumber] = AtomicLong(System.currentTimeMillis())
    }
    
    /**
     * ✅ Increment success count for priority boost
     */
    fun incrementSuccess(trainNumber: String) {
        val allTargets = primaryAcTargets + primarySleeperTargets + fallbackTargets
        allTargets.find { it.trainNumber == trainNumber }?.let {
            it.successCount++
            android.util.Log.d(TAG, "⭐ Success boost: ${it.trainName} (${it.successCount})")
        }
    }

    /**
     * 🔥 SMART ATTACK PLAN (Optimized)
     * - Combined tiering + fallbacks merged
     * - Recency-aware scoring
     * - Cooldown filter
     * - Success boost priority
     */
    fun getFullAttackPlan(
        isAc: Boolean,
        availabilitySnapshot: Map<String, Int>? = null
    ): List<TrainTarget> {
        
        val snap = availabilitySnapshot ?: getAvailabilitySnapshot()
        val allTargets = getAllTargetsWithFallback(isAc)
        
        // ✅ Filter: Remove trains in cooldown
        val availableTargets = allTargets.filter { !isInCooldown(it.trainNumber) }
        
        // ✅ Sort by score (lower = better)
        val sortedTargets = availableTargets.sortedBy { train ->
            val classes = if (isAc) train.acClasses else listOf(train.sleeperClass)
            val minScore = classes.minOfOrNull { className ->
                val key = "${train.trainNumber}_$className"
                val storedScore = snap[key]
                
                if (storedScore != null) {
                    // ✅ Apply success boost (more success = higher priority)
                    val boost = (train.successCount * -5).coerceAtMost(-5)
                    storedScore + boost
                } else {
                    999
                }
            } ?: 999
            
            minScore
        }
        
        android.util.Log.d(TAG, "🎯 Attack Plan: ${sortedTargets.size} trains available " +
            "(Top: ${sortedTargets.take(3).joinToString { "${it.trainName}(${it.successCount})" }})")
        
        return sortedTargets
    }

    /**
     * 🔥 CLASS INTELLIGENCE (Optimized)
     */
    fun getSmartClassOrder(
        train: TrainTarget,
        isAc: Boolean,
        availabilitySnapshot: Map<String, Int>? = null
    ): List<String> {
        
        val classes = if (isAc) train.acClasses else listOf(train.sleeperClass)
        val snap = availabilitySnapshot ?: getAvailabilitySnapshot()
        
        if (snap.isEmpty()) return classes

        return classes.sortedBy { className ->
            val key = "${train.trainNumber}_$className"
            snap[key] ?: 999
        }
    }

    /**
     * Check if a train-class combination is bookable
     */
    fun isBookable(trainNumber: String, className: String, waitListThreshold: Int = 15): Boolean {
        val score = calculateScore(trainNumber, className)
        return score < waitListThreshold
    }

    /**
     * Get best available class for a train
     */
    fun getBestAvailableClass(train: TrainTarget, isAc: Boolean): String? {
        val snap = getAvailabilitySnapshot()
        val classes = if (isAc) train.acClasses else listOf(train.sleeperClass)
        
        return classes.minByOrNull { className ->
            val key = "${train.trainNumber}_$className"
            snap[key] ?: 999
        }?.takeIf { className ->
            val key = "${train.trainNumber}_$className"
            (snap[key] ?: 999) < 15
        }
    }

    /**
     * Clear all cached availability data
     */
    fun clearCache() {
        availabilityCache.clear()
        lastAttackTime.clear()
        // Reset success counts
        (primaryAcTargets + primarySleeperTargets + fallbackTargets).forEach {
            it.successCount = 0
        }
        android.util.Log.d(TAG, "🗑️ Cache cleared")
    }
    
    /**
     * Get statistics for debugging
     */
    fun getStats(): String {
        val cacheSize = availabilityCache.size
        val cooldownCount = lastAttackTime.count { !isInCooldown(it.key) }
        val primarySuccess = primaryAcTargets.sumOf { it.successCount }
        val sleeperSuccess = primarySleeperTargets.sumOf { it.successCount }
        
        return "Cache:$cacheSize | Cooldown:$cooldownCount | AC Success:$primarySuccess | SL Success:$sleeperSuccess"
    }
}
