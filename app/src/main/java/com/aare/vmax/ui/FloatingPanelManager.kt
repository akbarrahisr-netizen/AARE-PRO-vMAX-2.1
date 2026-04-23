package com.aare.vmax.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
    
    companion object {
        private const val TAG = "FloatingPanel"
    }
    
    fun show() {
        if (rootView != null) {
            Log.w(TAG, "Panel already showing")
            return
        }
        
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }
        
        try {
            createAndAddPanel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show panel", e)
        }
    }
    
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    private fun requestOverlayPermission() {
        Log.d(TAG, "Requesting overlay permission")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    private fun createAndAddPanel() {
        // 📦 Main Container
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(12))
            
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1E1E"))
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#555555"))
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dpToPx(8).toFloat()
            }
        }
        
        // 🔘 Drag Handle
        val dragHandle = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(4)
            ).apply {
                bottomMargin = dpToPx(8)
            }
            setBackgroundColor(Color.parseColor("#666666"))
            alpha = 0.6f
        }
        container.addView(dragHandle)
        
        // 📊 Status TextView
        tvStatus = TextView(context).apply {
            text = "Status: Ready"
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }
        container.addView(tvStatus)
        
        // 🔘 Buttons Container
        val buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // ▶ Start Button
        val btnStart = Button(context).apply {
            text = "▶ Start"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(40), 1f).apply {
                rightMargin = dpToPx(4)
            }
            setOnClickListener { onStartClick() }
        }
        buttonsContainer.addView(btnStart)
        
        // ■ Stop Button
        val btnStop = Button(context).apply {
            text = "■ Stop"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#C62828"))
            
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(40), 1f).apply {
                leftMargin = dpToPx(4)
            }
            setOnClickListener { onStopClick() }
        }
        buttonsContainer.addView(btnStop)
        
        container.addView(buttonsContainer)
        rootView = container
        
        setupDragListeners()
        
        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            
            x = dpToPx(8)
            y = dpToPx(200)
        }
        
        windowManager.addView(rootView, layoutParams)
        updateStatus("Ready")
        Log.d(TAG, "Panel shown successfully")
    }
    
    private fun setupDragListeners() {
        rootView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    val params = rootView?.layoutParams as? WindowManager.LayoutParams
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        val params = rootView?.layoutParams as? WindowManager.LayoutParams
                        params?.x = initialX + deltaX
                        params?.y = initialY + deltaY
                        params?.let { windowManager.updateViewLayout(rootView, it) }
                    }
                    true
                }
                
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    }
                    true
                }
                
                else -> false
            }
        }
    }
    
    private fun onStartClick() {
        updateStatus("Starting...")
        
        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    orchestrator.setContext(context)
                }
                
                val success = withContext(Dispatchers.IO) {
                    orchestrator.start()
                }
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        updateStatus("Running ✓")
                    } else {
                        updateStatus("Failed ✗")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Error ✗")
                }
            }
        }
    }
    
    private fun onStopClick() {
        updateStatus("Stopping...")
        
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    orchestrator.reset()
                }
                withContext(Dispatchers.Main) {
                    updateStatus("Stopped")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Stop Error")
                }
            }
        }
    }
    
    fun updateStatus(status: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            tvStatus?.text = "Status: $status"
        }
    }
    
    fun remove() {
        try {
            rootView?.let {
                windowManager.removeView(it)
                rootView = null
                tvStatus = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing panel", e)
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
