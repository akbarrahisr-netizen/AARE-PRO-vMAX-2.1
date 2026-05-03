package com.vmax.sniper

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat

import com.vmax.sniper.core.engine.WorkflowEngine
import com.vmax.sniper.core.model.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ActivityCompat.requestPermissions(this, 
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 101)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    VmaxVIPScreen()
                }
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, WorkflowEngine::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(expectedComponentName.flattenToString())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmaxVIPScreen() {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("VMAX_DATA", Context.MODE_PRIVATE)
    
    // ==================== 1. JOURNEY DETAILS ====================
    var trainNumber by remember { mutableStateOf(sharedPrefs.getString("TRAIN_NO", "") ?: "") }
    var journeyDate by remember { mutableStateOf(sharedPrefs.getString("JOURNEY_DATE", getDefaultDate()) ?: getDefaultDate()) }
    var latency by remember { mutableStateOf(sharedPrefs.getString("LATENCY", "400") ?: "400") }
    
    // ==================== 2. TRAVEL CLASS & QUOTA ====================
    var selectedClass by remember { mutableStateOf(sharedPrefs.getString("TARGET_CLASS", "SL") ?: "SL") }
    var selectedQuota by remember { mutableStateOf(sharedPrefs.getString("QUOTA", "Tatkal") ?: "Tatkal") }
    val classes = listOf("1A", "2A", "3A", "CC", "3E", "EC", "SL", "FC", "2S", "VS", "VC", "EV")
    val quotas = listOf("Tatkal", "General", "Ladies", "Senior Citizen")
    
    // ==================== 3. PASSENGERS ====================
    val passengers = remember { mutableStateListOf<PassengerData>() }
    val children = remember { mutableStateListOf<ChildData>() }
    
    // ==================== 4. BOOKING OPTIONS ====================
    var autoUpgradation by remember { mutableStateOf(false) }
    var confirmBerthsOnly by remember { mutableStateOf(false) }
    var insurance by remember { mutableStateOf(true) }
    var bookingOption by remember { mutableStateOf(0) } // 0=None, 1=Same Coach, 2=1 Lower, 3=2 Lower
    
    // ==================== 5. COACH & MOBILE ====================
    var coachPreferred by remember { mutableStateOf(false) }
    var coachId by remember { mutableStateOf("") }
    var mobileNo by remember { mutableStateOf("") }
    
    // ==================== 6. PAYMENT ====================
    var paymentCategory by remember { mutableStateOf(PaymentCategory.BHIM_UPI) }
    var upiId by remember { mutableStateOf("") }
    var manualPayment by remember { mutableStateOf(false) }
    var autofillOTP by remember { mutableStateOf(true) }
    var captchaAutofill by remember { mutableStateOf(true) }
    
    // ==================== UI STATES ====================
    var isSniperRunning by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var expandedClass by remember { mutableStateOf(false) }
    var expandedQuota by remember { mutableStateOf(false) }
    
    // Refresh service status on resume
    DisposableEffect(Unit) {
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.addObserver(lifecycleObserver)
        onDispose {
            (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.removeObserver(lifecycleObserver)
        }
    }
    
    LaunchedEffect(Unit) {
        // Load saved passengers from SharedPreferences
        val savedPassengers = sharedPrefs.getString("PASSENGERS", "")
        if (savedPassengers.isNullOrEmpty()) {
            if (passengers.isEmpty()) repeat(4) { passengers.add(PassengerData()) }
            if (children.isEmpty()) repeat(2) { children.add(ChildData()) }
        } else {
            // Parse saved passengers (format: "Name|Age|Gender|Berth|Meal")
            val parsed = savedPassengers.split(";").map { 
                val parts = it.split("|")
                PassengerData(
                    name = parts.getOrNull(0) ?: "",
                    age = parts.getOrNull(1) ?: "",
                    gender = parts.getOrNull(2) ?: "",
                    berthPreference = parts.getOrNull(3) ?: "",
                    meal = parts.getOrNull(4) ?: ""
                )
            }
            passengers.clear()
            passengers.addAll(parsed)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎯 VMAX SNIPER PRO", color = Color(0xFF7E57C2), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("भारत का सबसे तेज़ Tatkal Sniper", color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(20.dp))

        // ⚙️ ACCESSIBILITY BUTTON
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (isEnabled) Color(0xFF2E7D32) else Color.DarkGray)
        ) {
            Text(if (isEnabled) "✅ Accessibility Service ON" else "⚠️ Enable Accessibility Service")
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ==================== JOURNEY DETAILS CARD ====================
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🚂 JOURNEY DETAILS", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                
                OutlinedTextField(
                    value = trainNumber, 
                    onValueChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) trainNumber = it },
                    label = { Text("Train Number (5 digits)") }, 
                    modifier = Modifier.fillMaxWidth(), 
                    singleLine = true,
                    isError = trainNumber.isNotBlank() && trainNumber.length != 5
                )
                
                OutlinedTextField(
                    value = journeyDate, 
                    onValueChange = { if (it.matches(Regex("^\\d{0,2}-\\d{0,2}-\\d{0,4}\$"))) journeyDate = it },
                    label = { Text("Journey Date (DD-MM-YYYY)") },
                    placeholder = { Text("27-12-2025") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = latency, 
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) latency = it },
                    label = { Text("Latency Offset (ms) - Default 400") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ==================== TRAVEL CLASS & QUOTA CARD ====================
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎯 TRAVEL CLASS & QUOTA", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                
                // Target Class Dropdown
                ExposedDropdownMenuBox(expanded = expandedClass, onExpandedChange = { expandedClass = it }) {
                    OutlinedTextField(
                        value = selectedClass, 
                        onValueChange = {}, 
                        readOnly = true, 
                        label = { Text("Target Class") }, 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClass) }, 
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedClass, onDismissRequest = { expandedClass = false }) {
                        classes.forEach { className -> 
                            DropdownMenuItem(
                                text = { Text(className) }, 
                                onClick = { selectedClass = className; expandedClass = false }
                            )
                        }
                    }
                }
                
                // Quota Dropdown
                ExposedDropdownMenuBox(expanded = expandedQuota, onExpandedChange = { expandedQuota = it }) {
                    OutlinedTextField(
                        value = selectedQuota, 
                        onValueChange = {}, 
                        readOnly = true, 
                        label = { Text("Quota") }, 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedQuota) }, 
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedQuota, onDismissRequest = { expandedQuota = false }) {
                        quotas.forEach { quotaName -> 
                            DropdownMenuItem(
                                text = { Text(quotaName) }, 
                                onClick = { selectedQuota = quotaName; expandedQuota = false }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ==================== PASSENGERS SECTION ====================
        Text("👥 PASSENGERS (Adult)", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
        
        passengers.forEachIndexed { index, p ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A35)), modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Row 1: Name & Age
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, modifier = Modifier.width(25.dp))
                        OutlinedTextField(value = p.name, onValueChange = { passengers[index] = p.copy(name = it) }, label = { Text("Name") }, modifier = Modifier.weight(2f), singleLine = true)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = p.age, onValueChange = { passengers[index] = p.copy(age = it) }, label = { Text("Age") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    // Row 2: Gender, Berth, Meal
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = p.gender, onValueChange = { passengers[index] = p.copy(gender = it) }, label = { Text("Gender") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = p.berthPreference, onValueChange = { passengers[index] = p.copy(berthPreference = it) }, label = { Text("Berth") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = p.meal, onValueChange = { passengers[index] = p.copy(meal = it) }, label = { Text("Meal") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            }
        }
        
        // Add/Remove Passenger Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { passengers.add(PassengerData()) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B21A8))) {
                Text("+ Add Passenger")
            }
            Button(onClick = { if (passengers.size > 1) passengers.removeAt(passengers.size - 1) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))) {
                Text("- Remove Last")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ==================== CHILDREN SECTION ====================
        Text("👶 INFANTS (Optional)", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
        
        children.forEachIndexed { index, c ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A35)), modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = c.name, onValueChange = { children[index] = c.copy(name = it) }, label = { Text("Infant Name") }, modifier = Modifier.weight(2f), singleLine = true)
                        OutlinedTextField(value = c.ageRange, onValueChange = { children[index] = c.copy(ageRange = it) }, label = { Text("Age (1-4 yrs)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = c.gender, onValueChange = { children[index] = c.copy(gender = it) }, label = { Text("Gender") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { children.add(ChildData()) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B21A8))) {
                Text("+ Add Infant")
            }
            Button(onClick = { if (children.isNotEmpty()) children.removeAt(children.size - 1) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))) {
                Text("- Remove Infant")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ==================== BOOKING OPTIONS CARD ====================
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚙️ BOOKING OPTIONS", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoUpgradation, onCheckedChange = { autoUpgradation = it })
                        Text("Auto Upgradation", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmBerthsOnly, onCheckedChange = { confirmBerthsOnly = it })
                        Text("Confirm Berths Only", fontSize = 12.sp)
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Insurance: ", fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = insurance, onClick = { insurance = true }, label = { Text("Yes") })
                        FilterChip(selected = !insurance, onClick = { insurance = false }, label = { Text("No") })
                    }
                }
                
                // Booking Option Spinner
                var expandedBookingOpt by remember { mutableStateOf(false) }
                val bookingOptions = listOf("None", "Same Coach", "At least 1 Lower Berth", "2 Lower Berths")
                ExposedDropdownMenuBox(expanded = expandedBookingOpt, onExpandedChange = { expandedBookingOpt = it }) {
                    OutlinedTextField(
                        value = bookingOptions[bookingOption], 
                        onValueChange = {}, 
                        readOnly = true, 
                        label = { Text("Booking Condition") }, 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBookingOpt) }, 
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedBookingOpt, onDismissRequest = { expandedBookingOpt = false }) {
                        bookingOptions.forEachIndexed { idx, opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = { bookingOption = idx; expandedBookingOpt = false })
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ==================== COACH & MOBILE CARD ====================
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📱 COACH & MOBILE", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = coachPreferred, onCheckedChange = { coachPreferred = it })
                    Text("Coach Preferred", fontSize = 14.sp)
                }
                
                if (coachPreferred) {
                    OutlinedTextField(value = coachId, onValueChange = { if (it.length <= 2) coachId = it.uppercase() }, label = { Text("Coach ID (e.g., A, B, S1)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                
                OutlinedTextField(value = mobileNo, onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) mobileNo = it }, label = { Text("Mobile Number (Optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ==================== PAYMENT CARD ====================
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💳 PAYMENT METHOD", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                
                var expandedPayment by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expandedPayment, onExpandedChange = { expandedPayment = it }) {
                    OutlinedTextField(value = paymentCategory.display, onValueChange = {}, readOnly = true, label = { Text("Payment Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayment) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expandedPayment, onDismissRequest = { expandedPayment = false }) {
                        PaymentCategory.values().forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.display) }, onClick = { paymentCategory = cat; expandedPayment = false })
                        }
                    }
                }
                
                if (paymentCategory == PaymentCategory.UPI_ID) {
                    OutlinedTextField(value = upiId, onValueChange = { upiId = it }, label = { Text("UPI ID (e.g., name@okhdfcbank)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = manualPayment, onCheckedChange = { manualPayment = it })
                        Text("Manual Payment", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autofillOTP, onCheckedChange = { autofillOTP = it })
                        Text("Auto-fill OTP", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = captchaAutofill, onCheckedChange = { captchaAutofill = it })
                        Text("Auto Captcha", fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // ==================== SNIPER FIRING TIME INFO ====================
        val targetHour = if (classes.indexOf(selectedClass) < 6) 10 else 11
        Text(
            text = "🎯 SNIPER WILL FIRE AT $targetHour:00:00", 
            color = Color(0xFFFF9800), 
            fontWeight = FontWeight.Bold, 
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        // ==================== ARM / STOP BUTTON ====================
        if (isSniperRunning) {
            Button(onClick = { 
                isSniperRunning = false
                Toast.makeText(context, "🛑 Sniper Deactivated", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth().height(65.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                Text("🛑 STOP SNIPER", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        } else {
            Button(
                onClick = {
                    isEnabled = isAccessibilityServiceEnabled(context)
                    if (!isEnabled) {
                        Toast.makeText(context, "⚠️ Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    val validPassengers = passengers.filter { it.isFilled() }
                    if (validPassengers.isEmpty()) {
                        Toast.makeText(context, "❌ Please fill at least one passenger!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Save all data to SharedPreferences
                    sharedPrefs.edit().apply {
                        putString("TRAIN_NO", trainNumber)
                        putString("JOURNEY_DATE", journeyDate)
                        putString("LATENCY", latency)
                        putString("TARGET_CLASS", selectedClass)
                        putString("QUOTA", selectedQuota)
                        putString("PASSENGERS", validPassengers.joinToString(";") { "${it.name}|${it.age}|${it.gender}|${it.berthPreference}|${it.meal}" })
                    }.apply()

                    // Create SniperTask
                    val task = SniperTask(
                        triggerTime = "$targetHour:00:00",
                        msAdvance = latency.toIntOrNull() ?: 400,
                        trainNumber = trainNumber,
                        travelClass = getTravelClassEnum(selectedClass),
                        quota = getQuotaEnum(selectedQuota),
                        journeyDate = journeyDate,
                        passengers = validPassengers,
                        children = children.filter { it.name.isNotBlank() },
                        bookingOption = getBookingOptionEnum(bookingOption),
                        autoUpgradation = autoUpgradation,
                        confirmBerthsOnly = confirmBerthsOnly,
                        insurance = insurance,
                        coachPreferred = coachPreferred,
                        coachId = coachId,
                        mobileNo = mobileNo,
                        payment = PaymentDetails(
                            category = paymentCategory,
                            upiId = upiId,
                            manualPayment = manualPayment,
                            autofillOTP = autofillOTP
                        ),
                        captchaAutofill = captchaAutofill
                    )

                    val intent = Intent(context, WorkflowEngine::class.java).apply {
                        action = WorkflowEngine.ACTION_START_SNIPER
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            putExtra(WorkflowEngine.EXTRA_TASK, task)
                        } else {
                            putExtra(WorkflowEngine.EXTRA_TASK, task)
                        }
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }

                    isSniperRunning = true
                    Toast.makeText(context, "🚀 SNIPER ARMED for $targetHour:00:00!", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth().height(65.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text("🔥 ARM THE SNIPER", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ==================== HELPER FUNCTIONS ====================

fun getDefaultDate(): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
    return String.format("%02d-%02d-%04d", 
        calendar.get(java.util.Calendar.DAY_OF_MONTH),
        calendar.get(java.util.Calendar.MONTH) + 1,
        calendar.get(java.util.Calendar.YEAR))
}

fun getTravelClassEnum(className: String): TravelClass {
    return when (className) {
        "1A" -> TravelClass.AC_FIRST
        "2A" -> TravelClass.AC_2TIER
        "3A" -> TravelClass.AC_3TIER
        "SL" -> TravelClass.SLEEPER
        else -> TravelClass.SLEEPER
    }
}

fun getQuotaEnum(quotaName: String): Quota {
    return when (quotaName) {
        "Tatkal" -> Quota.TATKAL
        "General" -> Quota.GENERAL
        else -> Quota.TATKAL
    }
}

fun getBookingOptionEnum(option: Int): BookingOption {
    return when (option) {
        1 -> BookingOption.SAME_COACH
        2 -> BookingOption.ONE_LOWER_BERTH
        3 -> BookingOption.TWO_LOWER_BERTHS
        else -> BookingOption.NONE
    }
}
