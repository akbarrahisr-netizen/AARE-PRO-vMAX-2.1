package com.aare.vmax.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.MainThread
import com.aare.vmax.R
import com.aare.vmax.core.orchestrator.AutomationOrchestrator
import com.aare.vmax.core.orchestrator.StrikeConfig
import com.aare.vmax.core.orchestrator.WorkflowEngine
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.hypot

// =========================================================
// CONFIG & STATE PERSISTENCE
// =========================================================

data class FloatingPanelConfig(
    val defaultTrainNumber: String = "12487",
    val defaultBookingClass: String = "SL",
    val initialX: Int = 0,
    val initialY: Int = 300,
    val friction: Float = 0.92f, // Inertia decay rate (0.9 = slippery, 0.8 = sticky)
    val minVelocity: Float = 50f // Minimum velocity to trigger fling (pixels/sec)
)

sealed class PanelState {
    object Idle : PanelState()
    object Running : PanelState()
    object Stopping : PanelState()
    object Error : PanelState()
}

// =========================================================
// ⚡ PURE PHYSICS UI MANAGER (V16)
// =========================================================

class FloatingPanelManager(
    private val context: Context,
    private val engine: WorkflowEngine,    private val orchestrator: AutomationOrchestrator,
    private val scope: CoroutineScope,
    private val config: FloatingPanelConfig = FloatingPanelConfig(),
    private val prefs: SharedPreferences = context.getSharedPreferences("vmax_panel_v16", Context.MODE_PRIVATE)
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val metrics = context.resources.displayMetrics

    // UI References
    private var view: View? = null
    private var startBtn: Button? = null
    private var stopBtn: Button? = null
    private var statusText: TextView? = null

    private var params: WindowManager.LayoutParams? = null

    // ⚡ Atomic State Machine
    private val stateAtomic = AtomicInteger(0)
    private val sessionCounter = AtomicInteger(0)
    private var job: Job? = null

    // 🧊 Physics Engine Variables
    private val choreographer = Choreographer.getInstance()
    private var isDragging = false
    private var isFlinging = false
    
    // Position & Velocity (Float for precision)
    private var currentX = 0f
    private var currentY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    
    // Kalman Filter State (For smooth touch tracking)
    private var kalmanX = 0f
    private var kalmanY = 0f
    private var kalmanVelX = 0f
    private var kalmanVelY = 0f
    
    // Timing
    private var lastFrameTimeNanos = 0L
    private var lastTouchTimeNanos = 0L

    // =====================================================
    // SHOW (With Restore Logic)
    // =====================================================
    @MainThread
    fun show() {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "❌ Overlay Permission Required!", Toast.LENGTH_LONG).show()            return
        }
        
        if (view?.isAttachedToWindow == true) return

        try {
            val savedX = prefs.getInt("panel_x", config.initialX)
            val savedY = prefs.getInt("panel_y", config.initialY)
            
            currentX = savedX.toFloat()
            currentY = savedY.toFloat()
            kalmanX = currentX
            kalmanY = currentY

            view = LayoutInflater.from(context)
                .inflate(R.layout.vmax_floating_panel, null)

            bindViews(view!!)
            setupWindow(savedX, savedY)
            setupActions()
            setupDrag()

            wm.addView(view, params)
            setStatus("IDLE")
            Log.d("FloatingPanel", "✅ Panel shown at ($savedX, $savedY)")
        } catch (e: Exception) {
            Log.e("FloatingPanel", "Failed to show panel", e)
            Toast.makeText(context, "⚠️ UI Error", Toast.LENGTH_SHORT).show()
        }
    }

    // =====================================================
    // BIND
    // =====================================================
    private fun bindViews(v: View) {
        startBtn = v.findViewById(R.id.btn_start)
        stopBtn = v.findViewById(R.id.btn_stop)
        statusText = v.findViewById(R.id.tv_status)
    }

    // =====================================================
    // WINDOW SETUP
    // =====================================================
    private fun setupWindow(x: Int, y: Int) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    // =====================================================
    // ACTION ENGINE (Atomic FSM)
    // =====================================================
    private fun setupActions() {
        
        // 🟢 START BUTTON
        startBtn?.setOnClickListener {
            if (!stateAtomic.compareAndSet(0, 1)) return@setOnClickListener

            val sid = sessionCounter.incrementAndGet()
            render("RUNNING")

            job = scope.launch(Dispatchers.Default) {
                try {
                    orchestrator.start(
                        StrikeConfig(
                            config.defaultTrainNumber,
                            config.defaultBookingClass
                        )
                    )
                } catch (e: Exception) {
                    Log.e("FloatingPanel", "Start failed", e)
                    if (sessionCounter.get() == sid) {
                        stateAtomic.set(3)
                        post("ERROR")
                    }
                } finally {
                    if (sessionCounter.get() == sid) {
                        stateAtomic.set(0)
                        post("IDLE")
                    }
                }
            }
        }

        // 🛑 STOP BUTTON
        stopBtn?.setOnClickListener {
            if (!stateAtomic.compareAndSet(1, 2)) return@setOnClickListener
            val sid = sessionCounter.incrementAndGet()
            render("STOPPING")

            job?.cancel(CancellationException("User stopped"))
            job = null

            scope.launch(Dispatchers.IO) {
                try {
                    engine.reset()
                } catch (e: Exception) {
                    Log.e("FloatingPanel", "Reset failed", e)
                } finally {
                    if (sessionCounter.get() == sid) {
                        stateAtomic.set(0)
                        post("IDLE")
                    }
                }
            }
        }
    }

    // =====================================================
    // FAST UI UPDATE
    // =====================================================
    private inline fun render(t: String) {
        statusText?.text = "Status: $t"
    }

    private fun post(t: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            render(t)
        } else {
            scope.launch(Dispatchers.Main) { render(t) }
        }
    }

    // =====================================================
    // KALMAN FILTER (Noise Reduction for Touch)
    // =====================================================
    private fun applyKalmanFilter(measuredX: Float, measuredY: Float, dt: Float) {
        if (dt <= 0) return
        
        // Simple 1D Kalman Filter constants
        val Q = 0.01f // Process noise covariance
        val R = 0.1f  // Measurement noise covariance
        
        // Prediction Step
        kalmanX += kalmanVelX * dt
        kalmanY += kalmanVelY * dt
                // Update Step (Simplified Gain Calculation)
        val gainX = Q / (Q + R)
        val gainY = Q / (Q + R)
        
        kalmanX += gainX * (measuredX - kalmanX)
        kalmanY += gainY * (measuredY - kalmanY)
        
        // Estimate Velocity from filtered position
        kalmanVelX = (kalmanX - (kalmanX - kalmanVelX * dt)) / dt
        kalmanVelY = (kalmanY - (kalmanY - kalmanVelY * dt)) / dt
    }

    // =====================================================
    // PHYSICS-BASED DRAG & FLING
    // =====================================================
    private fun setupDrag() {
        view?.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0f
            var startY = 0f

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        isFlinging = false
                        startX = e.rawX
                        startY = e.rawY
                        lastTouchTimeNanos = System.nanoTime()
                        lastFrameTimeNanos = System.nanoTime()
                        
                        // Reset velocities
                        velocityX = 0f
                        velocityY = 0f
                        kalmanVelX = 0f
                        kalmanVelY = 0f
                        
                        choreographer.postFrameCallback(frameCallback)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val currentTime = System.nanoTime()
                        val dt = (currentTime - lastTouchTimeNanos) / 1_000_000_000f
                        
                        if (dt > 0) {
                            // Apply Kalman Filter to raw touch coordinates
                            applyKalmanFilter(e.rawX, e.rawY, dt)
                            
                            // Use filtered velocity for smoother feel
                            velocityX = kalmanVelX
                            velocityY = kalmanVelY                            
                            // Update position directly during drag
                            // Center the view on finger
                            currentX = e.rawX - (view?.width ?: 0) / 2f
                            currentY = e.rawY - (view?.height ?: 0) / 2f
                        }
                        
                        startX = e.rawX
                        startY = e.rawY
                        lastTouchTimeNanos = currentTime
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        
                        // Check for Fling based on filtered velocity
                        val speed = hypot(velocityX.toDouble(), velocityY.toDouble()).toFloat()
                        if (speed > config.minVelocity) {
                            isFlinging = true
                            // Continue frame callback for inertia physics
                        } else {
                            isFlinging = false
                            choreographer.removeFrameCallback(frameCallback)
                            savePosition()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    // 🧊 Frame Callback for Physics Simulation (Inertia)
    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        val dt = if (lastFrameTimeNanos == 0L) 0.016f else (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
        lastFrameTimeNanos = frameTimeNanos
        
        if (isFlinging) {
            // Apply Friction (Decay velocity)
            velocityX *= config.friction
            velocityY *= config.friction
            
            // Update Position based on velocity
            currentX += velocityX * dt
            currentY += velocityY * dt
            
            // Stop if too slow
            if (abs(velocityX) < 10f && abs(velocityY) < 10f) {
                isFlinging = false                choreographer.removeFrameCallback(this)
                savePosition()
            }
        }
        
        // Clamp to screen bounds
        val w = view?.width ?: 0
        val h = view?.height ?: 0
        val maxX = metrics.widthPixels - w
        val maxY = metrics.heightPixels - h
        
        currentX = currentX.coerceIn(0f, maxX.toFloat())
        currentY = currentY.coerceIn(0f, maxY.toFloat())
        
        // Update UI Layout Params
        params?.apply {
            x = currentX.toInt()
            y = currentY.toInt()
        }
        view?.let { wm.updateViewLayout(it, params) }
        
        if (isDragging || isFlinging) {
            choreographer.postFrameCallback(this)
        }
    }

    private fun savePosition() {
        prefs.edit()
            .putInt("panel_x", currentX.toInt())
            .putInt("panel_y", currentY.toInt())
            .apply()
    }

    // =====================================================
    // CLEANUP
    // =====================================================
    fun remove() {
        job?.cancel(CancellationException("Panel removed"))
        job = null
        choreographer.removeFrameCallback(frameCallback)

        view?.let { v ->
            try {
                if (v.isAttachedToWindow) {
                    wm.removeView(v)
                }
            } catch (e: IllegalArgumentException) {
                Log.w("FloatingPanel", "View already removed")
            } finally {
                v.setOnTouchListener(null)                startBtn?.setOnClickListener(null)
                stopBtn?.setOnClickListener(null)
                
                view = null
                startBtn = null
                stopBtn = null
                statusText = null
                stateAtomic.set(0)
            }
        }
    }

    fun isShowing(): Boolean = view?.isAttachedToWindow == true
    
    fun updateConfig(newConfig: FloatingPanelConfig) {
        // Config update logic
    }
}
