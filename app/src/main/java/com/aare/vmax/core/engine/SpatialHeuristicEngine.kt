package com.aare.vmax.core.engine

import android.view.accessibility.AccessibilityNodeInfo

class SpatialHeuristicEngine {

    // -----------------------------
    // FIND NODE BY TEXT (SAFE)
    // -----------------------------
    fun findNodeByText(
        rootNode: AccessibilityNodeInfo?,
        text: String
    ): AccessibilityNodeInfo? {

        if (rootNode == null) return null

        val nodes = rootNode.findAccessibilityNodeInfosByText(text)

        return nodes?.firstOrNull()
    }

    // -----------------------------
    // FIND INPUT NEXT TO LABEL
    // -----------------------------
    fun findInputNextToLabel(
        labelNode: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {

        if (labelNode == null) return null

        val parent = labelNode.parent ?: return null

        val childCount = parent.childCount
        if (childCount <= 0) return null

        for (i in 0 until childCount) {

            val child = parent.getChild(i) ?: continue

            val className = child.className?.toString() ?: continue

            // safer check (EditText OR input field)
            if (
                className.contains("EditText", ignoreCase = true) ||
                className.contains("android.widget.EditText")
            ) {
                return child
            }
        }

        return null
    }

    // -----------------------------
    // OPTIONAL: CLEANUP HELPER (GOOD PRACTICE)
    // -----------------------------
    fun recycleNode(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (_: Exception) {
            // ignore safely
        }
    }
}
