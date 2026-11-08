package com.trackit.app.ui.profile

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.app.ui.components.AppCard
import com.trackit.app.ui.components.FeedbackMessage
import com.trackit.app.ui.components.ScreenHeader
import com.trackit.app.ui.components.SectionCaption
import com.trackit.app.ui.viewmodel.AccountViewModel

@Composable
fun EditProfileScreen(vm: AccountViewModel, onBack: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(user?.name ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var localError by remember { mutableStateOf<String?>(null) }
    val busy = vm.busy

    LaunchedEffect(Unit) {
        vm.clearMessages()
        vm.loadDetails()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Edit Profile", onBack)

        AppCard {
            SectionCaption("Current email")
            Text(
                user?.email ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            vm.details?.pendingEmail?.let { pending ->
                Text(
                    "Waiting for you to confirm: $pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        AppCard {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40); localError = null },
                label = { Text("Display name") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; localError = null },
                label = { Text("Email") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                supportingText = {
                    Text("Changing your email sends a confirmation link. It only changes after you confirm it.")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            vm.success?.let { FeedbackMessage(it, isError = false, modifier = Modifier.padding(top = 12.dp)) }
            (localError ?: vm.error)?.let { FeedbackMessage(it, isError = true, modifier = Modifier.padding(top = 12.dp)) }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        val n = name.trim()
                        val e = email.trim()
                        localError = when {
                            n.isEmpty() -> "Please enter your name."
                            n.length > 40 -> "Name must be 40 characters or fewer."
                            !Patterns.EMAIL_ADDRESS.matcher(e).matches() -> "Please enter a valid email address."
                            else -> null
                        }
                        if (localError == null) vm.saveProfile(n, e)
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}
