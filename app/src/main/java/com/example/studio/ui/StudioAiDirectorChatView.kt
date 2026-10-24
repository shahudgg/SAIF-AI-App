package com.example.studio.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio.ai.StudioAiDirectorEngine
import com.example.studio.model.*
import com.example.studio.state.StudioAction
import com.example.studio.state.StudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StudioAiDirectorChatView(
    workflowType: AiWorkflowType,
    viewModel: StudioViewModel,
    onBack: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val brief = remember {
        WorkflowProjectBrief(
            workflowType = workflowType,
            topicOrScript = if (workflowType == AiWorkflowType.ZERO_TO_VIDEO) {
                "How Artificial Intelligence is Transforming Human Creativity"
            } else if (workflowType == AiWorkflowType.VOICEOVER_TO_VIDEO) {
                "In a world where every single second matters, high precision storytelling takes the lead."
            } else {
                "Make footage high energy with snappy jump cuts and vivid neon grade."
            }
        )
    }

    val messages = remember {
        mutableStateListOf<DirectorChatMessage>().apply {
            // Welcome message from AI Director
            add(
                DirectorChatMessage(
                    sender = "director",
                    messageText = "Hello! I am your AI Video Director for ${workflowType.title}. Let's tailor this project to your exact vision."
                )
            )

            // Inject first question card
            val questions = StudioAiDirectorEngine.generateInitialQuestions(workflowType)
            questions.forEach { q ->
                add(
                    DirectorChatMessage(
                        sender = "director",
                        messageText = q.question,
                        questionCard = q
                    )
                )
            }
        }
    }

    var userInputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    // Media picker for reference video or audio
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            brief.userFootageUri = uri.toString()
            messages.add(
                DirectorChatMessage(
                    sender = "user",
                    messageText = "Attached reference media: ${uri.lastPathSegment ?: "footage.mp4"}"
                )
            )
            Toast.makeText(context, "Media attached to project brief", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0C1B))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // --- 1. Top Header Bar ---
        Surface(
            color = Color(0xFF131022),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1D1730))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = workflowType.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x3300E5FF)
                            ) {
                                Text(
                                    text = "DIRECTOR",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Adaptive Conversational Planning",
                            color = Color(0xFF9E95AC),
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    onClick = {
                        messages.clear()
                        messages.add(
                            DirectorChatMessage(
                                sender = "director",
                                messageText = "Project reset. Ready for a fresh direction for ${workflowType.title}."
                            )
                        )
                        val questions = StudioAiDirectorEngine.generateInitialQuestions(workflowType)
                        questions.forEach { q ->
                            messages.add(
                                DirectorChatMessage(
                                    sender = "director",
                                    messageText = q.question,
                                    questionCard = q
                                )
                            )
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1735),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF))
                ) {
                    Text(
                        text = "Reset",
                        color = Color(0xFFCCC3D9),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // --- 2. Chat Stream with Interactive Question Cards ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = messages,
                key = { _, msg -> msg.id }
            ) { _, msg ->
                if (msg.sender == "director") {
                    // Director Bubble
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF7B2BFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                                color = Color(0xFF1A1428),
                                border = BorderStroke(1.dp, Color(0x22FFFFFF))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = msg.messageText,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp
                                    )
                                }
                            }

                            // Interactive Question Card if present
                            msg.questionCard?.let { card ->
                                Spacer(modifier = Modifier.height(8.dp))
                                ConversationalQuestionCard(
                                    card = card,
                                    brief = brief,
                                    onAnswerConfirmed = { answer ->
                                        // Update project brief according to question type
                                        when (card.id) {
                                            "duration" -> {
                                                val sec = answer.filter { it.isDigit() }.toFloatOrNull() ?: 30f
                                                brief.durationSeconds = sec
                                            }
                                            "language" -> brief.language = answer
                                            "style" -> brief.visualStyle = answer
                                            "aspect" -> brief.aspectRatio = answer
                                            "goal" -> brief.editingGoal = answer
                                            "voice" -> brief.voiceCharacter = answer
                                        }

                                        messages.add(
                                            DirectorChatMessage(
                                                sender = "user",
                                                messageText = "Confirmed choice: $answer"
                                            )
                                        )
                                    }
                                )
                            }

                            // Scene Breakdown Preview Card
                            msg.sceneBreakdownPreview?.let { scenes ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF1E1735),
                                    border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🎬 Planned Scene Manifest (${scenes.size} Scenes)",
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0x3300E5FF)
                                            ) {
                                                Text(
                                                    text = "${brief.durationSeconds.toInt()}s Total",
                                                    color = Color(0xFF00E5FF),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        scenes.forEach { sc ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF130E22),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Scene ${sc.sceneId}: ${sc.title}",
                                                            color = Color(0xFF00E5FF),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "${sc.durationSeconds.toInt()}s • ${sc.motionGraphicsType}",
                                                            color = Color(0xFFCCC3D9),
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    if (sc.narrationSegment.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = "\"${sc.narrationSegment}\"",
                                                            color = Color(0xFFE5E2E1),
                                                            fontSize = 11.sp,
                                                            lineHeight = 15.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Apply to timeline action button
                                        Button(
                                            onClick = {
                                                Toast.makeText(context, "Assembling timeline tracks...", Toast.LENGTH_SHORT).show()
                                                onCompleted()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            modifier = Modifier.fillMaxWidth().height(42.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Apply To Timeline ✓", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // User Bubble
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            color = Color(0xFF7B2BFF),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Text(
                                text = msg.messageText,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            if (isGenerating) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E5FF),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AI Director is analyzing & assembling scenes...",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // --- 3. Persistent Action Prompt Bar ---
        Surface(
            color = Color(0xFF131022),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Generate Final Project Button
                Button(
                    onClick = {
                        isGenerating = true
                        scope.launch {
                            val result = StudioAiDirectorEngine.executeWorkflowGeneration(
                                context = context,
                                baseProject = viewModel.uiState.value.project,
                                brief = brief,
                                onProgress = { _, _ -> }
                            )

                            isGenerating = false
                            result.onSuccess { generatedProject ->
                                viewModel.commit(StudioAction.ReplaceProject(generatedProject))
                                messages.add(
                                    DirectorChatMessage(
                                        sender = "director",
                                        messageText = "✨ Complete project assembled! Your timeline is ready with synchronized video scenes, kinetic titles, and audio ducking.",
                                        sceneBreakdownPreview = brief.sceneManifest
                                    )
                                )
                                Toast.makeText(context, "✅ Project generated onto timeline!", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF7B2BFF), Color(0xFF00E5FF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🚀 ASSEMBLE ${workflowType.title}",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Chat Input Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1B1528))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            mediaPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                )
                            )
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = Color(0xFFCCC3D9), modifier = Modifier.size(20.dp))
                    }

                    OutlinedTextField(
                        value = userInputText,
                        onValueChange = { userInputText = it },
                        placeholder = { Text("Ask Director or type instruction...", fontSize = 12.5.sp, color = Color(0xFF9E95AC)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            val text = userInputText.trim()
                            if (text.isNotBlank()) {
                                brief.topicOrScript = text
                                messages.add(
                                    DirectorChatMessage(
                                        sender = "user",
                                        messageText = text
                                    )
                                )
                                userInputText = ""

                                scope.launch {
                                    delay(400)
                                    messages.add(
                                        DirectorChatMessage(
                                            sender = "director",
                                            messageText = "Noted: \"$text\". I've updated the project direction. Hit 'Assemble' anytime or refine more preferences."
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7B2BFF))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Conversational Question Card with 4 presets + Custom option.
 * Can be edited anytime without discarding earlier choices.
 */
@Composable
fun ConversationalQuestionCard(
    card: QuestionCardData,
    brief: WorkflowProjectBrief,
    onAnswerConfirmed: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(!card.isConfirmed) }
    var selectedId by remember { mutableStateOf(card.selectedOptionId) }
    var customText by remember { mutableStateOf(card.customText) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1B1528),
        border = BorderStroke(1.dp, if (card.isConfirmed) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0x3300E5FF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (card.isConfirmed) Icons.Default.CheckCircle else Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = if (card.isConfirmed) Color(0xFF10B981) else Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = card.question,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (card.isConfirmed) {
                    Surface(
                        onClick = { isExpanded = !isExpanded },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF261E38)
                    ) {
                        Text(
                            text = if (isExpanded) "Collapse" else "Edit Answer ✏️",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (isExpanded) {
                if (card.explanation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = card.explanation,
                        color = Color(0xFF9E95AC),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Presets Grid
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.presets.forEach { opt ->
                        val isSel = selectedId == opt.id
                        Surface(
                            onClick = {
                                selectedId = opt.id
                                card.selectedOptionId = opt.id
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) Color(0xFF7B2BFF).copy(alpha = 0.35f) else Color(0xFF130F20),
                            border = BorderStroke(1.dp, if (isSel) Color(0xFF00E5FF) else Color(0x22FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = opt.label,
                                        color = if (isSel) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                    )
                                    opt.subtitle?.let { sub ->
                                        Text(text = sub, color = Color(0xFFCCC3D9), fontSize = 10.5.sp)
                                    }
                                }

                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // 5th Custom Option
                    if (card.allowCustom) {
                        val isCustomSel = selectedId == "custom"
                        Surface(
                            onClick = {
                                selectedId = "custom"
                                card.selectedOptionId = "custom"
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCustomSel) Color(0xFF7B2BFF).copy(alpha = 0.35f) else Color(0xFF130F20),
                            border = BorderStroke(1.dp, if (isCustomSel) Color(0xFF00E5FF) else Color(0x22FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Custom Option",
                                        color = if (isCustomSel) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isCustomSel) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (isCustomSel) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    }
                                }

                                if (isCustomSel) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = customText,
                                        onValueChange = {
                                            customText = it
                                            card.customText = it
                                        },
                                        placeholder = { Text(card.customPlaceholder, fontSize = 11.5.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00E5FF),
                                            unfocusedBorderColor = Color(0x33FFFFFF),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Confirm Choice Action
                Button(
                    onClick = {
                        card.isConfirmed = true
                        isExpanded = false
                        val ans = card.getEffectiveAnswer()
                        onAnswerConfirmed(ans)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text("Confirm Choice ✓", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Collapsed Summary
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selected: ${card.getEffectiveAnswer()}",
                    color = Color(0xFF10B981),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
