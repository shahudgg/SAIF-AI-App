package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.components.FloatingVoiceOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class LiveVoiceService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    private lateinit var engine: LiveVoiceSessionManager

    companion object {
        @Volatile
        var activeInstance: LiveVoiceService? = null
        const val EXTRA_SCREEN_SHARE = "extra_screen_share"
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        engine = LiveVoiceSessionManager(this) { 
            stopSelf()
        }
        LiveVoiceManager.engine = engine
        
        createNotificationChannel()
        val isScreenShareRequested = LiveVoiceManager.shouldStartWithScreenShare || LiveVoiceManager.isScreenSharingActive.value
        val notification = NotificationCompat.Builder(this, "VOICE_CHANNEL")
            .setContentTitle(if (isScreenShareRequested) "Live Baatcheet & Screen Share Active" else "Live Baatcheet Session")
            .setContentText(if (isScreenShareRequested) "SAIF AI is watching your screen and listening..." else "Tap to return to SAIF AI")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
            
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val types = if (isScreenShareRequested) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(1, notification, types)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveVoiceService", "Error in startForeground onCreate: ${e.message}", e)
        }
        
        showOverlay()
        
        engine.start()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        serviceScope.launch {
            LiveVoiceManager.isAppInForeground.collect { inForeground ->
                if (inForeground) {
                    hideOverlay()
                } else {
                    showOverlay()
                }
            }
        }
    }
    
    private fun hideOverlay() {
        composeView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                android.util.Log.e("LiveVoiceService", "Error removing overlay", e)
            }
        }
        composeView = null
    }
    
    @androidx.annotation.OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    private fun showOverlay() {
        if (composeView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            android.util.Log.w("LiveVoiceService", "SYSTEM_ALERT_WINDOW not granted, skipping floating bubble")
            return
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }
        
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LiveVoiceService)
            setViewTreeViewModelStoreOwner(this@LiveVoiceService)
            setViewTreeSavedStateRegistryOwner(this@LiveVoiceService)
            
            setContent {
                FloatingVoiceOverlay(
                    engine = engine,
                    onClose = { stopSelf() },
                    onOpenApp = {
                        val intent = Intent(this@LiveVoiceService, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                    },
                    onDrag = { dx, dy ->
                        layoutParams.x += dx.toInt()
                        layoutParams.y += dy.toInt()
                        try {
                            windowManager.updateViewLayout(composeView, layoutParams)
                        } catch (e: Exception) {
                            android.util.Log.e("LiveVoiceService", "Error updating layout", e)
                        }
                    }
                )
            }
        }
        
        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Exception) {
            android.util.Log.e("LiveVoiceService", "Failed to add overlay window", e)
            composeView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "VOICE_CHANNEL",
                "Live Voice Session",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val enableScreenShare = intent?.getBooleanExtra(EXTRA_SCREEN_SHARE, false) == true || LiveVoiceManager.shouldStartWithScreenShare
        if (enableScreenShare) {
            elevateToMediaProjection()
        }
        return START_STICKY
    }

    fun elevateToMediaProjection() {
        try {
            val notification = NotificationCompat.Builder(this, "VOICE_CHANNEL")
                .setContentTitle("Live Baatcheet & Screen Share Active")
                .setContentText("SAIF AI is watching your screen and listening...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(1, notification)
            }
            android.util.Log.i("LiveVoiceService", "Elevated to MEDIA_PROJECTION foreground service successfully")
        } catch (e: Exception) {
            android.util.Log.e("LiveVoiceService", "Could not elevate to mediaProjection: ${e.message}", e)
        }
    }

    fun demoteFromMediaProjection() {
        try {
            val notification = NotificationCompat.Builder(this, "VOICE_CHANNEL")
                .setContentTitle("Live Voice Session Active")
                .setContentText("Tap to return to SAIF AI")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, notification)
            }
            android.util.Log.i("LiveVoiceService", "Demoted to MICROPHONE foreground service")
        } catch (e: Exception) {
            android.util.Log.e("LiveVoiceService", "Could not demote from mediaProjection: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeInstance = null
        com.example.vision.ScreenCaptureManager.getInstance().stopCapture()
        engine.stop()
        LiveVoiceManager.engine = null
        LiveVoiceManager.isLiveModeActive.value = false
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        
        composeView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                android.util.Log.e("LiveVoiceService", "Error removing view in onDestroy", e)
            }
        }
        composeView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}
