package com.vmax.sniper

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedQuota by remember { mutableStateOf("Tatkal") }
    val passengers = remember { mutableStateListOf<PassengerData>() }
    
    LaunchedEffect(Unit) {
        if (passengers.isEmpty()) {
            repeat(4) { passengers.add(PassengerData(name = "", age = "")) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎯 VMAX Sniper Pro", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Quota Selection
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("📋 Booking Type", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedQuota == "Tatkal",
                        onClick = { selectedQuota = "Tatkal" },
                        label = { Text("Tatkal (11:00 AM)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD32F2F)
                        )
                    )
                    FilterChip(
                        selected = selectedQuota == "Premium",
                        onClick = { selectedQuota = "Premium" },
                        label = { Text("Premium (10:00 AM)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD32F2F)
                        )
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Passenger List Title
        Text("👥 Passengers", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
        
        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(passengers) { index, p ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${index + 1}.",
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        OutlinedTextField(
                            value = p.name,
                            onValueChange = { passengers[index] = p.copy(name = it) },
                            label = { Text("Name", color = Color.Gray) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFFFF9800),
                                focusedLabelColor = Color(0xFFFF9800)
                            )
                        )
                        OutlinedTextField(
                            value = p.age,
                            onValueChange = { passengers[index] = p.copy(age = it) },
                            label = { Text("Age", color = Color.Gray) },
                            modifier = Modifier.width(70.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFFFF9800),
                                focusedLabelColor = Color(0xFFFF9800)
                            )
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // Instruction Text
        val targetHour = if (selectedQuota == "Tatkal") 11 else 10
        val loginTime = if (targetHour == 11) "10:58" else "9:58"
        
        Text(
            text = "⚠️ $loginTime AM पर IRCTC मे लॉगिन करके पैसेंजर स्क्रीन पर तैयार रहें!",
            color = Color(0xFFFF9800),
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 🔥 THE DEADLY MERGE BUTTON
        Button(
            onClick = {
                val nameString = passengers.map { it.name }.filter { it.isNotBlank() }.joinToString(",")
                if (nameString.isEmpty()) {
                    Toast.makeText(context, "उस्ताद, कम से कम एक पैसेंजर का नाम तो डालिए!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                // Save data
                val sharedPrefs = context.getSharedPreferences("VMAX_DATA", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("PASSENGER_LIST", nameString).apply()

                // Set target class based on quota
                WorkflowEngine.targetClass = if (selectedQuota == "Tatkal") "SL" else "3A"

                // Schedule sniper
                TimeSniper.prepareSniper()
                TimeSniper.scheduleFire(targetHour, 0) {
                    WorkflowEngine.isSniperActive = true
                }
                
                Toast.makeText(context, "🎯 SNIPER LOCKED! ${if(selectedQuota == "Tatkal") "SL @ 11:00" else "3A @ 10:00"} पर वार होगा!", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            Text("🔥 ARM ZERO-DELAY SNIPER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

data class PassengerData(val name: String = "", val age: String = "")
