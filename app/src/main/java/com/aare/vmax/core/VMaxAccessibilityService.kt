package com.aare.vmax.core.orchestrator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Calendar

class VMAXAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_TERMINATOR"
        private const val PREFS = "VMaxProfile"
        private const val ACTION_START = "com.aare.vmax.ACTION_START_AUTOMATION"
        private const val DEBOUNCE = 300L
        private const val DEFAULT_LATENCY = 400
        private const val MAX_RETRY = 3
    }

    enum class Step { IDLE, DASHBOARD, TRAIN, PASSENGER, OPTIONS, REVIEW, PAYMENT, DONE, ERROR }

    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var active = false
    private var step = Step.IDLE
    private var retry = 0
    private var lastAction = 0L
    private var filling = false
    private var captchaPause = false
    private var passengersDone = 0
    private var totalPassengers = 1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { if (i?.action == ACTION_START) start(i) }
    }

    override fun onServiceConnected() {
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = arrayOf("cris.org.in.prs.ima", "com.irctc.railconnect", "in.irctc.railconnect")
            notificationTimeout = 100
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(ACTION_START), ContextCompat.RECEIVER_EXPORTED)
        Log.d(TAG, "✅ TERMINATOR READY")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!active || event == null || captchaPause) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val now = System.currentTimeMillis()
        if (now - lastAction < DEBOUNCE && !filling) return
        val root = rootInActiveWindow ?: return

        // 🚀 Fast-Forward: सीधा ट्रेन लिस्ट पर हमला
        if (step == Step.DASHBOARD) {
            val train = prefs.getString("train", "") ?: ""
            val cls = prefs.getString("class", "SL") ?: "SL"
            if (findTextContains(root, train) != null || findTextContains(root, cls) != null) {
                step = Step.TRAIN
            }
        }

        try {
            when (step) {
                Step.DASHBOARD -> handleDashboard(root)
                Step.TRAIN -> handleTrain(root)
                Step.PASSENGER -> handlePassenger(root)
                Step.OPTIONS -> handleOptions(root)
                Step.REVIEW -> handleReview(root)
                Step.PAYMENT -> handlePayment()
                else -> {}
            }
        } catch (e: Exception) { error("Crash ${e.message}") }
    }

    private fun handleDashboard(root: AccessibilityNodeInfo) {
        if (clickText(root, "OK")) return
        if (findText(root, "Book Ticket") != null) { step = Step.TRAIN; toast("Dashboard") }
    }

    private fun handleTrain(root: AccessibilityNodeInfo) {
        val train = prefs.getString("train", "") ?: ""
        val cls = prefs.getString("class", "SL") ?: "SL"
        if (shouldWait(cls)) return
        val node = findTextContains(root, train)
        if (node != null) {
            retry = 0
            if (clickDesc(root, cls)) step = Step.PASSENGER
            node.recycle()
        } else { if (!clickText(root, "Refresh")) retry++ }
        if (retry > MAX_RETRY) error("Train not found")
    }

    private fun handlePassenger(root: AccessibilityNodeInfo) {
        val nameField = findEdit(root, "Name")
        if (nameField != null) { if (!filling) fillPassenger(nameField) }
        else { clickDesc(root, "PASSENGER DETAILS") }
    }

    private fun fillPassenger(node: AccessibilityNodeInfo) {
        filling = true
        scope.launch {
            try {
                val i = passengersDone
                val name = prefs.getString("name_$i", "") ?: ""
                val age = prefs.getString("age_$i", "25") ?: "25"
                val gender = prefs.getString("gender_$i", "Male") ?: "Male"
                val meal = prefs.getString("meal_$i", "No Food") ?: "No Food"
                val d = prefs.getInt("latency_ms", DEFAULT_LATENCY).toLong()

                input(node, name); node.recycle(); delay(d)

                val r1 = rootInActiveWindow ?: return@launch
                findEdit(r1, "Age")?.let { input(it, age); it.recycle() }
                delay(d)

                val r2 = rootInActiveWindow ?: return@launch
                clickDesc(r2, gender); delay(d)

                // 🍴 Meal Exact Mapping (पॉपअप से मिलान)
                if (meal != "No Food" && meal.isNotEmpty()) {
                    val rMeal = rootInActiveWindow ?: return@launch
                    findText(rMeal, "Meal Preference")?.let { clickNode(it); it.recycle() } 
                        ?: findTextContains(rMeal, "Catering")?.let { clickNode(it); it.recycle() }
                    delay(d * 2)

                    val rMealOpt = rootInActiveWindow ?: return@launch
                    when (meal.lowercase()) {
                        "veg" -> findText(rMealOpt, "Veg")
                        "non veg", "non-veg" -> findText(rMealOpt, "Non Veg")
                        "jain meal" -> findText(rMealOpt, "Jain Meal")
                        "veg diabetic" -> findText(rMealOpt, "Veg (Diabetic)")
                        "non veg diabetic" -> findText(rMealOpt, "Non Veg (Diabetic)")
                        else -> findText(rMealOpt, meal)
                    }?.let { clickNode(it); it.recycle() }
                    delay(d)
                }

                passengersDone++
                if (passengersDone < totalPassengers) {
                    val r3 = rootInActiveWindow ?: return@launch
                    findTextContains(r3, "Add New")?.let { clickNode(it); it.recycle() }
                    delay(d * 2)
                } else { step = Step.OPTIONS }
            } finally { filling = false; lastAction = System.currentTimeMillis() }
        }
    }

    private fun handleOptions(root: AccessibilityNodeInfo) {
        scope.launch {
            val d = prefs.getInt("latency_ms", DEFAULT_LATENCY).toLong()
            
            // 📱 Mobile Number Edit
            val mobile = prefs.getString("mobile_number", "") ?: ""
            if (mobile.isNotEmpty()) {
                findText(root, "Edit")?.let { clickNode(it); it.recycle(); delay(d) }
                rootInActiveWindow?.let { r ->
                    findEdit(r, "Mobile")?.let { input(it, mobile); it.recycle(); delay(d) }
                }
            }

            val currentRoot = rootInActiveWindow ?: return@launch
            val firstMeal = prefs.getString("meal_0", "No Food") ?: "No Food"
            
            // 🍽️ I don't want Food Checkbox
            if (firstMeal == "No Food") {
                findTextContains(currentRoot, "don't want Food/Beverages")?.let { clickNode(it); it.recycle() }
            }

            if (prefs.getBoolean("auto_upgrade", false)) { 
                findTextContains(currentRoot, "Consider for auto upgradation")?.let { clickNode(it); it.recycle() } 
            }
            if (prefs.getBoolean("confirm_only", false)) { 
                findTextContains(currentRoot, "Book Only If Confirm Berth Are Allocated")?.let { clickNode(it); it.recycle() } 
            }

            // 🚀 Final Review Details Button
            findTextContains(currentRoot, "REVIEW JOURNEY DETAILS")?.let {
                clickNode(it); it.recycle()
                step = Step.REVIEW; captchaPause = true; toast("Solving Captcha")
            }
        }
    }

    private fun handleReview(root: AccessibilityNodeInfo) {
        if (findTextContains(root, "PAYMENT") != null) { step = Step.PAYMENT; captchaPause = false }
    }

    private fun handlePayment() {
        scope.launch {
            try {
                val method = prefs.getString("payment_method", "PhonePe") ?: "PhonePe"
                val d = prefs.getInt("latency_ms", DEFAULT_LATENCY).toLong()
                
                val r1 = waitForRoot(5, d) ?: return@launch
                if (method.contains("Wallet", true)) clickDesc(r1, "Wallet") else clickDesc(r1, "BHIM")
                delay(d)
                
                val r2 = waitForRoot(5, d) ?: return@launch
                traverse(r2) { it.contentDescription?.toString()?.equals(method, true) == true || it.text?.toString()?.equals(method, true) == true }?.let { clickNode(it); it.recycle() }
                delay(d)
                
                val r3 = waitForRoot(5, d) ?: return@launch
                findTextContains(r3, "Pay")?.let { clickNode(it); it.recycle(); active = false; toast("✅ DONE") }
            } catch (e: Exception) { error("Payment Fail") }
        }
    }

    private fun shouldWait(cls: String): Boolean {
        val cal = Calendar.getInstance(); val hour = cal.get(Calendar.HOUR_OF_DAY); val min = cal.get(Calendar.MINUTE)
        val isAC = cls in listOf("1A", "2A", "3A", "3E", "CC", "EC")
        val target = when { hour < 9 -> 8; isAC -> 10; else -> 11 }
        return hour == target - 1 && min >= 55
    }

    private fun findText(root: AccessibilityNodeInfo, t: String) = root.findAccessibilityNodeInfosByText(t).firstOrNull { it.text?.toString()?.equals(t, true) == true }
    private fun findTextContains(root: AccessibilityNodeInfo, t: String) = root.findAccessibilityNodeInfosByText(t).firstOrNull { it.text?.toString()?.contains(t, true) == true }
    private fun findEdit(root: AccessibilityNodeInfo, t: String) = traverse(root) { it.isEditable && (it.text?.toString()?.contains(t, true) == true || it.hintText?.toString()?.contains(t, true) == true || it.contentDescription?.toString()?.contains(t, true) == true) }

    private fun traverse(n: AccessibilityNodeInfo?, f: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (f(n)) return AccessibilityNodeInfo.obtain(n)
        for (i in 0 until n.childCount) {
            val c = n.getChild(i); val r = traverse(c, f); c?.recycle()
            if (r != null) return r
        }
        return null
    }

    private fun clickText(root: AccessibilityNodeInfo, t: String) = findText(root, t)?.let { clickNode(it); it.recycle(); true } ?: false
    private fun clickDesc(root: AccessibilityNodeInfo, d: String) = traverse(root) { it.contentDescription?.toString()?.contains(d, true) == true }?.let { clickNode(it); it.recycle(); true } ?: false

    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var t: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(n)
        while (t != null) {
            if (t.isClickable) {
                if (t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { lastAction = System.currentTimeMillis(); t.recycle(); return true }
            }
            val p = t.parent; t.recycle(); t = p?.let { AccessibilityNodeInfo.obtain(it) }
        }
        return false
    }

    private fun input(n: AccessibilityNodeInfo, txt: String) {
        val b = Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, txt)
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
        lastAction = System.currentTimeMillis()
    }

    private suspend fun waitForRoot(attempts: Int, delay: Long): AccessibilityNodeInfo? {
        repeat(attempts) { rootInActiveWindow?.let { return it }; delay(delay) }
        return null
    }

    private fun start(i: Intent) { reset(); totalPassengers = i.getIntExtra("passengers", 1).coerceIn(1, 6); active = true; step = Step.DASHBOARD; toast("ARMED") }
    private fun reset() { active = false; step = Step.IDLE; retry = 0; passengersDone = 0; captchaPause = false; filling = false; lastAction = 0 }
    private fun error(msg: String) { active = false; step = Step.ERROR; toast(msg) }
    private fun toast(m: String) { scope.launch { try { Toast.makeText(applicationContext, m, Toast.LENGTH_SHORT).show() } catch (e: Exception) {} } }
    override fun onInterrupt() { reset() }
    override fun onDestroy() { super.onDestroy(); try { unregisterReceiver(receiver) } catch (e: Exception) {}; scope.cancel() }
}

