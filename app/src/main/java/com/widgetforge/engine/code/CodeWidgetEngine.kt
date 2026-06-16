package com.widgetforge.engine.code

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.widgetforge.data.models.CodeWidgetManifest
import com.widgetforge.data.models.WidgetChannelMessage
import com.widgetforge.receiver.WidgetCommunicationBus
import com.widgetforge.receiver.WidgetVisibilityTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/**
 * CodeWidgetEngine — sandboxed WebView-based JavaScript runtime.
 *
 * Architecture:
 *  1. An off-screen WebView executes main.js in a file:// context so
 *     relative asset references resolve to the unzipped bundle directory.
 *  2. A JavascriptInterface bridges the rendered HTML5 Canvas frame buffer
 *     (base64-encoded PNG) into a native Android Bitmap.
 *  3. A Kotlin coroutine loop (not the JS loop itself) calls
 *     window.renderFrame() on each tick at the manifest-defined FPS.
 *  4. The loop immediately pauses when WidgetVisibilityTracker.isVisible
 *     is false, preventing unnecessary CPU/GPU/battery usage.
 */
class CodeWidgetEngine(
    private val context: Context,
    val appWidgetId: Int,
    private val bundleDir: File,
    val manifest: CodeWidgetManifest,
    private val onFrame: (Bitmap) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var webView: WebView? = null
    private var renderJob: Job? = null

    @Volatile private var running = false
    @Volatile private var paused = false

    // ── Channel state for cross-widget communication ────────────────────────
    private val channelState = mutableMapOf<String, String>()

    // ── Frame emission flow ─────────────────────────────────────────────────
    private val _frameFlow = MutableSharedFlow<Bitmap>(replay = 1)
    val frameFlow: SharedFlow<Bitmap> = _frameFlow

    // ── Bridge ──────────────────────────────────────────────────────────────

    inner class CanvasBridge {
        /**
         * Called from JavaScript: window.AndroidBridge.onFrameReady(base64Png)
         * Decodes the base64 image data into a Bitmap and delivers it to the
         * AppWidgetProvider for RemoteViews rendering.
         */
        @JavascriptInterface
        fun onFrameReady(base64Data: String) {
            try {
                val stripped = base64Data.removePrefix("data:image/png;base64,")
                val bytes = Base64.decode(stripped, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                scope.launch {
                    _frameFlow.emit(bmp)
                    onFrame(bmp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame decode error: ${e.message}")
            }
        }

        /**
         * Publish a message to a named cross-widget channel.
         * Called from JS: window.AndroidBridge.publish(channel, jsonPayload)
         */
        @JavascriptInterface
        fun publish(channel: String, payload: String) {
            WidgetCommunicationBus.publish(context, appWidgetId, channel, payload)
        }

        /**
         * Read current channel state.
         * Called from JS: window.AndroidBridge.getChannelState(channel)
         */
        @JavascriptInterface
        fun getChannelState(channel: String): String {
            return channelState[channel] ?: "{}"
        }

        /**
         * Log from JavaScript context.
         */
        @JavascriptInterface
        fun log(message: String) {
            Log.d("Widget[$appWidgetId]JS", message)
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        if (running) return
        running = true

        scope.launch(Dispatchers.Main) {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    domStorageEnabled = true
                    // Off-screen: no display attachment needed
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        Log.d(TAG, "Widget[$appWidgetId] page loaded; injecting bridge")
                        injectBridgeAndStart()
                        startRenderLoop()
                    }
                }
                addJavascriptInterface(CanvasBridge(), "AndroidBridge")
                // Load from unzipped bundle directory so relative paths work
                val indexHtml = buildHtmlShell()
                val baseUrl = "file://${bundleDir.absolutePath}/"
                loadDataWithBaseURL(baseUrl, indexHtml, "text/html", "UTF-8", null)
            }
        }
    }

    fun pause() {
        paused = true
        scope.launch(Dispatchers.Main) {
            webView?.evaluateJavascript("if(window.onWidgetPause) window.onWidgetPause();", null)
        }
    }

    fun resume() {
        paused = false
        scope.launch(Dispatchers.Main) {
            webView?.evaluateJavascript("if(window.onWidgetResume) window.onWidgetResume();", null)
        }
    }

    fun stop() {
        running = false
        renderJob?.cancel()
        scope.launch(Dispatchers.Main) {
            webView?.destroy()
            webView = null
        }
        scope.cancel()
    }

    fun deliverChannelMessage(message: WidgetChannelMessage) {
        // Only deliver if subscribed
        val subscribed = manifest.channels.any { it.name == message.channel }
        if (!subscribed) return

        channelState[message.channel] = message.payload
        val js = """
            if(window.__channels) {
                window.__channels['${message.channel}'] = ${message.payload};
                if(window.onChannelMessage) {
                    window.onChannelMessage('${message.channel}', ${message.payload});
                }
            }
        """.trimIndent()
        scope.launch(Dispatchers.Main) {
            webView?.evaluateJavascript(js, null)
        }
    }

    fun updateDimensions(widthPx: Int, heightPx: Int) {
        val js = "if(window.onResize) window.onResize($widthPx, $heightPx);"
        scope.launch(Dispatchers.Main) {
            webView?.evaluateJavascript(js, null)
        }
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private fun injectBridgeAndStart() {
        val channelInit = manifest.channels.joinToString(",\n") { ch ->
            "'${ch.name}': ${ch.initialState}"
        }
        val initJs = """
            window.__channels = { $channelInit };
            window.__widgetId = $appWidgetId;
            window.__fps = ${manifest.fps};
        """.trimIndent()
        webView?.evaluateJavascript(initJs, null)
    }

    private fun startRenderLoop() {
        val frameIntervalMs = (1000L / manifest.fps.coerceIn(1, 60))
        renderJob = scope.launch {
            while (running) {
                if (!paused && WidgetVisibilityTracker.isVisible) {
                    webView?.evaluateJavascript(
                        """
                        (function() {
                            try {
                                if (typeof renderFrame === 'function') {
                                    renderFrame();
                                } else if (window.__canvas) {
                                    var data = window.__canvas.toDataURL('image/png');
                                    AndroidBridge.onFrameReady(data);
                                }
                            } catch(e) { AndroidBridge.log('renderFrame error: ' + e.message); }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
                delay(frameIntervalMs)
            }
        }
    }

    /**
     * Build the HTML host page that loads main.js from the bundle directory.
     * The base URL is set to file://bundleDir/ so relative asset paths resolve correctly.
     */
    private fun buildHtmlShell(): String {
        val mainJsContent = try {
            File(bundleDir, "main.js").readText()
        } catch (e: Exception) {
            "// main.js not found"
        }

        val cellW = manifest.cellWidth
        val cellH = manifest.cellHeight
        // Approximate dp size per cell (standard Android homescreen cell ≈ 74dp)
        val canvasW = cellW * 74
        val canvasH = cellH * 74

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
* { margin:0; padding:0; box-sizing:border-box; }
body { background:transparent; overflow:hidden; }
canvas { display:block; }
</style>
</head>
<body>
<canvas id="widgetCanvas" width="$canvasW" height="$canvasH"></canvas>
<script>
// ── Standard Canvas Context Setup ──────────────────────────────
window.__canvas = document.getElementById('widgetCanvas');
var ctx = window.__canvas.getContext('2d');
var WIDTH = window.__canvas.width;
var HEIGHT = window.__canvas.height;

// ── renderFrame hook: user's main.js calls draw(), we capture to base64 ──
window.renderFrame = function() {
    if (typeof draw === 'function') {
        ctx.clearRect(0, 0, WIDTH, HEIGHT);
        draw(ctx, WIDTH, HEIGHT);
    }
    var data = window.__canvas.toDataURL('image/png');
    AndroidBridge.onFrameReady(data);
};

// ── Resize handler ─────────────────────────────────────────────
window.onResize = function(w, h) {
    WIDTH = w; HEIGHT = h;
    window.__canvas.width = w;
    window.__canvas.height = h;
    ctx = window.__canvas.getContext('2d');
};

// ── User script ────────────────────────────────────────────────
$mainJsContent
</script>
</body>
</html>
        """.trimIndent()
    }

    companion object {
        private const val TAG = "CodeWidgetEngine"
    }
}
