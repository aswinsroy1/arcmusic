package com.aeswox.arcmusic.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aeswox.arcmusic.db.MediaScannerManager
import com.aeswox.arcmusic.db.ScanPhase
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
}

@Composable
fun GlobalProgressOverlay(
    modifier: Modifier = Modifier,
    viewModel: GlobalOverlayViewModel = hiltViewModel()
) {
    val scanProgress by viewModel.scanProgress.collectAsState()
    val sharingState by viewModel.sharingState.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val completedTransferCount by viewModel.completedTransferCount.collectAsState()
    val totalTransferCount by viewModel.totalTransferCount.collectAsState()

    val isScanning = scanProgress.isRunning
    val isTransferring = sharingState == SharingState.TRANSFERRING

    val shouldShow = isScanning || isTransferring

    AnimatedVisibility(
        visible = shouldShow,
        enter = slideInVertically(initialOffsetY = { -it * 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it * 2 }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp) // Below status bar
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .shadow(16.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.15f))
                    .clip(CircleShape),
                color = Color.White,
                contentColor = Color.Black
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            progress = { scanProgress.fraction },
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                        Column {
                            val title = scanProgress.phase.label
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (scanProgress.total > 0) {
                                Text(
                                    text = "${scanProgress.current} of ${scanProgress.total} tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Black.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else if (isTransferring) {
                        CircularProgressIndicator(
                            progress = { transferProgress },
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                        Column {
                            Text(
                                text = "Transferring Media",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (totalTransferCount > 0) {
                                Text(
                                    text = "$completedTransferCount of $totalTransferCount files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Black.copy(alpha = 0.7f)
                                )
                            } else {
                                Text(
                                    text = "${(transferProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Black.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
