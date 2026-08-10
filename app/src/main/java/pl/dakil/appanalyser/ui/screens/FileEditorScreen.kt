package pl.dakil.appanalyser.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.ui.components.filesystem.GoToLineDialog
import pl.dakil.appanalyser.ui.components.filesystem.UnsavedChangesDialog
import pl.dakil.appanalyser.ui.components.filesystem.applyMatchHighlights
import pl.dakil.appanalyser.ui.components.filesystem.highlight
import pl.dakil.appanalyser.ui.components.filesystem.languageForExtension
import pl.dakil.appanalyser.ui.components.flatTopAppBarColors
import pl.dakil.appanalyser.viewmodel.EditorState
import pl.dakil.appanalyser.viewmodel.FileEditorViewModel

/**
 * Basic text editor for files created or found in the File system tab.
 *
 * Uses the `TextFieldValue` flavour of [BasicTextField] on purpose: syntax highlighting and search
 * hits are span-level styling, which only [VisualTransformation] can apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(
    path: String,
    onNavigateBack: () -> Unit,
    viewModel: FileEditorViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val state by viewModel.state.collectAsState()
    val content by viewModel.content.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val monospace by viewModel.monospace.collectAsState()
    val wordWrap by viewModel.wordWrap.collectAsState()
    val syntaxHighlighting by viewModel.syntaxHighlighting.collectAsState()
    val highlightingSuppressed by viewModel.highlightingSuppressed.collectAsState()
    val findVisible by viewModel.findVisible.collectAsState()

    var showUnsaved by remember { mutableStateOf(false) }
    var showGoToLine by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val resources = LocalResources.current
    LaunchedEffect(path) { viewModel.load(path) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.saveAs(it) } }

    fun attemptBack() {
        if (isDirty) showUnsaved = true else onNavigateBack()
    }

    BackHandler { attemptBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isDirty) "$fileName •" else fileName,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (state is EditorState.Ready) {
                        IconButton(onClick = { viewModel.toggleFind() }) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.editor_find),
                            )
                        }
                        IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = stringResource(R.string.editor_undo),
                            )
                        }
                        IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Redo,
                                contentDescription = stringResource(R.string.editor_redo),
                            )
                        }
                        IconButton(onClick = { viewModel.save() }, enabled = isDirty) {
                            Icon(
                                Icons.Rounded.Save,
                                contentDescription = stringResource(R.string.editor_save),
                            )
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.files_more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_go_to_line)) },
                                onClick = {
                                    menuExpanded = false
                                    showGoToLine = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_editor_word_wrap)) },
                                onClick = { viewModel.setWordWrap(!wordWrap) },
                                trailingIcon = { CheckMark(wordWrap) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_editor_monospace)) },
                                onClick = { viewModel.setMonospace(!monospace) },
                                trailingIcon = { CheckMark(monospace) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_editor_highlighting)) },
                                onClick = { viewModel.setSyntaxHighlighting(!syntaxHighlighting) },
                                trailingIcon = { CheckMark(syntaxHighlighting) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_save_as)) },
                                onClick = {
                                    menuExpanded = false
                                    saveAsLauncher.launch(fileName)
                                },
                            )
                        }
                    }
                },
                colors = flatTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                EditorState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                EditorState.Binary -> EditorPlaceholder(stringResource(R.string.editor_binary))
                EditorState.Failed -> EditorPlaceholder(stringResource(R.string.editor_load_failed))
                is EditorState.TooLarge -> EditorPlaceholder(
                    message = stringResource(
                        R.string.editor_too_large,
                        Formatter.formatFileSize(context, current.sizeBytes),
                    ),
                    actionLabel = stringResource(R.string.editor_open_anyway),
                    onAction = { viewModel.load(path, force = true) },
                )
                EditorState.Ready -> {
                    AnimatedVisibility(visible = findVisible) {
                        FindBar(viewModel)
                    }
                    if (highlightingSuppressed && syntaxHighlighting) {
                        Text(
                            text = stringResource(R.string.editor_highlighting_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    EditorBody(
                        viewModel = viewModel,
                        fileName = fileName,
                        monospace = monospace,
                        wordWrap = wordWrap,
                        highlightingEnabled = syntaxHighlighting && !highlightingSuppressed,
                        modifier = Modifier.weight(1f),
                    )
                    EditorStatusBar(text = content.text, caret = content.selection.start)
                }
            }
        }
    }

    if (showUnsaved) {
        UnsavedChangesDialog(
            onSave = {
                showUnsaved = false
                viewModel.save()
                onNavigateBack()
            },
            onDiscard = {
                showUnsaved = false
                onNavigateBack()
            },
            onDismiss = { showUnsaved = false },
        )
    }

    if (showGoToLine) {
        GoToLineDialog(
            lineCount = content.text.count { it == '\n' } + 1,
            onConfirm = {
                viewModel.goToLine(it)
                showGoToLine = false
            },
            onDismiss = { showGoToLine = false },
        )
    }
}

@Composable
private fun CheckMark(checked: Boolean) {
    if (checked) Icon(Icons.Rounded.Check, contentDescription = null)
}

/** The text surface itself. */
@Composable
private fun EditorBody(
    viewModel: FileEditorViewModel,
    fileName: String,
    monospace: Boolean,
    wordWrap: Boolean,
    highlightingEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val content by viewModel.content.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val currentMatch by viewModel.currentMatch.collectAsState()
    val query by viewModel.findQuery.collectAsState()
    val colors = MaterialTheme.colorScheme

    val language = remember(fileName) {
        languageForExtension(fileName.substringAfterLast('.', ""))
    }
    // Rebuilt only when the text, language or theme actually change — never per recomposition.
    val highlighted = remember(content.text, language, highlightingEnabled, colors) {
        if (highlightingEnabled) {
            highlight(content.text, language, colors)
        } else {
            AnnotatedString(content.text)
        }
    }
    val activeMatchColor = colors.primary.copy(alpha = 0.45f)
    val inactiveMatchColor = colors.primary.copy(alpha = 0.18f)
    val transformation = remember(highlighted, matches, currentMatch, query) {
        VisualTransformation {
            TransformedText(
                applyMatchHighlights(
                    source = highlighted,
                    matches = matches,
                    queryLength = query.length,
                    currentMatch = currentMatch,
                    activeColor = activeMatchColor,
                    inactiveColor = inactiveMatchColor,
                ),
                OffsetMapping.Identity,
            )
        }
    }

    val textStyle = LocalTextStyle.current.copy(
        color = colors.onSurface,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .then(if (wordWrap) Modifier else Modifier.horizontalScroll(rememberScrollState())),
    ) {
        BasicTextField(
            value = content,
            onValueChange = { viewModel.onContentChange(it) },
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = transformation,
            // Off-wrap needs an unbounded line box, so let the field size to its content instead.
            modifier = if (wordWrap) {
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            } else {
                Modifier.padding(16.dp)
            },
        )
    }
}

