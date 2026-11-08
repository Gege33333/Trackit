package com.trackit.app.ui.track

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.app.R
import com.trackit.app.data.LoadState
import com.trackit.app.data.MoneyRecord
import com.trackit.app.data.Preset
import com.trackit.app.data.RecordType
import com.trackit.app.ui.components.AppCard
import com.trackit.app.ui.components.ConfirmDialog
import com.trackit.app.ui.components.ErrorBanner
import com.trackit.app.ui.components.GENERIC_ERROR_MESSAGE
import com.trackit.app.ui.components.PresetDialog
import com.trackit.app.ui.components.PresetUseDialog
import com.trackit.app.ui.components.RecordDialog
import com.trackit.app.ui.components.SectionCaption
import com.trackit.app.ui.components.SummaryLine
import com.trackit.app.ui.components.TransactionRow
import com.trackit.app.ui.components.netResultColor
import com.trackit.app.ui.theme.minusCardBottom
import com.trackit.app.ui.theme.minusCardTop
import com.trackit.app.ui.theme.minusColor
import com.trackit.app.ui.theme.plusCardBottom
import com.trackit.app.ui.theme.plusCardTop
import com.trackit.app.ui.theme.plusColor
import com.trackit.app.ui.viewmodel.TrackViewModel
import com.trackit.app.util.formatNet
import com.trackit.app.util.formatRp
import com.trackit.app.util.localDate
import com.trackit.app.util.summarize
import com.trackit.app.util.toEpochMillis
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Used only for editing an existing record. Adding happens in the dropdown panel. */
private data class RecordDialogState(val record: MoneyRecord?, val type: RecordType)
private data class PresetDialogState(val preset: Preset?)

private val panelDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

