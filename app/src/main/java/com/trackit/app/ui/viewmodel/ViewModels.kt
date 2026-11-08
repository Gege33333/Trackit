package com.trackit.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trackit.app.AppContainer
import com.trackit.app.data.AccountDetails
import com.trackit.app.data.AuthRepository
import com.trackit.app.data.EmailChangeResult
import com.trackit.app.data.MoneyRecord
import com.trackit.app.data.Preset
import com.trackit.app.data.PresetRepository
import com.trackit.app.data.RecordRepository
import com.trackit.app.data.RecordType
import com.trackit.app.data.SettingsRepository
import com.trackit.app.util.DaySummary
import com.trackit.app.util.localDate
import com.trackit.app.util.summarize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private const val GENERIC_VM_ERROR = "Something went wrong. Please try again."

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

    /** Silently re-syncs the email/name with the server (e.g. after confirming an email change). */
    fun refreshAccount() {
        viewModelScope.launch { auth.fetchAccountDetails() }
    }
}

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var info by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        error = null
        info = null
    }

    fun login(email: String, password: String) = launchAuth {
        auth.login(email, password)
            .onFailure { error = it.message ?: GENERIC_VM_ERROR }
    }

    fun register(name: String, email: String, password: String) = launchAuth {
        auth.register(name, email, password)
            .onSuccess { signedIn ->
                if (!signedIn) info = "Account created. Check your email to confirm it, then log in."
            }
            .onFailure { error = it.message ?: GENERIC_VM_ERROR }
    }

    private fun launchAuth(block: suspend () -> Unit) {
        if (loading) return
        viewModelScope.launch {
            loading = true
            error = null
            info = null
            try {
                block()
            } finally {
                loading = false
            }
        }
    }
}

/** Edit profile, password, sessions and account deletion. */
class AccountViewModel(private val auth: AuthRepository) : ViewModel() {
    val user = auth.user

    var details by mutableStateOf<AccountDetails?>(null)
        private set
    var detailsLoading by mutableStateOf(false)
        private set
    var detailsError by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var success by mutableStateOf<String?>(null)
        private set

    init {
        // Forget cached details when a different user signs in or out.
        viewModelScope.launch {
            auth.user.map { it?.id }.distinctUntilChanged().collect {
                details = null
                detailsError = null
            }
        }
    }

    fun clearMessages() {
        error = null
        success = null
    }

    fun loadDetails() {
        if (detailsLoading) return
        viewModelScope.launch {
            detailsLoading = true
            detailsError = null
            auth.fetchAccountDetails()
                .onSuccess { details = it }
                .onFailure { detailsError = it.message ?: GENERIC_VM_ERROR }
            detailsLoading = false
        }
    }

    fun saveProfile(name: String, email: String) {
        if (busy) return
        val current = auth.user.value ?: return
        val newName = name.trim()
        val newEmail = email.trim()
        val nameChanged = newName != current.name
        val emailChanged = !newEmail.equals(current.email, ignoreCase = true)
        clearMessages()
        if (!nameChanged && !emailChanged) {
            error = "You haven't changed anything yet."
            return
        }
        viewModelScope.launch {
            busy = true
            val notes = mutableListOf<String>()
            var failure: String? = null

            if (nameChanged) {
                val r = auth.updateDisplayName(newName)
                if (r.isSuccess) {
                    notes.add("Display name updated.")
                } else {
                    failure = r.exceptionOrNull()?.message ?: GENERIC_VM_ERROR
                }
            }
            if (failure == null && emailChanged) {
                auth.updateEmail(newEmail)
                    .onSuccess { result ->
                        notes.add(
                            when (result) {
                                EmailChangeResult.Updated -> "Email updated."
                                is EmailChangeResult.ConfirmationRequired ->
                                    "We sent a confirmation link to ${result.newEmail}. " +
                                        "Your email will change after you confirm it."
                            }
                        )
                    }
                    .onFailure { failure = it.message ?: GENERIC_VM_ERROR }
            }

            if (notes.isNotEmpty()) success = notes.joinToString(" ")
            val f = failure
            if (f != null) error = f
            busy = false
            if (emailChanged) loadDetails()
        }
    }

