package com.aare.vmax.v2.model

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class UiFingerprint(
    val className: String,
    val packageName: String,
    val text: String?,
    val contentDescription: String?,
    val boundsCenterX: Int,
    val boundsCenterY: Int,
    val isClickable: Boolean
) {
    companion object {
        fun from(node: AccessibilityNodeInfo): UiFingerprint? {
            return try {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                UiFingerprint(
                    className = node.className?.toString() ?: "",
                    packageName = node.packageName?.toString() ?: "",
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    boundsCenterX = bounds.centerX(),
                    boundsCenterY = bounds.centerY(),
                    isClickable = node.isClickable
                )
            } catch (e: Exception) { null }
        }
    }

    fun matchConfidence(other: UiFingerprint): Float {
        var score = 0f
        if (this.className == other.className) score += 0.3f
        if (this.text == other.text) score += 0.4f
        if (this.contentDescription == other.contentDescription) score += 0.2f
        // लोकेशन चेक
        if (Math.abs(this.boundsCenterX - other.boundsCenterX) < 50) score += 0.05f
        if (Math.abs(this.boundsCenterY - other.boundsCenterY) < 50) score += 0.05f
        return score
    }
}
