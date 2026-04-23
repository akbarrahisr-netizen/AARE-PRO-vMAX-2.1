package com.aare.vmax.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.aare.vmax.core.orchestrator.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class FloatingPanelManager(
    context: Context,
    private val workflowEngine: WorkflowEngine, // 👈 यह लाइन मिसिंग थी!
    private val orchestrator: AutomationOrchestrator,
    private val scope: CoroutineScope
) {

    private val contextRef = WeakReference(context)
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var floatingView: LinearLayout? = null
    private var statusText: TextView? = null
    private var isShowing = false

    companion object {
        private const val TAG = "FloatingPanel"
        private const val DEFAULT_X = 50
        private const val DEFAULT_Y = 200
        private const val PANEL_ALPHA = 0.9f
    }

    // ✅ Permission handler
    private fun checkPermission(): Boolean {
        val ctx = contextRef.get() ?: return false

        if (!Settings.canDrawOverlays(ctx)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)

                Toast.makeText(
                    ctx,
                    "Enable 'Draw over other apps'",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                Log.e(TAG, "Permission error", e)
            }
            return false
        }
        return true
    }

    fun show() {
        if (isShowing) return
        if (!checkPermission()) return

        val ctx = contextRef.get() ?: return

        try {
            floatingView = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#CC000000"))
                setPadding(30, 30, 30, 30)
                elevation = 12f
            }

            val title = TextView(ctx).apply {
                text = "VMAX PRO"
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                textSize = 15f
                setPadding(0, 0, 0, 12)
            }

            statusText = TextView(ctx).apply {
                text = "Status: Ready"
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(0, 0, 0, 12)
            }

            val btnStart = Button(ctx).apply {
                text = "▶ START"
                setBackgroundColor(Color.parseColor("#2E7D32"))
                setTextColor(Color.WHITE)

                setOnClickListener {
                    updateStatus("Starting...")

                    scope.launch {
                        try {
                            orchestrator.start(
                                StrikeConfig("12487", "SL")
                            )
                            updateStatus("Running ✓")
                        } catch (e: Exception) {
                            Log.e(TAG, "Start failed", e)
                            updateStatus("Error ✗")
                        }
                    }
                }
            }

            val btnStop = Button(ctx).apply {
                text = "■ STOP"
                setBackgroundColor(Color.parseColor("#C62828"))
                setTextColor(Color.WHITE)

                setOnClickListener {
                    scope.launch {
                        try {
                            orchestrator.reset()
                            updateStatus("Stopped")
                        } catch (e: Exception) {
                            Log.e(TAG, "Stop failed", e)
                        }
                    }
                }
            }

            floatingView?.apply {
                addView(title)
                addView(statusText)
                addView(btnStart)
                addView(btnStop)
            }

            val type =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = DEFAULT_X
                y = DEFAULT_Y
                alpha = PANEL_ALPHA
            }

            // ✅ Drag support
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f

            floatingView?.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        true
                    }

                    else -> false
                }
            }

            windowManager.addView(floatingView, params)
            isShowing = true

        } catch (e: Exception) {
            Log.e(TAG, "Show failed", e)
        }
    }

    fun remove() {
        if (!isShowing) return

        try {
            floatingView?.let {
                windowManager.removeView(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remove failed", e)
        } finally {
            floatingView = null
            statusText = null
            isShowing = false
        }
    }

    fun updateStatus(text: String) {
        statusText?.post {
            statusText?.text = "Status: $text"
        }
    }

    fun toggle() {
        if (isShowing) remove() else show()
    }

    fun isVisible(): Boolean = isShowing
}
