package com.trackit.app.data.supabase

import com.trackit.app.data.AuthRepository
import com.trackit.app.data.LoadState
import com.trackit.app.data.MoneyRecord
import com.trackit.app.data.Preset
import com.trackit.app.data.PresetRepository
import com.trackit.app.data.RecordRepository
import com.trackit.app.data.RecordType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

private const val PAGE_SIZE = 1000

// =============================================================================== Records

class SupabaseRecordRepository(
    private val client: SupabaseClient,
    auth: AuthRepository,
    scope: CoroutineScope,
) : RecordRepository {

    private val _records = MutableStateFlow<List<MoneyRecord>>(emptyList())
    private val _state = MutableStateFlow<LoadState>(LoadState.Loading)
    override val records: StateFlow<List<MoneyRecord>> = _records.asStateFlow()
    override val state: StateFlow<LoadState> = _state.asStateFlow()

    init {
        scope.launch {
            auth.user.map { it?.id }.distinctUntilChanged().collect { id ->
                if (id == null) {
                    _records.value = emptyList()
                    _state.value = LoadState.Ready
                } else {
                    refresh()
                }
            }
        }
    }

    override suspend fun refresh(): Result<Unit> {
        _state.value = LoadState.Loading
        val result = attempt { fetchAll() }
        return result.fold(
            onSuccess = { list ->
                _records.value = list
                _state.value = LoadState.Ready
                Result.success(Unit)
            },
            onFailure = { e ->
                _state.value = LoadState.Failed(e.message ?: "Couldn't load your records.")
                Result.failure(e)
            },
        )
    }

    private suspend fun fetchAll(): List<MoneyRecord> {
        val out = mutableListOf<MoneyRecord>()
        var offset = 0
        while (true) {
            val body = client.rest(
                "GET",
                "transactions?select=id,type,amount,description,occurred_at" +
                    "&order=occurred_at.desc,id.asc&limit=$PAGE_SIZE&offset=$offset",
            )
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) parse(arr.getJSONObject(i))?.let { out.add(it) }
            if (arr.length() < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return out
    }

    override suspend fun upsert(record: MoneyRecord): Result<Unit> = attempt {
        val json = JSONObject()
            .put("type", record.type.name.lowercase())
            .put("amount", record.amount)
            .put("description", if (record.description.isBlank()) JSONObject.NULL else record.description)
            .put("occurred_at", Instant.ofEpochMilli(record.dateTime).toString())

        val exists = _records.value.any { it.id == record.id }
        val body = if (exists) {
            client.rest("PATCH", "transactions?id=eq.${record.id}", json.toString(), "return=representation")
        } else {
            client.rest("POST", "transactions", json.put("id", record.id).toString(), "return=representation")
        }
        val row = JSONArray(body).optJSONObject(0)
            ?: throw ValidationException("This record no longer exists. Pull the latest data with Retry.")
        val saved = parse(row) ?: throw ValidationException("Unexpected response from the server.")
        _records.value = if (exists) {
            _records.value.map { if (it.id == saved.id) saved else it }
        } else {
            _records.value + saved
        }
        Unit
    }

    override suspend fun delete(id: String): Result<Unit> = attempt {
        client.rest("DELETE", "transactions?id=eq.$id", prefer = "return=minimal")
        _records.value = _records.value.filterNot { it.id == id }
        Unit
    }

    private fun parse(o: JSONObject): MoneyRecord? {
        val type = when (o.optString("type")) {
            "plus" -> RecordType.PLUS
            "minus" -> RecordType.MINUS
            else -> return null
        }
        return MoneyRecord(
            id = o.getString("id"),
            type = type,
            amount = Math.round(o.getDouble("amount")),
            description = o.strOrNull("description") ?: "",
            dateTime = OffsetDateTime.parse(o.getString("occurred_at")).toInstant().toEpochMilli(),
        )
    }
}

// =============================================================================== Easy Input presets

class SupabasePresetRepository(
    private val client: SupabaseClient,
    auth: AuthRepository,
    scope: CoroutineScope,
) : PresetRepository {

    private val _presets = MutableStateFlow<List<Preset>>(emptyList())
    private val _state = MutableStateFlow<LoadState>(LoadState.Loading)
    override val presets: StateFlow<List<Preset>> = _presets.asStateFlow()
    override val state: StateFlow<LoadState> = _state.asStateFlow()

    init {
        scope.launch {
            auth.user.map { it?.id }.distinctUntilChanged().collect { id ->
                if (id == null) {
                    _presets.value = emptyList()
                    _state.value = LoadState.Ready
                } else {
                    refresh()
                }
            }
        }
    }

    override suspend fun refresh(): Result<Unit> {
        _state.value = LoadState.Loading
        val result = attempt {
            val arr = JSONArray(client.rest("GET", "easy_inputs?select=id,name,amount&order=created_at.asc,id.asc"))
            List(arr.length()) { parse(arr.getJSONObject(it)) }
        }
        return result.fold(
            onSuccess = { list ->
                _presets.value = list
                _state.value = LoadState.Ready
                Result.success(Unit)
            },
            onFailure = { e ->
                _state.value = LoadState.Failed(e.message ?: "Couldn't load your Easy Inputs.")
                Result.failure(e)
            },
        )
    }

    override suspend fun upsert(preset: Preset): Result<Unit> = attempt {
        val json = JSONObject().put("name", preset.name).put("amount", preset.amount)
        val exists = _presets.value.any { it.id == preset.id }
        val body = if (exists) {
            client.rest("PATCH", "easy_inputs?id=eq.${preset.id}", json.toString(), "return=representation")
        } else {
            client.rest("POST", "easy_inputs", json.put("id", preset.id).toString(), "return=representation")
        }
        val row = JSONArray(body).optJSONObject(0)
            ?: throw ValidationException("This Easy Input no longer exists. Pull the latest data with Retry.")
        val saved = parse(row)
        _presets.value = if (exists) {
            _presets.value.map { if (it.id == saved.id) saved else it }
        } else {
            _presets.value + saved
        }
        Unit
    }

    override suspend fun delete(id: String): Result<Unit> = attempt {
        client.rest("DELETE", "easy_inputs?id=eq.$id", prefer = "return=minimal")
        _presets.value = _presets.value.filterNot { it.id == id }
        Unit
    }

    private fun parse(o: JSONObject) = Preset(
        id = o.getString("id"),
        name = o.getString("name"),
        amount = Math.round(o.getDouble("amount")),
    )
}