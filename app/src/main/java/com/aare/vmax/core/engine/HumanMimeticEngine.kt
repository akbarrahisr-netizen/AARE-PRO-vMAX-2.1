package com.aare.vmax.core.engine

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlin.random.Random

class HumanMimeticEngine {

    // -----------------------------
    // 1. HUMAN DELAY ENGINE
    // -----------------------------
    suspend fun humanDelay(min: Long = 80, max: Long = 250) {
        val delayTime = Random.nextLong(min, max)
        delay(delayTime)
    }

    // -----------------------------
    // 2. SAFE HUMAN CLICK ENGINE
    // -----------------------------
    fun performHumanClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        return try {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                // fallback: try parent click
                node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    // -----------------------------
    // 3. HUMAN-LIKE TEXT INPUT
    // -----------------------------
    suspend fun typeHumanLike(
        node: AccessibilityNodeInfo?,
        text: String
    ) {

        if (node == null) return

        try {

            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }

            val success = node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )

            // fallback delay (human pause after typing)
            if (success) {
                humanDelay(120, 350)
            }

        } catch (e: Exception) {
            // silently fail (accessibility nodes are unstable)
        }
    }

    // -----------------------------
    // 4. HUMAN "THINKING PAUSE"
    // -----------------------------
    suspend fun thinkingPause() {
        humanDelay(200, 600)
    }
}
