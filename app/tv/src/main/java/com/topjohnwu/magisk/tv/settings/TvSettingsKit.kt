package com.topjohnwu.magisk.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.ui.TvAction
import com.topjohnwu.magisk.tv.ui.TvActionRow
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.TvListFocus
import com.topjohnwu.magisk.tv.ui.TvPageTitle
import com.topjohnwu.magisk.tv.ui.TvRadioRow
import com.topjohnwu.magisk.tv.ui.TvRow
import com.topjohnwu.magisk.tv.ui.TvSectionHeader
import com.topjohnwu.magisk.tv.ui.TvSwitchRow
import com.topjohnwu.magisk.tv.ui.tvFocusedContainer

/** One option of a [SettingsListScope.choice] row. */
internal data class SettingsOption(val label: String, val summary: String? = null)

/** What the right-hand pane shows for the focused row. */
internal data class SettingsPreview(
    val title: String,
    val description: String? = null,
    val options: List<SettingsOption> = emptyList(),
    val selectedIndex: Int = -1,
)

internal data class OpenChoice(
    val title: String,
    val description: String?,
    val options: List<SettingsOption>,
    val selectedIndex: Int,
    val onSelect: (Int) -> Unit,
)

internal data class OpenText(
    val title: String,
    val description: String?,
    val value: String,
    val placeholder: String?,
    val onDone: (String) -> Unit,
)

/**
 * Declarative rows of the settings list. Every row is focusable and focusing one shows its
 * explanation in the right pane, as in the Google TV system settings.
 */
internal class SettingsListScope(
    private val lazy: LazyListScope,
    private val onPreview: (SettingsPreview?) -> Unit,
    private val onOpenChoice: (OpenChoice) -> Unit,
    private val onOpenText: (OpenText) -> Unit,
    private val focus: TvListFocus,
) {
    fun header(text: String) {
        lazy.item(contentType = "header") { TvSectionHeader(text) }
    }

    /** A row that runs [onClick]. With [drill] it also opens with Right, like rows that lead to a page. */
    fun item(
        key: String,
        title: String,
        summary: String? = null,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        description: String? = summary,
        drill: Boolean = false,
        onClick: () -> Unit,
    ) {
        lazy.item(key = key, contentType = "row") {
            TvRow(
                title = title,
                summary = summary,
                icon = icon,
                enabled = enabled,
                modifier = focus.row(key).openOnRight(drill && enabled, onClick),
                onFocused = { onPreview(SettingsPreview(title, description)) },
                onClick = onClick,
            )
        }
    }

    fun switch(
        key: String,
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        summary: String? = null,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        description: String? = summary,
    ) {
        lazy.item(key = key, contentType = "switch") {
            TvSwitchRow(
                title = title,
                summary = summary,
                icon = icon,
                checked = checked,
                enabled = enabled,
                modifier = focus.row(key),
                onFocused = { onPreview(SettingsPreview(title, description)) },
                onCheckedChange = onCheckedChange,
            )
        }
    }

    /**
     * A row whose value is one of [options]. Focus previews the options on the right; OK or Right
     * opens them as a radio list that starts on the current value.
     */
    fun choice(
        key: String,
        title: String,
        options: List<SettingsOption>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        description: String? = null,
    ) {
        val open = { onOpenChoice(OpenChoice(title, description, options, selectedIndex, onSelect)) }
        lazy.item(key = key, contentType = "choice") {
            TvRow(
                title = title,
                summary = options.getOrNull(selectedIndex)?.label,
                icon = icon,
                enabled = enabled,
                modifier = focus.row(key).openOnRight(enabled, open),
                onFocused = { onPreview(SettingsPreview(title, description, options, selectedIndex)) },
                onClick = open,
            )
        }
    }

    /**
     * A text value. The row shows the current value; OK or Right opens a full-screen entry page, so
     * moving through the list never raises the keyboard.
     */
    fun text(
        key: String,
        title: String,
        value: String,
        onValueChange: (String) -> Unit,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        description: String? = null,
        summary: String? = value.ifBlank { null },
        placeholder: String? = null,
    ) {
        val open = { onOpenText(OpenText(title, description, value, placeholder, onValueChange)) }
        lazy.item(key = key, contentType = "text") {
            TvRow(
                title = title,
                summary = summary,
                icon = icon,
                enabled = enabled,
                modifier = focus.row(key).openOnRight(enabled, open),
                onFocused = { onPreview(SettingsPreview(title, description ?: summary)) },
                onClick = open,
            )
        }
    }
}

