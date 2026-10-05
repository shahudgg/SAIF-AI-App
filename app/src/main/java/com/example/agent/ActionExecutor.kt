package com.example.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.agent.skills.AgentEnv
import com.example.agent.skills.AppSkill
import com.example.agent.skills.ContactAliasStore
import com.example.agent.skills.SkillRegistry
import com.example.util.SaifAccessibilityService
import kotlinx.coroutines.delay
import org.json.JSONObject

object ActionExecutor {
    private const val TAG = "SAIF_AGENT"

    suspend fun execute(call: AgentCall, context: Context, listener: AgentListener? = null): ActionResult {
        val name = call.name.lowercase()
        val args = call.arguments
        val service = SaifAccessibilityService.instance

        Log.d(TAG, "Executing call '$name' with args: $args")

        return try {
            when (name) {
                "click", "tap_xy" -> {
                    val normX = args.optInt("x", args.optInt("point_x", 500)).coerceIn(0, 999)
                    val normY = args.optInt("y", args.optInt("point_y", 500)).coerceIn(0, 999)
                    val metrics = service?.getRealDisplayMetrics() ?: Pair(1080, 2400)
                    val px = (normX.toFloat() / 1000f) * metrics.first
                    val py = (normY.toFloat() / 1000f) * metrics.second
                    // Random small jitter +- 4px
                    val jitterX = px + (Math.random().toFloat() * 8f - 4f)
                    val jitterY = py + (Math.random().toFloat() * 8f - 4f)
                    val ok = service?.performHumanTap(jitterX, jitterY) ?: false
                    ActionResult(ok, "Tapped coordinates ($normX, $normY)", fingerprintChanged = ok)
                }

                "tap_element" -> {
                    val idx = args.optInt("index", -1)
                    if (idx < 0) {
                        return ActionResult(false, "Invalid element index: $idx")
                    }
                    val node = ScreenObserver.getNode(idx)
                    if (node != null) {
                        var ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (!ok) {
                            var parent = node.parent
                            var depth = 0
                            while (parent != null && depth < 3) {
                                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                    ok = true
                                    break
                                }
                                parent = parent.parent
                                depth++
                            }
                        }
                        if (!ok) {
                            val bounds = Rect()
                            node.getBoundsInScreen(bounds)
                            if (bounds.width() > 0 && bounds.height() > 0) {
                                ok = service?.performHumanTap(bounds.centerX().toFloat(), bounds.centerY().toFloat()) ?: false
                            }
                        }
                        ActionResult(ok, "Tapped element [$idx]", fingerprintChanged = ok)
                    } else {
                        ActionResult(false, "Element [$idx] not found in current screen cache")
                    }
                }

                "type", "type_into" -> {
                    val text = args.optString("text", "")
                    val clearFirst = args.optBoolean("clear_first", true)
                    val pressEnter = args.optBoolean("press_enter", false)
                    val idx = args.optInt("index", -1)

                    var targetNode = if (idx >= 0) ScreenObserver.getNode(idx) else null
                    if (targetNode == null) {
                        val root = service?.rootInActiveWindow
                        targetNode = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    }

                    var ok = false
                    if (targetNode != null) {
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        delay(100)
                        if (clearFirst) {
                            val clearArgs = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "") }
                            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
                        }
                        val setArgs = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
                        ok = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

                        // Fallback to clipboard paste
                        if (!ok) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("agent_text", text))
                            ok = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        }
                    }

                    if (pressEnter) {
                        delay(250)
                        service?.pressEnterOrSearch()
                    }

                    ActionResult(ok, "Typed text: '$text'", fingerprintChanged = ok)
                }

                "swipe" -> {
                    val direction = args.optString("direction", "up").lowercase()
                    val distance = args.optString("distance", "medium").lowercase()
                    val ratio = when (distance) {
                        "short" -> 0.25f
                        "long" -> 0.70f
                        else -> 0.45f
                    }
                    val ok = service?.performHumanScrollDirection(direction, ratio) ?: false
                    ActionResult(ok, "Swiped $direction ($distance)", fingerprintChanged = ok)
                }

                "scroll_until" -> {
                    val query = args.optString("text", "")
                    val direction = args.optString("direction", "down")
                    val maxSwipes = args.optInt("max_swipes", 8).coerceIn(1, 15)

                    var found = false
                    for (i in 0 until maxSwipes) {
                        if (service?.waitForText(query, 500) == true) {
                            found = true
                            break
                        }
                        service?.performHumanScrollDirection(direction, 0.45f)
                        delay(400)
                        service?.waitForIdle(1000, 200)
                    }
                    ActionResult(found, if (found) "Found '$query' after scrolling" else "'$query' not found after $maxSwipes swipes", fingerprintChanged = found)
                }

                "drag_and_drop" -> {
                    val startX = args.optInt("start_x", 0).toFloat()
                    val startY = args.optInt("start_y", 0).toFloat()
                    val endX = args.optInt("end_x", 0).toFloat()
                    val endY = args.optInt("end_y", 0).toFloat()
                    val ok = service?.performHumanSwipe(startX, startY, endX, endY, 600L) ?: false
                    ActionResult(ok, "Drag and drop executed", fingerprintChanged = ok)
                }

                "long_press", "long_press_xy" -> {
                    val normX = args.optInt("x", 500).coerceIn(0, 999)
                    val normY = args.optInt("y", 500).coerceIn(0, 999)
                    val metrics = service?.getRealDisplayMetrics() ?: Pair(1080, 2400)
                    val px = (normX.toFloat() / 1000f) * metrics.first
                    val py = (normY.toFloat() / 1000f) * metrics.second
                    val ok = service?.performHumanTap(px, py, durationMs = 800L) ?: false
                    ActionResult(ok, "Long pressed ($normX, $normY)", fingerprintChanged = ok)
                }

                "press_key" -> {
                    val key = args.optString("key", "").uppercase()
                    val ok = when (key) {
                        "ENTER", "SEARCH" -> service?.pressEnterOrSearch() ?: false
                        "BACK" -> service?.pressBack() ?: false
                        "HOME" -> service?.pressHome() ?: false
                        "RECENTS" -> service?.pressRecents() ?: false
                        "VOLUME_UP" -> {
                            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                            am?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                            true
                        }
                        "VOLUME_DOWN" -> {
                            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                            am?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                            true
                        }
                        else -> false
                    }
                    ActionResult(ok, "Pressed key: $key", fingerprintChanged = ok)
                }

                "press_system", "go_back" -> {
                    val button = args.optString("button", "back").lowercase()
                    val ok = when (button) {
                        "home" -> service?.pressHome() ?: false
                        "back" -> service?.pressBack() ?: false
                        "recents" -> service?.pressRecents() ?: false
                        "notifications" -> service?.openNotificationsShade() ?: false
                        "quick_settings" -> service?.openQuickSettings() ?: false
                        "lock_screen" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false else false
                        "screenshot" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false else false
                        else -> service?.pressBack() ?: false
                    }
                    ActionResult(ok, "System action: $button", fingerprintChanged = ok)
                }

                "open_app" -> {
                    val appName = args.optString("app_name", "").ifBlank { args.optString("name", "") }
                    val skillRes = SkillRegistry.execute("open_app", args, AgentEnv(context, service, listener))
                    ActionResult(skillRes.success, skillRes.message, fingerprintChanged = skillRes.success, data = skillRes.data)
                }

                "open_url" -> {
                    val url = args.optString("url", "")
                    val pkg = args.optString("package", "")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        if (pkg.isNotBlank()) setPackage(pkg)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    service?.waitForIdle(2000, 300)
                    ActionResult(true, "Opened URL: $url", fingerprintChanged = true)
                }

                "run_skill" -> {
                    val skillName = args.optString("name", "")
                    val skillArgs = args.optJSONObject("args") ?: JSONObject()
                    val skillRes = SkillRegistry.execute(skillName, skillArgs, AgentEnv(context, service, listener))
                    ActionResult(skillRes.success, skillRes.message, fingerprintChanged = skillRes.success, data = skillRes.data)
                }

                "read_screen" -> {
                    val snapshot = ScreenObserver.capture(withScreenshot = false)
                    ActionResult(true, snapshot.toCompactText(4000), data = snapshot)
                }

                "find_contact" -> {
                    val q = args.optString("query", "")
                    val contacts = ContactAliasStore.resolveContact(context, q)
                    ActionResult(true, "Found ${contacts.size} contacts for '$q'", data = contacts.map { it.name })
                }

                "speak" -> {
                    val text = args.optString("text", "")
                    if (text.isNotBlank()) {
                        listener?.onMilestone(text)
                    }
                    ActionResult(true, "Spoke: $text")
                }

                "ask_user" -> {
                    val question = args.optString("question", "")
                    val optsArray = args.optJSONArray("options")
                    val opts = if (optsArray != null) (0 until optsArray.length()).map { optsArray.getString(it) } else null
                    val answer = UserDialogBridge.askUser(question, opts, listener)
                    ActionResult(true, "User responded: $answer", data = answer)
                }

                "note" -> {
                    val text = args.optString("text", "")
                    ActionResult(true, "Saved note: $text", data = text)
                }

                "wait" -> {
                    val seconds = args.optDouble("seconds", 1.0).coerceIn(0.1, 10.0)
                    delay((seconds * 1000).toLong())
                    ActionResult(true, "Waited ${seconds}s")
                }

                "take_screenshot" -> {
                    val shot = service?.captureScreenshotBase64()
                    ActionResult(shot?.base64 != null, "Captured screenshot", data = shot?.base64)
                }

                "finish" -> {
                    val success = args.optBoolean("success", true)
                    val summary = args.optString("summary", "Task completed")
                    ActionResult(success, summary, data = args.opt("data"))
                }

                "yield_to_user" -> {
                    val reason = args.optString("reason", "Manual user action needed")
                    ActionResult(false, reason)
                }

                else -> {
                    ActionResult(false, "Unsupported tool call: $name")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing action '$name': ${e.message}", e)
            ActionResult(false, "Action '$name' failed: ${e.message}")
        }
    }
}
