package com.aare.vmax

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aare.vmax.core.orchestrator.PassengerData

class MainActivity : AppCompatActivity() {
    
    private lateinit var etTrainNumber: EditText
    private lateinit var etClass: EditText
    private lateinit var etName1: EditText
    private lateinit var etAge1: EditText
    private lateinit var etGender1: EditText
    private lateinit var etName2: EditText
    private lateinit var etAge2: EditText
    private lateinit var etGender2: EditText
    private lateinit var etName3: EditText
    private lateinit var etAge3: EditText
    private lateinit var etGender3: EditText
    private lateinit var etName4: EditText
    private lateinit var etAge4: EditText
    private lateinit var etGender4: EditText
    private lateinit var btnSaveProfile: Button
    private lateinit var btnStartIrctc: Button
    private lateinit var tvProfileStatus: TextView
    
    companion object {
        private const val PREFS_NAME = "VMaxProfile"
        // ✅ Full path of the service class (Crucial for accurate check)
        private const val SERVICE_CLASS_NAME = "com.aare.vmax.VMAXAccessibilityService"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        loadSavedData()
        setupClickListeners()
        
        // ✅ नई बैटरी ऑप्टिमाइज़ेशन चेक करो
        checkAndRequestBatteryOptimization()
        
        // ✅ चाइनीज फोन्स के लिए ऑटो-स्टार्ट गाइड (पहली बार में)
        if (isFirstTimeUser()) {
            showSystemOptimizationGuide()
            markFirstTimeUserDone()
        }
    }
    
    private fun initViews() {
        etTrainNumber = findViewById(R.id.etTrainNumber)
        etClass = findViewById(R.id.etClass)
        
        etName1 = findViewById(R.id.etName1)
        etAge1 = findViewById(R.id.etAge1)
        etGender1 = findViewById(R.id.etGender1)
        
        etName2 = findViewById(R.id.etName2)
        etAge2 = findViewById(R.id.etAge2)
        etGender2 = findViewById(R.id.etGender2)
        
        etName3 = findViewById(R.id.etName3)
        etAge3 = findViewById(R.id.etAge3)
        etGender3 = findViewById(R.id.etGender3)
        
        etName4 = findViewById(R.id.etName4)
        etAge4 = findViewById(R.id.etAge4)
        etGender4 = findViewById(R.id.etGender4)
        
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        btnStartIrctc = findViewById(R.id.btnStartIrctc)
        tvProfileStatus = findViewById(R.id.tvProfileStatus)

        setupGenderPicker(etGender1)
        setupGenderPicker(etGender2)
        setupGenderPicker(etGender3)
        setupGenderPicker(etGender4)

        setupClassPicker(etClass)
    }

    private fun setupGenderPicker(editText: EditText) {
        editText.isFocusable = false
        editText.isFocusableInTouchMode = false
        editText.setOnClickListener {
            val options = arrayOf("Male", "Female", "Transgender")
            android.app.AlertDialog.Builder(this)
                .setTitle("Select Gender")
                .setItems(options) { _, which ->
                    editText.setText(options[which])
                }
                .show()
        }
    }

    private fun setupClassPicker(editText: EditText) {
        editText.isFocusable = false
        editText.isFocusableInTouchMode = false
        editText.setOnClickListener {
            val classes = arrayOf("SL", "3E", "3A", "2A", "1A", "2S", "CC")
            android.app.AlertDialog.Builder(this)
                .setTitle("Select Booking Class")
                .setItems(classes) { _, which ->
                    editText.setText(classes[which])
                }
                .show()
        }
    }
    
    private fun loadSavedData() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        etTrainNumber.setText(prefs.getString("train", ""))
        etClass.setText(prefs.getString("class", "SL"))
        
