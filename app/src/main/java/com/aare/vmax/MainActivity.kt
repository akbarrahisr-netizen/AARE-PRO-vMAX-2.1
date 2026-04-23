package com.aare.vmax

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
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
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import java.util.*

class MainActivity : Activity() {

    companion object {
        private const val TAG = "VMAX_Main"
        private const val PREF_NAME = "VMaxProfile"
        // ✅ Exact component name for accessibility check
        private const val ACCESSIBILITY_SERVICE_COMPONENT = 
            "com.aare.vmax/com.aare.vmax.core.service.VMaxAccessibilityService"
    }

    // 🎨 UI References
    private lateinit var etTrain: EditText
    private lateinit var etName: EditText
    private lateinit var etAge: EditText
    private lateinit var btnPerm: Button
    private lateinit var tvStatus: TextView

    // 💾 SharedPreferences
    private val prefs by lazy { getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity created")
        
        setupUI()
        loadSavedData()
        checkServiceStatus()
    }

    override fun onResume() {
        super.onResume()        // ✅ Jab user wapas aaye toh status refresh karein
        updatePermissionButton()
        checkServiceStatus()
    }

    // ❌ onActivityResult REMOVED - Deprecated method ab use nahi karenge
    // ✅ Ab simple startActivity use karenge + onResume mein status check hoga

