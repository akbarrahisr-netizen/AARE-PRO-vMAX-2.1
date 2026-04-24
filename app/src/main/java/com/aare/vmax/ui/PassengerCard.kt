package com.aare.vmax.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aare.vmax.core.model.PassengerData

@Composable
fun PassengerCard(index: Int, passenger: PassengerData) {
    // 💜 यही वो शानदार बैंगनी बॉर्डर वाला डब्बा है
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .border(2.dp, Color(0xFF8A2BE2), RoundedCornerShape(12.dp)), // नियॉन बैंगनी बॉर्डर
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)) // प्रीमियम डार्क थीम
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PASSENGER $index",
                color = Color(0xFF8A2BE2),
                fontWeight = FontWeight.Bold
            )
            // यहाँ नाम और उम्र के इनपुट आएंगे
            Text("Name: ${passenger.name}", color = Color.White)
            Text("Age: ${passenger.age}", color = Color.White)
        }
    }
}
