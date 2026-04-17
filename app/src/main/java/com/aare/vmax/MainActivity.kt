package com.aare.vmax

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.aare.vmax.data.repository.PassengerRepository
import com.aare.vmax.data.datastore.ConfigStore
import com.aare.vmax.presentation.screen.ControlRoomScreen
import com.aare.vmax.presentation.viewmodel.ControlRoomViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ControlRoomViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // -----------------------------
        // DATA LAYER
        // -----------------------------
        val passengerRepo = PassengerRepository(applicationContext)
        val configStore = ConfigStore(applicationContext)

        // -----------------------------
        // VIEWMODEL FACTORY (SAFE WAY)
        // -----------------------------
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ControlRoomViewModel(passengerRepo, configStore) as T
            }
        }

        viewModel = ViewModelProvider(this, factory)[ControlRoomViewModel::class.java]

        // -----------------------------
        // UI
        // -----------------------------
        setContent {
            ControlRoomScreen(viewModel = viewModel)
        }
    }
}
