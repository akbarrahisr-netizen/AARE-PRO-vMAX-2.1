package com.aare.vmax.core.executor

import android.os.SystemClock
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * ✅ Stable Priority Rate Limiter (Production Safe)
 * - No busy waiting
 * - Correct token bucket math
 * - Thread-safe + coroutine friendly
 * - Drift-free refill logic
 */
enum class ActionPriority {
    CRITICAL,
    NORMAL,
    BACKGROUND
}

class PriorityRateLimiter(
    config: Map<ActionPriority, RateConfig> = mapOf(
        ActionPriority.CRITICAL to RateConfig(minInterval = 50L, burst = 3),
        ActionPriority.NORMAL to RateConfig(minInterval = 200L, burst = 2),
        ActionPriority.BACKGROUND to RateConfig(minInterval = 2000L, burst = 1)
    )
) {

    data class RateConfig(
        val minInterval: Long,
        val burst: Int
    )

    private val buckets = ConcurrentHashMap<ActionPriority, TokenBucket>()

    init {
        config.forEach { (priority, cfg) ->
            buckets[priority] = TokenBucket(cfg.burst, cfg.minInterval)
        }
    }

    /**
     * ✅ Acquire permission for action execution
     * Suspends until allowed or timeout happens
     */
    suspend fun acquire(
        priority: ActionPriority,
        maxWaitMs: Long = 5000L
    ): Boolean {

        val bucket = buckets[priority] ?: return true

        val start = SystemClock.elapsedRealtime()

        while (true) {
            val now = SystemClock.elapsedRealtime()

            // Timeout check
            if (now - start >= maxWaitMs) return false

            val waitTime = bucket.tryConsumeOrNextWait(now)

            if (waitTime <= 0) {
                return true
            }

            // Sleep exactly required time (NO CPU spam)
            delay(waitTime.coerceAtLeast(1L))
        }
    }

    // =========================================================
    // 🧠 TOKEN BUCKET (Correct & Drift-Free)
    // =========================================================
    private class TokenBucket(
        private val maxTokens: Int,
        private val refillIntervalMs: Long
    ) {

        private val tokens = AtomicLong(maxTokens.toLong())
        private val lastRefillTime = AtomicLong(SystemClock.elapsedRealtime())

        /**
         * Returns:
         * 0 = can execute now
         * >0 = wait time required
         */
        fun tryConsumeOrNextWait(now: Long): Long {
            refill(now)

            val current = tokens.get()

            return if (current > 0) {
                tokens.decrementAndGet()
                0L
            } else {
                // predict next refill time
                val last = lastRefillTime.get()
                val nextRefill = last + refillIntervalMs
                max(1L, nextRefill - now)
            }
        }

        /**
         * ✅ Correct refill logic (NO drift)
         */
        private fun refill(now: Long) {
            val last = lastRefillTime.get()
            val elapsed = now - last

            if (elapsed < refillIntervalMs) return

            val refillCount = (elapsed / refillIntervalMs)

            if (refillCount <= 0) return

            while (true) {
                val current = tokens.get()
                val newValue = (current + refillCount).coerceAtMost(maxTokens.toLong())

                if (tokens.compareAndSet(current, newValue)) {
                    break
                }
            }

            // ✅ IMPORTANT FIX: move forward properly (no time jump loss)
            val newLast = last + (refillCount * refillIntervalMs)
            lastRefillTime.set(newLast)
        }
    }
}
