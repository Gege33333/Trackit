package com.trackit.app.data

import android.content.SharedPreferences
import android.util.Patterns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/*
 * Repository interfaces. The app only depends on these, so the Local* implementations below
 * (stored on the device) can later be replaced by Supabase implementations without touching
 * any ViewModel or screen.
 */

interface AuthRepository {
    val user: StateFlow<UserAccount?>
    suspend fun register(name: String, email: String, password: String): Result<Unit>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun logout()
}

interface RecordRepository {
    val records: StateFlow<List<MoneyRecord>>
    suspend fun upsert(record: MoneyRecord)
    suspend fun delete(id: String)
}

interface PresetRepository {
    val presets: StateFlow<List<Preset>>
    suspend fun upsert(preset: Preset)
    suspend fun delete(id: String)
}

interface SettingsRepository {
    /** true = dark, false = light, null = follow system. */
    val darkMode: StateFlow<Boolean?>
    fun setDarkMode(dark: Boolean?)
}

// ---------------------------------------------------------------------------------------------
// Local (on-device) implementations
// ---------------------------------------------------------------------------------------------

class LocalAuthRepository(private val prefs: SharedPreferences) : AuthRepository {
    private val _user = MutableStateFlow(restore())
    override val user: StateFlow<UserAccount?> = _user.asStateFlow()

    private fun users() = JSONArray(prefs.getString(KEY_USERS, "[]"))

    private fun JSONObject.toAccount() = UserAccount(getString("id"), getString("name"), getString("email"))

    private fun restore(): UserAccount? {
        val id = prefs.getString(KEY_SESSION, null) ?: return null
        val arr = users()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("id") == id) return o.toAccount()
        }
        return null
    }

    private fun fail(message: String) = Result.failure<Unit>(Exception(message))

    private fun hash(password: String): String =
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray()).joinToString("") { "%02x".format(it) }

    override suspend fun register(name: String, email: String, password: String): Result<Unit> {
        val e = email.trim().lowercase()
        val n = name.trim()
        if (n.isEmpty()) return fail("Please enter your name.")
        if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) return fail("Please enter a valid email address.")
        if (password.length < 6) return fail("Password must be at least 6 characters.")
        val arr = users()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("email") == e) return fail("This email is already registered.")
        }
        val obj = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("name", n)
            .put("email", e)
            .put("hash", hash(password))
        arr.put(obj)
        prefs.edit().putString(KEY_USERS, arr.toString()).putString(KEY_SESSION, obj.getString("id")).apply()
        _user.value = obj.toAccount()
        return Result.success(Unit)
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        val e = email.trim().lowercase()
        if (e.isEmpty() || password.isEmpty()) return fail("Please enter your email and password.")
        val arr = users()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("email") == e && o.getString("hash") == hash(password)) {
                prefs.edit().putString(KEY_SESSION, o.getString("id")).apply()
                _user.value = o.toAccount()
                return Result.success(Unit)
            }
        }
        return fail("Incorrect email or password.")
    }

    override suspend fun logout() {
        prefs.edit().remove(KEY_SESSION).apply()
        _user.value = null
    }

    private companion object {
        const val KEY_USERS = "users"
        const val KEY_SESSION = "session_uid"
    }
}

class LocalRecordRepository(
    private val prefs: SharedPreferences,
    auth: AuthRepository,
    scope: CoroutineScope,
) : RecordRepository {
    private val _records = MutableStateFlow<List<MoneyRecord>>(emptyList())
    override val records: StateFlow<List<MoneyRecord>> = _records.asStateFlow()
    private var uid: String? = null

    init {
        scope.launch {
            auth.user.collect { u ->
                uid = u?.id
                _records.value = if (u == null) emptyList() else decode(prefs.getString("records_${u.id}", null))
            }
        }
    }

    override suspend fun upsert(record: MoneyRecord) {
        val list = _records.value.toMutableList()
        val i = list.indexOfFirst { it.id == record.id }
        if (i >= 0) list[i] = record else list.add(record)
        commit(list)
    }

    override suspend fun delete(id: String) = commit(_records.value.filterNot { it.id == id })

    private fun commit(list: List<MoneyRecord>) {
        _records.value = list
        uid?.let { prefs.edit().putString("records_$it", encode(list)).apply() }
    }

    private fun encode(list: List<MoneyRecord>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("type", it.type.name).put("amount", it.amount)
                    .put("description", it.description).put("dateTime", it.dateTime)
            )
        }
        return arr.toString()
    }

    /** Old records with a type that no longer exists (e.g. CAPITAL) are skipped. */
    private fun decode(s: String?): List<MoneyRecord> {
        if (s.isNullOrEmpty()) return emptyList()
        val a = JSONArray(s)
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val type = runCatching { RecordType.valueOf(o.getString("type")) }.getOrNull() ?: continue
                add(
                    MoneyRecord(
                        id = o.getString("id"),
                        type = type,
                        amount = o.getLong("amount"),
                        description = o.optString("description"),
                        dateTime = o.getLong("dateTime"),
                    )
                )
            }
        }
    }
}

class LocalPresetRepository(
    private val prefs: SharedPreferences,
    auth: AuthRepository,
    scope: CoroutineScope,
) : PresetRepository {
    private val _presets = MutableStateFlow<List<Preset>>(emptyList())
    override val presets: StateFlow<List<Preset>> = _presets.asStateFlow()
    private var uid: String? = null

    init {
        scope.launch {
            auth.user.collect { u ->
                uid = u?.id
                _presets.value = if (u == null) emptyList() else decode(prefs.getString("presets_${u.id}", null))
            }
        }
    }

    override suspend fun upsert(preset: Preset) {
        val list = _presets.value.toMutableList()
        val i = list.indexOfFirst { it.id == preset.id }
        if (i >= 0) list[i] = preset else list.add(preset)
        commit(list)
    }

    override suspend fun delete(id: String) = commit(_presets.value.filterNot { it.id == id })

    private fun commit(list: List<Preset>) {
        _presets.value = list
        uid?.let { prefs.edit().putString("presets_$it", encode(list)).apply() }
    }

    private fun encode(list: List<Preset>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount)) }
        return arr.toString()
    }

    private fun decode(s: String?): List<Preset> {
        if (s.isNullOrEmpty()) return emptyList()
        val a = JSONArray(s)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Preset(o.getString("id"), o.getString("name"), o.getLong("amount"))
        }
    }
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