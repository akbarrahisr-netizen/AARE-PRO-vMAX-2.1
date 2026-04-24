package com.aare.vmax.v2.engine

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.v2.model.UiFingerprint
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

object ActionVerifier {

    // 🎯 मेन फंक्शन जो एक्शन करेगा और वेरीफाई करेगा
    suspend fun executeVerified(
        service: AccessibilityService,
        fingerprint: UiFingerprint,
        maxAttempts: Int = 3,
        onStatus: (String) -> Unit
    ): Boolean {
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            val root = service.rootInActiveWindow ?: continue
            
            try {
                val targetNode = findNodeByFingerprint(root, fingerprint)
                if (targetNode != null && targetNode.isClickable) {
                    onStatus("🎯 Action Attempt $attempt")
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    
                    // 🛡️ REAL VERIFICATION LOGIC
                    val success = waitForVerification(service, fingerprint, targetNode.text?.toString(), 2000L)
                    if (success) {
                        onStatus("✅ Action Verified Success!")
                        return true
                    }
                }
            } finally {
                try { root.recycle() } catch (_: Exception) {}
            }
            delay(300) 
        }
        onStatus("❌ Action Failed after $maxAttempts attempts")
        return false
    }

    // ✅ आपका भेजा हुआ REAL state-diff चेक लॉजिक
    private suspend fun waitForVerification(
        service: AccessibilityService,
        originalFingerprint: UiFingerprint,
        targetText: String?, 
        timeoutMs: Long
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            delay(30) 
            
            val root = service.rootInActiveWindow ?: continue
            
            try {
                if (targetText != null) {
                    val stillExists = root.findAccessibilityNodeInfosByText(targetText)
                        ?.any { it.isVisibleToUser && it.isEnabled } == true
                    if (!stillExists) return@withTimeoutOrNull true
                }
                
                val currentHash = computeRootHash(root)
                if (currentHash != (originalFingerprint.boundsCenterX xor originalFingerprint.boundsCenterY)) {
                    val originalStillThere = findNodeByFingerprint(root, originalFingerprint)
                    if (originalStillThere == null) return@withTimeoutOrNull true
                }
                
                if (root.findAccessibilityNodeInfosByText("Success")?.isNotEmpty() == true ||
                    root.findAccessibilityNodeInfosByText("Confirmed")?.isNotEmpty() == true) {
                    return@withTimeoutOrNull true
                }
                
            } finally {
                try { root.recycle() } catch (_: Exception) {}
            }
        }
        false
    } ?: false

    // ✅ Helper: compute stable root hash
    private fun computeRootHash(root: AccessibilityNodeInfo): Int {
        var hash = root.packageName?.hashCode() ?: 0
        hash = hash xor root.childCount
        root.text?.let { hash = hash xor it.hashCode() }
        return hash
    }

    // ✅ Helper: find node matching fingerprint
    private fun findNodeByFingerprint(
        root: AccessibilityNodeInfo,
        fp: UiFingerprint
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            try {
                val nodeFp = UiFingerprint.from(node)
                if (nodeFp != null && nodeFp.matchConfidence(fp) > 0.8f) {
                    return node
                }
                for (i in 0 until node.childCount) {
                    try { node.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
                }
            } finally {
                if (node !== root) try { node.recycle() } catch (_: Exception) {}
            }
        }
        return null
    }
}
