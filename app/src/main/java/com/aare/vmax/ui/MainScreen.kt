package com.aare.vmax.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.core.model.SniperTask
import com.aare.vmax.core.orchestrator.WorkflowEngine // ✅ सही इंजन का पता
import com.aare.vmax.ui.PassengerCard // ✅ components वाला एरर यहाँ फिक्स कर दिया है
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    
    // 🎯 कोटा, ट्रेन नंबर और क्लास का स्टेट
    var selectedQuota by remember { mutableStateOf("Tatkal") }
    var trainNumber by remember { mutableStateOf("12506") } // IRCTC वाला डिफ़ॉल्ट ट्रेन नंबर
    var travelClass by remember { mutableStateOf("3A") }    // SL, 3A, 2A
    
    var passengerList by remember { mutableStateOf(List(6) { PassengerData() }) }
    val displayCount = if (selectedQuota == "Tatkal") 4 else 6

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        
        // 🎯 ट्रेन और क्लास की सेटिंग
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = trainNumber,
                onValueChange = { trainNumber = it },
                label = { Text("Train No", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White)
            )
            OutlinedTextField(
                value = travelClass,
                onValueChange = { travelClass = it },
                label = { Text("Class", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 🎯 कोटा चुनने के बटन (4 या 6 डब्बे)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedQuota = "General" },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedQuota == "General") Color(0xFF8A2BE2) else Color.DarkGray),
                modifier = Modifier.weight(1f)
            ) { Text("GENERAL (6)") }

            Button(
                onClick = { selectedQuota = "Tatkal" },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedQuota == "Tatkal") Color(0xFF8A2BE2) else Color.DarkGray),
                modifier = Modifier.weight(1f)
            ) { Text("TATKAL (4)") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 💜 आपके प्रीमियम बैंगनी डब्बे
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(displayCount) { index ->
                PassengerCard(index = index + 1, passenger = passengerList[index])
            }
        }
        
        // 🔥 असली स्नाइपर बटन (अब यह इंजन को डेटा भेजेगा)
        Button(
            onClick = {
                // 1. सारा डेटा पैक करो
                val task = SniperTask(
                    taskId = UUID.randomUUID().toString(),
                    trainNumber = trainNumber,
                    travelClass = travelClass,
                    quota = selectedQuota,
                    passengers = passengerList.take(displayCount)
                )
                
                // 2. इंजन (WorkflowEngine) को आर्डर दो
                val intent = Intent(context, WorkflowEngine::class.java).apply {
                    action = "com.aare.vmax.ACTION_START"
                    putExtra("extra_task", task)
                }
                context.startService(intent) // स्नाइपर एक्टिवेट!
                
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("🔥 ARM THE SNIPER", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

