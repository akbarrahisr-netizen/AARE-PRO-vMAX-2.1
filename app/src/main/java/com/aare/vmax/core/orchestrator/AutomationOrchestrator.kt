package com.aare.vmax.core.orchestrator

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aare.vmax.core.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference

// ✅ पैसेंजर डेटा मॉडल
data class PassengerData(val name: String, val age: String, val gender: String)

class AutomationOrchestrator(
    private val engine: WorkflowEngine,
    private val scope: CoroutineScope,
    private val onStatusChanged: ((String) -> Unit)? = null
) {
    // ✅ एरर फिक्स: MutableSharedFlow को सही तरीके से डिफाइन किया
    private val _eventFlow = MutableSharedFlow<AccessibilityEvent>(extraBufferCapacity = 10)
    val eventFlow = _eventFlow.asSharedFlow()

    private var isRunning = false
    private val lock = Mutex()
    private var ctxRef: WeakReference<Context>? = null

    fun setContext(context: Context) {
        ctxRef = WeakReference(context.applicationContext)
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isRunning) return
        scope.launch {
            try { engine.onScreenChanged() } catch (e: Exception) { }
        }
    }

    suspend fun start(): Boolean = lock.withLock {
        if (isRunning) return@withLock true
        val ctx = ctxRef?.get() ?: return@withLock false
        val prefs = ctx.getSharedPreferences("VMaxProfile", Context.MODE_PRIVATE)

        val trainNum = prefs.getString("train", "") ?: ""
        val bClass = prefs.getString("class", "SL") ?: "SL"
        val passengers = mutableListOf<PassengerData>()

        for (i in 0 until 4) {
            val n = prefs.getString("name_$i", "") ?: ""
            val a = prefs.getString("age_$i", "") ?: ""
            val g = prefs.getString("gender_$i", "Male") ?: "Male"
            if (n.isNotBlank()) passengers.add(PassengerData(n, a, g))
        }

        if (passengers.isEmpty()) {
            update("❌ No Passengers")
            return@withLock false
        }

        val steps = buildSteps(trainNum, bClass, passengers)
        engine.loadRecording(steps)
        isRunning = true
        update("🚀 Running...")
        engine.onScreenChanged()
        return@withLock true
    }

    suspend fun reset() = lock.withLock {
        try { engine.reset() } catch (e: Exception) { }
        isRunning = false
        update("⏹ Stopped")
    }

    // ✅ एरर फिक्स: सर्विस फाइल को 'shutdown' फंक्शन मिल जाएगा
    fun shutdown() {
        isRunning = false
        ctxRef?.clear()
    }

    private fun update(msg: String) {
        onStatusChanged?.invoke(msg)
    }

    private fun buildSteps(train: String, bClass: String, passengers: List<PassengerData>): List<RecordedStep> {
        val steps = mutableListOf<RecordedStep>()

        // ट्रेन के आधार पर सही क्लास पर क्लिक
        steps.add(RecordedStep(
            id = "click_class",
            actionType = ActionType.CLICK,
            criteria = bClass,
            anchorText = train,
            maxRetries = 10,
            postActionDelayMs = 400
        ))

        steps.add(RecordedStep(id = "click_details", actionType = ActionType.CLICK, criteria = "PASSENGER DETAILS", postActionDelayMs = 400))

        passengers.forEachIndexed { i, p ->
            steps.add(RecordedStep(id = "add_$i", actionType = ActionType.CLICK, criteria = "+ Add New", postActionDelayMs = 300))
            steps.add(RecordedStep(id = "name_$i", actionType = ActionType.INPUT_TEXT, criteria = "Passenger Name", inputText = p.name))
            steps.add(RecordedStep(id = "age_$i", actionType = ActionType.INPUT_TEXT, criteria = "Age", inputText = p.age))
            steps.add(RecordedStep(id = "gender_$i", actionType = ActionType.CLICK, criteria = p.gender))
            steps.add(RecordedStep(id = "submit_$i", actionType = ActionType.CLICK, criteria = "Add Passenger", postActionDelayMs = 300))
        }

        steps.add(RecordedStep(id = "review", actionType = ActionType.CLICK, criteria = "Review Journey Details", postActionDelayMs = 600))
        return steps
    }
}
