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

/**
 * Whether the transfer dialog may [operation] [paths] into [destination]. A move follows the drop
 * rules ([dropDestination]). A copy only refuses the recursive cases: copying into the folder the
 * items already live in is a legitimate duplicate, since the copy de-collides names ("x (1)").
 */
fun transferAllowed(paths: Collection<String>, destination: String, operation: ClipboardOperation): Boolean =
    when (operation) {
        ClipboardOperation.MOVE -> dropDestination(paths, destination) != null
        ClipboardOperation.COPY -> {
            val target = File(destination).absolutePath
            paths.isNotEmpty() && paths.map { File(it).absolutePath }
                .none { target == it || target.startsWith("$it${File.separator}") }
        }
    }
