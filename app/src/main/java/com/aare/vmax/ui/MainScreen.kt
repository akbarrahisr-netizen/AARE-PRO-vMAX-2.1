package com.aare.vmax.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aare.vmax.core.engine.WorkflowEngine
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.core.model.SniperTask
import com.aare.vmax.ui.components.PassengerCard
import com.aare.vmax.ui.theme.VMaxColors
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onServiceResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = VMaxColors.current
    
    var trainNo by remember { mutableStateOf("12506") }
    var selectedQuota by remember { mutableStateOf("Tatkal") }
    var passengerList by remember { 
        mutableStateOf(listOf(PassengerData(id = UUID.randomUUID().toString()))) 
    }
    
    val validPassengers by derivedStateOf { 
        passengerList.filter { it.isFilled() && it.isValid() } 
    }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showQuotaMenu by remember { mutableStateOf(false) }
    
    val quotaOptions = remember { 
        listOf("General", "Tatkal", "Premium Tatkal", "Ladies", "Lower Berth/Sr. Citizen", "Divyangjan") 
    }
    val maxPassengers = if (selectedQuota == "General") 6 else 4
    
    val isAccessibilityEnabled = remember(context) {
        isAccessibilityServiceEnabled(context, WorkflowEngine::class.java)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .semantics { contentDescription = "VMAX Sniper main form" }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = trainNo,
                onValueChange = { 
                    if (it.length <= 5 && it.matches(Regex("\\d*"))) {
                        trainNo = it 
                    }
                },
                label = { Text("Train No", color = colors.hint) },
                placeholder = { Text("5 digits", color = colors.hint.copy(alpha = 0.6f)) },
                isError = trainNo.isNotBlank() && !trainNo.matches(Regex("\\d{5}")),
                supportingText = {
                    if (trainNo.isNotBlank() && !trainNo.matches(Regex("\\d{5}"))) {
                        Text("Enter valid 5-digit number", color = colors.error, fontSize = 10.sp)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.fieldBg,
                    unfocusedContainerColor = colors.fieldBg,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = colors.onField,
                    unfocusedTextColor = colors.onField,
                    errorBorderColor = colors.error,
                    errorTextColor = colors.error
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(0.6f)
                    .semantics { contentDescription = "Train number input" }
            )
            
            ExposedDropdownMenuBox(
                expanded = showQuotaMenu,
                onExpandedChange = { showQuotaMenu = it },
                modifier = Modifier.weight(0.4f)
            ) {
                OutlinedTextField(
                    value = selectedQuota,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Quota", color = colors.hint) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showQuotaMenu) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.fieldBg,
                        unfocusedContainerColor = colors.fieldBg,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = colors.onField,
                        unfocusedTextColor = colors.onField
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .semantics { contentDescription = "Quota selection dropdown" }
                )
                ExposedDropdownMenu(
                    expanded = showQuotaMenu,
                    onDismissRequest = { showQuotaMenu = false },
                    modifier = Modifier.background(colors.dropdownBg)
                ) {
                    quotaOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    option, 
                                    color = if (option == selectedQuota) colors.accent else colors.onField,
                                    maxLines = 1
                                ) 
                            },
                            onClick = { 
                                selectedQuota = option
                                showQuotaMenu = false 
                            },
                            modifier = Modifier.semantics { 
                                contentDescription = "Select $option quota" 
                            }
                        )
                    }
                }
            }
        }

        if (!isAccessibilityEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.warning.copy(alpha = 0.1f)),
                border = androidx.compose.ui.BorderStroke(1.dp, colors.warning),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) 
                    }
                    .padding(12.dp)
                    .semantics { contentDescription = "Enable accessibility service warning" }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("⚠️", fontSize = 18.sp)
                    Text(
                        "Enable VMAX in Accessibility Settings", 
                        color = colors.warning, 
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "👥 Passengers (${validPassengers.size} valid)",
                color = colors.accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Text(
                text = "Max: $maxPassengers",
                color = colors.hint,
                fontSize = 12.sp
            )
        }

        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(passengerList, key = { it.id }) { passenger ->
                PassengerCard(
                    passenger = passenger,
                    passengerIndex = passengerList.indexOf(passenger),
                    onUpdate = { updated ->
                        val index = passengerList.indexOfFirst { it.id == passenger.id }
                        if (index != -1) {
                            val newList = passengerList.toMutableList()
                            newList[index] = updated
                            passengerList = newList
                        }
                    },
                    onRemove = { 
                        if (passengerList.size > 1) {
                            passengerList = passengerList.filter { it.id != passenger.id }
                        }
                    },
                    onFieldSubmit = { }
                )
            }
            
            if (passengerList.size < maxPassengers) {
                item {
                    TextButton(
                        onClick = {
                            passengerList = passengerList + PassengerData(
                                id = UUID.randomUUID().toString(),
                                isMandatory = true
                            )
                            scope.launch {
                                listState.animateScrollToItem(passengerList.size)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Add new passenger" }
                    ) {
                        Text(
                            "+ Add Passenger", 
                            color = colors.accent, 
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        val hasErrors = passengerList.any { !it.isValid() && it.isFilled() }
        if (hasErrors) {
            Text(
                text = "⚠️ Fix invalid passengers before arming",
                color = colors.warning,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val canArm = validPassengers.isNotEmpty() && 
                    trainNo.matches(Regex("\\d{5}")) && 
                    !isLoading && 
                    isAccessibilityEnabled

        Button(
            onClick = {
                scope.launch {
                    keyboardController?.hide()
                    
                    if (!trainNo.matches(Regex("\\d{5}"))) {
                        errorMessage = "Enter valid 5-digit train number"
                        return@launch
                    }
                    if (validPassengers.isEmpty()) {
                        errorMessage = "Add at least one valid passenger"
                        return@launch
                    }
                    
                    isLoading = true
                    errorMessage = null
                    
                    try {
                        // ✅ FIX 1: Removed extra arguments that break SniperTask
                        val task = SniperTask(
                            taskId = UUID.randomUUID().toString(),
                            trainNumber = trainNo.trim(),
                            travelClass = "3A",  
                            quota = selectedQuota,
                            passengers = validPassengers
                        )
                        
                        // ✅ FIX 2: Hardcoded direct strings to avoid "Private" errors
                        val intent = Intent(context, WorkflowEngine::class.java).apply {
                            action = "com.aare.vmax.ACTION_START"
                            putExtra("extra_task", task)
                        }
                        
                        ContextCompat.startForegroundService(context, intent)
                        
                        onServiceResult(true, null)
                        Toast.makeText(context, "🎯 Sniper Armed!", Toast.LENGTH_SHORT).show()
                        
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message?.take(40)}"
                        onServiceResult(false, errorMessage)
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = canArm,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canArm) Color.Red else Color.Red.copy(alpha = 0.4f),
                disabledContainerColor = Color.Red.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .semantics { 
                    contentDescription = if (isLoading) "Arming sniper, please wait" else "Arm the sniper button" 
                }
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Text("Arming...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            } else {
                Text(
                    "🔥 ARM THE SNIPER", 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp
                )
            }
        }
        
        errorMessage?.let { msg ->
            Text(
                text = "❌ $msg",
                color = colors.error,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { contentDescription = "Error message: $msg" }
            )
        }
    }
}

private fun isAccessibilityServiceEnabled(
    context: Context, 
    serviceClass: Class<*>
): Boolean {
    return try {
        val enabled = Settings.Secure.getString(
            context.contentResolver, 
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        
        val splitter = android.text.TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        
        val expectedComponent = ComponentName(context, serviceClass).flattenToString()
        
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponent, ignoreCase = true)) {
                return true
            }
        }
        false
    } catch (e: Exception) {
        false
    }
}
