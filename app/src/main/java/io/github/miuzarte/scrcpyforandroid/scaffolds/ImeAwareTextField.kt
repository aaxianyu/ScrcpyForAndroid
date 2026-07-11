package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

/**
 * 页面列表中使用的输入框组件，不跟随页面整体抬升，而是自动滚动到输入法上方。
 * 与对话框中的输入框区分，避免互相干扰。
 *
 * 实现原理：
 * - 当输入框获得焦点时，在组件底部添加等于 IME 高度的 padding
 * - 使 LazyColumn 可以滚动，将输入框顶到输入法上方可视区域
 * - 失去焦点时自动移除 padding
 * - 外层 LazyColumn 需要配合 .imePadding() 使用以预留滚动空间
 */
@Composable
fun ImeAwareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    useLabelAsPlaceholder: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onFocusGained: (() -> Unit)? = null,
    onFocusLost: (() -> Unit)? = null,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    textStyle: TextStyle = textStyles.main,
    cursorBrush: Brush = SolidColor(colorScheme.primary),
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current

    // 当输入框获得焦点时，在底部添加等于 IME 高度的 padding
    val imeBottom = WindowInsets.ime.getBottom(density)
    val bottomPadding = if (isFocused && imeBottom > 0) {
        with(density) { imeBottom.toDp() } + UiSpacing.Large
    } else {
        0.dp
    }

    Column(
        modifier = modifier
            .padding(bottom = bottomPadding)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    val wasFocused = isFocused
                    isFocused = focusState.isFocused
                    if (wasFocused && !focusState.isFocused) {
                        onFocusLost?.invoke()
                    } else if (!wasFocused && focusState.isFocused) {
                        onFocusGained?.invoke()
                    }
                }
                .focusRequester(focusRequester),
            label = label,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            insideMargin = insideMargin,
            textStyle = textStyle,
            cursorBrush = cursorBrush,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            interactionSource = interactionSource,
        )
    }
}
