package com.aeswox.arcmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeswox.arcmusic.ui.animations.jellyClick
import com.aeswox.arcmusic.ui.components.HugeIcons
import androidx.compose.material3.Icon
import com.aeswox.arcmusic.ui.components.JellyIconButton

import com.aeswox.arcmusic.ui.components.CustomHorizontalSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(
    onNavigateBack: () -> Unit,
    onScanMediaStore: () -> Unit,
    onTestEac3: () -> Unit,
    onClearScanLog: () -> Unit,
    onExportScanLog: () -> Unit,
    tintTransparency: Float,
    noiseFactor: Float,
    glowIntensity: Float,
    onTintTransparencyChange: (Float) -> Unit,
    onNoiseFactorChange: (Float) -> Unit,
    onGlowIntensityChange: (Float) -> Unit,
    // Jiggle Physics
    physicsMass: Float,
    physicsStiffness: Float,
    physicsDampingRatio: Float,
    physicsAmplitude: Float,
    physicsGravity: Float,
    onPhysicsMassChange: (Float) -> Unit,
    onPhysicsStiffnessChange: (Float) -> Unit,
    onPhysicsDampingRatioChange: (Float) -> Unit,
    onPhysicsAmplitudeChange: (Float) -> Unit,
    onPhysicsGravityChange: (Float) -> Unit,
    // Wave Properties
    baselineHeight: Float,
    onBaselineHeightChange: (Float) -> Unit,
    waveMaxAmp: Float,
    onWaveMaxAmpChange: (Float) -> Unit,
    cycleLength: Float,
    onCycleLengthChange: (Float) -> Unit,
    shadowOffset: Float,
    onShadowOffsetChange: (Float) -> Unit,
    shadowOpacity: Float,
    onShadowOpacityChange: (Float) -> Unit,
    primaryOpacity: Float,
    onPrimaryOpacityChange: (Float) -> Unit,
    thumbRadius: Float,
    onThumbRadiusChange: (Float) -> Unit,
    unplayedStroke: Float,
    onUnplayedStrokeChange: (Float) -> Unit,
    bloomDuration: Float,
    onBloomDurationChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Developer Options", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    JellyIconButton(onClick = onNavigateBack) {
                        Icon(HugeIcons.ArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Glass Properties
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Glass Tint Transparency: ${String.format("%.2f", tintTransparency)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    CustomHorizontalSlider(
                        value = tintTransparency,
                        onValueChange = onTintTransparencyChange,
                        valueRange = 0.0f..0.8f
                    )
                }
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Monochromatic Noise Factor: ${String.format("%.2f", noiseFactor)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    CustomHorizontalSlider(
                        value = noiseFactor,
                        onValueChange = onNoiseFactorChange,
                        valueRange = 0.0f..0.12f
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Background Elements
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Glow Intensity: ${String.format("%.2f", glowIntensity)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    CustomHorizontalSlider(
                        value = glowIntensity,
                        onValueChange = onGlowIntensityChange,
                        valueRange = 0.0f..1.0f
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Jiggle Physics
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Text(
                    text = "Jiggle Physics",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Mass: ${String.format("%.2f", physicsMass)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = physicsMass, onValueChange = onPhysicsMassChange, valueRange = 0.1f..1.0f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Stiffness: ${String.format("%.2f", physicsStiffness)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = physicsStiffness, onValueChange = onPhysicsStiffnessChange, valueRange = 10.0f..200.0f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Damping Ratio: ${String.format("%.2f", physicsDampingRatio)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = physicsDampingRatio, onValueChange = onPhysicsDampingRatioChange, valueRange = 0.1f..0.5f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Impact Amplitude: ${String.format("%.2f", physicsAmplitude)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = physicsAmplitude, onValueChange = onPhysicsAmplitudeChange, valueRange = 0.1f..2.0f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Gravity: ${String.format("%.2f", physicsGravity)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = physicsGravity, onValueChange = onPhysicsGravityChange, valueRange = 0.1f..20.0f)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Wave Properties
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Text(
                    text = "Wave Properties",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Baseline Height (dp): ${String.format("%.2f", baselineHeight)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = baselineHeight, onValueChange = onBaselineHeightChange, valueRange = 0f..15f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Wave Max Amplitude (dp): ${String.format("%.2f", waveMaxAmp)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = waveMaxAmp, onValueChange = onWaveMaxAmpChange, valueRange = 0f..10f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Cycle Length (dp): ${String.format("%.2f", cycleLength)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = cycleLength, onValueChange = onCycleLengthChange, valueRange = 20f..200f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Shadow Offset (rad): ${String.format("%.2f", shadowOffset)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = shadowOffset, onValueChange = onShadowOffsetChange, valueRange = 0f..6.28f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Shadow Opacity: ${String.format("%.2f", shadowOpacity)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = shadowOpacity, onValueChange = onShadowOpacityChange, valueRange = 0f..1f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Primary Wave Opacity: ${String.format("%.2f", primaryOpacity)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = primaryOpacity, onValueChange = onPrimaryOpacityChange, valueRange = 0f..1f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Thumb Radius (dp): ${String.format("%.2f", thumbRadius)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = thumbRadius, onValueChange = onThumbRadiusChange, valueRange = 0f..15f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Unplayed Stroke (dp): ${String.format("%.2f", unplayedStroke)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = unplayedStroke, onValueChange = onUnplayedStrokeChange, valueRange = 1f..10f)
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Bloom Duration (ms): ${String.format("%.2f", bloomDuration)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    CustomHorizontalSlider(value = bloomDuration, onValueChange = onBloomDurationChange, valueRange = 100f..2000f)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            DeveloperCard(
                title = "Scan MediaStore",
                subtitle = "Force manual library scan",
                icon = Icons.Outlined.Storage,
                onClick = onScanMediaStore
            )
            Spacer(modifier = Modifier.height(12.dp))

            DeveloperCard(
                title = "Test EAC3 Playback",
                subtitle = "Test EAC3 Audio Format",
                icon = Icons.Outlined.GraphicEq,
                onClick = onTestEac3
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Diagnostics Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(20.dp),
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Scan Diagnostics Log",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Share with developer to diagnose rescan issues",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onClearScanLog,
                            modifier = Modifier.jellyClick { onClearScanLog() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Clear Logs", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onExportScanLog,
                            modifier = Modifier.jellyClick { onExportScanLog() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Export Logs", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DeveloperCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
            )
            .jellyClick { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
