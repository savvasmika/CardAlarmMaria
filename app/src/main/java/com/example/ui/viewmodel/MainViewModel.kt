package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.model.AlarmRecord
import com.example.data.model.AppSettings
import com.example.data.model.ShiftDay
import com.example.data.repository.ShiftRepository
import com.example.excel.MariaScheduleParser
import com.example.excel.SampleScheduleData
import com.example.excel.XlsxWorkbookParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ImportState(
    val isParsing: Boolean = false,
    val parsedResult: MariaScheduleParser.ParseResult? = null,
    val fileName: String? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShiftRepository(application)
    private val excelParser = XlsxWorkbookParser()
    private val mariaParser = MariaScheduleParser()

    // 0 = Σήμερα, 1 = Πρόγραμμα, 2 = Εισαγωγή Excel, 3 = Ρυθμίσεις
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedMonth = MutableStateFlow<String>("")
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    private val _settings = MutableStateFlow(repository.getSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _editingShift = MutableStateFlow<ShiftDay?>(null)
    val editingShift: StateFlow<ShiftDay?> = _editingShift.asStateFlow()

    private val _testAlarmCountdown = MutableStateFlow<Int?>(null)
    val testAlarmCountdown: StateFlow<Int?> = _testAlarmCountdown.asStateFlow()

    // Database flows
    val allShifts: StateFlow<List<ShiftDay>> = repository.allShifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingAlarms: StateFlow<List<AlarmRecord>> = repository.getPendingAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableMonths: StateFlow<List<String>> = repository.allMonthKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Επιλογή τρέχοντος μήνα αρχικά (π.χ. "2026-09")
        val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        _selectedMonth.value = currentMonthKey
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun selectMonth(monthKey: String) {
        _selectedMonth.value = monthKey
    }

    /**
     * Επεξεργασία αρχείου .xlsx που επέλεξε ο χρήστης μέσω του File Picker.
     */
    fun parseExcelFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _importState.value = ImportState(isParsing = true, fileName = fileName)
            try {
                val context = getApplication<Application>()
                val parseResult = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val workbook = excelParser.parse(stream)
                        mariaParser.parseWorkbook(workbook)
                    } ?: throw Exception("Δεν ήταν δυνατό το άνοιγμα του αρχείου.")
                }

                if (parseResult.shifts.isEmpty()) {
                    _importState.value = ImportState(
                        isParsing = false,
                        fileName = fileName,
                        errorMessage = "Δεν βρέθηκαν καταχωρήσεις για την εργαζόμενη «ΜΑΡΙΑ» στα φύλλα του Excel."
                    )
                } else {
                    _importState.value = ImportState(
                        isParsing = false,
                        fileName = fileName,
                        parsedResult = parseResult
                    )
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Σφάλμα ανάγνωσης Excel", e)
                _importState.value = ImportState(
                    isParsing = false,
                    fileName = fileName,
                    errorMessage = "Σφάλμα κατά την ανάγνωση του αρχείου: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    /**
     * Φόρτωση δοκιμαστικού προγράμματος (Demo) με βάση το παράδειγμα της Μαρίας.
     */
    fun loadSampleData() {
        val sampleShifts = SampleScheduleData.generateSampleShifts()
        _importState.value = ImportState(
            isParsing = false,
            fileName = "Δοκιμαστικό_Πρόγραμμα_Μαρία.xlsx",
            parsedResult = MariaScheduleParser.ParseResult(
                shifts = sampleShifts,
                processedSheets = listOf("SEP", "OCT"),
                mariaFoundInSheets = listOf("SEP", "OCT"),
                debugInfo = "Δοκιμαστικό πρόγραμμα με ${sampleShifts.size} ημέρες εργασίας & ρεπό."
            )
        )
    }

    /**
     * Επιβεβαίωση εισαγωγής προγράμματος και αυτόματη δημιουργία/ενημέρωση των alarms.
     */
    fun confirmImport() {
        val parsed = _importState.value.parsedResult ?: return
        viewModelScope.launch {
            _importState.value = _importState.value.copy(isParsing = true)
            try {
                val scheduledAlarms = repository.importShiftsAndScheduleAlarms(
                    newShifts = parsed.shifts,
                    clearPrevious = true
                )

                _importState.value = ImportState(
                    successMessage = "Επιτυχής εισαγωγή! Αποθηκεύτηκαν ${parsed.shifts.size} ημέρες και προγραμματίστηκαν ${scheduledAlarms.size} alarms για τη Μαρία."
                )

                // Αυτόματη μετάβαση στην καρτέλα «Πρόγραμμα»
                delay(1200)
                _selectedTab.value = 1
            } catch (e: Exception) {
                Log.e("MainViewModel", "Σφάλμα επιβεβαίωσης εισαγωγής", e)
                _importState.value = _importState.value.copy(
                    isParsing = false,
                    errorMessage = "Αποτυχία αποθήκευσης προγράμματος: ${e.localizedMessage}"
                )
            }
        }
    }

    fun dismissImportPreview() {
        _importState.value = ImportState()
    }

    fun openEditDialog(shift: ShiftDay) {
        _editingShift.value = shift
    }

    fun closeEditDialog() {
        _editingShift.value = null
    }

    /**
     * Χειροκίνητη διόρθωση ωραρίου μίας ημέρας.
     */
    fun saveManualEdit(rawSchedule: String) {
        val current = _editingShift.value ?: return
        viewModelScope.launch {
            val timing = mariaParser.parseShiftTiming(rawSchedule)
            val updatedShift = current.copy(
                rawSchedule = rawSchedule,
                isOff = timing.isOff,
                offType = timing.offType,
                firstStartTime = timing.firstStartTime,
                lastEndTime = timing.lastEndTime,
                hoursWorked = timing.estimatedHours,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateShiftManually(updatedShift)
            _editingShift.value = null
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            _settings.value = newSettings
            repository.saveSettings(newSettings, rescheduleAlarms = true)
        }
    }

    /**
     * Δοκιμαστικό alarm σε 5 δευτερόλεπτα.
     */
    fun triggerTestAlarm() {
        viewModelScope.launch {
            repository.triggerTestAlarm(5)
            // Αντίστροφη μέτρηση στην οθόνη
            for (i in 5 downTo 1) {
                _testAlarmCountdown.value = i
                delay(1000)
            }
            _testAlarmCountdown.value = null
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _importState.value = ImportState()
        }
    }
}
