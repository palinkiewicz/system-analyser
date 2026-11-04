package pl.dakil.appanalyser.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.dakil.appanalyser.ui.theme.LocalMotionPreferences

private const val STAGGER_MS = 45L
private const val MAX_STAGGERED = 6

/**
 * Whether the decorative motion plays: spins, shape morphs and cascades as content appears. Off
 * in Appearance, such places show their end state straight away. Screen transitions, press
 * feedback and values changing (a usage bar moving) never depend on it.
 */
val decorativeMotion: Boolean
    @Composable @ReadOnlyComposable get() = LocalMotionPreferences.current.decorative

/** [spec] for a decorative animation, or an instant jump when decorative motion is off. */
@Composable
@ReadOnlyComposable
fun <T> decorativeSpec(spec: AnimationSpec<T>): AnimationSpec<T> = if (decorativeMotion) spec else snap()

/**
 * Rises and fades an item in when it first composes, [index] steps after the previous one, so a
 * page of cards cascades in rather than popping. Draw-phase only: layout never moves. Decorative,
 * so nothing at all with decorative motion off.
 */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    if (!decorativeMotion) return this
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, MAX_STAGGERED) * STAGGER_MS)
        progress.animateTo(1f, spec)
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 40.dp.toPx()
    }
}
