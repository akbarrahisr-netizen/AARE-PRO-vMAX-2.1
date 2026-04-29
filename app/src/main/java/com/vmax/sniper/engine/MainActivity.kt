package com.vmax.sniper

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.vmax.sniper.engine.WorkflowEngine
import com.vmax.sniper.engine.TimeSniper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ActivityCompat.requestPermissions(this, 
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 101)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    VmaxVIPScreen()
                }
            }
        }
    }
}

data class PassengerData(
    val name: String = "", val age: String = "", val gender: String = "", 
    val berth: String = "", val meal: String = "", val childName: String = "", 
    val childAge: String = "", val childGender: String = ""
)

// ✅ उस्ताद की स्पेशल फिक्स: Accessibility Check
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, WorkflowEngine::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(expectedComponentName.flattenToString())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmaxVIPScreen() {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("VMAX_DATA", Context.MODE_PRIVATE)
    
    var trainNumber by remember { mutableStateOf(sharedPrefs.getString("TRAIN_NO", "") ?: "") }
    var latency by remember { mutableStateOf(sharedPrefs.getString("LATENCY", "400") ?: "400") }
    var paymentMethod by remember { mutableStateOf(sharedPrefs.getString("PAYMENT", "UPI") ?: "UPI") }
    var selectedClass by remember { mutableStateOf(sharedPrefs.getString("TARGET_CLASS", "SL") ?: "SL") }
    val classes = listOf("1A", "2A", "3A", "CC", "3E", "EC", "SL", "FC", "2S", "VS", "VC", "EV")
    
    val passengers = remember { mutableStateListOf<PassengerData>() }
    var isSniperRunning by remember { mutableStateOf(WorkflowEngine.isSniperActive) }
    
    LaunchedEffect(Unit) {
        if (passengers.isEmpty()) repeat(4) { passengers.add(PassengerData()) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🦅 VMAX SNIPER VIP", color = Color(0xFF7E57C2), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("USTAD EDITION - ZERO DELAY", color = Color.Gray, fontSize = 12.sp)
        
        Spacer(modifier = Modifier.height(20.dp))

        // 🚂 TRAIN & NETWORK CARD
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🚂 TRAIN & NETWORK", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = trainNumber, onValueChange = { trainNumber = it }, label = { Text("Train Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = latency, onValueChange = { latency = it }, label = { Text("Latency Offset (ms)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
            Text("⚙️ ACCESSIBILITY चालू करें")
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                passengers[0] = PassengerData("Md Akbar", "27", "Male", "Lower", "Veg", "", "", "")
                passengers[1] = PassengerData("Md Ilahi", "29", "Male", "Lower", "", "", "", "")
                passengers[2] = PassengerData("Raja Roy", "43", "Male", "Upper", "NonVeg", "", "", "")
                passengers[3] = PassengerData("Soni", "34", "Female", "Middle", "", "", "", "")
                Toast.makeText(context, "⚡ Profile Loaded!", Toast.LENGTH_SHORT).show()
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("🚀 QUICK FILL") }
            Button(onClick = { passengers.indices.forEach { passengers[it] = PassengerData() } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))) { Text("🗑️ CLEAR") }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 🎯 TARGET CLASS
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🎯 TARGET CLASS", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(value = selectedClass, onValueChange = {}, readOnly = true, label = { Text("Select Class") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        classes.forEach { className -> DropdownMenuItem(text = { Text(className) }, onClick = { selectedClass = className; expanded = false }) }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("👥 PASSENGERS DETAILS", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
        passengers.forEachIndexed { index, p ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = p.name, onValueChange = { passengers[index] = p.copy(name = it) }, label = { Text("Name") }, modifier = Modifier.weight(2f), singleLine = true)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = p.age, onValueChange = { passengers[index] = p.copy(age = it) }, label = { Text("Age") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = p.gender, onValueChange = { passengers[index] = p.copy(gender = it) }, label = { Text("Gen (M/F)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = p.berth, onValueChange = { passengers[index] = p.copy(berth = it) }, label = { Text("Berth") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = p.meal, onValueChange = { passengers[index] = p.copy(meal = it) }, label = { Text("Meal") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        val targetHour = if (classes.indexOf(selectedClass) < 6) 10 else 11
        Text(text = "🎯 SNIPER FIRING AT $targetHour:00:00", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))

        if (isSniperRunning) {
            Button(onClick = { WorkflowEngine.isSniperActive = false; isSniperRunning = false }, modifier = Modifier.fillMaxWidth().height(65.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                Text("🛑 STOP SNIPER", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        } else {
            Button(
                onClick = {
                    // ✅ Security Check 1: Accessibility On है या नहीं?
                    if (!isAccessibilityServiceEnabled(context)) {
                        Toast.makeText(context, "⚠️ पहले Accessibility Service चालू करें!", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    val validPassengers = passengers.filter { it.name.isNotBlank() }
                    if (validPassengers.isEmpty()) {
                        Toast.makeText(context, "उस्ताद, कम से कम 1 नाम तो डालिए!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val formattedString = validPassengers.joinToString(",") { "${it.name}|${it.age}|${it.gender}|${it.berth}|${it.meal}|${it.childName}|${it.childAge}|${it.childGender}" }
                    
                    // ✅ Option C: सब कुछ सेव हो रहा है
                    sharedPrefs.edit().apply {
                        putString("PASSENGER_LIST", formattedString)
                        putString("TRAIN_NO", trainNumber)
                        putString("LATENCY", latency)
                        putString("PAYMENT", paymentMethod)
                        putString("TARGET_CLASS", selectedClass)
                    }.apply()

                    // ✅ Option B: TimeSniper को एक्टिवेट करना
                    TimeSniper.scheduleFire(targetHour, latency.toLongOrNull() ?: 400L) {
                        WorkflowEngine.isSniperActive = true
                        WorkflowEngine.isProcessing = false
                    }
                    isSniperRunning = true
                    Toast.makeText(context, "🚀 SNIPER ARMED for $targetHour:00!", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth().height(65.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) { Text("🔥 ARM ZERO-DELAY SNIPER", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}
