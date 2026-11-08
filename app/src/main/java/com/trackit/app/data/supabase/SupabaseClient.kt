package com.trackit.app.data.supabase

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** An error returned by (or while reaching) Supabase. [message] is safe to show to the user. */
class ApiException(
    val status: Int,
    val code: String?,
    val detail: String,
    message: String,
) : Exception(message)

class ValidationException(message: String) : Exception(message)

/** Runs [block] and turns any failure into a Result with a user-friendly message. */
inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: ApiException) {
    Result.failure(e)
} catch (e: ValidationException) {
    Result.failure(e)
} catch (e: Exception) {
    Result.failure(Exception("Something went wrong. Please try again."))
}

/** Returns null for both a missing key and a JSON null (plain optString would return "null"). */
fun JSONObject.strOrNull(name: String): String? = if (isNull(name)) null else optString(name)

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String,
    val name: String,
)

/** Keeps the login session in encrypted storage so the user stays logged in after restarts. */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences? = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "trackit_session",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        null // secure storage unavailable: keep the session in memory only
    }
    private var memory: Session? = null

    fun load(): Session? {
        val p = prefs ?: return memory
        val access = p.getString("access_token", null) ?: return null
        val refresh = p.getString("refresh_token", null) ?: return null
        val uid = p.getString("user_id", null) ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = p.getLong("expires_at", 0L),
            userId = uid,
            email = p.getString("email", "") ?: "",
            name = p.getString("name", "") ?: "",
        )
    }

    fun save(s: Session) {
        val p = prefs
        if (p == null) {
            memory = s
            return
        }
        p.edit()
            .putString("access_token", s.accessToken)
            .putString("refresh_token", s.refreshToken)
            .putLong("expires_at", s.expiresAt)
            .putString("user_id", s.userId)
            .putString("email", s.email)
            .putString("name", s.name)
            .apply()
    }

    fun clear() {
        memory = null
        prefs?.edit()?.clear()?.apply()
    }
}

internal fun nowSeconds(): Long = System.currentTimeMillis() / 1000

fun sessionFrom(j: JSONObject): Session {
    val user = j.getJSONObject("user")
    val email = user.strOrNull("email") ?: ""
    val metaName = user.optJSONObject("user_metadata")?.strOrNull("display_name").orEmpty()
    val expiresAt = if (j.has("expires_at")) j.getLong("expires_at") else nowSeconds() + j.optLong("expires_in", 3600L)
    return Session(
        accessToken = j.getString("access_token"),
        refreshToken = j.getString("refresh_token"),
        expiresAt = expiresAt,
        userId = user.getString("id"),
        email = email,
        name = metaName.ifBlank { email.substringBefore('@') },
    )
}

/**
 * Minimal Supabase client (Auth + PostgREST over HTTPS).
 * Only the public/publishable key is used here. Data access is protected by RLS on the server.
 */
class SupabaseClient(baseUrl: String, private val apiKey: String, val store: SessionStore) {
    private val base = baseUrl.trim().trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val refreshLock = Mutex()

    /** Called when the refresh token is no longer valid and the user must log in again. */
    var onSessionLost: (() -> Unit)? = null

    private class Raw(val status: Int, val body: String)

    private fun requireConfigured() {
        if (!base.startsWith("https://") || apiKey.isBlank()) {
            throw ApiException(
                0, "not_configured", "",
                "Supabase is not configured yet. Add SUPABASE_URL and SUPABASE_KEY to local.properties, then rebuild the app.",
            )
        }
    }

    private suspend fun call(request: Request): Raw = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { Raw(it.code, it.body?.string().orEmpty()) }
        } catch (e: IOException) {
            throw ApiException(0, "network", e.message.orEmpty(), "No connection to the server. Check your internet and try again.")
        }
    }

    private fun buildError(raw: Raw): ApiException {
        val j = runCatching { JSONObject(raw.body) }.getOrNull()
        val code = j?.strOrNull("error_code") ?: j?.strOrNull("code")
        val detail = j?.strOrNull("msg") ?: j?.strOrNull("message")
            ?: j?.strOrNull("error_description") ?: j?.strOrNull("error") ?: ""
        val message = when {
            code == "invalid_credentials" || detail.contains("Invalid login credentials", ignoreCase = true) ->
                "Incorrect email or password."
            code == "email_not_confirmed" || detail.contains("Email not confirmed", ignoreCase = true) ->
                "Please confirm your email first. Check your inbox for the confirmation link."
                "This email is already registered."
            code == "weak_password" -> "That password is too weak. Try a longer or less common one."
            raw.status == 429 || code?.contains("rate_limit") == true ->
                "Too many attempts. Please wait a moment and try again."
            raw.status == 401 -> "Your session has expired. Please log in again."
            raw.status == 403 -> "You don't have permission to do that."
            raw.status >= 500 -> "The server is having problems. Please try again later."
            detail.isNotBlank() -> detail
            else -> "Request failed (${raw.status})."
        }
        return ApiException(raw.status, code, detail, message)
    }


    suspend fun authRequest(path: String, body: JSONObject): JSONObject {
        requireConfigured()
        val request = Request.Builder()
            .url("$base/auth/v1/$path")
            .header("apikey", apiKey)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        val raw = call(request)
        if (raw.status !in 200..299) throw buildError(raw)
        return if (raw.body.isBlank()) JSONObject() else JSONObject(raw.body)
    }

        if (base.isEmpty() || apiKey.isBlank()) return
        val request = Request.Builder()
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(jsonType))
            .build()
        try {
            call(request)
        } catch (e: ApiException) {
            // best effort: the local session is already cleared
        }
    }

    private suspend fun validAccessToken(forceRefresh: Boolean): String = refreshLock.withLock {
        val s = store.load()
            ?: throw ApiException(401, "no_session", "", "You are signed out. Please log in again.")
        if (!forceRefresh && s.expiresAt - nowSeconds() > 60) s.accessToken else refreshSession(s)
    }

    private suspend fun refreshSession(old: Session): String {
        try {
            val j = authRequest("token?grant_type=refresh_token", JSONObject().put("refresh_token", old.refreshToken))
            val fresh = sessionFrom(j).let { if (it.name.isBlank()) it.copy(name = old.name) else it }
        } catch (e: ApiException) {
            if (e.status == 400 || e.status == 401 || e.status == 403) {
                store.clear()
                onSessionLost?.invoke()
                throw ApiException(401, "session_expired", e.detail, "Your session has expired. Please log in again.")
            }
            throw e
        }
    }

    // ---------------------------------------------------------------- Database (PostgREST)

    /** Returns the response body. Throws [ApiException] on any failure. */
    suspend fun rest(method: String, path: String, body: String? = null, prefer: String? = null): String {
        requireConfigured()
        for (tryIndex in 0..1) {
            val token = validAccessToken(forceRefresh = tryIndex == 1)
            val builder = Request.Builder()
                .url("$base/rest/v1/$path")
                .header("apikey", apiKey)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
            if (prefer != null) builder.header("Prefer", prefer)
            val payload = (body ?: "").toRequestBody(jsonType)
            val request = when (method) {
                "POST" -> builder.post(payload)
                "PATCH" -> builder.patch(payload)
                "DELETE" -> builder.delete()
                else -> builder.get()
            }.build()
            val raw = call(request)
            if (raw.status in 200..299) return raw.body
            if (raw.status == 401 && tryIndex == 0) continue // token rejected: refresh once and retry
            throw buildError(raw)
        }
        throw ApiException(401, "unauthorized", "", "Your session has expired. Please log in again.")
    }
}