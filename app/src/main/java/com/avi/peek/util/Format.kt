package com.avi.peek.util

import android.text.format.DateUtils
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Long.compactCount(): String {
    return when {
        this < 1_000 -> toString()
        this < 1_000_000 -> {
            val value = this / 1_000.0
            if (value < 10) DecimalFormat("0.0").format(value) + "K" else DecimalFormat("0").format(value) + "K"
        }
        else -> {
            val value = this / 1_000_000.0
            if (value < 10) DecimalFormat("0.0").format(value) + "M" else DecimalFormat("0").format(value) + "M"
        }
    }
}

fun Long.relativeTime(now: Long = System.currentTimeMillis()): String {
    val millis = this * 1000
    return DateUtils.getRelativeTimeSpanString(
        millis,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

fun Long.fullDate(): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
    return Instant.ofEpochSecond(this).atZone(ZoneId.systemDefault()).format(formatter)
}

fun Long.fullDateTime(): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())
    return Instant.ofEpochSecond(this).atZone(ZoneId.systemDefault()).format(formatter)
}

fun Long.fileSize(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return DecimalFormat("0.#").format(kb) + " KB"
    val mb = kb / 1024.0
    if (mb < 1024) return DecimalFormat("0.#").format(mb) + " MB"
    return DecimalFormat("0.#").format(mb / 1024.0) + " GB"
}

fun Long.durationSeconds(): String {
    val total = this.coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes > 0) "%d:%02d".format(minutes, seconds) else "0:%02d".format(seconds)
}

fun String.instagramWebUrl(): String = "https://www.instagram.com/$this/"
fun String.instagramPostUrl(): String = "https://www.instagram.com/p/$this/"
