package pl.dakil.appanalyser.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.data.ProgressStyle
import pl.dakil.appanalyser.ui.theme.LocalMotionPreferences

/**
 * A usage meter: an expressive wavy bar that springs to each new [fraction] instead of jumping.
 * The wave stays still (amplitude only, no travel), so a live value doesn't animate continuously.
 * With the straight progress style chosen in Appearance it is a flat bar, still springing.
 */
@Composable
fun UsageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "usage",
    )
    if (LocalMotionPreferences.current.progressStyle == ProgressStyle.STRAIGHT) {
        LinearProgressIndicator(
            progress = { animated },
            modifier = modifier.fillMaxWidth(),
            color = color,
            trackColor = trackColor,
        )
        return
    }
    LinearWavyProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth(),
        color = color,
        trackColor = trackColor,
        amplitude = { WavyProgressIndicatorDefaults.indicatorAmplitude(it) },
        wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
        waveSpeed = 0.dp,
    )
}

/**
 * A linear progress bar in the style chosen in Appearance, wavy or straight: determinate with a
 * [progress], indeterminate without one.
 */
@Composable
fun AppLinearProgress(modifier: Modifier = Modifier, progress: (() -> Float)? = null) {
    val straight = LocalMotionPreferences.current.progressStyle == ProgressStyle.STRAIGHT
    when {
        straight && progress != null -> LinearProgressIndicator(progress = progress, modifier = modifier)
        straight -> LinearProgressIndicator(modifier = modifier)
        progress != null -> LinearWavyProgressIndicator(progress = progress, modifier = modifier)
        else -> LinearWavyProgressIndicator(modifier = modifier)
    }
}
