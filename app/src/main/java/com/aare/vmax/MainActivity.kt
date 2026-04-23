package com.aare.vmax

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// ✅ पुरानी बनी हुई PassengerData क्लास को ही इस्तेमाल करेंगे (Duplicate नहीं बनेगा)
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
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        loadSavedData()
        setupClickListeners()
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
    
    private fun loadPassengerData(index: Int, nameView: EditText, ageView: EditText, genderView: EditText, prefs: SharedPreferences) {
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
        
        // 💾 सेव करते ही Orchestrator खुद इसे पढ़ लेगा, किसी Holder की ज़रूरत नहीं!
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
        
        tvProfileStatus.text = "✅ Saved! ${passengerList.size} passenger(s)"
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
    
    private fun addPassengerIfValid(list: MutableList<PassengerData>, nameView: EditText, ageView: EditText, genderView: EditText) {
        val name = nameView.text.toString().trim()
        val ageText = ageView.text.toString().trim()
        val gender = genderView.text.toString().trim()
        
        if (name.isNotEmpty() && ageText.isNotEmpty() && gender.isNotEmpty()) {
            // ✅ Age को String में ही रखा है ताकि Orchestrator बिना क्रैश हुए पढ़ सके
            list.add(PassengerData(name, ageText, gender))
        }
    }
    
    private fun startIrctcAutomation() {
        val trainNumber = etTrainNumber.text.toString().trim()
        if (trainNumber.isEmpty()) {
            tvProfileStatus.text = "❌ Please save profile first!"
            return
        }
        
        // ✅ स्मार्ट सर्विस चेकर (बिना Import एरर के)
        if (!isAccessibilityServiceEnabled()) {
            tvProfileStatus.text = "⚠️ Enable Accessibility Service in Settings"
            tvProfileStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            openAccessibilitySettings()
            return
        }
        
        tvProfileStatus.text = "🚀 Opening IRCTC..."
        tvProfileStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        
        openIrctcApp()
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        // पैकेज नाम से चेक करेगा, जिससे सर्विस का Import नहीं देना पड़ेगा
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(packageName) == true
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    private fun openIrctcApp() {
        val irctcPackages = listOf(
            "cris.org.in.prs.ima",
            "com.irctc.railconnect",
            "in.irctc.railconnect"
        )
        
        var launched = false
        for (pkg in irctcPackages) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    startActivity(intent)
                    launched = true
                    tvProfileStatus.text = "🚂 IRCTC App Opened!"
                    break
                }
            } catch (e: Exception) { }
        }
        
        if (!launched) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.irctc.co.in"))
            startActivity(webIntent)
            tvProfileStatus.text = "🌐 Opening IRCTC Website"
        }
    }
}
