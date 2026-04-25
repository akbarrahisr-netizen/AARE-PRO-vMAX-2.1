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

    // 🧠 Core State
    var trainNo by remember { mutableStateOf("12506") }
    var selectedQuota by remember { mutableStateOf("Tatkal") }
    var passengerList by remember { mutableStateOf(listOf(PassengerData(id = UUID.randomUUID().toString()))) }
    
    // ⚙️ Advanced Booking Options (From your Screenshots)
    var travelClass by remember { mutableStateOf("3A") }
    var autoUpgradation by remember { mutableStateOf(false) }
    var confirmBerthsOnly by remember { mutableStateOf(false) }
    var travelInsurance by remember { mutableStateOf(true) }
    var bookingOption by remember { mutableStateOf("None") }
    var coachPreferred by remember { mutableStateOf(false) }
    var coachId by remember { mutableStateOf("") }
    var mobileNo by remember { mutableStateOf("") }

    // 💳 Payment Suite
    var paymentMethod by remember { mutableStateOf("UPI apps") }
    var selectedApp by remember { mutableStateOf("PhonePe") }
    var autofillOTP by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var showQuotaMenu by remember { mutableStateOf(false) }

    // 🗂️ Data Lists
    val quotaOptions = listOf("General", "Tatkal", "Premium Tatkal", "Ladies", "Lower Berth")
    val classes = remember { listOf("EA", "1A", "2A", "3A", "CC", "3E", "EC", "SL", "FC", "2S", "VS", "VC", "EV") }
    val payMethods = listOf("e-Wallets", "Netbanking/Cards", "UPI ID", "UPI apps")
    val upiApps = mapOf("UPI apps" to listOf("PhonePe", "Paytm", "CRED UPI", "BHIM UPI", "Google Pay"), "e-Wallets" to listOf("IRCTC", "MobiKwik", "Paytm Wallet"))

    // ✅ Robust Accessibility Check
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
        
        // 🚆 TRAIN + QUOTA
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = trainNo, onValueChange = { if (it.length <= 5 && it.matches(digitRegex)) trainNo = it },
                label = { Text("Train No") }, modifier = Modifier.weight(0.6f),
                isError = trainNo.isNotBlank() && !trainRegex.matches(trainNo)
            )
            ExposedDropdownMenuBox(expanded = showQuotaMenu, onExpandedChange = { showQuotaMenu = it }, modifier = Modifier.weight(0.4f)) {
                OutlinedTextField(value = selectedQuota, onValueChange = {}, readOnly = true, label = { Text("Quota") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(showQuotaMenu) }, modifier = Modifier.menuAnchor())
                ExposedDropdownMenu(expanded = showQuotaMenu, onDismissRequest = { showQuotaMenu = false }) {
                    quotaOptions.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { selectedQuota = it; showQuotaMenu = false }) }
                }
            }
        }

        // ⚠️ ACCESSIBILITY WARNING CARD
        if (!isAccessibilityEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                border = BorderStroke(1.dp, Color(0xFFFFA500)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA500).copy(alpha = 0.1f))
            ) {
                Text("⚠️ VMAX Service is OFF (Tap to fix & refresh)", modifier = Modifier.padding(12.dp), color = Color(0xFFFFA500), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        val listState = rememberLazyListState()
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            
            // 👥 PASSENGERS
            item { Text("👥 Passengers (${passengerList.count { it.isFilled() }} valid)", color = colors.accent, modifier = Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(passengerList, key = { _, it -> it.id }) { index, p ->
                PassengerCard(passenger = p, passengerIndex = index, onUpdate = { u -> passengerList = passengerList.toMutableList().apply { this[index] = u } }, onRemove = { if (passengerList.size > 1) passengerList = passengerList.filter { it.id != p.id } })
            }
            
            item {
                TextButton(onClick = { passengerList = passengerList + PassengerData(id = UUID.randomUUID().toString()); scope.launch { listState.animateScrollToItem(passengerList.lastIndex) } }) { Text("+ Add Passenger") }
                
                // ⚙️ ADVANCED OPTIONS (From your Screen 524981)
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚙️ Booking Options", fontWeight = FontWeight.Bold, color = colors.accent)
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = autoUpgradation, onCheckedChange = { autoUpgradation = it })
                            Text("Consider Auto upgradation", fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = confirmBerthsOnly, onCheckedChange = { confirmBerthsOnly = it })
                            Text("Book only if confirm berths are allotted", fontSize = 12.sp)
                        }
                        
                        Text("Travel Insurance", modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row {
                            RadioButton(selected = travelInsurance, onClick = { travelInsurance = true })
                            Text("Yes", modifier = Modifier.align(Alignment.CenterVertically))
                            Spacer(Modifier.width(10.dp))
                            RadioButton(selected = !travelInsurance, onClick = { travelInsurance = false })
                            Text("No", modifier = Modifier.align(Alignment.CenterVertically))
                        }
                        
                        // Class Selection (All 13)
                        var showClasses by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OutlinedTextField(value = travelClass, onValueChange = {}, readOnly = true, label = { Text("Travel Class") }, modifier = Modifier.fillMaxWidth().clickable { showClasses = true }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = colors.onField, disabledBorderColor = colors.accent, disabledLabelColor = colors.hint))
                            DropdownMenu(expanded = showClasses, onDismissRequest = { showClasses = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                                classes.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { travelClass = c; showClasses = false }) }
                            }
                        }

                        // Payment Logic (Scrollable Apps)
                        Text("Payment Method", modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp, color = colors.hint)
                        payMethods.forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = paymentMethod == m, onClick = { paymentMethod = m; selectedApp = upiApps[m]?.firstOrNull() ?: "Default" })
                                Text(m, fontSize = 12.sp)
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

        // 🔥 FIRE BUTTON
        Button(
            onClick = {
                scope.launch {
                    if (isLoading) return@launch
                    keyboard?.hide()
                    isLoading = true
                    try {
                        val task = SniperTask(
                            taskId = UUID.randomUUID().toString(), trainNumber = trainNo, travelClass = travelClass, quota = selectedQuota, passengers = passengerList.filter { it.isFilled() },
                            paymentMethod = paymentMethod, upiApp = selectedApp, autoUpgradation = autoUpgradation, confirmBerthsOnly = confirmBerthsOnly, insurance = travelInsurance
                        )
                        // ✅ SIGNAL TRIGGER (Broadcast) - Avoids Service Start Crashes
                        context.sendBroadcast(Intent("com.aare.vmax.ACTION_START").apply { setPackage(context.packageName); putExtra("extra_task", task) })
                        Toast.makeText(context, "🎯 Sniper Armed!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                    finally { isLoading = false }
                }
            },
            enabled = passengerList.any { it.isFilled() } && isAccessibilityEnabled && !isLoading,
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White) else Text("🔥 ARM THE SNIPER", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

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
