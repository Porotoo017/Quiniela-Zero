package com.quinielazero.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.quinielazero.app.ui.QuinielaViewModel
import com.quinielazero.app.ui.screens.QuinielaZeroRoot
import com.quinielazero.app.ui.theme.QuinielaZeroTheme

class MainActivity : ComponentActivity() {
    private val viewModel: QuinielaViewModel by viewModels()

    private val requestNotifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val state by viewModel.uiState.collectAsState()
            QuinielaZeroTheme(themeMode = state.config.themeMode) {
                QuinielaZeroRoot(
                    state = state,
                    viewModel = viewModel,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }
}
