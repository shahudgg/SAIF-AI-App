package com.example.agent

import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.util.SaifAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class UiElement(
    val idx: Int,
    val role: String,
    val text: String,
    val desc: String,
    val hint: String,
    val stateDesc: String,
    val viewId: String,
    val bounds: Rect,
    val norm: List<Int>, // [ymin, xmin, ymax, xmax] in 0..999 scale
    val clickable: Boolean,
    val longClickable: Boolean,
    val editable: Boolean,
    val focused: Boolean,
    val checked: Boolean,
    val selected: Boolean,
    val enabled: Boolean,
    val scrollable: Boolean,
    val parentIdx: Int
)

data class ScreenSnapshot(
    val packageName: String,
    val windowTitle: String,
    val activityHint: String,
    val keyboardVisible: Boolean,
    val focusedInputIdx: Int,
    val hasScrollable: Boolean,
    val secure: Boolean,
    val isLocked: Boolean,
    val displayWidth: Int,
    val displayHeight: Int,
    val elements: List<UiElement>,
    val screenshotJpegBase64: String?,
    val fingerprint: String
) {
    fun toCompactText(maxChars: Int = 7000): String {
        val sb = StringBuilder()
        sb.append("PKG: ").append(packageName)
        if (windowTitle.isNotBlank()) sb.append(" | TITLE: ").append(windowTitle)
        if (keyboardVisible) sb.append(" [KEYBOARD_OPEN]")
        if (secure) sb.append(" [SECURE/BLACK_SCREEN]")
        sb.append("\nELEMENTS:\n")

        for (el in elements) {
            val line = StringBuilder()
            line.append("[${el.idx}] ").append(el.role)
            if (el.text.isNotBlank()) line.append(" \"").append(el.text).append("\"")
            if (el.desc.isNotBlank() && el.desc != el.text) line.append(" desc=\"").append(el.desc).append("\"")
            if (el.viewId.isNotBlank()) line.append(" id=").append(el.viewId)
            if (el.clickable) line.append(" clickable")
            if (el.editable) line.append(" editable")
            if (el.focused) line.append(" focused")
            if (el.selected) line.append(" selected")
            if (el.scrollable) line.append(" scrollable")
            line.append(" [${el.norm.joinToString(",")}]\n")

            if (sb.length + line.length > maxChars) {
                sb.append("... [truncated ").append(elements.size - el.idx).append(" elements]\n")
                break
            }
            sb.append(line)
        }
        return sb.toString()
    }
}

object ScreenObserver {
    private const val TAG = "SAIF_AGENT"
    private const val MAX_ELEMENTS = 450
    private const val OVERLAY_PACKAGE = "com.saifai.assistant"

    // Snapshot node registry for the current frame
    private val nodeRegistry = mutableMapOf<Int, AccessibilityNodeInfo>()

    @Synchronized
    fun registerNode(idx: Int, node: AccessibilityNodeInfo) {
        nodeRegistry[idx] = node
    }

    @Synchronized
    fun getNode(idx: Int): AccessibilityNodeInfo? {
        val node = nodeRegistry[idx] ?: return null
        return try {
            // Test if node is still valid
            node.refresh()
            node
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun clearRegistry() {
        nodeRegistry.clear()
    }

    suspend fun capture(withScreenshot: Boolean = true): ScreenSnapshot = withContext(Dispatchers.Default) {
        val service = SaifAccessibilityService.instance
        if (service == null) {
            return@withContext ScreenSnapshot(
                packageName = "",
                windowTitle = "Service Disabled",
                activityHint = "",
                keyboardVisible = false,
                focusedInputIdx = -1,
                hasScrollable = false,
                secure = false,
                isLocked = false,
                displayWidth = 1080,
                displayHeight = 2400,
                elements = emptyList(),
                screenshotJpegBase64 = null,
                fingerprint = "NO_SERVICE"
            )
        }

        clearRegistry()

        // 1. Dimensions
        val metrics = service.getRealDisplayMetrics()
        val displayW = metrics.first.coerceAtLeast(1)
        val displayH = metrics.second.coerceAtLeast(1)

        // 2. Windows traversal & UI hierarchy gathering
        var currentPkg = service.getCurrentPackage()
        var windowTitle = ""
        var keyboardVisible = false
        var hasScrollable = false
        var focusedInputIdx = -1
        var isLocked = false

        val rawElements = mutableListOf<UiElement>()
        val windows = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) service.windows else emptyList()
        } catch (e: Exception) {
            emptyList<AccessibilityWindowInfo>()
        }

        val rootsToVisit = mutableListOf<AccessibilityNodeInfo>()
        if (windows.isNotEmpty()) {
            for (window in windows) {
                // Check if window is input method (keyboard)
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    keyboardVisible = true
                }
                // Skip SAIF AI's own overlay window
                val title = window.title?.toString() ?: ""
                if (title.contains("SAIF", ignoreCase = true) || title.contains("AssistantOverlay", ignoreCase = true)) {
                    continue
                }
                val root = window.root
                if (root != null) {
                    val rootPkg = root.packageName?.toString() ?: ""
                    if (!rootPkg.equals(OVERLAY_PACKAGE, ignoreCase = true) && !rootPkg.equals(service.packageName, ignoreCase = true)) {
                        rootsToVisit.add(root)
                    }
                }
            }
        }

