package com.aare.vmax.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
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
import kotlin.math.abs
import kotlin.math.hypot

data class FloatingPanelConfig(
    val defaultTrainNumber: String = "12487",
    val defaultBookingClass: String = "SL",
    val initialX: Int = 0,
    val initialY: Int = 300,
    val friction: Float = 0.92f,
    val minVelocity: Float = 50f
)

class FloatingPanelManager(
    private val context: Context,
    private val engine: WorkflowEngine,
    private val orchestrator: AutomationOrchestrator,
    private val scope: CoroutineScope,
    private val config: FloatingPanelConfig = FloatingPanelConfig(),
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vmax_panel_v16", Context.MODE_PRIVATE)
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val metrics = context.resources.displayMetrics

    private var view: View? = null
    private var startBtn: Button? = null
    private var stopBtn: Button? = null
    private var statusText: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    private var job: Job? = null

    // Position + physics
    private var currentX = 0f
    private var currentY = 0f
    private var velocityX = 0f
    private var velocityY = 0f

    private var isDragging = false
    private var isFlinging = false

    private val choreographer = Choreographer.getInstance()
    private var lastFrameTime = 0L

    // =========================
    // SHOW PANEL
    // =========================
    @MainThread
    fun show() {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Overlay Permission Required", Toast.LENGTH_LONG).show()
            return
        }

        if (view?.isAttachedToWindow == true) return

        val savedX = prefs.getInt("x", config.initialX)
        val savedY = prefs.getInt("y", config.initialY)

        currentX = savedX.toFloat()
        currentY = savedY.toFloat()

        view = LayoutInflater.from(context).inflate(R.layout.vmax_floating_panel, null)
        bind(view!!)
        setupWindow(savedX, savedY)
        setupActions()
        setupDrag()

        wm.addView(view, params)
        setStatus("IDLE")
    }

    private fun bind(v: View) {
        startBtn = v.findViewById(R.id.btn_start)
        stopBtn = v.findViewById(R.id.btn_stop)
        statusText = v.findViewById(R.id.tv_status)
    }

    private fun setupWindow(x: Int, y: Int) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    // =========================
    // ACTIONS
    // =========================
    private fun setupActions() {

        startBtn?.setOnClickListener {

            job?.cancel()
            setStatus("RUNNING")

            job = scope.launch(Dispatchers.Default) {
                try {
                    orchestrator.start(
                        StrikeConfig(
                            config.defaultTrainNumber,
                            config.defaultBookingClass
                        )
                    )
                } catch (e: Exception) {
                    Log.e("Panel", "Start error", e)
                } finally {
                    setStatus("IDLE")
                }
            }
        }

        stopBtn?.setOnClickListener {

            job?.cancel()
            job = null

            scope.launch {
                try {
                    orchestrator.reset()
                } catch (e: Exception) {
                    Log.e("Panel", "Reset error", e)
                } finally {
                    setStatus("IDLE")
                }
            }
        }
    }

    // =========================
    // STATUS
    // =========================
    private fun setStatus(text: String) {
        scope.launch(Dispatchers.Main) {
            statusText?.text = "Status: $text"
        }
    }

    // =========================
    // DRAG + FLING
    // =========================
    private fun setupDrag() {

        view?.setOnTouchListener { _, e ->

            when (e.action) {

                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    isFlinging = false
                    velocityX = 0f
                    velocityY = 0f
                    choreographer.postFrameCallback(frameCallback)
                }

                MotionEvent.ACTION_MOVE -> {
                    currentX = e.rawX - (view?.width ?: 0) / 2f
                    currentY = e.rawY - (view?.height ?: 0) / 2f
                    velocityX = e.x
                    velocityY = e.y
                }

                MotionEvent.ACTION_UP -> {
                    isDragging = false

                    val speed = hypot(velocityX, velocityY)

                    if (speed > config.minVelocity) {
                        isFlinging = true
                        choreographer.postFrameCallback(frameCallback)
                    } else {
                        savePosition()
                        stopAnimation()
                    }
                }
            }
            true
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(time: Long) {

            val dt = if (lastFrameTime == 0L) 0.016f
            else (time - lastFrameTime) / 1_000_000_000f

            lastFrameTime = time

            if (isFlinging) {
                velocityX *= config.friction
                velocityY *= config.friction

                currentX += velocityX * dt
                currentY += velocityY * dt

                if (abs(velocityX) < 5f && abs(velocityY) < 5f) {
                    isFlinging = false
                    stopAnimation()
                    savePosition()
                }

                updatePosition()
                choreographer.postFrameCallback(this)
            }
        }
    }

    private fun updatePosition() {
        val w = view?.width ?: 0
        val h = view?.height ?: 0

        val maxX = metrics.widthPixels - w
        val maxY = metrics.heightPixels - h

        currentX = currentX.coerceIn(0f, maxX.toFloat())
        currentY = currentY.coerceIn(0f, maxY.toFloat())

        params?.x = currentX.toInt()
        params?.y = currentY.toInt()

        view?.let { wm.updateViewLayout(it, params) }
    }

    private fun stopAnimation() {
        choreographer.removeFrameCallback(frameCallback)
        isFlinging = false
    }

    private fun savePosition() {
        prefs.edit()
            .putInt("x", currentX.toInt())
            .putInt("y", currentY.toInt())
            .apply()
    }

    // =========================
    // REMOVE
    // =========================
    fun remove() {
        job?.cancel()
        job = null

        stopAnimation()

        view?.let {
            if (it.isAttachedToWindow) {
                wm.removeView(it)
            }
        }

        view = null
        startBtn = null
        stopBtn = null
        statusText = null
    }

    fun isShowing(): Boolean = view?.isAttachedToWindow == true
}
