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

    // 🚀 बॉट को चालू करने का मेन बटन
    fun start(targetPackage: String, keywords: List<String>, onStatus: (String) -> Unit) {
        if (running) return
        running = true
        
        engine.initialize(scope)
        onStatus("🟢 Orchestrator Started")
        
        scope.launch {
            try {
                // ✅ Safe Loop: कैंसल होने पर तुरंत रुकेगा
                while (isActive && running) {
                    
                    // इंजन से काम करवाओ
                    engine.processLatestEventsAsync(targetPackage, keywords) { fp ->
                        onStatus("🎯 Target Found")
                        
                        // ✅ चाइल्ड जॉब: क्लिक और वेरीफाई करो
                        launch {
                            val success = ActionVerifier.executeVerified(
                                service, fp, onStatus = onStatus
                            )
                            if (success) {
                                running = false // सक्सेस होने पर बॉट रोक दो ताकि आगे का फॉर्म भर सके
                            }
                        }
                    }
                    delay(50) // सिस्टम को साँस लेने का टाइम
                }
            } catch (e: CancellationException) {
                // ✅ जानबूझकर रोकने पर कोई क्रैश नहीं होगा
                onStatus("🛑 Bot Stopped Safely")
                throw e 
            } catch (e: Exception) {
                onStatus("❌ Error: ${e.message?.take(40)}")
            } finally {
                // ✅ कुछ भी हो जाए, आख़िर में मेमोरी क्लीनअप ज़रूर होगा
                running = false
                engine.shutdown()
            }
        }
    }

    // 🛑 बॉट को रोकने का बटन (Emergency Stop)
    fun stop() {
        running = false
        scope.cancel() // स्कोप कैंसल करते ही सारे काम तुरंत रुक जाएंगे
        engine.shutdown()
    }
}
