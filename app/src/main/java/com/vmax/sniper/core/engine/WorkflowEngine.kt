package com.vmax.sniper.core.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_SCROLL_BACKWARD
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_SCROLL_FORWARD
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

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
            const val ADD_PASSENGER_BTN = "$PKG:id/tv_add_passanger"
            const val PROCEED_BTN = "$PKG:id/btn_proceed"
            const val BOOK_NOW_BTN = "$PKG:id/btn_book_now"
            const val SEARCH_BTN = "$PKG:id/btn_search_trains"
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
        }
    }

    enum class EngineState {
        IDLE, PRE_GAME, REFRESH, ATTACK, FORM, CAPTCHA, PAYMENT, DONE
    }

    // ==================== STATE MANAGEMENT ====================
    private val currentState = AtomicReference(EngineState.IDLE)
    private val stateMutex = Mutex()
    private var lastStateTransition = 0L

    // ==================== VARIABLES ====================
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val isProcessing = AtomicBoolean(false)
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var hasRefreshed = false
    private var isReviewClicked = false
    private var lastEventTime = 0L
    private var lastWindowHash = 0
    private val clipboard: ClipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private var currentEngineJob: Job? = null

    // ✅ Timestamp formatter for precise logs
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private fun getCurrentTimestamp(): String = timestampFormat.format(Date(TimeSyncManager.currentTimeMillis()))

    // ==================== ✅ FIX 3 & 4: PUBLIC API FOR CAPTCHA SOLVER ====================
    fun getStableRoot(): AccessibilityNodeInfo? = rootInActiveWindow

    // ✅ FIX 3: Changed from private to internal so CaptchaSolver can access
    internal fun stableClick(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    // ==================== STATE HELPERS ====================
    private suspend fun transitionTo(newState: EngineState) {
        stateMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastStateTransition < 100) return
            Log.d(TAG, "[${getCurrentTimestamp()}] 📍 STATE: ${currentState.get()} → $newState")
            currentState.set(newState)
            lastStateTransition = now
        }
    }

    private suspend fun isState(expected: EngineState): Boolean = stateMutex.withLock {
        currentState.get() == expected
    }

    // ==================== ROOT HELPER ====================
    private inline fun <T> withRoot(block: (AccessibilityNodeInfo) -> T?): T? {
        val root = rootInActiveWindow ?: return null
        return try { block(root) } finally { root.recycle() }
    }

    // ==================== TEXT HELPERS ====================
    fun setTextFast(node: AccessibilityNodeInfo, text: String) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

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
                val pasteBtn = findNodeDirectByText("Paste")
                pasteBtn?.let { stableClick(it) }
            }
        }
    }

    // ==================== NODE SEARCH ====================
    fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        for (label in labels) {
            root.findAccessibilityNodeInfosByText(label).firstOrNull {
                it.text?.toString()?.trim().equals(label, ignoreCase = true) && it.isVisibleToUser
            }?.let { return it }
        }
        return null
    }

    private fun findNodeDirect(viewId: String): AccessibilityNodeInfo? {
        return withRoot { root ->
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }
        }
    }

    private fun findNodeDirectByText(text: String): AccessibilityNodeInfo? {
        return withRoot { root ->
            root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isVisibleToUser }
        }
    }

    private fun findClassNodeElite(trainNode: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        for (i in 0 until trainNode.childCount) {
            val child = trainNode.getChild(i) ?: continue
            val text = child.text?.toString()?.uppercase() ?: ""
            if (text.contains(className)) return child
        }
        return null
    }

    // ==================== SMART WEIGHT ====================
    private fun getSmartWeight(text: String, className: String): Int {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("available") || lowerText.contains("avail") -> 0
            lowerText.contains("rac") -> -1
            lowerText.contains("wl") -> extractWaitListNumber(lowerText)
            else -> 999
        }
    }

    private fun extractWaitListNumber(text: String): Int {
        var num = 0
        var found = false
        for (c in text) {
            if (c in '0'..'9') {
                num = num * 10 + (c - '0')
                found = true
            } else if (found) break
        }
        return if (found) num else 99
    }

    private suspend fun humanDelay() = delay(Random.nextLong(8, 18))
    private suspend fun adaptiveDelay() = delay(Random.nextLong(5, 15))

    // ==================== UI STABILIZER ====================
    private suspend fun ensureListIsReady() {
        performGlobalAction(GLOBAL_ACTION_SCROLL_FORWARD)
        delay(20)
        performGlobalAction(GLOBAL_ACTION_SCROLL_BACKWARD)
        delay(20)
    }

    // ==================== NODE CACHE ====================
    private suspend fun buildLocalNodeCache(root: AccessibilityNodeInfo): Map<String, AccessibilityNodeInfo> {
        val cache = mutableMapOf<String, AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var depth = 0
        while (queue.isNotEmpty() && depth < 15) {
            val node = queue.removeFirst()
            depth++

            val text = node.text?.toString() ?: ""
            val trainNoMatch = Regex("\\d{5}").find(text)
            if (trainNoMatch != null) {
                cache[trainNoMatch.value] = node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    if (depth < 15) queue.add(child)
                }
            }
        }
        return cache
    }

    // ==================== ELITE STRIKE ====================
    private suspend fun executeEliteStrike(
        trainNode: AccessibilityNodeInfo,
        trainNo: String,
        className: String
    ): Boolean {
        val classNode = findClassNodeElite(trainNode, className) ?: return false

        try {
            val weight = getSmartWeight(classNode.text?.toString() ?: "", className)
            TrainPriorityManager.updateAvailability(trainNo, className, weight)

            if (weight > 20) return false

            if (!AttackLock.tryLock(trainNo, className)) return false

            delay(12)

            val bookBtn = findClassNodeElite(classNode, "BOOK NOW")
                ?: findClassNodeElite(classNode, "अभी बुक करें")

            return try {
                if (bookBtn != null && bookBtn.isVisibleToUser) {
                    stableClick(bookBtn)
                } else {
                    AttackLock.reset()
                    false
                }
            } finally {
                bookBtn?.recycle()
            }

        } finally {
            classNode.recycle()
        }
    }

    // ==================== SMART ATTACK ====================
    private suspend fun smartAttackWithLock(isAc: Boolean): Boolean = coroutineScope {
        ensureListIsReady()

        currentEngineJob?.cancel()
        currentEngineJob = coroutineContext[Job]

        val root = rootInActiveWindow ?: return@coroutineScope false

        try {
            val cache = buildLocalNodeCache(root)
            val snapshot = TrainPriorityManager.getAvailabilitySnapshot()
            val targets = TrainPriorityManager.getFullAttackPlan(isAc, snapshot)

            if (targets.isEmpty()) return@coroutineScope false

            val winnerSignal = CompletableDeferred<Boolean>()
            val attackJobs = mutableListOf<Job>()

            for (train in targets) {
                if (AttackLock.isLocked() || winnerSignal.isCompleted) break

                val bestClass = TrainPriorityManager
                    .getSmartClassOrder(train, isAc, snapshot)
                    .firstOrNull() ?: continue

                val job = launch(Dispatchers.Default) {
                    if (AttackLock.isLocked() || winnerSignal.isCompleted) return@launch

                    val trainNode = cache[train.trainNumber] ?: return@launch

                    if (executeEliteStrike(trainNode, train.trainNumber, bestClass)) {
                        if (winnerSignal.complete(true)) {
                            attackJobs.forEach { it.cancel() }
                        }
                    }
                }
                attackJobs.add(job)
            }

            return@coroutineScope withTimeoutOrNull(350L) {
                winnerSignal.await()
            } ?: false

        } finally {
            root.recycle()
        }
    }

    // ==================== FAST FORM FILL ====================
    private suspend fun fillPassengerFormUltraFast() = withRoot { root ->
        val task = activeTask ?: return@withRoot
        val passengers = task.passengers.take(4)

        for ((index, passenger) in passengers.withIndex()) {
            if (index > 0) {
                val addBtn = findNodeFast(root, listOf("Add Passenger"), IRCTC.ADD_PASSENGER_BTN)
                addBtn?.let { stableClick(it) }
                adaptiveDelay()
            }

            val nameField = root.findAccessibilityNodeInfosByViewId(IRCTC.NAME_INPUT).firstOrNull { it.isVisibleToUser }
            val ageField = root.findAccessibilityNodeInfosByViewId(IRCTC.AGE_INPUT).firstOrNull { it.isVisibleToUser }

            nameField?.let { setTextFast(it, passenger.name) }
            ageField?.let { setTextFast(it, passenger.age) }

            if (passenger.gender.isNotBlank()) {
                val spinner = root.findAccessibilityNodeInfosByViewId(IRCTC.GENDER_SPINNER).firstOrNull { it.isVisibleToUser }
                spinner?.let {
                    stableClick(it)
                    delay(10)
                    val option = root.findAccessibilityNodeInfosByText(passenger.gender).firstOrNull { it.isVisibleToUser }
                    option?.let { stableClick(it) }
                }
            }
        }

        val insuranceBtn = if (task.insurance) {
            root.findAccessibilityNodeInfosByViewId(IRCTC.INSURANCE_YES).firstOrNull()
        } else {
            root.findAccessibilityNodeInfosByViewId(IRCTC.INSURANCE_NO).firstOrNull()
        }
        insuranceBtn?.let { stableClick(it) }

        val proceedBtn = root.findAccessibilityNodeInfosByViewId(IRCTC.PROCEED_BTN).firstOrNull()
        proceedBtn?.let {
            stableClick(it)
            isReviewClicked = true
            transitionTo(EngineState.CAPTCHA)
            Log.d(TAG, "[${getCurrentTimestamp()}] 📋 Proceed to Review")
        }
    }

    // ==================== CAPTCHA HANDLING ====================
    private suspend fun handleCaptcha() {
        val captchaImage = findNodeDirect(IRCTC.CAPTCHA_IMAGE)
        val captchaInput = findNodeDirect(IRCTC.CAPTCHA_INPUT)

        if (captchaImage != null && captchaInput != null && activeTask?.captchaAutofill == true) {
            if (captchaInput.text.isNullOrBlank()) {
                Log.d(TAG, "[${getCurrentTimestamp()}] 🔐 Solving Captcha...")
                CaptchaSolver.executeBypass(this, captchaImage, captchaInput)
                transitionTo(EngineState.PAYMENT)
            }
        }
    }

    private suspend fun handlePaymentPage(): Boolean {
        val paymentCards = findNodeDirect(IRCTC.PAYMENT_CARDS)
        return paymentCards != null
    }

    private suspend fun selectPaymentUltraFast() {
        Log.d(TAG, "[${getCurrentTimestamp()}] 💳 Payment Selection")

        var paymentNode = findNodeDirect(IRCTC.PAYMENT_UPI_APPS)
        if (paymentNode != null) {
            stableClick(paymentNode)
            adaptiveDelay()
            val upiApps = listOf("Google Pay", "PhonePe", "Paytm", "Amazon Pay")
            for (app in upiApps) {
                val appNode = findNodeDirectByText(app)
                if (appNode != null) {
                    stableClick(appNode)
                    break
                }
            }
            return
        }

        paymentNode = findNodeDirect(IRCTC.PAYMENT_BHIM_UPI)
        if (paymentNode != null) {
            stableClick(paymentNode)
            adaptiveDelay()
            return
        }

        paymentNode = findNodeDirect(IRCTC.PAYMENT_CARDS)
        paymentNode?.let { stableClick(it) }

        humanDelay()
        val proceedBtn = findNodeDirect(IRCTC.PROCEED_BTN)
        proceedBtn?.let { stableClick(it) }
    }

    private suspend fun handleFinalPay(): Boolean {
        val payBtn = findNodeDirect(IRCTC.PROCEED_BTN)
        if (payBtn != null && payBtn.isClickable) {
            val text = payBtn.text?.toString()?.lowercase() ?: ""
            if ((text.contains("pay") && text.contains("₹")) ||
                text.contains("proceed") ||
                text.contains("भुगतान")) {
                stableClick(payBtn)
                Log.d(TAG, "[${getCurrentTimestamp()}] ✅ BOOKING SUCCESSFUL!")
                updateNotification("✅ BOOKING SUCCESSFUL!")
                transitionTo(EngineState.DONE)
                isArmed = false
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
                return true
            }
        }
        return false
    }

    private fun launchIrctcApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(IRCTC.PKG)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d(TAG, "[${getCurrentTimestamp()}] 📱 IRCTC App launched automatically")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to launch IRCTC: ${e.message}")
        }
    }

    private fun triggerPreciseRefresh() {
        withRoot { root ->
            val refreshBtn = root.findAccessibilityNodeInfosByViewId(IRCTC.SEARCH_BTN)
                .firstOrNull { it.isVisibleToUser && it.isClickable }

            if (refreshBtn != null) {
                stableClick(refreshBtn)
                Log.d(TAG, "[${getCurrentTimestamp()}] ✅ Refresh Clicked at PRECISE time!")
            } else {
                Log.e(TAG, "[${getCurrentTimestamp()}] ❌ Refresh button not found!")
            }
        }
    }

    // ==================== SERVICE LIFECYCLE ====================
    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        createNotificationChannel()
        startForeground(1, buildNotification("⚡ VMAX ELITE"))
        Log.d(TAG, "[${getCurrentTimestamp()}] ✅ PRODUCTION READY SNIPER ACTIVE")
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

                serviceScope.launch {
                    transitionTo(EngineState.PRE_GAME)
                    launchIrctcApp()
                    delay(2000)

                    TimeSyncManager.syncTime()
                    Log.d(TAG, "[${getCurrentTimestamp()}] ⏰ Time synced! Offset: ${TimeSyncManager.getOffset()}ms")

                    val targetHour = if (activeTask!!.triggerTime.startsWith("10")) 10 else 11
                    val advanceMs = activeTask!!.msAdvance.toLong().coerceIn(120, 200)

                    Log.d(TAG, "[${getCurrentTimestamp()}] 🎯 Scheduling fire at $targetHour:00:00 with ${advanceMs}ms advance")

                    TimeSniper.scheduleFire(targetHour, advanceMs) {
                        isArmed = true

                        serviceScope.launch {
                            if (!hasRefreshed) {
                                transitionTo(EngineState.REFRESH)
                                Log.d(TAG, "[${getCurrentTimestamp()}] 🔥 EXECUTING PRECISE REFRESH!")
                                triggerPreciseRefresh()
                                hasRefreshed = true
                                transitionTo(EngineState.ATTACK)
                                Log.d(TAG, "[${getCurrentTimestamp()}] 🎯 Sniper Armed! Attack mode engaged")
                            }
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
        lastEventTime = 0
        lastWindowHash = 0
        AttackLock.reset()
        TrainPriorityManager.clearCache()
        currentEngineJob?.cancel()
        currentEngineJob = null
        runBlocking { transitionTo(EngineState.IDLE) }
        Log.d(TAG, "🔄 Engine Reset")
    }

    // ==================== MAIN EVENT HANDLER ====================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isArmed) return
        if (System.currentTimeMillis() - lastEventTime < 40) return
        lastEventTime = System.currentTimeMillis()

        if (event.packageName?.toString() != IRCTC.PKG) return
        if (!isProcessing.compareAndSet(false, true)) return

        serviceScope.launch {
            try {
                val targetHour = if (activeTask?.triggerTime?.startsWith("10") == true) 10 else 11

                if (handleFinalPay()) return@launch

                if (isState(EngineState.PAYMENT)) {
                    if (handlePaymentPage()) {
                        selectPaymentUltraFast()
                    }
                    return@launch
                }

                if (isState(EngineState.ATTACK) && !isReviewClicked) {
                    val isAc = targetHour == 10
                    Log.d(TAG, "[${getCurrentTimestamp()}] 🎯 Starting ${if (isAc) "AC" else "Sleeper"} Attack")

                    val success = smartAttackWithLock(isAc = isAc)

                    if (success) {
                        transitionTo(EngineState.FORM)
                        fillPassengerFormUltraFast()
                    } else {
                        Log.w(TAG, "[${getCurrentTimestamp()}] ⚠️ No hit, retrying...")
                        delay(50)
                        AttackLock.reset()
                        smartAttackWithLock(isAc = isAc)
                        fillPassengerFormUltraFast()
                    }
                    return@launch
                }

                if (isReviewClicked && isState(EngineState.CAPTCHA)) {
                    handleCaptcha()
                    return@launch
                }

                if (isReviewClicked && !isState(EngineState.CAPTCHA) && !isState(EngineState.PAYMENT)) {
                    transitionTo(EngineState.CAPTCHA)
                }

            } catch (e: Exception) {
                Log.e(TAG, "[${getCurrentTimestamp()}] ❌ Event Error: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }
    }

    // ==================== NOTIFICATIONS ====================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 VMAX ELITE")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun updateNotification(message: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(1, buildNotification(message))
            Log.d(TAG, message)
        } catch (e: Exception) {}
    }

    override fun onInterrupt() { resetEngineState() }
    override fun onDestroy() { serviceScope.cancel(); super.onDestroy() }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabled.contains(expected.flattenToString())
}
