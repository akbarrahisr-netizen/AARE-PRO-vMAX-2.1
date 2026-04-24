package com.aare.vmax.ui.screens

import android.content.*
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.aare.vmax.core.model.*
import com.aare.vmax.core.orchestrator.WorkflowEngine
import com.aare.vmax.ui.components.PassengerCard
import com.aare.vmax.ui.theme.VMaxColors
import java.util.UUID

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val colors = VMaxColors.current
    var trainNumber by remember { mutableStateOf("12506") }
    var travelClass by remember { mutableStateOf("3A") }
    var passengerList by remember { mutableStateOf(listOf(PassengerData())) }

    Column(Modifier.fillMaxSize().background(colors.background).padding(16.dp)) {
        // ऊपर की सेटिंग (Train No/Class) - जैसा आपने स्क्रीनशॉट में माँगा था
        OutlinedTextField(value = trainNumber, onValueChange = { trainNumber = it }, label = { Text("Train No") })
        
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(passengerList, key = { _, p -> p.id }) { index, p ->
                PassengerCard(
                    passenger = p,
                    passengerIndex = index,
                    onUpdate = { updated ->
                        val newList = passengerList.toMutableList()
                        newList[index] = updated
                        passengerList = newList
                    },
                    onRemove = {
                        passengerList = passengerList.filter { it.id != p.id }
                    }
                )
            }
            
            item {
                if (passengerList.size < 6) {
                    TextButton(onClick = { passengerList = passengerList + PassengerData() }) {
                        Text("+ Add Passenger", color = colors.accent)
                    }
                }
            }
        }

        // 🔥 FIRE BUTTON
        Button(
            onClick = {
                val task = SniperTask(UUID.randomUUID().toString(), trainNumber, travelClass, "Tatkal", passengerList.filter { it.isFilled() })
                val intent = Intent(context, WorkflowEngine::class.java).apply {
                    action = "com.aare.vmax.ACTION_START"
                    putExtra("extra_task", task)
                }
                ContextCompat.startForegroundService(context, intent)
                Toast.makeText(context, "🎯 Sniper Armed!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("🔥 ARM THE SNIPER")
        }
    }
}
