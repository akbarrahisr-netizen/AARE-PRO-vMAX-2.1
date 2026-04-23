package com.aare.vmax.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.aare.vmax.R
import com.aare.vmax.core.orchestrator.AutomationOrchestrator
import com.aare.vmax.core.orchestrator.WorkflowEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingPanelManager(
    private val context: Context,
    private val engine: WorkflowEngine,
    private val orchestrator: AutomationOrchestrator,
    private val scope: CoroutineScope
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private var tvStatus: TextView? = null
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    fun show() {
        if (rootView != null) return

        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        rootView = LayoutInflater.from(context).inflate(R.layout.floating_panel, null)
        setupViews()
        setupDragListeners()

        windowManager.addView(rootView, layoutParams)
        updateStatus("Ready")
    }

    private fun setupViews() {
        val btnStart = rootView?.findViewById<Button>(R.id.btnStart)
        val btnStop = rootView?.findViewById<Button>(R.id.btnStop)
        tvStatus = rootView?.findViewById(R.id.tvStatus)

        btnStart?.setOnClickListener {
            updateStatus("Starting...")
            scope.launch {
                try {
                    orchestrator.setContext(context)
                    // ✅ एरर फिक्स: अब कोई पैरामीटर नहीं भेज रहे
                    val success = orchestrator.start() 
                    withContext(Dispatchers.Main) {
                        updateStatus(if (success) "Running ✓" else "Failed ✗")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { updateStatus("Error ✗") }
                }
            }
        }

        btnStop?.setOnClickListener {
            scope.launch {
                orchestrator.reset()
                withContext(Dispatchers.Main) { updateStatus("Stopped") }
            }
        }
    }

    private fun setupDragListeners() {
        rootView?.setOnTouchListener { v, event ->
            val params = rootView?.layoutParams as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(rootView, params)
                    true
                }
                MotionEvent.ACTION_UP -> !isDragging
                else -> false
            }
        }
    }

    private fun updateStatus(status: String) {
        tvStatus?.text = "Status: $status"
    }

    fun remove() {
        rootView?.let {
            windowManager.removeView(it)
            rootView = null
        }
    }
}
