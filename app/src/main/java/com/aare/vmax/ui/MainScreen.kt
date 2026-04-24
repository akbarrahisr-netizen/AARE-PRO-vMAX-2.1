package com.aare.vmax.ui

import android.content.*
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aare.vmax.core.orchestrator.WorkflowEngine // ✅ सही पैकेज
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.core.model.SniperTask
import com.aare.vmax.ui.components.PassengerCard
import com.aare.vmax.ui.theme.VMaxColors
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = VMaxColors.current
    
    // 🧠 स्टेट मैनेजमेंट
    var trainNo by remember { mutableStateOf("12506") }
    var travelClass by remember { mutableStateOf("3A") }
    var selectedQuota by remember { mutableStateOf("Tatkal") }
    var passengerList by remember { 
        mutableStateOf(listOf(PassengerData(id = UUID.randomUUID().toString()))) 
    }
    
    // UI स्टेट
    var isLoading by remember { mutableStateOf(false) }
    var showQuotaMenu by remember { mutableStateOf(false) }
    var showClassMenu by remember { mutableStateOf(false) }
    
    // ऑप्शंस
    val quotaOptions = remember { listOf("General", "Tatkal", "Premium Tatkal", "Ladies", "Lower Berth") }
    val classOptions = remember { listOf("SL", "3E", "3A", "2A", "1A", "CC", "2S") }
    val maxPassengers = if (selectedQuota == "General") 6 else 4
    
    val isAccessibilityEnabled = remember(context) { isAccessibilityServiceEnabled(context, WorkflowEngine::class.java) }

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp)) {
        // 🚄 ट्रेन और क्लास सेलेक्टर
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = trainNo,
                onValueChange = { if (it.length <= 5 && it.matches(Regex("\\d*"))) trainNo = it },
                label = { Text("Train No", color = colors.hint) },
                modifier = Modifier.weight(0.5f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            // क्लास ड्रॉपडाउन (नया अपग्रेड)
            Box(modifier = Modifier.weight(0.5f)) {
                OutlinedTextField(
                    value = travelClass, onValueChange = {}, readOnly = true,
                    label = { Text("Class", color = colors.hint) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showClassMenu) },
                    modifier = Modifier.clickable { showClassMenu = true },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                DropdownMenu(expanded = showClassMenu, onDismissRequest = { showClassMenu = false }) {
                    classOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { travelClass = option; showClassMenu = false })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 🎯 कोटा सेलेक्टर
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedQuota, onValueChange = {}, readOnly = true,
                label = { Text("Quota", color = colors.hint) },
                modifier = Modifier.fillMaxWidth().clickable { showQuotaMenu = true },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showQuotaMenu) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            DropdownMenu(expanded = showQuotaMenu, onDismissRequest = { showQuotaMenu = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
                quotaOptions.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { selectedQuota = option; showQuotaMenu = false })
                }
            }
        }

        // 📜 पैसेंजर लिस्ट
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(passengerList, key = { _, p -> p.id }) { index, passenger ->
                PassengerCard(
                    passenger = passenger,
                    passengerIndex = index,
                    onUpdate = { updated ->
                        val newList = passengerList.toMutableList()
                        newList[index] = updated
                        passengerList = newList
                    },
                    onRemove = { if (passengerList.size > 1) passengerList = passengerList.filter { it.id != passenger.id } }
                )
            }
            
            item {
                if (passengerList.size < maxPassengers) {
                    TextButton(onClick = { 
                        passengerList = passengerList + PassengerData(id = UUID.randomUUID().toString())
                        scope.launch { listState.animateScrollToItem(passengerList.size) }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("+ Add Passenger", color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 🔥 ARM THE SNIPER
        val isValid = trainNo.length == 5 && passengerList.any { it.isFilled() } && isAccessibilityEnabled

        Button(
            onClick = {
                isLoading = true
                keyboardController?.hide()
                
                val task = SniperTask(UUID.randomUUID().toString(), trainNo, travelClass, selectedQuota, passengerList.filter { it.isFilled() })
                val intent = Intent(context, WorkflowEngine::class.java).apply {
                    action = WorkflowEngine.ACTION_START
                    putExtra(WorkflowEngine.EXTRA_TASK, task)
                }
                
                try {
                    ContextCompat.startForegroundService(context, intent)
                    Toast.makeText(context, "🎯 Sniper Armed!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                isLoading = false
            },
            enabled = isValid && !isLoading,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, disabledContainerColor = Color.Gray),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("🔥 ARM THE SNIPER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        
        if (!isAccessibilityEnabled) {
            Text("⚠️ Enable Accessibility in Settings", color = Color.Yellow, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
        }
    }
}

// 🛠️ सर्विस चेक
private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    val expected = ComponentName(context, serviceClass).flattenToString()
    return enabled.contains(expected)
}