    // 🎨 UI Setup
    private fun setupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(40, 60, 40, 60)
        }

        // 🚀 Title
        layout.addView(TextView(this).apply {
            text = "🚀 AARE-PRO vMAX 2.1"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        // 📊 Service Status Indicator
        tvStatus = TextView(this).apply {
            text = "🔴 Service: Not Running"
            setTextColor(Color.parseColor("#FF6B6B"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
            textSize = 14f
        }
        layout.addView(tvStatus)

        // 📝 Input Fields
        etTrain = createInput("🚂 Train Number", prefs.getString("train", "12487"))
        etName = createInput("👤 Passenger Name", prefs.getString("name", "Md Ilahi"))
        etAge = createInput("🎂 Age", prefs.getString("age", "27"))
        
        layout.addView(etTrain)
        layout.addView(etName)
        layout.addView(etAge)

        // 💾 SAVE BUTTON
        val btnSave = Button(this).apply {
            text = "💾 Save Profile"
            setBackgroundColor(Color.parseColor("#5E35B1"))
            setTextColor(Color.WHITE)            setPadding(0, 25, 0, 25)
            elevation = 5f
            setOnClickListener { saveProfile() }
        }
        layout.addView(btnSave)

        // 🗑️ CLEAR BUTTON
        val btnClear = Button(this).apply {
            text = "🗑️ Clear All Data"
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.LTGRAY)
            setPadding(0, 20, 0, 20)
            setOnClickListener { clearProfile() }
        }
        layout.addView(btnClear)

        // Spacing
        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30
            )
        })

        // ⚙️ PERMISSION BUTTON
        btnPerm = Button(this).apply {
            text = "⚙️ Setup Permissions"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setTextColor(Color.WHITE)
            setPadding(0, 25, 0, 25)
            elevation = 5f
            setOnClickListener { handlePermissionFlow() }
        }
        layout.addView(btnPerm)

        // ℹ️ Helper Text
        layout.addView(TextView(this).apply {
            text = "💡 Tip: Enable Accessibility + Overlay permissions for automation"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 0)
        })

        setContentView(layout)
    }

    // 📥 Create styled EditText
    private fun createInput(hintText: String, savedValue: String?): EditText {
        return EditText(this).apply {
            hint = hintText            setText(savedValue)
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            setPadding(30, 25, 30, 25)
            textSize = 15f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }
    }

    // 💾 Save Profile Data
    private fun saveProfile() {
        val train = etTrain.text.toString().trim()
        val name = etName.text.toString().trim()
        val age = etAge.text.toString().trim()

        if (train.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "⚠️ Train & Name are required!", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putString("train", train)
            .putString("name", name)
            .putString("age", age)
            .putLong("last_saved", System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Profile saved: $train, $name, $age")
        Toast.makeText(this, "✅ Profile Saved Successfully!", Toast.LENGTH_SHORT).show()
    }

    // 🗑️ Clear Profile Data
    private fun clearProfile() {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Clear Data?")
            .setMessage("Sab saved data delete ho jayega. Continue karein?")
            .setPositiveButton("Yes") { _, _ ->
                prefs.edit().clear().apply()
                etTrain.setText("12487")
                etName.setText("Md Ilahi")
                etAge.setText("27")
                Toast.makeText(this, "🗑️ Data Cleared", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Profile data cleared")            }
            .setNegativeButton("No", null)
            .show()
    }

    // ⚙️ Permission Flow Handler - ✅ FIXED: No startActivityForResult
    private fun handlePermissionFlow() {
        when {
            // ❌ Overlay permission nahi hai
            !Settings.canDrawOverlays(this) -> {
                Log.w(TAG, "Overlay permission missing, requesting...")
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    // ✅ FIXED: Simple startActivity, no requestCode needed
                    startActivity(intent)
                    // ✅ User wapas aayega toh onResume mein status auto-check hoga
                } catch (e: Exception) {
                    Log.e(TAG, "Overlay intent failed", e)
                    showToast("❌ Could not open overlay settings")
                }
            }
            // ✅ Overlay hai, ab Accessibility check karein
            !isAccessibilityServiceEnabled() -> {
                Log.w(TAG, "Accessibility not enabled, redirecting...")
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    showToast("🔧 Please enable 'VMAX PRO' in Accessibility")
                } catch (e: Exception) {
                    Log.e(TAG, "Accessibility intent failed", e)
                    showToast("⚙️ Go to Settings > Accessibility > Enable VMAX PRO")
                }
            }
            // ✅ Sab permissions ready hain!
            else -> {
                showToast("🎉 All permissions granted! Service should be running.")
                checkServiceStatus()
            }
        }
        updatePermissionButton()
    }

    // ✅ FIXED: Safe & Exact Accessibility Service Check
    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            // ✅ Split by ":" and check for EXACT component name match
            // Format: "packageName1/ServiceName1:packageName2/ServiceName2"
            enabledServices.split(":")
                .any { it.trim().equals(ACCESSIBILITY_SERVICE_COMPONENT, ignoreCase = true) }
                
        } catch (e: Exception) {
            Log.e(TAG, "Service check failed", e)
            false
        }
    }

    // 🔄 Update Permission Button Text/Color
    private fun updatePermissionButton() {
        when {
            !Settings.canDrawOverlays(this) -> {
                btnPerm.text = "❌ Allow Overlay Permission"
                btnPerm.setBackgroundColor(Color.parseColor("#D32F2F")) // Red
                btnPerm.isEnabled = true
            }
            !isAccessibilityServiceEnabled() -> {
                btnPerm.text = "⚙️ Enable Accessibility Service"
                btnPerm.setBackgroundColor(Color.parseColor("#F57C00")) // Orange
                btnPerm.isEnabled = true
            }
            else -> {
                btnPerm.text = "✅ All Permissions Granted"
                btnPerm.setBackgroundColor(Color.parseColor("#2E7D32")) // Green
                btnPerm.isEnabled = false
            }
        }
    }

    // 📡 Check if Service is actually running
    private fun checkServiceStatus() {
        val isRunning = isAccessibilityServiceEnabled() && Settings.canDrawOverlays(this)
        
        tvStatus.apply {
            if (isRunning) {
                text = "🟢 Service: Active & Running"
                setTextColor(Color.parseColor("#66BB6A"))
            } else {
                text = "🔴 Service: Not Running"
                setTextColor(Color.parseColor("#FF6B6B"))
            }
        }
                Log.d(TAG, "Service status: ${if (isRunning) "RUNNING" else "STOPPED"}")
    }

    // 📥 Load saved data into fields
    private fun loadSavedData() {
        etTrain.setText(prefs.getString("train", "12487"))
        etName.setText(prefs.getString("name", "Md Ilahi"))
        etAge.setText(prefs.getString("age", "27"))
    }

    // 🍞 Simple Toast Helper
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // 🧹 Cleanup
    override fun onDestroy() {
        Log.d(TAG, "MainActivity destroyed")
        super.onDestroy()
    }
}
