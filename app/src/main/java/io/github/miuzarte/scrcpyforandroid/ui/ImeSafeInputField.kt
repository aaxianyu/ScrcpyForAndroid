// Copyright 2026, ScrcpyForAndroid contributors
// SPDX-License-Identifier: Apache-2.0

package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * IME 安全的单行搜索框。
 *
 * miuix `InputField` 只收 `String`，内部转 `TextFieldValue` 时 selection 归零：中文
 * 输入法组词态每帧被重置，出现「输入 123 变 231」这类乱序。这里改用
 * `BasicTextField(value: TextFieldValue)` 托管（`rememberSaveable` +
 * `TextFieldValue.Saver`，内部 state 持有 selection），与 `FileManagerScreen`
 * 的路径跳转框同款修法。
 *
 * 外观与 miuix `InputField` 对齐（胶囊背景色、左右图标槽、label 占位、光标色、
 * 字号字重均对齐 `SearchBarDefaults`）；[onValueChange] 只回调纯文本，调用方无感知；
 * 外部值变化（如清空）时同步 `TextFieldValue(value, TextRange(value.length))`，
 * 光标永远落在末尾；
 * [onValueChange] 里先更新本地 state 再回调，避免重组把旧文本写回。
 */
@Composable
fun ImeSafeInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    color: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    onSearch: (String) -> Unit = {},
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value))
    }

    // 外部改值（清空 / 重置）时同步进来，光标置尾。
    // 约定：外部只能整体清空，不能与用户输入并发改值——
    // 否则本地未提交的输入会被外部值直接覆盖
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnSearch by rememberUpdatedState(onSearch)

    val inputTextStyle = MiuixTheme.textStyles.main
        .copy(fontWeight = FontWeight.Medium)
        .merge(textStyle)
        .copy(color = LocalContentColor.current)

    val cursorBrush = SolidColor(MiuixTheme.colorScheme.primary)

    BasicTextField(
        value = textFieldValue,
        onValueChange = { updated ->
            textFieldValue = updated
            if (updated.text != value) currentOnValueChange(updated.text)
        },
        modifier = modifier
            .onFocusChanged { onFocusChanged?.invoke(it.isFocused) },
        enabled = enabled,
        singleLine = true,
        textStyle = inputTextStyle,
        cursorBrush = cursorBrush,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { currentOnSearch(textFieldValue.text) }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .background(
                        color = color,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左右图标绕一圈与容器边框保持一致的内距（同 SearchBar.InputField 默认值）
                    leadingIcon?.let { icon ->
                        Box(
                            modifier = Modifier.padding(
                                start = SearchBarDefaults.LeadingIconStartPadding,
                                end = SearchBarDefaults.LeadingIconEndPadding,
                            ),
                        ) { icon() }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = SearchBarDefaults.InputFieldMinHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = label,
                                style = remember(textStyle) {
                                    TextStyle(
                                        fontSize = SearchBarDefaults.InputFieldFontSize,
                                        fontWeight = FontWeight.Medium,
                                    ).merge(textStyle)
                                },
                                color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                            )
                        }
                        innerTextField()
                    }
                    trailingIcon?.let { icon ->
                        Box(
                            modifier = Modifier.padding(
                                start = SearchBarDefaults.TrailingIconStartPadding,
                                end = SearchBarDefaults.TrailingIconEndPadding,
                            ),
                        ) { icon() }
                    }
                }
            }
        },
    )
}
