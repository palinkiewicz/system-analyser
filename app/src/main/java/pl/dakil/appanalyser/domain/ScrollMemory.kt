package pl.dakil.appanalyser.domain

/** Where a folder's list was scrolled to: its first visible row and how far into it. */
data class ScrollPosition(val index: Int, val offset: Int) {
    companion object {
        val Top = ScrollPosition(0, 0)
    }
}

/**
 * The scroll positions a pane comes back to. Only the folders on the way to the current one (and
 * the current one itself) are remembered, which gives a file manager's natural behaviour:
 * going **up**, by the up button, system back or a breadcrumb, returns to where the parent was left,
 * while going **down** into a folder always starts at its top, even one visited before.
 */
class ScrollMemory {
    private val positions = mutableMapOf<String, ScrollPosition>()

    fun positionFor(path: String): ScrollPosition = positions[path] ?: ScrollPosition.Top

    /**
     * Stores [position] for [path] while the pane shows [currentPath]. A report from a folder the
     * pane has since left sideways or upwards is ignored; it must not resurrect that folder.
     */
    fun record(path: String, position: ScrollPosition, currentPath: String?) {
        if (currentPath != null && path.isAncestorOrSelfOf(currentPath)) positions[path] = position
    }

    /** Forgets everything that isn't on the way to [currentPath]; called on every navigation. */
    fun trimTo(currentPath: String?) {
        positions.keys.retainAll { currentPath != null && it.isAncestorOrSelfOf(currentPath) }
    }

    private fun String.isAncestorOrSelfOf(path: String) =
        path == this || path.startsWith(if (endsWith('/')) this else "$this/")
}
