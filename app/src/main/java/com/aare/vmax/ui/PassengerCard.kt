package com.aare.vmax.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.ui.theme.VMaxColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerCard(
    passenger: PassengerData,
    passengerIndex: Int,
    onUpdate: (PassengerData) -> Unit,
    onRemove: () -> Unit, // ✅ रिमूव फंक्शन जोड़ा
    modifier: Modifier = Modifier
) {
    val colors = VMaxColors.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.5.dp, colors.accent, RoundedCornerShape(16.dp))
            .background(colors.cardBg, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("👤 Passenger ${passengerIndex + 1}", color = colors.accent)
            if (passengerIndex > 0) { // कम से कम 1 पैसेंजर रहना चाहिए
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "Remove", tint = colors.error)
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // ✍️ Name & Age
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = passenger.name,
                onValueChange = { onUpdate(passenger.copy(name = it)) },
                label = { Text("Name") },
                modifier = Modifier.weight(0.6f)
            )
            OutlinedTextField(
                value = passenger.age,
                onValueChange = { onUpdate(passenger.copy(age = it)) },
                label = { Text("Age") },
                modifier = Modifier.weight(0.4f)
            )
        }

        // 🎯 Gender & Berth (Dropdowns)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IRCTCDropdown(
                label = "Berth",
                options = listOf("LOWER", "UPPER", "MIDDLE", "SIDE LOWER", "SIDE UPPER"),
                selectedOption = passenger.berthPreference,
                onSelectionChange = { onUpdate(passenger.copy(berthPreference = it)) },
                modifier = Modifier.weight(0.5f)
            )
            IRCTCDropdown(
                label = "Meal",
                options = listOf("No Food", "VEG", "NON VEG"),
                selectedOption = passenger.meal,
                onSelectionChange = { onUpdate(passenger.copy(meal = it)) },
                modifier = Modifier.weight(0.5f)
            )
        }
        
        // ✅ Bed Roll Checkbox
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = passenger.bedRoll, onCheckedChange = { onUpdate(passenger.copy(bedRoll = it)) })
            Text("Bed Roll Required", color = colors.onField)
        }
    }
}

// यहाँ IRCTCDropdown और ReadOnlyField का कोड पहले जैसा ही रहेगा
