package com.sitpro.expensevoice

import android.Manifest
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
import androidx.compose.material3.FabPosition
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.sitpro.expensevoice.ui.rememberTexts
import com.sitpro.expensevoice.ui.AppLanguage
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.sitpro.expensevoice.ui.resolve
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import com.sitpro.expensevoice.ui.PendingBanner
import com.sitpro.expensevoice.ui.PendingStrip
import com.sitpro.expensevoice.ui.recordDay
import com.sitpro.expensevoice.ui.today
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

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

                val texts = rememberTexts()
                LaunchedEffect(texts) {
                    // Each message carries the restore of the screen it came from (This month also reloads totals).
                    merge(
                        todayVm.messages.map { it to todayVm::restore },
                        monthVm.messages.map { it to monthVm::restore },
                    ).collect { (msg, restore) ->
                        val undo = msg.undoTransactionIds
                        val result = snackbar.showSnackbar(
                            texts.resolve(msg),
                            actionLabel = if (undo.isNotEmpty()) texts.get(R.string.undo) else null,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed && undo.isNotEmpty()) restore(undo)
                    }
                }
                val banner by c.pendingBanner.collectAsState(initial = null)
                val selected by todayVm.selected.collectAsState()
                val viewedDay by todayVm.day.collectAsState()
                val micDay = recordDay(viewedDay, today(), onTodayTab = tab == Tab.TODAY)
                var confirmDelete by remember { mutableStateOf(false) }

                MainScaffold(
                    tab = tab,
                    onTab = {
                        // The Today icon jumps back to today; Back from Settings returns to the day being viewed.
                        if (it == Tab.TODAY) if (tab == Tab.SETTINGS) todayVm.refresh() else todayVm.goToToday()
                        if (it == Tab.MONTH) monthVm.refresh()
                        tab = it
                    },
                    snackbar = snackbar,
                    banner = banner,
                    selectedCount = if (tab == Tab.TODAY) selected.size else 0,
                    onSelectAll = todayVm::selectAll,
                    onDeleteSelected = { confirmDelete = true },
                    onExitSelection = todayVm::clearSelection,
                    micDay = micDay,
                    onRecord = { startActivity(RecordActivity.intent(this, micDay)) },
                    today = { TodayScreen(todayVm, it) },
                    month = { MonthScreen(monthVm, it) },
                    settings = { SettingsScreen(settingsVm, it) },
                )

                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        text = { Text(texts.plural(R.plurals.confirm_delete_many, selected.size)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmDelete = false
                                todayVm.deleteSelected()
                            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
                    )
                }
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
    banner: PendingBanner?,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExitSelection: () -> Unit,
    micDay: LocalDate?,
    onRecord: () -> Unit,
    today: @androidx.compose.runtime.Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
    month: @androidx.compose.runtime.Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
    settings: @androidx.compose.runtime.Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            if (selectedCount > 0) {
                SelectionBar(selectedCount, onSelectAll, onDeleteSelected, onExitSelection)
            } else TopAppBar(
                title = { Text(stringResource(if (tab == Tab.SETTINGS) R.string.settings else R.string.app_name)) },
                navigationIcon = {
                    if (tab == Tab.SETTINGS) {
                        IconButton(onClick = { onTab(Tab.TODAY) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    }
                },
                actions = {
                    // VI / EN switch for demos; the system recreates the activity with the new language.
                    val context = LocalContext.current
                    val current = LocalConfiguration.current.locales[0].language
                    TextButton(onClick = { AppLanguage.set(context, AppLanguage.toggled(current)) }) {
                        Icon(Icons.Filled.Language, stringResource(R.string.language_switch))
                        Spacer(Modifier.width(4.dp))
                        Text(AppLanguage.label(current))
                    }
                    if (tab != Tab.SETTINGS) {
                        IconButton(onClick = { onTab(Tab.SETTINGS) }) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                    }
                },
            )
        },
        bottomBar = {
            if (tab != Tab.SETTINGS) {
                NavigationBar {
                    NavigationBarItem(tab == Tab.TODAY, { onTab(Tab.TODAY) }, { Icon(Icons.Filled.Today, null) }, label = { Text(stringResource(R.string.tab_today)) })
                    NavigationBarItem(tab == Tab.MONTH, { onTab(Tab.MONTH) }, { Icon(Icons.Filled.CalendarMonth, null) }, label = { Text(stringResource(R.string.tab_month)) })
                }
            }
        },
        floatingActionButton = {
            // Big, centered mic: recording is the main action of the app.
            // Hidden while selecting so a stray tap doesn't start a recording.
            if (tab != Tab.SETTINGS && selectedCount == 0) {
                if (micDay == null) {
                    FloatingActionButton(
                        onClick = onRecord,
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                    ) { Icon(Icons.Filled.Mic, stringResource(R.string.record), Modifier.size(40.dp)) }
                } else {
                    // Viewing an earlier day: a different colour and the date, so it's clear where it will go.
                    val date = rememberTexts().shortDate(micDay)
                    FloatingActionButton(
                        onClick = onRecord,
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.Mic, stringResource(R.string.record_for_day, date), Modifier.size(32.dp))
                            Text(date, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            banner?.let { PendingStrip(it, onOpenSettings = { onTab(Tab.SETTINGS) }) }
            Box(Modifier.weight(1f)) {
                val rest = PaddingValues(bottom = padding.calculateBottomPadding())
                when (tab) {
                    Tab.TODAY -> today(rest)
                    Tab.MONTH -> month(rest)
                    Tab.SETTINGS -> settings(rest)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun SelectionBar(count: Int, onSelectAll: () -> Unit, onDelete: () -> Unit, onExit: () -> Unit) {
    val texts = rememberTexts()
    TopAppBar(
        title = { Text(texts.plural(R.plurals.selected_count, count)) },
        navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Filled.Close, stringResource(R.string.exit_selection)) } },
        actions = {
            TextButton(onClick = onSelectAll) { Text(stringResource(R.string.select_all)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete_selected)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}
