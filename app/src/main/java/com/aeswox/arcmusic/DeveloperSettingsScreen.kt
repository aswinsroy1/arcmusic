package com.aeswox.arcmusic

import androidx.compose.foundation.background
import com.aeswox.arcmusic.ui.animations.physicsBounceOverscroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RestartAlt
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
    coilDiskCacheLimitMb: Int,
    onTintTransparencyChange: (Float) -> Unit,
    onNoiseFactorChange: (Float) -> Unit,
    onGlowIntensityChange: (Float) -> Unit,
    onCoilDiskCacheLimitMbChange: (Int) -> Unit,
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
    overrideFontScaleEnabled: Boolean = false,
    onOverrideFontScaleEnabledChange: (Boolean) -> Unit = {},
    fontScale: Float = 1.0f,
    onFontScaleChange: (Float) -> Unit = {},
    immersiveModeEnabled: Boolean = false,
    onImmersiveModeEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
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
                .physicsBounceOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // UI Experiments
            ExpandableSettingsCard(
                title = "UI Experiments",
                onReset = {
                    onImmersiveModeEnabledChange(false)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Immersive Mode",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = immersiveModeEnabled,
                        onCheckedChange = { onImmersiveModeEnabledChange(it) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Accessibility
            ExpandableSettingsCard(
                title = "Accessibility (Experimental)",
                onReset = {
                    onOverrideFontScaleEnabledChange(false)
                    onFontScaleChange(1.0f)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Override System Font Scale",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = overrideFontScaleEnabled,
                        onCheckedChange = { onOverrideFontScaleEnabledChange(it) }
                    )
                }
                
                if (overrideFontScaleEnabled) {
                    SliderRow(
                        label = "Font Scale",
                        value = fontScale,
                        onValueChange = onFontScaleChange,
                        valueRange = 0.7f..1.3f
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Glass & Glow Engine
            ExpandableSettingsCard(
                title = "Glass & Glow Engine",
                onReset = {
                    onTintTransparencyChange(0.40f)
                    onNoiseFactorChange(0.06f)
                    onGlowIntensityChange(0.38f)
                }
            ) {
                SliderRow("Glass Tint Transparency", tintTransparency, onTintTransparencyChange, 0.0f..0.8f)
                SliderRow("Monochromatic Noise Factor", noiseFactor, onNoiseFactorChange, 0.0f..0.12f)
                SliderRow("Glow Intensity", glowIntensity, onGlowIntensityChange, 0.0f..1.0f)
            }
            Spacer(modifier = Modifier.height(12.dp))



            // Jiggle Physics
            ExpandableSettingsCard(
                title = "Jiggle Physics",
                onReset = {
                    onPhysicsMassChange(0.2f)
                    onPhysicsStiffnessChange(100.0f)
                    onPhysicsDampingRatioChange(0.25f)
                    onPhysicsAmplitudeChange(1.0f)
                    onPhysicsGravityChange(9.81f)
                }
            ) {
                SliderRow("Mass", physicsMass, onPhysicsMassChange, 0.1f..1.0f)
                SliderRow("Stiffness", physicsStiffness, onPhysicsStiffnessChange, 10.0f..200.0f)
                SliderRow("Damping Ratio", physicsDampingRatio, onPhysicsDampingRatioChange, 0.1f..0.5f)
                SliderRow("Impact Amplitude", physicsAmplitude, onPhysicsAmplitudeChange, 0.1f..2.0f)
                SliderRow("Gravity", physicsGravity, onPhysicsGravityChange, 0.1f..20.0f)
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Wave Properties
            ExpandableSettingsCard(
                title = "Wave Properties",
                onReset = {
                    onBaselineHeightChange(5.75f)
                    onWaveMaxAmpChange(8.81f)
                    onCycleLengthChange(126.41f)
                    onShadowOffsetChange(2.42f)
                    onShadowOpacityChange(0.50f)
                    onPrimaryOpacityChange(0.92f)
                    onThumbRadiusChange(7.00f)
                    onUnplayedStrokeChange(5.75f)
                    onBloomDurationChange(600f)
                }
            ) {
                SliderRow("Baseline Height (dp)", baselineHeight, onBaselineHeightChange, 0f..15f)
                SliderRow("Wave Max Amplitude (dp)", waveMaxAmp, onWaveMaxAmpChange, 0f..15f)
                SliderRow("Cycle Length (dp)", cycleLength, onCycleLengthChange, 20f..200f)
                SliderRow("Shadow Offset (rad)", shadowOffset, onShadowOffsetChange, 0f..6.28f)
                SliderRow("Shadow Opacity", shadowOpacity, onShadowOpacityChange, 0f..1f)
                SliderRow("Primary Wave Opacity", primaryOpacity, onPrimaryOpacityChange, 0f..1f)
                SliderRow("Thumb Radius (dp)", thumbRadius, onThumbRadiusChange, 0f..15f)
                SliderRow("Unplayed Stroke (dp)", unplayedStroke, onUnplayedStrokeChange, 1f..10f)
                SliderRow("Bloom Duration (ms)", bloomDuration, onBloomDurationChange, 100f..2000f)
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Data & Storage
            ExpandableSettingsCard(
                title = "Data & Storage",
                onReset = {
                    onCoilDiskCacheLimitMbChange(500)
                }
            ) {
                SliderRow(
                    label = "Image Cache Limit",
                    value = coilDiskCacheLimitMb.toFloat(),
                    onValueChange = { onCoilDiskCacheLimitMbChange(it.toInt()) },
                    valueRange = 250f..5000f,
                    format = "%.0f MB"
                )
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
            GlassCard(
                modifier = Modifier.fillMaxWidth()
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
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .jellyClick { onClick() }
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

@Composable
private fun SliderRow(label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, format: String = "%.2f") {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = String.format(format, value), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        CustomHorizontalSlider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun ExpandableSettingsCard(
    title: String,
    onReset: () -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                JellyIconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Outlined.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}
