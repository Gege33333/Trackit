package com.trackit.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Loading status of data fetched from the server. */
sealed interface LoadState {
    object Loading : LoadState
    object Ready : LoadState
    data class Failed(val message: String) : LoadState
}

interface AuthRepository {
    val user: StateFlow<UserAccount?>

    /** Result true = signed in; false = account created but email confirmation is required. */
    suspend fun register(name: String, email: String, password: String): Result<Boolean>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun logout()
}

/**
 * Online-first: the server is the source of truth. A write only updates [records]
 * after the server confirmed it, and returns a failure otherwise.
 */
interface RecordRepository {
    val records: StateFlow<List<MoneyRecord>>
    val state: StateFlow<LoadState>
    suspend fun refresh(): Result<Unit>
    suspend fun upsert(record: MoneyRecord): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}

interface PresetRepository {
    val presets: StateFlow<List<Preset>>
    val state: StateFlow<LoadState>
    suspend fun refresh(): Result<Unit>
    suspend fun upsert(preset: Preset): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}

interface SettingsRepository {
    /** true = dark, false = light, null = follow system. */
    val darkMode: StateFlow<Boolean?>
    fun setDarkMode(dark: Boolean?)
}

class LocalSettingsRepository(private val prefs: SharedPreferences) : SettingsRepository {
    private val _dark = MutableStateFlow(read())
    override val darkMode: StateFlow<Boolean?> = _dark.asStateFlow()

    private fun read(): Boolean? = when (prefs.getString("theme_mode", "system")) {
        "dark" -> true
        "light" -> false
        else -> null
    }

    override fun setDarkMode(dark: Boolean?) {
        prefs.edit().putString("theme_mode", when (dark) { true -> "dark"; false -> "light"; null -> "system" }).apply()
        _dark.value = dark
    }
}