@Composable
private fun FindBar(viewModel: FileEditorViewModel) {
    val query by viewModel.findQuery.collectAsState()
    val replaceWith by viewModel.replaceWith.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val currentMatch by viewModel.currentMatch.collectAsState()
    val matchCase by viewModel.matchCase.collectAsState()

    Surface(tonalElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setFindQuery(it) },
                    label = { Text(stringResource(R.string.editor_find)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (matches.isEmpty()) {
                        stringResource(R.string.editor_no_matches)
                    } else {
                        stringResource(R.string.editor_match_x_of_y, currentMatch + 1, matches.size)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { viewModel.toggleMatchCase() }) {
                    Icon(
                        Icons.Rounded.TextFields,
                        contentDescription = stringResource(R.string.editor_match_case),
                        tint = if (matchCase) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = { viewModel.findPrevious() }) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.editor_find_previous),
                    )
                }
                IconButton(onClick = { viewModel.findNext() }) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.editor_find_next),
                    )
                }
                IconButton(onClick = { viewModel.toggleFind() }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.files_cancel),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replaceWith,
                    onValueChange = { viewModel.setReplaceWith(it) },
                    label = { Text(stringResource(R.string.editor_replace)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { viewModel.replaceCurrent() },
                    enabled = matches.isNotEmpty(),
                ) { Text(stringResource(R.string.editor_replace)) }
                TextButton(
                    onClick = { viewModel.replaceAll() },
                    enabled = matches.isNotEmpty(),
                ) { Text(stringResource(R.string.editor_replace_all)) }
            }
        }
    }
}

/** `Ln 12, Col 4 · 340 lines · UTF-8` */
@Composable
private fun EditorStatusBar(text: String, caret: Int) {
    val safeCaret = caret.coerceIn(0, text.length)
    val line = text.substring(0, safeCaret).count { it == '\n' } + 1
    val column = safeCaret - (text.lastIndexOf('\n', safeCaret - 1) + 1) + 1
    val lineCount = text.count { it == '\n' } + 1

    HorizontalDivider()
    Text(
        text = stringResource(R.string.editor_status_x, line, column, lineCount),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun EditorPlaceholder(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
