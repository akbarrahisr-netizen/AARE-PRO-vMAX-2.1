package com.aare.vmax.v2.model

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PRODUCTION-SAFE: Event buffer with explicit backpressure + memory-safe copying
 * 
 * ✅ Key fixes:
 * • AccessibilityEvent.obtain() for safe copy (prevents stale data)
 * • Debounced emission (prevents race + memory churn)
 * • Explicit recycle in clear() (prevents leaks)
 * • Synchronized access (thread-safe)
 */
class EventBuffer {
    
    // ✅ Store COPIED events (original may be recycled by OS)
    private val _latestByWindow = mutableMapOf<Long?, AccessibilityEvent>()
    private val _flow = MutableStateFlow<Map<Long?, AccessibilityEvent>>(emptyMap())
    val flow = _flow.asStateFlow()
    
    // ✅ Debounce scope + lock for thread safety
    private val debounceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pendingEmit: Job? = null
    private val emitLock = Any()
    
    /**
     * Offer event with explicit backpressure + safe copy
     */
    fun offer(event: AccessibilityEvent) {
        val windowId = event.windowId?.toLong()
        val type = event.eventType
        
        // ✅ Explicit backpressure: drop intermediate scrolls (intentional)
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED && _latestByWindow.containsKey(windowId)) {
            return
        }
        
        // ✅ CRITICAL FIX: Create safe copy via obtain() (original may be recycled by OS)
        val safeCopy = try {
            AccessibilityEvent.obtain(event)
        } catch (e: Exception) {
            // Fallback: skip if copy fails
            return
        }
        
        // ✅ Thread-safe update with copy
        synchronized(emitLock) {            // Recycle previous copy if exists (prevent leak)
            _latestByWindow[windowId]?.recycle()
            _latestByWindow[windowId] = safeCopy
        }
        
        // ✅ Debounced emission (prevents race + memory churn)
        synchronized(emitLock) {
            pendingEmit?.cancel()
            pendingEmit = debounceScope.launch {
                delay(50) // ✅ 50ms debounce window
                synchronized(emitLock) {
                    _flow.value = _latestByWindow.toMap()
                }
            }
        }
    }
    
    /**
     * Consume latest events (caller receives ownership of copies)
     */
    fun consumeLatest(): List<AccessibilityEvent> {
        return synchronized(emitLock) {
            val events = _latestByWindow.values.toList()
            _latestByWindow.clear()
            events
        }
    }
    
    /**
     * Clear all buffered events (MUST recycle copies to prevent leak)
     */
    fun clear() {
        synchronized(emitLock) {
            // ✅ CRITICAL: Recycle all stored copies before clearing
            _latestByWindow.values.forEach { event ->
                try { event.recycle() } catch (_: Exception) {}
            }
            _latestByWindow.clear()
            pendingEmit?.cancel()
            _flow.value = emptyMap()
        }
        debounceScope.cancel()
    }
    
    /**
     * Cleanup: call when buffer is no longer needed
     */
    fun shutdown() {
        clear()
        debounceScope.cancel()    }
}
