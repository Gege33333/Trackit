package com.trackit.app

import android.app.Application
import android.content.Context
import com.trackit.app.data.AuthRepository
import com.trackit.app.data.LocalSettingsRepository
import com.trackit.app.data.PresetRepository
import com.trackit.app.data.RecordRepository
import com.trackit.app.data.SettingsRepository
import com.trackit.app.data.supabase.SessionStore
import com.trackit.app.data.supabase.SupabaseAuthRepository
import com.trackit.app.data.supabase.SupabaseClient
import com.trackit.app.data.supabase.SupabasePresetRepository
import com.trackit.app.data.supabase.SupabaseRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple manual dependency container. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("trackit", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val client = SupabaseClient(
        baseUrl = BuildConfig.SUPABASE_URL,
        apiKey = BuildConfig.SUPABASE_KEY, // public/publishable key only
        store = SessionStore(appContext),
    )

    val auth: AuthRepository = SupabaseAuthRepository(client, scope)
    val records: RecordRepository = SupabaseRecordRepository(client, auth, scope)
    val presets: PresetRepository = SupabasePresetRepository(client, auth, scope)
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
