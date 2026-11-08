package com.trackit.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Colors (ARGB ints) used to draw the picture, taken from the current app theme. */
data class ExportPalette(
    val background: Int,
    val card: Int,
    val cell: Int,
    val text: Int,
    val muted: Int,
    val plus: Int,
    val minus: Int,
)

private fun withAlpha(color: Int, alpha: Float): Int {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (color and 0x00FFFFFF) or (a shl 24)
}

/**
 * Draws the month calendar (grid + month total) as a bitmap.
 * Only dates and net amounts are drawn; no personal information.
 */
fun renderMonthImage(
    month: YearMonth,
    dayNet: Map<LocalDate, Long>,
    monthNet: Long,
    p: ExportPalette,
): Bitmap {
    val width = 1080
    val margin = 48f
    val cardPad = 24f
    val innerW = width - 2 * margin - 2 * cardPad
    val cellW = innerW / 7f
    val cellH = cellW * 0.95f
    val daysInMonth = month.lengthOfMonth()
    val offset = month.atDay(1).dayOfWeek.value - 1 // Monday first
    val rows = (offset + daysInMonth + 6) / 7

    val headerTop = 64f
    val headerBottom = headerTop + 130f
    val cardTop = headerBottom + 36f
    val weekdayH = 56f
    val gridTop = cardTop + cardPad + weekdayH
    val cardBottom = gridTop + rows * cellH + cardPad
    val height = (cardBottom + 110f).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(p.background)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Header: month on the left, month total on the right
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.LEFT
    paint.color = p.text
    paint.textSize = 64f
    canvas.drawText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), margin, headerTop + 56f, paint)

    paint.textAlign = Paint.Align.RIGHT
    paint.color = p.muted
    paint.textSize = 26f
    paint.letterSpacing = 0.12f
    canvas.drawText("MONTH TOTAL", width - margin, headerTop + 22f, paint)
    paint.letterSpacing = 0f
    paint.color = when {
        monthNet > 0 -> p.plus
        monthNet < 0 -> p.minus
        else -> p.text
    }
    paint.textSize = 64f
    canvas.drawText(formatNet(monthNet), width - margin, headerTop + 92f, paint)

    // Card
    paint.color = p.card
    canvas.drawRoundRect(RectF(margin, cardTop, width - margin, cardBottom), 48f, 48f, paint)

    // Weekday letters
    paint.textAlign = Paint.Align.CENTER
    paint.color = p.muted
    paint.textSize = 28f
    val gridLeft = margin + cardPad
    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, letter ->
        canvas.drawText(letter, gridLeft + cellW * (i + 0.5f), cardTop + cardPad + 36f, paint)
    }

    // Day cells
    val gap = 8f
    for (index in 0 until rows * 7) {
        val row = index / 7
        val col = index % 7
        val left = gridLeft + col * cellW + gap / 2f
        val top = gridTop + row * cellH + gap / 2f
        val rect = RectF(left, top, left + cellW - gap, top + cellH - gap)
        val dayNumber = index - offset + 1

        if (dayNumber < 1 || dayNumber > daysInMonth) {
            paint.color = withAlpha(p.cell, 0.35f)
            canvas.drawRoundRect(rect, 26f, 26f, paint)
            continue
        }

        val net = dayNet[month.atDay(dayNumber)] ?: 0L
        val hasResult = net != 0L
        val accent = if (net > 0) p.plus else p.minus

        paint.color = if (hasResult) withAlpha(accent, 0.18f) else p.cell
        canvas.drawRoundRect(rect, 26f, 26f, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.color = p.text
        paint.textSize = 36f
        val dayBaseline = if (hasResult) rect.top + rect.height() * 0.45f else rect.top + rect.height() * 0.6f
        canvas.drawText(dayNumber.toString(), rect.centerX(), dayBaseline, paint)

        if (hasResult) {
            paint.color = accent
            paint.textSize = 26f
            canvas.drawText(
                (if (net > 0) "+" else "-") + compactAmount(net),
                rect.centerX(),
                rect.top + rect.height() * 0.78f,
                paint,
            )
        }
    }

    // Footer
    paint.textAlign = Paint.Align.CENTER
    paint.color = p.muted
    paint.textSize = 34f
    canvas.drawText("Trackit", width / 2f, cardBottom + 70f, paint)

    return bitmap
}

/** Saves the bitmap to the phone's gallery (Pictures/Trackit). */
fun saveBitmapToPhotos(context: Context, bitmap: Bitmap, baseName: String): Result<Unit> = runCatching {
    val fileName = "$baseName.png"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Trackit")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create the image file.")
        try {
            val stream = resolver.openOutputStream(uri) ?: error("Couldn't open the image file.")
            stream.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) error("Couldn't write the image.")
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    } else {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Trackit")
        if (!dir.exists() && !dir.mkdirs()) error("Couldn't create the Trackit folder.")
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) error("Couldn't write the image.")
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
    }
}
