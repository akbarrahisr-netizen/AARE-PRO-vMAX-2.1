package com.aare.vmax.core.utils

import android.view.accessibility.AccessibilityNodeInfo

object SafeRecycle {
    fun recycle(node: AccessibilityNodeInfo?) {
        try { node?.recycle() } catch (_: Exception) {}
    }
}

