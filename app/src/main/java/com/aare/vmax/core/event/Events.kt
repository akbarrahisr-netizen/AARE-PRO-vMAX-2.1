package com.aare.vmax.core.event

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

// 👇 Models imports (IMPORTANT FIX)
import com.aare.vmax.core.models.ScreenType
import com.aare.vmax.core.models.ActionType
import com.aare.vmax.core.models.AutomationError

// =========================================================
// ✅ EVENTS
// =========================================================
sealed class AutomationEvent {

    data class ScreenChanged(
        val screenType: ScreenType
    ) : AutomationEvent()

    data class NodeFound(
        val nodeId: String
    ) : AutomationEvent()

    data class ActionCompleted(
        val action: ActionType,
        val success: Boolean
    ) : AutomationEvent()

    data class ErrorOccurred(
        val error: AutomationError,
        val recoverable: Boolean
    ) : AutomationEvent()

    object WorkflowComplete : AutomationEvent()
    object UserInterventionRequired : AutomationEvent()
}

// =========================================================
// ✅ THREAD-SAFE EVENT BUS
// =========================================================
class AutomationEventBus {

    private val subscribers = CopyOnWriteArrayList<(AutomationEvent) -> Unit>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun subscribe(handler: (AutomationEvent) -> Unit) {
        subscribers.add(handler)
    }

    fun unsubscribe(handler: (AutomationEvent) -> Unit) {
        subscribers.remove(handler)
    }

    fun publish(event: AutomationEvent) {
        for (handler in subscribers) {
            scope.launch {
                try {
                    handler(event)
                } catch (e: Exception) {
                    Log.e("EventBus", "Handler crash: ${e.message}")
                }
            }
        }
    }
}
