package com.aare.vmax.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aare.vmax.core.model.PassengerData

@Composable
fun PassengerCard(index: Int, passenger: PassengerData) {
    // 💜 उस्ताद, यही है वो असली बैंगनी डब्बा जिसमें ६ ऑप्शंस फिट हैं
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .border(2.dp, Color(0xFF8A2BE2), RoundedCornerShape(12.dp)), // नियॉन बैंगनी बॉर्डर
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)) // डार्क थीम
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PASSENGER $index",
                color = Color(0xFF8A2BE2),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // १. नाम (Name) और २. उम्र (Age) के इनपुट बॉक्स
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = passenger.name,
                    onValueChange = { passenger.name = it },
                    label = { Text("Name", color = Color.Gray) },
                    modifier = Modifier.weight(2f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8A2BE2),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = passenger.age,
                    onValueChange = { passenger.age = it },
                    label = { Text("Age", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8A2BE2),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ३, ४, ५. जेंडर, बर्थ और मील (अभी टेक्स्ट में, बाद में ड्रॉपडाउन करेंगे)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚧ ${passenger.gender}", color = Color.LightGray, modifier = Modifier.weight(1f))
                Text("💺 ${passenger.berthPref}", color = Color.LightGray, modifier = Modifier.weight(1f))
                Text("🍱 ${passenger.mealPref}", color = Color.LightGray, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ६. बेड रोल (Bed Roll) - टिक वाला बटन
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = passenger.bedRoll,
                    onCheckedChange = { passenger.bedRoll = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF8A2BE2))
                )
                Text("Bed Roll Required", color = Color.White)
            }
        }
    }
}