        loadPassengerData(0, etName1, etAge1, etGender1, prefs)
        loadPassengerData(1, etName2, etAge2, etGender2, prefs)
        loadPassengerData(2, etName3, etAge3, etGender3, prefs)
        loadPassengerData(3, etName4, etAge4, etGender4, prefs)
    }
    
    private fun loadPassengerData(
        index: Int, 
        nameView: EditText, 
        ageView: EditText, 
        genderView: EditText, 
        prefs: SharedPreferences
    ) {
        nameView.setText(prefs.getString("name_$index", ""))
        ageView.setText(prefs.getString("age_$index", ""))
        genderView.setText(prefs.getString("gender_$index", ""))
    }
    
    private fun setupClickListeners() {
        btnSaveProfile.setOnClickListener { saveProfile() }
        btnStartIrctc.setOnClickListener { startIrctcAutomation() }
    }
    
    private fun saveProfile() {
        val trainNumber = etTrainNumber.text.toString().trim()
        if (trainNumber.isEmpty()) {
            tvProfileStatus.text = "❌ Please enter Train Number"
            return
        }
        
        val passengerList = getPassengersList()
        if (passengerList.isEmpty()) {
            tvProfileStatus.text = "❌ Add at least 1 passenger"
            return
        }
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString("train", trainNumber)
            putString("class", etClass.text.toString().trim().ifEmpty { "SL" })
            putInt("passenger_count", passengerList.size)
            
            passengerList.forEachIndexed { index, passenger ->
                putString("name_$index", passenger.name)
                putString("age_$index", passenger.age)
                putString("gender_$index", passenger.gender)
            }
            apply()
        }
        tvProfileStatus.text = "✅ Saved! ${passengerList.size} passenger(s) 😁"
        tvProfileStatus.setTextColor(getColor(android.R.color.holo_green_dark))
    }
    
    private fun getPassengersList(): MutableList<PassengerData> {
        val passengers = mutableListOf<PassengerData>()
        addPassengerIfValid(passengers, etName1, etAge1, etGender1)
        addPassengerIfValid(passengers, etName2, etAge2, etGender2)
        addPassengerIfValid(passengers, etName3, etAge3, etGender3)
        addPassengerIfValid(passengers, etName4, etAge4, etGender4)
        return passengers
    }
    
    private fun addPassengerIfValid(
        list: MutableList<PassengerData>, 
        nameView: EditText, 
        ageView: EditText, 
        genderView: EditText
    ) {
        val name = nameView.text.toString().trim()
        val ageText = ageView.text.toString().trim()
        val gender = genderView.text.toString().trim()
        
        if (name.isNotEmpty() && ageText.isNotEmpty() && gender.isNotEmpty()) {
            // ✅ नाम की लंबाई (4-16)
            if (name.length < 4) {
                nameView.error = "नाम कम से कम 4 अक्षर का होना चाहिए"
                nameView.requestFocus()
                return
            }
            if (name.length > 16) {
                nameView.error = "नाम 16 अक्षर से ज्यादा नहीं होना चाहिए"
                nameView.requestFocus()
                return
            }

            // ✅ उम्र (1-199)
            val age = ageText.toIntOrNull()
            if (age == null || age < 1 || age > 199) {
                ageView.error = "उम्र 1 से 199 के बीच होनी चाहिए"
                ageView.requestFocus()
                return
            }

            // ✅ जेंडर
            val validGenders = listOf("Male", "Female", "Transgender", "M", "F")
            if (gender !in validGenders && !gender.startsWith("M", ignoreCase = true) && 
                !gender.startsWith("F", ignoreCase = true)) {
                genderView.error = "सही जेंडर चुनें (Male/Female/Transgender)"
                genderView.requestFocus()
                return
            }

            list.add(PassengerData(name, ageText, gender))
            nameView.error = null
            ageView.error = null
            genderView.error = null
        }
    }
    
    // ✅ बैटरी ऑप्टिमाइज़ेशन से छूट (सिस्टम से लड़ने का पहला हथियार)
    private fun checkAndRequestBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                tvProfileStatus.text = "⚠️ बैटरी ऑप्टिमाइज़ेशन बंद करें!"
                tvProfileStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
                
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
    
    // ✅ चाइनीज फोन्स के लिए ऑटो-स्टार्ट गाइड
    private fun requestAutoStartPermission() {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        
        when {
            manufacturer.contains("xiaomi") -> {
                try {
                    val intent = Intent("miui.intent.action.OP_AUTO_START")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    openAccessibilitySettings()
                }
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                try {
                    val intent = Intent()
                    intent.component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    openAccessibilitySettings()
                }
            }
            manufacturer.contains("vivo") -> {
                try {
                    val intent = Intent()
                    intent.component = android.content.ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    openAccessibilitySettings()
                }
            }
        }
    }
    
    // ✅ नए यूजर को सेटिंग्स बताने के लिए
    private fun isFirstTimeUser(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean("settings_guide_shown", false)
    }
    
    private fun markFirstTimeUserDone() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("settings_guide_shown", true).apply()
    }
    
    private fun showSystemOptimizationGuide() {
        val manufacturer = android.os.Build.MANUFACTURER
        val message = when {
            manufacturer.contains("Xiaomi", ignoreCase = true) -> 
                "📍 Settings → Apps → VMAX Pro → Autostart → Enable\n" +
                "📍 Settings → Battery → App battery saver → No restrictions"
            
            manufacturer.contains("Oppo", ignoreCase = true) || 
            manufacturer.contains("Realme", ignoreCase = true) -> 
                "📍 Settings → Apps → App management → VMAX Pro → Autostart → Enable\n" +
                "📍 Settings → Battery → Optimize battery → VMAX Pro → Don't optimize"
            
            manufacturer.contains("Vivo", ignoreCase = true) -> 
                "📍 Settings → More settings → Permissions → Autostart → Enable VMAX\n" +
                "📍 Settings → Battery → Background app restrictions → Allow"
            
            else -> 
                "📍 Settings → Apps → VMAX Pro → Battery → Unrestricted\n" +
                "📍 Recent Apps → Long press VMAX Pro → Lock the app"
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("⚡ IMPORTANT: System Optimization Settings")
            .setMessage(message)
            .setPositiveButton("Got it!") { _, _ -> 
                requestAutoStartPermission()
            }
            .setCancelable(true)
            .show()
    }
    
    private fun startIrctcAutomation() {
        val trainNumber = etTrainNumber.text.toString().trim()
        if (trainNumber.isEmpty()) {
            tvProfileStatus.text = "❌ Please save profile first! 🧐"
            return
        }
        
        if (!isAccessibilityServiceEnabled()) {
            tvProfileStatus.text = "⚠️ Enable VMAX in Accessibility Settings"
            tvProfileStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            openAccessibilitySettings()
            return
        }
        
        tvProfileStatus.text = "🚀 IRCTC खुल रहा है... अगर बंद हो जाए तो ऐप को 'लॉक' कर दें! 🔥"
        tvProfileStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        
        openIrctcApp()
    }
    
    // ✅ सटीक सर्विस चेक (पूरा रास्ता + SimpleStringSplitter)
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = SERVICE_CLASS_NAME
        val enabledServices = Settings.Secure.getString(
            contentResolver, 
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val splitter = android.text.TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        
        while (splitter.hasNext()) {
            val enabledService = splitter.next()
            if (enabledService.equals(expectedService, ignoreCase = true)) {
                return true
            }
            if (enabledService.contains(packageName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    // ✅ IRCTC लॉन्चर (3 पैकेज + फ्लैग्स + प्ले स्टोर फॉलबैक)
    private fun openIrctcApp() {
        val irctcPackages = arrayOf(
            "cris.org.in.prs.ima",      // असली IRCTC Rail Connect
            "com.irctc.railconnect",    // पुराना वर्जन
            "in.irctc.railconnect"      // बीटा वर्जन
        )
        
        var isAppLaunched = false
        val pm = packageManager

        for (pkg in irctcPackages) {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    
                    tvProfileStatus.text = "🚂 IRCTC ऐप मिल गया! हमला शुरू... 🔥"
                    tvProfileStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                    isAppLaunched = true
                    break
                }
            } catch (e: Exception) {
                // अगला पैकेज ट्राई करो
            }
        }

        if (!isAppLaunched) {
            tvProfileStatus.text = "❌ ऐप नहीं मिला! प्ले स्टोर जा रहे हैं..."
            tvProfileStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            
            try {
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=cris.org.in.prs.ima"))
                playStoreIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(playStoreIntent)
            } catch (e: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=cris.org.in.prs.ima"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(webIntent)
            }
        }
    }
}
