package com.trackit.app.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.app.data.LoadState
import com.trackit.app.ui.components.AppCard
import com.trackit.app.ui.components.ErrorBanner
import com.trackit.app.ui.components.FeedbackMessage
import com.trackit.app.ui.components.MonthPickerDialog
import com.trackit.app.ui.theme.minusColor
import com.trackit.app.ui.theme.plusColor
import com.trackit.app.ui.viewmodel.ExportViewModel
import com.trackit.app.util.ExportPalette
import com.trackit.app.util.renderMonthImage
import com.trackit.app.util.saveBitmapToPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthLabelFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** Profile card: choose any month and save its calendar picture to Photos. */
@Composable
fun ExportCard(vm: ExportViewModel) {
    val month by vm.month.collectAsStateWithLifecycle()
    val data by vm.state.collectAsStateWithLifecycle()
    val loadState by vm.loadState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    val palette = ExportPalette(
        background = MaterialTheme.colorScheme.background.toArgb(),
        card = MaterialTheme.colorScheme.surface.toArgb(),
        cell = MaterialTheme.colorScheme.surfaceVariant.toArgb(),
        text = MaterialTheme.colorScheme.onSurface.toArgb(),
        muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        plus = MaterialTheme.plusColor.toArgb(),
        minus = MaterialTheme.minusColor.toArgb(),
    )

    fun doSave() {
        if (saving) return
        val snapshot = data
        saving = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = renderMonthImage(snapshot.month, snapshot.dayNet, snapshot.monthNet, palette)
                    val name = "Trackit_${snapshot.month}_" +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
                    try {
                        saveBitmapToPhotos(context, bitmap, name).getOrThrow()
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            saving = false
            messageIsError = result.isFailure
            message = if (result.isSuccess) {
                "${snapshot.month.format(monthLabelFormat)} saved to Photos (Pictures > Trackit)."
            } else {
                "Couldn't save the picture. Please try again."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            doSave()
        } else {
            messageIsError = true
            message = "Allow storage access to save the picture."
        }
    }

    fun onSaveClick() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            doSave()
        }
    }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Save calendar picture",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Choose a month and save it to Photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Month selector: arrows + tappable label that opens the month picker
        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthArrow(Icons.Rounded.ChevronLeft, "Previous month") { vm.previousMonth(); message = null }
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable { pickerOpen = true }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    month.format(monthLabelFormat),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            MonthArrow(Icons.Rounded.ChevronRight, "Next month") { vm.nextMonth(); message = null }
        }

        if (loadState is LoadState.Loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        (loadState as? LoadState.Failed)?.let { failed ->
            ErrorBanner(failed.message, onRetry = { vm.refresh() }, modifier = Modifier.padding(top = 12.dp))
        }
        message?.let { FeedbackMessage(it, isError = messageIsError, modifier = Modifier.padding(top = 12.dp)) }

        Button(
            onClick = { onSaveClick() },
            enabled = loadState is LoadState.Ready && !saving,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(52.dp),
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save to Photos", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (pickerOpen) {
        MonthPickerDialog(
            initial = month,
            onDismiss = { pickerOpen = false },
            onPick = {
                vm.setMonth(it)
                message = null
                pickerOpen = false
            },
        )
    }
}

@Composable
private fun MonthArrow(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}
