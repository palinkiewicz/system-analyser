package pl.dakil.appanalyser.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.FileSystemRepository
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.TextLoadResult
import java.io.File

/** What the editor is currently showing instead of (or as) the text. */
sealed interface EditorState {
    data object Loading : EditorState
    data object Ready : EditorState
    data class TooLarge(val sizeBytes: Long) : EditorState
    data object Binary : EditorState
    data object Failed : EditorState
}

class FileEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileSystemRepository(application)
    private val settings = SettingsRepository.get(application)

    private var path: String? = null

    private val _state = MutableStateFlow<EditorState>(EditorState.Loading)
    val state: StateFlow<EditorState> = _state

    private val _content = MutableStateFlow(TextFieldValue(""))
    val content: StateFlow<TextFieldValue> = _content

    private val _fileName = MutableStateFlow("")
    val fileName: StateFlow<String> = _fileName

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    /** True once the file is past the highlighting budget, so the UI can explain the plain text. */
    private val _highlightingSuppressed = MutableStateFlow(false)
    val highlightingSuppressed: StateFlow<Boolean> = _highlightingSuppressed

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages

    val monospace: StateFlow<Boolean> = settings.editorMonospace
    val wordWrap: StateFlow<Boolean> = settings.editorWordWrap
    val syntaxHighlighting: StateFlow<Boolean> = settings.editorSyntaxHighlighting

    // --- Find & replace -----------------------------------------------------

    private val _findVisible = MutableStateFlow(false)
    val findVisible: StateFlow<Boolean> = _findVisible

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery

    private val _replaceWith = MutableStateFlow("")
    val replaceWith: StateFlow<String> = _replaceWith

    private val _matchCase = MutableStateFlow(false)
    val matchCase: StateFlow<Boolean> = _matchCase

    /** Start offsets of every match, recomputed off the main thread on a debounce. */
    private val _matches = MutableStateFlow<List<Int>>(emptyList())
    val matches: StateFlow<List<Int>> = _matches

    private val _currentMatch = MutableStateFlow(-1)
    val currentMatch: StateFlow<Int> = _currentMatch

    private var searchJob: Job? = null
    private var undoJob: Job? = null

    // Bounded history so a long editing session can't grow without limit.
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var lastCommitted: TextFieldValue = TextFieldValue("")

    // -----------------------------------------------------------------------
    // Loading & saving
    // -----------------------------------------------------------------------

    fun load(filePath: String, force: Boolean = false) {
        if (path == filePath && _state.value !is EditorState.Loading && !force) return
        path = filePath
        _fileName.value = File(filePath).name
        viewModelScope.launch {
            _state.value = EditorState.Loading
            val limit = if (force) FileSystemRepository.MAX_FORCED_BYTES
            else FileSystemRepository.MAX_EDITABLE_BYTES
            when (val result = repository.readText(filePath, limit)) {
                is TextLoadResult.Success -> {
                    val value = TextFieldValue(result.text)
                    _content.value = value
                    lastCommitted = value
                    undoStack.clear()
                    redoStack.clear()
                    updateUndoFlags()
                    _isDirty.value = false
                    _highlightingSuppressed.value = result.text.length > MAX_HIGHLIGHT_CHARS
                    _state.value = EditorState.Ready
                }
                is TextLoadResult.TooLarge -> _state.value = EditorState.TooLarge(result.sizeBytes)
                TextLoadResult.Binary -> _state.value = EditorState.Binary
                TextLoadResult.Failed -> _state.value = EditorState.Failed
            }
        }
    }

    fun save() {
        val filePath = path ?: return
        viewModelScope.launch {
            val ok = repository.writeText(filePath, _content.value.text)
            if (ok) _isDirty.value = false
            _messages.tryEmit(if (ok) R.string.editor_saved else R.string.editor_save_failed)
        }
    }

    fun saveAs(destination: Uri) {
        viewModelScope.launch {
            val ok = repository.writeTextTo(destination, _content.value.text)
            _messages.tryEmit(if (ok) R.string.editor_saved else R.string.editor_save_failed)
        }
    }

    // -----------------------------------------------------------------------
    // Editing & history
    // -----------------------------------------------------------------------

    fun onContentChange(value: TextFieldValue) {
        val textChanged = value.text != _content.value.text
        _content.value = value
        if (!textChanged) return

        _isDirty.value = true
        redoStack.clear()
        // Coalesce a burst of typing into one history entry instead of one per keystroke.
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            delay(UNDO_DEBOUNCE_MS)
            commitHistory()
        }
        updateUndoFlags()
        scheduleSearch()
    }

    private fun commitHistory() {
        if (lastCommitted.text == _content.value.text) return
        undoStack.addLast(lastCommitted)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        lastCommitted = _content.value
        updateUndoFlags()
    }

    fun undo() {
        undoJob?.cancel()
        commitHistory()
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_content.value)
        _content.value = previous
        lastCommitted = previous
        _isDirty.value = true
        updateUndoFlags()
        scheduleSearch()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_content.value)
        _content.value = next
        lastCommitted = next
        _isDirty.value = true
        updateUndoFlags()
        scheduleSearch()
    }

    private fun updateUndoFlags() {
        _canUndo.value = undoStack.isNotEmpty() || lastCommitted.text != _content.value.text
        _canRedo.value = redoStack.isNotEmpty()
    }

    // -----------------------------------------------------------------------
    // Find & replace
    // -----------------------------------------------------------------------

    fun toggleFind() {
        _findVisible.value = !_findVisible.value
        if (!_findVisible.value) {
            _matches.value = emptyList()
            _currentMatch.value = -1
        } else {
            scheduleSearch()
        }
    }

    fun setFindQuery(query: String) {
        _findQuery.value = query
        scheduleSearch()
    }

    fun setReplaceWith(text: String) {
        _replaceWith.value = text
    }

    fun toggleMatchCase() {
        _matchCase.value = !_matchCase.value
        scheduleSearch()
    }

    private fun scheduleSearch() {
        if (!_findVisible.value) return
        searchJob?.cancel()
        val query = _findQuery.value
        if (query.isEmpty()) {
            _matches.value = emptyList()
            _currentMatch.value = -1
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val text = _content.value.text
            val ignoreCase = !_matchCase.value
            val found = withContext(Dispatchers.Default) {
                val result = mutableListOf<Int>()
                var index = text.indexOf(query, 0, ignoreCase)
                while (index >= 0) {
                    result += index
                    index = text.indexOf(query, index + query.length, ignoreCase)
                }
                result
            }
            _matches.value = found
            _currentMatch.value = if (found.isEmpty()) -1 else _currentMatch.value.coerceIn(0, found.size - 1)
            if (found.isNotEmpty() && _currentMatch.value < 0) _currentMatch.value = 0
        }
    }

    fun findNext() = stepMatch(1)

    fun findPrevious() = stepMatch(-1)

    /** Wraps around at both ends and moves the selection onto the match so it scrolls into view. */
    private fun stepMatch(direction: Int) {
        val found = _matches.value
        if (found.isEmpty()) return
        val next = ((_currentMatch.value + direction) % found.size + found.size) % found.size
        _currentMatch.value = next
        val start = found[next]
        _content.value = _content.value.copy(
            selection = TextRange(start, start + _findQuery.value.length)
        )
    }

    fun replaceCurrent() {
        val found = _matches.value
        val index = _currentMatch.value
        val query = _findQuery.value
        if (found.isEmpty() || index !in found.indices || query.isEmpty()) return
        val start = found[index]
        val text = _content.value.text
        if (start + query.length > text.length) return
        val updated = text.replaceRange(start, start + query.length, _replaceWith.value)
        commitHistory()
        _content.value = TextFieldValue(
            text = updated,
            selection = TextRange(start + _replaceWith.value.length),
        )
        _isDirty.value = true
        commitHistory()
        scheduleSearch()
    }

    fun replaceAll() {
        val query = _findQuery.value
        if (query.isEmpty()) return
        val text = _content.value.text
        val updated = text.replace(query, _replaceWith.value, ignoreCase = !_matchCase.value)
        if (updated == text) return
        commitHistory()
        _content.value = TextFieldValue(updated)
        _isDirty.value = true
        commitHistory()
        scheduleSearch()
        _messages.tryEmit(R.string.editor_replaced_all)
    }

    // -----------------------------------------------------------------------
    // Go to line
    // -----------------------------------------------------------------------

    /** Moves the caret to the start of [line] (1-based), clamped to the document. */
    fun goToLine(line: Int) {
        val text = _content.value.text
        var offset = 0
        var remaining = (line - 1).coerceAtLeast(0)
        while (remaining > 0) {
            val next = text.indexOf('\n', offset)
            if (next < 0) break
            offset = next + 1
            remaining--
        }
        _content.value = _content.value.copy(selection = TextRange(offset.coerceAtMost(text.length)))
    }

    fun setMonospace(enabled: Boolean) = settings.setEditorMonospace(enabled)
    fun setWordWrap(enabled: Boolean) = settings.setEditorWordWrap(enabled)
    fun setSyntaxHighlighting(enabled: Boolean) = settings.setEditorSyntaxHighlighting(enabled)

    companion object {
        /** Above this the regex highlighter is skipped — it would cost too much per keystroke. */
        const val MAX_HIGHLIGHT_CHARS = 128 * 1024

        private const val MAX_HISTORY = 50
        private const val UNDO_DEBOUNCE_MS = 500L
        private const val SEARCH_DEBOUNCE_MS = 200L
    }
}
