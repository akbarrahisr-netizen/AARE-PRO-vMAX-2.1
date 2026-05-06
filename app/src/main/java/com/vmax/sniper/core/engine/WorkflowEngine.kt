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
import com.vmax.sniper.core.network.TimeSyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * VMAX ELITE - TATKAL PRO EDITION v12.0.0
 * ✅ MASTER LIST | POPUP HANDLER | CAPTCHA LOAD DETECTION | SCROLL SUPPORT | WEBVIEW FALLBACK
 */
class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_ENGINE"
        private const val MIN_EVENT_GAP_MS = 30L
        private const val MIN_CLICK_GAP_MS = 20L
        private const val SCREEN_CACHE_MS = 150L
        private const val REVIEW_STABILITY_DURATION_MS = 120000L
        private const val CAPTCHA_TIMEOUT_MS = 8000L
        private const val REVIEW_STABLE_COUNT_REQUIRED = 3
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 100L
        private const val TIME_SYNC_TIMEOUT_MS = 3000L
        private const val WATCHDOG_INTERVAL_MS = 2000L
        private const val WATCHDOG_TIMEOUT_MS = 5000L
        private const val SCROLL_TIMEOUT_MS = 500L
        private const val CAPTCHA_LOAD_WAIT_MS = 1500L
        
        const val ACTION_START_SNIPER = "com.vmax.sniper.START_SNIPER"
        const val EXTRA_TASK = "extra_task"
        const val EXTRA_FORCE_ATTACK = "FORCE_ATTACK"
        const val ACTION_SERVICE_STOPPED = "com.vmax.sniper.SERVICE_STOPPED"

        object Timing {
            const val FAST_MS = 15L
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
            const val MASTER_LIST_CHECKBOX = "$PKG:id/cb_passenger"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
            
            val SEARCH_BTN_FALLBACK = listOf("Search Trains", "Search", "Train Search")
            val BOOK_NOW_FALLBACK = listOf("Book Now", "Book", "Confirm Booking")
            val NAME_TEXT_FALLBACK = listOf("Name", "नाम", "Passenger Name")
            val AGE_TEXT_FALLBACK = listOf("Age", "आयु", "Passenger Age")
            val GENDER_TEXT_FALLBACK = listOf("Gender", "लिंग", "Male", "Female", "Transgender")
            val REVIEW_TEXT_FALLBACK = listOf("Review Journey", "यात्रा विवरण", "Passenger Details", "Review Booking")
            val ADD_PASSENGER_TEXT_FALLBACK = listOf("Add Passenger", "Select Passenger", "Add")
            val ALERT_BUTTONS = listOf("OK", "YES", "I AGREE", "DISMISS", "सहमति", "Agree", "Continue", "Close", "Got It")
            val PROCEED_TEXT_FALLBACK = listOf("Proceed", "Continue", "Next", "आगे बढ़ें")
            val PAYMENT_WEBVIEW_FALLBACK = listOf("Pay", "Confirm", "Proceed", "Pay Now", "Verify")
        }
    }

    // ==================== STATE MACHINE ====================
    private enum class WorkflowStep {
        IDLE, REFRESH_DONE, ATTACK_DONE, FORM_FILLING_MASTER_LIST, FORM_FILLING_DETAILS, 
        FORM_FILLED, REVIEW_READY, CAPTCHA_SOLVED, PAYMENT_DONE, BOOKING_DONE, FAILED
    }
    
    private enum class ProcessingState {
        IDLE, PROCESSING
    }
    
    private data class UiEvent(val type: String, val timestamp: Long)
    
    private val currentStep = AtomicReference(WorkflowStep.IDLE)
    private val processingState = AtomicReference(ProcessingState.IDLE)
    
    @Volatile
    private var isProcessingEvent = false
    private var retryCount = 0

    private val eventChannel = Channel<UiEvent>(
        capacity = 200,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var cachedScreenType = ""
    private var cachedScreenTime = 0L
    private val screenLock = AtomicBoolean(false)
    
    private var lastProcessedEventTime = 0L
    private var lastProcessedEventType = ""

    private val activeTask = AtomicReference<SniperTask?>(null)
    private val isArmed = AtomicBoolean(false)
    private val formComplete = AtomicBoolean(false)
    private val lastEventTime = AtomicLong(0L)
    private val lastClickTime = AtomicLong(0L)
    private var lastProgressTime = System.currentTimeMillis()

    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private fun getCurrentTimestamp(): String = timestampFormat.format(Date())

    private fun logDebug(message: String) = Log.d(TAG, "[${getCurrentTimestamp()}] $message")
    private fun logError(message: String) = Log.e(TAG, "[${getCurrentTimestamp()}] $message")
    private fun logError(message: String, e: Exception) = Log.e(TAG, "[${getCurrentTimestamp()}] $message", e)

    private fun updateProgress() { lastProgressTime = System.currentTimeMillis() }

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

    // ==================== SCREEN DETECTION ====================
    private fun detectScreenType(root: AccessibilityNodeInfo): String {
        val now = System.currentTimeMillis()
        if (cachedScreenType.isNotBlank() && now - cachedScreenTime < SCREEN_CACHE_MS) {
            return cachedScreenType
        }
        
        // ✅ Check for popups first
        for (text in IRCTC.ALERT_BUTTONS) {
            if (safeFindByText(root, listOf(text)) != null) {
                cachedScreenType = "ALERT"
                cachedScreenTime = now
                return "ALERT"
            }
        }
        
        val screen = when {
            safeFindById(root, IRCTC.SEARCH_BTN) != null || safeFindByText(root, IRCTC.SEARCH_BTN_FALLBACK) != null -> "SEARCH"
            safeFindById(root, IRCTC.BOOK_NOW_BTN) != null || safeFindByText(root, IRCTC.BOOK_NOW_FALLBACK) != null -> "TRAIN_LIST"
            safeFindById(root, IRCTC.NAME_INPUT) != null || safeFindByText(root, IRCTC.NAME_TEXT_FALLBACK) != null -> "PASSENGER_FORM"
            safeFindById(root, IRCTC.MASTER_LIST_CHECKBOX) != null -> "MASTER_LIST"
            safeFindById(root, IRCTC.CAPTCHA_IMAGE) != null || safeFindByText(root, IRCTC.REVIEW_TEXT_FALLBACK) != null -> "REVIEW"
            safeFindById(root, IRCTC.PAYMENT_UPI_APPS) != null -> "PAYMENT"
            else -> "UNKNOWN"
        }
        
        cachedScreenType = screen
        cachedScreenTime = now
        return screen
    }
    
    private fun getCurrentScreen(): String {
        val root = rootInActiveWindow ?: return "UNKNOWN"
        return try { detectScreenType(root) } catch (e: Exception) { "UNKNOWN" }
    }

    private fun AccessibilityNodeInfo.isActuallyVisible(): Boolean {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return isVisibleToUser || (Build.VERSION.SDK_INT >= 30 && bounds.width() > 0 && bounds.height() > 0 && isClickable)
    }

    // ==================== NODE FINDERS ====================
    private fun safeFindById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        return try { root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isActuallyVisible() } } catch (e: Exception) { null }
    }

    private fun safeFindByText(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        return try {
            for (text in texts) {
                val node = root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isActuallyVisible() }
                if (node != null) return node
            }
            null
        } catch (e: Exception) { null }
    }
    
    private suspend fun <T> withFreshRoot(block: (AccessibilityNodeInfo) -> T?): T? {
        delay(10)
        val root = rootInActiveWindow ?: return null
        return try { block(root) } catch (e: Exception) { logError("withFreshRoot error: ${e.message}"); null }
    }
    
    private suspend fun <T> withRoot(block: suspend (AccessibilityNodeInfo) -> T?): T? = withFreshRoot(block)

    // ==================== CLICK ====================
    private fun safeClickDelay(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastClickTime.get()
        if (now - last < MIN_CLICK_GAP_MS) return false
        return lastClickTime.compareAndSet(last, now)
    }

    private fun ultraClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !safeClickDelay()) return false
        return try {
            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent = parent.parent
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                val path = Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }
                val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
                dispatchGesture(gesture, null, null)
                true
            } else false
        } catch (e: Exception) { logError("UltraClick failed: ${e.message}"); false }
    }
    
    internal suspend fun stableClick(node: AccessibilityNodeInfo?): Boolean = ultraClick(node)
    private fun click(node: AccessibilityNodeInfo?): Boolean = ultraClick(node)

    private suspend fun ultraSetText(node: AccessibilityNodeInfo, text: String) {
        repeat(2) {
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return
            delay(5)
        }
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("vmax_sniper", text))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) { logError("UltraSetText failed: ${e.message}") }
    }
    
    internal suspend fun setTextFast(node: AccessibilityNodeInfo, text: String) = ultraSetText(node, text)

    // ==================== HELPER FUNCTIONS ====================
    private suspend fun handlePopups(root: AccessibilityNodeInfo): Boolean {
        var handled = false
        for (text in IRCTC.ALERT_BUTTONS) {
            val btn = safeFindByText(root, listOf(text))
            if (btn != null && btn.isClickable) {
                ultraClick(btn)
                logDebug("🚨 Alert Handled: $text")
                delay(200)
                handled = true
                break
            }
        }
        return handled
    }
    
    private suspend fun scrollToView(node: AccessibilityNodeInfo): Boolean {
        return try {
            val args = Bundle()
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 10)
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, args)
            delay(SCROLL_TIMEOUT_MS)
            true
        } catch (e: Exception) { false }
    }

    // ==================== WORKFLOW ACTIONS ====================
    private suspend fun withUltraRetry(block: suspend () -> Boolean): Boolean {
        repeat(MAX_RETRY_COUNT) { attempt ->
            try { if (block()) return true } catch (e: Exception) { logError("Retry ${attempt + 1} failed: ${e.message}") }
            delay(RETRY_DELAY_MS * (attempt + 1))
        }
        return false
    }
    
    private suspend fun withSafeRetry(block: suspend () -> Boolean): Boolean = withUltraRetry(block)
    private suspend fun executeRefresh(): Boolean = withUltraRetry {
        withFreshRoot { root ->
            val node = safeFindById(root, IRCTC.SEARCH_BTN) ?: safeFindByText(root, IRCTC.SEARCH_BTN_FALLBACK)
            node?.isClickable == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } ?: false
    }
    private suspend fun executeAttack(): Boolean = withUltraRetry {
        withFreshRoot { root ->
            val node = safeFindById(root, IRCTC.BOOK_NOW_BTN) ?: safeFindByText(root, IRCTC.BOOK_NOW_FALLBACK)
            node?.let { ultraClick(it); return@withFreshRoot true }
            false
        } ?: false
    }
    private suspend fun isFormAlreadyFilled(): Boolean = withFreshRoot { root ->
        safeFindById(root, IRCTC.PROCEED_BTN) != null || safeFindByText(root, IRCTC.PROCEED_TEXT_FALLBACK) != null
    } ?: formComplete.get()

    private suspend fun handleMasterList(root: AccessibilityNodeInfo): Boolean {
        logDebug("📋 Master List detected! Selecting passengers...")
        val checkboxes = root.findAccessibilityNodeInfosByViewId(IRCTC.MASTER_LIST_CHECKBOX)
        val passengersNeeded = activeTask.get()?.passengers?.size ?: 1
        
        for (i in 0 until minOf(checkboxes.size, passengersNeeded)) {
            ultraClick(checkboxes[i])
            delay(20)
        }
        
        val addBtn = safeFindByText(root, IRCTC.ADD_PASSENGER_TEXT_FALLBACK) ?: 
                     safeFindById(root, IRCTC.ADD_PASSENGER_BTN)
        ultraClick(addBtn)
        delay(Timing.UI_LOAD_MS)
        return true
    }

    private suspend fun fillPassengerForm() {
        updateProgress()
        
        withFreshRoot { root ->
            if (safeFindById(root, IRCTC.MASTER_LIST_CHECKBOX) != null) {
                currentStep.set(WorkflowStep.FORM_FILLING_MASTER_LIST)
                handleMasterList(root)
                currentStep.set(WorkflowStep.FORM_FILLING_DETAILS)
            }
        }
        
        currentStep.set(WorkflowStep.FORM_FILLING_DETAILS)
        val task = activeTask.get() ?: return
        val passengers = task.passengers.take(4)
        
        for ((index, passenger) in passengers.withIndex()) {
            if (index > 0) {
                withFreshRoot { root -> 
                    safeFindById(root, IRCTC.ADD_PASSENGER_BTN)?.let { ultraClick(it) } 
                }
                delay(Timing.FAST_MS)
            }
            
            withFreshRoot { root ->
                var nameNode = safeFindById(root, IRCTC.NAME_INPUT)
                if (nameNode == null) nameNode = safeFindByText(root, IRCTC.NAME_TEXT_FALLBACK)
                nameNode?.let { ultraSetText(it, passenger.name) }
            }
            delay(Timing.FAST_MS)
            
            withFreshRoot { root ->
                var ageNode = safeFindById(root, IRCTC.AGE_INPUT)
                if (ageNode == null) ageNode = safeFindByText(root, IRCTC.AGE_TEXT_FALLBACK)
                ageNode?.let { ultraSetText(it, passenger.age.toString()) }
            }
            delay(Timing.FAST_MS)
            
            if (passenger.gender.isNotBlank()) {
                withFreshRoot { root ->
                    var genderSpinner = safeFindById(root, IRCTC.GENDER_SPINNER)
                    if (genderSpinner == null) genderSpinner = safeFindByText(root, IRCTC.GENDER_TEXT_FALLBACK)
                    genderSpinner?.let { ultraClick(it) }
                }
                delay(Timing.FAST_MS)
                withFreshRoot { root -> 
                    val genderNode = root.findAccessibilityNodeInfosByText(passenger.gender)
                        .firstOrNull { it.isActuallyVisible() }
                    genderNode?.let { ultraClick(it) }
                }
                delay(Timing.FAST_MS)
            }
            updateProgress()
        }
        
        withFreshRoot { root ->
            val insuranceBtn = if (task.insurance) IRCTC.INSURANCE_YES else IRCTC.INSURANCE_NO
            safeFindById(root, insuranceBtn)?.let { ultraClick(it) }
        }
        delay(Timing.FAST_MS)
        
        withFreshRoot { root ->
            var proceedBtn = safeFindById(root, IRCTC.PROCEED_BTN) ?:
                             safeFindByText(root, IRCTC.PROCEED_TEXT_FALLBACK)
            
            if (proceedBtn == null) {
                val scrollableViews = root.findAccessibilityNodeInfosByViewId("android:id/list")
                if (scrollableViews.isNotEmpty()) {
                    scrollToView(scrollableViews[0])
                    proceedBtn = safeFindById(root, IRCTC.PROCEED_BTN) ?:
                                 safeFindByText(root, IRCTC.PROCEED_TEXT_FALLBACK)
                }
            }
            
            proceedBtn?.let {
                ultraClick(it)
                formComplete.set(true)
                currentStep.set(WorkflowStep.FORM_FILLED)
                logDebug("📋 Form complete")
                updateProgress()
            }
        }
    }

    private suspend fun solveCaptcha(): Boolean = withContext(Dispatchers.Default) {
        withFreshRoot { root ->
            handlePopups(root)
            
            val captchaImage = safeFindById(root, IRCTC.CAPTCHA_IMAGE)
            val captchaInput = safeFindById(root, IRCTC.CAPTCHA_INPUT)
            
            if (captchaImage != null && captchaInput != null && activeTask.get()?.captchaAutofill == true && captchaInput.text.isNullOrBlank()) {
                val isLoading = captchaImage.contentDescription?.contains("loading", ignoreCase = true) == true
                if (isLoading) {
                    logDebug("⏳ Captcha loading, waiting...")
                    delay(CAPTCHA_LOAD_WAIT_MS)
                }
                
                logDebug("🔐 Solving Captcha...")
                try {
                    withTimeout(CAPTCHA_TIMEOUT_MS) { 
                        CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage, captchaInput) 
                    }
                    true
                } catch (e: TimeoutCancellationException) {
                    logError("❌ Captcha solve timeout"); notifyUserCaptchaRequired(); waitForManualCaptcha(captchaInput); true
                } catch (e: Exception) {
                    logError("❌ Captcha solve error", e); notifyUserCaptchaRequired(); waitForManualCaptcha(captchaInput); true
                }
            } else false
        } ?: false
    }
    
    private fun notifyUserCaptchaRequired() {
        updateNotification("⚠️ Captcha required - Please enter manually")
        try { (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).let { if (Build.VERSION.SDK_INT >= 26) it.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") it.vibrate(500) } } catch (e: Exception) { logError("Vibrate failed", e) }
    }
    
    private suspend fun waitForManualCaptcha(inputNode: AccessibilityNodeInfo) {
        logDebug("⏳ Waiting for manual captcha...")
        repeat(50) { delay(100); if (inputNode.text.isNotBlank()) { logDebug("✅ Manual captcha detected"); updateProgress(); return } }
        logDebug("⚠️ No manual captcha entered")
    }
    
    private suspend fun isCaptchaFilled(): Boolean = withFreshRoot { root -> safeFindById(root, IRCTC.CAPTCHA_INPUT)?.text?.isNotBlank() == true } ?: false
    
    private suspend fun selectPayment(): Boolean = withUltraRetry {
        withFreshRoot { root ->
            handlePopups(root)
            val upiNode = safeFindById(root, IRCTC.PAYMENT_UPI_APPS)
            if (upiNode != null) {
                ultraClick(upiNode); delay(Timing.NORMAL_MS)
                for (app in listOf("Google Pay", "PhonePe", "Paytm", "BHIM")) {
                    val appNode = root.findAccessibilityNodeInfosByText(app).firstOrNull { it.isActuallyVisible() }
                    if (appNode != null) { 
                        ultraClick(appNode)
                        delay(Timing.NORMAL_MS)
                        
                        delay(1000)
                        withFreshRoot { newRoot ->
                            val payButton = safeFindByText(newRoot, IRCTC.PAYMENT_WEBVIEW_FALLBACK)
                            if (payButton == null) {
                                val displayMetrics = resources.displayMetrics
                                val gesture = GestureDescription.Builder()
                                    .addStroke(GestureDescription.StrokeDescription(
                                        Path().apply { moveTo((displayMetrics.widthPixels / 2).toFloat(), (displayMetrics.heightPixels * 0.85).toFloat()) }, 0, 50))
                                    .build()
                                dispatchGesture(gesture, null, null)
                                logDebug("🖱️ Webview fallback gesture")
                            } else {
                                ultraClick(payButton)
                            }
                        }
                        return@withFreshRoot true 
                    }
                }
                return@withFreshRoot true
            }
            
            safeFindById(root, IRCTC.PAYMENT_BHIM_UPI)?.let { ultraClick(it); return@withFreshRoot true }
            safeFindById(root, IRCTC.PAYMENT_CARDS)?.let { ultraClick(it); return@withFreshRoot true }
            false
        } ?: false
    }
    
    private suspend fun executeFinalPay(): Boolean = withUltraRetry {
        withFreshRoot { root ->
            handlePopups(root)
            val proceedBtn = safeFindById(root, IRCTC.PROCEED_BTN) ?:
                            safeFindByText(root, IRCTC.PAYMENT_WEBVIEW_FALLBACK)
            proceedBtn?.let { payBtn ->
                val text = payBtn.text?.toString()?.lowercase() ?: ""
                if (listOf("pay", "proceed", "confirm", "continue", "book", "भुगतान", "बुक", "verify").any { text.contains(it, true) }) {
                    ultraClick(payBtn); return@withFreshRoot true
                }
            }
            false
        } ?: false
    }
    
    private suspend fun waitForStableReviewScreen(): Boolean {
        val startTime = System.currentTimeMillis()
        var stableCount = 0
        var lastChangeTime = startTime
        while (stableCount < REVIEW_STABLE_COUNT_REQUIRED && System.currentTimeMillis() - startTime < REVIEW_STABILITY_DURATION_MS) {
            delay(200L)
            val isReview = withFreshRoot { root ->
                safeFindById(root, IRCTC.CAPTCHA_IMAGE) != null ||
                safeFindByText(root, IRCTC.REVIEW_TEXT_FALLBACK) != null
            } ?: false
            if (isReview) { if (System.currentTimeMillis() - lastChangeTime >= 200L) stableCount++ } else { stableCount = 0; lastChangeTime = System.currentTimeMillis() }
            updateProgress()
        }
        return stableCount >= REVIEW_STABLE_COUNT_REQUIRED
    }

    private fun saveFormCache(task: SniperTask) {
        getSharedPreferences("vmax_cache", Context.MODE_PRIVATE).edit().apply {
            putString("train_number", task.trainNumber); putString("journey_date", task.journeyDate)
            putString("trigger_time", task.triggerTime); putString("mobile", task.mobileNo)
            putBoolean("insurance", task.insurance); putBoolean("captcha_autofill", task.captchaAutofill)
            apply()
        }
        logDebug("💾 Form cache saved")
    }

    private suspend inline fun withScreenLockSafe(block: suspend () -> Unit) { if (!screenLock.compareAndSet(false, true)) return; try { block() } finally { screenLock.set(false) } }

    // ✅ ULTRA WORKER LOOP
    private fun startUltraWorker() {
        workerScope.launch {
            for (event in eventChannel) {
                try { onEvent() } catch (e: Exception) { logError("Worker crash: ${e.message}") }
            }
        }
        
        workerScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val step = currentStep.get()
                val timeout = when (step) {
                    WorkflowStep.REVIEW_READY, WorkflowStep.CAPTCHA_SOLVED -> 15000L
                    WorkflowStep.PAYMENT_DONE -> 10000L
                    else -> WATCHDOG_TIMEOUT_MS
                }
                if (System.currentTimeMillis() - lastProgressTime > timeout) {
                    logError("⚠️ Watchdog Reset (step: $step)")
                    softReset()
                }
            }
        }
    }
    
    private fun startWorkerLoop() = startUltraWorker()
    private fun forceSendEvent(event: UiEvent) { if (!eventChannel.trySend(event).isSuccess) { logError("Queue Full → Flushing"); eventChannel.tryReceive().getOrNull(); eventChannel.trySend(event) } }

    private suspend fun softReset() {
        logDebug("🔄 Soft Reset"); isArmed.set(false); formComplete.set(false); lastEventTime.set(0L)
        currentStep.set(WorkflowStep.IDLE); processingState.set(ProcessingState.IDLE); screenLock.set(false)
        cachedScreenType = ""; retryCount = 0; updateProgress(); logDebug("✅ Reset Complete")
    }

    private fun buildNotification(message: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 VMAX ELITE SNIPER").setContentText(message).setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW)
    
    internal fun updateNotification(message: String) { try { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, buildNotification(message).build()); logDebug(message) } catch (e: Exception) { logError("Notification error: ${e.message}") } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        if (Build.VERSION.SDK_INT >= 26) { val channel = NotificationChannel("vmax_channel", "VMAX Sniper", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(channel) }
        startForeground(1, buildNotification("⚡ VMAX ELITE ACTIVE").build())
        startWorkerLoop()
        logDebug("✅ SERVICE ACTIVE")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SNIPER) {
            val task = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_TASK, SniperTask::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_TASK)
            val forceAttack = intent.getBooleanExtra(EXTRA_FORCE_ATTACK, false)
            if (task != null) {
                activeTask.set(task); saveFormCache(task)
                workerScope.launch {
                    softReset(); isArmed.set(true); updateProgress()
                    if (forceAttack) {
                        logDebug("🔥 FORCE ATTACK MODE!")
                        mainScope.launch {
                            if (currentStep.get() == WorkflowStep.IDLE) {
                                when (getCurrentScreen()) {
                                    "MASTER_LIST", "PASSENGER_FORM" -> {
                                        currentStep.set(WorkflowStep.ATTACK_DONE)
                                        if (!isFormAlreadyFilled()) fillPassengerForm()
                                    }
                                    "REVIEW" -> { currentStep.set(WorkflowStep.REVIEW_READY); if (waitForStableReviewScreen() && solveCaptcha()) currentStep.set(WorkflowStep.CAPTCHA_SOLVED) }
                                    "PAYMENT" -> currentStep.set(WorkflowStep.PAYMENT_DONE)
                                    else -> { withFreshRoot { root -> safeFindById(root, IRCTC.SEARCH_BTN)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }; currentStep.set(WorkflowStep.REFRESH_DONE) }
                                }
                            }
                        }
                    } else {
                        val parts = task.triggerTime.split(":")
                        val (targetHour, targetMinute, targetSecond) = parts.getOrNull(0)?.toIntOrNull() ?: 10 to (parts.getOrNull(1)?.toIntOrNull() ?: 0) to (parts.getOrNull(2)?.toIntOrNull() ?: 0)
                        val advanceMs = task.msAdvance.toLong().coerceIn(120, 200)
                        withTimeoutOrNull(TIME_SYNC_TIMEOUT_MS) { TimeSyncManager.syncWithNetwork() }
                        logDebug("🎯 Target: $targetHour:$targetMinute:$targetSecond | Current: ${TimeSyncManager.getPreciseTimeString()}")
                        TimeSniper.scheduleFire(targetHour, targetMinute, targetSecond, advanceMs) {
                            isArmed.set(true)
                            mainScope.launch {
                                if (currentStep.get() == WorkflowStep.IDLE) {
                                    withFreshRoot { root -> safeFindById(root, IRCTC.SEARCH_BTN)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                                    currentStep.set(WorkflowStep.REFRESH_DONE)
                                    logDebug("🔥 ATTACK MODE ENGAGED!")
                                }
                            }
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent); startService(Intent(this, WorkflowEngine::class.java)) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isArmed.get() || event == null || event.packageName?.toString() != IRCTC.PKG) return
        val now = System.currentTimeMillis()
        var last = lastEventTime.get()
        while (now - last < MIN_EVENT_GAP_MS) { if (lastEventTime.compareAndSet(last, now)) break; last = lastEventTime.get() }
        val eventType = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) "STATE" else "CONTENT"
        if (eventType == lastProcessedEventType && now - lastProcessedEventTime < getDynamicDebounce()) return
        lastProcessedEventType = eventType; lastProcessedEventTime = now
        forceSendEvent(UiEvent(eventType, now))
    }

    private suspend fun onEvent() {
        if (isProcessingEvent) return
        isProcessingEvent = true
        try {
            if (!isArmed.get()) return
            val currentScreen = getCurrentScreen()
            val step = currentStep.get()
            
            // ✅ Handle Alert Popups first
            if (currentScreen == "ALERT") {
                withFreshRoot { root -> handlePopups(root) }
                delay(200)
                return
            }
            
            if (step == WorkflowStep.IDLE || step == WorkflowStep.REFRESH_DONE) {
                when (currentScreen) {
                    "MASTER_LIST", "PASSENGER_FORM" -> {
                        logDebug("🔄 Smart resume: Passenger Form/Master List")
                        currentStep.set(WorkflowStep.ATTACK_DONE)
                        if (!isFormAlreadyFilled()) fillPassengerForm()
                        return
                    }
                    "REVIEW" -> {
                        logDebug("🔄 Smart resume: Review Screen")
                        currentStep.set(WorkflowStep.REVIEW_READY)
                    }
                    "PAYMENT" -> {
                        logDebug("🔄 Smart resume: Payment Screen")
                        currentStep.set(WorkflowStep.PAYMENT_DONE)
                    }
                    else -> { }
                }
            }
            
            if ((currentScreen == "REVIEW" || currentScreen == "MASTER_LIST") && !screenLock.compareAndSet(false, true)) {
                return
            }
            
            if (!processingState.compareAndSet(ProcessingState.IDLE, ProcessingState.PROCESSING)) {
                return
            }
            
            try {
                when (currentStep.get()) {
                    WorkflowStep.IDLE -> { if (executeRefresh()) { currentStep.set(WorkflowStep.REFRESH_DONE); logDebug("➡️ REFRESH_DONE"); retryCount = 0; updateProgress() } }
                    WorkflowStep.REFRESH_DONE -> { if (executeAttack()) { currentStep.set(WorkflowStep.ATTACK_DONE); logDebug("➡️ ATTACK_DONE"); delay(Timing.UI_LOAD_MS); fillPassengerForm() } }
                    WorkflowStep.ATTACK_DONE -> { if (formComplete.get()) { currentStep.set(WorkflowStep.REVIEW_READY); logDebug("➡️ REVIEW_READY"); updateProgress() } }
                    WorkflowStep.FORM_FILLING_MASTER_LIST -> delay(100)
                    WorkflowStep.FORM_FILLING_DETAILS -> delay(50)
                    WorkflowStep.FORM_FILLED -> { currentStep.set(WorkflowStep.REVIEW_READY); logDebug("➡️ FORM_FILLED → REVIEW_READY"); updateProgress() }
                    WorkflowStep.REVIEW_READY -> { if (waitForStableReviewScreen() && solveCaptcha()) { currentStep.set(WorkflowStep.CAPTCHA_SOLVED); logDebug("➡️ CAPTCHA_SOLVED"); updateProgress() } }
                    WorkflowStep.CAPTCHA_SOLVED -> { if (isCaptchaFilled()) { delay(Timing.NORMAL_MS); if (selectPayment()) { currentStep.set(WorkflowStep.PAYMENT_DONE); logDebug("➡️ PAYMENT_DONE"); updateProgress() } } }
                    WorkflowStep.PAYMENT_DONE -> {
                        if (executeFinalPay()) {
                            currentStep.set(WorkflowStep.BOOKING_DONE); logDebug("🎉 BOOKING SUCCESSFUL! 🎉"); updateNotification("✅ BOOKING SUCCESSFUL! 🎉")
                            isArmed.set(false); mainScope.launch { LocalBroadcastManager.getInstance(this@WorkflowEngine).sendBroadcast(Intent(ACTION_SERVICE_STOPPED)) }
                        } else { retryCount++; if (retryCount >= MAX_RETRY_COUNT) { currentStep.set(WorkflowStep.FAILED); logError("❌ BOOKING FAILED"); updateNotification("❌ Booking failed") } else { logDebug("Retry ${retryCount + 1}/$MAX_RETRY_COUNT"); delay(RETRY_DELAY_MS) } }
                    }
                    WorkflowStep.BOOKING_DONE -> { }
                    WorkflowStep.FAILED -> logDebug("⚠️ Failed state - manual intervention needed")
                }
            } finally { processingState.set(ProcessingState.IDLE); screenLock.set(false) }
        } finally { isProcessingEvent = false }
    }

    override fun onInterrupt() { workerScope.launch { softReset() }; logDebug("⏸️ SERVICE INTERRUPTED") }
    override fun onDestroy() { eventChannel.close(); workerScope.cancel(); mainScope.cancel(); super.onDestroy(); logDebug("💀 SERVICE DESTROYED") }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains(expected.flattenToString())
}
