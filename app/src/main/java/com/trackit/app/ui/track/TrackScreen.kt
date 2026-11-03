package com.trackit.app.ui.track

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.app.data.MoneyRecord
import com.trackit.app.data.Preset
import com.trackit.app.data.RecordType
import com.trackit.app.ui.components.ConfirmDialog
import com.trackit.app.ui.components.PresetDialog
import com.trackit.app.ui.components.PresetUseDialog
import com.trackit.app.ui.components.RecordDialog
import com.trackit.app.ui.components.RecordRow
import com.trackit.app.ui.components.SummaryLine
import com.trackit.app.ui.theme.minusColor
import com.trackit.app.ui.theme.plusColor
import com.trackit.app.ui.viewmodel.TrackViewModel
import com.trackit.app.util.formatNet
import com.trackit.app.util.formatRp
import com.trackit.app.util.localDate
import com.trackit.app.util.summarize
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class RecordDialogState(val record: MoneyRecord?, val type: RecordType)
private data class PresetDialogState(val preset: Preset?)

@Composable
fun TrackScreen(vm: TrackViewModel) {
    val records by vm.records.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val todayRecords = remember(records, today) {
        records.filter { it.localDate() == today }.sortedByDescending { it.dateTime }
    }
    val summary = remember(todayRecords) { todayRecords.summarize() }

    var recordDialog by remember { mutableStateOf<RecordDialogState?>(null) }
    var presetDialog by remember { mutableStateOf<PresetDialogState?>(null) }
    var usingPreset by remember { mutableStateOf<Preset?>(null) }
    var deleting by remember { mutableStateOf<MoneyRecord?>(null) }
    var presetEditMode by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text("Trackit", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Add record buttons
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { recordDialog = RecordDialogState(null, RecordType.PLUS) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.plusColor,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text("+ Plus", maxLines = 1) }
                    Button(
                        onClick = { recordDialog = RecordDialogState(null, RecordType.MINUS) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.minusColor,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text("- Minus", maxLines = 1) }
                }
            }

            // Easy Input
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Easy Input", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (presetEditMode) "Tap a preset to edit it" else "Tap a preset, then choose Plus or Minus",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (presets.isNotEmpty()) {
                            TextButton(onClick = { presetEditMode = !presetEditMode }) {
                                Text(if (presetEditMode) "Done" else "Edit")
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(presets, key = { it.id }) { preset ->
                            PresetCard(preset, presetEditMode) {
                                if (presetEditMode) presetDialog = PresetDialogState(preset) else usingPreset = preset
                            }
                        }
                        item { NewPresetCard { presetDialog = PresetDialogState(null) } }
                    }
                }
            }

            // Today
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Column(Modifier.padding(top = 8.dp)) {
                            SummaryLine("Total Plus", formatRp(summary.plusTotal), MaterialTheme.plusColor)
                            SummaryLine("Total Minus", formatRp(summary.minusTotal), MaterialTheme.minusColor)
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            SummaryLine(
                                "Net Result",
                                formatNet(summary.net),
                                when {
                                    summary.net > 0 -> MaterialTheme.plusColor
                                    summary.net < 0 -> MaterialTheme.minusColor
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                bold = true,
                            )
                        }
                    }
                }
            }

            if (todayRecords.isEmpty()) {
                item {
                    Text(
                        "No records yet today. Add a plus or a minus above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(todayRecords, key = { it.id }) { record ->
                    RecordRow(
                        record = record,
                        onEdit = { recordDialog = RecordDialogState(record, record.type) },
                        onDelete = { deleting = record },
                    )
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
            onDismiss = { recordDialog = null },
            onSave = { vm.saveRecord(it); recordDialog = null },
        )
    }
    presetDialog?.let { state ->
        PresetDialog(
            initial = state.preset,
            onDismiss = { presetDialog = null },
            onSave = { vm.savePreset(it); presetDialog = null },
            onDelete = { vm.deletePreset(it.id); presetDialog = null },
        )
    }
    usingPreset?.let { preset ->
        PresetUseDialog(
            preset = preset,
            onChoose = { type -> vm.usePreset(preset, type); usingPreset = null },
            onDismiss = { usingPreset = null },
        )
    }
    deleting?.let { record ->
        ConfirmDialog(
            title = "Delete record?",
            message = "This record will be permanently removed.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteRecord(record.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun PresetCard(preset: Preset, editMode: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.widthIn(min = 120.dp, max = 200.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                preset.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatRp(preset.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (editMode) {
                Text("Edit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun NewPresetCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("New", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}