package com.aare.vmax.v2.orchestrator

import android.accessibilityservice.AccessibilityService
import com.aare.vmax.v2.engine.AccessEngineV2
import com.aare.vmax.v2.engine.ActionVerifier
import com.aare.vmax.v2.model.UiFingerprint
import kotlinx.coroutines.*

class OrchestratorV2(
    private val service: AccessibilityService,
    private val engine: AccessEngineV2
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    @Volatile private var running = false

    // 🚀 बॉट चालू करने का बटन
    fun start(targetPackage: String, keywords: List<String>, onStatus: (String) -> Unit) {
        if (running) return
        running = true
        
        engine.initialize(scope)
        onStatus("🟢 Orchestrator Started")
        
        scope.launch {
            try {
                while (isActive && running) {
                    // ✅ explicit type specify किया है ताकि एरर न आए
                    engine.processLatestEventsAsync(targetPackage, keywords) { fp: UiFingerprint ->
                        onStatus("🎯 Target Found")
                        launch {
                            val success = ActionVerifier.executeVerified(
                                service, fp, 3, onStatus
                            )
                            if (success) {
                                running = false 
                            }
                        }
                    }
                    delay(50)
                }
            } catch (e: Exception) {
                onStatus("❌ Error in loop")
            } finally {
                running = false
                engine.shutdown()
            }
        }
    }

    // 🛑 इमरजेंसी स्टॉप
    fun stop() {
        running = false
        scope.cancel()
        engine.shutdown()
    }
}

