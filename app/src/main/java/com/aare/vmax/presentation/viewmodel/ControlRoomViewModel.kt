package com.aare.vmax.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aare.vmax.data.repository.Passenger
import com.aare.vmax.data.repository.PassengerRepository
import com.aare.vmax.data.datastore.BookingConfig
import com.aare.vmax.data.datastore.ConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// -----------------------------
// UI STATE (UPDATED)
// -----------------------------
data class ControlRoomState(
    val status: String = "IDLE",
    val isLoading: Boolean = false,
    val name: String = "",
    val age: String = "",
    val gender: String = "",
    val berth: String = "",
    val trainNumber: String = "",
    val bookingClass: String = "SL"   // ✔ NEW FIELD ADDED
)

class ControlRoomViewModel(
    private val passengerRepo: PassengerRepository,
    private val configStore: ConfigStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlRoomState())
    val uiState: StateFlow<ControlRoomState> = _uiState

    // -----------------------------
    // SAVE PROFILE (FULL DYNAMIC)
    // -----------------------------
    fun saveProfile(
        name: String,
        age: Int,
        gender: String,
        berth: String,
        trainNo: String,
        bookingClass: String   // ✔ NOW DYNAMIC
    ) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoading = true)

            try {

                // Passenger save
                passengerRepo.savePassenger(
                    Passenger(name, age, gender, berth)
                )

                // Config save (FULL DYNAMIC NOW)
                configStore.saveConfig(
                    BookingConfig(
                        trainNumber = trainNo,
                        bookingClass = bookingClass
                    )
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    status = "Profile Saved ✅",
                    name = name,
                    age = age.toString(),
                    gender = gender,
                    berth = berth,
                    trainNumber = trainNo,
                    bookingClass = bookingClass   // ✔ UI SYNC
                )

            } catch (e: Exception) {

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    status = "Save Failed ❌"
                )
            }
        }
    }
}
