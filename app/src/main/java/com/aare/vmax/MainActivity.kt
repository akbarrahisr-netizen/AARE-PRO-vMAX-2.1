package com.aare.vmax

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    companion object {
        private const val TAG = "VMAX_Main"
        private const val PREF_NAME = "VMaxProfile"
    }

    private lateinit var etTrain: EditText
    private lateinit var etName: EditText
    private lateinit var etAge: EditText
    private lateinit var btnPerm: Button
    private lateinit var tvStatus: TextView

    private val prefs by lazy { getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        loadSavedData()
        checkServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButton()
        checkServiceStatus()
    }

    // ================= UI =================

    private fun setupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(40, 60, 40, 60)
        }

        layout.addView(TextView(this).apply {
            text = "🚀 AARE-PRO vMAX 2.1"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        })

        tvStatus = TextView(this).apply {
            text = "🔴 Service: Not Ready"
            setTextColor(Color.parseColor("#FF6B6B"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        layout.addView(tvStatus)

        etTrain = createInput("🚂 Train Number", "")
        etName = createInput("👤 Passenger Name", "")
        etAge = createInput("🎂 Age", "").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(etTrain)
        layout.addView(etName)
        layout.addView(etAge)

        val btnSave = Button(this).apply {
            text = "💾 Save Profile"
            setBackgroundColor(Color.parseColor("#5E35B1"))
            setTextColor(Color.WHITE)
            setOnClickListener { saveProfile() }
        }
        layout.addView(btnSave)

        val btnClear = Button(this).apply {
            text = "🗑️ Clear All Data"
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.LTGRAY)
            setOnClickListener { clearProfile() }
        }
        layout.addView(btnClear)

        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30
            )
        })

        btnPerm = Button(this).apply {
            text = "⚙️ Setup Permissions"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setTextColor(Color.WHITE)
            setOnClickListener { handlePermissionFlow() }
        }
        layout.addView(btnPerm)

        setContentView(layout)
    }

    private fun createInput(hint: String, value: String?): EditText {
        return EditText(this).apply {
            this.hint = hint
            setText(value)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            setPadding(30, 25, 30, 25)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }
    }

    // ================= DATA =================

    private fun saveProfile() {
        val train = etTrain.text.toString().trim()
        val name = etName.text.toString().trim()
        val age = etAge.text.toString().trim().toIntOrNull()

        if (train.isEmpty() || name.isEmpty() || age == null) {
            toast("⚠️ Enter valid details")
            return
        }

        prefs.edit()
            .putString("train", train)
            .putString("name", name)
            .putInt("age", age)
            .apply()

        toast("✅ Saved")
    }

    private fun loadSavedData() {
        etTrain.setText(prefs.getString("train", "12487"))
        etName.setText(prefs.getString("name", "Md Ilahi"))
        etAge.setText(prefs.getInt("age", 27).toString())
    }

    private fun clearProfile() {
        AlertDialog.Builder(this)
            .setTitle("Clear Data?")
            .setMessage("All data will be deleted")
            .setPositiveButton("Yes") { _, _ ->
                prefs.edit().clear().apply()
                loadSavedData()
                toast("🗑️ Cleared")
            }
            .setNegativeButton("No", null)
            .show()
    }

    // ================= PERMISSIONS =================

    private fun handlePermissionFlow() {
        when {
            !Settings.canDrawOverlays(this) -> {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }

            !isAccessibilityEnabled() -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                toast("Enable VMAX Service")
            }

            else -> toast("✅ All permissions granted")
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${com.aare.vmax.core.service.VMaxAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        return enabled.split(":").any { it.equals(expected, true) }
    }

    private fun updatePermissionButton() {
        when {
            !Settings.canDrawOverlays(this) -> {
                btnPerm.text = "❌ Allow Overlay"
                btnPerm.setBackgroundColor(Color.RED)
            }

            !isAccessibilityEnabled() -> {
                btnPerm.text = "⚙️ Enable Accessibility"
                btnPerm.setBackgroundColor(Color.YELLOW)
            }

            else -> {
                btnPerm.text = "✅ Ready"
                btnPerm.setBackgroundColor(Color.GREEN)
            }
        }
    }

    private fun checkServiceStatus() {
        val ready = isAccessibilityEnabled() && Settings.canDrawOverlays(this)

        if (ready) {
            tvStatus.text = "🟢 Ready"
            tvStatus.setTextColor(Color.GREEN)
        } else {
            tvStatus.text = "🔴 Not Ready"
            tvStatus.setTextColor(Color.RED)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
