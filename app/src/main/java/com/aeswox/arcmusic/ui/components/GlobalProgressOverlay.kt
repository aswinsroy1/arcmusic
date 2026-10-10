package com.aeswox.arcmusic.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aeswox.arcmusic.db.MediaScannerManager
import com.aeswox.arcmusic.sharing.ConnectionRequest
import com.aeswox.arcmusic.sharing.NearbySharingManager
import com.aeswox.arcmusic.sharing.SharingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GlobalOverlayViewModel @Inject constructor(
    private val scannerManager: MediaScannerManager,
    private val sharingManager: NearbySharingManager
) : ViewModel() {
    val scanProgress = scannerManager.scanProgress
    val sharingState = sharingManager.sharingState
    val transferProgress = sharingManager.transferProgress
    val completedTransferCount = sharingManager.completedTransferCount
    val totalTransferCount = sharingManager.totalTransferCount
    val currentTransferTitle = sharingManager.currentTransferTitle
    val currentTransferArtworkB64 = sharingManager.currentTransferArtworkB64
    val isReceiving = sharingManager.isReceiving
    val incomingSenderName = sharingManager.incomingSenderName
    val connectionRequest = sharingManager.connectionRequest

    fun acceptConnection(request: ConnectionRequest) = sharingManager.acceptConnection(request.endpointId)
    fun resetSharing() = sharingManager.reset()
}

