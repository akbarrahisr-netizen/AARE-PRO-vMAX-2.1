package com.aare.vmax.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.ui.theme.VMaxColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerCard(
    passenger: PassengerData,
    passengerIndex: Int,
    onUpdate: (PassengerData) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onFieldSubmit: (() -> Unit)? = null
) {
    val colors = VMaxColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // ✅ स्थिर ऑप्शंस (Recomposition से सुरक्षित)
    val genderOptions = remember { listOf("Male", "Female", "Transgender") }
    val berthOptions = remember { listOf("No Preference", "LOWER", "MIDDLE", "UPPER", "SIDE LOWER", "SIDE UPPER") }
    val mealOptions = remember { listOf("No Food", "VEG", "NON VEG", "JAIN MEAL") }
    
    var nameError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .background(colors.cardBg, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 📋 हेडर: पैसेंजर नंबर और रिमूव बटन
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "👤 Passenger ${passengerIndex + 1}", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (passengerIndex > 0) {
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Text("❌", color = colors.error, fontSize = 14.sp)
                }
            }
        }
        
        // ✍️ लाइन 1: Name और Age
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = passenger.name,
                onValueChange = { 
                    nameError = if (it.length < 4) "Short" else null
                    onUpdate(passenger.copy(name = it)) 
                },
                label = { Text("Name", fontSize = 12.sp) },
                isError = nameError != null,
                modifier = Modifier.weight(0.65f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onField, unfocusedTextColor = colors.onField, focusedContainerColor = colors.fieldBg, unfocusedContainerColor = colors.fieldBg),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = passenger.age,
                onValueChange = { if (it.length <= 3) onUpdate(passenger.copy(age = it)) },
                label = { Text("Age", fontSize = 12.sp) },
                modifier = Modifier.weight(0.35f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onField, unfocusedTextColor = colors.onField, focusedContainerColor = colors.fieldBg, unfocusedContainerColor = colors.fieldBg),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
        }

        // 🎯 लाइन 2: Gender और Berth Preference
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IRCTCDropdown(
                label = "Gender",
                options = genderOptions,
                selectedOption = passenger.gender,
                onSelectionChange = { onUpdate(passenger.copy(gender = it)) },
                modifier = Modifier.weight(0.5f)
            )
            IRCTCDropdown(
                label = "Berth",
                options = berthOptions,
                selectedOption = passenger.berthPreference,
                onSelectionChange = { onUpdate(passenger.copy(berthPreference = it)) },
                modifier = Modifier.weight(0.5f)
            )
        }

        // 🍽️ लाइन 3: Meal और Bed Roll
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IRCTCDropdown(
                label = "Meal",
                options = mealOptions,
                selectedOption = passenger.meal,
                onSelectionChange = { onUpdate(passenger.copy(meal = it)) },
                modifier = Modifier.weight(0.6f)
            )
            
            // ✅ Bed Roll Checkbox
            Row(modifier = Modifier.weight(0.4f).clickable { onUpdate(passenger.copy(bedRoll = !passenger.bedRoll)) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = passenger.bedRoll,
                    onCheckedChange = { onUpdate(passenger.copy(bedRoll = it)) },
                    colors = CheckboxDefaults.colors(checkedColor = colors.accent)
                )
                Text("Bed Roll", color = colors.onField, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IRCTCDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onSelectionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = VMaxColors.current
    
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.fieldBg,
                unfocusedContainerColor = colors.fieldBg,
                focusedTextColor = colors.onField,
                unfocusedTextColor = colors.onField,
                focusedBorderColor = colors.accent
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.cardBg)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = colors.onField, fontSize = 14.sp) },
                    onClick = { 
                        onSelectionChange(option)
                        expanded = false 
                    }
                )
            }
        }
    }
}
