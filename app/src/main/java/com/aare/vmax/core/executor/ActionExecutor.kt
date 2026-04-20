package com.aare.vmax.core.executor

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.models.ActionPriority

class ActionExecutor {

    private val TAG = "ActionExecutor"

    fun click(node: AccessibilityNodeInfo?, priority: ActionPriority): Boolean {

        if (node == null) {
            Log.e(TAG, "❌ Node is null")
            return false
        }

        if (!node.isVisibleToUser) {
            Log.w(TAG, "⚠️ Node not visible")
        }

        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        Log.d(
            TAG,
            "🖱 Click executed | Priority: $priority | Result: $success"
        )

        return success
    }
}
