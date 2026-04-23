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
        private const val SERVICE_CLASS_NAME = "VMAXAccessibilityService"
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
    
    // ✅ FIXED: Age validation with error handling
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
            val age = ageText.toIntOrNull()
            if (age != null && age in 1..120) {
                list.add(PassengerData(name, ageText, gender))
                ageView.error = null  // Clear error if valid
            } else {
                ageView.error = "Enter valid age (1-120)"
                ageView.requestFocus()
            }
        }
    }
    
    private fun startIrctcAutomation() {
        val trainNumber = etTrainNumber.text.toString().trim()
        if (trainNumber.isEmpty()) {
            tvProfileStatus.text = "❌ Please save profile first! 🧐"
            return
        }
        
        // ✅ FIXED: More accurate service check
        if (!isAccessibilityServiceEnabled()) {
            tvProfileStatus.text = "⚠️ Enable VMAX in Accessibility Settings"
            tvProfileStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            openAccessibilitySettings()
            return
        }
        
        tvProfileStatus.text = "🚀 IRCTC खुल रहा है... कमर कस लो उस्ताद! 🔥"
        tvProfileStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        openIrctcApp()
    }
    
    // ✅ FIXED: More accurate service check
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver, 
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        // Method 1: Exact service name check (if you want to import the service)
        // val serviceName = "$packageName/$SERVICE_CLASS_NAME"
        // return enabledServices.contains(serviceName)
        
        // Method 2: Safe check without import (still accurate enough)
        return enabledServices.contains(packageName) && 
               enabledServices.contains("AccessibilityService")
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    private fun openIrctcApp() {
        val irctcPackage = "cris.org.in.prs.ima" 
        try {
            val intent = packageManager.getLaunchIntentForPackage(irctcPackage)
            if (intent != null) {
                startActivity(intent)
                tvProfileStatus.text = "🚂 IRCTC App Opened! 🔥"
            } else {
                tvProfileStatus.text = "❌ IRCTC App NOT Installed!"
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$irctcPackage"))
                startActivity(playStoreIntent)
            }
        } catch (e: Exception) {
            tvProfileStatus.text = "❌ Error: ${e.message}"
        }
    }
}
