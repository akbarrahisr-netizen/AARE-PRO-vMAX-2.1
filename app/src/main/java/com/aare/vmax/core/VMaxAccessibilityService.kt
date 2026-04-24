package com.aare.vmax

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.aare.vmax.v2.engine.AccessEngineV2
import com.aare.vmax.v2.orchestrator.OrchestratorV2

class VMAXAccessibilityService : AccessibilityService() {

    private lateinit var engine: AccessEngineV2
    private lateinit var orchestrator: OrchestratorV2

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VMAX", "🟢 V2.1 Service Connected!")
        
        // 1. नए इंजन और कंट्रोल रूम (Orchestrator) को चालू करें
        engine = AccessEngineV2(this, this)
        orchestrator = OrchestratorV2(this, engine)
        
        // 🎯 टेस्ट के लिए बॉट को ऑटो-स्टार्ट कर रहे हैं (बाद में इसे ऐप के बटन से जोड़ेंगे)
        // यह 'cris.org.in.prs.ima' (IRCTC) में 'SL', '3A', या 'Refresh' ढूंढेगा
        val keywords = listOf("SL", "3A", "Refresh") 
        orchestrator.start("cris.org.in.prs.ima", keywords) { status ->
            Log.d("VMAX_STATUS", status)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 2. स्क्रीन की हर हलचल (Event) को सीधे नए इंजन में भेजें
        if (::engine.isInitialized) {
            engine.queueEvent(event)
        }
    }

    override fun onInterrupt() {
        // सर्विस रुकने पर
        Log.d("VMAX", "⚠️ Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        // 3. सर्विस बंद होने पर सब कुछ सुरक्षित तरीके से बंद करें (Memory Safe)
        if (::orchestrator.isInitialized) {
            orchestrator.stop()
        }
        Log.d("VMAX", "🛑 Service Destroyed")
    }
}
