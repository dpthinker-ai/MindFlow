package com.mindflow.app.data.skills

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

private const val JS_RESULT_BRIDGE_NAME = "MindFlowSkillRuntime"
private const val NATIVE_TOOL_BRIDGE_NAME = "MindFlowNativeBridge"
private const val JS_INPUT_BRIDGE_NAME = "MindFlowSkillInput"
private const val MAX_DIRECT_INVOCATION_DATA_CHARS = 1_000_000

class WebViewJsSkillExecutor(
    private val context: Context,
    private val nativeToolBridge: NativeToolBridge,
    private val timeoutMs: Long = 60_000L,
) : JsSkillExecutor {
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun execute(request: SkillExecutionRequest): SkillResult = withTimeout(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                executeOnMainThread(request) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun executeOnMainThread(
        request: SkillExecutionRequest,
        complete: (SkillResult) -> Unit,
    ) {
        var finished = false
        lateinit var webView: WebView

        fun finish(result: SkillResult) {
            if (finished) return
            finished = true
            webView.removeJavascriptInterface(JS_RESULT_BRIDGE_NAME)
            webView.removeJavascriptInterface(NATIVE_TOOL_BRIDGE_NAME)
            webView.removeJavascriptInterface(JS_INPUT_BRIDGE_NAME)
            webView.stopLoading()
            webView.destroy()
            complete(result)
        }

        if (request.invocation.data.length > MAX_DIRECT_INVOCATION_DATA_CHARS) {
            complete(
                SkillResult.failure(
                    "JS skill input is too large: ${request.invocation.data.length} chars",
                ),
            )
            return
        }

        val resultBridge = object {
            @JavascriptInterface
            fun onResult(raw: String?) {
                finish(
                    raw?.let { SkillResultJsonParser.parse(request.skill, it) }
                        ?: SkillResult.failure("JS skill returned empty result for ${request.skill.manifest.id}"),
                )
            }
        }

        val nativeBridge = object {
            @JavascriptInterface
            fun invoke(apiName: String?, payloadJson: String?): String {
                val api = apiName.orEmpty().trim()
                if (api.isBlank()) return """{"error":"missing apiName"}"""
                if (api !in request.skill.manifest.nativeApis || !nativeToolBridge.canInvoke(api)) {
                    return """{"error":"api not allowed: $api"}"""
                }
                return runCatching {
                    runBlocking {
                        nativeToolBridge.invoke(api, payloadJson.orEmpty().ifBlank { "{}" })
                    }
                }.getOrElse { throwable ->
                    """{"error":${(throwable.message ?: "bridge invocation failed").asJavascriptString()}}"""
                }
            }
        }

        val inputBridge = object {
            @JavascriptInterface
            fun getInvocationData(): String = request.invocation.data

            @JavascriptInterface
            fun getSecret(): String = request.secret
        }

        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(resultBridge, JS_RESULT_BRIDGE_NAME)
            addJavascriptInterface(nativeBridge, NATIVE_TOOL_BRIDGE_NAME)
            addJavascriptInterface(inputBridge, JS_INPUT_BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (finished) return
                    view.evaluateJavascript(WebViewSkillExecutionScript.build(), null)
                }

                override fun onReceivedError(
                    view: WebView?,
                    requestInfo: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (requestInfo?.isForMainFrame != false) {
                        finish(
                            SkillResult.failure(
                                error?.description?.toString()
                                    ?: "Failed to load JS skill page for ${request.skill.manifest.id}",
                            ),
                        )
                    }
                }
            }
            loadUrl(request.entryAssetUrl())
        }
    }

    private fun SkillExecutionRequest.entryAssetUrl(): String =
        "file:///android_asset/${skill.assetBasePath}/${skill.manifest.entry}"
}

internal object WebViewSkillExecutionScript {
    fun build(): String = """
        (function() {
          if (!window['ai_edge_gallery_get_result']) {
            $JS_RESULT_BRIDGE_NAME.onResult(JSON.stringify({ error: 'missing ai_edge_gallery_get_result' }));
            return;
          }
          if (!window.$JS_INPUT_BRIDGE_NAME ||
              typeof window.$JS_INPUT_BRIDGE_NAME.getInvocationData !== 'function') {
            $JS_RESULT_BRIDGE_NAME.onResult(JSON.stringify({ error: 'missing skill input bridge' }));
            return;
          }
          (async function() {
            try {
              const data = String(window.$JS_INPUT_BRIDGE_NAME.getInvocationData() || '{}');
              const secret = String(window.$JS_INPUT_BRIDGE_NAME.getSecret ? window.$JS_INPUT_BRIDGE_NAME.getSecret() : '');
              const raw = await window['ai_edge_gallery_get_result'](data, secret);
              $JS_RESULT_BRIDGE_NAME.onResult(String(raw));
            } catch (error) {
              const message = String(error && error.message ? error.message : error);
              $JS_RESULT_BRIDGE_NAME.onResult(JSON.stringify({ error: message }));
            }
          })();
        })();
    """.trimIndent()
}

private fun String.asJavascriptString(): String = buildString(length + 2) {
    append('"')
    forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}
