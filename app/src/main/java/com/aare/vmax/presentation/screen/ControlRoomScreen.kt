package com.aare.vmax.presentation.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.aare.vmax.presentation.viewmodel.ControlRoomViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlRoomScreen(viewModel: ControlRoomViewModel) {

    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Local UI States
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var trainNo by remember { mutableStateOf("") }
    
    var gender by remember { mutableStateOf("Male") }
    var genderExpanded by remember { mutableStateOf(false) }
    
    var berth by remember { mutableStateOf("LB") }
    
    var bookingClass by remember { mutableStateOf("SL") }
    var classExpanded by remember { mutableStateOf(false) }

    val genderOptions = listOf("Male", "Female", "Other")
    val classOptions = listOf("SL", "3A", "2A", "1A", "3E")

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("🚀 AARE-PRO vMAX 2.1", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        // 1. Train Number & Name
        OutlinedTextField(
            value = trainNo,
            onValueChange = { trainNo = it },
            label = { Text("Train Number") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Passenger Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 2. Age
        OutlinedTextField(
            value = age,
            onValueChange = { age = it },
            label = { Text("Age") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 3. Gender Dropdown
        ExposedDropdownMenuBox(
            expanded = genderExpanded,
            onExpandedChange = { genderExpanded = !genderExpanded }
        ) {
            OutlinedTextField(
                value = gender,
                onValueChange = {},
                readOnly = true,
                label = { Text("Gender") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = genderExpanded,
                onDismissRequest = { genderExpanded = false }
            ) {
                genderOptions.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { gender = option; genderExpanded = false })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 4. Booking Class Dropdown
        ExposedDropdownMenuBox(
            expanded = classExpanded,
            onExpandedChange = { classExpanded = !classExpanded }
        ) {
            OutlinedTextField(
                value = bookingClass,
                onValueChange = {},
                readOnly = true,
                label = { Text("Booking Class") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = classExpanded,
                onDismissRequest = { classExpanded = false }
            ) {
                classOptions.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { bookingClass = option; classExpanded = false })
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 5. Save Button (Saves to ViewModel)
        Button(
            onClick = {
                viewModel.saveProfile(
                    name = name,
                    age = age.toIntOrNull() ?: 0,
                    gender = gender,
                    berth = berth,
                    trainNo = trainNo,
                    selectedClass = bookingClass
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("💾 Save Full Profile")
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Status Text
        Text(text = "Status: ${state.status}", color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        // 6. THE LAUNCHER BUTTON
        Button(
            onClick = {
                val irctcPackageName = "cris.org.in.prs.ima"
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(irctcPackageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    } else {
                        Toast.makeText(context, "IRCTC App Not Found!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to open IRCTC", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🚀 START & OPEN IRCTC")
        }
    }
}
