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

class WorkflowEngine : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val TAG = "VMAX_Sniper_PRO"
        var isSniperActive = false
        var isProcessing = false 
        
        const val IRCTC_PKG = "cris.org.in.prs.ima"
        const val ID_NAME = "$IRCTC_PKG:id/et_passenger_name"
        const val ID_AGE = "$IRCTC_PKG:id/et_passenger_age"
        const val ID_GENDER = "$IRCTC_PKG:id/et_gender"
        const val ID_BERTH = "$IRCTC_PKG:id/et_berth_preference"
        const val ID_MEAL = "$IRCTC_PKG:id/et_meal" // 🆕 Added
        const val ID_CHILD_NAME = "$IRCTC_PKG:id/et_child_name" // 🆕 Added
        const val ID_CHILD_AGE = "$IRCTC_PKG:id/spinner_child_age" // 🆕 Added
        const val ID_CHILD_GENDER = "$IRCTC_PKG:id/spinner_child_gender" // 🆕 Added
        
        const val ID_ADD_BTN = "$IRCTC_PKG:id/tv_add_passanger"
        const val ID_PROCEED = "$IRCTC_PKG:id/btn_proceed"
        const val ID_CAPTCHA_IMG = "$IRCTC_PKG:id/iv_captcha"
        const val ID_CAPTCHA_INPUT = "$IRCTC_PKG:id/et_captcha"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isSniperActive || isProcessing) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val rootNode = rootInActiveWindow ?: return
        if (rootNode.packageName != IRCTC_PKG) return

        val screenCheck = findNodeByLabelsOrId(rootNode, listOf("Passenger Details", "यात्री विवरण"), ID_NAME)
        if (screenCheck == null) return

        isProcessing = true 

        serviceScope.launch {
            try {
                // 📂 डेटा फॉरमेट: "Name|Age|Gender|Berth|Meal|ChildName|ChildAge|ChildGender"
                val sharedPrefs = getSharedPreferences("VMAX_DATA", Context.MODE_PRIVATE)
                val namesRaw = sharedPrefs.getString("PASSENGER_LIST", "") ?: ""
                val passengerList = namesRaw.split(",").filter { it.isNotBlank() }
                
                if (passengerList.isNotEmpty()) {
                    executeTagTeamStrategy(passengerList)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Strategy Failed: ${e.message}")
            } finally {
                isProcessing = false 
                isSniperActive = false 
            }
        }
    }

    private suspend fun executeTagTeamStrategy(passengers: List<String>) {
        delay(100) 
        var currentRoot = rootInActiveWindow ?: return
        
        // 🚄 1. फुल पैसेंजर फॉर्म भरना
        for ((index, passData) in passengers.withIndex()) {
            
            // डेटा को अलग-अलग करना
            val details = passData.split("|")
            val pName = details.getOrNull(0) ?: ""
            val pAge = details.getOrNull(1) ?: ""
            val pGender = details.getOrNull(2) ?: ""
            val pBerth = details.getOrNull(3) ?: ""
            val pMeal = details.getOrNull(4) ?: ""
            val cName = details.getOrNull(5) ?: ""
            val cAge = details.getOrNull(6) ?: ""
            val cGender = details.getOrNull(7) ?: ""

            if (Random.nextInt(100) < 25) {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_SCROLL_FORWARD)
                delay(Random.nextLong(200, 400))
                currentRoot = rootInActiveWindow ?: return 
            }

            if (index > 0) {
                val addButton = findNodeByLabelsOrId(currentRoot, listOf("Add New", "Add Passenger"), ID_ADD_BTN)
                addButton?.let {
                    humanClickAdvanced(it)
                    delay(Random.nextLong(600, 900))
                    currentRoot = rootInActiveWindow ?: return
                }
            }

            // 👤 Adult Details
            findNodeByLabelsOrId(currentRoot, listOf("Name", "यात्री का नाम"), ID_NAME)?.let { 
                fillTextField(it, pName)
                delay(Random.nextLong(150, 300))
            }
            if (pAge.isNotBlank()) {
                findNodeByLabelsOrId(currentRoot, listOf("Age", "आयु"), ID_AGE)?.let { 
                    fillTextField(it, pAge)
                    delay(Random.nextLong(150, 300))
                }
            }
            if (pGender.isNotBlank()) selectSpinnerOption(currentRoot, ID_GENDER, pGender)
            if (pBerth.isNotBlank()) selectSpinnerOption(currentRoot, ID_BERTH, pBerth)
            if (pMeal.isNotBlank()) selectSpinnerOption(currentRoot, ID_MEAL, pMeal) // 🍔 Meal Added

            // 👶 Child Details (अगर डेटा में है तो)
            if (cName.isNotBlank()) {
                findNodeByLabelsOrId(currentRoot, listOf("Infant Name", "शिशु का नाम"), ID_CHILD_NAME)?.let {
                    fillTextField(it, cName)
                    delay(Random.nextLong(150, 300))
                }
                if (cAge.isNotBlank()) selectSpinnerOption(currentRoot, ID_CHILD_AGE, cAge)
                if (cGender.isNotBlank()) selectSpinnerOption(currentRoot, ID_CHILD_GENDER, cGender)
            }

            // Save बटन दबाना
            if (index > 0 || passengers.size == 1) { 
                findNodeByLabelsOrId(currentRoot, listOf("Save", "जोड़ें", "Add Passenger"), "")?.let { btn -> 
                    humanClickAdvanced(btn) 
                    delay(500)
                    currentRoot = rootInActiveWindow ?: return
                }
            }
        }
        
        delay(Random.nextLong(500, 800))
        currentRoot = rootInActiveWindow ?: return
        
        // 🚀 2. Review Journey / Proceed बटन दबाना
        findNodeByLabelsOrId(currentRoot, listOf("Review Journey Details", "Continue", "अभी बुक करें"), ID_PROCEED)?.let { 
            humanClickAdvanced(it) 
        }

        // ⏱️ 3. स्मार्ट रडार (म्यूजिक/लोडिंग साफ़ करना)
        var captchaFound = false
        for (i in 1..150) { 
            delay(1000) 
            val root = rootInActiveWindow ?: continue

            findNodeByLabelsOrId(root, listOf("OK", "ठीक है"), "")?.let { humanClickAdvanced(it); continue }
            findNodeByLabelsOrId(root, listOf("YES", "हाँ"), "")?.let { humanClickAdvanced(it); continue }

            val payBtn = findNodeByLabelsOrId(root, listOf("Proceed to Pay", "भुगतान के लिए आगे बढ़ें"), "")
            if (payBtn != null) {
                captchaFound = true
                break 
            }
        }
        
        if (captchaFound) {
            // 🤖 4. ऑटो-अटेम्प्ट (कैप्टचा बाईपास)
            delay(Random.nextLong(400, 700))
            var finalRoot = rootInActiveWindow ?: return
            
            val captchaField = findNodeByLabelsOrId(finalRoot, listOf("Enter the captcha", "Captcha", "कैप्चा"), ID_CAPTCHA_INPUT)
            if (captchaField != null) {
                humanClickAdvanced(captchaField) 
                delay(Random.nextLong(1500, 2200)) // 2 सेकंड इंतज़ार
                
                finalRoot = rootInActiveWindow ?: return
                findNodeByLabelsOrId(finalRoot, listOf("Proceed to Pay", "भुगतान के लिए आगे बढ़ें"), "")?.let { humanClickAdvanced(it) }

                // 🛑 5. डीप चेक: टैग-टीम एक्टिवेशन
                delay(3000) 
                finalRoot = rootInActiveWindow ?: return
                val stillOnSamePage = findNodeByLabelsOrId(finalRoot, listOf("Enter the captcha", "Captcha"), ID_CAPTCHA_INPUT)
                
                if (stillOnSamePage != null) {
                    Log.d(TAG, "⚠️ बाईपास फेल! टैग-टीम एक्टिवेट हो रही है...")
                    
                    // 🔄 कैप्टचा रिफ्रेश
                    findNodeByLabelsOrId(finalRoot, listOf("Refresh"), ID_CAPTCHA_IMG)?.let { 
                        humanClickAdvanced(it) 
                        delay(600)
                    }

                    // ⌨️ कीबोर्ड खोलना
                    finalRoot = rootInActiveWindow ?: return
                    findNodeByLabelsOrId(finalRoot, listOf("Enter the captcha", "Captcha"), ID_CAPTCHA_INPUT)?.let { 
                        humanClickAdvanced(it) 
                    }

                    // ⏳ 6. इंसान का 90 सेकंड इंतज़ार
                    var paymentPageReached = false
                    for (j in 1..90) { 
                        delay(1000)
                        val pRoot = rootInActiveWindow ?: continue
                        val upiOption = findNodeByLabelsOrId(pRoot, listOf("BHIM/ UPI", "UPI", "Autopay", "IRCTC iPay", "Wallets"), "")
                        if (upiOption != null) {
                            paymentPageReached = true
                            break
                        }
                    }

                    // 🚀 7. "रो बैठे चालू हो जाए!" (ऑटो-पेमेंट)
                    if (paymentPageReached) {
                        delay(800)
                        var pRoot = rootInActiveWindow ?: return
                        
                        findNodeByLabelsOrId(pRoot, listOf("BHIM/ UPI", "UPI", "Autopay", "IRCTC iPay"), "")?.let {
                            humanClickAdvanced(it)
                            delay(500)
                        }
                        
                        pRoot = rootInActiveWindow ?: return
                        val finalPayBtn = findNodeByLabelsOrId(pRoot, listOf("PROCEED TO PAY", "Pay", "Make Payment", "Pay ₹", "भुगतान करें"), "")
                        finalPayBtn?.let { humanClickAdvanced(it) }
                    }
                }
            }
        }
    }

    private suspend fun selectSpinnerOption(root: AccessibilityNodeInfo, spinnerId: String, optionText: String) {
        val spinner = findNodeByLabelsOrId(root, emptyList(), spinnerId)
        spinner?.let {
            humanClickAdvanced(it) 
            delay(Random.nextLong(200, 400))
            val newRoot = rootInActiveWindow ?: return
            
            val optionToClick = findNodeByLabelsOrId(newRoot, listOf(optionText), "")
            optionToClick?.let { opt -> 
                humanClickAdvanced(opt) 
                delay(Random.nextLong(150, 300))
            }
        }
    }

    private fun humanClickAdvanced(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val finalX = (bounds.centerX() + (-12..12).random()).toFloat()
        val finalY = (bounds.centerY() + (-12..12).random()).toFloat()
        val path = Path().apply {
            moveTo(finalX, finalY)
            lineTo(finalX + (1..3).random().toFloat(), finalY + (1..3).random().toFloat())
        }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, Random.nextLong(40, 60))).build()
        dispatchGesture(gesture, null, null)
    }

    private fun fillTextField(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findNodeByLabelsOrId(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isClickable || node.isEditable || (node.parent?.isClickable == true)) return node
            }
        }
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                val nodeText = node.text?.toString()?.uppercase() ?: continue
                if (nodeText.contains(label.uppercase())) {
                    if (node.isClickable || node.isEditable || (node.parent?.isClickable == true)) return node
                }
            }
        }
        return null
    }

    override fun onInterrupt() { isSniperActive = false; isProcessing = false }
    override fun onDestroy() { super.onDestroy(); serviceScope.cancel() }
}
