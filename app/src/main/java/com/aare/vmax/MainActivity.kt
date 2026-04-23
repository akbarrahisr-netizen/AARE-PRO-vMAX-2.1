package com.aare.vmax

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Calendar

// ✅ बुलेटप्रूफ Activity (कभी क्रैश नहीं होगी)
class MainActivity : Activity() {

    // ═══════════════════════════════════════════════════════
    // 📱 SAFE UI COMPONENTS (Nullable)
    // ═══════════════════════════════════════════════════════
    
    private var etTrainNumber: EditText? = null
    private var etClass: EditText? = null
    private var etLatency: EditText? = null
    private var btnSaveProfile: Button? = null
    private var btnStartIrctc: Button? = null
    private var tvProfileStatus: TextView? = null

    private val passengerFields = mutableListOf<PassengerFieldSet>()
    private var prefs: SharedPreferences? = null

    companion object {
        private const val PREFS_NAME = "VMaxProfile"
        private const val SERVICE_CLASS_NAME = "com.aare.vmax.VMAXAccessibilityService"
        private const val DEFAULT_LATENCY_MS = 400
        private const val MAX_PASSENGERS = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 🔥 ANTI-CRASH SHIELD: अब ऐप बैक नहीं होगा!
        try {
            setContentView(R.layout.activity_main)
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            initViews()
            setupPickers()
            loadSavedData()
            setupClickListeners()

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    checkBatteryOptimization()
                    handleFirstTimeSetup()
                } catch (e: Exception) {}
            }, 500)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "UI Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (isAccessibilityServiceEnabled()) {
                showStatus("✅ VMAX Service Active", android.R.color.holo_green_dark)
            }
        } catch (e: Exception) {}
    }

    private fun initViews() {
        etTrainNumber = findViewById(R.id.etTrainNumber)
        etClass = findViewById(R.id.etClass)
        etLatency = findViewById(R.id.etLatency)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        btnStartIrctc = findViewById(R.id.btnStartIrctc)
        tvProfileStatus = findViewById(R.id.tvProfileStatus)

        // Safe Initialization
        findViewById<EditText>(R.id.etName1)?.let { name ->
            passengerFields.add(PassengerFieldSet(name, findViewById(R.id.etAge1), findViewById(R.id.etGender1)))
        }
        findViewById<EditText>(R.id.etName2)?.let { name ->
            passengerFields.add(PassengerFieldSet(name, findViewById(R.id.etAge2), findViewById(R.id.etGender2)))
        }
        findViewById<EditText>(R.id.etName3)?.let { name ->
            passengerFields.add(PassengerFieldSet(name, findViewById(R.id.etAge3), findViewById(R.id.etGender3)))
        }
        findViewById<EditText>(R.id.etName4)?.let { name ->
            passengerFields.add(PassengerFieldSet(name, findViewById(R.id.etAge4), findViewById(R.id.etGender4)))
        }
    }

    private fun setupPickers() {
        passengerFields.forEach { it.setupGenderPicker(this) }
        etClass?.setupClassPicker(this)
    }

    private fun loadSavedData() {
        prefs?.let { p ->
            etTrainNumber?.setText(p.getString("train", ""))
            etClass?.setText(p.getString("class", "SL"))
            etLatency?.setText(p.getInt("latency_ms", DEFAULT_LATENCY_MS).toString())

            passengerFields.forEachIndexed { index, fields ->
                fields.loadFromPrefs(p, index)
            }
        }
    }

    private fun setupClickListeners() {
        btnSaveProfile?.setOnClickListener { handleSaveProfile() }
        btnStartIrctc?.setOnClickListener { handleStartAutomation() }
    }

    private fun handleSaveProfile() {
        try {
            val trainNumber = etTrainNumber?.text?.toString()?.trim() ?: ""
            if (trainNumber.isEmpty() || trainNumber.length != 5) {
                showStatus("❌ Enter valid 5-digit Train Number", android.R.color.holo_red_dark)
                return
            }

            var count = 0
            val editor = prefs?.edit()

            passengerFields.forEachIndexed { index, fields ->
                val name = fields.nameEt?.text?.toString()?.trim() ?: ""
                if (name.isNotEmpty()) {
                    count++
                    editor?.putString("name_$index", name)
                    editor?.putString("age_$index", fields.ageEt?.text?.toString()?.trim() ?: "")
                    editor?.putString("gender_$index", fields.genderEt?.text?.toString()?.trim() ?: "")
                }
            }

            if (count == 0) {
                showStatus("❌ Add at least 1 passenger", android.R.color.holo_red_dark)
                return
            }

            val latencyMs = etLatency?.text?.toString()?.trim()?.toIntOrNull() ?: DEFAULT_LATENCY_MS

            editor?.putString("train", trainNumber)
            editor?.putString("class", etClass?.text?.toString()?.trim() ?: "SL")
            editor?.putInt("latency_ms", latencyMs)
            editor?.putInt("passenger_count", count)
            editor?.putBoolean("settings_guide_shown", true)
            editor?.apply()

            showStatus("✅ Saved! $count passenger(s) | ${latencyMs}ms", android.R.color.holo_green_dark)
        } catch (e: Exception) {
            Toast.makeText(this, "Save Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleStartAutomation() {
        try {
            if (prefs?.getString("train", "").isNullOrEmpty()) {
                showStatus("❌ Please save profile first!", android.R.color.holo_red_dark)
                return
            }

            if (!isAccessibilityServiceEnabled()) {
                showStatus("⚠️ Enable VMAX in Accessibility", android.R.color.holo_orange_dark)
                openAccessibilitySettings()
                return
            }

            showStatus("🚀 Firing instantly!", android.R.color.holo_blue_dark)

            val intent = Intent("com.aare.vmax.ACTION_START_AUTOMATION")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            
            launchIrctcApp()
        } catch (e: Exception) {
            Toast.makeText(this, "Start Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                val service = splitter.next()
                if (service.equals(SERVICE_CLASS_NAME, ignoreCase = true) || service.contains(packageName, ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {}
        return false
    }

    private fun launchIrctcApp() {
        val packages = listOf("cris.org.in.prs.ima", "com.irctc.railconnect", "in.irctc.railconnect")
        for (pkg in packages) {
            try {
                packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    return
                }
            } catch (e: Exception) { continue }
        }
        Toast.makeText(this, "IRCTC App not found!", Toast.LENGTH_SHORT).show()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {}
            }
        }
    }

    private fun handleFirstTimeSetup() {
        if (prefs?.getBoolean("settings_guide_shown", false) == false) {
            prefs?.edit()?.putBoolean("settings_guide_shown", true)?.apply()
            Toast.makeText(this, "Welcome to VMAX Pro! Save your profile first.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showStatus(message: String, colorRes: Int) {
        tvProfileStatus?.text = message
        tvProfileStatus?.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {}
    }
}

// ═══════════════════════════════════════════════════════
// 🧩 HELPER CLASSES
// ═══════════════════════════════════════════════════════

data class PassengerData(val name: String, val age: String, val gender: String)

data class PassengerFieldSet(
    val nameEt: EditText?,
    val ageEt: EditText?,
    val genderEt: EditText?
) {
    fun setupGenderPicker(context: Context) {
        genderEt?.isFocusable = false
        genderEt?.setOnClickListener {
            val options = arrayOf("Male", "Female", "Transgender")
            android.app.AlertDialog.Builder(context)
                .setTitle("Select Gender")
                .setItems(options) { _, which -> genderEt.setText(options[which]) }
                .show()
        }
    }

    fun loadFromPrefs(prefs: SharedPreferences, index: Int) {
        nameEt?.setText(prefs.getString("name_$index", ""))
        ageEt?.setText(prefs.getString("age_$index", ""))
        genderEt?.setText(prefs.getString("gender_$index", ""))
    }
}

fun EditText.setupClassPicker(context: Context) {
    isFocusable = false
    setOnClickListener {
        val classes = arrayOf("SL", "3E", "3A", "2A", "1A", "2S", "CC")
        android.app.AlertDialog.Builder(context)
            .setTitle("Select Class")
            .setItems(classes) { _, which -> setText(classes[which]) }
            .show()
    }
}
