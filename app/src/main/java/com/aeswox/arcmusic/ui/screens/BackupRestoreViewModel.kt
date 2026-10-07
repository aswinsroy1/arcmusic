package com.aeswox.arcmusic.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeswox.arcmusic.data.backup.ArcMusicBackup
import com.aeswox.arcmusic.data.backup.BackupRestoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val backupRestoreManager: BackupRestoreManager
) : ViewModel() {

    private val _parsedBackup = MutableStateFlow<ArcMusicBackup?>(null)
    val parsedBackup: StateFlow<ArcMusicBackup?> = _parsedBackup.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun createBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            val result = backupRestoreManager.createBackup(uri)
            _isProcessing.value = false
            onResult(result.isSuccess)
        }
    }

    fun parseBackupFile(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            val backup = backupRestoreManager.parseBackup(uri)
            _parsedBackup.value = backup
            _isProcessing.value = false
            onResult(backup != null)
        }
    }

    fun restoreSelected(
        restoreSettings: Boolean,
        restorePlaylists: Boolean,
        restoreHistory: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        val backup = _parsedBackup.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val result = backupRestoreManager.restoreBackup(
                backup = backup,
                restoreSettings = restoreSettings,
                restorePlaylists = restorePlaylists,
                restoreHistory = restoreHistory
            )
            _isProcessing.value = false
            onResult(result.isSuccess)
        }
    }
    
    fun clearParsedBackup() {
        _parsedBackup.value = null
    }
}
