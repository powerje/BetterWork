package dev.betterwork.presentation

import dev.betterwork.domain.MAX_DURATION_MS

fun durationSeconds(ms: Long): String =
    if (ms % 1000 == 0L) (ms / 1000).toString()
    else "${ms / 1000}.${(ms % 1000).toString().padStart(3, '0').trimEnd('0')}"

fun parseDurationSeconds(text: String): Long {
    require(Regex("[0-9]{1,6}(\\.[0-9]{1,3})?").matches(text)) {
        "Enter seconds with at most three decimal places"
    }
    val parts = text.split('.')
    val duration = parts[0].toLong() * 1000 + (parts.getOrNull(1)?.padEnd(3, '0')?.toLong() ?: 0)
    require(duration in 1..MAX_DURATION_MS) { "Duration must be positive and at most 7 days" }
    return duration
}
