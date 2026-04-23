package com.aare.vmax.core.orchestrator

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aare.vmax.core.models.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ensureActive
import java.lang.ref.WeakReference

// ✅ Immutable passenger data with deep validation
data class PassengerData private constructor(
    val name: String,
    val age: String,
    val gender: String
) {
    companion object {
        fun create(name: String, age: String, gender: String): PassengerData? {
            val trimmedName = name.trim().take(50)
            val trimmedAge = age.trim()
            val trimmedGender = gender.trim().ifBlank { "Male" }
            val ageInt = trimmedAge.toIntOrNull()
            
            return if (trimmedName.isNotBlank() && 
                      ageInt != null && 
                      ageInt in 1..120 && 
                      trimmedGender.isNotBlank()) {
                PassengerData(trimmedName, trimmedAge, trimmedGender)
            } else {
                null
            }
        }
    }
}

class AutomationOrchestrator(
    private val engine: WorkflowEngine,
    private val scope: CoroutineScope,
    private val onStatusChanged: ((String) -> Unit)? = null
) {

    companion object {
        private const val TAG = "VMAX_Orchestrator"
        private const val MAX_PASS = 4
        private const val CONTEXT_TIMEOUT_MS = 5000L
    }

    private val _eventFlow = MutableSharedFlow<AccessibilityEvent>(extraBufferCapacity = 10)
    val eventFlow = _eventFlow.asSharedFlow()

    @Volatile
    private var state: OrchestratorState = OrchestratorState.IDLE
    private val stateLock = Mutex()
    private var ctxRef: WeakReference<Context>? = null
    private var activeJob: kotlinx.coroutines.Job? = null

    enum class OrchestratorState { IDLE, STARTING, RUNNING, STOPPING, ERROR }

    private suspend fun getContext(): Context? {
        return withTimeoutOrNull(CONTEXT_TIMEOUT_MS) {
            ctxRef?.get()?.applicationContext
        }
    }

    fun setContext(context: Context) {
        ctxRef = WeakReference(context.applicationContext)
        Log.d(TAG, "Context set: ${context.applicationContext?.javaClass?.simpleName}")
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || state != OrchestratorState.RUNNING) return
        
        scope.launch {
            try {
                this.coroutineContext.ensureActive()
                engine.onScreenChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Event handling failed: ${e.message}")
            }
        }
    }

    suspend fun start(): Boolean = stateLock.withLock {
        if (state != OrchestratorState.IDLE) return@withLock false

        val ctx = getContext() ?: run {
            update("❌ Context unavailable", OrchestratorState.ERROR)
            return@withLock false
        }

        try {
            state = OrchestratorState.STARTING
            update("🚀 Starting...", OrchestratorState.STARTING)

            val passengers = loadPassengersSafely(ctx)
            
            if (passengers.isEmpty()) {
                update("❌ No valid passengers", OrchestratorState.IDLE)
                return@withLock false
            }

            update("⚡ Preparing ${passengers.size} passenger(s)...", OrchestratorState.STARTING)
            val steps = buildSteps(passengers)
            
            engine.loadRecording(steps)
            
            state = OrchestratorState.RUNNING
            update("🎯 Injecting data...", OrchestratorState.RUNNING)
            
            activeJob = scope.launch {
                try {
                    this.coroutineContext.ensureActive()
                    engine.onScreenChanged()
                    update("✅ Running (${passengers.size})", OrchestratorState.RUNNING)
                } catch (e: CancellationException) {
                    update("⏹ Cancelled", OrchestratorState.IDLE)
                } catch (e: Exception) {
                    update("❌ Exec error", OrchestratorState.ERROR)
                    cleanupInternal()
                }
            }
            
            return@withLock true

        } catch (e: Exception) {
            update("❌ Error", OrchestratorState.ERROR)
            cleanupInternal()
            return@withLock false
        }
    }

    private fun loadPassengersSafely(ctx: Context): List<PassengerData> {
        return try {
            val prefs = ctx.getSharedPreferences("VMaxProfile", Context.MODE_PRIVATE)
            val result = mutableListOf<PassengerData>()
            for (i in 0 until MAX_PASS) {
                val name = prefs.getString("name_$i", "").orEmpty()
                val age = prefs.getString("age_$i", "").orEmpty()
                val gender = prefs.getString("gender_$i", "Male").orEmpty()
                PassengerData.create(name, age, gender)?.let { result.add(it) }
            }
            result
        } catch (e: Exception) { emptyList() }
    }

    suspend fun reset(): Boolean = stateLock.withLock {
        if (state == OrchestratorState.IDLE) return@withLock true
        state = OrchestratorState.STOPPING
        update("🛑 Stopping...", OrchestratorState.STOPPING)
        activeJob?.cancel()
        activeJob = null
        try { engine.reset() } catch (e: Exception) { }
        update("⏹ Stopped", OrchestratorState.IDLE)
        return@withLock true
    }

    fun shutdown() {
        activeJob?.cancel()
        activeJob = null
        ctxRef?.clear()
        ctxRef = null
        state = OrchestratorState.IDLE
    }

    private fun cleanupInternal() {
        activeJob?.cancel()
        activeJob = null
        state = OrchestratorState.IDLE
    }

    fun isRunning(): Boolean = state == OrchestratorState.RUNNING

    private fun update(msg: String, newState: OrchestratorState? = null) {
        newState?.let { state = it }
        Log.d(TAG, msg)
        try { onStatusChanged?.invoke(msg) } catch (e: Exception) { }
    }

    private fun getGenderText(gender: String): String {
        return when {
            gender.equals("Female", true) || gender.equals("F", true) -> "Female"
            gender.equals("Transgender", true) || gender.equals("Trans", true) -> "Transgender"
            else -> "Male"
        }.take(20)
    }

    private fun buildSteps(passengers: List<PassengerData>): List<RecordedStep> {
        val steps = mutableListOf<RecordedStep>()

        steps.add(RecordedStep(
            id = "open_form",
            actionType = ActionType.CLICK,
            criteria = "PASSENGER DETAILS",
            verificationStrategy = VerificationStrategy.None,
            maxRetries = 10,
            postActionDelayMs = 400
        ))

        passengers.forEachIndexed { index, passenger ->
            if (index > 0) {
                steps.add(RecordedStep(
                    id = "add_$index",
                    actionType = ActionType.CLICK,
                    criteria = "+ Add New",
                    verificationStrategy = VerificationStrategy.None,
                    maxRetries = 5,
                    postActionDelayMs = 300
                ))
            }

            steps.add(RecordedStep(
                id = "name_$index",
                actionType = ActionType.INPUT_TEXT,
                criteria = "Passenger Name",
                inputText = passenger.name,
                verificationStrategy = VerificationStrategy.None,
                postActionDelayMs = 150
            ))

            steps.add(RecordedStep(
                id = "age_$index",
                actionType = ActionType.INPUT_TEXT,
                criteria = "Age",
                inputText = passenger.age,
                verificationStrategy = VerificationStrategy.None,
                postActionDelayMs = 150
            ))

            steps.add(RecordedStep(
                id = "gender_$index",
                actionType = ActionType.CLICK,
                criteria = getGenderText(passenger.gender),
                verificationStrategy = VerificationStrategy.None,
                maxRetries = 5,
                postActionDelayMs = 150
            ))

            steps.add(RecordedStep(
                id = "submit_$index",
                actionType = ActionType.CLICK,
                criteria = "Add Passenger",
                verificationStrategy = VerificationStrategy.None,
                maxRetries = 5,
                postActionDelayMs = 300
            ))
        }

        steps.add(RecordedStep(
            id = "review",
            actionType = ActionType.CLICK,
            criteria = "Review Journey Details",
            verificationStrategy = VerificationStrategy.None,
            maxRetries = 10,
            postActionDelayMs = 500
        ))

        return steps
    }
}
