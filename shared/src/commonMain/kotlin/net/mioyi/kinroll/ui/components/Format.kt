package net.mioyi.kinroll.ui.components

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** `1284` → `1,284`. */
fun formatCount(n: Int): String {
    val digits = abs(n.toLong()).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$grouped" else grouped
}

/** Decimal units, matching the model sizes quoted in the plan (209 MB, 559 MB). */
fun formatBytes(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1000.0
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    return "${oneDecimal(value)} ${units[unit]}"
}

/** One decimal below 100, none above (`3.4 MB`, `209 MB`). */
fun oneDecimal(value: Double): String {
    if (value >= 100) return value.roundToLong().toString()
    val tenths = (value * 10).roundToLong()
    val whole = tenths / 10
    val frac = tenths % 10
    return if (frac == 0L) whole.toString() else "$whole.$frac"
}

/** Up to [decimals] places, trailing zeros trimmed. */
fun trimDecimal(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val whole = scaled / factor
    val frac = (scaled % factor).toString().padStart(decimals, '0').trimEnd('0')
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return if (frac.isEmpty()) "$sign$whole" else "$sign$whole.$frac"
}

@OptIn(ExperimentalTime::class)
private fun local(epochMillis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())

private fun Int.pad2() = toString().padStart(2, '0')

fun formatDate(epochMillis: Long): String {
    val t = local(epochMillis)
    return "${t.year}-${t.month.ordinal.plus(1).pad2()}-${t.day.pad2()}"
}

fun formatDateTime(epochMillis: Long): String {
    val t = local(epochMillis)
    return "${formatDate(epochMillis)} ${t.hour.pad2()}:${t.minute.pad2()}"
}

/** 0.004 → `1/250 s`, 2.5 → `2.5 s`. */
fun formatExposure(seconds: Double): String =
    if (seconds > 0 && seconds < 1) "1/${(1 / seconds).roundToLong()} s" else "${trimDecimal(seconds, 1)} s"
