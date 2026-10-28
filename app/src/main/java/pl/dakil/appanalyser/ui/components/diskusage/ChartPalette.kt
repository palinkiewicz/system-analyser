package pl.dakil.appanalyser.ui.components.diskusage

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Categorical colours for the disk usage charts, derived from the active colour scheme rather than
 * hardcoded: the primary's hue is rotated by the golden angle so neighbouring folders never get
 * similar hues, with saturation and lightness clamped into a band that reads on the theme's surface.
 * Every theme preset — and Material You — therefore gets a palette that belongs to it.
 */
@Immutable
class ChartPalette(
    private val hues: List<Color>,
    /** The "files in this folder" slices. */
    val files: Color,
    /** The "Restricted by Android" entry: a darker neutral, distinct from loose files. */
    val restricted: Color,
    /** What deeper rings and nested tiles fade toward. */
    private val surface: Color,
    /** Readable text on a palette colour. */
    val onLight: Color,
    val onDark: Color,
) {
    /** Colour for a folder whose top-level ancestor (under the focus) is [index], [depth] rings out. */
    fun color(index: Int, depth: Int = 1): Color {
        val base = if (index < 0) files else hues[index % hues.size]
        return lerp(base, surface, ((depth - 1) * DEPTH_FADE).coerceAtMost(MAX_FADE))
    }

    /**
     * Loose files inside a folder coloured [index]: the folder's hue washed toward the surface, so
     * they read as part of that folder rather than as a separate grey block.
     */
    fun filesColor(index: Int, depth: Int): Color =
        if (index < 0) color(-1, depth) else lerp(color(index, depth), surface, FILES_FADE)

    /** Whichever text colour has the higher WCAG contrast ratio against [background]. */
    fun contentColorFor(background: Color): Color {
        val bg = background.luminance()
        fun contrast(text: Color): Float {
            val fg = text.luminance()
            return (maxOf(bg, fg) + 0.05f) / (minOf(bg, fg) + 0.05f)
        }
        return if (contrast(onLight) >= contrast(onDark)) onLight else onDark
    }

    companion object {
        private const val HUE_COUNT = 8
        private const val GOLDEN_ANGLE = 137.508f
        private const val DEPTH_FADE = 0.17f
        private const val MAX_FADE = 0.6f
        private const val FILES_FADE = 0.55f

        fun from(scheme: ColorScheme): ChartPalette {
            val dark = scheme.surface.luminance() < 0.5f
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(scheme.primary.toArgb(), hsl)
            // Dynamic and pure-black schemes can have a nearly grey primary; never go below this.
            // Kept moderate: fully saturated hues read as a rainbow, not as Material colour.
            val saturation = hsl[1].coerceIn(0.35f, 0.55f)
            val lightness = if (dark) 0.7f else 0.56f
            val hues = List(HUE_COUNT) { i ->
                val hue = (hsl[0] + i * GOLDEN_ANGLE) % 360f
                Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))
            }
            return ChartPalette(
                hues = hues,
                files = scheme.outlineVariant,
                restricted = scheme.outline,
                surface = scheme.surface,
                // Whichever of surface/onSurface is the dark one goes on light tiles, and vice versa.
                onLight = if (dark) scheme.surface else scheme.onSurface,
                onDark = if (dark) scheme.onSurface else scheme.surface,
            )
        }
    }
}

@Composable
fun rememberChartPalette(): ChartPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { ChartPalette.from(scheme) }
}
