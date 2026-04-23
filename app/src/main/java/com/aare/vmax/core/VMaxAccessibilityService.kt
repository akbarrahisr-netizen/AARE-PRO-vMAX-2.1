package com.aare.vmax.core.orchestrator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.*

class VMAXAccessibilityService : AccessibilityService() {

    // ═══════════════════════════════════════════════════════
    // ⚙️ STATE & CONFIG
    // ═══════════════════════════════════════════════════════
    private lateinit var prefs: SharedPreferences
    private var automationState = AutomationState.IDLE
    private var isRunning = false
    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var retryCount = 0
    private val MAX_RETRIES = 3
    private var lastClickTime = 0L
    private val CLICK_DEBOUNCE_MS = 500L

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_START_AUTOMATION -> startAutomation()
                ACTION_STOP_AUTOMATION -> stopAutomation()
            }
        }
    }

    enum class AutomationState {
        IDLE, DASHBOARD, JOURNEY_DETAILS, TRAIN_SEARCH, 
        PASSENGER_DETAILS, COMPLETED, PAUSED_ERROR
    }

    companion object {
        const val ACTION_START_AUTOMATION = "com.aare.vmax.ACTION_START_AUTOMATION"
        const val ACTION_STOP_AUTOMATION = "com.aare.vmax.ACTION_STOP_AUTOMATION"
        const val SERVICE_CLASS_NAME = "com.aare.vmax.VMAXAccessibilityService"    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("VMaxProfile", Context.MODE_PRIVATE)
        configureService()
        registerCommandReceiver()
        logD("🛡️ VMAX Service Connected & Ready")
    }

    private fun configureService() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 50
            packageNames = arrayOf(
                "cris.org.in.prs.ima",
                "com.irctc.railconnect",
                "in.irctc.railconnect"
            )
        }
        serviceInfo = info
    }

    private fun registerCommandReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_START_AUTOMATION)
            addAction(ACTION_STOP_AUTOMATION)
        }
        ContextCompat.registerReceiver(this, commandReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        when (automationState) {
            AutomationState.DASHBOARD -> checkAndClickDashboard()
            AutomationState.JOURNEY_DETAILS -> handleJourneyDetails()
            AutomationState.TRAIN_SEARCH -> handleTrainSelection()
            AutomationState.PASSENGER_DETAILS -> fillPassengerDetails()
            AutomationState.PAUSED_ERROR -> handleCaptchaOrError()
            else -> {}
        }
    }
    override fun onInterrupt() {
        stopAutomation()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
        serviceJob?.cancel()
        logD("🔌 Service Destroyed")
    }

    // ═══════════════════════════════════════════════════════
    // 🚀 START/STOP COMMANDS
    // ═══════════════════════════════════════════════════════
    
    private fun startAutomation() {
        if (isRunning) {
            logD("⚠️ Already running")
            return
        }
        isRunning = true
        automationState = AutomationState.DASHBOARD
        retryCount = 0
        logD("🚀 Automation Started - State: DASHBOARD")
    }

    private fun stopAutomation() {
        isRunning = false
        automationState = AutomationState.IDLE
        serviceJob?.cancel()
        retryCount = 0
        logD("🛑 Automation Stopped")
    }

    // ═══════════════════════════════════════════════════════
    // 🧭 STATE HANDLERS (WITH DEBOUNCE & STATE PRE-SWITCH)
    // ═══════════════════════════════════════════════════════
    
    private fun checkAndClickDashboard() {
        val bookingNode = findNodeByText("Book Ticket") 
                       ?: findNodeByContentDesc("Book Ticket") 
                       ?: findNodeByText("Train Booking")
                       ?: findNodeByText("IRCTC")
                       ?: findNodeByViewId("cris.org.in.prs.ima:id/btn_book_ticket")
        
        if (bookingNode != null) {
            automationState = AutomationState.JOURNEY_DETAILS
            performClick(bookingNode, "✅ Dashboard: Booking Clicked")
            retryCount = 0        } else {
            handleRetry("Dashboard")
        }
    }

    private fun handleJourneyDetails() {
        val searchBtn = findNodeByText("Search Trains") 
                     ?: findNodeByText("Find Trains")
                     ?: findNodeByViewId("cris.org.in.prs.ima:id/btn_search")
        
        if (searchBtn != null) {
            automationState = AutomationState.TRAIN_SEARCH
            scope.launch {
                sniperDelayUntilTarget()
                performClick(searchBtn, "🎯 Journey Search Triggered")
            }
        } else {
            handleRetry("Journey Details")
        }
    }

    private fun handleTrainSelection() {
        val bookNowBtn = findNodeByText("Book Now") 
                      ?: findNodeByText("Continue")
                      ?: findNodeByText("Select")
                      ?: findNodeByViewId("cris.org.in.prs.ima:id/btn_book_now")
        
        if (bookNowBtn != null) {
            automationState = AutomationState.PASSENGER_DETAILS
            performClick(bookNowBtn, "🚂 Train Selected")
            retryCount = 0
        } else {
            handleRetry("Train Selection")
        }
    }

    private fun handleRetry(stateName: String) {
        retryCount++
        if (retryCount < MAX_RETRIES) {
            logD("⏳ $stateName retry $retryCount/$MAX_RETRIES")
            scope.launch { delay(1000) }
        } else {
            logD("❌ $stateName failed after $MAX_RETRIES retries")
            automationState = AutomationState.PAUSED_ERROR
            retryCount = 0
        }
    }

    private fun fillPassengerDetails() {
        val passengerCount = prefs.getInt("passenger_count", 0)        
        if (passengerCount > 6) {
            logD("⚠️ Passenger count $passengerCount exceeds IRCTC limit (6)")
            automationState = AutomationState.PAUSED_ERROR
            return
        }
        
        if (passengerCount == 0) {
            logD("⚠️ No passengers to fill")
            automationState = AutomationState.PAUSED_ERROR
            return
        }
        
        val targetClass = prefs.getString("class", "SL") ?: "SL"

        scope.launch {
            try {
                val classNode = findNodeByText(targetClass) 
                             ?: findNodeByViewId("cris.org.in.prs.ima:id/spinner_class")
                classNode?.let { 
                    performClick(it, "🎫 Class: $targetClass Selected") 
                    delay(500)
                }

                val addPassengerBtn = findNodeByText("Add Passenger") 
                                   ?: findNodeByText("Add New")
                addPassengerBtn?.let {
                    performClick(it, "➕ Add Passenger Clicked")
                    delay(400)
                }

                for (i in 0 until passengerCount.coerceAtMost(6)) {
                    val name = prefs.getString("name_$i", "") ?: continue
                    if (name.isEmpty()) continue
                    
                    val age = prefs.getString("age_$i", "") ?: "0"
                    val gender = prefs.getString("gender_$i", "") ?: ""
                    
                    val nameField = findNodeByViewId("cris.org.in.prs.ima:id/et_passenger_name")
                    val ageField = findNodeByViewId("cris.org.in.prs.ima:id/et_passenger_age")
                    
                    nameField?.let { performInputText(it, name, "📝 Name: $name") }
                    delay(200)
                    ageField?.let { performInputText(it, age, "🔢 Age: $age") }
                    delay(200)

                    val genderNode = findNodeByText(gender)
                    genderNode?.let { performClick(it, "👤 Gender: $gender") }
                    delay(300)
                    if (i < passengerCount - 1) {
                        val addAnother = findNodeByText("Add Another") 
                                      ?: findNodeByViewId("cris.org.in.prs.ima:id/btn_add_another")
                        addAnother?.let {
                            performClick(it, "➕ Add Another Passenger")
                            delay(500)
                        }
                    }
                }

                val submitBtn = findNodeByText("Continue") 
                             ?: findNodeByText("Book Now")
                             ?: findNodeByText("Proceed")
                             ?: findNodeByViewId("cris.org.in.prs.ima:id/btn_continue")
                submitBtn?.let { 
                    performClick(it, "🚀 Submitting Booking...") 
                    automationState = AutomationState.COMPLETED
                    logD("✅ Booking Flow Completed")
                }
            } catch (e: Exception) {
                logD("❌ Error in fillPassengerDetails: ${e.message}")
                automationState = AutomationState.PAUSED_ERROR
            }
        }
    }

    private fun handleCaptchaOrError() {
        val captchaPatterns = listOf(
            "captcha", "verification", "are you human", 
            "robot", "security check", "i'm not a robot"
        )
        
        var captchaDetected = false
        for (pattern in captchaPatterns) {
            val node = findNodeByText(pattern, contains = true)
            if (node != null) {
                captchaDetected = true
                node.recycle()
                break
            }
        }
        
        if (captchaDetected) {
            logD("🔐 CAPTCHA Detected - Pausing for manual input")
        }
    }

    // ═══════════════════════════════════════════════════════
    // ⏱️ SNIPER TIMING ENGINE
    // ═══════════════════════════════════════════════════════    
    private suspend fun sniperDelayUntilTarget() {
        val targetClass = prefs.getString("class", "SL") ?: "SL"
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        
        val targetHour = when {
            currentHour == 7 -> 8
            targetClass in listOf("1A", "2A", "3A", "3E", "CC", "EC") -> 10
            else -> 11
        }

        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val offset = prefs.getInt("latency_ms", 400).toLong()
        val fireTime = targetTime.timeInMillis - offset
        val waitMs = fireTime - System.currentTimeMillis()
        
        when {
            waitMs in 1..60000 -> {
                logD("⏳ Sniper Lock: Firing in ${waitMs}ms")
                delay(waitMs)
            }
            waitMs <= 0 && waitMs > -5000 -> {
                logD("🎯 Target window active - Executing instantly")
            }
            else -> {
                logD("⚠️ Timing off, proceeding anyway")
            }
        }
        logD("🔥 FIRE! Executing click...")
    }

    // ═══════════════════════════════════════════════════════
    // 🛡️ NODE FINDING UTILS (MEMORY SAFE)
    // ═══════════════════════════════════════════════════════
    
    private fun findNodeByText(text: String, contains: Boolean = false): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return traverseAndFind(root) { node ->
            val nodeText = node.text?.toString()
            if (contains) {
                nodeText?.contains(text, ignoreCase = true) == true
            } else {
                nodeText?.equals(text, ignoreCase = true) == true            }
        }
    }

    private fun findNodeByContentDesc(desc: String, contains: Boolean = false): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return traverseAndFind(root) { node ->
            val nodeDesc = node.contentDescription?.toString()
            if (contains) {
                nodeDesc?.contains(desc, ignoreCase = true) == true
            } else {
                nodeDesc?.equals(desc, ignoreCase = true) == true
            }
        }
    }

    private fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return traverseAndFind(root) { node ->
            node.viewIdResourceName?.equals(viewId, ignoreCase = true) == true
        }
    }

    private fun traverseAndFind(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        
        if (predicate(node)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = traverseAndFind(child, predicate)
            child?.recycle()
            if (found != null) return found
        }
        return null
    }

    // ═══════════════════════════════════════════════════════
    // ⚡ SAFE UI ACTIONS
    // ═══════════════════════════════════════════════════════
    
    private fun performClick(node: AccessibilityNodeInfo, logMsg: String) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastClickTime < CLICK_DEBOUNCE_MS) {                logD("⏸️ Debounced: $logMsg")
                return
            }
            lastClickTime = now
            
            if (node.isClickable) {
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    logD(logMsg)
                } else {
                    logD("❌ Click failed: $logMsg")
                }
            } else {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                Thread.sleep(100)
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    logD(logMsg)
                }
            }
        } catch (e: Exception) {
            logD("⚠️ Click exception: ${e.message}")
        } finally {
            node.recycle()
        }
    }

    private fun performInputText(node: AccessibilityNodeInfo, text: String, logMsg: String) {
        try {
            if (node.isEditable) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (success) {
                    logD(logMsg)
                } else {
                    logD("❌ Input failed: $logMsg")
                }
            }
        } catch (e: Exception) {
            logD("⚠️ Input exception: ${e.message}")
        } finally {
            node.recycle()
        }
    }

    private fun logD(msg: String) {
        Log.d("VMAX_SERVICE", msg)
    }}
