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
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.vmax.sniper.core.model.*
import com.vmax.sniper.core.network.TimeSniper
import com.vmax.sniper.core.network.TimeSyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * VMAX ELITE - EVENT-DRIVEN STATE MACHINE v7.3.3
 * ✅ Compilation Fixed | Smart Resume | Force Attack
 */
class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_ENGINE"
        private const val QUEUE_CAPACITY = 200
        private const val MIN_EVENT_GAP_MS = 30L
        private const val MIN_CLICK_GAP_MS = 20L
        private const val SCREEN_CACHE_MS = 150L
        private const val WORKER_DELAY_MS = 10L
        private const val REVIEW_STABILITY_DURATION_MS = 120000L
        private const val CAPTCHA_TIMEOUT_MS = 8000L
        private const val REVIEW_STABLE_COUNT_REQUIRED = 3
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 100L
        
        const val ACTION_START_SNIPER = "com.vmax.sniper.START_SNIPER"
        const val EXTRA_TASK = "extra_task"
        const val EXTRA_FORCE_ATTACK = "FORCE_ATTACK"
        const val ACTION_SERVICE_STOPPED = "com.vmax.sniper.SERVICE_STOPPED"

        object Timing {
            const val FAST_MS = 8L
            const val NORMAL_MS = 20L
            const val SLOW_MS = 50L
            const val UI_LOAD_MS = 150L
        }

        object IRCTC {
            const val PKG = "cris.org.in.prs.ima"
            const val SEARCH_BTN = "$PKG:id/btn_search_trains"
            const val PROCEED_BTN = "$PKG:id/btn_proceed"
            const val BOOK_NOW_BTN = "$PKG:id/btn_book_now"
            const val NAME_INPUT = "$PKG:id/et_passenger_name"
            const val AGE_INPUT = "$PKG:id/et_passenger_age"
            const val GENDER_SPINNER = "$PKG:id/et_gender"
            const val ADD_PASSENGER_BTN = "$PKG:id/tv_add_passanger"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
            
            val SEARCH_BTN_FALLBACK = listOf("Search Trains", "Search", "Train Search")
            val BOOK_NOW_FALLBACK = listOf("Book Now", "Book", "Confirm Booking")
        }
    }

    // ==================== STATE MACHINE ====================
    private enum class WorkflowStep {
        IDLE, REFRESH_DONE, ATTACK_DONE, FORM_FILLED, REVIEW_READY, 
        CAPTCHA_SOLVED, PAYMENT_DONE, BOOKING_DONE, FAILED
    }
    
    private enum class ProcessingState {
        IDLE, RUNNING, PROCESSING
    }
    
    private data class UiEvent(val type: String, val timestamp: Long)
    
    private var currentStep = WorkflowStep.IDLE
    private val processingState = AtomicReference(ProcessingState.IDLE)
    private val stateLock = Mutex()
    private val clickLock = Mutex()
    private var retryCount = 0

    private val eventChannel = Channel<UiEvent>(Channel.CONFLATED)
    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var cachedScreenType = ""
    private var cachedScreenTime = 0L
    private val screenLock = AtomicBoolean(false)
    
    private var lastProcessedEventTime = 0L
    private var lastProcessedEventType = ""

    // ==================== VARIABLES ====================
    private val activeTask = AtomicReference<SniperTask?>(null)
    private val isArmed = AtomicBoolean(false)
    private val formComplete = AtomicBoolean(false)
    private val lastEventTime = AtomicLong(0L)
    private var lastClickTime = 0L

    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private fun getCurrentTimestamp(): String = timestampFormat.format(Date())

    private fun logDebug(message: String) = Log.d(TAG, "[${getCurrentTimestamp()}] $message")
    private fun logError(message: String) = Log.e(TAG, "[${getCurrentTimestamp()}] $message")
    private fun logError(message: String, e: Exception) = Log.e(TAG, "[${getCurrentTimestamp()}] $message", e)

    // ==================== DEVICE DETECTION ====================
    private fun isHighEndDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        return manufacturer in listOf("samsung", "oneplus", "google", "xiaomi", "motorola") ||
               model.contains("s23") || model.contains("s24") || model.contains("pixel") ||
               model.contains("oneplus") || (model.contains("13") && model.contains("pro"))
    }

    private fun getGestureDuration(): Long = when {
        Build.VERSION.SDK_INT >= 33 && isHighEndDevice() -> 50L
        Build.VERSION.SDK_INT >= 31 -> 80L
        else -> 120L
    }

    private fun getDynamicDebounce(): Long = when {
        Build.VERSION.SDK_INT >= 33 && isHighEndDevice() -> 100L
        Build.VERSION.SDK_INT >= 31 -> 120L
        else -> 150L
    }

    // ==================== SCREEN DETECTION WITH CACHE ====================
    private fun detectScreenType(root: AccessibilityNodeInfo): String {
        val now = System.currentTimeMillis()
        if (cachedScreenType.isNotBlank() && now - cachedScreenTime < SCREEN_CACHE_MS) {
            return cachedScreenType
        }
        
        val screen = when {
            safeFindById(root, IRCTC.SEARCH_BTN) != null -> "SEARCH"
            safeFindByText(root, IRCTC.SEARCH_BTN_FALLBACK) != null -> "SEARCH"
            safeFindById(root, IRCTC.BOOK_NOW_BTN) != null -> "TRAIN_LIST"
            safeFindByText(root, IRCTC.BOOK_NOW_FALLBACK) != null -> "TRAIN_LIST"
            safeFindById(root, IRCTC.NAME_INPUT) != null -> "PASSENGER_FORM"
            safeFindById(root, IRCTC.CAPTCHA_IMAGE) != null -> "REVIEW"
            safeFindById(root, IRCTC.PAYMENT_UPI_APPS) != null -> "PAYMENT"
            else -> "UNKNOWN"
        }
        
        cachedScreenType = screen
        cachedScreenTime = now
        return screen
    }
    
    private fun getCurrentScreen(): String {
        val root = rootInActiveWindow ?: return "UNKNOWN"
        return try {
            detectScreenType(root)
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun AccessibilityNodeInfo.isActuallyVisible(): Boolean {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return isVisibleToUser ||
               (Build.VERSION.SDK_INT >= 30 &&
                bounds.width() > 0 && bounds.height() > 0 && 
                isClickable)
    }

    // ==================== SAFE NODE FINDERS ====================
    private fun safeFindById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        return try {
            root.findAccessibilityNodeInfosByViewId(id)
                .firstOrNull { it.isActuallyVisible() }
        } catch (e: Exception) { null }
    }

    private fun safeFindByText(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        return try {
            for (text in texts) {
                val node = root.findAccessibilityNodeInfosByText(text)
                    .firstOrNull { it.isActuallyVisible() }
                if (node != null) return node
            }
            null
        } catch (e: Exception) { null }
    }
    
    private inline fun <T> withRoot(block: (AccessibilityNodeInfo) -> T?): T? {
        repeat(2) {
            val root = rootInActiveWindow
            if (root != null) {
                return try {
                    block(root)
                } catch (e: Exception) {
                    logError("withRoot error: ${e.message}")
                    null
                }
            }
            Thread.sleep(10)
        }
        return null
    }

    // ==================== THREAD-SAFE CLICK ====================
    private suspend fun safeClickDelay(): Boolean {
        return clickLock.withLock {
            val now = System.currentTimeMillis()
            if (now - lastClickTime < MIN_CLICK_GAP_MS) return@withLock false
            lastClickTime = now
            true
        }
    }

    private suspend fun click(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (!safeClickDelay()) return false
        
        return try {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                var current = node.parent
                var clicked = false
                while (current != null && !clicked) {
                    if (current.isClickable) {
                        clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    current = current.parent
                }
                clicked
            }
        } catch (e: Exception) {
            logError("Click failed", e)
            false
        }
    }

    internal suspend fun stableClick(node: AccessibilityNodeInfo?): Boolean = click(node)

    // ==================== WORKFLOW ACTIONS WITH RETRY ====================
    private suspend fun executeRefresh(): Boolean {
        return withRetry {
            withRoot { root ->
                val node = safeFindById(root, IRCTC.SEARCH_BTN) ?: 
                           safeFindByText(root, IRCTC.SEARCH_BTN_FALLBACK)
                if (node != null && node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    true
                } else false
            } ?: false
        }
    }

    private suspend fun executeAttack(): Boolean {
        return withRetry {
            withRoot { root ->
                val node = safeFindById(root, IRCTC.BOOK_NOW_BTN) ?:
                           safeFindByText(root, IRCTC.BOOK_NOW_FALLBACK)
                node?.let { click(it); return@withRoot true }
                false
            } ?: false
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                logError("Retry attempt ${attempt + 1} failed", e)
                delay(RETRY_DELAY_MS)
            }
        }
        throw lastError ?: Exception("Operation failed after $MAX_RETRY_COUNT retries")
    }

    private suspend fun fillPassengerForm() {
        val task = activeTask.get() ?: return
        val passengers = task.passengers.take(4)
        
        for ((index, passenger) in passengers.withIndex()) {
            if (index > 0) {
                withRoot { root -> safeFindById(root, IRCTC.ADD_PASSENGER_BTN)?.let { click(it) } }
                delay(Timing.FAST_MS)
            }
            
            withRoot { root -> safeFindById(root, IRCTC.NAME_INPUT)?.let { setTextFast(it, passenger.name) } }
            delay(Timing.FAST_MS)
            
            withRoot { root -> safeFindById(root, IRCTC.AGE_INPUT)?.let { setTextFast(it, passenger.age.toString()) } }
            delay(Timing.FAST_MS)
            
            if (passenger.gender.isNotBlank()) {
                withRoot { root -> safeFindById(root, IRCTC.GENDER_SPINNER)?.let { click(it) } }
                delay(Timing.FAST_MS)
                withRoot { root -> 
                    val genderNode = root.findAccessibilityNodeInfosByText(passenger.gender)
                        .firstOrNull { it.isActuallyVisible() }
                    genderNode?.let { click(it) }
                }
                delay(Timing.FAST_MS)
            }
        }
        
        withRoot { root ->
            val insuranceBtn = if (task.insurance) IRCTC.INSURANCE_YES else IRCTC.INSURANCE_NO
            safeFindById(root, insuranceBtn)?.let { click(it) }
        }
        delay(Timing.FAST_MS)
        
        withRoot { root ->
            safeFindById(root, IRCTC.PROCEED_BTN)?.let {
                click(it)
                formComplete.set(true)
                logDebug("📋 Form complete")
            }
        }
    }

    private suspend fun solveCaptcha(): Boolean = withContext(Dispatchers.Default) {
        withRoot { root ->
            val captchaImage = safeFindById(root, IRCTC.CAPTCHA_IMAGE)
            val captchaInput = safeFindById(root, IRCTC.CAPTCHA_INPUT)
            if (captchaImage != null && captchaInput != null && activeTask.get()?.captchaAutofill == true) {
                if (captchaInput.text.isNullOrBlank()) {
                    logDebug("🔐 Solving Captcha...")
                    try {
                        withTimeout(CAPTCHA_TIMEOUT_MS) {
                            CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage, captchaInput)
                        }
                        true
                    } catch (e: TimeoutCancellationException) {
                        logError("❌ Captcha solve timeout - manual input needed")
                        waitForManualCaptcha(captchaInput)
                        true
                    } catch (e: Exception) {
                        logError("❌ Captcha solve error", e)
                        waitForManualCaptcha(captchaInput)
                        true
                    }
                } else false
            } else false
        } ?: false
    }
    
    private suspend fun waitForManualCaptcha(inputNode: AccessibilityNodeInfo) {
        logDebug("⏳ Waiting for manual captcha input...")
        repeat(50) {
            delay(100)
            if (inputNode.text.isNotBlank()) {
                logDebug("✅ Manual captcha detected")
                return
            }
        }
        logDebug("⚠️ No manual captcha entered")
    }
    
    private fun isCaptchaFilled(): Boolean {
        return withRoot { root ->
            safeFindById(root, IRCTC.CAPTCHA_INPUT)?.text?.isNotBlank() == true
        } ?: false
    }

    private suspend fun selectPayment(): Boolean {
        return withRetry {
            withRoot { root ->
                val upiNode = safeFindById(root, IRCTC.PAYMENT_UPI_APPS)
                if (upiNode != null) {
                    click(upiNode)
                    delay(Timing.NORMAL_MS)
                    val upiApps = listOf("Google Pay", "PhonePe", "Paytm", "BHIM")
                    for (app in upiApps) {
                        val appNode = root.findAccessibilityNodeInfosByText(app)
                            .firstOrNull { it.isActuallyVisible() }
                        if (appNode != null) {
                            click(appNode)
                            return@withRoot true
                        }
                    }
                    return@withRoot true
                }
                
                safeFindById(root, IRCTC.PAYMENT_BHIM_UPI)?.let { click(it); return@withRoot true }
                safeFindById(root, IRCTC.PAYMENT_CARDS)?.let { click(it); return@withRoot true }
                false
            } ?: false
        }
    }

    private suspend fun executeFinalPay(): Boolean {
        return withRetry {
            withRoot { root ->
                safeFindById(root, IRCTC.PROCEED_BTN)?.let { payBtn ->
                    val text = payBtn.text?.toString()?.lowercase() ?: ""
                    val isValid = listOf("pay", "proceed", "confirm", "continue", "book", "भुगतान", "बुक")
                        .any { text.contains(it, true) }
                    if (isValid) {
                        click(payBtn)
                        return@withRoot true
                    }
                }
                false
            } ?: false
        }
    }

    private suspend fun waitForStableReviewScreen(): Boolean {
        val startTime = System.currentTimeMillis()
        var stableCount = 0
        var lastChangeTime = startTime
        
        while (stableCount < REVIEW_STABLE_COUNT_REQUIRED && 
               System.currentTimeMillis() - startTime < REVIEW_STABILITY_DURATION_MS) {
            delay(200L)
            val isReview = withRoot { root ->
                safeFindById(root, IRCTC.CAPTCHA_IMAGE) != null
            } ?: false
            
            if (isReview) {
                if (System.currentTimeMillis() - lastChangeTime >= 200L) {
                    stableCount++
                }
            } else {
                stableCount = 0
                lastChangeTime = System.currentTimeMillis()
            }
        }
        return stableCount >= REVIEW_STABLE_COUNT_REQUIRED
    }

    private fun saveFormCache(task: SniperTask) {
        getSharedPreferences("vmax_cache", Context.MODE_PRIVATE).edit().apply {
            putString("train_number", task.trainNumber)
            putString("journey_date", task.journeyDate)
            putString("trigger_time", task.triggerTime)
            putString("mobile", task.mobileNo)
            putBoolean("insurance", task.insurance)
            putBoolean("captcha_autofill", task.captchaAutofill)
            apply()
        }
        logDebug("💾 Form cache saved")
    }

    // ✅ FINAL FIXED onEvent() with FORM_FILLED case
    private suspend fun onEvent() {
        stateLock.withLock {
            if (!isArmed.get()) return
            
            val currentScreen = getCurrentScreen()
            
            // SMART RESUME: Manually login kiye ho to wahan se start karo
            if (currentStep == WorkflowStep.IDLE || currentStep == WorkflowStep.REFRESH_DONE) {
                when (currentScreen) {
                    "PASSENGER_FORM" -> {
                        logDebug("🔄 Smart resume detected: Already on Passenger Form")
                        currentStep = WorkflowStep.ATTACK_DONE
                        fillPassengerForm()
                        return
                    }
                    "REVIEW" -> {
                        logDebug("🔄 Smart resume detected: Already on Review Screen")
                        currentStep = WorkflowStep.REVIEW_READY
                    }
                    "PAYMENT" -> {
                        logDebug("🔄 Smart resume detected: Already on Payment Screen")
                        currentStep = WorkflowStep.PAYMENT_DONE
                    }
                    else -> { /* do nothing */ }
                }
            }
            
            if (currentScreen == "REVIEW" && !screenLock.compareAndSet(false, true)) {
                return
            }
            
            if (processingState.get() == ProcessingState.PROCESSING) return
            processingState.set(ProcessingState.PROCESSING)
            
            try {
                when (currentStep) {
                    WorkflowStep.IDLE -> {
                        if (executeRefresh()) {
                            currentStep = WorkflowStep.REFRESH_DONE
                            logDebug("➡️ $currentStep")
                            retryCount = 0
                        }
                    }
                    WorkflowStep.REFRESH_DONE -> {
                        if (executeAttack()) {
                            currentStep = WorkflowStep.ATTACK_DONE
                            logDebug("➡️ $currentStep")
                            delay(Timing.UI_LOAD_MS)
                            fillPassengerForm()
                        }
                    }
                    WorkflowStep.ATTACK_DONE -> {
                        if (formComplete.get()) {
                            currentStep = WorkflowStep.REVIEW_READY
                            logDebug("➡️ $currentStep")
                        }
                    }
                    // ✅ FIXED: Added missing FORM_FILLED case
                    WorkflowStep.FORM_FILLED -> {
                        currentStep = WorkflowStep.REVIEW_READY
                        logDebug("➡️ FORM_FILLED → REVIEW_READY")
                    }
                    WorkflowStep.REVIEW_READY -> {
                        if (waitForStableReviewScreen()) {
                            if (solveCaptcha()) {
                                currentStep = WorkflowStep.CAPTCHA_SOLVED
                                logDebug("➡️ $currentStep")
                            }
                        }
                    }
                    WorkflowStep.CAPTCHA_SOLVED -> {
                        if (isCaptchaFilled()) {
                            delay(Timing.NORMAL_MS)
                            if (selectPayment()) {
                                currentStep = WorkflowStep.PAYMENT_DONE
                                logDebug("➡️ $currentStep")
                            }
                        }
                    }
                    WorkflowStep.PAYMENT_DONE -> {
                        if (executeFinalPay()) {
                            currentStep = WorkflowStep.BOOKING_DONE
                            logDebug("🎉 BOOKING SUCCESSFUL! 🎉")
                            updateNotification("✅ BOOKING SUCCESSFUL! 🎉")
                            isArmed.set(false)
                            mainScope.launch {
                                LocalBroadcastManager.getInstance(this@WorkflowEngine)
                                    .sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
                            }
                        } else {
                            retryCount++
                            if (retryCount >= MAX_RETRY_COUNT) {
                                currentStep = WorkflowStep.FAILED
                                logError("❌ BOOKING FAILED after $MAX_RETRY_COUNT attempts")
                                updateNotification("❌ Booking failed, please retry")
                            } else {
                                logDebug("Retrying payment step ${retryCount + 1}/$MAX_RETRY_COUNT")
                                delay(RETRY_DELAY_MS)
                            }
                        }
                    }
                    WorkflowStep.BOOKING_DONE -> { /* Ignore */ }
                    WorkflowStep.FAILED -> {
                        logDebug("⚠️ In failed state, waiting for manual intervention")
                    }
                }
            } finally {
                processingState.set(ProcessingState.IDLE)
                screenLock.set(false)
            }
        }
    }

    private fun startWorkerLoop() {
        workerScope.launch {
            while (isActive) {
                val event = eventChannel.receiveCatching().getOrNull()
                if (event != null) {
                    try {
                        onEvent()
                    } catch (e: Exception) {
                        logError("Worker error: ${e.message}")
                    }
                }
                yield()
                delay(WORKER_DELAY_MS)
            }
        }
    }

    // ==================== HELPER FUNCTIONS ====================
    internal fun setTextFast(node: AccessibilityNodeInfo, text: String) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("vmax_sniper", text))
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
            val duration = getGestureDuration()
            val longPressGesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            dispatchGesture(longPressGesture, null, null)
        }
    }

    private suspend fun softReset() {
        isArmed.set(false)
        formComplete.set(false)
        lastEventTime.set(0L)
        currentStep = WorkflowStep.IDLE
        processingState.set(ProcessingState.IDLE)
        screenLock.set(false)
        cachedScreenType = ""
        retryCount = 0
        logDebug("🔄 Soft Reset Complete")
    }

    // ==================== SERVICE LIFECYCLE ====================
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        createNotificationChannel()
        startForeground(1, buildNotification("⚡ VMAX ELITE ACTIVE"))
        startWorkerLoop()
        logDebug("✅ SERVICE ACTIVE")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SNIPER) {
            val task = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_TASK, SniperTask::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_TASK)
            }
            
            val forceAttack = intent.getBooleanExtra(EXTRA_FORCE_ATTACK, false)
            
            if (task != null) {
                activeTask.set(task)
                saveFormCache(task)
                
                workerScope.launch {
                    softReset()
                    isArmed.set(true)
                    
                    if (forceAttack) {
                        logDebug("🔥 FORCE ATTACK MODE - Starting immediately!")
                        mainScope.launch {
                            if (currentStep == WorkflowStep.IDLE) {
                                val currentScreen = getCurrentScreen()
                                when (currentScreen) {
                                    "PASSENGER_FORM" -> {
                                        currentStep = WorkflowStep.ATTACK_DONE
                                        fillPassengerForm()
                                    }
                                    "REVIEW" -> {
                                        currentStep = WorkflowStep.REVIEW_READY
                                        if (waitForStableReviewScreen()) {
                                            if (solveCaptcha()) {
                                                currentStep = WorkflowStep.CAPTCHA_SOLVED
                                            }
                                        }
                                    }
                                    "PAYMENT" -> {
                                        currentStep = WorkflowStep.PAYMENT_DONE
                                    }
                                    else -> {
                                        withRoot { root ->
                                            val searchNode = safeFindById(root, IRCTC.SEARCH_BTN) ?:
                                                             safeFindByText(root, IRCTC.SEARCH_BTN_FALLBACK)
                                            searchNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                        }
                                        currentStep = WorkflowStep.REFRESH_DONE
                                    }
                                }
                                logDebug("⚡ FORCE ATTACK MODE ENGAGED!")
                            }
                        }
                    } else {
                        val parts = task.triggerTime.split(":")
                        val targetHour = parts.getOrNull(0)?.toIntOrNull() ?: 10
                        val targetMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val targetSecond = parts.getOrNull(2)?.toIntOrNull() ?: 0
                        val advanceMs = task.msAdvance.toLong().coerceIn(120, 200)
                        
                        TimeSyncManager.syncTime()
                        logDebug("🎯 Target: $targetHour:$targetMinute:$targetSecond")
                        
                        TimeSniper.scheduleFire(targetHour, targetMinute, targetSecond, advanceMs) {
                            isArmed.set(true)
                            mainScope.launch {
                                if (currentStep == WorkflowStep.IDLE) {
                                    withRoot { root ->
                                        val searchNode = safeFindById(root, IRCTC.SEARCH_BTN) ?:
                                                         safeFindByText(root, IRCTC.SEARCH_BTN_FALLBACK)
                                        searchNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    }
                                    currentStep = WorkflowStep.REFRESH_DONE
                                    logDebug("🔥 ATTACK MODE ENGAGED!")
                                }
                            }
                        }
                    }
                }
                logDebug("⏳ Armed and waiting for ${task.triggerTime}")
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(this, WorkflowEngine::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isArmed.get() || event == null) return
        
        val now = System.currentTimeMillis()
        
        while (true) {
            val last = lastEventTime.get()
            if (now - last < MIN_EVENT_GAP_MS) return
            if (lastEventTime.compareAndSet(last, now)) break
        }
        
        if (event.packageName?.toString() != IRCTC.PKG) return
        
        val eventType = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "STATE"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT"
            else -> "OTHER"
        }
        
        val debounceMs = getDynamicDebounce()
        if (eventType == lastProcessedEventType && now - lastProcessedEventTime < debounceMs) {
            return
        }
        lastProcessedEventType = eventType
        lastProcessedEventTime = now
        
        val result = eventChannel.trySend(UiEvent(eventType, now))
        if (result.isFailure) {
            logError("⚠️ EVENT DROPPED (queue full)")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 VMAX ELITE SNIPER")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun updateNotification(message: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(1, buildNotification(message))
            logDebug(message)
        } catch (e: Exception) {
            logError("Notification error: ${e.message}")
        }
    }

    override fun onInterrupt() { 
        workerScope.launch { softReset() }
        logDebug("⏸️ SERVICE INTERRUPTED")
    }
    
    override fun onDestroy() { 
        eventChannel.close()
        workerScope.cancel()
        mainScope.cancel()
        super.onDestroy()
        logDebug("💀 SERVICE DESTROYED")
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.contains(expected.flattenToString())
}
