package com.aare.vmax

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    // ═══════════════════════════════════════════════════════
    // ⚙️ CONSTANTS & CONFIG
    // ═══════════════════════════════════════════════════════
    companion object {
        private const val TAG = "VMAX_MAIN"
        private const val PREFS_NAME = "VMaxProfile"
        
        // Latency config
        private const val DEFAULT_LATENCY_MS = 400
        private const val MIN_LATENCY_MS = 50
        private const val MAX_LATENCY_MS = 2000
        
        // Validation limits
        private const val MIN_NAME_LENGTH = 3 // ✅ इसे 3 कर दिया ताकि छोटे नाम भी आ सकें
        private const val MAX_NAME_LENGTH = 16
        private const val MIN_AGE = 1
        private const val MAX_AGE = 199
        
        // Broadcast action for service
        const val ACTION_START_AUTOMATION = "com.aare.vmax.ACTION_START_AUTOMATION"
    }

    // ═══════════════════════════════════════════════════════
    // 📱 UI COMPONENTS (lateinit = non-null after setContentView)
    // ═══════════════════════════════════════════════════════
    private lateinit var prefs: SharedPreferences
    
    // Train Details    
    private lateinit var etTrainNumber: EditText
    private lateinit var etClass: EditText
    private lateinit var etLatency: EditText
    
    // Passenger 1
    private lateinit var etName1: EditText; private lateinit var etAge1: EditText
    private lateinit var etGender1: EditText; private lateinit var etMeal1: EditText
    
    // Passenger 2 
    private lateinit var etName2: EditText; private lateinit var etAge2: EditText
    private lateinit var etGender2: EditText; private lateinit var etMeal2: EditText

    // ✅ Passenger 3 (Added)
    private lateinit var etName3: EditText; private lateinit var etAge3: EditText
    private lateinit var etGender3: EditText; private lateinit var etMeal3: EditText

    // ✅ Passenger 4 (Added)
    private lateinit var etName4: EditText; private lateinit var etAge4: EditText
    private lateinit var etGender4: EditText; private lateinit var etMeal4: EditText
    
    // Advanced & Payment
    private lateinit var cbAutoUpgrade: CheckBox
    private lateinit var cbConfirmOnly: CheckBox
    private lateinit var etPaymentMethod: EditText
    
    // Actions & Status
    private lateinit var btnSaveProfile: Button
    private lateinit var tvProfileStatus: TextView  // Status feedback

    // ═══════════════════════════════════════════════════════
    // 🔄 LIFECYCLE
    // ═══════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        initViews()
        setupPickers()
        setupClickListeners()
        loadSavedData()
        
        Log.d(TAG, "✅ MainActivity initialized with 4 Passengers")
    }

    override fun onResume() {
        super.onResume()
        if (isAccessibilityEnabled()) {
            updateStatus("✅ VMAX Service Active", android.R.color.holo_green_dark)
        } else {
            updateStatus("⚠️ Enable VMAX in Accessibility", android.R.color.holo_orange_dark)
        }    
    }

    // ═══════════════════════════════════════════════════════
    // 🎨 VIEW INITIALIZATION
    // ═══════════════════════════════════════════════════════
    private fun initViews() {
        etTrainNumber = findViewById(R.id.etTrainNumber)
        etClass = findViewById(R.id.etClass)
        etLatency = findViewById(R.id.etLatency)
        
        etName1 = findViewById(R.id.etName1); etAge1 = findViewById(R.id.etAge1)
        etGender1 = findViewById(R.id.etGender1); etMeal1 = findViewById(R.id.etMeal1)
        
        etName2 = findViewById(R.id.etName2); etAge2 = findViewById(R.id.etAge2)
        etGender2 = findViewById(R.id.etGender2); etMeal2 = findViewById(R.id.etMeal2)

        // ✅ Init Passenger 3
        etName3 = findViewById(R.id.etName3); etAge3 = findViewById(R.id.etAge3)
        etGender3 = findViewById(R.id.etGender3); etMeal3 = findViewById(R.id.etMeal3)

        // ✅ Init Passenger 4
        etName4 = findViewById(R.id.etName4); etAge4 = findViewById(R.id.etAge4)
        etGender4 = findViewById(R.id.etGender4); etMeal4 = findViewById(R.id.etMeal4)
        
        cbAutoUpgrade = findViewById(R.id.cbAutoUpgrade)
        cbConfirmOnly = findViewById(R.id.cbConfirmOnly)
        etPaymentMethod = findViewById(R.id.etPaymentMethod)
        
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        tvProfileStatus = findViewById(R.id.tvProfileStatus)
    }

    // ═══════════════════════════════════════════════════════
    // 🎯 PICKER SETUP 
    // ═══════════════════════════════════════════════════════
    private fun setupPickers() {
        setupPicker(etClass, arrayOf("SL", "3E", "3A", "2A", "1A", "2S", "CC"))
        setupPicker(etPaymentMethod, arrayOf("PhonePe", "Paytm", "Mobikwik", "UPI", "Wallet"))

        val genders = arrayOf("Male", "Female", "Transgender")
        val meals = arrayOf("Veg", "Non-Veg", "No Food")
        
        setupPicker(etGender1, genders); setupPicker(etMeal1, meals)
        setupPicker(etGender2, genders); setupPicker(etMeal2, meals)
        
        // ✅ Pickers for Passenger 3 & 4
        setupPicker(etGender3, genders); setupPicker(etMeal3, meals)
        setupPicker(etGender4, genders); setupPicker(etMeal4, meals)
    }

    private fun setupPicker(editText: EditText, options: Array<String>) {
        editText.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Select Option")
                .setItems(options) { _, which -> 
                    editText.setText(options[which])
                }
                .show()
        }
    }

    private fun setupClickListeners() {
        btnSaveProfile.setOnClickListener { handleSaveAndFire() }
    }

    // ═══════════════════════════════════════════════════════
    // 💾 LOAD SAVED DATA
    // ═══════════════════════════════════════════════════════
    private fun loadSavedData() {
        etTrainNumber.setText(prefs.getString("train", ""))
        etClass.setText(prefs.getString("class", "SL"))
        
        val savedLatency = prefs.getInt("latency_ms", DEFAULT_LATENCY_MS)
        etLatency.setText(savedLatency.coerceIn(MIN_LATENCY_MS, MAX_LATENCY_MS).toString())
        
        etName1.setText(prefs.getString("name_0", "")); etAge1.setText(prefs.getString("age_0", ""))
        etGender1.setText(prefs.getString("gender_0", "Male")); etMeal1.setText(prefs.getString("meal_0", "No Food"))
        
        etName2.setText(prefs.getString("name_1", "")); etAge2.setText(prefs.getString("age_1", ""))
        etGender2.setText(prefs.getString("gender_1", "Male")); etMeal2.setText(prefs.getString("meal_1", "No Food"))
        
        // ✅ Load Passenger 3
        etName3.setText(prefs.getString("name_2", "")); etAge3.setText(prefs.getString("age_2", ""))
        etGender3.setText(prefs.getString("gender_2", "Male")); etMeal3.setText(prefs.getString("meal_2", "No Food"))

        // ✅ Load Passenger 4
        etName4.setText(prefs.getString("name_3", "")); etAge4.setText(prefs.getString("age_3", ""))
        etGender4.setText(prefs.getString("gender_3", "Male")); etMeal4.setText(prefs.getString("meal_3", "No Food"))

        etPaymentMethod.setText(prefs.getString("payment_method", "PhonePe"))
        cbAutoUpgrade.isChecked = prefs.getBoolean("auto_upgrade", false)
        cbConfirmOnly.isChecked = prefs.getBoolean("confirm_only", false)
    }

    // ═══════════════════════════════════════════════════════
    // ✅ SAVE PROFILE + FIRE AUTOMATION (Smart Loop Logic)
    // ═══════════════════════════════════════════════════════    
    private fun handleSaveAndFire() {
        try {
            // 1️⃣ Validate Accessibility
            if (!isAccessibilityEnabled()) {
                updateStatus("⚠️ Enable VMAX in Accessibility!", android.R.color.holo_orange_dark)
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return
            }

            // 2️⃣ Validate Train
            val train = etTrainNumber.text.toString().trim()
            if (train.length != 5 || !train.matches(Regex("^\\d{5}$"))) {
                etTrainNumber.error = "Enter valid 5-digit train number"
                etTrainNumber.requestFocus()
                updateStatus("❌ Invalid Train Number", android.R.color.holo_red_dark)
                return
            }

            val latencyMs = etLatency.text.toString().trim().toIntOrNull()?.coerceIn(MIN_LATENCY_MS, MAX_LATENCY_MS) ?: DEFAULT_LATENCY_MS

            // 3️⃣ SMART PASSENGER LOOP (4p)
            val editor = prefs.edit()
            var validPassengerCount = 0
            
            // Group all passengers into a list
            val passengersList = listOf(
                PassengerInput(etName1, etAge1, etGender1, etMeal1),
                PassengerInput(etName2, etAge2, etGender2, etMeal2),
                PassengerInput(etName3, etAge3, etGender3, etMeal3),
                PassengerInput(etName4, etAge4, etGender4, etMeal4)
            )

            // Loop through and validate ONLY filled ones
            for (p in passengersList) {
                val name = p.name.text.toString().trim()
                val ageText = p.age.text.toString().trim()

                if (name.isNotEmpty()) {
                    if (name.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
                        p.name.error = "Name: $MIN_NAME_LENGTH-$MAX_NAME_LENGTH chars"
                        p.name.requestFocus()
                        updateStatus("❌ Invalid Name", android.R.color.holo_red_dark)
                        return
                    }
                    val age = ageText.toIntOrNull()
                    if (age == null || age !in MIN_AGE..MAX_AGE) {
                        p.age.error = "Age: $MIN_AGE-$MAX_AGE"
                        p.age.requestFocus()
                        updateStatus("❌ Invalid Age", android.R.color.holo_red_dark)
                        return
                    }

                    // Save valid passenger
                    editor.putString("name_$validPassengerCount", name)
                    editor.putString("age_$validPassengerCount", ageText)
                    editor.putString("gender_$validPassengerCount", p.gender.text.toString().trim().ifEmpty { "Male" })
                    editor.putString("meal_$validPassengerCount", p.meal.text.toString().trim().ifEmpty { "No Food" })
                    
                    validPassengerCount++
                }
            }

            // Check if at least 1 is added
            if (validPassengerCount == 0) {                
                updateStatus("❌ Add at least 1 passenger", android.R.color.holo_red_dark)
                return
            }

            // 4️⃣ Save Master Data
            editor.putString("train", train)
            editor.putString("class", etClass.text.toString().trim())
            editor.putInt("latency_ms", latencyMs)
            
            editor.putBoolean("auto_upgrade", cbAutoUpgrade.isChecked)
            editor.putBoolean("confirm_only", cbConfirmOnly.isChecked)
            editor.putString("payment_method", etPaymentMethod.text.toString().trim())
            
            editor.putInt("passenger_count", validPassengerCount)
            editor.putBoolean("profile_saved", true)
            editor.apply()

            // 5️⃣ Success & Launch
            updateStatus("✅ Sniper Armed! ($validPassengerCount passenger) | ${latencyMs}ms", android.R.color.holo_green_dark)
            Log.d(TAG, "✅ Profile saved: Train=$train, Passengers=$validPassengerCount")

            launchIrctcApp()

            // 6️⃣ Send Broadcast
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val intent = Intent(ACTION_START_AUTOMATION).apply {                        
                        setPackage(packageName)
                        putExtra("train", train)
                        putExtra("class", etClass.text.toString().trim())
                        putExtra("latency", latencyMs)
                        putExtra("passengers", validPassengerCount)
                    }
                    sendBroadcast(intent)
                    Log.d(TAG, "📡 Broadcast sent: ACTION_START_AUTOMATION")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Broadcast failed: ${e.message}", e)
                }
            }, 1500)

        } catch (e: Exception) {
            Log.e(TAG, "❌ handleSaveAndFire error: ${e.message}", e)
            updateStatus("❌ Error: ${e.message?.take(40)}", android.R.color.holo_red_dark)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🚀 APP LAUNCH & UTILS
    // ═══════════════════════════════════════════════════════
    private fun launchIrctcApp() {
        val irctcPackages = listOf("cris.org.in.prs.ima", "com.irctc.railconnect", "in.irctc.railconnect")
        for (pkg in irctcPackages) {
            try {
                packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    return 
                }
            } catch (e: Exception) { }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=cris.org.in.prs.ima")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().contains(packageName, ignoreCase = true)) return true
            }
            false
        } catch (e: Exception) { false }
    }
    
    private fun updateStatus(message: String, colorRes: Int) {
        tvProfileStatus.text = message
        tvProfileStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🔌 MainActivity destroyed")
    }

    // ✅ HELPER DATA CLASS
    data class PassengerInput(
        val name: EditText,
        val age: EditText,
        val gender: EditText,
        val meal: EditText
    )
}
