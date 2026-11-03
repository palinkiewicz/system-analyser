package pl.dakil.appanalyser.domain

import java.io.File

/**
 * Where dropping [paths] onto [destination] would put them, or null when that drop is refused:
 * onto one of the dragged items, into a folder inside a dragged one (a copy would recurse forever),
 * or into the folder every item already lives in (a no-op dressed up as an action).
 *
 * Decided here rather than in the repository so a hovered target can be rejected before the finger
 * lifts, with the same rules the transfer itself would apply.
 */
fun dropDestination(paths: Collection<String>, destination: String): String? {
    if (paths.isEmpty()) return null
    val target = File(destination).absolutePath
    val sources = paths.map { File(it).absolutePath }
    if (sources.any { target == it || target.startsWith("$it${File.separator}") }) return null
    if (sources.all { File(it).parent == target }) return null
    return target
}
