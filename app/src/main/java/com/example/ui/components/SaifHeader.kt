

package com.example.ui.components

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LiquidGlassDefaults
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.liquidBounceClickable
import kotlinx.coroutines.launch

@OptIn(ExperimentalTextApi::class)
@Composable
fun SaifHeader(
    currentMode: String,
    isLightMode: Boolean,
    onToggleTheme: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenModeSelector: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlusClick: () -> Unit,
    isFilterBookmarked: Boolean,
    onToggleBookmarkFilter: () -> Unit,
    onUpgradeClick: () -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Flowing liquid iridescent text gradient
    val infiniteTransition = rememberInfiniteTransition(label = "saif_title_liquid")
    val titlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "titlePhase"
    )

    val saifGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF8B5CF6), // Purple
            Color(0xFF3B82F6), // Blue
            Color(0xFF06B6D4), // Cyan
            Color(0xFFEC4899), // Pink
            Color(0xFF8B5CF6)  // Wrap
        ),
        startX = titlePhase,
        endX = titlePhase + 600f
    )

    // Bouncy "water drop" animation state for the menu icon
    val interactionSourceMenu = remember { MutableInteractionSource() }
    val isMenuPressed by interactionSourceMenu.collectIsPressedAsState()
    val scaleMenu by animateFloatAsState(
        targetValue = if (isMenuPressed) 0.72f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "WaterDropBounce"
    )

    // Liquid glide animation states for Upgrade button
    val upgradeScaleX = remember { Animatable(1f) }
    val upgradeScaleY = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0f) }
    val glowScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Status bar safe area padding + floating margin so header NEVER touches time/battery/cutout
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)
    ) {
        // Floating Header Capsule Bar
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF131826),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
            ),
            shadowElevation = if (isLightMode) 2.dp else 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Drawer Menu & Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Water drop bouncing icon wrapper
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .scale(scaleMenu)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSourceMenu,
                                indication = androidx.compose.foundation.LocalIndication.current,
                                onClick = onOpenDrawer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = SaifTheme.colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "SAIF AI",
                        style = TextStyle(
                            brush = saifGradient,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                // Right side: Admin Crown, Upgrade, Theme Toggle, Settings, Add
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Admin Crown Button (Shows only for Admin role)
                    val currentAuthUser by com.example.data.local.AuthManager.currentUser.collectAsState()
                    if (currentAuthUser?.isAdmin == true) {
                        Surface(
                            onClick = onOpenAdminDashboard,
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0x33F59E0B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.8f)),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = "👑", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Admin",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Upgrade Pill with Liquid Glide Animation
                    Box(contentAlignment = Alignment.Center) {
                        // Explosion Glow
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .scale(glowScale.value)
                                .background(Color(0xFFA855F7).copy(alpha = glowAlpha.value), RoundedCornerShape(18.dp))
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isLightMode) Color(0x20A855F7) else Color(0x22A855F7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66A855F7)),
                            modifier = Modifier
                                .scale(upgradeScaleX.value, upgradeScaleY.value)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    scope.launch {
                                        // Glow blast
                                        launch {
                                            glowAlpha.snapTo(0.6f)
                                            glowScale.snapTo(1f)
                                            launch { glowScale.animateTo(1.6f, animationSpec = tween(400, easing = FastOutSlowInEasing)) }
                                            glowAlpha.animateTo(0f, animationSpec = tween(400))
                                        }

                                        // Liquid glide (Squish horizontally, then bounce back)
                                        launch { upgradeScaleX.animateTo(1.12f, animationSpec = tween(150, easing = FastOutSlowInEasing)) }
                                        upgradeScaleY.animateTo(0.85f, animationSpec = tween(150, easing = FastOutSlowInEasing))

                                        launch { upgradeScaleX.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                        upgradeScaleY.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))

                                        onUpgradeClick()
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = "Upgrade",
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Upgrade",
                                    color = Color(0xFFA855F7),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Theme Icon Animation
                    val themeInteractionSource = remember { MutableInteractionSource() }
                    val isThemePressed by themeInteractionSource.collectIsPressedAsState()

                    val themeScale by animateFloatAsState(
                        targetValue = if (isThemePressed) 0.65f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "ThemeScale"
                    )

                    val themeRotation by animateFloatAsState(
                        targetValue = if (isLightMode) 180f else 0f,
                        animationSpec = tween(700, easing = FastOutSlowInEasing),
                        label = "ThemeRotation"
                    )

                    IconButton(
                        onClick = onToggleTheme,
                        interactionSource = themeInteractionSource,
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer {
                                scaleX = themeScale
                                scaleY = themeScale
                                rotationZ = themeRotation
                            }
                    ) {
                        AnimatedContent(
                            targetState = isLightMode,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                            label = "ThemeIcon"
                        ) { light ->
                            Icon(
                                imageVector = if (light) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = "Theme",
                                tint = SaifTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Settings Button
                    val settingsInteraction = remember { MutableInteractionSource() }
                    val isSettingsPressed by settingsInteraction.collectIsPressedAsState()

                    val settingsScale by animateFloatAsState(
                        targetValue = if (isSettingsPressed) 0.72f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
                        label = "SettingsScale"
                    )
                    val settingsRotation by animateFloatAsState(
                        targetValue = if (isSettingsPressed) 90f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "SettingsRotation"
                    )

                    IconButton(
                        onClick = onOpenSettings,
                        interactionSource = settingsInteraction,
                        modifier = Modifier
                            .size(40.dp)
                            .scale(settingsScale)
                            .graphicsLayer {
                                rotationZ = settingsRotation
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = SaifTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Plus / Project Builder Button (Normal prominent size)
                    val plusInteraction = remember { MutableInteractionSource() }
                    val isPlusPressed by plusInteraction.collectIsPressedAsState()
                    val plusScale by animateFloatAsState(
                        targetValue = if (isPlusPressed) 0.82f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
                        label = "PlusScale"
                    )
                    IconButton(
                        onClick = onPlusClick,
                        interactionSource = plusInteraction,
                        modifier = Modifier
                            .size(44.dp)
                            .scale(plusScale)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x22F97316),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.5f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Configure Project",
                                    tint = Color(0xFFF97316),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

