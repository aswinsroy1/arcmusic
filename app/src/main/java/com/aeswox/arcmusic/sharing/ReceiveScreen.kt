package com.aeswox.arcmusic.sharing

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeswox.arcmusic.AnimatedGlowBackground
import com.aeswox.arcmusic.AppCornerRadius
import com.aeswox.arcmusic.ui.animations.jellyClick
import com.aeswox.arcmusic.ui.components.HugeIcons
import com.aeswox.arcmusic.ui.components.JellyIconButton

@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun ReceiveScreen(
    viewModel: ShareViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val connectionRequest by viewModel.connectionRequest.collectAsState()
    val sharingState by viewModel.sharingState.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val totalTransferCount by viewModel.totalTransferCount.collectAsState()
    val completedTransferCount by viewModel.completedTransferCount.collectAsState()
    val currentTitle by viewModel.currentTransferTitle.collectAsState()
    val currentArtworkB64 by viewModel.currentTransferArtworkB64.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Custom connection request overlay is built into the bottom of the screen

    var showRenameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(viewModel.userName) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Device") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    singleLine = true,
                    label = { Text("Device Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    if (tempName.isNotBlank()) {
                        viewModel.userName = tempName.trim()
                    }
                    showRenameDialog = false 
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Permissions ──────────────────────────────────────────────────────────
    val permissionsList = mutableListOf<String>()
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        permissionsList += listOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        permissionsList += listOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        permissionsList += listOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
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
            viewModel.stopAdvertising()
            viewModel.stopDiscovery()
        }
    }
    
    val nfcAdapter = remember(context) {
        (context.getSystemService(android.content.Context.NFC_SERVICE) as? android.nfc.NfcManager)?.defaultAdapter
    }
    
    DisposableEffect(nfcAdapter) {
        if (nfcAdapter != null) {
            var currentContext = context
            var activity: android.app.Activity? = null
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is android.app.Activity) {
                    activity = currentContext
                    break
                }
                currentContext = currentContext.baseContext
            }
            
            if (activity != null) {
                val flags = android.nfc.NfcAdapter.FLAG_READER_NFC_A or android.nfc.NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                val callback = android.nfc.NfcAdapter.ReaderCallback { tag ->
                    try {
                        val isoDep = android.nfc.tech.IsoDep.get(tag)
                        isoDep?.connect()
                        val selectApdu = byteArrayOf(
                            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
                            0x07.toByte(),
                            0xF0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                            0x00.toByte()
                        )
                        val response = isoDep?.transceive(selectApdu)
                        if (response != null && response.size >= 2) {
                            val sw1 = response[response.size - 2]
                            val sw2 = response[response.size - 1]
                            if (sw1 == 0x90.toByte() && sw2 == 0x00.toByte()) {
                                val tokenBytes = response.copyOfRange(0, response.size - 2)
                                val token = String(tokenBytes, Charsets.UTF_8)
                                viewModel.connectViaNfcToken(token)
                            }
                        }
                        isoDep?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                nfcAdapter.enableReaderMode(activity, callback, flags, null)
            }
        }
        onDispose {
            var currentContext = context
            var activity: android.app.Activity? = null
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is android.app.Activity) {
                    activity = currentContext
                    break
                }
                currentContext = currentContext.baseContext
            }
            if (nfcAdapter != null && activity != null) {
                nfcAdapter.disableReaderMode(activity)
            }
        }
    }

    val shouldShowCard = connectionRequest != null ||
        sharingState == SharingState.CONNECTED ||
        sharingState == SharingState.TRANSFERRING ||
        sharingState == SharingState.COMPLETED
        
    val bgAlpha by androidx.compose.animation.core.animateFloatAsState(targetValue = if (shouldShowCard) 0f else 1f, label = "bgAlpha")

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Header ───────────────────────────────────────────────────────
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
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Device Name Pill
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { 
                            tempName = viewModel.userName
                            showRenameDialog = true 
                        },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = viewModel.userName,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Empty spacer to balance the back button
                Spacer(modifier = Modifier.size(42.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Background Status ───────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(alpha = bgAlpha)
            ) {
                Text(
                    text = "Ready to receive...",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Anyone nearby running Arc Music\ncan share with you while you're on this screen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(64.dp))
        }
        
        // ── Custom Persistent Overlay Card ─────────────────────────────────
        var visibleRequest by remember { mutableStateOf<com.aeswox.arcmusic.sharing.ConnectionRequest?>(null) }
        
        LaunchedEffect(connectionRequest) {
            if (connectionRequest != null) {
                visibleRequest = connectionRequest
            }
        }
        
        // Pre-parse the bitmap so it can be used for the background of the completed state
        val bitmap = remember(currentArtworkB64) {
            currentArtworkB64?.let { b64 ->
                try {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                        it.asImageBitmap()
                    }
                } catch (e: Exception) { null }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = shouldShowCard,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) + androidx.compose.animation.slideInVertically(
                initialOffsetY = { it / 4 }, 
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
            ),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it / 4 }, 
                animationSpec = androidx.compose.animation.core.tween(200)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept clicks to avoid dismissing
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Determine morphing properties
                val isCompleted = sharingState == SharingState.COMPLETED
                val cardWidth by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isCompleted) 320.dp else 300.dp,
                    animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
                    label = "width"
                )
                val cardHeight by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isCompleted) 420.dp else 360.dp,
                    animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
                    label = "height"
                )
                
                Box(
                    modifier = Modifier
                        .size(width = cardWidth, height = cardHeight)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                ) {
                    // Success Background
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCompleted && bitmap != null,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(800)),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        bitmap?.let {
                            androidx.compose.foundation.Image(
                                bitmap = it,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            // Scrim to make text readable
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                        }
                    }
                    
                    androidx.compose.animation.AnimatedContent(
                        targetState = sharingState,
                        label = "CardContent",
                        modifier = Modifier.fillMaxSize()
                    ) { state ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            when (state) {
                                SharingState.COMPLETED -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color.White),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF1E88E5), // Nice blue
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Text(
                                            text = "Music received from ${visibleRequest?.endpointName ?: "Device"}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                            color = if (bitmap != null) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Surface(
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                shape = CircleShape,
                                                color = if (bitmap != null) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                onClick = { viewModel.reset() }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("Close", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (bitmap != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                shape = CircleShape,
                                                color = if (bitmap != null) Color.White else MaterialTheme.colorScheme.primary,
                                                onClick = { 
                                                    viewModel.reset()
                                                    onNavigateBack()
                                                }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("Go to Library", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (bitmap != null) Color.Black else MaterialTheme.colorScheme.onPrimary)
                                                }
                                            }
                                        }
                                    }
                                }
                                SharingState.TRANSFERRING -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        val animatedProgress by animateFloatAsState(
                                            targetValue = transferProgress,
                                            animationSpec = tween(400, easing = LinearOutSlowInEasing),
                                            label = "receive_progress"
                                        )

                                        Box(
                                            modifier = Modifier.size(140.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bitmap != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = bitmap,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(120.dp).clip(CircleShape),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                                // Dark scrim for the percentage text
                                                Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)))
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
                                                }
                                            }
                                            
                                            androidx.compose.material3.CircularProgressIndicator(
                                                progress = { animatedProgress },
                                                modifier = Modifier.fillMaxSize(),
                                                strokeWidth = 6.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )
                                            
                                            Text(
                                                "${(animatedProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (bitmap != null) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        val receivingText = if (totalTransferCount > 1) {
                                            "Receiving ${completedTransferCount + 1} of $totalTransferCount..."
                                        } else {
                                            "Receiving..."
                                        }
                                        Text(
                                            receivingText,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            if (totalTransferCount > 1) "Multiple items" else (currentTitle ?: ""),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                SharingState.CONNECTED -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(48.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Text(
                                            "Preparing...",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                else -> {
                                    // Connection Request (Initial State)
                                    visibleRequest?.let { request ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
                                            val scale by infiniteTransition.animateFloat(
                                                initialValue = 0.9f,
                                                targetValue = 1.05f,
                                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                                    animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                                ),
                                                label = "scale"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .scale(scale)
                                                    .clip(CircleShape)
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                                            colors = listOf(
                                                                MaterialTheme.colorScheme.primary,
                                                                MaterialTheme.colorScheme.tertiary
                                                            )
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                                            }
                                            
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Text(
                                                text = "${request.endpointName} wants to share music with you.",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "PIN: ${request.authCode}",
                                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                            
                                            Spacer(modifier = Modifier.weight(1f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Surface(
                                                    modifier = Modifier.weight(1f).height(48.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    onClick = { viewModel.rejectConnection(request.endpointId) }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("Decline", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                Surface(
                                                    modifier = Modifier.weight(1f).height(48.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    onClick = { viewModel.acceptConnection(request.endpointId) }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("Accept", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
