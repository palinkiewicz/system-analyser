package pl.dakil.appanalyser.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.ui.components.filesystem.GoToLineDialog
import pl.dakil.appanalyser.ui.components.filesystem.UnsavedChangesDialog
import pl.dakil.appanalyser.ui.components.filesystem.applyMatchHighlights
import pl.dakil.appanalyser.ui.components.filesystem.highlight
import pl.dakil.appanalyser.ui.components.filesystem.languageForExtension
import pl.dakil.appanalyser.ui.designsystem.AppMenu
import pl.dakil.appanalyser.ui.designsystem.AppMenuCheckItem
import pl.dakil.appanalyser.ui.designsystem.AppMenuGroup
import pl.dakil.appanalyser.ui.designsystem.AppMenuItem
import pl.dakil.appanalyser.ui.designsystem.EmptyState
import pl.dakil.appanalyser.ui.designsystem.appTopBarColors
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.ui.theme.tabular
import pl.dakil.appanalyser.viewmodel.EditorState
import pl.dakil.appanalyser.viewmodel.FileEditorViewModel


/**
 * Basic text editor for files created or found in the File system tab.
 *
 * Uses the `TextFieldValue` flavour of [BasicTextField] on purpose: syntax highlighting and search
 * hits are span-level styling, which only [VisualTransformation] can apply.
 */
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

    val haptics = rememberHaptics()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // The unsaved-changes dot pops in and out rather than appending a "•".
                        AnimatedVisibility(
                            visible = isDirty,
                            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                        ) {
                            Box(
                                Modifier
                                    .padding(start = Spacing.s)
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }, shapes = IconButtonDefaults.shapes()) {
                        AppIcon(AppSymbols.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state is EditorState.Ready) {
                        IconToggleButton(
                            checked = findVisible,
                            onCheckedChange = { viewModel.toggleFind() },
                            shapes = IconButtonDefaults.toggleableShapes(),
                        ) {
                            AppIcon(AppSymbols.Search, contentDescription = stringResource(R.string.editor_find))
                        }
                        IconButton(onClick = { viewModel.undo() }, enabled = canUndo, shapes = IconButtonDefaults.shapes()) {
                            AppIcon(AppSymbols.Undo, contentDescription = stringResource(R.string.editor_undo))
                        }
                        IconButton(onClick = { viewModel.redo() }, enabled = canRedo, shapes = IconButtonDefaults.shapes()) {
                            AppIcon(AppSymbols.Redo, contentDescription = stringResource(R.string.editor_redo))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                haptics.confirm()
                                viewModel.save()
                            },
                            enabled = isDirty,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            AppIcon(AppSymbols.Save, contentDescription = stringResource(R.string.editor_save))
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                                AppIcon(AppSymbols.MoreVert, contentDescription = stringResource(R.string.files_more_options))
                            }
                            AppMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                AppMenuGroup(index = 0, count = 3) {
                                    AppMenuItem(
                                        text = stringResource(R.string.editor_go_to_line),
                                        symbol = AppSymbols.FormatListNumbered,
                                        onClick = {
                                            menuExpanded = false
                                            showGoToLine = true
                                        },
                                    )
                                }
                                AppMenuGroup(index = 1, count = 3) {
                                    AppMenuCheckItem(
                                        text = stringResource(R.string.settings_editor_word_wrap),
                                        checked = wordWrap,
                                        onCheckedChange = { viewModel.setWordWrap(it) },
                                        symbol = AppSymbols.WrapText,
                                    )
                                    AppMenuCheckItem(
                                        text = stringResource(R.string.settings_editor_monospace),
                                        checked = monospace,
                                        onCheckedChange = { viewModel.setMonospace(it) },
                                        symbol = AppSymbols.TextFields,
                                    )
                                    AppMenuCheckItem(
                                        text = stringResource(R.string.settings_editor_highlighting),
                                        checked = syntaxHighlighting,
                                        onCheckedChange = { viewModel.setSyntaxHighlighting(it) },
                                        symbol = AppSymbols.DataObject,
                                    )
                                }
                                AppMenuGroup(index = 2, count = 3) {
                                    AppMenuItem(
                                        text = stringResource(R.string.editor_save_as),
                                        symbol = AppSymbols.Download,
                                        onClick = {
                                            menuExpanded = false
                                            saveAsLauncher.launch(fileName)
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = appTopBarColors(),
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
                    LoadingIndicator(Modifier.size(64.dp))
                }
                EditorState.Binary -> EditorPlaceholder(AppSymbols.Draft, stringResource(R.string.editor_binary))
                EditorState.Failed -> EditorPlaceholder(AppSymbols.Error, stringResource(R.string.editor_load_failed))
                is EditorState.TooLarge -> EditorPlaceholder(
                    symbol = AppSymbols.Description,
                    message = stringResource(
                        R.string.editor_too_large,
                        Formatter.formatFileSize(context, current.sizeBytes),
                    ),
                    actionLabel = stringResource(R.string.editor_open_anyway),
                    onAction = { viewModel.load(path, force = true) },
                )
                EditorState.Ready -> {
                    AnimatedVisibility(
                        visible = findVisible,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                    ) {
                        FindBar(viewModel)
                    }
                    if (highlightingSuppressed && syntaxHighlighting) {
                        Text(
                            text = stringResource(R.string.editor_highlighting_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs),
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
            .background(colors.surface)
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

/** A pill-shaped, borderless field for the find panel. */
@Composable
private fun PanelField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

@Composable
private fun FindBar(viewModel: FileEditorViewModel) {
    val query by viewModel.findQuery.collectAsState()
    val replaceWith by viewModel.replaceWith.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val currentMatch by viewModel.currentMatch.collectAsState()
    val matchCase by viewModel.matchCase.collectAsState()

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
    ) {
        Column(
            Modifier.padding(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelField(
                    value = query,
                    onValueChange = { viewModel.setFindQuery(it) },
                    placeholder = stringResource(R.string.editor_find),
                    modifier = Modifier.weight(1f),
                )
                AnimatedContent(targetState = matches.isEmpty() to currentMatch, label = "matchCount") { (none, index) ->
                    Text(
                        text = if (none) {
                            stringResource(R.string.editor_no_matches)
                        } else {
                            stringResource(R.string.editor_match_x_of_y, index + 1, matches.size)
                        },
                        style = MaterialTheme.typography.labelLarge.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.s),
                    )
                }
                IconToggleButton(
                    checked = matchCase,
                    onCheckedChange = { viewModel.toggleMatchCase() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    AppIcon(AppSymbols.MatchCase, contentDescription = stringResource(R.string.editor_match_case))
                }
                IconButton(onClick = { viewModel.findPrevious() }, shapes = IconButtonDefaults.shapes()) {
                    AppIcon(AppSymbols.KeyboardArrowUp, contentDescription = stringResource(R.string.editor_find_previous))
                }
                IconButton(onClick = { viewModel.findNext() }, shapes = IconButtonDefaults.shapes()) {
                    AppIcon(AppSymbols.KeyboardArrowDown, contentDescription = stringResource(R.string.editor_find_next))
                }
                IconButton(onClick = { viewModel.toggleFind() }, shapes = IconButtonDefaults.shapes()) {
                    AppIcon(AppSymbols.Close, contentDescription = stringResource(R.string.files_cancel))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelField(
                    value = replaceWith,
                    onValueChange = { viewModel.setReplaceWith(it) },
                    placeholder = stringResource(R.string.editor_replace),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { viewModel.replaceCurrent() },
                    enabled = matches.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.editor_replace)) }
                TextButton(
                    onClick = { viewModel.replaceAll() },
                    enabled = matches.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.editor_replace_all)) }
            }
        }
    }
}

/** `Ln 12, Col 4 · 340 lines`, as a small pill at the editor's bottom edge. */
@Composable
private fun EditorStatusBar(text: String, caret: Int) {
    val safeCaret = caret.coerceIn(0, text.length)
    val line = text.substring(0, safeCaret).count { it == '\n' } + 1
    val column = safeCaret - (text.lastIndexOf('\n', safeCaret - 1) + 1) + 1
    val lineCount = text.count { it == '\n' } + 1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = stringResource(R.string.editor_status_x, line, column, lineCount),
                style = MaterialTheme.typography.labelMedium.tabular(),
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun EditorPlaceholder(
    symbol: AppSymbol,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            symbol = symbol,
            title = message,
            polygon = MaterialShapes.Cookie9Sided,
            actions = if (actionLabel != null && onAction != null) {
                { Button(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(actionLabel) } }
            } else null,
        )
    }
}
