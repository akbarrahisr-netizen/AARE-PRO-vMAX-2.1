package com.aare.vmax

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.core.app.ActivityCompat
import com.aare.vmax.core.engine.WorkflowEngine
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SMS permissions for OTP auto-read
        ActivityCompat.requestPermissions(this, 
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 101)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh service status on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Quota Dropdown State
    var expanded by remember { mutableStateOf(false) }
    var selectedQuota by remember { mutableStateOf("General") }
    
    val maxPassengers = when (selectedQuota) {
        "Tatkal", "Premium Tatkal" -> 4
        "General" -> 6
        else -> 2
    }
    
    val passengers = remember { mutableStateListOf<PassengerData>() }
    
    LaunchedEffect(maxPassengers) {
        if (passengers.size < maxPassengers) {
            repeat(maxPassengers - passengers.size) {
                passengers.add(PassengerData(name = "", age = "", gender = "Male", meal = "No Food"))
            }
        } else if (passengers.size > maxPassengers) {
            passengers.removeRange(maxPassengers, passengers.size)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text("🎯 VMAX Sniper Pro", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("IRCTC Rail Connect Automation", color = Color.Gray, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(8.dp))

        // Service status card
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isServiceEnabled) Color(0xFF2E7D32) else Color(0xFFC62828)),
            modifier = Modifier.fillMaxWidth().height(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if (isServiceEnabled) "🟢 SERVICE ACTIVE" else "🔴 SERVICE INACTIVE",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
        }

        // Quota Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            TextField(
                value = selectedQuota,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = ExposedDropdownMenuDefaults.textFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                listOf("General", "Tatkal", "Premium Tatkal", "Ladies", "Lower Berth/Sr. Citizen", "Divyangjan")
                    .forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedQuota = option
                                expanded = false
                            }
                        )
                    }
            }
        }

        // Dynamic passenger list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(passengers) { index, passenger ->
                PassengerCard(
                    index = index,
                    data = passenger,
                    onDataChanged = { updated ->
                        passengers[index] = updated
                    }
                )
            }
        }

        // ⭐ NEW: ARM THE SNIPER BUTTON
        Button(
            onClick = {
                if (!isServiceEnabled) {
                    Toast.makeText(context, "❌ Accessibility Service is not enabled! Please enable it first.", Toast.LENGTH_LONG).show()
                    return@Button
                }
                // Send data to WorkflowEngine
                val intent = Intent(context, WorkflowEngine::class.java).apply {
                    putExtra("QUOTA", selectedQuota)
                    putExtra("PASSENGER_COUNT", passengers.size)
                    // Pass serializable list
                    putExtra("PASSENGERS", ArrayList(passengers.map { it.toBundle() }))
                }
                context.startService(intent)
                Toast.makeText(context, "🎯 SNIPER ARMED! Switching to IRCTC...", Toast.LENGTH_SHORT).show()
                
                // Optional: Launch IRCTC app
                try {
                    context.startActivity(context.packageManager.getLaunchIntentForPackage("cris.org.in.prs.ima"))
                } catch (e: Exception) {
                    Toast.makeText(context, "⚠️ IRCTC app not found. Please open manually.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("🔫 ARM THE SNIPER", fontWeight = FontWeight.Bold)
        }

        // Status text
        Text(
            text = "📦 Quota: $selectedQuota | Max: $maxPassengers passengers",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Open settings button
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
        ) {
            Text("OPEN ACCESSIBILITY SETTINGS", fontWeight = FontWeight.Bold)
        }
    }
}

// Immutable data class
data class PassengerData(
    val name: String,
    val age: String,
    val gender: String,
    val meal: String,
    val berthPreference: String = "No Preference",
    val bedRoll: Boolean = false
) {
    // Helper to convert to Bundle for Intent
    fun toBundle(): Bundle = Bundle().apply {
        putString("name", name)
        putString("age", age)
        putString("gender", gender)
        putString("meal", meal)
        putString("berthPreference", berthPreference)
        putBoolean("bedRoll", bedRoll)
    }
    
    companion object {
        fun fromBundle(bundle: Bundle): PassengerData = PassengerData(
            name = bundle.getString("name") ?: "",
            age = bundle.getString("age") ?: "",
            gender = bundle.getString("gender") ?: "Male",
            meal = bundle.getString("meal") ?: "No Food",
            berthPreference = bundle.getString("berthPreference") ?: "No Preference",
            bedRoll = bundle.getBoolean("bedRoll")
        )
    }
}

@Composable
fun PassengerCard(
    index: Int,
    data: PassengerData,
    onDataChanged: (PassengerData) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Passenger ${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = data.name,
                onValueChange = { onDataChanged(data.copy(name = it)) },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // ✅ Age field – now number keyboard only
            OutlinedTextField(
                value = data.age,
                onValueChange = { 
                    if (it.all { char -> char.isDigit() }) 
                        onDataChanged(data.copy(age = it)) 
                },
                label = { Text("Age") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            // Gender dropdown
            var genderExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = genderExpanded,
                onExpandedChange = { genderExpanded = it }
            ) {
                TextField(
                    value = data.gender,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text("Gender") }
                )
                ExposedDropdownMenu(
                    expanded = genderExpanded,
                    onDismissRequest = { genderExpanded = false }
                ) {
                    listOf("Male", "Female", "Transgender").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onDataChanged(data.copy(gender = option))
                                genderExpanded = false
                            }
                        )
                    }
                }
            }
            
            // Berth Preference dropdown
            var berthExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = berthExpanded,
                onExpandedChange = { berthExpanded = it }
            ) {
                TextField(
                    value = data.berthPreference,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = berthExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text("Berth Preference") }
                )
                ExposedDropdownMenu(
                    expanded = berthExpanded,
                    onDismissRequest = { berthExpanded = false }
                ) {
                    listOf("No Preference", "Lower", "Middle", "Upper", "Side Lower", "Side Upper").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onDataChanged(data.copy(berthPreference = option))
                                berthExpanded = false
                            }
                        )
                    }
                }
            }
            
            // Meal dropdown
            var mealExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = mealExpanded,
                onExpandedChange = { mealExpanded = it }
            ) {
                TextField(
                    value = data.meal,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text("Meal Preference") }
                )
                ExposedDropdownMenu(
                    expanded = mealExpanded,
                    onDismissRequest = { mealExpanded = false }
                ) {
                    listOf("No Food", "Veg", "Non-Veg", "Jain").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onDataChanged(data.copy(meal = option))
                                mealExpanded = false
                            }
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = data.bedRoll,
                    onCheckedChange = { onDataChanged(data.copy(bedRoll = it)) }
                )
                Text("Bed Roll Required", color = Color.White)
            }
        }
    }
}

// Check if accessibility service is enabled
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    return enabled.contains(expected)
}
