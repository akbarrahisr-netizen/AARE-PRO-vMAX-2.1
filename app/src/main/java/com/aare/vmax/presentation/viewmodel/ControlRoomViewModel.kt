package com.aare.vmax.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aare.vmax.data.datastore.BookingConfig
import com.aare.vmax.data.datastore.ConfigStore
import com.aare.vmax.data.repository.Passenger
import com.aare.vmax.data.repository.PassengerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ControlRoomState(
    val status: String = "IDLE",
    val isLoading: Boolean = false,
    val isManualFillMode: Boolean = true
)

class ControlRoomViewModel(
    private val configStore: ConfigStore,
    private val passengerRepository: PassengerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlRoomState())
    val uiState: StateFlow<ControlRoomState> = _uiState

    // -----------------------------
    // SAVE ALL DATA
    // -----------------------------
    fun saveAllData(
        trainNo: String,
        pName: String,
        age: Int = 25,
        gender: String = "M",
        berth: String = "LB"
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    status = "SAVING...",
                    isLoading = true
                )

                configStore.saveConfig(
                    BookingConfig(
                        trainNumber = trainNo,
                        bookingClass = "SL"
                    )
                )

                passengerRepository.savePassenger(
                    Passenger(
                        name = pName,
                        age = age,
                        gender = gender,
                        berthPreference = berth
                    )
                )

                _uiState.value = _uiState.value.copy(
                    status = "PROFILE SAVED",
                    isLoading = false
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = "ERROR: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    // -----------------------------
    // TOGGLE MODE
    // -----------------------------
    fun toggleFillMode() {
        _uiState.value = _uiState.value.copy(
            isManualFillMode = !_uiState.value.isManualFillMode
        )
    }

    // -----------------------------
    // START SYSTEM (future orchestrator hook)
    // -----------------------------
    fun startSystem() {
        _uiState.value = _uiState.value.copy(
            status = "RUNNING..."
        )
    }

    // -----------------------------
    // STOP SYSTEM
    // -----------------------------
    fun stopSystem() {
        _uiState.value = _uiState.value.copy(
            status = "STOPPED"
        )
    }
}
