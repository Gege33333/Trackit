package com.trackit.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trackit.app.ui.components.AppCard
import com.trackit.app.ui.components.ConfirmDialog
import com.trackit.app.ui.components.ErrorBanner
import com.trackit.app.ui.components.FeedbackMessage
import com.trackit.app.ui.components.ScreenHeader
import com.trackit.app.ui.components.SectionCaption
import com.trackit.app.ui.components.SettingsRow
import com.trackit.app.ui.components.SummaryLine
import com.trackit.app.ui.viewmodel.AccountViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val securityDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault())

private fun formatInstant(value: Instant?): String = value?.let { securityDateFormat.format(it) } ?: "Not available"

@Composable
fun PrivacySecurityScreen(
    vm: AccountViewModel,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmOthers by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.clearMessages()
        vm.loadDetails()
    }

    val details = vm.details

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Privacy & Security", onBack)

        // Account security information
        AppCard {
            SectionCaption("Account security")
            Spacer(Modifier.height(8.dp))
            when {
                details != null -> {
                    SummaryLine("Email", details.email)
                    details.pendingEmail?.let { SummaryLine("Pending email", it) }
                    SummaryLine("Email status", if (details.emailVerified) "Verified" else "Not verified")
                    SummaryLine("Sign-in method", "Email and password")
                    SummaryLine("Last sign-in", formatInstant(details.lastSignInAt))
                    SummaryLine("Account created", formatInstant(details.createdAt))
                }
                vm.detailsLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Loading…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                vm.detailsError != null -> {
                    ErrorBanner(vm.detailsError ?: "", onRetry = { vm.loadDetails() })
                }
            }
        }

        // Password
        AppCard {
            SettingsRow(
                icon = Icons.Rounded.Lock,
                title = "Change password",
                subtitle = "Update the password you use to log in",
                onClick = onChangePassword,
            )
        }

        // Sessions
        AppCard {
            SectionCaption("Sessions")
            Text(
                "You are signed in on this device. If you think someone else has access to your account, sign out of your other devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            OutlinedButton(
                onClick = { confirmOthers = true },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Sign out of other devices") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirmSignOut = true },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Sign out of this device") }

            vm.success?.let { FeedbackMessage(it, isError = false, modifier = Modifier.padding(top = 12.dp)) }
            vm.error?.let { FeedbackMessage(it, isError = true, modifier = Modifier.padding(top = 12.dp)) }
        }

        // Danger zone
        AppCard {
            SettingsRow(
                icon = Icons.Rounded.DeleteForever,
                title = "Delete account",
                subtitle = "Permanently delete your account and data",
                onClick = onDeleteAccount,
                danger = true,
            )
        }
    }

    if (confirmOthers) {
        ConfirmDialog(
            title = "Sign out other devices?",
            message = "Your account will be signed out on every other phone or browser. You will stay signed in here.",
            confirmLabel = "Sign out others",
            onConfirm = { confirmOthers = false; vm.signOutOtherDevices() },
            onDismiss = { confirmOthers = false },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            message = "You will be signed out on this device only. You can log back in at any time.",
            confirmLabel = "Sign out",
            onConfirm = { confirmSignOut = false; onSignOut() },
            onDismiss = { confirmSignOut = false },
        )
    }
}
