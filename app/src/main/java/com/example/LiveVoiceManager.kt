package com.example

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import com.example.vision.ScreenCaptureManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LiveVoiceManager {
    val isAppInForeground = MutableStateFlow(false)
    val isLiveModeActive = MutableStateFlow(false)
    
    private val _isScreenSharingActive = MutableStateFlow(false)
    val isScreenSharingActive: StateFlow<Boolean> = _isScreenSharingActive.asStateFlow()

    @Volatile
    var shouldStartWithScreenShare: Boolean = false

    var currentSessionId: String? = null
    val engineFlow = MutableStateFlow<LiveVoiceSessionManager?>(null)
    var engine: LiveVoiceSessionManager?
        get() = engineFlow.value
        set(value) { engineFlow.value = value }

    fun toggleLiveVoice(context: Context) {
        if (isLiveModeActive.value) {
            stopLiveVoice(context)
        } else {
            startLiveVoice(context, enableScreenShare = false)
        }
    }

    fun startLiveVoice(context: Context, enableScreenShare: Boolean = false) {
        shouldStartWithScreenShare = enableScreenShare
        val intent = Intent(context, LiveVoiceService::class.java).apply {
            putExtra(LiveVoiceService.EXTRA_SCREEN_SHARE, enableScreenShare)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveVoiceManager", "Error starting LiveVoiceService: ${e.message}", e)
        }
        isLiveModeActive.value = true
        _isScreenSharingActive.value = enableScreenShare
        if (enableScreenShare) {
            LiveVoiceService.activeInstance?.elevateToMediaProjection()
        }
    }

    fun startScreenShare(projection: MediaProjection, metrics: DisplayMetrics): Boolean {
        return try {
            LiveVoiceService.activeInstance?.elevateToMediaProjection()
            val started = ScreenCaptureManager.getInstance().startCapture(projection, metrics)
            if (started) {
                _isScreenSharingActive.value = true
                ScreenCaptureManager.getInstance().onStoppedListener = {
                    stopScreenShare()
                }
            }
            started
        } catch (e: Exception) {
            android.util.Log.e("LiveVoiceManager", "Failed to startScreenShare: ${e.message}", e)
            _isScreenSharingActive.value = false
            false
        }
    }

    fun stopScreenShare() {
        ScreenCaptureManager.getInstance().stopCapture()
        _isScreenSharingActive.value = false
        LiveVoiceService.activeInstance?.demoteFromMediaProjection()
    }

    fun stopLiveVoice(context: Context) {
        try {
            com.example.agent.PhoneAgentRuntime.cancelAll()
        } catch (ignored: Exception) {}
        stopScreenShare()
        val intent = Intent(context, LiveVoiceService::class.java)
        context.stopService(intent)
        engine?.stop()
        engine = null
        isLiveModeActive.value = false
        _isScreenSharingActive.value = false
    }
}
