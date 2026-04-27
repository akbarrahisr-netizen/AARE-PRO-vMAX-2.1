package com.vmax.sniper.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlin.random.Random

class WorkflowEngine : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        var isSniperActive = false
        var isProcessing = false 
        var targetClass = "" 
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isSniperActive || isProcessing) return
        
        // 🛡️ 10/10 FIX: Stability + Event Filtering
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val rootNode = rootInActiveWindow ?: return
        val screenCheck = findNodeByLabels(rootNode, listOf("Passenger Details", "यात्री विवरण", "Add New"))
        if (screenCheck == null) return

        isProcessing = true 

        serviceScope.launch {
            try {
                val sharedPrefs = getSharedPreferences("VMAX_DATA", Context.MODE_PRIVATE)
                val namesRaw = sharedPrefs.getString("PASSENGER_LIST", "") ?: ""
                val passengerNames = namesRaw.split(",").filter { it.isNotBlank() }
                
                if (passengerNames.isNotEmpty()) {
                    executeAssault(passengerNames)
                }
            } catch (e: Exception) {
                Log.e("VMAX", "Assault Failed: ${e.message}")
            } finally {
                isProcessing = false 
                isSniperActive = false 
            }
        }
    }

    private suspend fun executeAssault(names: List<String>) {
        var currentRoot = rootInActiveWindow ?: return
        
        for ((index, name) in names.withIndex()) {
            // 🛡️ 10/10 FIX: Random Scrolling (30% Chance)
            if (Random.nextInt(100) < 30) {
                performGlobalAction(GLOBAL_ACTION_SCROLL_FORWARD)
                delay(Random.nextLong(200, 500))
            }

            if (index == 0) {
                val nameField = findNodeByLabels(currentRoot, listOf("Name", "Full Name", "Passenger Name", "यात्री का नाम"))
                nameField?.let { 
                    delay(Random.nextLong(300, 600))
                    fillTextField(it, name) 
                }
            } else {
                currentRoot = rootInActiveWindow ?: return
                val addButton = findNodeByLabels(currentRoot, listOf("Add New", "Add Adult", "Add Passenger", "नया जोड़ें"))
                
                if (addButton != null) {
                    delay(Random.nextLong(200, 450))
                    // 🛡️ 10/10 FIX: Click with Random Offset (Human Touch)
                    addButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    
                    delay(Random.nextLong(700, 1100)) // Form load
                    currentRoot = rootInActiveWindow ?: return 
                    
                    val nextNameField = findNodeByLabels(currentRoot, listOf("Name", "Full Name", "Passenger Name"))
                    nextNameField?.let {
                        delay(Random.nextLong(250, 500))
                        fillTextField(it, name)
                        
                        delay(Random.nextLong(400, 700))
                        findNodeByLabels(currentRoot, listOf("Save", "Add Passenger", "जोड़ें"))
                            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
            }
        }
        
        // 🎯 Final Proceed
        delay(Random.nextLong(800, 1200))
        currentRoot = rootInActiveWindow ?: return
        findNodeByLabels(currentRoot, listOf("Review Journey", "Continue", "आगे बढ़ें", "अभी बुक करें"))
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun fillTextField(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findNodeByLabels(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isClickable || node.isEditable || (node.parent?.isClickable == true)) {
                    return node
                }
            }
        }
        return null
    }

    override fun onInterrupt() { isSniperActive = false; isProcessing = false }
    override fun onDestroy() { super.onDestroy(); serviceScope.cancel() }
}
