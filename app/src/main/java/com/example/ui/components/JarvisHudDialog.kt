package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.JarvisMultiCommandEngine
import com.example.util.JarvisScreenObserver
import com.example.util.SmartConversationMemory
import kotlinx.coroutines.launch

/**
 * Full-screen 3D JARVIS Live Arc HUD Dialog.
 * Provides the legendary Iron Man Mark 85 Arc Core & 3D Particle Orb holographic interface,
 * with real-time screen inspection, memory telemetry, and direct multi-command voice/text dispatch.
 */
@Composable
fun JarvisHudDialog(
    onDismiss: () -> Unit,
    onExecutePrompt: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var visualizerMode by remember { mutableStateOf(JarvisVisualizerMode.ARC_HUD) }
    var visualizerState by remember { mutableStateOf(JarvisVisualizerState.IDLE) }
    var audioAmp by remember { mutableFloatStateOf(0.1f) }
    var commandInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var executionStatusText by remember { mutableStateOf("JARVIS ONLINE // MARK 85 ARC CORE READY") }

    // Screen telemetry
    var screenLocation by remember { mutableStateOf("Scanning screen...") }
    LaunchedEffect(Unit) {
        val sig = JarvisScreenObserver.inspectCurrentScreen()
        screenLocation = sig.humanReadableLocation
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF040204))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Telemetry Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (visualizerState == JarvisVisualizerState.IDLE) Color(0xFF10B981) else Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STARK // JARVIS AI",
                            color = Color(0xFFFF2A4B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    }

                    // Mode Switcher Pills (Arc HUD vs 3D Particle Orb)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x33FF2A4B))
                            .border(1.dp, Color(0x66FF2A4B), RoundedCornerShape(20.dp))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (visualizerMode == JarvisVisualizerMode.ARC_HUD) Color(0xFFE11D48) else Color.Transparent)
                                .clickable { visualizerMode = JarvisVisualizerMode.ARC_HUD }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ARC CORE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (visualizerMode == JarvisVisualizerMode.PARTICLE_ORB) Color(0xFF0284C7) else Color.Transparent)
                                .clickable { visualizerMode = JarvisVisualizerMode.PARTICLE_ORB }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "3D ORB",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Live Screen & Memory Telemetry Card
                Surface(
                    color = Color(0x221F0A10),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF2A4B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(text = "📍", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Screen: $screenLocation",
                                color = Color(0xFFFCA5A5),
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        val memoryCount = remember { SmartConversationMemory.getAllMemoriesList().size }
                        Text(
                            text = "🧠 Memory: $memoryCount facts",
                            color = Color(0xFF93C5FD),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 3D Visualizer Stage (Takes main viewport)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Jarvis3dLiveVisualizer(
                        mode = visualizerMode,
                        state = visualizerState,
                        audioAmplitude = audioAmp,
                        onOrbTap = {
                            visualizerState = if (visualizerState == JarvisVisualizerState.IDLE) {
                                audioAmp = 0.6f
                                JarvisVisualizerState.LISTENING
                            } else {
                                audioAmp = 0.05f
                                JarvisVisualizerState.IDLE
                            }
                        },
                        onMicTap = {
                            visualizerState = JarvisVisualizerState.LISTENING
                            audioAmp = 0.8f
                        },
                        onScreenShareTap = {
                            val sig = JarvisScreenObserver.inspectCurrentScreen()
                            screenLocation = sig.humanReadableLocation
                            executionStatusText = "Screen signature updated: ${sig.humanReadableLocation}"
                        },
                        onSettingsTap = {
                            executionStatusText = "Displaying JARVIS Diagnostics"
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Live Status Console
                Text(
                    text = executionStatusText,
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )

                // Command Dispatcher Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x331E1B24))
                        .border(1.dp, Color(0x44FF2A4B), RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        placeholder = {
                            Text(
                                text = "Speak or type compound commands...",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF2A4B),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Execute Button
                    IconButton(
                        onClick = {
                            val prompt = commandInput.trim()
                            if (prompt.isNotBlank() && !isExecuting) {
                                isExecuting = true
                                visualizerState = JarvisVisualizerState.THINKING
                                audioAmp = 0.7f
                                coroutineScope.launch {
                                    executionStatusText = "⚡ Executing command pipeline..."
                                    val result = JarvisMultiCommandEngine.executeCompoundPrompt(context, prompt)
                                    if (result.handled) {
                                        executionStatusText = result.summaryFeedback.lines().firstOrNull() ?: "Success"
                                        visualizerState = JarvisVisualizerState.SPEAKING
                                        audioAmp = 0.9f
                                        if (result.remainingAiQuery != null) {
                                            onExecutePrompt(result.remainingAiQuery)
                                        }
                                    } else {
                                        visualizerState = JarvisVisualizerState.SPEAKING
                                        audioAmp = 0.5f
                                        onExecutePrompt(prompt)
                                        executionStatusText = "Forwarded to AI Engine"
                                    }
                                    commandInput = ""
                                    isExecuting = false
                                }
                            }
                        },
                        enabled = commandInput.isNotBlank() && !isExecuting,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF1744), Color(0xFF8B5CF6))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Execute",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
