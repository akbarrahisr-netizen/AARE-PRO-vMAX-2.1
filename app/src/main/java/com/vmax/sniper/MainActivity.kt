package com.vmax.sniper

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.vmax.sniper.engine.TimeSniper
import com.vmax.sniper.engine.WorkflowEngine

/**
 * 🦅 VMAX PRO SNIPER - DASHBOARD (Final Version)
 * उस्ताद Md Akbar की "Zero-Delay" विधि पर आधारित।
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etPassengers = findViewById<EditText>(R.id.etPassengers)
        val spinnerClass = findViewById<Spinner>(R.id.spinnerClass)
        val btnActivate = findViewById<Button>(R.id.btnActivate)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvInstruction = findViewById<TextView>(R.id.tvInstruction)

        // तत्काल की 12 क्लासेस
        val classes = arrayOf("1A", "2A", "3A", "CC", "3E", "EC", "SL", "FC", "2S", "VS", "VC", "EV")
        spinnerClass.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, classes)

        btnActivate.setOnClickListener {
            val names = etPassengers.text.toString().trim()
            val targetClass = spinnerClass.selectedItem.toString()

            if (names.isEmpty()) {
                Toast.makeText(this, "उस्ताद, नाम तो डालिए!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 💾 पैसेंजर डेटा को तिजोरी (SharedPreferences) में सेव करना
            val sharedPrefs = getSharedPreferences("VMAX_DATA", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("PASSENGER_LIST", names).apply()

            // इंजन को टारगेट क्लास बताना
            WorkflowEngine.targetClass = targetClass
            
            // स्मार्ट टाइमिंग: AC (10 AM) या Sleeper (11 AM)
            val classPosition = spinnerClass.selectedItemPosition
            val targetHour = if (classPosition < 6) 10 else 11
            
            // UI अपडेट - लाल रंग यानी स्नाइपर लोडेड है!
            tvStatus.text = "🎯 SNIPER ARMED: $targetClass at $targetHour:00:00"
            tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_light))

            tvInstruction.text = """
                🚀 उस्ताद का बैटल प्लान:
                1. ${targetHour-1}:58 पर लॉगिन कर लें।
                2. पैसेंजर स्क्रीन (Add New) पर पहुँच जाएँ।
                3. बस शांति से इंतज़ार करें...
                🔥 स्नाइपर ठीक ${targetHour}:00:00.000 पर वार करेगा!
            """.trimIndent()

            // ⏰ परमाणु घड़ी (NTP) सिंक की तैयारी
            TimeSniper.prepareSniper()
            
            // 🔥 सटीक समय पर वार (ZERO DELAY विधि)
            TimeSniper.scheduleFire(targetHour, 0) {
                // उस्ताद पहले से ही IRCTC स्क्रीन पर हैं, इसलिए सीधा हमला!
                WorkflowEngine.isSniperActive = true
                
                runOnUiThread {
                    tvStatus.text = "🔥🔥🔥 FIRE! SNIPER ACTIVE AT $targetHour:00:00"
                    tvStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
                    Toast.makeText(this@MainActivity, "FIRE! 🔥 Assault Started", Toast.LENGTH_SHORT).show()
                }
            }
            
            Toast.makeText(this, "✅ $targetClass LOCKED! ${targetHour}:00 पर तैयार रहें!", Toast.LENGTH_LONG).show()
        }
    }
}
