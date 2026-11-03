package com.trackit.app

import android.app.Application
import android.content.Context
import com.trackit.app.data.AuthRepository
import com.trackit.app.data.LocalAuthRepository
import com.trackit.app.data.LocalPresetRepository
import com.trackit.app.data.LocalRecordRepository
import com.trackit.app.data.LocalSettingsRepository
import com.trackit.app.data.PresetRepository
import com.trackit.app.data.RecordRepository
import com.trackit.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Simple manual dependency container.
 * To move to Supabase later, swap the Local* classes below for Supabase implementations
 * of the same interfaces.
 */
class AppContainer(context: Context) {
    private val prefs = context.getSharedPreferences("trackit", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val auth: AuthRepository = LocalAuthRepository(prefs)
    val records: RecordRepository = LocalRecordRepository(prefs, auth, scope)
    val presets: PresetRepository = LocalPresetRepository(prefs, auth, scope)
    val settings: SettingsRepository = LocalSettingsRepository(prefs)
}

class TrackitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
