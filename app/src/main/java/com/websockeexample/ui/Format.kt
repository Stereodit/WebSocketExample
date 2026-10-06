package com.websockeexample.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private val us = DecimalFormatSymbols(Locale.US)
private val clock: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

internal fun formatPrice(value: Double): String {
    val pattern = when {
        value >= 1000 -> "#,##0.00"
        value >= 1 -> "#,##0.00"
        value >= 0.1 -> "0.0000"
        else -> "0.00000000"
    }
    return DecimalFormat(pattern, us).format(value)
}

internal fun formatQuantity(value: Double): String = DecimalFormat("0.00####", us).format(value)

internal fun formatPercent(fraction: Double): String =
    DecimalFormat("+0.00%;-0.00%", us).format(fraction)

internal fun formatVolume(value: Double): String = when {
    value >= 1_000_000 -> DecimalFormat("0.00", us).format(value / 1_000_000) + "M"
    value >= 1_000 -> DecimalFormat("0.0", us).format(value / 1_000) + "K"
    else -> DecimalFormat("#,##0", us).format(value)
}

internal fun formatCount(value: Long): String = DecimalFormat("#,##0", us).format(value)

internal fun formatClock(millis: Long): String = clock.format(Instant.ofEpochMilli(millis))

internal fun formatUptime(sinceMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - sinceMillis) / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
