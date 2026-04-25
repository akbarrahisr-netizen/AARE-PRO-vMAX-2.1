package com.aare.vmax.ui

import android.content.*
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo

import com.aare.vmax.core.orchestrator.WorkflowEngine 
import com.aare.vmax.core.engine.AutomationCommandCenter
import com.aare.vmax.core.model.*
import com.aare.vmax.ui.components.PassengerCard
import com.aare.vmax.ui.theme.VMaxColors
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onServiceResult: (Boolean, String?) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = VMaxColors.current
    
    val trainRegex = remember { Regex("\\d{5}") }
    val digitRegex = remember { Regex("\\d*") }

    // 🧠 Core States (Merged from v2.1 Control Room)
    var trainNo by remember { mutableStateOf("12506") }
    var selectedQuota by remember { mutableStateOf("Tatkal") }
    var passengerList by remember { mutableStateOf(listOf(PassengerData(id = UUID.randomUUID().toString()))) }
    
    // ⚙️ Advanced Booking Logic
    var travelClass by remember { mutableStateOf("3A") }
    var autoUpgradation by remember { mutableStateOf(false) }
    var confirmBerthsOnly by remember { mutableStateOf(false) }
    var travelInsurance by remember { mutableStateOf(true) }
    var bookingOption by remember { mutableStateOf("None") }

    // 💳 Payment Suite
    var paymentMethod by remember { mutableStateOf("UPI apps") }
    var selectedApp by remember { mutableStateOf("PhonePe") }
    var autofillOTP by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(false) }
    var showQuotaMenu by remember { mutableStateOf(false) }

    // 🗂️ Master Lists
    val classes = remember { listOf("EA", "1A", "2A", "3A", "CC", "3E", "EC", "SL", "FC", "2S", "VS", "VC", "EV") }
    val payMethods = listOf("e-Wallets", "Netbanking/Cards", "UPI ID", "UPI apps")
    val upiApps = mapOf(
        "UPI apps" to listOf("PhonePe", "Paytm", "CRED UPI", "BHIM UPI", "Google Pay"),
        "e-Wallets" to listOf("IRCTC", "MobiKwik", "Paytm Wallet")
    )

    // ✅ Brahmaastra Accessibility Detection
    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context, WorkflowEngine::class.java)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isAccessibilityEnabled = isAccessibilityServiceEnabled(context, WorkflowEngine::class.java)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp)) {
        
        // 🚀 HEADER
        Text("🚀 AARE-PRO vMAX 2.1", style = MaterialTheme.typography.headlineSmall, color = colors.accent, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        // 🚆 TRAIN + QUOTA
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = trainNo, onValueChange = { if (it.length <= 5 && it.matches(digitRegex)) trainNo = it },
                label = { Text("Train No") }, modifier = Modifier.weight(0.6f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                isError = trainNo.isNotBlank() && !trainRegex.matches(trainNo)
            )
            ExposedDropdownMenuBox(expanded = showQuotaMenu, onExpandedChange = { showQuotaMenu = it }, modifier = Modifier.weight(0.4f)) {
                OutlinedTextField(value = selectedQuota, onValueChange = {}, readOnly = true, label = { Text("Quota") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(showQuotaMenu) }, modifier = Modifier.menuAnchor())
                ExposedDropdownMenu(expanded = showQuotaMenu, onDismissRequest = { showQuotaMenu = false }) {
                    listOf("General", "Tatkal", "Premium Tatkal").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { selectedQuota = it; showQuotaMenu = false }) }
                }
            }
        }

        // ⚠️ ACCESSIBILITY WARNING
        if (!isAccessibilityEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                border = BorderStroke(1.dp, Color.Yellow),
                colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.1f))
            ) {
                Text("🛠️ Service is OFF (Tap to enable VMAX Sniper)", modifier = Modifier.padding(12.dp), color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        val listState = rememberLazyListState()
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            
            // 👥 PASSENGERS
            item { Text("👥 Passenger Details", color = colors.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(passengerList, key = { _, it -> it.id }) { index, p ->
                PassengerCard(passenger = p, passengerIndex = index, onUpdate = { u -> passengerList = passengerList.toMutableList().apply { this[index] = u } }, onRemove = { if (passengerList.size > 1) passengerList = passengerList.filter { it.id != p.id } })
            }
            
            item {
                TextButton(onClick = { passengerList = passengerList + PassengerData(id = UUID.randomUUID().toString()); scope.launch { listState.animateScrollToItem(passengerList.lastIndex) } }) { Text("+ Add Passenger") }
                
                // ⚙️ CONFIGURATION CARD
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚙️ Advanced Sniper Options", fontWeight = FontWeight.Bold, color = colors.accent)
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = autoUpgradation, onCheckedChange = { autoUpgradation = it })
                            Text("Auto Upgradation", fontSize = 12.sp)
                            Spacer(Modifier.width(16.dp))
                            Checkbox(checked = confirmBerthsOnly, onCheckedChange = { confirmBerthsOnly = it })
                            Text("Confirm Berth Only", fontSize = 12.sp)
                        }

                        // Class Chips
                        Text("Select Class", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            classes.forEach { c -> FilterChip(selected = travelClass == c, onClick = { travelClass = c }, label = { Text(c) }, modifier = Modifier.padding(end = 4.dp)) }
                        }
                    }
                }

                // 💳 PAYMENT CARD
                Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("💳 Payment Configuration", fontWeight = FontWeight.Bold, color = Color.Cyan)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            payMethods.forEach { method ->
                                FilterChip(selected = paymentMethod == method, onClick = { paymentMethod = method; selectedApp = upiApps[method]?.firstOrNull() ?: "Default" }, label = { Text(method) }, modifier = Modifier.padding(end = 6.dp))
                            }
                        }
                        if (upiApps[paymentMethod] != null) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                                upiApps[paymentMethod]!!.forEach { app ->
                                    FilterChip(selected = selectedApp == app, onClick = { selectedApp = app }, label = { Text(app) }, modifier = Modifier.padding(end = 4.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }

        // 🔥 ACTION AREA
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        if (isLoading) return@launch
                        keyboard?.hide()
                        isLoading = true
                        
                        // 1. Start Automation System
                        AutomationCommandCenter.startSystem()

                        val task = SniperTask(
                            taskId = UUID.randomUUID().toString(), trainNumber = trainNo, travelClass = travelClass, quota = selectedQuota, passengers = passengerList.filter { it.isFilled() },
                            paymentMethod = paymentMethod, upiApp = selectedApp, autoUpgradation = autoUpgradation, confirmBerthsOnly = confirmBerthsOnly, insurance = travelInsurance
                        )
                        
                        // 2. Broadcast to Workflow Engine
                        context.sendBroadcast(Intent(WorkflowEngine.ACTION_START).apply { setPackage(context.packageName); putExtra(WorkflowEngine.EXTRA_TASK, task) })
                        
                        // 3. Open IRCTC
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("cris.org.in.prs.ima")
                        launchIntent?.let { context.startActivity(it) } ?: Toast.makeText(context, "IRCTC Not Found!", Toast.LENGTH_SHORT).show()
                        
                        isLoading = false
                    }
                },
                enabled = passengerList.any { it.isFilled() } && isAccessibilityEnabled && !isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🚀 START SNIPER & OPEN IRCTC", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { AutomationCommandCenter.stopSystem() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🛑 EMERGENCY STOP")
            }
        }
    }
}

// ✅ ACCESSBIILITY DETECTION LOGIC
private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        if (enabledServices != null) {
            for (service in enabledServices) {
                val serviceInfo = service.resolveInfo.serviceInfo
                if (serviceInfo.packageName == context.packageName && (serviceInfo.name == serviceClass.name || serviceInfo.name.endsWith(serviceClass.simpleName))) return true
            }
        }
    } catch (e: Exception) { }
    return false
}
