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

    // ═══════════════════════════════════════════════════════
    // ⚙️ CONSTANTS & CONFIG
    // ═══════════════════════════════════════════════════════
    companion object {
        private const val TAG = "VMAX_FINAL"
        private const val PREFS = "VMaxProfile"
        private const val ACTION_START = "com.aare.vmax.ACTION_START_AUTOMATION"

        private const val DEBOUNCE = 300L
        private const val DEFAULT_LATENCY = 400
        private const val MAX_RETRY = 3
    }

    // ═══════════════════════════════════════════════════════
    // 🧠 STATE MACHINE
    // ═══════════════════════════════════════════════════════
    enum class Step {
        IDLE, DASHBOARD, TRAIN, PASSENGER,
        OPTIONS, REVIEW, PAYMENT, DONE, ERROR
    }

    // ═══════════════════════════════════════════════════════
    // 🔧 CORE VARIABLES
    // ═══════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════
    // 📡 BROADCAST RECEIVER
    // ═══════════════════════════════════════════════════════
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == ACTION_START) start(i)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🔄 LIFECYCLE
    // ═══════════════════════════════════════════════════════
    override fun onServiceConnected() {
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

            packageNames = arrayOf(
                "cris.org.in.prs.ima",
                "com.irctc.railconnect",
                "in.irctc.railconnect"
            )

            notificationTimeout = 100
        }

        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(ACTION_START),
            ContextCompat.RECEIVER_EXPORTED
        )

        Log.d(TAG, "✅ VMAX 100% READY")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!active || event == null || captchaPause) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val now = System.currentTimeMillis()
        if (now - lastAction < DEBOUNCE && !filling) return

        val root = rootInActiveWindow ?: return

        // ✅ 100% FIXED: Fast-Forward (अब यह आपकी चुनी हुई क्लास को पहचानेगा, सिर्फ SL को नहीं)
        if (step == Step.DASHBOARD) {
            val train = prefs.getString("train", "") ?: ""
            val cls = prefs.getString("class", "SL") ?: "SL"
            
            if (findTextContains(root, train) != null || findTextContains(root, cls) != null) {
                step = Step.TRAIN
                Log.d(TAG, "🎯 Fast-forwarded to TRAIN search!")
            }
        }

        try {
            when (step) {
                Step.DASHBOARD -> handleDashboard(root)
                Step.TRAIN -> handleTrain(root)
                Step.PASSENGER -> handlePassenger(root)
                Step.OPTIONS -> handleOptions(root)
                Step.REVIEW -> handleReview(root)
                Step.PAYMENT -> handlePayment(root)
                else -> {}
            }
        } catch (e: Exception) {
            error("Crash ${e.message}")
        }
    }

    override fun onInterrupt() {
        reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        scope.cancel()
        Log.d(TAG, "🔌 Service destroyed")
    }

    // ═══════════════════════════════════════════════════════
    // 🧭 STATE HANDLERS
    // ═══════════════════════════════════════════════════════

    private fun handleDashboard(root: AccessibilityNodeInfo) {
        if (clickText(root, "OK")) return
        if (findText(root, "Book Ticket") != null) {
            step = Step.TRAIN
            toast("Dashboard detected")
        }
    }

    private fun handleTrain(root: AccessibilityNodeInfo) {
        val train = prefs.getString("train", "") ?: ""
        val cls = prefs.getString("class", "SL") ?: "SL"

        // ⏱️ Sniper Timing
        if (shouldWait(cls)) return

        val node = findTextContains(root, train)

        if (node != null) {
            if (clickDesc(root, cls)) {
                step = Step.PASSENGER
                retry = 0
            }
            node.recycle() // ✅ Memory Leak Fix
        } else {
            if (!clickText(root, "Refresh")) retry++
        }

        if (retry > MAX_RETRY) error("Train not found")
    }

    private fun handlePassenger(root: AccessibilityNodeInfo) {
        val nameField = findEdit(root, "Name")

        if (nameField != null) {
            if (!filling) fillPassenger(root, nameField)
            // Note: fillPassenger will recycle nameField after using it
        } else {
            clickDesc(root, "PASSENGER DETAILS")
        }
    }

    private fun fillPassenger(root: AccessibilityNodeInfo, node: AccessibilityNodeInfo) {
        filling = true

        scope.launch {
            try {
                val i = passengersDone
                val name = prefs.getString("name_$i", "") ?: ""
                val age = prefs.getString("age_$i", "25") ?: "25"
                val gender = prefs.getString("gender_$i", "Male") ?: "Male"
                val meal = prefs.getString("meal_$i", "No Food") ?: "No Food"
                val d = prefs.getInt("latency_ms", DEFAULT_LATENCY).toLong()

                // 📝 Name
                input(node, name)
                node.recycle() // ✅ Memory Leak Fix
                delay(d)

                // 🔢 Age
                findEdit(root, "Age")?.let { 
                    input(it, age)
                    it.recycle() // ✅ Memory Leak Fix
                }
                delay(d)

                // 👤 Gender
                clickDesc(root, gender)
                delay(d)

                // 🍴 Meal
                if (meal != "No Food" && meal.isNotEmpty()) {
                    val r1 = rootInActiveWindow ?: return@launch
                    val mealBtn = findText(r1, "Meal Preference") 
                               ?: findTextContains(r1, "Meal") 
                               ?: findTextContains(r1, "Food")
                    mealBtn?.let { 
                        clickNode(it)
                        it.recycle() // ✅ Memory Leak Fix
                    }
                    delay(d)

                    val r2 = rootInActiveWindow ?: return@launch
                    clickDesc(r2, meal)
                    delay(d)
                }

                passengersDone++

                // ➕ Add next
                if (passengersDone < totalPassengers) {
                    val addRoot = rootInActiveWindow ?: return@launch
                    val addBtn = findText(addRoot, "+ Add New")
                        ?: findTextContains(addRoot, "Add New")
                    addBtn?.let { 
                        clickNode(it)
                        it.recycle() // ✅ Memory Leak Fix
                    }
                    delay(d * 2)
                } else {
                    step = Step.OPTIONS
                }

            } finally {
                filling = false
                lastAction = System.currentTimeMillis()
            }
        }
    }

    private fun handleOptions(root: AccessibilityNodeInfo) {
        if (prefs.getBoolean("auto_upgrade", false)) {
            clickDesc(root, "Consider Auto upgradation")
        }
        if (prefs.getBoolean("confirm_only", false)) {
            clickDesc(root, "Book only if confirm berths")
        }
        if (clickDesc(root, "REVIEW JOURNEY")) {
            step = Step.REVIEW
            captchaPause = true
            toast("Solve captcha manually")
        }
    }

    private fun handleReview(root: AccessibilityNodeInfo) {
        val paymentNode = findTextContains(root, "PAYMENT")
        if (paymentNode != null) {
            step = Step.PAYMENT
            captchaPause = false
            paymentNode.recycle() // ✅ Memory Leak Fix
        }
    }

    private fun handlePayment(root: AccessibilityNodeInfo) {
        scope.launch {
            try {
                val method = prefs.getString("payment_method", "PhonePe") ?: "PhonePe"

                if (method.contains("Wallet", true) || method.contains("Mobikwik", true)) {
                    clickDesc(root, "Wallet")
                } else {
                    clickDesc(root, "BHIM")
                }

                delay(500)

                val r1 = rootInActiveWindow ?: return@launch
                val payMethodBtn = traverse(r1) { 
                    it.contentDescription?.toString()?.equals(method, true) == true ||
                    it.text?.toString()?.equals(method, true) == true
                }
                payMethodBtn?.let { 
                    clickNode(it)
                    it.recycle() // ✅ Memory Leak Fix
                }

                delay(500)

                val r2 = rootInActiveWindow ?: return@launch
                val payBtn = findTextContains(r2, "Pay")
                payBtn?.let {
                    clickNode(it)
                    it.recycle() // ✅ Memory Leak Fix
                    active = false
                    toast("Payment started")
                }

            } catch (e: Exception) {
                error("Payment failed")
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // ⏱️ SNIPER TIMING ENGINE
    // ═══════════════════════════════════════════════════════
    private fun shouldWait(cls: String): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)

        val isAC = cls in listOf("1A", "2A", "3A", "3E", "CC", "EC")

        val target = when {
            hour < 9 -> 8
            isAC -> 10
            else -> 11
        }

        return hour == target - 1 && min >= 55
    }

    // ═══════════════════════════════════════════════════════
    // 🛠️ NODE FINDING UTILS (Memory-Safe)
    // ═══════════════════════════════════════════════════════

    private fun findText(root: AccessibilityNodeInfo, t: String) =
        root.findAccessibilityNodeInfosByText(t)
            .firstOrNull { it.text?.toString()?.equals(t, true) == true }

    private fun findTextContains(root: AccessibilityNodeInfo, t: String) =
        root.findAccessibilityNodeInfosByText(t)
            .firstOrNull { it.text?.toString()?.contains(t, true) == true }

    private fun findEdit(root: AccessibilityNodeInfo, t: String) =
        traverse(root) {
            it.isEditable && (
                    it.text?.toString()?.contains(t, true) == true ||
                    it.hintText?.toString()?.contains(t, true) == true ||
                    it.contentDescription?.toString()?.contains(t, true) == true
            )
        }

    private fun traverse(n: AccessibilityNodeInfo?, f: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (f(n)) return AccessibilityNodeInfo.obtain(n)

        for (i in 0 until n.childCount) {
            val c = n.getChild(i)
            val r = traverse(c, f)
            c?.recycle()
            if (r != null) return r
        }
        return null
    }

    // ═══════════════════════════════════════════════════════
    // ⚡ ACTION EXECUTION
    // ═══════════════════════════════════════════════════════

    private fun clickText(root: AccessibilityNodeInfo, t: String): Boolean {
        val node = findText(root, t)
        return node?.let { 
            val result = clickNode(it)
            it.recycle()
            result
        } ?: false
    }

    private fun clickDesc(root: AccessibilityNodeInfo, d: String): Boolean {
        val node = traverse(root) { 
            it.contentDescription?.toString()?.contains(d, true) == true 
        }
        return node?.let { 
            val result = clickNode(it)
            it.recycle()
            result
        } ?: false
    }

    // ✅ 100% FIXED: Parent Traversal Memory Leak
    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var t: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(n)
        var success = false
        
        while (t != null) {
            if (t.isClickable) {
                success = t.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    lastAction = System.currentTimeMillis()
                    t.recycle()
                    return true
                }
            }
            val parent = t.parent
            t.recycle()
            t = parent
        }
        return false
    }

    private fun input(n: AccessibilityNodeInfo, txt: String) {
        try {
            val b = Bundle()
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, txt)
            n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
            lastAction = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Input failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🚀 CONTROL METHODS
    // ═══════════════════════════════════════════════════════

    private fun start(i: Intent) {
        reset()
        totalPassengers = i.getIntExtra("passengers", 1).coerceIn(1, 6)
        active = true
        step = Step.DASHBOARD
        toast("STARTED")
    }

    private fun reset() {
        active = false
        step = Step.IDLE
        retry = 0
        passengersDone = 0
        captchaPause = false
        filling = false
        lastAction = 0
    }

    private fun error(msg: String) {
        active = false
        step = Step.ERROR
        toast(msg)
    }

    private fun toast(m: String) {
        scope.launch {
            try {
                Toast.makeText(applicationContext, m, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w(TAG, "Toast failed: ${e.message}")
            }
        }
    }
}
