package com.aare.vmax

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.aare.vmax.core.engine.WorkflowEngine

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        Text("🎯 VMAX Sniper Pro", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("IRCTC Rail Connect Automation", color = Color.Gray, fontSize = 14.sp)
        
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isServiceEnabled) Color(0xFF2E7D32) else Color(0xFFC62828)),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if (isServiceEnabled) "🟢 SERVICE ACTIVE" else "🔴 SERVICE INACTIVE",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }
        }

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

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(passengers) { index, passenger ->
                PassengerCard(
                    index = index,
                    data = passenger,
                    onDataChanged = { updated -> passengers[index] = updated }
                )
            }
        }

        Text(
            text = "📦 Quota: $selectedQuota | Max: $maxPassengers passengers",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
        ) {
            Text("OPEN ACCESSIBILITY SETTINGS", fontWeight = FontWeight.Bold)
        }
        
        // 🔥 ARM THE SNIPER बटन (इंजन को डेटा भेजने के लिए तैयार)
        Button(
            onClick = {
                val intent = Intent(context, WorkflowEngine::class.java).apply {
                    action = WorkflowEngine.ACTION_START_SNIPER
                    putExtra("PASSENGER_LIST", java.util.ArrayList(passengers))
                }
                context.startService(intent)
                android.widget.Toast.makeText(context, "🎯 SNIPER ARMED! अब IRCTC ऐप खोलें...", android.widget.Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            Text("🔥 ARM THE SNIPER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ✅ Serializable जोड़ दिया गया है ताकि डेटा सर्विस तक जा सके
data class PassengerData(
    val name: String,
    val age: String,
    val gender: String,
    val meal: String,
    val berthPreference: String = "No Preference",
    val bedRoll: Boolean = false
) : java.io.Serializable

@OptIn(ExperimentalMaterial3Api::class)
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
            
            OutlinedTextField(
                value = data.age,
                onValueChange = { if (it.all { char -> char.isDigit() }) onDataChanged(data.copy(age = it)) },
                label = { Text("Age") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                singleLine = true
            )
            
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

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    return enabled.contains(expected)
}
