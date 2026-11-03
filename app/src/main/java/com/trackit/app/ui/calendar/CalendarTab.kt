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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.trackit.app.data.MoneyRecord
import com.trackit.app.ui.components.ConfirmDialog
import com.trackit.app.ui.components.RecordDialog
import com.trackit.app.ui.components.RecordRow
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

private val calendarWeekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private const val CALENDAR_CELL_RATIO = 0.8f

@Composable
fun CalendarTab(vm: CalendarViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MoneyRecord?>(null) }
    var deleting by remember { mutableStateOf<MoneyRecord?>(null) }
    val today = LocalDate.now()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        // A. Month header + monthly total, side by side
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    s.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.previousMonth() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    IconButton(onClick = { vm.nextMonth() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Month total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatNet(s.monthNet),
                    color = when {
                        s.monthNet > 0 -> MaterialTheme.plusColor
                        s.monthNet < 0 -> MaterialTheme.minusColor
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }

        // B. Calendar grid
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            calendarWeekdays.forEach { name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val offset = s.month.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(offset) { null } + (1..s.month.lengthOfMonth()).map { s.month.atDay(it) }
        val padded: List<LocalDate?> = cells + List((7 - cells.size % 7) % 7) { null }
        padded.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).padding(2.dp).aspectRatio(CALENDAR_CELL_RATIO))
                    } else {
                        CalendarDayCell(
                            date = date,
                            net = s.dayNet[date],
                            selected = date == s.selected,
                            isToday = date == today,
                            onClick = { vm.select(date) },
                            modifier = Modifier.weight(1f).padding(2.dp),
                        )
                    }
                }
            }
        }

        // C. Daily details
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    s.selected.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    "Records",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (s.dayRecords.isEmpty()) {
                    Text(
                        "No records for this date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    s.dayRecords.forEach { record ->
                        RecordRow(record, onEdit = { editing = record }, onDelete = { deleting = record })
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Summary", style = MaterialTheme.typography.titleSmall)
                SummaryLine("Total Plus", formatRp(s.daySummary.plusTotal), MaterialTheme.plusColor)
                SummaryLine("Total Minus", formatRp(s.daySummary.minusTotal), MaterialTheme.minusColor)
                SummaryLine(
                    "Net Result",
                    formatNet(s.daySummary.net),
                    when {
                        s.daySummary.net > 0 -> MaterialTheme.plusColor
                        s.daySummary.net < 0 -> MaterialTheme.minusColor
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    bold = true,
                )
            }
        }
    }

    editing?.let { record ->
        RecordDialog(
            initial = record,
            defaultType = record.type,
            defaultDate = null,
            onDismiss = { editing = null },
            onSave = { vm.saveRecord(it); editing = null },
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
    val background = if (hasResult) accent.copy(alpha = 0.18f) else scheme.surfaceVariant.copy(alpha = 0.55f)
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .aspectRatio(CALENDAR_CELL_RATIO)
            .clip(shape)
            .background(background)
            .then(if (selected) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isToday) scheme.primary else scheme.onSurface,
            )
            if (hasResult) {
                Text(
                    (if (netValue > 0) "+" else "-") + compactAmount(netValue),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                )
            }
        }
    }
}