@Composable
fun TrackScreen(vm: TrackViewModel) {
    val records by vm.records.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val recordsState by vm.recordsState.collectAsStateWithLifecycle()
    val presetsState by vm.presetsState.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val todayRecords = remember(records, today) {
        records.filter { it.localDate() == today }.sortedByDescending { it.dateTime }
    }
    val summary = remember(todayRecords) { todayRecords.summarize() }

    val loading = recordsState is LoadState.Loading || presetsState is LoadState.Loading
    val failure = (recordsState as? LoadState.Failed)?.message ?: (presetsState as? LoadState.Failed)?.message

    var panelOpen by remember { mutableStateOf(false) }
    var panelType by remember { mutableStateOf(RecordType.PLUS) }
    var recordDialog by remember { mutableStateOf<RecordDialogState?>(null) }
    var presetDialog by remember { mutableStateOf<PresetDialogState?>(null) }
    var usingPreset by remember { mutableStateOf<Preset?>(null) }
    var deleting by remember { mutableStateOf<MoneyRecord?>(null) }
    var presetEditMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun closeDialogs() {
        recordDialog = null
        presetDialog = null
        usingPreset = null
        deleting = null
        busy = false
        serverError = null
    }

    fun togglePanel(type: RecordType) {
        serverError = null
        if (panelOpen && panelType == type) {
            panelOpen = false
        } else {
            panelType = type
            panelOpen = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TrackHeader(today) }

            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            if (failure != null) {
                item { ErrorBanner(failure, onRetry = { vm.refresh() }) }
            }

            // Plus / Minus cards + dropdown add panel
            item {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ActionCard(
                            title = "Plus",
                            icon = Icons.Rounded.Add,
                            top = MaterialTheme.plusCardTop,
                            bottom = MaterialTheme.plusCardBottom,
                            selected = panelOpen && panelType == RecordType.PLUS,
                            modifier = Modifier.weight(1f),
                            onClick = { togglePanel(RecordType.PLUS) },
                        )
                        ActionCard(
                            title = "Minus",
                            icon = Icons.Rounded.Remove,
                            top = MaterialTheme.minusCardTop,
                            bottom = MaterialTheme.minusCardBottom,
                            selected = panelOpen && panelType == RecordType.MINUS,
                            modifier = Modifier.weight(1f),
                            onClick = { togglePanel(RecordType.MINUS) },
                        )
                    }
                    AnimatedVisibility(
                        visible = panelOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            key(panelType) {
                                AddRecordPanel(
                                    type = panelType,
                                    busy = busy,
                                    serverError = serverError,
                                    onClose = { panelOpen = false; serverError = null },
                                    onSave = { record ->
                                        busy = true
                                        serverError = null
                                        vm.saveRecord(record) { result ->
                                            busy = false
                                            result
                                                .onSuccess { panelOpen = false; toast("Record saved") }
                                                .onFailure { serverError = it.message ?: GENERIC_ERROR_MESSAGE }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Easy Input
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Easy Input",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (presets.isNotEmpty()) {
                            TextButton(onClick = { presetEditMode = !presetEditMode }) {
                                Text(
                                    if (presetEditMode) "Done" else "Edit",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { presetDialog = PresetDialogState(null) }) {
                            Text("New +", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        if (presetEditMode) "Tap a preset to edit it" else "Tap a preset, then choose Plus or Minus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (presets.isEmpty()) {
                        Text(
                            "No Easy Inputs yet. Tap New + to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(presets, key = { it.id }) { preset ->
                                PresetTile(preset, presetEditMode) {
                                    if (presetEditMode) presetDialog = PresetDialogState(preset) else usingPreset = preset
                                }
                            }
                        }
                    }
                }
            }

            // Today summary
            item {
                AppCard {
                    SectionCaption("Today")
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Net Result",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatNet(summary.net),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = netResultColor(summary.net),
                        )
                    }
                    HorizontalDivider(
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    SummaryLine("Total Plus", formatRp(summary.plusTotal), MaterialTheme.plusColor)
                    SummaryLine("Total Minus", formatRp(summary.minusTotal), MaterialTheme.minusColor)
                }
            }

            // Records
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Records",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    AppCard {
                        if (todayRecords.isEmpty()) {
                            Text(
                                if (loading || failure != null) "Nothing to show yet."
                                else "No records yet today. Add a plus or a minus above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            todayRecords.forEachIndexed { index, record ->
                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                TransactionRow(
                                    record = record,
                                    onEdit = { recordDialog = RecordDialogState(record, record.type) },
                                    onDelete = { deleting = record },
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    recordDialog?.let { state ->
        RecordDialog(
            initial = state.record,
            defaultType = state.type,
            defaultDate = null,
            busy = busy,
            serverError = serverError,
            onDismiss = { closeDialogs() },
            onSave = { record ->
                busy = true
                serverError = null
                vm.saveRecord(record) { result ->
                    busy = false
                    result
                        .onSuccess { closeDialogs(); toast("Record saved") }
                        .onFailure { serverError = it.message ?: GENERIC_ERROR_MESSAGE }
                }
            },
        )
    }

    presetDialog?.let { state ->
        PresetDialog(
            initial = state.preset,
            busy = busy,
            serverError = serverError,
            onDismiss = { closeDialogs() },
            onSave = { preset ->
                busy = true
                serverError = null
                vm.savePreset(preset) { result ->
                    busy = false
                    result
                        .onSuccess { closeDialogs(); toast("Easy Input saved") }
                        .onFailure { serverError = it.message ?: GENERIC_ERROR_MESSAGE }
                }
            },
            onDelete = { preset ->
                busy = true
                serverError = null
                vm.deletePreset(preset.id) { result ->
                    busy = false
                    result
                        .onSuccess { closeDialogs(); toast("Easy Input deleted") }
                        .onFailure { serverError = it.message ?: GENERIC_ERROR_MESSAGE }
                }
            },
        )
    }

    usingPreset?.let { preset ->
        PresetUseDialog(
            preset = preset,
            busy = busy,
            serverError = serverError,
            onDismiss = { closeDialogs() },
            onChoose = { type ->
                busy = true
                serverError = null
                vm.usePreset(preset, type) { result ->
                    busy = false
                    result
                        .onSuccess {
                            closeDialogs()
                            toast((if (type == RecordType.PLUS) "Plus" else "Minus") + " added: " + preset.name)
                        }
                        .onFailure { serverError = it.message ?: GENERIC_ERROR_MESSAGE }
                }
            },
        )
    }

    deleting?.let { record ->
        ConfirmDialog(
            title = "Delete record?",
            message = "This record will be permanently removed.",
            confirmLabel = "Delete",
            busy = busy,
            error = serverError,
            onDismiss = { closeDialogs() },
            onConfirm = {
                busy = true
                serverError = null
                vm.deleteRecord(record.id) { result ->
                    busy = false
                    result
                        .onSuccess { closeDialogs(); toast("Record deleted") }
                        .onFailure { serverError = it.message ?: GENERIC_ERROR_MESSAGE }
                }
            },
        )
    }
}

/** The dropdown form that opens under the Plus / Minus cards. */
@Composable
private fun AddRecordPanel(
    type: RecordType,
    busy: Boolean,
    serverError: String?,
    onClose: () -> Unit,
    onSave: (MoneyRecord) -> Unit,
) {
    val context = LocalContext.current
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val amount = amountText.toLongOrNull() ?: 0L
    val isPlus = type == RecordType.PLUS

    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isPlus) "New Plus Record" else "New Minus Record",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, enabled = !busy, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        PanelLabel("Amount (IDR)")
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter(Char::isDigit).take(12); validationError = null },
            placeholder = { Text("0") },
            singleLine = true,
            enabled = !busy,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { if (amount > 0) Text(formatRp(amount)) },
            isError = validationError != null,
            modifier = Modifier.fillMaxWidth(),
        )

        PanelLabel("Description")
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(80) },
            placeholder = { Text("Enter description") },
            singleLine = true,
            enabled = !busy,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        PanelLabel("Date")
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                    date.year, date.monthValue - 1, date.dayOfMonth,
                ).show()
            },
            enabled = !busy,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(date.format(panelDateFormat), maxLines = 1)
        }

        (validationError ?: serverError)?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // Gap between the Date button and the Save button
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (amount <= 0) {
                    validationError = "Enter an amount greater than zero."
                } else {
                    onSave(
                        MoneyRecord(
                            type = type,
                            amount = amount,
                            description = description.trim(),
                            // Only the date is chosen by the user; the time of day is kept
                            // in the background so records stay in order within a day.
                            dateTime = LocalDateTime.of(date, LocalTime.now().withNano(0)).toEpochMillis(),
                        )
                    )
                }
            },
            enabled = !busy,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.background,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (busy) "Saving…" else "Save record", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun TrackHeader(today: LocalDate) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CalendarLogo(height = 52.dp)
            Image(
                painter = painterResource(R.drawable.trackit_wordmark),
                contentDescription = "Trackit",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.height(36.dp),
            )
        }
        Text(
            today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** The calendar logo: a theme-colored base layer with the orange accent layer on top. */
@Composable
private fun CalendarLogo(height: Dp) {
    Box {
        Image(
            painter = painterResource(R.drawable.trackit_calendar_base),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.height(height),
        )
        Image(
            painter = painterResource(R.drawable.trackit_calendar_accent),
            contentDescription = null,
            modifier = Modifier.height(height),
        )
    }
}

/** Plus / Minus card: just the icon and the title, centered. */
@Composable
private fun ActionCard(
    title: String,
    icon: ImageVector,
    top: Color,
    bottom: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .then(if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PresetTile(preset: Preset, editMode: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.widthIn(min = 150.dp, max = 220.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatRp(preset.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (editMode) Icons.Rounded.Edit else Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
