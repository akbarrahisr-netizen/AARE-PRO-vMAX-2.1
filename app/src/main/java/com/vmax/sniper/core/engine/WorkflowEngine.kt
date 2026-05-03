package com.vmax.sniper.core.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.vmax.sniper.core.model.*
import com.vmax.sniper.core.network.TimeSniper
import com.vmax.sniper.core.network.TimeSyncManager // ✅ FIX: Missing Import
import com.vmax.sniper.core.engine.CaptchaSolver // ✅ FIX: Missing Import
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.random.Random

class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Sniper"
        const val ACTION_START_SNIPER = "com.vmax.sniper.START_SNIPER"
        const val EXTRA_TASK = "extra_task"
        
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
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_EWALLET = "$PKG:id/radio_ewallet"
            const val PAYMENT_UPI_ID = "$PKG:id/radio_upi_id"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            const val UPI_ID_INPUT = "$PKG:id/et_upi_id" // ✅ FIX: Properly Defined
            const val AUTO_UPGRADE_CHECK = "$PKG:id/checkbox_auto_upgrade"
            const val CONFIRM_BERTH_CHECK = "$PKG:id/checkbox_confirm_berth"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
            const val BOOKING_OPT_SPINNER = "$PKG:id/spinner_booking_option" // ✅ FIX: Properly Defined
            const val COACH_PREF_INPUT = "$PKG:id/et_coach_preference"
            const val MOBILE_INPUT = "$PKG:id/et_mobile_number"
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var isProcessing = false
    private var currentPassengerIndex = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        createNotificationChannel()
        startForeground(1, buildNotification("🎯 Sniper Ready"))
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
                isArmed = false
                currentPassengerIndex = 0
                val targetHour = if (activeTask!!.triggerTime.startsWith("10")) 10 else 11
                TimeSniper.scheduleFire(targetHour, activeTask!!.msAdvance.toLong()) {
                    isArmed = true
                    updateNotification("🔥 SNIPER ACTIVE!")
                }
            }
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isArmed || isProcessing) return
        val root = rootInActiveWindow ?: return

        try {
            if (root.packageName == IRCTC.PKG) {
                findNodeByLabelsOrId(root, listOf("OK", "YES"), "")?.let { humanClickAdvanced(it); return }

                if (findNodeByLabelsOrId(root, listOf("Name"), IRCTC.NAME_INPUT) != null) {
                    isProcessing = true
                    serviceScope.launch {
                        try { fillAllDetailsAndProceed() } 
                        finally { isProcessing = false }
                    }
                    return
                }
                
                if (root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS).isNotEmpty()) {
                    isProcessing = true
                    serviceScope.launch {
                        try { selectPaymentMethod(root) } 
                        finally { isProcessing = false }
                    }
                    return
                }
            }
        } catch (e: Exception) {
            isProcessing = false
        } finally { root.recycle() }
    }

    private suspend fun fillAllDetailsAndProceed() {
        val task = activeTask ?: return
        var currentRoot = rootInActiveWindow ?: return
        
        for (i in currentPassengerIndex until min(currentRoot.findAccessibilityNodeInfosByViewId(IRCTC.NAME_INPUT).size, task.passengers.size)) {
            val passenger = task.passengers[i]
            if (i > 0) {
                findNodeByLabelsOrId(currentRoot, listOf("Add New"), IRCTC.ADD_PASSENGER_BTN)?.let {
                    humanClickAdvanced(it); delay(400); currentRoot = rootInActiveWindow ?: return
                }
            }
            findNodeByLabelsOrId(currentRoot, emptyList(), IRCTC.NAME_INPUT)?.let { setText(it, passenger.name) }
            findNodeByLabelsOrId(currentRoot, emptyList(), IRCTC.AGE_INPUT)?.let { setText(it, passenger.age) }
            currentPassengerIndex = i + 1
        }

        awaitAdvanceOptionsSetup()
        
        findNodeByLabelsOrId(currentRoot, listOf("Review Journey Details", "Continue"), IRCTC.PROCEED_BTN)?.let { 
            humanClickAdvanced(it)
        }
    }

    private suspend fun awaitAdvanceOptionsSetup() {
        val task = activeTask ?: return
        val root = rootInActiveWindow ?: return
        
        if (task.autoUpgradation) {
            findNodeByLabelsOrId(root, emptyList(), IRCTC.AUTO_UPGRADE_CHECK)?.let { if(!it.isChecked) humanClickAdvanced(it) }
        }
        
        if (task.bookingOption.value > 0) {
            selectSpinnerOption(root, IRCTC.BOOKING_OPT_SPINNER, task.bookingOption.display)
        }
    }

    private suspend fun selectPaymentMethod(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        when (task.payment.category) {
            PaymentCategory.BHIM_UPI -> findNodeByLabelsOrId(root, emptyList(), IRCTC.PAYMENT_BHIM_UPI)?.let { humanClickAdvanced(it) }
            PaymentCategory.UPI_ID -> {
                findNodeByLabelsOrId(root, emptyList(), IRCTC.PAYMENT_UPI_ID)?.let { 
                    humanClickAdvanced(it)
                    delay(300)
                    findNodeByLabelsOrId(root, emptyList(), IRCTC.UPI_ID_INPUT)?.let { input -> setText(input, task.payment.upiId) }
                }
            }
            else -> {} 
        }
        findNodeByLabelsOrId(root, listOf("Continue"), IRCTC.PROCEED_BTN)?.let { humanClickAdvanced(it) }
    }

    private fun humanClickAdvanced(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
        dispatchGesture(gesture, null, null)
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private suspend fun selectSpinnerOption(root: AccessibilityNodeInfo, spinnerId: String, optionText: String) {
        findNodeByLabelsOrId(root, emptyList(), spinnerId)?.let { spinner ->
            humanClickAdvanced(spinner); delay(300)
            findNodeByLabelsOrId(rootInActiveWindow ?: return, listOf(optionText), "")?.let { humanClickAdvanced(it) }
        }
    }

    private fun findNodeByLabelsOrId(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 VMAX Sniper Pro")
        .setContentText(content)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(content))
    }

    override fun onInterrupt() {}
}