    fun changePassword(current: String, newPassword: String, confirm: String, onSuccess: () -> Unit) {
        if (busy) return
        clearMessages()
        when {
            current.isEmpty() -> { error = "Enter your current password."; return }
            newPassword.length < 8 -> { error = "New password must be at least 8 characters."; return }
            newPassword != confirm -> { error = "The new passwords don't match."; return }
            newPassword == current -> { error = "Your new password must be different from the current one."; return }
        }
        viewModelScope.launch {
            busy = true
            auth.changePassword(current, newPassword)
                .onSuccess {
                    success = "Password updated."
                    onSuccess()
                }
                .onFailure { error = it.message ?: GENERIC_VM_ERROR }
            busy = false
        }
    }

    fun signOutOtherDevices() {
        if (busy) return
        clearMessages()
        viewModelScope.launch {
            busy = true
            auth.signOutOtherDevices()
                .onSuccess { success = "Signed out of all other devices." }
                .onFailure { error = it.message ?: GENERIC_VM_ERROR }
            busy = false
        }
    }

    fun deleteAccount(password: String, confirmText: String) {
        if (busy) return
        clearMessages()
        if (confirmText.trim() != "DELETE") {
            error = "Type DELETE to confirm."
            return
        }
        if (password.isEmpty()) {
            error = "Enter your password to confirm."
            return
        }
        viewModelScope.launch {
            busy = true
            // On success the user is signed out and the app returns to the login screen.
            auth.deleteAccount(password)
                .onFailure { error = it.message ?: GENERIC_VM_ERROR }
            busy = false
        }
    }
}

class TrackViewModel(
    private val recordRepo: RecordRepository,
    private val presetRepo: PresetRepository,
) : ViewModel() {
    val records = recordRepo.records
    val presets = presetRepo.presets
    val recordsState = recordRepo.state
    val presetsState = presetRepo.state

    fun refresh() {
        viewModelScope.launch { recordRepo.refresh() }
        viewModelScope.launch { presetRepo.refresh() }
    }

    fun saveRecord(record: MoneyRecord, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(recordRepo.upsert(record)) }
    }

    fun deleteRecord(id: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(recordRepo.delete(id)) }
    }

    /** Easy Input: creates a Plus or Minus record from the preset; the preset itself is never modified. */
    fun usePreset(preset: Preset, type: RecordType, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onDone(
                recordRepo.upsert(
                    MoneyRecord(
                        type = type,
                        amount = preset.amount,
                        description = preset.name,
                        dateTime = System.currentTimeMillis(),
                    )
                )
            )
        }
    }

    fun savePreset(preset: Preset, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(presetRepo.upsert(preset)) }
    }

    fun deletePreset(id: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(presetRepo.delete(id)) }
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

    val loadState = recordRepo.state

    val state: StateFlow<CalendarState> = combine(_month, _selected, recordRepo.records) { m, s, r ->
        buildCalendarState(m, s, r)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        buildCalendarState(_month.value, _selected.value, recordRepo.records.value),
    )

    fun refresh() {
        viewModelScope.launch { recordRepo.refresh() }
    }

    fun previousMonth() = goTo(_month.value.minusMonths(1))
    fun nextMonth() = goTo(_month.value.plusMonths(1))

    private fun goTo(month: YearMonth) {
        _month.value = month
        _selected.value = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1)
    }

    fun select(date: LocalDate) {
        _selected.value = date
    }

    fun saveRecord(record: MoneyRecord, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(recordRepo.upsert(record)) }
    }

    fun deleteRecord(id: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(recordRepo.delete(id)) }
    }
}

/** Lets the Profile page pick any month and read its calendar data for the picture export. */
class ExportViewModel(private val recordRepo: RecordRepository) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val loadState = recordRepo.state

    val state: StateFlow<CalendarState> = combine(_month, recordRepo.records) { m, r ->
        buildCalendarState(m, m.atDay(1), r)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        buildCalendarState(_month.value, _month.value.atDay(1), recordRepo.records.value),
    )

    fun setMonth(month: YearMonth) {
        _month.value = month
    }

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun refresh() {
        viewModelScope.launch { recordRepo.refresh() }
    }
}

class VmFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(AppViewModel::class.java) -> AppViewModel(c.auth, c.settings)
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(c.auth)
            modelClass.isAssignableFrom(AccountViewModel::class.java) -> AccountViewModel(c.auth)
            modelClass.isAssignableFrom(TrackViewModel::class.java) -> TrackViewModel(c.records, c.presets)
            modelClass.isAssignableFrom(CalendarViewModel::class.java) -> CalendarViewModel(c.records)
            modelClass.isAssignableFrom(ExportViewModel::class.java) -> ExportViewModel(c.records)
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return vm as T
    }
}
