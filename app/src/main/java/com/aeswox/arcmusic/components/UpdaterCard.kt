package com.aeswox.arcmusic.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeswox.arcmusic.updater.DownloadState
import com.aeswox.arcmusic.GlassCard
import com.aeswox.arcmusic.ui.animations.jellyClick

import com.aeswox.arcmusic.updater.UpdateResult

@Composable
fun UpdaterOverlay(
    isVisible: Boolean,
    updateResult: UpdateResult,
    downloadState: DownloadState,
    onDismiss: () -> Unit,
    onUpdateClick: (UpdateResult.UpdateAvailable) -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (downloadState == DownloadState.Idle) {
                        onDismiss()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(400)
                ) + fadeIn(tween(400)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 4 },
                    animationSpec = tween(300)
                ) + fadeOut(tween(300))
            ) {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // Consume clicks inside the card bounds
                ) {
                    UpdaterCard(
                        updateResult = updateResult,
                        downloadState = downloadState,
                        onDismiss = onDismiss,
                        onUpdateClick = onUpdateClick
                    )
                }
            }
        }
    }
}

@Composable
fun UpdaterCard(
    updateResult: UpdateResult,
    downloadState: DownloadState,
    onDismiss: () -> Unit,
    onUpdateClick: (UpdateResult.UpdateAvailable) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .heightIn(max = 500.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.aeswox.arcmusic.AppCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            when (updateResult) {
                is UpdateResult.Checking -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Checking for updates...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is UpdateResult.NoUpdate -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = "You're Up to Date!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You are currently running the latest version of Arc Music.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End).jellyClick { onDismiss() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Awesome")
                        }
                    }
                }
                is UpdateResult.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = "Update Check Failed",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateResult.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End).jellyClick { onDismiss() }
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
                is UpdateResult.UpdateAvailable -> {
                    Text(
                        text = "Update Available!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Version ${updateResult.version} is ready to install.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = parseMarkdown(cleanChangelog(updateResult.changelog)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (downloadState) {
                            is DownloadState.Idle -> {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.jellyClick { onDismiss() }
                                ) {
                                    Text("Later")
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = { onUpdateClick(updateResult) },
                                    modifier = Modifier.jellyClick { onUpdateClick(updateResult) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("Update", fontWeight = FontWeight.Bold)
                                }
                            }
                            is DownloadState.Downloading -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    LinearProgressIndicator(
                                        progress = { downloadState.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Downloading... ${(downloadState.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is DownloadState.ReadyToInstall -> {
                                Text(
                                    text = "Ready to install!",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun cleanChangelog(raw: String): String {
    val packageIndex = raw.indexOf("### \uD83D\uDCE6 Packages")
    if (packageIndex != -1) {
        return raw.substring(0, packageIndex).trim()
    }
    val fallbackIndex = raw.indexOf("### Packages")
    if (fallbackIndex != -1) {
        return raw.substring(0, fallbackIndex).trim()
    }
    return raw.trim()
}

fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        
        val matches = boldRegex.findAll(text)
        
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            
            append(text.substring(currentIndex, start))
            
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            
            currentIndex = end
        }
        
        append(text.substring(currentIndex))
    }
}
