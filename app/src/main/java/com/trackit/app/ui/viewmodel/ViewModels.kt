package com.trackit.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trackit.app.AppContainer
import com.trackit.app.data.AuthRepository
import com.trackit.app.data.MoneyRecord
import com.trackit.app.data.Preset
import com.trackit.app.data.PresetRepository
import com.trackit.app.data.RecordRepository
import com.trackit.app.data.RecordType
import com.trackit.app.data.SettingsRepository
import com.trackit.app.util.DaySummary
import com.trackit.app.util.localDate
import com.trackit.app.util.summarize
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class AppViewModel(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val user = auth.user
    val darkMode = settings.darkMode

    fun setDarkMode(dark: Boolean?) = settings.setDarkMode(dark)
    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        error = null
    }

    fun login(email: String, password: String) = launchAuth { auth.login(email, password) }

    fun register(name: String, email: String, password: String) = launchAuth { auth.register(name, email, password) }

    private fun launchAuth(block: suspend () -> Result<Unit>) {
        if (loading) return
        viewModelScope.launch {
            loading = true
            error = null
            block().onFailure { error = it.message ?: "Something went wrong. Please try again." }
            loading = false
        }
    }
}

class TrackViewModel(
    private val recordRepo: RecordRepository,
    private val presetRepo: PresetRepository,
) : ViewModel() {
    val records = recordRepo.records
    val presets = presetRepo.presets

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun saveRecord(record: MoneyRecord) {
        viewModelScope.launch { recordRepo.upsert(record) }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch { recordRepo.delete(id) }
    }

    /** Easy Input: instantly creates a Plus or Minus record; the preset itself is never modified. */
    fun usePreset(preset: Preset, type: RecordType) {
        viewModelScope.launch {
            recordRepo.upsert(
                MoneyRecord(
                    type = type,
                    amount = preset.amount,
                    description = preset.name,
                    dateTime = System.currentTimeMillis(),
                )
            )
            val label = if (type == RecordType.PLUS) "Plus" else "Minus"
            _messages.emit("$label added: ${preset.name}")
        }
    }

    fun savePreset(preset: Preset) {
        viewModelScope.launch { presetRepo.upsert(preset) }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch { presetRepo.delete(id) }
    }
}

data class CalendarState(
    val month: YearMonth,
    val selected: LocalDate,
    /** Net (plus - minus) per date. Dates without records are absent. */
    val dayNet: Map<LocalDate, Long>,
    val monthPlus: Long,
    val monthMinus: Long,
    /** Combined month total: monthPlus - monthMinus. */
    val monthNet: Long,
    val dayRecords: List<MoneyRecord>,
    val daySummary: DaySummary,
)

private fun buildCalendarState(month: YearMonth, selected: LocalDate, records: List<MoneyRecord>): CalendarState {
    val inMonth = records.filter { YearMonth.from(it.localDate()) == month }
    val dayNet = inMonth
        .groupBy { it.localDate() }
        .mapValues { (_, list) -> list.summarize().net }
    val monthSummary = inMonth.summarize()
    val dayRecords = records.filter { it.localDate() == selected }.sortedBy { it.dateTime }
    return CalendarState(
        month = month,
        selected = selected,
        dayNet = dayNet,
        monthPlus = monthSummary.plusTotal,
        monthMinus = monthSummary.minusTotal,
        monthNet = monthSummary.net,
        dayRecords = dayRecords,
        daySummary = dayRecords.summarize(),
    )
}

class CalendarViewModel(private val recordRepo: RecordRepository) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    private val _selected = MutableStateFlow(LocalDate.now())

    val state: StateFlow<CalendarState> = combine(_month, _selected, recordRepo.records) { m, s, r ->
        buildCalendarState(m, s, r)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        buildCalendarState(_month.value, _selected.value, recordRepo.records.value),
    )

    fun previousMonth() = goTo(_month.value.minusMonths(1))
    fun nextMonth() = goTo(_month.value.plusMonths(1))

    private fun goTo(month: YearMonth) {
        _month.value = month
        _selected.value = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1)
    }

    fun select(date: LocalDate) {
        _selected.value = date
    }

    fun saveRecord(record: MoneyRecord) {
        viewModelScope.launch { recordRepo.upsert(record) }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch { recordRepo.delete(id) }
    }
}

class VmFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(AppViewModel::class.java) -> AppViewModel(c.auth, c.settings)
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(c.auth)
            modelClass.isAssignableFrom(TrackViewModel::class.java) -> TrackViewModel(c.records, c.presets)
            modelClass.isAssignableFrom(CalendarViewModel::class.java) -> CalendarViewModel(c.records)
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return vm as T
    }
}