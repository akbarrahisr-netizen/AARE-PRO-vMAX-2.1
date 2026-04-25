package com.aare.vmax.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.core.model.PaymentDetails
import com.aare.vmax.core.model.SniperTask
import com.aare.vmax.core.orchestrator.WorkflowEngine
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    
    // 🧠 State Management
    var trainNo by remember { mutableStateOf("") }
    var travelClass by remember { mutableStateOf("3A") }
    var selectedQuota by remember { mutableStateOf("Tatkal") }
    var passengers by remember { mutableStateOf(listOf(PassengerData())) }
    var payment by remember { mutableStateOf(PaymentDetails()) }
    
    // ⚙️ Advanced Settings
    var bookingOption by remember { mutableStateOf("None") }
    var autoUpgradation by remember { mutableStateOf(false) }
    var confirmBerthsOnly by remember { mutableStateOf(false) }
    var insurance by remember { mutableStateOf(true) }
    var coachPreferred by remember { mutableStateOf(false) }
    var coachId by remember { mutableStateOf("") }
    var mobileNo by remember { mutableStateOf("") }
    
    // ✅ Service Status
    var isEnabled by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101018)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ⚠️ Service Status Header
        StatusHeader(isEnabled) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // 🚆 SECTION: Journey Details
            item {
                VMaxCard(title = "🚆 Journey Details") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VMaxTextField(trainNo, "Train No", Modifier.weight(1f), true) { trainNo = it }
                        VMaxDropdown(travelClass, "Class", listOf("Sleeper", "3A", "2A", "1A", "CC", "EC"), Modifier.weight(1f)) { travelClass = it }
                    }
                    VMaxDropdown(selectedQuota, "Quota", listOf("Tatkal", "General", "Ladies", "Senior Citizen"), Modifier.fillMaxWidth()) { selectedQuota = it }
                }
            }
            
            // 👥 SECTION: Passengers
            itemsIndexed(passengers) { index, passenger ->
                PassengerCard(
                    index = index,
                    passenger = passenger,
                    canRemove = passengers.size > 1,
                    onUpdate = { updated -> passengers = passengers.toMutableList().apply { this[index] = updated } },
                    onRemove = { passengers = passengers.toMutableList().apply { removeAt(index) } }
                )
            }
            
            item {
                Button(
                    onClick = { passengers = passengers + PassengerData() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B21A8))
                ) { Text("+ Add Passenger") }
            }
            
            // ⚙️ SECTION: Advanced Settings
            item {
                VMaxCard(title = "⚙️ Advanced Settings") {
                    VMaxDropdown(bookingOption, "Booking Option", listOf("None", "Same Coach", "1 Lower", "2 Lower"), Modifier.fillMaxWidth()) { bookingOption = it }
                    
                    Row {
                        VMaxCheckbox("Auto Upgrade", autoUpgradation) { autoUpgradation = it }
                        VMaxCheckbox("Confirm Berths", confirmBerthsOnly) { confirmBerthsOnly = it }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Insurance: ", color = Color.White, fontSize = 14.sp)
                        FilterChip(selected = insurance, onClick = { insurance = true }, label = { Text("Yes") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = !insurance, onClick = { insurance = false }, label = { Text("No") })
                    }

                    VMaxCheckbox("Coach Preferred", coachPreferred) { coachPreferred = it }
                    AnimatedVisibility(visible = coachPreferred) {
                        VMaxTextField(coachId, "Coach ID (e.g. S1, B1)", Modifier.fillMaxWidth()) { coachId = it.uppercase() }
                    }
                }
            }
            
            // 💳 SECTION: Payment Details
            item {
                VMaxCard(title = "💳 Payment Method") {
                    VMaxDropdown(payment.paymentCategory, "Category", listOf("e-Wallets", "UPI ID", "UPI apps"), Modifier.fillMaxWidth()) {
                        payment = payment.copy(paymentCategory = it)
                    }
                    
                    AnimatedVisibility(visible = payment.paymentCategory == "UPI apps") {
                        VMaxDropdown(payment.paymentMethod, "Select App", listOf("PhonePe", "Paytm", "CRED UPI"), Modifier.fillMaxWidth()) {
                            payment = payment.copy(paymentMethod = it)
                        }
                    }

                    AnimatedVisibility(visible = payment.paymentCategory == "UPI ID") {
                        VMaxTextField(payment.upiId, "Enter UPI ID", Modifier.fillMaxWidth()) { payment = payment.copy(upiId = it) }
                    }

                    Row {
                        VMaxCheckbox("Manual Pay", payment.manualPayment) { payment = payment.copy(manualPayment = it) }
                        VMaxCheckbox("Autofill OTP", payment.autofillOTP) { payment = payment.copy(autofillOTP = it) }
                    }
                }
            }
        }
        
        // 🔥 ARM BUTTON
        Button(
            onClick = {
                val task = SniperTask(
                    taskId = UUID.randomUUID().toString(),
                    trainNumber = trainNo, travelClass = travelClass, quota = selectedQuota,
                    passengers = passengers.filter { it.isFilled() }, payment = payment,
                    autoUpgradation = autoUpgradation, confirmBerthsOnly = confirmBerthsOnly,
                    insurance = insurance, coachPreferred = coachPreferred, coachId = coachId
                )
                
                val intent = Intent(context, WorkflowEngine::class.java).apply {
                    action = WorkflowEngine.ACTION_START
                    putExtra(WorkflowEngine.EXTRA_TASK, task)
                }
                ContextCompat.startForegroundService(context, intent)
                Toast.makeText(context, "🎯 Sniper Armed & Ready!", Toast.LENGTH_SHORT).show()
            },
            enabled = isEnabled && trainNo.length == 5,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            Text("🔥 ARM THE SNIPER", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

// --- 🛠️ REUSABLE UI COMPONENTS (The Upgrade) ---

@Composable
fun StatusHeader(isEnabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = if (isEnabled) Color(0xFF1B5E20) else Color(0xFFB71C1C),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            if (isEnabled) "🟢 VMAX Service Active" else "🔴 Service OFF (Tap to fix)",
            color = Color.White, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun VMaxCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun VMaxTextField(value: String, label: String, modifier: Modifier, isNum: Boolean = false, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = Color.Gray) },
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = if (isNum) KeyboardType.Number else KeyboardType.Text)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VMaxDropdown(value: String, label: String, options: List<String>, modifier: Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label, color = Color.Gray) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
fun VMaxCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun PassengerCard(index: Int, passenger: PassengerData, canRemove: Boolean, onUpdate: (PassengerData) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A35))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("👤 Passenger ${index + 1}", color = Color.White, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VMaxTextField(passenger.name, "Name", Modifier.weight(1f)) { onUpdate(passenger.copy(name = it)) }
                VMaxTextField(passenger.age, "Age", Modifier.weight(0.4f), true) { onUpdate(passenger.copy(age = it)) }
            }
            Row {
                VMaxTextField(passenger.nationality, "Nationality", Modifier.weight(1f)) { onUpdate(passenger.copy(nationality = it)) }
                VMaxDropdown(passenger.gender, "Gender", listOf("Male", "Female", "Trans"), Modifier.weight(1f)) { onUpdate(passenger.copy(gender = it)) }
            }
            if (canRemove) {
                Text("Remove", color = Color.Red, modifier = Modifier.clickable { onRemove() })
            }
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    return enabled.contains(expected)
}
