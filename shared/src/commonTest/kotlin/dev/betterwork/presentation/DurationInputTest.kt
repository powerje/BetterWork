package dev.betterwork.presentation

import kotlin.test.*

class DurationInputTest {
    @Test
    fun editingImportedDurationsPreservesMilliseconds() {
        for (duration in listOf(1L, 50L, 999L, 1000L, 60_001L, 604_800_000L)) {
            assertEquals(duration, parseDurationSeconds(durationSeconds(duration)))
        }
    }

    @Test
    fun invalidDurationsDoNotOverflowOrSilentlyRound() {
        for (text in
            listOf("", "0", "-1", "NaN", "Infinity", "0.0001", "604801", "999999999999999999999")) {
            assertFailsWith<IllegalArgumentException> { parseDurationSeconds(text) }
        }
    }
}
