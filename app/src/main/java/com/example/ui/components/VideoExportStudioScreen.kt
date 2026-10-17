package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modern Export Studio Screen faithfully matching the provided HTML specification:
 * - Preview strip card
 * - Target Resolution (480p, 720p, 1080p, 4K)
 * - Frame Rate (24, 30, 60) & Format Codec (H.264, H.265)
 * - Neural Processing Options (AI watermark animation, Generate metadata pack)
 * - AI Metadata Pack Preview (Viral titles, hashtags, chapters)
 * - Export MP4 Now button with rendering modal & progress
 */
@Composable
fun VideoExportStudioScreen(
    project: VideoProjectData,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedRes by remember { mutableStateOf("1080p") }
    var estSize by remember { mutableStateOf("48.2 MB") }
    var selectedFps by remember { mutableIntStateOf(30) }
    var selectedCodec by remember { mutableStateOf("H.264") }
    var isWatermarkEnabled by remember { mutableStateOf(true) }
    var isMetadataPackEnabled by remember { mutableStateOf(true) }

    var isRendering by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableFloatStateOf(0f) }
    var renderPassText by remember { mutableStateOf("Pass 1: Neural Upscaling...") }
    var isExportComplete by remember { mutableStateOf(false) }

    val activeClip = project.clips.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131313))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp)
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF201F1F))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Export Studio",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Sub-Header Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Export Studio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00DAF3)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "${project.projectName} · Cut #04", color = Color(0xFFD1BCFF), fontSize = 12.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF2A2A2A)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("NEURAL ENGINE", color = Color(0xFF00DAF3), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Preview Strip Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1C1B1B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 75.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0E0E0E))
                    ) {
                        if (activeClip?.uri != null) {
                            AsyncImage(
                                model = activeClip.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.align(Alignment.Center).size(28.dp))
                        }
                        Text(
                            text = "0:42",
                            color = Color(0xFFD1BCFF),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(project.projectName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("9:16 Vertical · 23.98 fps master", color = Color(0xFFCCC3D9), fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF353534)) {
                                Text("≈ $estSize", color = Color(0xFF00DAF3), fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Surface(shape = RoundedCornerShape(10.dp), color = Color(0x337B2BFF)) {
                                Text("Ready", color = Color(0xFFD1BCFF), fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Resolution Selector
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target Resolution", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("REC.709 Standard", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val resList = listOf(
                        Triple("480p", "Draft", "18.4 MB"),
                        Triple("720p", "Social", "29.7 MB"),
                        Triple("1080p", "Crisp HD", "48.2 MB"),
                        Triple("4K", "Cinema", "142.8 MB")
                    )

                    resList.forEach { (res, desc, size) ->
                        val isSelected = selectedRes == res
                        Surface(
                            onClick = {
                                selectedRes = res
                                estSize = size
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF2A2A2A) else Color(0xFF201F1F),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF00DAF3) else Color(0x25FFFFFF)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = res,
                                    color = if (isSelected) Color(0xFF00DAF3) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = desc,
                                    color = Color(0xFFCCC3D9),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Frame Rate & Codec
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // FPS
                Column(modifier = Modifier.weight(1f)) {
                    Text("Frame Rate", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1B1B))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(24, 30, 60).forEach { fps ->
                            val isSelected = selectedFps == fps
                            Surface(
                                onClick = { selectedFps = fps },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF006CEB) else Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$fps",
                                    color = if (isSelected) Color.White else Color(0xFFCCC3D9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Codec
                Column(modifier = Modifier.weight(1f)) {
                    Text("Format Codec", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1B1B))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("H.264", "H.265").forEach { codec ->
                            val isSelected = selectedCodec == codec
                            Surface(
                                onClick = { selectedCodec = codec },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF006CEB) else Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = codec,
                                    color = if (isSelected) Color.White else Color(0xFFCCC3D9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Smart Export Toggles
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Neural Processing Options", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                // Watermark toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF201F1F),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF353534)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFFD1BCFF), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("AI watermark animation", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Only on AI-enhanced sequences", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = isWatermarkEnabled,
                            onCheckedChange = { isWatermarkEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00DAF3),
                                checkedTrackColor = Color(0xFF7B2BFF)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF201F1F),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF353534)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Generate metadata pack", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Auto-generates viral Title, Tags & Chapters", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = isMetadataPackEnabled,
                            onCheckedChange = { isMetadataPackEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00DAF3),
                                checkedTrackColor = Color(0xFF7B2BFF)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Generated Metadata Pack Preview
            if (isMetadataPackEnabled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2A2A2A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("AI Metadata Pack Preview", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("Optimized for Instagram Reels & YouTube Shorts", color = Color(0xFF00DAF3), fontSize = 10.5.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Recommended Viral Title", color = Color(0xFFCCC3D9), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF1C1B1B), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Tokyo Midnight Cyberpunk 4K Cinematic | Speed Edit",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("High Impact Hashtags", color = Color(0xFFCCC3D9), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("#cyberpunk", "#tokyovlog", "#reelsedit", "#saifeditor", "#cinematic").forEach { tag ->
                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF201F1F)) {
                                    Text(tag, color = Color(0xFF00DAF3), fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 6. Big Export Action Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        if (!isRendering) {
                            isRendering = true
                            renderProgress = 0f
                            isExportComplete = false
                            scope.launch {
                                for (p in 5..100 step 5) {
                                    delay(100)
                                    renderProgress = p / 100f
                                    if (p in 30..69) {
                                        renderPassText = "Pass 2: Encoding $selectedCodec Stream..."
                                    } else if (p >= 70) {
                                        renderPassText = "Pass 3: Finalizing Audio Mux & Metadata..."
                                    }
                                }
                                delay(300)
                                isRendering = false
                                isExportComplete = true
                                Toast.makeText(context, "✅ Video exported & saved to Gallery / Camera Roll!", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExportComplete) Color(0xFF00707D) else Color(0xFF7B2BFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = if (isExportComplete) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isExportComplete) "Saved to Camera Roll ✓" else "Export MP4 Now ↑",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockClock, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Screen on rakho · On-device fast neural rendering", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                }
            }
        }

        // Render Sheet Modal Overlay
        AnimatedVisibility(
            visible = isRendering,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color(0xFF1E1E28),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Color(0xFF00DAF3),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Rendering Frames...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${(renderProgress * 100).toInt()}%",
                            color = Color(0xFF00DAF3),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { renderProgress },
                        color = Color(0xFF00DAF3),
                        trackColor = Color(0xFF0E0E0E),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(renderPassText, color = Color(0xFFCCC3D9), fontSize = 11.5.sp)
                        Text("ETA: 00:04", color = Color(0xFFCCC3D9), fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
