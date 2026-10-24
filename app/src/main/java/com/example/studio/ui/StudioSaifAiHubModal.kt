package com.example.studio.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio.model.*
import com.example.studio.state.StudioAction
import com.example.studio.state.StudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Master SAIF AI Video Workspace Modal.
 * Features:
 * 1. Prominent Mode Switch: [ AI Workflows (4) | Classic Editing (4) ]
 * 2. AI Workflows:
 *    - 1. ZERO TO VIDEO (Conversational Director + Adaptive Question Cards)
 *    - 2. VIDEO TO EDITING (Smart pacing, cuts & grade without altering narration)
 *    - 3. VIDEO EDITING WITH AI VOICE (Footage-aware narration generation & music ducking)
 *    - 4. VOICE OVER TO VIDEO (Authoritative voiceover preservation + matching scenes)
 * 3. Classic Editing:
 *    - 1. Prompt Se Edit
 *    - 2. Video Dekar Edit
 *    - 3. Script Se Video Banao
 *    - 4. Ek Line Se Video Banao
 *    - Plus Telegram Asset Manager & AI Pro Tools
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioSaifAiHubModal(
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val project = uiState.project

    // Master Workspace Mode Switch: AI Workflows vs Classic Tools
    var workspaceMode by remember { mutableStateOf(SaifAiWorkspaceMode.AI_WORKFLOWS) }

    // Active AI Workflow selected (null = Hub view)
    var activeAiWorkflow by remember { mutableStateOf<AiWorkflowType?>(null) }

    // Classic Tool Submode: 0: Menu, 1: Prompt Se Edit, 2: Video Dekar Edit, 3: Script Se Video, 4: Ek Line Se Video, 5: Telegram Assets
    var classicSubMode by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0C1B),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(44.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = Color(0xFF00E5FF).copy(alpha = 0.5f)
            ) {}
        },
        modifier = Modifier.fillMaxHeight(0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF7B2BFF), Color(0xFF00E5FF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SAIF AI",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x3500E5FF)
                            ) {
                                Text(
                                    text = if (workspaceMode == SaifAiWorkspaceMode.AI_WORKFLOWS) "AI DIRECTOR" else "CLASSIC SUITE",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = if (activeAiWorkflow != null) {
                                activeAiWorkflow!!.stepLabel
                            } else if (workspaceMode == SaifAiWorkspaceMode.AI_WORKFLOWS) {
                                "4 Autonomous Video Creation & Editing Workflows"
                            } else {
                                "Original Editing Options & Precision Tools"
                            },
                            color = Color(0xFF9E95AC),
                            fontSize = 11.5.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (activeAiWorkflow != null) {
                            activeAiWorkflow = null
                        } else if (classicSubMode != 0) {
                            classicSubMode = 0
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B1528))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFCCC3D9),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Top Mode Switch: [ AI Workflows (4) | Classic Editing (4) ]
            if (activeAiWorkflow == null && classicSubMode == 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF161224),
                    border = BorderStroke(1.dp, Color(0x3300E5FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Tab 1: AI Workflows
                        val isAiSel = workspaceMode == SaifAiWorkspaceMode.AI_WORKFLOWS
                        Surface(
                            onClick = { workspaceMode = SaifAiWorkspaceMode.AI_WORKFLOWS },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isAiSel) Color(0xFF7B2BFF) else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = if (isAiSel) Color.White else Color(0xFFCCC3D9),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI WORKFLOWS (4)",
                                    color = if (isAiSel) Color.White else Color(0xFFCCC3D9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Tab 2: Classic Editing Options
                        val isClassicSel = workspaceMode == SaifAiWorkspaceMode.CLASSIC_TOOLS
                        Surface(
                            onClick = { workspaceMode = SaifAiWorkspaceMode.CLASSIC_TOOLS },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isClassicSel) Color(0xFF006CEB) else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = if (isClassicSel) Color.White else Color(0xFFCCC3D9),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CLASSIC TOOLS (4)",
                                    color = if (isClassicSel) Color.White else Color(0xFFCCC3D9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Body Content
            if (activeAiWorkflow != null) {
                // Active Conversational AI Director Chat for selected workflow
                StudioAiDirectorChatView(
                    workflowType = activeAiWorkflow!!,
                    viewModel = viewModel,
                    onBack = { activeAiWorkflow = null },
                    onCompleted = {
                        activeAiWorkflow = null
                        onDismiss()
                    }
                )
            } else if (workspaceMode == SaifAiWorkspaceMode.AI_WORKFLOWS) {
                // 4 Core AI Workflows Hub
                AiWorkflowsHubView(
                    onSelectWorkflow = { wf -> activeAiWorkflow = wf }
                )
            } else {
                // Classic Tools Suite
                ClassicEditingSuiteView(
                    viewModel = viewModel,
                    activeSubMode = classicSubMode,
                    onSelectSubMode = { classicSubMode = it },
                    onBackToClassicMenu = { classicSubMode = 0 },
                    onCompleted = onDismiss
                )
            }
        }
    }
}

/**
 * Hub displaying the 4 core AI workflows:
 * 1. ZERO TO VIDEO
 * 2. VIDEO TO EDITING
 * 3. VIDEO EDITING WITH AI VOICE
 * 4. VOICE OVER TO VIDEO
 */
@Composable
private fun AiWorkflowsHubView(
    onSelectWorkflow: (AiWorkflowType) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SaifAiWorkflowCard(
                type = AiWorkflowType.ZERO_TO_VIDEO,
                icon = Icons.Default.Bolt,
                gradient = listOf(Color(0xFF7B2BFF), Color(0xFF00E5FF)),
                accentColor = Color(0xFF00E5FF),
                onClick = { onSelectWorkflow(AiWorkflowType.ZERO_TO_VIDEO) }
            )
        }

        item {
            SaifAiWorkflowCard(
                type = AiWorkflowType.VIDEO_TO_EDITING,
                icon = Icons.Default.Movie,
                gradient = listOf(Color(0xFF006CEB), Color(0xFF7B2BFF)),
                accentColor = Color(0xFFA78BFA),
                onClick = { onSelectWorkflow(AiWorkflowType.VIDEO_TO_EDITING) }
            )
        }

        item {
            SaifAiWorkflowCard(
                type = AiWorkflowType.VIDEO_EDIT_WITH_AI_VOICE,
                icon = Icons.Default.RecordVoiceOver,
                gradient = listOf(Color(0xFF10B981), Color(0xFF006CEB)),
                accentColor = Color(0xFF34D399),
                onClick = { onSelectWorkflow(AiWorkflowType.VIDEO_EDIT_WITH_AI_VOICE) }
            )
        }

        item {
            SaifAiWorkflowCard(
                type = AiWorkflowType.VOICEOVER_TO_VIDEO,
                icon = Icons.Default.SpatialAudioOff,
                gradient = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)),
                accentColor = Color(0xFFF472B6),
                onClick = { onSelectWorkflow(AiWorkflowType.VOICEOVER_TO_VIDEO) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF161224),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "All 4 workflows feature a Conversational AI Director with Adaptive Question Cards, custom inputs, and Universal API support.",
                        color = Color(0xFFCCC3D9),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SaifAiWorkflowCard(
    type: AiWorkflowType,
    icon: ImageVector,
    gradient: List<Color>,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF191326),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f)),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            gradient[0].copy(alpha = 0.22f),
                            gradient[1].copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(gradient)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = type.title,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentColor.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = type.badge,
                                color = accentColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = type.subtitle,
                        color = Color(0xFFCCC3D9),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Classic Editing Suite retaining the original 4 options:
 * 1. Prompt Se Edit
 * 2. Video Dekar Edit
 * 3. Script Se Video Banao
 * 4. Ek Line Se Video Banao
 */
