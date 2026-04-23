package com.aare.vmax

import android.app.Activity // ✅ AppCompatActivity को हटाकर इसे डाल दिया है
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
import com.aare.vmax.core.orchestrator.PassengerData
import java.util.Calendar

// ✅ यहाँ भी बदलाव कर दिया है
class MainActivity : Activity() {

    // ═══════════════════════════════════════════════════════
    // 📱 UI COMPONENTS
    // ═══════════════════════════════════════════════════════
    
    private lateinit var etTrainNumber: EditText
    private lateinit var etClass: EditText
    private lateinit var etLatency: EditText
    private lateinit var btnSaveProfile: Button
    private lateinit var btnStartIrctc: Button
    private lateinit var tvProfileStatus: TextView

    private val passengerFields = mutableListOf<PassengerFieldSet>()

    private var isAutomationReady = false
    private lateinit var prefs: SharedPreferences

    // ═══════════════════════════════════════════════════════
    // ⚙️ CONSTANTS
    // ═══════════════════════════════════════════════════════
    
    companion object {
        private const val PREFS_NAME = "VMaxProfile"
        private const val SERVICE_CLASS_NAME = "com.aare.vmax.VMAXAccessibilityService"
        
        private const val DEFAULT_LATENCY_MS = 400
        private const val MIN_LATENCY_MS = 50
        private const val MAX_LATENCY_MS = 2000
        
        private const val MAX_PASSENGERS = 6
        
        private const val MIN_NAME_LENGTH = 4
        private const val MAX_NAME_LENGTH = 16
        
        private const val MIN_AGE = 1
        private const val MAX_AGE = 199
    }

    // ═══════════════════════════════════════════════════════
    // 🔄 LIFECYCLE METHODS
    // ═══════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initViews()
        setupPickers()
        loadSavedData()
        setupClickListeners()

