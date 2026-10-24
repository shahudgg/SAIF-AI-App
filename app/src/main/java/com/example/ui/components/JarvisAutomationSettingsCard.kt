package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.data.preferences.YouTubePreferences

@Composable
fun JarvisAutomationSettingsCard(
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ytPrefs = remember { YouTubePreferences(context) }
    val appPrefs = remember { AppPreferences(context) }

    var youtubeApiKey by remember { mutableStateOf(ytPrefs.youtubeApiKey) }
    var selectedPersonality by remember { mutableStateOf(appPrefs.assistantPersonality) }
    var soundEffectsEnabled by remember { mutableStateOf(appPrefs.isSoundEffectsEnabled) }
    var isExpanded by remember { mutableStateOf(true) }

    val isAccessibilityEnabled = JarvisAccessibilityService.isEnabled() ||
            com.example.util.SaifAccessibilityService.isServiceEnabled(context)

    val cardBg = if (isDarkTheme) Color(0xFF1A2234) else Color(0xFFF8FAFC)
    val cardBorder = if (isDarkTheme) Color(0xFF2E3D5B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accentBlue = Color(0xFF2563EB)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Device Control",
                            tint = accentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "JARVIS Device Automation & Voice",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textPrimary
                        )
                        Text(
                            text = "Phone control, WhatsApp, YouTube & sound FX",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle",
                    tint = textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Accessibility status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isAccessibilityEnabled) Color(0xFF10B981).copy(alpha = 0.12f)
                                else Color(0xFFF59E0B).copy(alpha = 0.12f)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAccessibilityEnabled) "Accessibility Automation: ACTIVE" else "Accessibility Automation: DISABLED",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                            }
                            Text(
                                text = "Required for hands-free WhatsApp auto-send, YouTube auto-play & screen clicks.",
                                fontSize = 11.sp,
                                color = textSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        if (!isAccessibilityEnabled) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Voice Personality Mode
                    Text(
                        text = "Voice Assistant Personality",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val personalities = listOf(
                        GeminiConstants.PERSONALITY_JARVIS,
                        GeminiConstants.PERSONALITY_PROFESSIONAL,
                        GeminiConstants.PERSONALITY_TECHGPT_X,
                        GeminiConstants.PERSONALITY_GIRLFRIEND,
                        GeminiConstants.PERSONALITY_BOYFRIEND
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        personalities.take(3).forEach { p ->
                            val isSel = selectedPersonality == p
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) accentBlue else cardBorder.copy(alpha = 0.5f))
                                    .clickable {
                                        selectedPersonality = p
                                        appPrefs.assistantPersonality = p
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = p,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else textSecondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        personalities.drop(3).forEach { p ->
                            val isSel = selectedPersonality == p
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) accentBlue else cardBorder.copy(alpha = 0.5f))
                                    .clickable {
                                        selectedPersonality = p
                                        appPrefs.assistantPersonality = p
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = p,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Procedural Sci-Fi Sound Effects Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Futuristic Earcon Chimes",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                            Text(
                                text = "Procedural sound effects on wake & standby",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                        Switch(
                            checked = soundEffectsEnabled,
                            onCheckedChange = {
                                soundEffectsEnabled = it
                                appPrefs.isSoundEffectsEnabled = it
                                if (it) {
                                    com.jarvis.assistant.audio.JarvisSoundEffects.playWakeSound()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentBlue
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // YouTube Data API Key (Optional)
                    Text(
                        text = "YouTube Data API Key (Optional)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textPrimary
                    )
                    Text(
                        text = "Leaves empty for built-in zero-quota direct video search.",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = youtubeApiKey,
                        onValueChange = {
                            youtubeApiKey = it
                            ytPrefs.youtubeApiKey = it.trim()
                        },
                        placeholder = { Text("AIzaSy...", fontSize = 12.sp, color = textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBlue,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
