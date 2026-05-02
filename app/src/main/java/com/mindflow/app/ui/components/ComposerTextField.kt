package com.mindflow.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComposerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 3,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val keyboardOptions = when (imeAction) {
        ImeAction.Default, ImeAction.None -> KeyboardOptions.Default
        else -> KeyboardOptions.Default.copy(imeAction = imeAction)
    }
    val keyboardActions = if (onImeAction == null || imeAction == ImeAction.Default || imeAction == ImeAction.None) {
        KeyboardActions()
    } else {
        KeyboardActions(
            onDone = { onImeAction.invoke() },
            onSend = { onImeAction.invoke() },
        )
    }

    Surface(
        modifier = modifier,
        color = WhiteGlass,
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft),
        shadowElevation = 3.dp,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                },
            minLines = minLines,
            maxLines = maxLines,
            textStyle = textStyle,
            placeholder = {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = TextSoft,
                )
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = WhiteGlass,
                unfocusedContainerColor = WhiteGlass,
                disabledContainerColor = WhiteGlass,
                focusedIndicatorColor = Accent.copy(alpha = 0.18f),
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}
