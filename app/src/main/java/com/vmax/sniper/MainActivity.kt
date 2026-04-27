package com.vmax.sniper

import android.Manifest
import android.content.Context
import android.os.Bundle
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
        
        // 🛡️ SMS Permissions - OTP ऑटो-रीड की तैयारी
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmaxVIPScreen() {
    val context = LocalContext.current
    
    // --- स्टेट्स (States) ---
    var trainNumber by remember { mutableStateOf("") }
    var latency by remember { mutableStateOf("400") }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var selectedClass by remember { mutableStateOf("SL") }
    val classes = listOf("1A", "2A", "3A", "CC", "3E", "EC", "SL", "FC", "2S", "VS", "VC", "EV")
    
    val passengers = remember { mutableStateListOf<PassengerData>() }
    
    // शुरुआती 4 खाली पैसेंजर लोड करना
    LaunchedEffect(Unit) {
        if (passengers.isEmpty()) repeat(4) { passengers.add(PassengerData()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🦅 VMAX SNIPER VIP", color = Color(0xFF7E57C2), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("USTAD EDITION - ZERO DELAY", color = Color.Gray, fontSize = 12.sp)
        
        Spacer(modifier = Modifier.height(20.dp))

        // 🚂 TRAIN & NETWORK CARD
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🚂 TRAIN & NETWORK", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = trainNumber, onValueChange = { trainNumber = it },
                    label = { Text("Train Number") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = latency, onValueChange = { latency = it },
                    label = { Text("Latency Offset (ms)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ⚡ QUICK FILL - (वही पैसेंजर लिस्ट जो आपको चाहिए)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = {
                    passengers[0] = PassengerData("Md Akbar", "27")
                    passengers[1] = PassengerData("Md Ilahi", "29")
                    passengers[2] = PassengerData("Raja Roy", "43")
                    passengers[3] = PassengerData("Soni", "34")
                    Toast.makeText(context, "⚡ Profile Loaded!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("🚀 QUICK FILL")
            }
            
            Button(
                onClick = { passengers.indices.forEach { passengers[it] = PassengerData() } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
            ) {
                Text("🗑️ CLEAR")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 🎯 TARGET CLASS
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🎯 TARGET CLASS", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedClass, onValueChange = {}, readOnly = true,
                        label = { Text("Select Class") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        classes.forEach { className ->
                            DropdownMenuItem(
                                text = { Text(className) },
                                onClick = { selectedClass = className; expanded = false }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 👥 PASSENGERS LIST
        Text("👥 PASSENGERS", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
        passengers.forEachIndexed { index, p ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}.", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = p.name, onValueChange = { passengers[index] = p.copy(name = it) },
                        label = { Text("Name") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = p.age, onValueChange = { passengers[index] = p.copy(age = it) },
                        label = { Text("Age") }, modifier = Modifier.width(75.dp), singleLine = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 💳 PAYMENT
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("💳 PAYMENT BOARD", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("UPI", "PhonePe", "Paytm").forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF00E676))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ⏰ TIMING CALCULATION
        val classPos = classes.indexOf(selectedClass)
        val targetHour = if (classPos < 6) 10 else 11
        
        Text(
            text = "🎯 SNIPER FIRING AT $targetHour:00:00.000",
            color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 🔥 THE ARM BUTTON
        Button(
            onClick = {
                val nameString = passengers.map { it.name }.filter { it.isNotBlank() }.joinToString(",")
                if (nameString.isEmpty()) {
                    Toast.makeText(context, "उस्ताद, नाम तो डालिए!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                // SharedPreferences में डेटा की पक्की एंट्री
                val sharedPrefs = context.getSharedPreferences("VMAX_DATA", Context.MODE_PRIVATE)
                sharedPrefs.edit().apply {
                    putString("PASSENGER_LIST", nameString)
                    putString("TRAIN_NO", trainNumber)
                    putString("LATENCY", latency)
                    putString("PAYMENT", paymentMethod)
                    putString("TARGET_CLASS", selectedClass)
                }.apply()

                // इंजन को अलर्ट करें (Fix for the build error)
                WorkflowEngine.targetClass = selectedClass

                // ⏰ परमाणु समय सिंक और लॉक
                TimeSniper.prepareSniper()
                TimeSniper.scheduleFire(targetHour, 0) {
                    WorkflowEngine.isSniperActive = true
                }
                
                Toast.makeText(context, "🎯 SNIPER ARMED: $selectedClass @ ${targetHour}:00 AM", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth().height(65.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            Text("🔥 ARM ZERO-DELAY SNIPER", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

data class PassengerData(val name: String = "", val age: String = "")