/** Two panes: the list of rows on the left, and a preview of the focused row on the right. */
@Composable
internal fun SettingsTwoPane(
    focus: TvListFocus,
    preview: SettingsPreview?,
    listModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = listModifier
                .weight(0.6f)
                .fillMaxHeight()
                .fadingTopEdge(),
            state = focus.listState,
            contentPadding = PaddingValues(
                start = TvKit.SafeHorizontal - 8.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = TvKit.SafeVertical + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
        PreviewPane(
            preview = preview,
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun PreviewPane(preview: SettingsPreview?, modifier: Modifier = Modifier) {
    // An inset card, drawn only when the focused row has something the list does not already say
    val hasContent = preview != null && (!preview.description.isNullOrBlank() || preview.options.isNotEmpty())
    Box(
        modifier = modifier
            .padding(top = 8.dp, bottom = TvKit.SafeVertical, end = TvKit.SafeHorizontal - 16.dp)
            .then(
                if (hasContent) {
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                } else {
                    Modifier
                },
            ),
    ) {
        if (preview == null || !hasContent) return@Box
        Column(Modifier.padding(horizontal = 32.dp, vertical = 28.dp)) {
            Text(
                text = preview.title,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (!preview.description.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = preview.description,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                )
            }
            if (preview.options.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                preview.options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.label,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            )
                            option.summary?.let {
                                Text(
                                    it,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == preview.selectedIndex) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** A list of mutually exclusive options. Opens focused on the current value, so OK never changes it silently. */
@Composable
internal fun SettingsChoicePage(choice: OpenChoice, onDone: () -> Unit) {
    val selectedRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedRequester.requestFocus() } }
    Row(
        Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                // Left steps back out, the mirror of Right opening it
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onDone()
                    true
                } else {
                    false
                }
            },
    ) {
        Column(
            Modifier
                .weight(0.44f)
                .padding(start = TvKit.SafeHorizontal + 12.dp, top = 24.dp, end = 24.dp),
        ) {
            TvPageTitle(choice.title)
            if (!choice.description.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    choice.description,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(0.56f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(start = 12.dp, end = TvKit.SafeHorizontal, top = 12.dp, bottom = TvKit.SafeVertical + 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            choice.options.forEachIndexed { index, option ->
                item {
                    TvRadioRow(
                        title = option.label,
                        summary = option.summary,
                        selected = index == choice.selectedIndex,
                        modifier = if (index == choice.selectedIndex.coerceAtLeast(0)) {
                            Modifier.focusRequester(selectedRequester)
                        } else {
                            Modifier
                        },
                        onClick = {
                            choice.onSelect(index)
                            onDone()
                        },
                    )
                }
            }
        }
    }
}

/** Full-screen text entry with Save and Cancel; Down leaves the field for the buttons. */
@Composable
internal fun SettingsTextPage(input: OpenText, onDone: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(input.value, TextRange(input.value.length))) }
    val fieldRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }
    val save = {
        input.onDone(text.text.trim())
        onDone()
    }
    LaunchedEffect(Unit) { runCatching { fieldRequester.requestFocus() } }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(0.44f)
                .padding(start = TvKit.SafeHorizontal + 12.dp, top = 24.dp, end = 24.dp),
        ) {
            TvPageTitle(input.title)
            if (!input.description.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    input.description,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            Modifier
                .weight(0.56f)
                .padding(start = 12.dp, end = TvKit.SafeHorizontal, top = 40.dp),
        ) {
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .border(
                        2.dp,
                        if (fieldFocused) tvFocusedContainer() else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        shape,
                    )
                    .padding(horizontal = 22.dp, vertical = 18.dp),
            ) {
                if (text.text.isEmpty() && !input.placeholder.isNullOrBlank()) {
                    Text(input.placeholder, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldRequester)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                focusManager.moveFocus(FocusDirection.Down)
                            } else {
                                false
                            }
                        },
                )
            }
            Spacer(Modifier.height(24.dp))
            TvActionRow(
                actions = listOf(
                    TvAction(stringResource(R.string.tv_set_save), onClick = save),
                    TvAction(stringResource(R.string.tv_set_cancel), onClick = onDone),
                ),
                requestInitialFocus = false,
            )
        }
    }
}

/** Opens the row with Right as well as OK, for rows that lead to a page. */
internal fun Modifier.openOnRight(enabled: Boolean, open: () -> Unit): Modifier = if (!enabled) {
    this
} else {
    onPreviewKeyEvent { event ->
        if (event.key == Key.DirectionRight) {
            if (event.type == KeyEventType.KeyDown) open()
            true
        } else {
            false
        }
    }
}

/** Rows scrolling up under the tab bar fade out instead of being cut off. */
private fun Modifier.fadingTopEdge(height: Dp = 24.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Transparent, height.toPx() / size.height to Color.Black),
                blendMode = BlendMode.DstIn,
            )
        }
