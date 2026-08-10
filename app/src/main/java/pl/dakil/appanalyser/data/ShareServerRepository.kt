package pl.dakil.appanalyser.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.dakil.appanalyser.domain.ShareState

/**
 * In-process bridge between [pl.dakil.appanalyser.service.ShareServerService] and the UI.
 *
 * Deliberately **memory-only**, unlike the other repositories: a share session exposes files on the
 * network, so it must never be restored from disk after a restart. Nothing here touches the
 * service, which keeps a cold-started receiver safe to write to it.
 */
object ShareServerRepository {

    private val _state = MutableStateFlow<ShareState>(ShareState.Stopped)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    fun setRunning(running: ShareState.Running) {
        _state.value = running
    }

    fun setStopped() {
        _state.value = ShareState.Stopped
    }

    fun isRunning(): Boolean = _state.value is ShareState.Running
}
