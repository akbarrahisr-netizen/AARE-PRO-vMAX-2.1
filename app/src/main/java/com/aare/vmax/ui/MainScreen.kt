package com.aare.vmax.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// ✅ IMPORTS FOR 3-LAYER SECURITY CHECK
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo

import com.aare.vmax.core.orchestrator.WorkflowEngine 
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
    
    val trainRegex = remember { Regex("\\d{5}") }

    // 🧠 Basic State
    var trainNo by remember { mutableStateOf("12506") }
    var selectedQuota by remember { mutableStateOf("Tatkal") }
    var passengerList by remember { 
        mutableStateOf(listOf(PassengerData(id = UUID.randomUUID().toString()))) 
    }
    
    // ⚙️ Advanced Booking State
    var selectedClass by remember { mutableStateOf("3A") }
    var selectedPaymentMethod by remember { mutableStateOf("UPI") }
    var selectedUPIApp by remember { mutableStateOf("BHIM UPI") }
    var showClassDropdown by remember { mutableStateOf(false) }
    var showPaymentDropdown by remember { mutableStateOf(false) }

    // 🗂️ All 13 Travel Classes
    val travelClasses = remember {
        listOf(
            "EA" to "First AC (EA)", "1A" to "First AC (1A)", "2A" to "Second AC (2A)",
            "3A" to "Third AC (3A)", "CC" to "AC Chair Car (CC)", "3E" to "Third AC Economy (3E)",
            "EC" to "Executive Chair Car (EC)", "SL" to "Sleeper (SL)", "FC" to "First Class (FC)",
            "2S" to "Second Sitting (2S)", "VS" to "Vistadome Sleeper (VS)", 
            "VC" to "Vistadome Chair Car (VC)", "EV" to "Vistadome AC (EV)"
        )
    }
    
    // 💳 Payment Options
    val paymentOptions = remember {
        mapOf(
            "UPI" to listOf("BHIM UPI", "PhonePe", "Paytm", "CRED UPI", "Google Pay"),
            "e-Wallets" to listOf("IRCTC Wallet", "MobiKwik", "Paytm Wallet", "FreeCharge"),
            "Netbanking" to listOf("SBI", "HDFC", "ICICI", "Axis", "PNB", "BOB"), 
            "Card" to listOf("Credit Card", "Debit Card", "Cash Card") 
        )
    }
    
    val validPassengers by derivedStateOf { 
        passengerList.filter { it.isFilled() && it.isValid() } 
    }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showQuotaMenu by remember { mutableStateOf(false) }
    
    val quotaOptions = listOf("General", "Tatkal", "Premium Tatkal", "Ladies", "Lower Berth/Sr. Citizen", "Divyangjan") 
    val maxPassengers = if (selectedQuota == "General") 6 else 4
    
    // ✅ AUTO-REFRESH LOGIC
    var isAccessibilityEnabled by remember { 
        mutableStateOf(isAccessibilityServiceEnabled(context, WorkflowEngine::class.java)) 
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context, WorkflowEngine::class.java)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .semantics { contentDescription = "VMAX Sniper main form" }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 🚆 Train & Quota
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = trainNo,
                onValueChange = { 
                    if (it.length <= 5 && it.matches(Regex("\\d*"))) trainNo = it 
                },
                label = { Text("Train No", color = colors.hint) },
                placeholder = { Text("5 digits", color = colors.hint.copy(alpha = 0.6f)) },
                isError = trainNo.isNotBlank() && !trainRegex.matches(trainNo),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.fieldBg, unfocusedContainerColor = colors.fieldBg,
                    focusedBorderColor = colors.accent, unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = colors.onField, unfocusedTextColor = colors.onField,
                    errorBorderColor = colors.error, errorTextColor = colors.error
                ),
                singleLine = true,
                modifier = Modifier.weight(0.6f)
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
                        focusedContainerColor = colors.fieldBg, unfocusedContainerColor = colors.fieldBg,
                        focusedBorderColor = colors.accent, unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = colors.onField, unfocusedTextColor = colors.onField
                    ),
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showQuotaMenu, onDismissRequest = { showQuotaMenu = false },
                    modifier = Modifier.background(colors.cardBg)
                ) {
                    quotaOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = if (option == selectedQuota) colors.accent else colors.onField) },
                            onClick = { selectedQuota = option; showQuotaMenu = false }
                        )
                    }
                }
            }
        }

        // ⚠️ Accessibility Warning
        if (!isAccessibilityEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.warning.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, colors.warning),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        isAccessibilityEnabled = isAccessibilityServiceEnabled(context, WorkflowEngine::class.java)
                        if (!isAccessibilityEnabled) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) 
                        }
                    }
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚠️", fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                    Text("VMAX Service is OFF\n(Tap here to fix & refresh)", color = colors.warning, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("👥 Passengers (${validPassengers.size} valid)", color = colors.accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("Max: $maxPassengers", color = colors.hint, fontSize = 12.sp)
        }

        // 📜 Scrollable Passenger List & Booking Options
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)        
        ) {
            itemsIndexed(passengerList, key = { _, it -> it.id }) { index, passenger ->
                PassengerCard(
                    passenger = passenger,
                    passengerIndex = index,
                    onUpdate = { updated ->
                        passengerList = passengerList.toMutableList().apply {
                            this[index] = updated
                        }
                    },
                    onRemove = { 
                        if (passengerList.size > 1) {
                            passengerList = passengerList.filter { it.id != passenger.id }
                        }
                    }
                )
            }
            
            if (passengerList.size < maxPassengers) {
                item {
                    TextButton(
                        onClick = {
                            passengerList = passengerList + PassengerData(id = UUID.randomUUID().toString())
                            scope.launch { listState.animateScrollToItem(passengerList.lastIndex) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Add Passenger", color = colors.accent, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }

            // ⚙️ ADVANCED BOOKING OPTIONS
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚙️ Booking Options", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        
                        // Travel Class Selection
                        Text("Travel Class", color = colors.hint, fontSize = 12.sp)
                        ExposedDropdownMenuBox(
                            expanded = showClassDropdown,
                            onExpandedChange = { showClassDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = travelClasses.find { it.first == selectedClass }?.second ?: selectedClass,
                                onValueChange = {}, readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showClassDropdown) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = colors.fieldBg, unfocusedContainerColor = colors.fieldBg,
                                    focusedBorderColor = colors.accent, unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = colors.onField, unfocusedTextColor = colors.onField
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = showClassDropdown, onDismissRequest = { showClassDropdown = false },
                                modifier = Modifier.background(colors.fieldBg).heightIn(max = 250.dp) 
                            ) {
                                travelClasses.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { 
                                            Row {
                                                Text(code, fontWeight = FontWeight.Bold, color = colors.accent)                                                
                                                Spacer(Modifier.width(8.dp))
                                                Text(name, color = colors.onField)
                                            }
                                        },
                                        onClick = { selectedClass = code; showClassDropdown = false }
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Payment Method Selection
                        Text("Payment Method", color = colors.hint, fontSize = 12.sp)
                        ExposedDropdownMenuBox(
                            expanded = showPaymentDropdown,
                            onExpandedChange = { showPaymentDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = selectedPaymentMethod,
                                onValueChange = {}, readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPaymentDropdown) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = colors.fieldBg, unfocusedContainerColor = colors.fieldBg,
                                    focusedBorderColor = colors.accent, unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = colors.onField, unfocusedTextColor = colors.onField
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = showPaymentDropdown, onDismissRequest = { showPaymentDropdown = false },
                                modifier = Modifier.background(colors.fieldBg)
                            ) {
                                paymentOptions.keys.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category, color = colors.onField) },
                                        onClick = { 
                                            selectedPaymentMethod = category
                                            selectedUPIApp = paymentOptions[category]?.firstOrNull() ?: ""
                                            showPaymentDropdown = false 
                                        }
                                    )
                                }                            
                            }
                        }
                        
                        // Show Apps if UPI or Wallet selected
                        if (paymentOptions[selectedPaymentMethod]?.isNotEmpty() == true) {
                            Spacer(Modifier.height(8.dp))
                            Text("Select App", color = colors.hint, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                paymentOptions[selectedPaymentMethod]?.forEach { app ->
                                    FilterChip(
                                        selected = selectedUPIApp == app,
                                        onClick = { selectedUPIApp = app },
                                        label = { Text(app, fontSize = 11.sp, color = if (selectedUPIApp == app) Color.White else colors.onField) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colors.accent,
                                            containerColor = colors.fieldBg
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true, selected = selectedUPIApp == app,
                                            borderColor = colors.accent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val hasErrors = passengerList.any { !it.isValid() && it.isFilled() }
        if (hasErrors) {
            Text("⚠️ Fix invalid passengers before arming", color = colors.warning, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        val canArm = validPassengers.isNotEmpty() && trainRegex.matches(trainNo) && !isLoading && isAccessibilityEnabled

        // 🔥 FIRE BUTTON
        Button(
            onClick = {
                scope.launch {
                    keyboardController?.hide()
                    
                    if (!trainRegex.matches(trainNo)) {
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
                        val task = SniperTask(
                            taskId = UUID.randomUUID().toString(),
                            trainNumber = trainNo.trim(),
                            travelClass = selectedClass,  
                            quota = selectedQuota,                            
                            passengers = validPassengers,
                            paymentMethod = selectedPaymentMethod,
                            upiApp = selectedUPIApp
                        )
                        
                        val intent = Intent(context, WorkflowEngine::class.java).apply {
                            action = WorkflowEngine.ACTION_START 
                            putExtra(WorkflowEngine.EXTRA_TASK, task)
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
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Text("Arming...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            } else {
                Text("🔥 ARM THE SNIPER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        
        errorMessage?.let { msg ->
            Text("❌ $msg", color = colors.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// 🛡️ THE BRAHMAASTRA: 3-Layer Accessibility Check (यह 100% काम करेगा)
private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    var isEnabled = false
    try {
        // 🔹 LAYER 1: Official Android Settings Splitter (Strict Match)
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                isEnabled = true
                break
            }
        }

        // 🔹 LAYER 2: Brute-Force String Match (अगर फोन ने नाम बिगाड़ दिया हो)
        if (!isEnabled) {
            if (enabledServicesSetting.contains(context.packageName) &&
                enabledServicesSetting.contains(serviceClass.simpleName)) {
                isEnabled = true
            }
        }

        // 🔹 LAYER 3: Android Accessibility Manager (सिस्टम को बाईपास करके डायरेक्ट पूछना)
        if (!isEnabled) {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (service in enabledServices) {
                if (service.resolveInfo.serviceInfo.packageName == context.packageName &&
                    service.resolveInfo.serviceInfo.name == serviceClass.name) {
                    isEnabled = true
                    break
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return isEnabled
}
