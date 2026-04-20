package com.aare.vmax.core.finder

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

// ✅ Smart Match के लिए फॉर्मूला
data class NodeMatchCriteria(
    val text: String? = null,
    val contentDesc: String? = null,
    val className: String? = null,
    val isClickable: Boolean? = null,
    val bounds: Rect? = null,
    val minScore: Int = 50 
)

// ✅ NodeFinder.kt - Queue-based BFS (Safe + Fast)
class NodeFinder {
    
    fun findByContentDescription(
        root: AccessibilityNodeInfo, 
        targetDesc: String,
        maxDepth: Int = 10
    ): AccessibilityNodeInfo? {
        
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(AccessibilityNodeInfo.obtain(root) to 0)
        
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            
            if (depth > maxDepth) {
                node.recycle()
                continue
            }
            
            // ✅ Check match
            if (node.contentDescription?.toString()?.contains(targetDesc, ignoreCase = true) == true) {
                // ✅ Return safe copy, recycle queue items later (Leak fixed)
                queue.forEach { (n, _) -> n.recycle() }
                queue.clear()
                return node 
            }
            
            // ✅ Enqueue children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.addLast(child to depth + 1)
            }
            
            node.recycle() // Current node processed
        }
        
        return null
    }
    
    // ✅ Smart Match: Text + Class + Bounds + Clickable combo
    fun findBySmartMatch(
        root: AccessibilityNodeInfo,
        criteria: NodeMatchCriteria
    ): AccessibilityNodeInfo? {
        
        return root.bfsTraversal { node ->
            var score = 0
            
            if (criteria.text != null && node.text?.toString()?.contains(criteria.text, ignoreCase = true) == true) score += 30
            if (criteria.contentDesc != null && node.contentDescription?.toString()?.contains(criteria.contentDesc, ignoreCase = true) == true) score += 30
            if (criteria.className != null && node.className?.toString() == criteria.className) score += 20
            if (criteria.isClickable != null && node.isClickable == criteria.isClickable) score += 10
            
            // ✅ Bounds fix for Android
            if (criteria.bounds != null) {
                val nodeBounds = Rect()
                node.getBoundsInScreen(nodeBounds)
                if (Rect.intersects(nodeBounds, criteria.bounds)) score += 10
            }
            
            score >= criteria.minScore // Threshold-based match
        }
    }
}

// ✅ Helper extension for BFS
private inline fun AccessibilityNodeInfo.bfsTraversal(
    predicate: (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo? {
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.addLast(AccessibilityNodeInfo.obtain(this))
    
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        try {
            if (predicate(node)) {
                queue.forEach { it.recycle() }
                queue.clear()
                return AccessibilityNodeInfo.obtain(node)
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it) }
            }
        } finally {
            node.recycle()
        }
    }
    return null
}
