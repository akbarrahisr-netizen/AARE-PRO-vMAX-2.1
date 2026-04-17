package com.aare.vmax.core.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

object AutomationCommandCenter {

    private val _isSystemActive = MutableStateFlow(false)
    val isSystemActive: StateFlow<Boolean> = _isSystemActive

    // Prevent duplicate runs (VERY IMPORTANT)
    private val isLocked = AtomicBoolean(false)

    fun startSystem() {
        // अगर पहले से running है → ignore
        if (!isLocked.compareAndSet(false, true)) return

        _isSystemActive.value = true
    }

    fun stopSystem() {
        isLocked.set(false)
        _isSystemActive.value = false
    }

    fun isRunning(): Boolean {
        return _isSystemActive.value
    }
}
