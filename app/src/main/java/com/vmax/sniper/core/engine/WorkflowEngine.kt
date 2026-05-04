package com.vmax.sniper.core.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.vmax.sniper.core.model.*
import com.vmax.sniper.core.network.TimeSniper
import kotlinx.coroutines.*
import kotlin.math.min
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * VMAX SNIPER - COMPETITION-GRADE Tatkal Engine
 * प्रीडिक्टिव | जीरो-सर्च | पैरेलल प्रायोरिटी | 0 बग
 */
class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Sniper"
        const val ACTION_START_SNIPER = "com.vmax.sniper.START_SNIPER"
        const val EXTRA_TASK = "extra_task"
        const val ACTION_SERVICE_STOPPED = "com.vmax.sniper.SERVICE_STOPPED"
        
        object IRCTC {
            const val PKG = "cris.org.in.prs.ima"
            const val NAME_INPUT = "$PKG:id/et_passenger_name"
            const val AGE_INPUT = "$PKG:id/et_passenger_age"
            const val GENDER_SPINNER = "$PKG:id/et_gender"
            const val BERTH_SPINNER = "$PKG:id/et_berth_preference"
            const val MEAL_SPINNER = "$PKG:id/et_meal"
            const val CHILD_NAME = "$PKG:id/et_child_name"
            const val CHILD_AGE = "$PKG:id/spinner_child_age"
            const val CHILD_GENDER = "$PKG:id/spinner_child_gender"
            const val ADD_PASSENGER_BTN = "$PKG:id/tv_add_passanger"
            const val ADD_CHILD_BTN = "$PKG:id/btn_add_child"
            const val SAVE_BTN = "$PKG:id/btn_save"
            const val PROCEED_BTN = "$PKG:id/btn_proceed"
            const val BOOK_NOW_BTN = "$PKG:id/btn_book_now"
            const val SEARCH_BTN = "$PKG:id/btn_search_trains"
            const val DATE_INPUT = "$PKG:id/jDate"
            const val CAL_MONTH_TEXT = "$PKG:id/month_year"
            const val CAL_NEXT_MONTH = "$PKG:id/btn_next_month"
            const val CAL_OK_BTN = "$PKG:id/btn_ok"
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_EWALLET = "$PKG:id/radio_ewallet"
            const val PAYMENT_UPI_ID = "$PKG:id/radio_upi_id"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            const val UPI_ID_INPUT = "$PKG:id/et_upi_id"
            const val AUTO_UPGRADE_CHECK = "$PKG:id/checkbox_auto_upgrade"
            const val CONFIRM_BERTH_CHECK = "$PKG:id/checkbox_confirm_berth"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
            const val BOOKING_OPT_SPINNER = "$PKG:id/spinner_booking_option"
            const val COACH_PREF_INPUT = "$PKG:id/et_coach_preference"
            const val MOBILE_INPUT = "$PKG:id/et_mobile_number"
        }
    }

    // ==================== STATE MACHINE ====================
    enum class EngineState {
        IDLE, SEARCH, CLASS_SELECTED, FORM_FILL, REVIEW, PAYMENT, DONE
    }
    private var currentState = EngineState.IDLE
    private var lastStateTransition = 0L

    // ==================== MEMORY SAFE VARIABLES ====================
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val isProcessing = AtomicBoolean(false)
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var hasRefreshed = false
    private var currentPassengerIndex = 0
    private var watchdogJob: Job? = null
    private var recoveryJob: Job? = null
    private var isReviewClicked = false
    private var lastEventTime = 0L
    private var lastWindowHash = 0
    private val clipboard: ClipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private var captchaRetryCount = 0
    private var jitterBase = 12L
    
    // ✅ Cache only bounds (NO node storage to avoid memory leak)
    private val boundsCache = mutableMapOf<String, Rect>()

    // ==================== CONTROLLED HUMAN-LIKE DELAYS ====================
    private suspend fun humanDelay() {
        val jitter = jitterBase + Random.nextLong(-3, 4)
        delay(jitter.coerceIn(8, 20))
    }
    private suspend fun frameDelay() = delay(16)
    private suspend fun fastDelay() = delay(10)
    private suspend fun mediumDelay() = delay(Random.nextLong(20, 32))
    private suspend fun addDelay() = delay(Random.nextLong(12, 20))

    // ==================== SMART MULTI-ROOT SNAPSHOT ====================
    private fun getStableRoot(): AccessibilityNodeInfo? {
        repeat(3) {
            val root = rootInActiveWindow
            if (root != null && root.childCount > 0) {
                return root
            }
            Thread.sleep(5)
        }
        return null
    }

    // ==================== STATE GUARD ====================
    private fun isState(expected: EngineState): Boolean = currentState == expected

    private fun transitionTo(newState: EngineState) {
        val now = System.currentTimeMillis()
        if (now - lastStateTransition < 100) return
        Log.d(TAG, "📍 State: $currentState → $newState")
        currentState = newState
        lastStateTransition = now
    }

    // ==================== SERVICE LIFECYCLE ====================
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 10
        }
        createNotificationChannel()
        startForeground(1, buildNotification("⚡ COMPETITION-GRADE ACTIVE"))
        Log.d(TAG, "✅ COMPETITION-GRADE SNIPER ACTIVE")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SNIPER) {
            activeTask = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_TASK, SniperTask::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_TASK)
            }
            if (activeTask != null) {
                resetEngineState()
                val targetHour = if (activeTask!!.triggerTime.startsWith("10")) 10 else 11
                val advanceMs = activeTask!!.msAdvance.toLong().coerceIn(100, 250)
                TimeSniper.scheduleFire(targetHour, advanceMs) {
                    isArmed = true
                    transitionTo(EngineState.SEARCH)
                    serviceScope.launch {
                        if (!hasRefreshed) {
                            triggerPreciseRefresh()
                            hasRefreshed = true
                        }
                    }
                }
                updateNotification("⏳ Waiting for ${activeTask!!.triggerTime}")
            }
        }
        return START_STICKY
    }

    private fun resetEngineState() {
        isArmed = false
        hasRefreshed = false
        isReviewClicked = false
        currentPassengerIndex = 0
        captchaRetryCount = 0
        transitionTo(EngineState.IDLE)
        watchdogJob?.cancel()
        recoveryJob?.cancel()
        lastEventTime = 0
        lastWindowHash = 0
        boundsCache.clear()
        Log.d(TAG, "🔄 Engine State Reset")
    }

    private fun triggerPreciseRefresh() {
        val root = getStableRoot() ?: return
        try {
            val refreshBtn = findCachedNode("Refresh", root) ?: findNodeFast(root, listOf("Search", "Refresh"), IRCTC.SEARCH_BTN)
            if (refreshBtn?.isClickable == true) {
                clickFromCacheOrNode("Refresh", refreshBtn)
            }
        } finally { root.recycle() }
    }

    // ==================== 🔍 ZERO-SEARCH + CACHE ====================
    fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        for (label in labels) {
            root.findAccessibilityNodeInfosByText(label).firstOrNull { 
                it.text?.toString()?.trim().equals(label, ignoreCase = true) && it.isVisibleToUser 
            }?.let { return it }
        }
        return findNodeBFS(root, labels.map { it.uppercase() })
    }
    
    // ✅ Predictive Cache (bounds only)
    private fun findCachedNode(key: String, root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (boundsCache.containsKey(key)) {
            return findNodeFast(root, listOf(key), "")
        }
        val newNode = findNodeFast(root, listOf(key), "")
        if (newNode != null) {
            val bounds = Rect()
            newNode.getBoundsInScreen(bounds)
            boundsCache[key] = bounds
        }
        return newNode
    }
    
    // ✅ DIRECT CLICK FROM CACHE - NO BFS
    private fun clickFromCacheOrNode(key: String, node: AccessibilityNodeInfo) {
        val cachedBounds = boundsCache[key]
        if (cachedBounds != null) {
            val path = Path().apply { moveTo(cachedBounds.centerX().toFloat(), cachedBounds.centerY().toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 10L))
                .build()
            dispatchGesture(gesture, null, null)
        } else {
            stableClick(node)
        }
    }
    
    // ✅ PRE-FETCH STRATEGY
    private fun preCache(key: String, root: AccessibilityNodeInfo) {
        if (!boundsCache.containsKey(key)) {
            findNodeFast(root, listOf(key), "")?.let {
                val bounds = Rect()
                it.getBoundsInScreen(bounds)
                boundsCache[key] = bounds
            }
        }
    }

    private fun findNodeBFS(root: AccessibilityNodeInfo, targetTexts: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.isVisibleToUser) continue
            
            val nodeText = node.text?.toString()?.trim()?.uppercase() ?: ""
            val nodeDesc = node.contentDescription?.toString()?.trim()?.uppercase() ?: ""
            
            if (targetTexts.any { nodeText.contains(it) || nodeDesc.contains(it) }) {
                return node
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    // ✅ ZERO-LAG FAST PATH
    private fun instantBookNowClick(root: AccessibilityNodeInfo): Boolean {
        val bookNow = root.findAccessibilityNodeInfosByViewId(IRCTC.BOOK_NOW_BTN).firstOrNull()
        if (bookNow != null) {
            ultraBurstClick(bookNow)
            return true
        }
        return false
    }

    // ==================== EVENT DEDUPLICATION ====================
    private fun isDuplicate(root: AccessibilityNodeInfo): Boolean {
        val hash = root.hashCode()
        if (hash == lastWindowHash) return true
        lastWindowHash = hash
        return false
    }

    // ==================== 🔥 TRUE PARALLEL + PRIORITY ====================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (event == null || !isArmed) return
        if (now - lastEventTime < 40) return
        lastEventTime = now
        
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && 
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            
        val packageName = event.packageName?.toString() ?: return
        if (packageName != IRCTC.PKG) return
        
        if (!isProcessing.compareAndSet(false, true)) return
        
        serviceScope.launch {
            var root: AccessibilityNodeInfo? = null
            try {
                root = getStableRoot() ?: return@launch
                if (isDuplicate(root)) return@launch
                
                // 🔥 PRE-FETCH for next state
                when (currentState) {
                    EngineState.SEARCH -> preCache("Book Now", root)
                    EngineState.CLASS_SELECTED -> preCache("Add Passenger", root)
                    else -> {}
                }
                
                // ✅ TRUE PARALLEL WITH PRIORITY
                processEventParallel(root)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Event Error: ${e.message}")
            } finally {
                isProcessing.set(false)
                try { root?.recycle() } catch (e: Exception) {}
            }
        }
    }
    
    // ✅ PARALLEL + PRIORITY (Final button always fastest)
    private suspend fun processEventParallel(root: AccessibilityNodeInfo) = coroutineScope {
        val finalJob = async { handleFinalProceed(root) }
        val popupJob = async { handlePopups(root) }
        val captchaJob = async { handleCaptcha(root) }
        val paymentJob = async { handlePaymentPage(root) }
        val classJob = async { handleClassSelection(root) }
        val formJob = async { handlePassengerForm(root) }
        
        // 🔥 FINAL BUTTON = HIGHEST PRIORITY (check first)
        if (finalJob.await()) return@coroutineScope
        if (popupJob.await()) return@coroutineScope
        if (captchaJob.await()) return@coroutineScope
        if (paymentJob.await()) return@coroutineScope
        if (classJob.await()) return@coroutineScope
        if (formJob.await()) return@coroutineScope
        
        // ZERO-LAG FAST PATH
        if (instantBookNowClick(root)) return@coroutineScope
    }
    
    private suspend fun handleSessionExpired(root: AccessibilityNodeInfo): Boolean {
        val expired = findNodeFast(root, listOf("Session Expired", "Try Again", "Login"), "")
        if (expired != null) {
            Log.w(TAG, "⚠️ Session expired - recovering")
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(100)
            triggerPreciseRefresh()
            return true
        }
        return false
    }

    private suspend fun handleCaptcha(root: AccessibilityNodeInfo): Boolean {
        if (!isReviewClicked) return false
        
        val captchaImage = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE).firstOrNull()
        val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT).firstOrNull()
        
        if (captchaImage != null && captchaInput != null && activeTask?.captchaAutofill == true) {
            if (captchaInput.text.isNullOrBlank() && captchaRetryCount < 3) {
                Log.d(TAG, "🔐 Solving Captcha... (Attempt: ${captchaRetryCount + 1})")
                captchaRetryCount++
                CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage, captchaInput)
                return true
            } else {
                captchaRetryCount = 0
            }
        }
        return false
    }

    private suspend fun handleClassSelection(root: AccessibilityNodeInfo): Boolean {
        if (!hasRefreshed || !isState(EngineState.SEARCH)) return false
        
        val classNode = findNodeFast(root, listOf(activeTask?.travelClass?.code ?: ""), "")
        if (classNode != null && classNode.isClickable) {
            transitionTo(EngineState.CLASS_SELECTED)
            safeClick(classNode)
            frameDelay()
            findNodeFast(root, listOf("Book Now", "अभी बुक करें"), IRCTC.BOOK_NOW_BTN)?.let { 
                ultraBurstClick(it)
            }
            findPassengerFormAndFill()
            return true
        }
        return false
    }

    private suspend fun handlePassengerForm(root: AccessibilityNodeInfo): Boolean {
        if (!isState(EngineState.CLASS_SELECTED)) return false
        if (findNodeFast(root, listOf("Name"), IRCTC.NAME_INPUT) != null) {
            transitionTo(EngineState.FORM_FILL)
            fillAllDetailsSuperFast(root)
            return true
        }
        return false
    }

    private suspend fun handlePaymentPage(root: AccessibilityNodeInfo): Boolean {
        if (root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS).isNotEmpty()) {
            transitionTo(EngineState.PAYMENT)
            isReviewClicked = false
            selectPaymentFast(root)
            return true
        }
        return false
    }

    private suspend fun handleFinalProceed(root: AccessibilityNodeInfo): Boolean {
        val payBtn = findNodeFast(root, listOf("PAY", "PROCEED", "CONTINUE", "भुगतान"), IRCTC.PROCEED_BTN)
        if (payBtn != null) {
            ultraBurstClick(payBtn)
            updateNotification("✅ Booking Submitted!")
            transitionTo(EngineState.DONE)
            isArmed = false
            LocalBroadcastManager.getInstance(this@WorkflowEngine).sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
            return true
        }
        return false
    }

    private suspend fun handlePopups(root: AccessibilityNodeInfo): Boolean {
        val okButton = findNodeFast(root, listOf("OK", "ठीक है", "YES", "हाँ", "CONFIRM", "I AGREE"), "")
        if (okButton != null && okButton.isClickable) {
            stableClick(okButton)
            humanDelay()
            Log.d(TAG, "🔘 Popup Bypassed")
            return true
        }
        return false
    }

    // ✅ OPTIMIZED SAFE CLICK (no heavy getStableRoot inside loop)
    private suspend fun safeClick(node: AccessibilityNodeInfo, verifyText: String? = null): Boolean {
        repeat(3) {
            stableClick(node)
            delay(20)
            if (verifyText == null) return true
        }
        return true
    }

    private suspend fun findPassengerFormAndFill() {
        repeat(4) {
            val root = getStableRoot() ?: return
            try {
                if (findNodeFast(root, listOf("Name"), IRCTC.NAME_INPUT) != null) {
                    fillAllDetailsSuperFast(root)
                    return
                }
            } finally { root.recycle() }
            humanDelay()
        }
    }

    private suspend fun fillAllDetailsSuperFast(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        
        try {
            val maxPassengers = min(task.passengers.size, 4)
            
            for (i in currentPassengerIndex until maxPassengers) {
                val passenger = task.passengers[i]
                
                if (i > 0) {
                    findCachedNode("Add Passenger", root)?.let { clickFromCacheOrNode("Add Passenger", it) }
                    addDelay()
                }
                
                findCachedNode("Name", root)?.let {
                    if (it.text.isNullOrBlank()) setTextFast(it, passenger.name)
                    fastDelay()
                }
                
                findCachedNode("Age", root)?.let {
                    if (it.text.isNullOrBlank()) setTextFast(it, passenger.age)
                    fastDelay()
                }
                
                if (passenger.gender.isNotBlank()) {
                    selectPopupOption(root, IRCTC.GENDER_SPINNER, passenger.gender)
                }
                
                if (i > 0) {
                    findCachedNode("Save", root)?.let { clickFromCacheOrNode("Save", it) }
                    addDelay()
                }
                currentPassengerIndex = i + 1
            }
            
            for (child in task.children) {
                findNodeFast(root, listOf("Add Infant"), IRCTC.ADD_CHILD_BTN)?.let {
                    safeClick(it)
                    addDelay()
                }
                findNodeFast(root, listOf("Infant Name"), IRCTC.CHILD_NAME)?.let {
                    if (it.text.isNullOrBlank()) setTextFast(it, child.name)
                    fastDelay()
                }
            }
            
            if (task.journeyDate.isNotBlank()) {
                selectDateWithCalendar(root, task.journeyDate)
            }
            
            mediumDelay()
            findNodeFast(root, listOf("Review Journey", "Continue"), IRCTC.PROCEED_BTN)?.let {
                safeClick(it)
                transitionTo(EngineState.REVIEW)
                isReviewClicked = true
                startWatchdog()
                updateNotification("📋 Review Page - Ready")
            }
        } finally {
            root.recycle()
        }
    }

    private suspend fun selectDateWithCalendar(root: AccessibilityNodeInfo, targetDate: String) {
        val parts = targetDate.split("-")
        val day = parts[0].toInt().toString()
        val monthNum = parts[1].toInt()
        
        val monthNames = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", 
                                 "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val targetMonth = monthNames[monthNum - 1]
        
        val dateField = findNodeFast(root, emptyList(), IRCTC.DATE_INPUT) ?: return
        stableClick(dateField)
        mediumDelay()
        
        repeat(3) {
            val calRoot = getStableRoot() ?: return@repeat
            try {
                val currentMonthYear = findNodeFast(calRoot, emptyList(), IRCTC.CAL_MONTH_TEXT)?.text?.toString()?.uppercase() ?: ""
                
                if (currentMonthYear.contains(targetMonth)) {
                    val dayNode = findNodeFast(calRoot, listOf(day), "")
                    dayNode?.let {
                        stableClick(it)
                        frameDelay()
                        findNodeFast(calRoot, emptyList(), IRCTC.CAL_OK_BTN)?.let { ok ->
                            stableClick(ok)
                        }
                    }
                    return
                } else {
                    findNodeFast(calRoot, emptyList(), IRCTC.CAL_NEXT_MONTH)?.let { next ->
                        stableClick(next)
                        frameDelay()
                    }
                }
            } finally { calRoot.recycle() }
        }
    }

    private suspend fun selectPopupOption(root: AccessibilityNodeInfo, spinnerId: String, optionText: String): Boolean {
        val spinner = findNodeFast(root, emptyList(), spinnerId) ?: return false
        stableClick(spinner)
        mediumDelay()
        
        repeat(3) {
            val newRoot = getStableRoot() ?: return false
            try {
                findNodeFast(newRoot, listOf(optionText), "")?.let {
                    stableClick(it)
                    return true
                }
            } finally { newRoot.recycle() }
            humanDelay()
        }
        return false
    }

    private suspend fun selectPaymentFast(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        watchdogJob?.cancel()
        
        try {
            when (task.payment.category) {
                PaymentCategory.CARDS_NETBANKING -> findCachedNode("Cards", root)?.let { clickFromCacheOrNode("Cards", it) }
                PaymentCategory.BHIM_UPI -> findCachedNode("UPI", root)?.let { clickFromCacheOrNode("UPI", it) }
                else -> findCachedNode("Payment", root)?.let { clickFromCacheOrNode("Payment", it) }
            }
        } finally { root.recycle() }
        
        mediumDelay()
        val newRoot = getStableRoot() ?: return
        try {
            findNodeFast(newRoot, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)?.let { 
                stableClick(it)
                updateNotification("💳 Payment Selected")
            }
        } finally { newRoot.recycle() }
    }

    // ✅ ULTRA BURST CLICK FOR CRITICAL BUTTONS
    private suspend fun ultraBurstClick(node: AccessibilityNodeInfo) {
        repeat(5) {
            stableClick(node)
            delay(Random.nextLong(5, 12))
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            delay(3000)
            if (isReviewClicked && isState(EngineState.REVIEW)) {
                val root = getStableRoot()
                if (root != null) {
                    try {
                        val proceedBtn = findNodeFast(root, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)
                        if (proceedBtn?.isClickable == true) {
                            safeClick(proceedBtn)
                            Log.d(TAG, "🛡️ Watchdog retry clicked")
                        }
                    } finally { root.recycle() }
                }
            }
        }
    }

    private fun startUltraRecovery() {
        recoveryJob?.cancel()
        recoveryJob = serviceScope.launch {
            delay(2500)
            val root = getStableRoot()
            if (root != null) {
                try {
                    val stuck = findNodeFast(root, listOf("Loading", "Please wait"), "")
                    if (stuck != null) {
                        Log.d(TAG, "⚠️ Stuck detected - refreshing")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        delay(300)
                        triggerPreciseRefresh()
                    }
                } finally { root.recycle() }
            }
        }
    }

    // ==================== 🖱️ STABLE CLICK ====================
    fun stableClick(node: AccessibilityNodeInfo) {
        var target = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }

        target?.let { clickableNode ->
            val bounds = Rect()
            clickableNode.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 12L))
                    .build()
                dispatchGesture(gesture, null, null)
            }
        }
    }

    fun setTextFast(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { 
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) 
        }
        val setTextSuccess = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        
        if (!setTextSuccess) {
            clipboard.setPrimaryClip(ClipData.newPlainText("vmax_sniper", text))
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
            val longPressGesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
                .build()
            dispatchGesture(longPressGesture, null, null)
            
            serviceScope.launch {
                delay(50)
                getStableRoot()?.let { root ->
                    try {
                        findNodeFast(root, listOf("Paste", "पेस्ट", "PASTE"), "")?.let { pasteBtn ->
                            stableClick(pasteBtn)
                        }
                    } finally { root.recycle() }
                }
            }
        }
    }

    // ==================== 🔔 NOTIFICATIONS ====================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 COMPETITION-GRADE")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun updateNotification(message: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(1, buildNotification(message))
            Log.d(TAG, message)
        } catch (e: Exception) {
            Log.e(TAG, "Notification error: ${e.message}")
        }
    }

    override fun onInterrupt() { 
        resetEngineState() 
        Log.d(TAG, "⏸️ Service Interrupted")
    }
    
    override fun onDestroy() { 
        watchdogJob?.cancel()
        recoveryJob?.cancel()
        serviceScope.cancel() 
        super.onDestroy()
        Log.d(TAG, "💀 Service Destroyed")
    }
}

// ==================== 📱 HELPER FUNCTION ====================
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains(expected.flattenToString())
}