        Handler(Looper.getMainLooper()).postDelayed({
            checkBatteryOptimization()
            handleFirstTimeSetup()
        }, 500)
    }

    override fun onResume() {
        super.onResume()
        if (isAccessibilityServiceEnabled()) {
            showStatus("✅ VMAX Service Active", android.R.color.holo_green_dark)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🎨 VIEW INITIALIZATION
    // ═══════════════════════════════════════════════════════
    
    private fun initViews() {
        etTrainNumber = findViewById(R.id.etTrainNumber)
        etClass = findViewById(R.id.etClass)
        etLatency = findViewById(R.id.etLatency)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        btnStartIrctc = findViewById(R.id.btnStartIrctc)
        tvProfileStatus = findViewById(R.id.tvProfileStatus)

        passengerFields.addAll(
            listOf(
                PassengerFieldSet(findViewById(R.id.etName1), findViewById(R.id.etAge1), findViewById(R.id.etGender1)),
                PassengerFieldSet(findViewById(R.id.etName2), findViewById(R.id.etAge2), findViewById(R.id.etGender2)),
                PassengerFieldSet(findViewById(R.id.etName3), findViewById(R.id.etAge3), findViewById(R.id.etGender3)),
                PassengerFieldSet(findViewById(R.id.etName4), findViewById(R.id.etAge4), findViewById(R.id.etGender4))
            )
        )
    }

    private fun setupPickers() {
        passengerFields.forEach { it.setupGenderPicker(this) }
        etClass.setupClassPicker(this)
    }

    // ═══════════════════════════════════════════════════════
    // 💾 DATA HANDLING
    // ═══════════════════════════════════════════════════════

    private fun loadSavedData() {
        try {
            etTrainNumber.setText(prefs.getString("train", ""))
            etClass.setText(prefs.getString("class", "SL"))

            val savedLatency = prefs.getInt("latency_ms", DEFAULT_LATENCY_MS)
            etLatency.setText(savedLatency.coerceIn(MIN_LATENCY_MS, MAX_LATENCY_MS).toString())

            passengerFields.forEachIndexed { index, fields ->
                fields.loadFromPrefs(prefs, index)
            }

        } catch (e: Exception) {
            showStatus("⚠️ Data load error", ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        }
    }

    private fun setupClickListeners() {
        btnSaveProfile.setOnClickListener { handleSaveProfile() }
        btnStartIrctc.setOnClickListener { handleStartAutomation() }
    }

    // ═══════════════════════════════════════════════════════
    // ✅ SAVE PROFILE LOGIC
    // ═══════════════════════════════════════════════════════

    private fun handleSaveProfile() {
        val trainNumber = etTrainNumber.text.toString().trim()
        when (val trainResult = validateTrainNumber(trainNumber)) {
            is ValidationResult.Error -> {
                etTrainNumber.error = trainResult.message
                showStatus("❌ ${trainResult.message}", android.R.color.holo_red_dark)
                return
            }
            else -> {}
        }

        val passengers = collectValidPassengers()
        when {
            passengers.isEmpty() -> {
                showStatus("❌ Add at least 1 passenger", android.R.color.holo_red_dark)
                return
            }
            passengers.size > MAX_PASSENGERS -> {
                showStatus("❌ Max $MAX_PASSENGERS passengers allowed", android.R.color.holo_red_dark)
                return
            }
        }

        val latencyMs = etLatency.text.toString().trim()
            .toIntOrNull()
            ?.coerceIn(MIN_LATENCY_MS, MAX_LATENCY_MS)
            ?: DEFAULT_LATENCY_MS

        saveToPreferences(trainNumber, latencyMs, passengers)

        showStatus(
            "✅ Saved! ${passengers.size} passenger(s) | ${latencyMs}ms",
            android.R.color.holo_green_dark
        )
        isAutomationReady = true
    }

    private fun validateTrainNumber(number: String): ValidationResult {
        return when {
            number.isEmpty() -> ValidationResult.Error("Enter train number")
            !number.matches(Regex("^\\d{5}$")) -> ValidationResult.Error("Must be 5 digits")
            else -> ValidationResult.Success
        }
    }

    private fun collectValidPassengers(): MutableList<PassengerData> {
        val passengers = mutableListOf<PassengerData>()

        for (fields in passengerFields) {
            val name = fields.nameEt.text.toString().trim()
            val ageText = fields.ageEt.text.toString().trim()
            val gender = fields.genderEt.text.toString().trim()

            if (name.isEmpty() && ageText.isEmpty() && gender.isEmpty()) continue

            when (val result = validatePassenger(name, ageText, gender, fields)) {
                is ValidationResult.Error -> return mutableListOf()
                is ValidationResult.Success -> {
                    passengers.add(PassengerData(name, ageText, gender))
                    fields.clearErrors()
                }
            }
        }
        return passengers
    }

    private fun validatePassenger(
        name: String,
        ageText: String,
        gender: String,
        fields: PassengerFieldSet
    ): ValidationResult {

        if (name.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
            fields.nameEt.error = "नाम $MIN_NAME_LENGTH-$MAX_NAME_LENGTH अक्षर का होना चाहिए"
            fields.nameEt.requestFocus()
            return ValidationResult.Error("Invalid name")
        }

        val age = ageText.toIntOrNull()
        if (age == null || age !in MIN_AGE..MAX_AGE) {
            fields.ageEt.error = "उम्र $MIN_AGE-$MAX_AGE के बीच होनी चाहिए"
            fields.ageEt.requestFocus()
            return ValidationResult.Error("Invalid age")
        }

        if (gender !in listOf("Male", "Female", "Transgender")) {
            fields.genderEt.error = "Select valid gender"
            fields.genderEt.requestFocus()
            return ValidationResult.Error("Invalid gender")
        }

        return ValidationResult.Success
    }

    private fun saveToPreferences(
        trainNumber: String,
        latencyMs: Int,
        passengers: List<PassengerData>
    ) {
        prefs.edit().apply {
            putString("train", trainNumber)
            putString("class", etClass.text.toString().trim().ifEmpty { "SL" })
            putInt("latency_ms", latencyMs)
            putInt("passenger_count", passengers.size)

            passengers.forEachIndexed { index, passenger ->
                putString("name_$index", passenger.name)
                putString("age_$index", passenger.age)
                putString("gender_$index", passenger.gender)
            }
            putBoolean("settings_guide_shown", true)
        }.apply()
    }

    // ═══════════════════════════════════════════════════════
    // 🚀 AUTOMATION LOGIC (NO DELAY!)
    // ═══════════════════════════════════════════════════════

    private fun handleStartAutomation() {
        
        if (prefs.getString("train", "").isNullOrEmpty()) {
            showStatus("❌ Please save profile first!", android.R.color.holo_red_dark)
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            showStatus("⚠️ Enable VMAX in Accessibility", android.R.color.holo_orange_dark)
            openAccessibilitySettings()
            return
        }

        val config = AutomationConfig(
            trainNumber = prefs.getString("train", "")!!,
            bookingClass = prefs.getString("class", "SL")!!,
            latencyMs = prefs.getInt("latency_ms", DEFAULT_LATENCY_MS),
            passengerCount = prefs.getInt("passenger_count", 0)
        )

        displayTargetTime(config)

        // ✅ सीधा लॉन्च! कोई Delay नहीं। रॉकेट की तरह उड़ेगा।
        val intent = Intent("com.aare.vmax.ACTION_START_AUTOMATION")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        
        launchIrctcApp()
    }

    private fun displayTargetTime(config: AutomationConfig) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val targetTime = when {
            currentHour == 7 -> "08:00 AM (Normal)"
            currentHour in 8..9 || config.bookingClass in listOf("1A", "2A", "3A", "3E", "CC") -> "10:00 AM (AC Tatkal)"
            else -> "11:00 AM (SL Tatkal)"
        }

        showStatus("🎯 $targetTime\n⏱️ ${config.latencyMs}ms\n🚀 Firing instantly!", android.R.color.holo_blue_dark)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val service = splitter.next()
            if (service.equals(SERVICE_CLASS_NAME, ignoreCase = true) || service.contains(packageName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun launchIrctcApp() {
        val packages = listOf(
            "cris.org.in.prs.ima",
            "com.irctc.railconnect",
            "in.irctc.railconnect"
        )

        for (pkg in packages) {
            try {
                packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    showStatus("🚂 IRCTC Launched!", android.R.color.holo_green_dark)
                    return
                }
            } catch (e: Exception) {
                continue
            }
        }
        showIrctcInstallDialog()
    }

    // ═══════════════════════════════════════════════════════
    // ⚡ SYSTEM OPTIMIZATION
    // ═══════════════════════════════════════════════════════

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showStatus("⚠️ Disable battery optimization", android.R.color.holo_orange_dark)
                Handler(Looper.getMainLooper()).postDelayed({
                    requestIgnoreBatteryOptimizations()
                }, 1500)
            }
        }
    }

    private fun handleFirstTimeSetup() {
        if (!prefs.getBoolean("settings_guide_shown", false)) {
            showDisclaimerDialog()
            prefs.edit().putBoolean("settings_guide_shown", true).apply()
        }
    }

    private fun showStatus(message: String, colorRes: Int) {
        tvProfileStatus.text = message
        tvProfileStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun showDisclaimerDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("⚠️ Legal Disclaimer")
            .setMessage(
                "VMAX Pro is an assistive tool.\n\n" +
                        "• No guarantee of ticket confirmation\n" +
                        "• User responsible for IRCTC ToS compliance\n" +
                        "• Automated booking may violate policies\n" +
                        "• Use at your own risk\n\n" +
                        "By continuing, you accept these terms."
            )
            .setPositiveButton("I Understand") { _, _ -> 
                showOptimizationGuide()
            }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showOptimizationGuide() {
        val message = DeviceOptimizationHelper.getOptimizationInstructions(this)

        android.app.AlertDialog.Builder(this)
            .setTitle("⚡ Keep VMAX Running")
            .setMessage(message)
            .setPositiveButton("Got it!") { _, _ ->
                try {
                    startActivity(Intent("miui.intent.action.OP_AUTO_START").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    openAccessibilitySettings()
                }
            }
            .show()
    }

    private fun showIrctcInstallDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("IRCTC App Required")
            .setMessage("Please install IRCTC Rail Connect to continue.")
            .setPositiveButton("Install") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=cris.org.in.prs.ima")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=cris.org.in.prs.ima")))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Please enable Accessibility Service manually", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            // Silently fail
        }
    }
}

