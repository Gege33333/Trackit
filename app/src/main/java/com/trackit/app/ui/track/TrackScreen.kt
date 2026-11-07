package com.trackit.app.ui.track

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.app.R
import com.trackit.app.data.LoadState
import com.trackit.app.data.MoneyRecord
import com.trackit.app.data.Preset
import com.trackit.app.data.RecordType
import com.trackit.app.ui.components.ConfirmDialog
import com.trackit.app.ui.components.ErrorBanner
import com.trackit.app.ui.components.GENERIC_ERROR_MESSAGE
import com.trackit.app.ui.components.PresetDialog
import com.trackit.app.ui.components.PresetUseDialog
import com.trackit.app.ui.components.RecordDialog
import com.trackit.app.ui.components.SummaryLine
import com.trackit.app.ui.theme.minusColor
import com.trackit.app.ui.theme.plusColor
import com.trackit.app.ui.viewmodel.TrackViewModel
import com.trackit.app.util.formatNet
import com.trackit.app.util.formatRp
import com.trackit.app.util.localDate
import com.trackit.app.util.summarize
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class RecordDialogState(val record: MoneyRecord?, val type: RecordType)
private data class PresetDialogState(val preset: Preset?)

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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            if (failure != null) {
                item { ErrorBanner(failure, onRetry = { vm.refresh() }) }
            }

            item {
                            modifier = Modifier.weight(1f),
                            modifier = Modifier.weight(1f),
                }
            }

            // Easy Input
            item {
                    Text(
                        if (presetEditMode) "Tap a preset to edit it" else "Tap a preset, then choose Plus or Minus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(presets, key = { it.id }) { preset ->
                                    if (presetEditMode) presetDialog = PresetDialogState(preset) else usingPreset = preset
                                }
                            }
                    }
                }
            }

            item {
                    ) {
                    SummaryLine("Total Plus", formatRp(summary.plusTotal), MaterialTheme.plusColor)
                    SummaryLine("Total Minus", formatRp(summary.minusTotal), MaterialTheme.minusColor)
                }
            }

            item {
                            Text(
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
    ) {
                Text(
                    preset.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatRp(preset.amount),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}