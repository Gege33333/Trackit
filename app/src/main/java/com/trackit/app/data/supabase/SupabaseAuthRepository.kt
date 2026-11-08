package com.trackit.app.data.supabase

import android.util.Patterns
import com.trackit.app.data.AccountDetails
import com.trackit.app.data.AuthRepository
import com.trackit.app.data.EmailChangeResult
import com.trackit.app.data.UserAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.coroutines.cancellation.CancellationException

class SupabaseAuthRepository(
    private val client: SupabaseClient,
    scope: CoroutineScope,
) : AuthRepository {

    private val _user = MutableStateFlow(client.store.load()?.toAccount())
    override val user: StateFlow<UserAccount?> = _user.asStateFlow()

    init {
        client.onSessionLost = { _user.value = null }
        if (_user.value != null) {
            scope.launch {
                syncProfileName()
                fetchAccountDetails()
            }
        }
    }

    private fun Session.toAccount() = UserAccount(id = userId, name = name, email = email)

    private fun signedOutError() = ApiException(401, "no_session", "", "You are signed out. Please log in again.")

    private fun parseInstant(value: String?): Instant? =
        value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    /** Keeps the locally stored email (and the UI) in sync with the server. */
    private fun syncEmail(email: String) {
        val s = client.store.load() ?: return
        if (email.isNotBlank() && !email.equals(s.email, ignoreCase = true)) {
            val updated = s.copy(email = email)
            client.store.save(updated)
            _user.value = updated.toAccount()
        }
    }

    // ------------------------------------------------------------------ Register / login / logout

    override suspend fun register(name: String, email: String, password: String): Result<Boolean> = attempt {
        val n = name.trim()
        val e = email.trim().lowercase()
        if (n.isEmpty()) throw ValidationException("Please enter your name.")
        if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) throw ValidationException("Please enter a valid email address.")
        if (password.length < 6) throw ValidationException("Password must be at least 6 characters.")

        val body = JSONObject()
            .put("email", e)
            .put("password", password)
            .put("data", JSONObject().put("display_name", n))
        val j = client.authRequest("signup", body)
        if (j.has("access_token")) {
            val s = sessionFrom(j).let { if (it.name.isBlank()) it.copy(name = n) else it }
            client.store.save(s)
            _user.value = s.toAccount()
            true
        } else {
            false // email confirmation required
        }
    }

    override suspend fun login(email: String, password: String): Result<Unit> = attempt {
        val e = email.trim().lowercase()
        if (e.isEmpty() || password.isEmpty()) throw ValidationException("Please enter your email and password.")
        val j = client.authRequest(
            "token?grant_type=password",
            JSONObject().put("email", e).put("password", password),
        )
        val s = sessionFrom(j)
        client.store.save(s)
        _user.value = s.toAccount()
        syncProfileName()
    }

    /** Signs out this device only. Other devices stay signed in. */
    override suspend fun logout() {
        val s = client.store.load()
        client.store.clear()
        _user.value = null
        if (s != null) client.signOutRemote(s.accessToken, "local")
    }

    // ------------------------------------------------------------------ Account management

    override suspend fun fetchAccountDetails(): Result<AccountDetails> = attempt {
        val j = JSONObject(client.authorized("GET", "user"))
        val email = j.strOrNull("email").orEmpty()
        val pending = j.strOrNull("new_email")?.takeIf { it.isNotBlank() }
        syncEmail(email)
        AccountDetails(
            email = email,
            pendingEmail = pending,
            emailVerified = !j.strOrNull("email_confirmed_at").isNullOrBlank(),
            createdAt = parseInstant(j.strOrNull("created_at")),
            lastSignInAt = parseInstant(j.strOrNull("last_sign_in_at")),
        )
    }

    override suspend fun updateDisplayName(name: String): Result<Unit> = attempt {
        val n = name.trim()
        if (n.isEmpty()) throw ValidationException("Please enter your name.")
        if (n.length > 40) throw ValidationException("Name must be 40 characters or fewer.")
        val s = client.store.load() ?: throw signedOutError()

        // The profiles table is the source of truth for the display name.
        val rows = JSONArray(
            client.rest(
                "PATCH",
                "profiles?id=eq.${s.userId}",
                JSONObject().put("display_name", n).toString(),
                "return=representation",
            )
        )
        if (rows.length() == 0) {
            client.rest(
                "POST",
                "profiles",
                JSONObject().put("id", s.userId).put("display_name", n).toString(),
                "return=minimal",
            )
        }
        // Keep the auth metadata in sync (best effort).
        try {
            client.authorized("PUT", "user", JSONObject().put("data", JSONObject().put("display_name", n)))
        } catch (e: ApiException) {
            // not critical
        }
        val updated = (client.store.load() ?: s).copy(name = n)
        client.store.save(updated)
        _user.value = updated.toAccount()
        Unit
    }

    override suspend fun updateEmail(newEmail: String): Result<EmailChangeResult> = attempt<EmailChangeResult> {
        val e = newEmail.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) throw ValidationException("Please enter a valid email address.")
        val s = client.store.load() ?: throw signedOutError()
        if (e == s.email.lowercase()) throw ValidationException("That is already your email address.")

        val j = JSONObject(client.authorized("PUT", "user", JSONObject().put("email", e)))
        val pending = j.strOrNull("new_email")?.takeIf { it.isNotBlank() }
        if (pending != null) {
            EmailChangeResult.ConfirmationRequired(pending)
        } else {
            syncEmail(j.strOrNull("email")?.takeIf { it.isNotBlank() } ?: e)
            EmailChangeResult.Updated
        }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = attempt {
        if (currentPassword.isEmpty()) throw ValidationException("Enter your current password.")
        if (newPassword.length < 8) throw ValidationException("New password must be at least 8 characters.")
        if (newPassword == currentPassword) {
            throw ValidationException("Your new password must be different from the current one.")
        }
        val s = client.store.load() ?: throw signedOutError()
        verifyPassword(s.email, currentPassword)
        client.authorized("PUT", "user", JSONObject().put("password", newPassword))
        Unit
    }

    override suspend fun signOutOtherDevices(): Result<Unit> = attempt {
        client.authorized("POST", "logout?scope=others", JSONObject())
        Unit
    }

    override suspend fun deleteAccount(currentPassword: String): Result<Unit> = attempt {
        if (currentPassword.isEmpty()) throw ValidationException("Enter your password to confirm.")
        val s = client.store.load() ?: throw signedOutError()
        verifyPassword(s.email, currentPassword)
        // Runs the secured database function that deletes only the caller's own account.
        client.rest("POST", "rpc/delete_my_account", "{}")
        client.store.clear()
        _user.value = null
        Unit
    }

    /** Re-checks the current password by signing in again; this also refreshes the session. */
    private suspend fun verifyPassword(email: String, password: String) {
        try {
            val j = client.authRequest(
                "token?grant_type=password",
                JSONObject().put("email", email).put("password", password),
            )
            val fresh = sessionFrom(j)
            val existing = client.store.load()
            client.store.save(fresh.copy(name = existing?.name?.ifBlank { null } ?: fresh.name))
        } catch (e: ApiException) {
            if (e.code == "invalid_credentials" || e.detail.contains("Invalid login credentials", ignoreCase = true)) {
                throw ValidationException("Your current password is incorrect.")
            }
            throw e
        }
    }

    /** Best effort: the display name in the profiles table is the source of truth. */
    private suspend fun syncProfileName() {
        try {
            val rows = JSONArray(client.rest("GET", "profiles?select=display_name&limit=1"))
            val name = rows.optJSONObject(0)?.strOrNull("display_name").orEmpty()
            val s = client.store.load() ?: return
            if (name.isNotBlank() && name != s.name) {
                val updated = s.copy(name = name)
                client.store.save(updated)
                _user.value = updated.toAccount()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ignore: the name from sign-up is already shown
        }
    }
}