// ═══════════════════════════════════════════════════════
// 🧩 HELPER CLASSES & UTILS
// ═══════════════════════════════════════════════════════

data class PassengerFieldSet(
    val nameEt: EditText,
    val ageEt: EditText,
    val genderEt: EditText
) {
    fun setupGenderPicker(context: Context) {
        genderEt.isFocusable = false
        genderEt.setOnClickListener {
            val options = arrayOf("Male", "Female", "Transgender")
            android.app.AlertDialog.Builder(context)
                .setTitle("Select Gender")
                .setItems(options) { _, which -> genderEt.setText(options[which]) }
                .show()
        }
    }

    fun loadFromPrefs(prefs: SharedPreferences, index: Int) {
        nameEt.setText(prefs.getString("name_$index", ""))
        ageEt.setText(prefs.getString("age_$index", ""))
        genderEt.setText(prefs.getString("gender_$index", ""))
    }

    fun clearErrors() {
        nameEt.error = null
        ageEt.error = null
        genderEt.error = null
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

data class AutomationConfig(
    val trainNumber: String,
    val bookingClass: String,
    val latencyMs: Int,
    val passengerCount: Int
)

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

object DeviceOptimizationHelper {
    fun getOptimizationInstructions(context: Context): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                "📍 Settings → Apps → VMAX Pro → Autostart → Enable\n" +
                "📍 Settings → Battery → App battery saver → No restrictions\n" +
                "📍 Security App → Permissions → Autostart → Enable"

            manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                "📍 Settings → Apps → App management → VMAX Pro → Autostart → Enable\n" +
                "📍 Settings → Battery → Optimize battery → VMAX Pro → Don't optimize\n" +
                "📍 Recent Apps → Lock VMAX Pro"

            manufacturer.contains("vivo") ->
                "📍 Settings → More settings → Permissions → Autostart → Enable\n" +
                "📍 Settings → Battery → Background power management → Allow\n" +
                "📍 i Manager → App manager → VMAX Pro → Lock"

            manufacturer.contains("samsung") ->
                "📍 Settings → Apps → VMAX Pro → Battery → Unrestricted\n" +
                "📍 Settings → Device care → Battery → Background usage limits → Never sleeping\n" +
                "📍 Recent Apps → Long press VMAX → Lock"

            else ->
                "📍 Settings → Apps → VMAX Pro → Battery → Unrestricted\n" +
                "📍 Recent Apps → Long press VMAX Pro → Lock the app\n" +
                "📍 Keep app open in background before Tatkal time"
        }
    }
}
