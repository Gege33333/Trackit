package com.trackit.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.app.data.LoadState
import com.trackit.app.data.MoneyRecord
import com.trackit.app.ui.components.ConfirmDialog
import com.trackit.app.ui.components.ErrorBanner
import com.trackit.app.ui.components.GENERIC_ERROR_MESSAGE
import com.trackit.app.ui.components.RecordDialog
import com.trackit.app.ui.components.SummaryLine
import com.trackit.app.ui.theme.minusColor
import com.trackit.app.ui.theme.plusColor
import com.trackit.app.ui.viewmodel.CalendarViewModel
import com.trackit.app.util.compactAmount
import com.trackit.app.util.formatNet
import com.trackit.app.util.formatRp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale


@Composable
fun CalendarTab(vm: CalendarViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val loadState by vm.loadState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MoneyRecord?>(null) }
    var deleting by remember { mutableStateOf<MoneyRecord?>(null) }
    var busy by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()

    fun closeDialogs() {
        editing = null
        deleting = null
        busy = false
        serverError = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (loadState is LoadState.Loading) {
        }
        (loadState as? LoadState.Failed)?.let { failed ->
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    s.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatNet(s.monthNet),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

                    calendarWeekdays.forEach { name ->
                        Text(
                            name,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val offset = s.month.atDay(1).dayOfWeek.value - 1
                val padded: List<LocalDate?> = cells + List((7 - cells.size % 7) % 7) { null }
                padded.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) {
                            } else {
                                CalendarDayCell(
                                    date = date,
                                    net = s.dayNet[date],
                                    selected = date == s.selected,
                                    isToday = date == today,
                                    onClick = { vm.select(date) },
                                )
                            }
                        }
                    }
                }

            Text(
                s.selected.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                "Records",
            )
            if (s.dayRecords.isEmpty()) {
                Text(
                    "No records for this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                }
            }
        }
    }

    editing?.let { record ->
        RecordDialog(
            initial = record,
            defaultType = record.type,
            defaultDate = null,
            busy = busy,
            serverError = serverError,
            onDismiss = { closeDialogs() },
            onSave = { updated ->
                busy = true
                serverError = null
                vm.saveRecord(updated) { result ->
                    busy = false
                    result
                        .onSuccess { closeDialogs() }
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
                        .onSuccess { closeDialogs() }
                        .onFailure { serverError = it.message ?: GENERIC_ERROR_MESSAGE }
                }
            },
        )
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    net: Long?,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val hasResult = net != null && net != 0L
    val netValue: Long = net ?: 0L
    val accent: Color = when {
        !hasResult -> scheme.onSurfaceVariant
        netValue > 0 -> MaterialTheme.plusColor
        else -> MaterialTheme.minusColor
    }

    Box(
        modifier = modifier
            .aspectRatio(CALENDAR_CELL_RATIO)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
            )
            if (hasResult) {
                Text(
                    (if (netValue > 0) "+" else "-") + compactAmount(netValue),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}