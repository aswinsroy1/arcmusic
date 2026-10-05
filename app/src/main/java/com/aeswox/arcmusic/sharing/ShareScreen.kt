package com.aeswox.arcmusic.sharing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.aeswox.arcmusic.shimmerLoading
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeswox.arcmusic.AppCornerRadius
import com.aeswox.arcmusic.AnimatedGlowBackground
import com.aeswox.arcmusic.ui.animations.jellyClick
import com.aeswox.arcmusic.ui.animations.physicsBounceOverscroll
import com.aeswox.arcmusic.ui.components.HugeIcons
import com.aeswox.arcmusic.ui.components.JellyIconButton
import kotlin.math.*

@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun ShareScreen(
    viewModel: ShareViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onExternalShareClick: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp
) {
    val sharingState by viewModel.sharingState.collectAsState()
    val discoveredEndpoints by viewModel.discoveredEndpoints.collectAsState()
    val connectionRequest by viewModel.connectionRequest.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val totalTransferCount by viewModel.totalTransferCount.collectAsState()
    val completedTransferCount by viewModel.completedTransferCount.collectAsState()
    val context = LocalContext.current

    // Connection request dialog
    connectionRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { viewModel.rejectConnection(request.endpointId) },
            title = { Text("Connection Request") },
            text = {
                Text(
                    "Accept connection to ${request.endpointName}?\n\nVerify that the PIN ${request.authCode} " +
                    "matches the PIN shown on the other device before accepting."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.acceptConnection(request.endpointId) }) { Text("Accept") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectConnection(request.endpointId) }) { Text("Reject") }
            }
        )
    }

    // Permissions
    val permissionsList = buildPermissionsList()
    val permissionsState = com.google.accompanist.permissions.rememberMultiplePermissionsState(permissions = permissionsList)

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) permissionsState.launchMultiplePermissionRequest()
    }

    DisposableEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.startAdvertising()
            viewModel.startDiscovery()
        }
        onDispose {
            com.aeswox.arcmusic.sharing.NfcShareService.currentToken = null
            viewModel.stopAdvertising()
            viewModel.stopDiscovery()
        }
    }

    val payloadLabel by viewModel.payloadDisplayLabel.collectAsState(
        initial = SharePayloadDisplayInfo("Preparing to send", "Gathering items...")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .physicsBounceOverscroll()
                .padding(bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                JellyIconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = HugeIcons.ArrowLeft,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Send Music",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
            }

            // ── What we're sending ───────────────────────────────────
            SharePayloadInfoCard(payloadLabel = payloadLabel, modifier = Modifier.padding(horizontal = 24.dp))

            Spacer(modifier = Modifier.height(16.dp))

            // ── Section label ────────────────────────────────────────
            Text(
                text = "Share to devices nearby",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )

            // ── Device discovery card ────────────────────────────────
            DeviceDiscoveryCard(
                sharingState = sharingState,
                discoveredEndpoints = discoveredEndpoints,
                transferProgress = transferProgress,
                totalTransferCount = totalTransferCount,
                completedTransferCount = completedTransferCount,
                onEndpointClick = { viewModel.requestConnection(it) },
                onCancelTransfer = { viewModel.cancelTransfer() },
                onNfcTokenChanged = { token ->
                    com.aeswox.arcmusic.sharing.NfcShareService.currentToken = token
                    viewModel.stopAdvertising()
                    viewModel.startAdvertising(token)
                },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── External share option ────────────────────────────────
            ExternalShareCard(
                onClick = { viewModel.prepareExternalShare(context) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── What's being sent card ───────────────────────────────────────────────────

@Composable
private fun SharePayloadInfoCard(payloadLabel: SharePayloadDisplayInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Artwork or Icon
            if (payloadLabel.imagePath != null) {
                coil.compose.AsyncImage(
                    model = payloadLabel.imagePath,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = payloadLabel.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = payloadLabel.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ── Radial device discovery area ─────────────────────────────────────────────

@Composable
private fun DeviceDiscoveryCard(
    sharingState: SharingState,
    discoveredEndpoints: List<DiscoveredEndpoint>,
    transferProgress: Float,
    totalTransferCount: Int,
    completedTransferCount: Int,
    onEndpointClick: (String) -> Unit,
    onCancelTransfer: () -> Unit,
    onNfcTokenChanged: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("sharing_prefs", android.content.Context.MODE_PRIVATE) }
    
    val hasNfc = remember(context) {
        val nfcManager = context.getSystemService(android.content.Context.NFC_SERVICE) as? android.nfc.NfcManager
        nfcManager?.defaultAdapter != null
    }
    
    // Default to false if device has no NFC, even if prefs said true previously
    var isNfcEnabled by remember(hasNfc) { 
        mutableStateOf(hasNfc && sharedPrefs.getBoolean("is_nfc_enabled", false)) 
    }
    
    LaunchedEffect(isNfcEnabled) {
        if (isNfcEnabled) {
            val token = java.util.UUID.randomUUID().toString().take(8)
            onNfcTokenChanged(token)
        } else {
            onNfcTokenChanged(null)
        }
    }

    // Scan-ring pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_alpha"
    )
    val scanRadius by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_radius"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = surfaceHigh.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // QR code or link pill (Placeholder)
            if (hasNfc) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    val nfcBgColor by animateColorAsState(
                        targetValue = if (isNfcEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        label = "nfcBgColor"
                    )
                    val nfcContentColor by animateColorAsState(
                        targetValue = if (isNfcEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        label = "nfcContentColor"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(nfcBgColor)
                            .clickable { 
                                isNfcEnabled = !isNfcEnabled 
                                sharedPrefs.edit().putBoolean("is_nfc_enabled", isNfcEnabled).apply()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Smartphone,
                                contentDescription = "Share via NFC",
                                tint = nfcContentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tap to share",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = nfcContentColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Devices / Skeleton Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = if (discoveredEndpoints.isEmpty()) Arrangement.SpaceAround else Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sharingState == SharingState.ERROR) {
                    // Empty when error, handled below
                } else if (sharingState == SharingState.TRANSFERRING) {
                    // Show the connected device with progress arc
                    TransferringView(
                        progress = transferProgress,
                        totalCount = totalTransferCount,
                        completedCount = completedTransferCount,
                        onCancel = onCancelTransfer
                    )
                } else if (discoveredEndpoints.isNotEmpty()) {
                    discoveredEndpoints.forEach { endpoint ->
                        DeviceAvatarButton(
                            endpoint = endpoint,
                            progress = 0f,
                            isTransferring = false,
                            onClick = { onEndpointClick(endpoint.id) }
                        )
                    }
                } else {
                    // Skeleton loading items
                    repeat(4) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                                    .shimmerLoading()
                            )
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                                    .shimmerLoading()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Footer text
            Text(
                text = "Ensure the other device is on the Receive screen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Error retry row
        if (sharingState == SharingState.ERROR) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "An error occurred",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .jellyClick { /* Retry logic passed from viewmodel normally */ }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Retry",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}


/**
 * Shows a single device avatar with a progress arc around its circumference
 * when a transfer is active. Shown in the centre of the discovery area during transfer.
 */
@Composable
private fun TransferringView(
    progress: Float,
    totalCount: Int,
    completedCount: Int,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.weight(1f))
        val text = if (totalCount > 1) "Sending ${completedCount + 1} of $totalCount..." else "Sending…"
        DeviceAvatarButton(
            endpoint = DiscoveredEndpoint(id = "", name = text),
            progress = progress,
            isTransferring = true,
            onClick = {}
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .jellyClick(onClick = onCancel)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * A circular avatar with an animated progress arc on its border.
 * While [isTransferring] is true the arc sweeps from 0→360° as [progress] 0→1.
 */
@Composable
private fun DeviceAvatarButton(
    endpoint: DiscoveredEndpoint,
    progress: Float,
    isTransferring: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "avatar_progress"
    )

    // Pulse animation while waiting for a tap (not transferring)
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (!isTransferring) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_pulse_scale"
    )

    val initial = if (endpoint.name.isNotEmpty()) endpoint.name.first().uppercaseChar() else '?'

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .drawBehind {
                    val strokeWidth = 3.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcRect = Size(size.width - strokeWidth, size.height - strokeWidth)

                    if (isTransferring) {
                        // Track ring
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcRect,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        // Progress arc
                        if (animatedProgress > 0f) {
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcRect,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .jellyClick(scaleDownTo = 0.88f, onClick = onClick)
        ) {
            Text(
                text = initial.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        if (!isTransferring) {
            Text(
                text = endpoint.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 80.dp)
            )
        }
    }
}

// ── External share card ───────────────────────────────────────────────────────

@Composable
private fun ExternalShareCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .jellyClick(scaleDownTo = 0.97f, onClick = onClick),
        shape = RoundedCornerShape(AppCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = HugeIcons.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Share to external app",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Open in any app on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Permission helper ─────────────────────────────────────────────────────────

@Composable
private fun buildPermissionsList(): List<String> {
    val list = mutableListOf<String>()
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        list += listOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        list += listOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        list += listOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    return list
}