@Composable
private fun ClassicEditingSuiteView(
    viewModel: StudioViewModel,
    activeSubMode: Int,
    onSelectSubMode: (Int) -> Unit,
    onBackToClassicMenu: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val project = viewModel.uiState.value.project
    val scope = rememberCoroutineScope()

    if (activeSubMode == 0) {
        // Classic Menu List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ClassicOptionCard(
                    title = "1. Prompt Se Edit (Level 5)",
                    subtitle = "Direct text prompts to modify colors, scale, filters and transitions on timeline",
                    badge = "PROMPT FX",
                    accent = Color(0xFF00DAF3),
                    onClick = { onSelectSubMode(1) }
                )
            }

            item {
                ClassicOptionCard(
                    title = "2. Video Dekar Edit (Level 5 Reference)",
                    subtitle = "Import reference footage for automated cut detection, pacing, and color grade",
                    badge = "REFERENCE",
                    accent = Color(0xFFA78BFA),
                    onClick = { onSelectSubMode(2) }
                )
            }

            item {
                ClassicOptionCard(
                    title = "3. Script Se Video Banao (Level 15 Script Writer)",
                    subtitle = "Generate structured multi-act script with speech pacing and scene visual cues",
                    badge = "SCRIPT AI",
                    accent = Color(0xFF34D399),
                    onClick = { onSelectSubMode(3) }
                )
            }

            item {
                ClassicOptionCard(
                    title = "4. Ek Line Se Video Banao (Level 18 Zero-to-Video)",
                    subtitle = "1-line prompt fast synthesizer for multi-lane timeline creation",
                    badge = "1-LINE AI",
                    accent = Color(0xFFF472B6),
                    onClick = { onSelectSubMode(4) }
                )
            }
        }
    } else {
        // Sub-mode view
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBackToClassicMenu,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1735),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Text(
                        text = "← Back to Classic Tools",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            when (activeSubMode) {
                1 -> {
                    // Prompt Se Edit
                    var promptInput by remember { mutableStateOf("Boost cinematic contrast and add neon vignette") }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Prompt Se Edit", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                val updated = applyCustomPromptEditToProject(project, promptInput)
                                viewModel.commit(StudioAction.ReplaceProject(updated))
                                Toast.makeText(context, "Prompt edit applied to timeline!", Toast.LENGTH_SHORT).show()
                                onCompleted()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Apply Prompt Modification ✓", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                2 -> {
                    // Video Dekar Edit
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Video Dekar Edit", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Apply automated cinematic grade, zoom ramps, and jump cuts to current clips.",
                            color = Color(0xFFCCC3D9),
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = {
                                val updated = applyCinematicGradeToProject(project)
                                viewModel.commit(StudioAction.ReplaceProject(updated))
                                Toast.makeText(context, "Applied reference cinematic grade!", Toast.LENGTH_SHORT).show()
                                onCompleted()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006CEB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Apply Cinematic Grade & Jump Cuts", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                3 -> {
                    // Script Se Video Banao
                    var scriptIdea by remember { mutableStateOf("5 Lessons on Building High Performance Software") }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Script Se Video Banao", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = scriptIdea,
                            onValueChange = { scriptIdea = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                val updated = generateZeroToVideoStudioProject(
                                    baseProject = project,
                                    prompt = scriptIdea,
                                    durationSec = 30f,
                                    style = "Cinematic 35mm",
                                    aspect = "9:16"
                                )
                                viewModel.commit(StudioAction.ReplaceProject(updated))
                                Toast.makeText(context, "Generated video project from script!", Toast.LENGTH_SHORT).show()
                                onCompleted()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate Full Video from Script", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                4 -> {
                    // Ek Line Se Video Banao
                    var oneLine by remember { mutableStateOf("Tokyo rain neon cyber drift") }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Ek Line Se Video Banao", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = oneLine,
                            onValueChange = { oneLine = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                val updated = generateZeroToVideoStudioProject(
                                    baseProject = project,
                                    prompt = oneLine,
                                    durationSec = 15f,
                                    style = "Cyberpunk Neon",
                                    aspect = "9:16"
                                )
                                viewModel.commit(StudioAction.ReplaceProject(updated))
                                Toast.makeText(context, "1-Line Video synthesized onto timeline!", Toast.LENGTH_SHORT).show()
                                onCompleted()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Synthesize 1-Line Video", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicOptionCard(
    title: String,
    subtitle: String,
    badge: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1428),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accent.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = badge,
                            color = accent,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = subtitle, color = Color(0xFFCCC3D9), fontSize = 11.sp, lineHeight = 15.sp)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
    }
}

// =========================================================================
// PURE ALGORITHMIC GENERATION & EDITING HELPERS
// =========================================================================

fun generateZeroToVideoStudioProject(
    baseProject: StudioProject,
    prompt: String,
    durationSec: Float,
    style: String,
    aspect: String
): StudioProject {
    val totalUs = (durationSec * 1_000_000L).toLong()
    val numScenes = when {
        durationSec <= 6f -> 2
        durationSec <= 15f -> 3
        else -> 4
    }
    val sceneDurationUs = totalUs / numScenes

    val videoLaneId = baseProject.lanes.firstOrNull { it.trackType == TrackType.VIDEO }?.id ?: "lane_video_1"
    val textLaneId = baseProject.lanes.firstOrNull { it.trackType == TrackType.TEXT }?.id ?: "lane_text_1"
    val voiceLaneId = baseProject.lanes.firstOrNull { it.trackType == TrackType.VOICE }?.id ?: "lane_voice_1"
    val audioLaneId = baseProject.lanes.firstOrNull { it.trackType == TrackType.AUDIO }?.id ?: "lane_audio_1"

    val styleEffects = when (style) {
        "Cyberpunk Neon" -> ItemEffects(contrast = 1.30f, saturation = 1.40f, neonGlow = 0.45f, neonColorHex = 0xFF00E5FF)
        "Cinematic 35mm" -> ItemEffects(contrast = 1.15f, saturation = 0.95f, vignette = 0.35f)
        "Anime Ghibli" -> ItemEffects(contrast = 1.05f, saturation = 1.45f, brightness = 1.10f)
        "3D Hyper-Real" -> ItemEffects(contrast = 1.25f, saturation = 1.20f)
        else -> ItemEffects(contrast = 1.10f, saturation = 1.15f)
    }

    val newItems = mutableListOf<TimelineItem>()

    // 1. Generate Video Scenes on Video Lane
    for (i in 0 until numScenes) {
        val startUs = i * sceneDurationUs
        val sceneTag = when (i) {
            0 -> "Intro: ${prompt.take(20)}"
            numScenes - 1 -> "Climax & Outro"
            else -> "Dynamic Action ${i + 1}"
        }
        val videoItem = VideoItem(
            id = UUID.randomUUID().toString(),
            laneId = videoLaneId,
            startUs = startUs,
            durationUs = sceneDurationUs,
            mediaUri = "ai_scene_${i + 1}",
            label = sceneTag,
            effects = styleEffects,
            transform = ItemTransform(scale = 1.05f),
            origin = "ai",
            aiWhy = "Zero-to-video scene $i for style $style"
        )
        newItems.add(videoItem)
    }

    // 2. Generate Kinetic Title & Subtitles on Text Lane
    val titleItem = TextItem(
        id = UUID.randomUUID().toString(),
        laneId = textLaneId,
        startUs = 0L,
        durationUs = sceneDurationUs.coerceAtMost(4_000_000L),
        text = prompt.take(28).uppercase(),
        stylePreset = if (style == "Cyberpunk Neon") "Hormozi Neon" else "Cinematic Serif",
        fontSizeSp = 26f,
        origin = "ai"
    )
    newItems.add(titleItem)

    // 3. Generate Voiceover on Voice Lane
    val voiceItem = VoiceItem(
        id = UUID.randomUUID().toString(),
        laneId = voiceLaneId,
        startUs = 500_000L,
        durationUs = (totalUs - 1_000_000L).coerceAtLeast(2_000_000L),
        text = "Experience $prompt in full cinematic power.",
        speakerTone = "Energetic",
        audioDucking = true,
        origin = "ai"
    )
    newItems.add(voiceItem)

    // 4. Generate Ambient BGM on Audio Lane
    val audioItem = AudioItem(
        id = UUID.randomUUID().toString(),
        laneId = audioLaneId,
        startUs = 0L,
        durationUs = totalUs,
        isSfx = false,
        volume = 0.40f,
        fadeInUs = 500_000L,
        fadeOutUs = 1_000_000L,
        audioDucking = true,
        duckingAmount = 0.35f,
        label = "AI Ambient Score",
        origin = "ai"
    )
    newItems.add(audioItem)

    return baseProject.copy(
        name = prompt.take(24).trim().ifBlank { "AI Video Project" },
        aspect = aspect,
        items = newItems,
        updatedAt = System.currentTimeMillis()
    )
}

fun applyCinematicGradeToProject(project: StudioProject): StudioProject {
    val updatedItems = project.items.map { item ->
        if (item is VideoItem) {
            item.copy(
                effects = item.effects.copy(
                    contrast = 1.25f,
                    saturation = 1.28f,
                    vignette = 0.28f,
                    brightness = 1.02f
                ),
                transform = item.transform.copy(scale = 1.04f)
            )
        } else item
    }
    return project.copy(items = updatedItems, updatedAt = System.currentTimeMillis())
}

fun applyCustomPromptEditToProject(
    project: StudioProject,
    prompt: String
): StudioProject {
    val pLower = prompt.lowercase()
    val boostContrast = pLower.contains("contrast") || pLower.contains("cinematic")
    val boostSat = pLower.contains("saturation") || pLower.contains("color") || pLower.contains("grade")
    val addZoom = pLower.contains("zoom") || pLower.contains("scale") || pLower.contains("cut")

    val updatedItems = project.items.map { item ->
        if (item is VideoItem) {
            val newEffects = item.effects.copy(
                contrast = if (boostContrast) 1.25f else item.effects.contrast,
                saturation = if (boostSat) 1.30f else item.effects.saturation,
                vignette = if (boostContrast) 0.25f else item.effects.vignette
            )
            val newTransform = item.transform.copy(
                scale = if (addZoom) 1.06f else item.transform.scale
            )
            item.copy(effects = newEffects, transform = newTransform)
        } else item
    }

    val textLaneId = project.lanes.firstOrNull { it.trackType == TrackType.TEXT }?.id ?: "lane_text_1"
    val hasText = project.items.any { it is TextItem }
    val extraTitle = if (!hasText) {
        listOf(
            TextItem(
                id = UUID.randomUUID().toString(),
                laneId = textLaneId,
                startUs = 0L,
                durationUs = 3_000_000L,
                text = prompt.take(24).uppercase(),
                stylePreset = "Hormozi Neon",
                origin = "ai"
            )
        )
    } else emptyList()

    return project.copy(items = updatedItems + extraTitle, updatedAt = System.currentTimeMillis())
}
