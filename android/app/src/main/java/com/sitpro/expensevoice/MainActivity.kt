package com.sitpro.expensevoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sitpro.expensevoice.capture.RecordActivity
import com.sitpro.expensevoice.ui.AppTheme
import com.sitpro.expensevoice.ui.MonthScreen
import com.sitpro.expensevoice.ui.MonthViewModel
import com.sitpro.expensevoice.ui.SettingsScreen
import com.sitpro.expensevoice.ui.SettingsViewModel
import com.sitpro.expensevoice.ui.TodayScreen
import com.sitpro.expensevoice.ui.TodayViewModel

private enum class Tab { TODAY, MONTH, SETTINGS }

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestMissingPermissions()

        val c = container
        val factory = viewModelFactory {
            initializer { TodayViewModel(c) }
            initializer { MonthViewModel(c) }
            initializer { SettingsViewModel(c) }
        }

        setContent {
            AppTheme {
                val todayVm: TodayViewModel = viewModel(factory = factory)
                val monthVm: MonthViewModel = viewModel(factory = factory)
                val settingsVm: SettingsViewModel = viewModel(factory = factory)
                var tab by rememberSaveable { mutableStateOf(if (c.settings.current.isConfigured) Tab.TODAY else Tab.SETTINGS) }
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    todayVm.messages.collect { msg ->
                        val result = snackbar.showSnackbar(
                            msg.text,
                            actionLabel = msg.undoTransactionId?.let { "Hoàn tác" },
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) msg.undoTransactionId?.let(todayVm::restore)
                    }
                }

                MainScaffold(
                    tab = tab,
                    onTab = {
                        if (it == Tab.TODAY) todayVm.refresh()
                        if (it == Tab.MONTH) monthVm.refresh()
                        tab = it
                    },
                    snackbar = snackbar,
                    onRecord = { startActivity(Intent(this, RecordActivity::class.java)) },
                    today = { TodayScreen(todayVm, it) },
                    month = { MonthScreen(monthVm, it) },
                    settings = { SettingsScreen(settingsVm, it) },
                )
            }
        }
    }

    private fun requestMissingPermissions() {
        val missing = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun MainScaffold(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState,
    onRecord: () -> Unit,
    today: @androidx.compose.runtime.Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
    month: @androidx.compose.runtime.Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
    settings: @androidx.compose.runtime.Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.SETTINGS) "Cài đặt" else "Chi tiêu") },
                navigationIcon = {
                    if (tab == Tab.SETTINGS) {
                        IconButton(onClick = { onTab(Tab.TODAY) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                    }
                },
                actions = {
                    if (tab != Tab.SETTINGS) {
                        IconButton(onClick = { onTab(Tab.SETTINGS) }) { Icon(Icons.Filled.Settings, "Cài đặt") }
                    }
                },
            )
        },
        bottomBar = {
            if (tab != Tab.SETTINGS) {
                NavigationBar {
                    NavigationBarItem(tab == Tab.TODAY, { onTab(Tab.TODAY) }, { Icon(Icons.Filled.Today, null) }, label = { Text("Hôm nay") })
                    NavigationBarItem(tab == Tab.MONTH, { onTab(Tab.MONTH) }, { Icon(Icons.Filled.CalendarMonth, null) }, label = { Text("Tháng này") })
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.TODAY) FloatingActionButton(onClick = onRecord) { Icon(Icons.Filled.Mic, "Ghi âm") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (tab) {
            Tab.TODAY -> today(padding)
            Tab.MONTH -> month(padding)
            Tab.SETTINGS -> settings(padding)
        }
    }
}
