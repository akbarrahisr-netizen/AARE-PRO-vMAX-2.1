package com.aare.vmax.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.ui.components.PassengerCard

@Composable
fun MainScreen() {
    // १. कोटा का स्टेट (Default: General)
    var selectedQuota by remember { mutableStateOf("General") }
    
    // २. पैसेंजर लिस्ट का स्टेट (६ डब्बों का सांचा)
    var passengerList by remember { 
        mutableStateOf(List(6) { PassengerData() }) 
    }

    // ३. कोटा के हिसाब से संख्या तय करना (४ या ६)
    val displayCount = if (selectedQuota == "Tatkal") 4 else 6

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("SELECT QUOTA", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        
        // कोटा चुनने के बटन (General / Tatkal)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedQuota = "General" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedQuota == "General") Color(0xFF8A2BE2) else Color.DarkGray
                ),
                modifier = Modifier.weight(1f)
            ) { Text("GENERAL (6)") }

            Button(
                onClick = { selectedQuota = "Tatkal" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedQuota == "Tatkal") Color(0xFF8A2BE2) else Color.DarkGray
                ),
                modifier = Modifier.weight(1f)
            ) { Text("TATKAL (4)") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🎯 ४ या ६ बैंगनी डब्बे यहाँ दिखेंगे
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(displayCount) { index ->
                PassengerCard(
                    index = index + 1,
                    passenger = passengerList[index]
                )
            }
        }
        
        // बटन: ARM THE SNIPER
        Button(
            onClick = { /* इंजन चालू करने का लॉजिक यहाँ आएगा */ },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("🔥 ARM THE SNIPER", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}
