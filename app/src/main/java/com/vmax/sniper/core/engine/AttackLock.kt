package com.vmax.sniper.core.engine

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object AttackLock {

    private val locked = AtomicBoolean(false)

    private data class Winner(
        val train: String,
        val className: String
    )

    private val winnerRef = AtomicReference<Winner?>(null)

    // ✅ ATOMIC LOCK: Single Source of Truth
    fun tryLock(train: String, className: String): Boolean {
        val acquired = locked.compareAndSet(false, true)

        if (acquired) {
            winnerRef.set(Winner(train, className))
        }

        return acquired
    }

    fun isLocked(): Boolean = locked.get()

    fun getWinner(): Pair<String?, String?> {
        val w = winnerRef.get()
        return w?.train to w?.className
    }

    fun reset() {
        winnerRef.set(null)
        locked.set(false)
    }
}
