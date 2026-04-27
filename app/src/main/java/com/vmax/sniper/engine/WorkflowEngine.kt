package com.vmax.sniper.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * 🦅 VMAX SNIPER HYBRID FINAL
 * यह इंजन अब 10/10 है। इसमें ID सर्च, रैंडम डिले और उंगली की हलचल (Micro-shake) शामिल है।
 */
class WorkflowEngine : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val TAG = "VMAX_Sniper_PRO"
        var isSniperActive = false
        var isProcessing = false // 🛡️ डुप्लीकेट क्लिक रोकने का लॉक
        const val IRCTC_PKG = "cris.org.in.prs.ima"
        
        // IRCTC ऐप के असली View IDs (77 Points)
        const val ID_NAME = "$IRCTC_PKG:id/et_passenger_name"
        const val ID_ADD_BTN = "$IRCTC_PKG:id/tv_add_passanger"
        const val ID_PROCEED = "$IRCTC_PKG:id/btn_proceed"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 1. क्या स्नाइपर चालू है? क्या कोई काम पहले से चल रहा है?
        if (!isSniperActive || isProcessing) return
        
        // 2. क्या स्क्रीन पूरी तरह बदल चुकी है? (Stability Check)
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val rootNode = rootInActiveWindow ?: return
        
        // 3. क्या हम IRCTC ऐप के अंदर हैं?
        if (rootNode.packageName != IRCTC_PKG) return

        // 4. क्या हम पैसेंजर वाली स्क्रीन पर पहुँच गए हैं?
        val screenCheck = findNodeByLabelsOrId(rootNode, listOf("Passenger Details", "यात्री विवरण"), ID_NAME)
        if (screenCheck == null) return

        // 🔒 लॉक लगाओ ताकि एक ही स्क्रीन पर बार-बार कोड न चले
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
                Log.e(TAG, "Assault Failed: ${e.message}")
            } finally {
                // 🔓 काम खत्म, अब लॉक खोलो और स्नाइपर को शांत करो
                isProcessing = false 
                isSniperActive = false 
            }
        }
    }

    private suspend fun executeAssault(names: List<String>) {
        delay(100) // स्क्रीन सेटल होने का छोटा इंतज़ार
        var currentRoot = rootInActiveWindow ?: return
        
        for ((index, name) in names.withIndex()) {
            // 🛡️ रैंडम स्क्रॉल (30% चांस - असली इंसान की तरह)
            if (Random.nextInt(100) < 30) {
                performGlobalAction(GLOBAL_ACTION_SCROLL_FORWARD)
                delay(Random.nextLong(200, 450))
                currentRoot = rootInActiveWindow ?: return 
            }

            if (index == 0) {
                // पैसेंजर 1: सीधा भरें
                val nameField = findNodeByLabelsOrId(currentRoot, listOf("Name", "यात्री का नाम"), ID_NAME)
                nameField?.let { 
                    delay(Random.nextLong(300, 600))
                    fillTextField(it, name) 
                }
            } else {
                // बाकी पैसेंजर: क्लिक -> रिफ्रेश -> फिल
                val addButton = findNodeByLabelsOrId(currentRoot, listOf("Add New", "Add Passenger"), ID_ADD_BTN)
                
                if (addButton != null) {
                    delay(Random.nextLong(200, 400))
                    humanClick(addButton) // 👆 असली उंगली वाला टैप
                    
                    delay(Random.nextLong(750, 1100)) // फॉर्म खुलने का इंतज़ार
                    currentRoot = rootInActiveWindow ?: return 
                    
                    val nextNameField = findNodeByLabelsOrId(currentRoot, listOf("Name"), ID_NAME)
                    nextNameField?.let {
                        delay(Random.nextLong(250, 500))
                        fillTextField(it, name)
                        
                        delay(Random.nextLong(400, 700))
                        findNodeByLabelsOrId(currentRoot, listOf("Save", "जोड़ें"), "")
                            ?.let { btn -> humanClick(btn) }
                        
                        delay(400)
                        currentRoot = rootInActiveWindow ?: return
                    }
                }
            }
        }
        
        // 🎯 फाइनल 'Continue' बटन पर धावा
        delay(Random.nextLong(800, 1200))
        currentRoot = rootInActiveWindow ?: return
        findNodeByLabelsOrId(currentRoot, listOf("Review Journey", "Continue", "अभी बुक करें"), ID_PROCEED)
            ?.let { humanClick(it) }
    }

    // ==================== 🛠️ HUMAN CLICK (The Stealth Feature) ====================
    private fun humanClick(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        // बटन के सेंटर से थोड़ा हटकर (Random Offset)
        val finalX = (bounds.centerX() + (-12..12).random()).toFloat()
        val finalY = (bounds.centerY() + (-12..12).random()).toFloat()

        val path = Path().apply { 
            moveTo(finalX, finalY) 
            // 🛡️ Micro-shake: उंगली की सूक्ष्म हलचल (इंसानी कंपन)
            lineTo(finalX + (1..2).random().toFloat(), finalY + (1..2).random().toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 45))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun fillTextField(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findNodeByLabelsOrId(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        // पहले ID से ढूँढो (यह सबसे तेज़ है)
        if (viewId.isNotEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        // फिर टेक्स्ट से ढूँढो (Fallback)
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isClickable || node.isEditable || (node.parent?.isClickable == true)) return node
            }
        }
        return null
    }

    override fun onInterrupt() { isSniperActive = false; isProcessing = false }
    override fun onDestroy() { super.onDestroy(); serviceScope.cancel() }
}
