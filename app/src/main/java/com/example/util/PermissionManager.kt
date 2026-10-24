package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PermissionItemInfo(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val isGranted: Boolean,
    val isSpecialSettings: Boolean = false,
    val manifestPermission: String? = null
)

data class PermissionPromptData(
    val id: String,
    val title: String,
    val message: String,
    val manifestPermission: String? = null,
    val isSpecial: Boolean = false,
    val onGranted: (() -> Unit)? = null
)

object PermissionManager {

    private val _permissionPrompt = MutableStateFlow<PermissionPromptData?>(null)
    val permissionPrompt: StateFlow<PermissionPromptData?> = _permissionPrompt

    fun showPermissionPrompt(prompt: PermissionPromptData) {
        _permissionPrompt.value = prompt
    }

    fun dismissPermissionPrompt() {
        _permissionPrompt.value = null
    }

    fun hasRecordAudio(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasAccessibility(context: Context): Boolean {
        return SaifAccessibilityService.isServiceEnabled(context)
    }

    fun hasCamera(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallPhone(context: Context): Boolean {
        return true // Uses ACTION_DIAL + Accessibility automation (zero dangerous permissions, safe for Play Protect)
    }

    fun hasReadContacts(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSendSms(context: Context): Boolean {
        return true // Uses ACTION_SENDTO SMS composer intent (zero dangerous permissions, safe for Play Protect)
    }

    fun hasPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasNotificationListener(context: Context): Boolean {
        return SaifNotificationListenerService.isNotificationAccessGranted(context)
    }

    fun hasBatteryOptimizationPermission(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun requestBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
    }

    fun hasWriteSettings(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun requestWriteSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBluetooth(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getAllPermissionItems(context: Context): List<PermissionItemInfo> {
        val list = mutableListOf<PermissionItemInfo>()

        list.add(
            PermissionItemInfo(
                id = "mic",
                title = "Microphone (माइक्रोफ़ोन)",
                description = "Live Baatcheet aur Hey Saif voice commands sunne ke liye",
                iconName = "mic",
                isGranted = hasRecordAudio(context),
                manifestPermission = Manifest.permission.RECORD_AUDIO
            )
        )

        list.add(
            PermissionItemInfo(
                id = "overlay",
                title = "Display Over Other Apps (फ़्लोटिंग ओवरले)",
                description = "Doosre apps ke upar live assistant orb aur controls dikhane ke liye",
                iconName = "layers",
                isGranted = hasOverlayPermission(context),
                isSpecialSettings = true
            )
        )

        list.add(
            PermissionItemInfo(
                id = "accessibility",
                title = "Accessibility Automation (स्क्रीन ऑटोमेशन)",
                description = "YouTube, WhatsApp, Instagram me auto click, scroll aur text type karne ke liye",
                iconName = "touch_app",
                isGranted = hasAccessibility(context),
                isSpecialSettings = true
            )
        )

        list.add(
            PermissionItemInfo(
                id = "camera",
                title = "Camera (कैमरा)",
                description = "Photo analysis, scan aur photo khinchne ke liye",
                iconName = "camera",
                isGranted = hasCamera(context),
                manifestPermission = Manifest.permission.CAMERA
            )
        )

        list.add(
            PermissionItemInfo(
                id = "contacts",
                title = "Contacts (संपर्क)",
                description = "Mom, Papa ya doston ka number dhoondh kar call/message karne ke liye",
                iconName = "contacts",
                isGranted = hasReadContacts(context),
                manifestPermission = Manifest.permission.READ_CONTACTS
            )
        )

        list.add(
            PermissionItemInfo(
                id = "call",
                title = "Phone Calls (फ़ोन कॉल)",
                description = "Voice command bol kar direct call milane ke liye",
                iconName = "phone",
                isGranted = hasCallPhone(context),
                manifestPermission = null
            )
        )

        list.add(
            PermissionItemInfo(
                id = "sms",
                title = "SMS Messages (संदेश)",
                description = "Direct SMS message bhejne ke liye",
                iconName = "message",
                isGranted = hasSendSms(context),
                manifestPermission = null
            )
        )

        list.add(
            PermissionItemInfo(
                id = "notification_listener",
                title = "Notification Access (सूचना पढ़ना)",
                description = "WhatsApp aur phone ke notifications bol kar sunane ke liye",
                iconName = "notifications",
                isGranted = hasNotificationListener(context),
                isSpecialSettings = true
            )
        )

        list.add(
            PermissionItemInfo(
                id = "battery_opt",
                title = "Battery Optimization (बैकग्राउंड में सक्रिय रहना)",
                description = "Live Baatcheet aur Screen Share bina rukawat chalne ke liye",
                iconName = "battery",
                isGranted = hasBatteryOptimizationPermission(context),
                isSpecialSettings = true
            )
        )

        list.add(
            PermissionItemInfo(
                id = "write_settings",
                title = "System Settings (सिस्टम सेटिंग्स)",
                description = "Voice command se screen brightness aur volume control karne ke liye",
                iconName = "settings",
                isGranted = hasWriteSettings(context),
                isSpecialSettings = true
            )
        )

        list.add(
            PermissionItemInfo(
                id = "location",
                title = "Location (स्थान / मौसम)",
                description = "Live weather, traffic aur aas-paas ki jaankari lene ke liye",
                iconName = "location",
                isGranted = hasLocationPermission(context),
                manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        list.add(
            PermissionItemInfo(
                id = "bluetooth",
                title = "Bluetooth & Headset (ब्लूटूथ हेडसेट)",
                description = "Wireless earphone aur car mic se hands-free baatcheet ke liye",
                iconName = "bluetooth",
                isGranted = hasBluetooth(context),
                manifestPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null
            )
        )

        list.add(
            PermissionItemInfo(
                id = "screen_projection",
                title = "Screen Sharing (स्क्रीन शेयर & विज़न)",
                description = "Live Multimodal AI me screen dikhane aur real-time assistance lene ke liye",
                iconName = "screen_share",
                isGranted = com.example.vision.ScreenCaptureManager.getInstance().isScreenShareActive(),
                isSpecialSettings = true
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                PermissionItemInfo(
                    id = "post_notifications",
                    title = "System Notifications (सूचना भेजना)",
                    description = "Live Voice service aur task updates alert notification dikhane ke liye",
                    iconName = "notifications_active",
                    isGranted = hasPostNotifications(context),
                    manifestPermission = Manifest.permission.POST_NOTIFICATIONS
                )
            )
        }

        return list
    }

    fun openSpecialPermissionSetting(context: Context, id: String) {
        try {
            when (id) {
                "overlay" -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                }
                "accessibility" -> {
                    SaifAccessibilityService.openAccessibilitySettings(context)
                }
                "notification_listener" -> {
                    SaifNotificationListenerService.openNotificationAccessSettings(context)
                }
                "battery_opt" -> {
                    requestBatteryOptimization(context)
                }
                "write_settings" -> {
                    requestWriteSettings(context)
                }
                "screen_projection" -> {
                    // Inform user that screen projection is requested dynamically when starting live baatcheet
                    android.widget.Toast.makeText(
                        context,
                        "Screen Share live baatcheet start karte waqt chalu hota hai",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    openAppSettings(context)
                }
            }
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // fallback
        }
    }
}
