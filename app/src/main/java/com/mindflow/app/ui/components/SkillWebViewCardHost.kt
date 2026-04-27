package com.mindflow.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.WhiteGlass
import kotlinx.coroutines.launch

/**
 * Native only owns the WebView shell. Skill-specific card layout must live in JS/HTML assets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillWebViewCardHost(
    url: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1.333f,
    expandLabel: String = "展开卡片",
) {
    val safeAspectRatio = aspectRatio.takeIf { it in 0.5f..2.5f } ?: 1.333f
    var showFullScreen by remember(url) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = WhiteGlass.copy(alpha = 0.96f),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, BorderSoft),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SkillWebView(
                url = url,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 360.dp)
                    .aspectRatio(safeAspectRatio),
            )
        }
        AssistChip(
            onClick = { showFullScreen = true },
            label = { Text(expandLabel) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Fullscreen,
                    contentDescription = null,
                )
            },
        )
    }

    if (showFullScreen) {
        ModalBottomSheet(
            onDismissRequest = { showFullScreen = false },
            sheetState = sheetState,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                SkillWebView(
                    url = url,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            showFullScreen = false
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭卡片",
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SkillWebView(
    url: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setBackgroundColor(Color.TRANSPARENT)
                setOnTouchListener { view, _ ->
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.url != url) {
                view.loadUrl(url)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
    )
}
