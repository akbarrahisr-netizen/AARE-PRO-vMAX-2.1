package com.aare.vmax.core.engine

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class SafetyClutchEngine {

    // -----------------------------
    // NORMALIZE TEXT (IMPORTANT)
    // -----------------------------
    private fun normalize(text: String): String {
        return text.lowercase().trim()
    }

    // -----------------------------
    // SYSTEM BUSY DETECTION
    // -----------------------------
    fun isSystemBusy(rootNode: AccessibilityNodeInfo?): Boolean {

        if (rootNode == null) return false

        val busyKeywords = listOf(
            "please wait",
            "processing",
            "loading",
            "wait"
        )

        for (keyword in busyKeywords) {

            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)

            if (!nodes.isNullOrEmpty()) {
                Log.d("VMAX_SAFETY", "System Busy detected: $keyword")
                return true
            }
        }

        return false
    }

    // -----------------------------
    // CRITICAL ERROR DETECTION
    // -----------------------------
    fun hasCriticalError(rootNode: AccessibilityNodeInfo?): Boolean {

        if (rootNode == null) return false

        val errorKeywords = listOf(
            "session expired",
            "connection error",
            "quota full",
            "try again",
            "failed"
        )

        for (keyword in errorKeywords) {

            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)

            if (!nodes.isNullOrEmpty()) {
                Log.e("VMAX_SAFETY", "Critical Error detected: $keyword")
                return true
            }
        }

        return false
    }

    // -----------------------------
    // CAPTCHA DETECTION (IMPROVED)
    // -----------------------------
    fun isCaptchaVisible(rootNode: AccessibilityNodeInfo?): Boolean {

        if (rootNode == null) return false

        val captchaKeywords = listOf(
            "type the characters",
            "captcha",
            "enter the text",
            "verify you are human"
        )

        for (keyword in captchaKeywords) {

            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)

            if (!nodes.isNullOrEmpty()) {
                Log.d("VMAX_SAFETY", "Captcha detected: $keyword")
                return true
            }
        }

        return false
    }
}
