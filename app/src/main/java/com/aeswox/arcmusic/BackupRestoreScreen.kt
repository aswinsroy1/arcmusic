package com.aeswox.arcmusic.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeswox.arcmusic.SettingsGroup
import com.aeswox.arcmusic.SettingsHeader
import com.aeswox.arcmusic.SettingsItem
import com.aeswox.arcmusic.ui.animations.physicsBounceOverscroll
import com.aeswox.arcmusic.ui.components.ArcModalBottomSheet
import com.aeswox.arcmusic.ui.components.JellyTextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp
) {
    val context = LocalContext.current
    val parsedBackup by viewModel.parsedBackup.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var showRestoreSheet by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    // Mock state for checkboxes
    var restorePlaylists by remember { mutableStateOf(true) }
    var restoreSettings by remember { mutableStateOf(true) }
    var restoreStats by remember { mutableStateOf(true) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.createBackup(uri) { success ->
                val msg = if (success) "Backup created successfully" else "Failed to create backup"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.parseBackupFile(uri) { success ->
                if (success) {
                    showRestoreSheet = true
                } else {
                    android.widget.Toast.makeText(context, "Failed to read backup file", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Transparent)) {
        LazyColumn(
            contentPadding = PaddingValues(top = 24.dp, bottom = bottomPadding, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.physicsBounceOverscroll().fillMaxSize()
        ) {
            item {
                SettingsHeader(title = "Backup & Restore", onNavigateBack = onNavigateBack)
            }
            
            item {
                SettingsGroup(title = "BACKUP") {
                    SettingsItem(
                        icon = Icons.Outlined.Upload,
                        text = "Create Backup",
                        trailingText = "Save a .json file",
                        showArrow = false,
                        onClick = {
                            backupLauncher.launch("ArcMusic_Backup_${System.currentTimeMillis()}.json")
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "RESTORE") {
                    SettingsItem(
                        icon = Icons.Outlined.Download,
                        text = "Restore Backup",
                        trailingText = "Load from file",
                        showArrow = false,
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
            }
            
            item {
                Text(
                    text = "Backups include your custom playlists, listening statistics, app settings, and API keys. Music files themselves are not backed up. When restoring, the app will attempt to re-link your playlists to your current local library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

    if (showRestoreSheet && parsedBackup != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ArcModalBottomSheet(
            currentSheet = Unit,
            onDismissRequest = { showRestoreSheet = false },
            sheetState = sheetState
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Selective Restore",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Choose what to restore from this backup file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = restoreSettings, onCheckedChange = { restoreSettings = it })
                    Text("App Settings & API Keys")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = restorePlaylists, onCheckedChange = { restorePlaylists = it })
                    Text("Playlists & Search History")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = restoreStats, onCheckedChange = { restoreStats = it })
                    Text("Listening Statistics")
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    JellyTextButton(onClick = { 
                        showRestoreSheet = false
                        viewModel.clearParsedBackup() 
                    }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            viewModel.restoreSelected(restoreSettings, restorePlaylists, restoreStats) { success ->
                                val msg = if (success) "Restore successful" else "Restore failed"
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                            showRestoreSheet = false 
                        },
                        enabled = !isProcessing
                    ) {
                        Text(if (isProcessing) "Restoring..." else "Restore")
                    }
                }
            }
        }
    }
}
