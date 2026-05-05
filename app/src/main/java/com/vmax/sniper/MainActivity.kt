package com.vmax.sniper

import android.Manifest
import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.vmax.sniper.core.engine.WorkflowEngine
import com.vmax.sniper.core.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.Calendar

class MainActivity : ComponentActivity() {
    
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WorkflowEngine.ACTION_SERVICE_STOPPED) {
                getSharedPreferences("VMAX_DATA", Context.MODE_PRIVATE).edit()
                    .putBoolean("SNIPER_RUNNING", false).apply()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        
        LocalBroadcastManager.getInstance(this).registerReceiver(
            stateReceiver,
            IntentFilter(WorkflowEngine.ACTION_SERVICE_STOPPED)
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    VmaxVIPScreen()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateReceiver)
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

fun getDefaultDate(): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    return String.format("%02d-%02d-%04d", 
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.YEAR))
}

fun isValidDate(date: String): Boolean {
    return try {
        val df = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.ENGLISH)
        df.isLenient = false
        df.parse(date)
        true
    } catch (e: Exception) {
        false
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmaxVIPScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sharedPrefs = context.getSharedPreferences("VMAX_DATA", Context.MODE_PRIVATE)
    
    var isEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var isSniperRunning by remember { 
        mutableStateOf(sharedPrefs.getBoolean("SNIPER_RUNNING", false))
    }
    
    var isBatteryOptimizationIgnored by remember { mutableStateOf(false) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isEnabled = isAccessibilityServiceEnabled(context)
                isSniperRunning = sharedPrefs.getBoolean("SNIPER_RUNNING", false)
                
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isBatteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                } else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    var trainNumber by remember { mutableStateOf(sharedPrefs.getString("TRAIN_NO", "") ?: "") }
    var journeyDate by remember { mutableStateOf(sharedPrefs.getString("JOURNEY_DATE", getDefaultDate()) ?: getDefaultDate()) }
    var latency by remember { mutableStateOf(sharedPrefs.getString("LATENCY", "150") ?: "150") }
    var triggerTime by remember { mutableStateOf(sharedPrefs.getString("TRIGGER_TIME", "") ?: "") }
    
    var selectedClass by remember { mutableStateOf(sharedPrefs.getString("TARGET_CLASS", "SL") ?: "SL") }
    var selectedQuota by remember { mutableStateOf(sharedPrefs.getString("QUOTA", "Tatkal") ?: "Tatkal") }
    val classes = listOf("1A", "2A", "3A", "CC", "3E", "EC", "SL", "FC", "2S")
    val quotas = listOf("Tatkal", "General")
    
    val passengers = remember { mutableStateListOf<PassengerData>() }
    val children = remember { mutableStateListOf<ChildData>() }
    
    var autoUpgradation by remember { mutableStateOf(false) }
    var confirmBerthsOnly by remember { mutableStateOf(false) }
    var insurance by remember { mutableStateOf(true) }
    var bookingOption by remember { mutableStateOf(0) }
    val bookingOptions = listOf("None", "Same Coach", "1 Lower Berth", "2 Lower Berths")
    
    var coachPreferred by remember { mutableStateOf(false) }
    var coachId by remember { mutableStateOf("") }
    var mobileNo by remember { mutableStateOf("") }
    
    var paymentCategory by remember { mutableStateOf(PaymentCategory.BHIM_UPI) }
    var upiId by remember { mutableStateOf("") }
    var manualPayment by remember { mutableStateOf(false) }
    var autofillOTP by remember { mutableStateOf(true) }
    var captchaAutofill by remember { mutableStateOf(true) }
    
    var expandedClass by remember { mutableStateOf(false) }
    var expandedQuota by remember { mutableStateOf(false) }
    var expandedPayment by remember { mutableStateOf(false) }
    var expandedBookingOpt by remember { mutableStateOf(false) }
    
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                journeyDate = String.format("%02d-%02d-%04d", dayOfMonth, month + 1, year)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
    }
    
    // ✅ FIXED: Load ALL saved data
    LaunchedEffect(Unit) {
        // Load all text fields
        trainNumber = sharedPrefs.getString("TRAIN_NO", "") ?: ""
        journeyDate = sharedPrefs.getString("JOURNEY_DATE", getDefaultDate()) ?: getDefaultDate()
        latency = sharedPrefs.getString("LATENCY", "150") ?: "150"
        triggerTime = sharedPrefs.getString("TRIGGER_TIME", "") ?: ""
        selectedClass = sharedPrefs.getString("TARGET_CLASS", "SL") ?: "SL"
        selectedQuota = sharedPrefs.getString("QUOTA", "Tatkal") ?: "Tatkal"
        
        // Load passenger data
        val savedPassengersJson = sharedPrefs.getString("PASSENGERS_JSON", "")
        if (savedPassengersJson.isNullOrEmpty()) {
            if (passengers.isEmpty()) repeat(4) { passengers.add(PassengerData()) }
            if (children.isEmpty()) repeat(2) { children.add(ChildData()) }
        } else {
            try {
                val parsed = Json.decodeFromString<List<PassengerData>>(savedPassengersJson)
                passengers.clear()
                passengers.addAll(parsed)
                if (passengers.isEmpty()) repeat(4) { passengers.add(PassengerData()) }
            } catch (e: Exception) {
                repeat(4) { passengers.add(PassengerData()) }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VMAX SNIPER PRO", color = Color(0xFF7E57C2), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Precision Refresh | Tatkal Optimized", color = Color(0xFF4CAF50), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (isEnabled) Color(0xFF2E7D32) else Color.DarkGray)
        ) {
            Text(if (isEnabled) "Accessibility Service ON" else "Enable Accessibility Service")
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        if (!isBatteryOptimizationIgnored) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C))
            ) {
                Text(
                    "Battery Optimization Active! Tap to disable for better performance",
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("TIMING OPTIMIZATION", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = latency,
                    onValueChange = { 
                        if (it.isEmpty()) latency = ""
                        else if (it.all { c -> c.isDigit() } && it.length <= 3) latency = it 
                    },
                    label = { Text("Latency Offset (ms)") },
                    placeholder = { Text("150") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("100ms (5G) | 150ms (4G) | 200ms (Slow)", color = Color.Gray) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = triggerTime,
                    onValueChange = { triggerTime = it },
                    label = { Text("Fire Time (HH:MM:SS)") },
                    placeholder = { Text("10:00:00 या 11:00:00 या 08:30:00") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("खाली छोड़ो तो class के हिसाब से auto set होगा", color = Color.Gray) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("JOURNEY DETAILS", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                
                OutlinedTextField(value = trainNumber, onValueChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) trainNumber = it },
                    label = { Text("Train Number (5 digits)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = trainNumber.isNotBlank() && trainNumber.length != 5)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = journeyDate,
                        onValueChange = { journeyDate = it },
                        label = { Text("Journey Date (DD-MM-YYYY)") },
                        placeholder = { Text("27-12-2025") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = journeyDate.isNotBlank() && !isValidDate(journeyDate)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { datePickerDialog.show() }) {
                        Text("📅")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CLASS & QUOTA", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                
                ExposedDropdownMenuBox(expanded = expandedClass, onExpandedChange = { expandedClass = it }) {
                    OutlinedTextField(value = selectedClass, onValueChange = {}, readOnly = true, label = { Text("Target Class") }, 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClass) }, 
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expandedClass, onDismissRequest = { expandedClass = false }) {
                        classes.forEach { className -> 
                            DropdownMenuItem(text = { Text(className) }, onClick = { selectedClass = className; expandedClass = false })
                        }
                    }
                }
                
                ExposedDropdownMenuBox(expanded = expandedQuota, onExpandedChange = { expandedQuota = it }) {
                    OutlinedTextField(value = selectedQuota, onValueChange = {}, readOnly = true, label = { Text("Quota") }, 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedQuota) }, 
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expandedQuota, onDismissRequest = { expandedQuota = false }) {
                        quotas.forEach { quotaName -> 
                            DropdownMenuItem(text = { Text(quotaName) }, onClick = { selectedQuota = quotaName; expandedQuota = false })
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("PASSENGERS (Adult)", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
        passengers.forEachIndexed { index, p ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A35)), modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, modifier = Modifier.width(25.dp))
                        OutlinedTextField(value = p.name, onValueChange = { passengers[index] = p.copy(name = it) }, 
                            label = { Text("Name") }, modifier = Modifier.weight(2f), singleLine = true)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = p.age, onValueChange = { 
                            if (it.isEmpty() || it.all { c -> c.isDigit() }) passengers[index] = p.copy(age = it) 
                        }, 
                            label = { Text("Age") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = p.gender, onValueChange = { passengers[index] = p.copy(gender = it) }, 
                            label = { Text("Gender (M/F/T)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = p.berthPreference, onValueChange = { passengers[index] = p.copy(berthPreference = it) }, 
                            label = { Text("Berth") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = p.meal, onValueChange = { passengers[index] = p.copy(meal = it) }, 
                            label = { Text("Meal") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { passengers.add(PassengerData()) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B21A8))) { Text("+ Add Passenger") }
            Button(onClick = { if (passengers.size > 1) passengers.removeAt(passengers.size - 1) }, 
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))) { Text("- Remove Last") }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("INFANTS (Optional)", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
        children.forEachIndexed { index, c ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A35)), modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = c.name, onValueChange = { children[index] = c.copy(name = it) }, 
                            label = { Text("Infant Name") }, modifier = Modifier.weight(2f), singleLine = true)
                        OutlinedTextField(value = c.ageRange, onValueChange = { children[index] = c.copy(ageRange = it) }, 
                            label = { Text("Age (1-4)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = c.gender, onValueChange = { children[index] = c.copy(gender = it) }, 
                            label = { Text("Gender") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { children.add(ChildData()) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B21A8))) { Text("+ Add Infant") }
            Button(onClick = { if (children.isNotEmpty()) children.removeAt(children.size - 1) }, 
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))) { Text("- Remove Infant") }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("BOOKING OPTIONS", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Checkbox(checked = autoUpgradation, onCheckedChange = { autoUpgradation = it })
                        Text("Auto Upgradation") 
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Checkbox(checked = confirmBerthsOnly, onCheckedChange = { confirmBerthsOnly = it })
                        Text("Confirm Berths") 
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Insurance: ", fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = insurance, onClick = { insurance = true }, label = { Text("Yes") })
                        FilterChip(selected = !insurance, onClick = { insurance = false }, label = { Text("No") })
                    }
                }
                ExposedDropdownMenuBox(expanded = expandedBookingOpt, onExpandedChange = { expandedBookingOpt = it }) {
                    OutlinedTextField(value = bookingOptions[bookingOption], onValueChange = {}, readOnly = true, 
                        label = { Text("Booking Condition") }, 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBookingOpt) }, 
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expandedBookingOpt, onDismissRequest = { expandedBookingOpt = false }) {
                        bookingOptions.forEachIndexed { idx, opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = { bookingOption = idx; expandedBookingOpt = false })
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("COACH & MOBILE", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Checkbox(checked = coachPreferred, onCheckedChange = { coachPreferred = it })
                    Text("Coach Preferred")
                }
                if (coachPreferred) {
                    OutlinedTextField(value = coachId, onValueChange = { if (it.length <= 2) coachId = it.uppercase() }, 
                        label = { Text("Coach ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                OutlinedTextField(value = mobileNo, onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) mobileNo = it }, 
                    label = { Text("Mobile Number (Optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = mobileNo.isNotBlank() && mobileNo.length != 10)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("PAYMENT METHOD", color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                ExposedDropdownMenuBox(expanded = expandedPayment, onExpandedChange = { expandedPayment = it }) {
                    OutlinedTextField(value = paymentCategory.display, onValueChange = {}, readOnly = true, 
                        label = { Text("Payment Category") }, 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayment) }, 
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expandedPayment, onDismissRequest = { expandedPayment = false }) {
                        PaymentCategory.entries.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.display) }, onClick = { paymentCategory = cat; expandedPayment = false })
                        }
                    }
                }
                if (paymentCategory == PaymentCategory.UPI_ID) {
                    OutlinedTextField(value = upiId, onValueChange = { upiId = it }, 
                        label = { Text("UPI ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = manualPayment, onCheckedChange = { manualPayment = it }); Text("Manual Payment") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = autofillOTP, onCheckedChange = { autofillOTP = it }); Text("Auto OTP") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = captchaAutofill, onCheckedChange = { captchaAutofill = it }); Text("Auto Captcha") }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        val finalTriggerTime = if (triggerTime.isBlank()) {
            if (listOf("1A","2A","3A","CC","3E","EC").contains(selectedClass)) "10:00:00" else "11:00:00"
        } else triggerTime
        Text(text = "SNIPER WILL FIRE AT $finalTriggerTime", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (isSniperRunning) {
                    isSniperRunning = false
                    sharedPrefs.edit().putBoolean("SNIPER_RUNNING", false).apply()
                    Toast.makeText(context, "Sniper Stopped!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                if (!isEnabled) {
                    Toast.makeText(context, "Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (trainNumber.length != 5 || !trainNumber.all { it.isDigit() }) {
                    Toast.makeText(context, "Train number must be 5 digits!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (!isValidDate(journeyDate)) {
                    Toast.makeText(context, "Invalid journey date! Use DD-MM-YYYY format", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (mobileNo.isNotBlank() && mobileNo.length != 10) {
                    Toast.makeText(context, "Mobile number must be 10 digits!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                val validPassengers = passengers.filter { it.name.isNotBlank() && it.age.isNotBlank() }
                if (validPassengers.isEmpty()) {
                    Toast.makeText(context, "Fill at least one passenger with Name & Age!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                sharedPrefs.edit().apply {
                    putString("TRAIN_NO", trainNumber)
                    putString("JOURNEY_DATE", journeyDate)
                    putString("LATENCY", if (latency.isEmpty()) "150" else latency)
                    putString("TRIGGER_TIME", triggerTime)
                    putString("TARGET_CLASS", selectedClass)
                    putString("QUOTA", selectedQuota)
                    putString("PASSENGERS_JSON", Json.encodeToString(validPassengers))
                    putBoolean("SNIPER_RUNNING", true)
                }.apply()
                
                val finalLatency = if (latency.isEmpty()) 150 else latency.toIntOrNull() ?: 150
                
                val task = SniperTask(
                    triggerTime = finalTriggerTime,
                    msAdvance = finalLatency,
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
                    putExtra(WorkflowEngine.EXTRA_TASK, task)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isSniperRunning = true
                Toast.makeText(context, "SNIPER ARMED for $finalTriggerTime!", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth().height(65.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSniperRunning) Color(0xFF424242) else Color(0xFFD32F2F)
            )
        ) {
            Text(
                if (isSniperRunning) "STOP SNIPER" else "ARM THE SNIPER", 
                fontWeight = FontWeight.Bold, 
                fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}
