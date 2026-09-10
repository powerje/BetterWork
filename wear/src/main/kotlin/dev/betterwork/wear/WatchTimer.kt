package dev.betterwork.wear

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.presentation.*

@Composable
internal fun WatchTimer(
    timer: TimerUi,
    app: BetterApp,
    active: Boolean,
    controls: () -> Unit,
    done: () -> Unit,
) {
    val state = timer.session
    val recovered by app.recovered.collectAsState()
    val handle = app.sessionHandle
    WatchList(timer = true, resetKey = if (active) state?.phase to recovered else null) {
        if (state == null) item { WatchAction("Routines", action = done) }
        else if (timer.terminal) {
            item { WatchHeading(timer.status) }
            item { WatchText(state.routine.name) }
            item { WatchCountdown(formatTime(state.activeMs), "active time") }
            item {
                WatchAction("Done", primary = true) {
                    app.dismiss()
                    done()
                }
            }
        } else {
            item {
                val label =
                    when {
                        recovered -> "Interrupted · ${timer.label}"
                        state.phase == Phase.PAUSED -> "Paused · ${timer.label}"
                        state.phase == Phase.WAITING -> "${timer.label} complete"
                        else -> timer.label
                    }
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { heading() },
                )
            }
            item { WatchCountdown(timer.time, "remaining") }
            item {
                WatchAction(timer.primaryLabel, primary = true) {
                    app.command(timer.primaryCommand, handle)
                }
            }
            item {
                Column {
                    Spacer(Modifier.height(6.dp))
                    ChildButton(
                        onClick = controls,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Controls", textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchCountdown(value: String, description: String) {
    val typography = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = with(density) { maxWidth.toPx() }
        val styles =
            listOf(
                if (maxWidth <= 160.dp) typography.numeralMedium else typography.numeralLarge,
                typography.numeralMedium,
                typography.numeralSmall,
                typography.numeralExtraSmall,
            )
        val style =
            styles.firstOrNull {
                measurer.measure(AnnotatedString(value), it, softWrap = false).size.width <= width
            } ?: styles.last()
        Text(
            value,
            style = style.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = "$value $description" },
        )
    }
}
