package com.aeswox.arcmusic.data.backup

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ArcMusicBackup(
    val metadata: BackupMetadata,
    val playlists: List<PlaylistBackup>? = null,
    val playHistory: List<PlayHistoryBackup>? = null,
    val searchHistory: List<SearchHistoryBackup>? = null,
    val settings: Map<String, String>? = null // Changed from Any to String to fix Moshi Codegen
)

@JsonClass(generateAdapter = true)
data class SearchHistoryBackup(
    val query: String,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class BackupMetadata(
    val appVersionCode: Int,
    val appVersionName: String,
    val backupTimestampMs: Long,
    val deviceModel: String
)

@JsonClass(generateAdapter = true)
data class PlaylistBackup(
    val id: String,
    val name: String,
    val dateCreated: Long,
    val description: String?,
    val tracks: List<TrackFingerprint>
)

@JsonClass(generateAdapter = true)
data class TrackFingerprint(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val positionInPlaylist: Int
)

@JsonClass(generateAdapter = true)
data class PlayHistoryBackup(
    val fingerprint: TrackFingerprint,
    val playedAt: Long,
    val playDurationMs: Long,
    val completed: Boolean,
    val skipReason: String?
)
