package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

enum class JarvisVisualizerMode {
    ARC_HUD,    // Mark 85 Arc Core Holographic HUD
    PARTICLE_ORB // 3D Floating Particle Orb
}

enum class JarvisVisualizerState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

/**
 * High-performance 3D Live JARVIS Visualizer Composable.
 * Renders the authentic 3D Mark 85 Arc Core HUD or 3D Particle Orb with live native JavaScript bridging,
 * interactive touch rotation, dynamic state reactive animations, and sound spectrum.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Jarvis3dLiveVisualizer(
    mode: JarvisVisualizerMode = JarvisVisualizerMode.PARTICLE_ORB,
    state: JarvisVisualizerState = JarvisVisualizerState.IDLE,
    audioAmplitude: Float = 0.0f,
    onOrbTap: () -> Unit = {},
    onMicTap: () -> Unit = {},
    onScreenShareTap: () -> Unit = {},
    onSettingsTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val assetUrl = remember(mode) {
        if (mode == JarvisVisualizerMode.ARC_HUD) {
            "file:///android_asset/hud/index.html"
        } else {
            "file:///android_asset/orb.html"
        }
    }

    // Reactively update WebView state via JS
    val stateStr = remember(state) {
        when (state) {
            JarvisVisualizerState.IDLE -> "idle"
            JarvisVisualizerState.LISTENING -> "listening"
            JarvisVisualizerState.THINKING -> "thinking"
            JarvisVisualizerState.SPEAKING -> "speaking"
        }
    }

    LaunchedEffect(stateStr, audioAmplitude, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val jsCmd = """
            if (window.setOrbState) {
                window.setOrbState('$stateStr', $audioAmplitude);
            }
            if (window.JarvisOrb) {
                window.JarvisOrb.setState('$stateStr');
                window.JarvisOrb.setExternalAudio($audioAmplitude, $audioAmplitude, $audioAmplitude);
            }
        """.trimIndent()
        wv.evaluateJavascript(jsCmd, null)
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val initJs = """
                            if (window.setOrbState) {
                                window.setOrbState('$stateStr', $audioAmplitude);
                            }
                        """.trimIndent()
                        view?.evaluateJavascript(initJs, null)
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onOrbClicked() {
                        onOrbTap()
                    }

                    @JavascriptInterface
                    fun onMicClicked() {
                        onMicTap()
                    }

                    @JavascriptInterface
                    fun onPowerClicked() {
                        onOrbTap()
                    }

                    @JavascriptInterface
                    fun onScreenShareClicked() {
                        onScreenShareTap()
                    }

                    @JavascriptInterface
                    fun onHistoryClicked() {
                        onSettingsTap()
                    }

                    @JavascriptInterface
                    fun onSettingsClicked() {
                        onSettingsTap()
                    }
                }, "JarvisBridge")

                loadUrl(assetUrl)
                webViewRef = this
            }
        },
        update = { wv ->
            webViewRef = wv
        },
        modifier = modifier
    )
}
