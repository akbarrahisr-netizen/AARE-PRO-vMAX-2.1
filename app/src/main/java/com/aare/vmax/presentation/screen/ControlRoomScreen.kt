package com.aare.vmax.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ControlRoomScreen() {

    // Temporary local state (ViewModel aane par replace ho jayega)
    var trainNumber by remember { mutableStateOf("") }
    var passengerName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("IDLE") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // ---------------- HEADER ----------------
        Text(
            text = "🚀 AARE-PRO vMAX 2.1",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Control Room Dashboard",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ---------------- STATUS PANEL ----------------
        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Status: $status")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------------- INPUT SECTION ----------------
        OutlinedTextField(
            value = trainNumber,
            onValueChange = { trainNumber = it },
            label = { Text("Train Number") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = passengerName,
            onValueChange = { passengerName = it },
            label = { Text("Passenger Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ---------------- ACTION BUTTONS ----------------
        Button(
            onClick = {
                status = "PROFILE SAVED"
                // TODO: ViewModel.saveProfile(trainNumber, passengerName)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Profile")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                status = "INITIALIZING..."
                // TODO: Start orchestration engine (safe layer)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("START SYSTEM")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                status = "STOPPED"
                // TODO: cancel all coroutines / jobs
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("EMERGENCY STOP")
        }
    }
}
