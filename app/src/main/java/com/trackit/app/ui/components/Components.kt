package com.trackit.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trackit.app.data.MoneyRecord
import com.trackit.app.data.Preset
import com.trackit.app.data.RecordType
import com.trackit.app.ui.theme.minusColor
import com.trackit.app.ui.theme.plusColor
import com.trackit.app.util.formatRp
import com.trackit.app.util.localDateTime
import com.trackit.app.util.toEpochMillis
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

const val GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again."

@Composable
fun typeColor(type: RecordType): Color = when (type) {
    RecordType.PLUS -> MaterialTheme.plusColor
    RecordType.MINUS -> MaterialTheme.minusColor
}

fun typeLabel(type: RecordType): String = when (type) {
    RecordType.PLUS -> "Plus (+)"
    RecordType.MINUS -> "Minus (-)"
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

@Composable
fun SummaryLine(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Shown when loading data from the server failed. */
@Composable
fun ErrorBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "Please wait…" else confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
fun RecordDialog(
    initial: MoneyRecord?,
    defaultType: RecordType,
    defaultDate: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (MoneyRecord) -> Unit,
    busy: Boolean = false,
    serverError: String? = null,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(initial?.type ?: defaultType) }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var dateTime by remember {
        mutableStateOf(
            initial?.localDateTime()
                ?: LocalDateTime.of(defaultDate ?: LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    val amount = amountText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Add record" else "Edit record") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RecordType.PLUS to "+ Plus",
                        RecordType.MINUS to "- Minus",
                    ).forEach { (t, label) ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            enabled = !busy,
                            label = { Text(label, maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit).take(12); validationError = null },
                    label = { Text("Amount") },
                    prefix = { Text("Rp") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(if (amount > 0) formatRp(amount) else "") },
                    isError = validationError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(80) },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                dateTime = LocalDateTime.of(LocalDate.of(y, m + 1, d), dateTime.toLocalTime())
                            },
                            dateTime.year, dateTime.monthValue - 1, dateTime.dayOfMonth,
                        ).show()
                    },
                ) { Text(dateTime.toLocalDate().format(dateFormat), maxLines = 1) }
                (validationError ?: serverError)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (amount <= 0) {
                        validationError = "Enter an amount greater than zero."
                    } else {
                        onSave(
                            MoneyRecord(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                type = type,
                                amount = amount,
                                description = description.trim(),
                                dateTime = dateTime.toEpochMillis(),
                            )
                        )
                    }
                },
            ) { Text(if (busy) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** Add / edit dialog for an Easy Input preset. */
@Composable
fun PresetDialog(
    initial: Preset?,
    onDismiss: () -> Unit,
    onSave: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
    busy: Boolean = false,
    serverError: String? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val amount = amountText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "New Easy Input" else "Edit Easy Input") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30); validationError = null },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Daily Result") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit).take(12); validationError = null },
                    label = { Text("Amount") },
                    prefix = { Text("Rp") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(if (amount > 0) formatRp(amount) else "") },
                    modifier = Modifier.fillMaxWidth(),
                )
                (validationError ?: serverError)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }, enabled = !busy) {
                        Text("Delete this Easy Input", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    when {
                        name.isBlank() -> validationError = "Please enter a name."
                        amount <= 0 -> validationError = "Enter an amount greater than zero."
                        else -> onSave(Preset(id = initial?.id ?: UUID.randomUUID().toString(), name = name.trim(), amount = amount))
                    }
                },
            ) { Text(if (busy) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** Shown after tapping an Easy Input preset: choose where the preset amount goes. */
@Composable
fun PresetUseDialog(
    preset: Preset,
    onChoose: (RecordType) -> Unit,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    serverError: String? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(preset.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    formatRp(preset.amount),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (busy) "Adding…" else "Add this amount as:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onChoose(RecordType.PLUS) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+ Plus", color = MaterialTheme.plusColor, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onChoose(RecordType.MINUS) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("- Minus", color = MaterialTheme.minusColor, fontWeight = FontWeight.SemiBold)
                    }
                }
                serverError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}