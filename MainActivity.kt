package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.screens.ImportExcelScreen
import com.example.ui.screens.ManualEditDialog
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val allShifts by viewModel.allShifts.collectAsState()
    val pendingAlarms by viewModel.pendingAlarms.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val editingShift by viewModel.editingShift.collectAsState()
    val testAlarmCountdown by viewModel.testAlarmCountdown.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Αίτηση άδειας ειδοποιήσεων για Android 13+ (TIRAMISU)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Άδεια ειδοποιήσεων
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Υπολογισμός σημερινού ωραρίου, επόμενου alarm και προσεχών ημερών
    val todayDateKey = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    val todayShift = remember(allShifts, todayDateKey) {
        allShifts.firstOrNull { it.dateKey == todayDateKey }
            ?: allShifts.firstOrNull() // Αν δεν υπάρχει σημερινό, προβολή πρώτης καταχώρησης
    }

    val nextAlarm = remember(pendingAlarms) {
        val now = System.currentTimeMillis()
        pendingAlarms.firstOrNull { it.triggerTimestamp > now }
    }

    val upcomingShifts = remember(allShifts, todayDateKey) {
        allShifts.filter { it.dateKey >= todayDateKey }.take(5)
    }

    // Αν ο επιλεγμένος μήνας είναι κενός και έχουμε διαθέσιμους μήνες, επιλέγουμε τον πρώτο
    LaunchedEffect(availableMonths, selectedMonth) {
        if (selectedMonth.isEmpty() && availableMonths.isNotEmpty()) {
            val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            if (availableMonths.contains(currentMonth)) {
                viewModel.selectMonth(currentMonth)
            } else {
                viewModel.selectMonth(availableMonths.first())
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Maria Work Alarm",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    NavigationItemData("Σήμερα", Icons.Filled.Schedule, Icons.Outlined.Schedule, "tab_today"),
                    NavigationItemData("Πρόγραμμα", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "tab_schedule"),
                    NavigationItemData("Εισαγωγή Excel", Icons.Filled.FileUpload, Icons.Outlined.FileUpload, "tab_import"),
                    NavigationItemData("Ρυθμίσεις", Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
                )

                navItems.forEachIndexed { index, item ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(index) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag(item.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> TodayScreen(
                    todayShift = todayShift,
                    nextAlarm = nextAlarm,
                    upcomingShifts = upcomingShifts,
                    testAlarmCountdown = testAlarmCountdown,
                    onNavigateToImport = { viewModel.selectTab(2) },
                    onNavigateToSchedule = { viewModel.selectTab(1) },
                    onEditTodayShift = { shift -> viewModel.openEditDialog(shift) },
                    onTestAlarm = { viewModel.triggerTestAlarm() }
                )
                1 -> ScheduleScreen(
                    allShifts = allShifts,
                    availableMonths = availableMonths,
                    selectedMonth = selectedMonth,
                    onSelectMonth = { month -> viewModel.selectMonth(month) },
                    onEditShift = { shift -> viewModel.openEditDialog(shift) }
                )
                2 -> ImportExcelScreen(
                    importState = importState,
                    onFileSelected = { uri, fileName -> viewModel.parseExcelFile(uri, fileName) },
                    onLoadSampleData = { viewModel.loadSampleData() },
                    onConfirmImport = { viewModel.confirmImport() },
                    onDismissPreview = { viewModel.dismissImportPreview() }
                )
                3 -> SettingsScreen(
                    settings = settings,
                    testAlarmCountdown = testAlarmCountdown,
                    onUpdateSettings = { newSettings -> viewModel.updateSettings(newSettings) },
                    onTestAlarm = { viewModel.triggerTestAlarm() },
                    onClearAllData = { viewModel.clearAllData() }
                )
            }
        }
    }

    // Διάλογος χειροκίνητης επεξεργασίας ωραρίου
    editingShift?.let { shift ->
        ManualEditDialog(
            shift = shift,
            onDismiss = { viewModel.closeEditDialog() },
            onSave = { newRawSchedule ->
                viewModel.saveManualEdit(newRawSchedule)
            }
        )
    }
}

data class NavigationItemData(
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val tag: String
)
