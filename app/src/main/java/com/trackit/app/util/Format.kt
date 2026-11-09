package com.trackit.app.util

import com.trackit.app.data.MoneyRecord
import com.trackit.app.data.RecordType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/** 100000 -> "Rp100.000" */
fun formatRp(value: Long): String {
    val grouped = abs(value).toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp$grouped"
}

fun formatSigned(type: RecordType, amount: Long): String = when (type) {
    RecordType.PLUS -> "+" + formatRp(amount)
    RecordType.MINUS -> "-" + formatRp(amount)
}

fun formatNet(net: Long): String = (if (net > 0) "+" else if (net < 0) "-" else "") + formatRp(net)

/** Short form for calendar cells: 20000 -> "20k", 1500000 -> "1.5M" */
fun compactAmount(value: Long): String {
    val v = abs(value)
    fun trim(d: Double): String {
        val x = Math.round(d * 10) / 10.0
        return if (x % 1.0 == 0.0) x.toLong().toString() else x.toString()
    }
    return when {
        v >= 1_000_000 -> trim(v / 1_000_000.0) + "M"
        v >= 1_000 -> trim(v / 1_000.0) + "k"
        else -> v.toString()
    }
}

fun MoneyRecord.localDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTime), ZoneId.systemDefault())

fun MoneyRecord.localDate(): LocalDate = localDateTime().toLocalDate()

fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

data class DaySummary(val plusTotal: Long, val minusTotal: Long) {
    val net: Long get() = plusTotal - minusTotal
}

fun List<MoneyRecord>.summarize() = DaySummary(
    plusTotal = filter { it.type == RecordType.PLUS }.sumOf { it.amount },
    minusTotal = filter { it.type == RecordType.MINUS }.sumOf { it.amount },
)