        // Always fallback or supplement with rootInActiveWindow
        val activeRoot = service.rootInActiveWindow
        if (activeRoot != null) {
            val activePkg = activeRoot.packageName?.toString() ?: ""
            if (!rootsToVisit.any { it == activeRoot } &&
                !activePkg.equals(OVERLAY_PACKAGE, ignoreCase = true) &&
                !activePkg.equals(service.packageName, ignoreCase = true)
            ) {
                rootsToVisit.add(activeRoot)
            }
            if (currentPkg.isBlank()) currentPkg = activePkg
        }

        var elementIndexCounter = 0

        fun processNode(node: AccessibilityNodeInfo, parentIdx: Int) {
            if (elementIndexCounter >= MAX_ELEMENTS) return

            val isVisible = node.isVisibleToUser
            if (!isVisible) return

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // Ignore off-screen or zero-size elements
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            if (bounds.right <= 0 || bounds.bottom <= 0 || bounds.left >= displayW || bounds.top >= displayH) return

            val text = (node.text?.toString() ?: "").take(60)
            val desc = (node.contentDescription?.toString() ?: "").take(60)
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) (node.hintText?.toString() ?: "").take(60) else ""
            val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) (node.stateDescription?.toString() ?: "").take(60) else ""
            val fullViewId = node.viewIdResourceName ?: ""
            val shortViewId = if (fullViewId.contains(":id/")) fullViewId.substringAfter(":id/") else fullViewId

            val isClickable = node.isClickable
            val isLongClickable = node.isLongClickable
            val isEditable = node.isEditable
            val isFocused = node.isFocused
            val isChecked = node.isChecked
            val isSelected = node.isSelected
            val isEnabled = node.isEnabled
            val isScroll = node.isScrollable

            if (isScroll) hasScrollable = true

            // Retain node if interactive or has semantic text/desc
            val isMeaningful = isClickable || isLongClickable || isEditable || isScroll ||
                    text.isNotBlank() || desc.isNotBlank() || hint.isNotBlank() || stateDesc.isNotBlank()

            var myIdx = parentIdx
            if (isMeaningful) {
                val idx = elementIndexCounter++
                myIdx = idx
                registerNode(idx, node)

                if (isEditable && isFocused) {
                    focusedInputIdx = idx
                }

                // Compute normalized 0..999 coordinates: [ymin, xmin, ymax, xmax]
                val ymin = ((bounds.top.toFloat() / displayH) * 1000).toInt().coerceIn(0, 999)
                val xmin = ((bounds.left.toFloat() / displayW) * 1000).toInt().coerceIn(0, 999)
                val ymax = ((bounds.bottom.toFloat() / displayH) * 1000).toInt().coerceIn(0, 999)
                val xmax = ((bounds.right.toFloat() / displayW) * 1000).toInt().coerceIn(0, 999)
                val norm = listOf(ymin, xmin, ymax, xmax)

                val className = (node.className?.toString() ?: "").lowercase()
                val role = when {
                    isEditable || className.contains("edittext") -> "input"
                    isClickable && (className.contains("button") || className.contains("imagebutton")) -> "button"
                    className.contains("checkbox") || className.contains("switch") -> "switch"
                    className.contains("tab") -> "tab"
                    className.contains("image") -> "image"
                    className.contains("textview") || text.isNotBlank() -> "text"
                    isScroll -> "list"
                    isClickable -> "button"
                    else -> "other"
                }

                rawElements.add(
                    UiElement(
                        idx = idx,
                        role = role,
                        text = text,
                        desc = desc,
                        hint = hint,
                        stateDesc = stateDesc,
                        viewId = shortViewId,
                        bounds = bounds,
                        norm = norm,
                        clickable = isClickable,
                        longClickable = isLongClickable,
                        editable = isEditable,
                        focused = isFocused,
                        checked = isChecked,
                        selected = isSelected,
                        enabled = isEnabled,
                        scrollable = isScroll,
                        parentIdx = parentIdx
                    )
                )
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                processNode(child, myIdx)
            }
        }

        for (root in rootsToVisit) {
            try {
                processNode(root, -1)
            } catch (e: Exception) {
                Log.w(TAG, "Error visiting window root: ${e.message}")
            }
        }

        // Sort elements top-to-bottom, left-to-right
        val sortedElements = rawElements.sortedWith(
            compareBy<UiElement> { it.bounds.top }.thenBy { it.bounds.left }
        )

        // 3. Optional Screenshot
        var screenshotBase64: String? = null
        var isSecure = false

        if (withScreenshot) {
            val shotResult = service.captureScreenshotBase64()
            screenshotBase64 = shotResult.base64
            isSecure = shotResult.isSecure
        }

        // 4. Fingerprint computation (Package + top element text hash)
        val fpBuilder = StringBuilder().append(currentPkg).append(":")
        for (el in sortedElements.take(15)) {
            if (el.text.isNotBlank()) fpBuilder.append(el.text).append("|")
            else if (el.desc.isNotBlank()) fpBuilder.append(el.desc).append("|")
            else if (el.viewId.isNotBlank()) fpBuilder.append(el.viewId).append("|")
        }
        val fingerprint = try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(fpBuilder.toString().toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(12)
        } catch (e: Exception) {
            fpBuilder.toString().take(16)
        }

        ScreenSnapshot(
            packageName = currentPkg,
            windowTitle = windowTitle,
            activityHint = "",
            keyboardVisible = keyboardVisible,
            focusedInputIdx = focusedInputIdx,
            hasScrollable = hasScrollable,
            secure = isSecure,
            isLocked = isLocked,
            displayWidth = displayW,
            displayHeight = displayH,
            elements = sortedElements,
            screenshotJpegBase64 = screenshotBase64,
            fingerprint = fingerprint
        )
    }
}
