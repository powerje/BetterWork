package dev.betterwork.wear

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.*
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.*

private val WatchColors =
    ColorScheme(
        primary = Color(0xFF9CD5AC),
        onPrimary = Color(0xFF00391D),
        primaryDim = Color(0xFF82B991),
        primaryContainer = Color(0xFF194E31),
        onPrimaryContainer = Color(0xFFB7F2C6),
        secondary = Color(0xFFBCC8BE),
        onSecondary = Color(0xFF27342B),
        secondaryContainer = Color(0xFF36473C),
        onSecondaryContainer = Color(0xFFD8E5DB),
        background = Color.Black,
        onBackground = Color.White,
        surfaceContainer = Color(0xFF19221C),
        surfaceContainerLow = Color(0xFF111812),
        surfaceContainerHigh = Color(0xFF27322B),
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFBCC8BE),
        outline = Color(0xFF89958B),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )

@Composable
internal fun WatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WatchColors, content = content)
}

@Composable
internal fun WatchList(
    title: String? = null,
    timer: Boolean = false,
    resetKey: Any? = null,
    content: TransformingLazyColumnScope.() -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    LaunchedEffect(resetKey) { if (resetKey != null) state.scrollToItem(0) }
    ScreenScaffold(scrollState = state, timeText = if (timer) ({}) else null) { padding ->
        TransformingLazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            contentPadding =
                if (timer) PaddingValues(horizontal = 16.dp, vertical = 12.dp) else padding,
            verticalArrangement = Arrangement.spacedBy(if (timer) 2.dp else 8.dp),
        ) {
            if (title != null) item { WatchHeading(title) }
            content()
        }
    }
}

@Composable
internal fun WatchHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { heading() },
    )
}

@Composable
internal fun TransformingLazyColumnItemScope.WatchAction(
    label: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    action: () -> Unit,
) {
    val spec = rememberTransformationSpec()
    val modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).transformedHeight(this, spec)
    if (primary)
        Button(action, modifier, enabled = enabled, transformation = SurfaceTransformation(spec)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(label, textAlign = TextAlign.Center)
            }
        }
    else
        FilledTonalButton(
            action,
            modifier,
            enabled = enabled,
            transformation = SurfaceTransformation(spec),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(label, textAlign = TextAlign.Center)
            }
        }
}

@Composable
internal fun WatchText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