@Composable
fun GlobalProgressOverlay(
    modifier: Modifier = Modifier,
    currentRoute: String? = null,
    viewModel: GlobalOverlayViewModel = hiltViewModel()
) {
    val scanProgress by viewModel.scanProgress.collectAsState()
    val sharingState by viewModel.sharingState.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val completedTransferCount by viewModel.completedTransferCount.collectAsState()
    val totalTransferCount by viewModel.totalTransferCount.collectAsState()
    val currentTitle by viewModel.currentTransferTitle.collectAsState()
    val artworkB64 by viewModel.currentTransferArtworkB64.collectAsState()
    val isReceiving by viewModel.isReceiving.collectAsState()
    val senderName by viewModel.incomingSenderName.collectAsState()
    val connectionRequest by viewModel.connectionRequest.collectAsState()

    val isScanning = scanProgress.isRunning
    val isTransferring = sharingState == SharingState.TRANSFERRING
    val isReceiveComplete = isReceiving && sharingState == SharingState.COMPLETED
    val hasIncomingActivity = (isReceiving && isTransferring) || isReceiveComplete || connectionRequest != null

    // ReceiveScreen owns its full-screen card. Other routes use this shared overlay.
    val isExcludedRoute = currentRoute == "media_management" ||
        currentRoute == "receive" ||
        currentRoute?.startsWith("share") == true
    val shouldShow = (isScanning || isTransferring || hasIncomingActivity) && !isExcludedRoute
    val canExpandReceive = hasIncomingActivity && connectionRequest == null && !isExcludedRoute
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(canExpandReceive) {
        if (!canExpandReceive) isExpanded = false
    }

    val artwork = remember(artworkB64) {
        artworkB64?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    AnimatedVisibility(
        visible = shouldShow,
        enter = slideInVertically(
            initialOffsetY = { -it - 100 },
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 180f)
        ) + fadeIn(animationSpec = tween(300)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 180f)
        ),
        exit = slideOutVertically(targetOffsetY = { -it - 100 }, animationSpec = tween(250)) +
            fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.8f, animationSpec = tween(250)),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (isExpanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isExpanded = false }
                )
            }

            val cardHeight = 360.dp
            val expandedTop = ((maxHeight - cardHeight) / 2).coerceAtLeast(56.dp)
            val topOffset by animateDpAsState(
                targetValue = if (isExpanded) expandedTop else 48.dp,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 220f),
                label = "receive_overlay_position"
            )
            val cornerRadius by animateDpAsState(
                targetValue = if (isExpanded) 32.dp else 28.dp,
                animationSpec = spring(stiffness = 240f),
                label = "receive_overlay_shape"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topOffset),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = isExpanded && canExpandReceive,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = spring())) togetherWith
                            (fadeOut(tween(120)) + scaleOut(targetScale = 0.96f, animationSpec = tween(120))) using
                            SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.82f, stiffness = 220f) }
                    },
                    label = "progress_pill_to_receive_card"
                ) { expanded ->
                    if (expanded) {
                        IncomingTransferCard(
                            progress = transferProgress,
                            completedCount = completedTransferCount,
                            totalCount = totalTransferCount,
                            title = currentTitle,
                            senderName = senderName,
                            artwork = artwork,
                            completed = isReceiveComplete,
                            cornerRadius = cornerRadius,
                            onDismiss = viewModel::resetSharing
                        )
                    } else {
                        ProgressPill(
                            isTransferring = isTransferring,
                            isScanning = isScanning,
                            scanTitle = scanProgress.phase.label,
                            scanCurrent = scanProgress.current,
                            scanTotal = scanProgress.total,
                            progress = transferProgress,
                            completedCount = completedTransferCount,
                            totalCount = totalTransferCount,
                            title = currentTitle,
                            completed = isReceiveComplete,
                            request = connectionRequest,
                            canExpandReceive = canExpandReceive,
                            onExpand = { isExpanded = true },
                            onAccept = viewModel::acceptConnection
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressPill(
    isTransferring: Boolean,
    isScanning: Boolean,
    scanTitle: String,
    scanCurrent: Int,
    scanTotal: Int,
    progress: Float,
    completedCount: Int,
    totalCount: Int,
    title: String?,
    completed: Boolean,
    request: ConnectionRequest?,
    canExpandReceive: Boolean,
    onExpand: () -> Unit,
    onAccept: (ConnectionRequest) -> Unit
) {
    val pillClick = if (canExpandReceive) Modifier.clickable(onClick = onExpand) else Modifier
    Surface(
        modifier = pillClick
            .shadow(16.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.15f))
            .clip(CircleShape),
        color = Color.White,
        contentColor = Color.Black,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                request != null -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Column {
                        Text(
                            "${request.endpointName} wants to send",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { onAccept(request) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("Accept")
                        }
                    }
                }
                completed -> {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Music received", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Tap to view details", style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.7f))
                    }
                }
                isTransferring -> {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Column {
                        Text(
                            if (title.isNullOrBlank()) "Receiving files…" else "Receiving ${title}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (totalCount > 0) "$completedCount of $totalCount files" else "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }
                isScanning -> {
                    CircularProgressIndicator(
                        progress = { scanCurrent.toFloat() / scanTotal.coerceAtLeast(1) },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Column {
                        Text(scanTitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        if (scanTotal > 0) {
                            Text("$scanCurrent of $scanTotal tracks", style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.7f))
                        }
                    }
                }
                else -> {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Column {
                        Text("Transferring Media", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            if (totalCount > 0) "$completedCount of $totalCount files" else "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingTransferCard(
    progress: Float,
    completedCount: Int,
    totalCount: Int,
    title: String?,
    senderName: String?,
    artwork: androidx.compose.ui.graphics.ImageBitmap?,
    completed: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "incoming_transfer_progress"
    )
    Surface(
        modifier = Modifier
            .width(300.dp)
            .height(360.dp)
            .shadow(20.dp, RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(Modifier.fillMaxSize()) {
            if (completed && artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (completed) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF1E88E5), modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Music received from ${senderName ?: "Device"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (artwork != null) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                } else {
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        if (artwork != null) {
                            Image(
                                bitmap = artwork,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Box(Modifier.size(120.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)))
                        } else {
                            Box(
                                Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
                            }
                        }
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 6.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            "${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (artwork != null) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        if (totalCount > 1) "Receiving ${completedCount + 1} of $totalCount…" else "Receiving…",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (totalCount > 1) "Multiple items" else title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
