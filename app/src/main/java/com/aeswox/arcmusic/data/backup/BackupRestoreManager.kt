package com.aeswox.arcmusic.data.backup

import android.content.Context
import android.net.Uri
import com.aeswox.arcmusic.BuildConfig
import com.aeswox.arcmusic.data.SettingsRepository
import com.aeswox.arcmusic.db.MusicDatabase
import com.aeswox.arcmusic.db.entities.PlayHistory
import com.aeswox.arcmusic.db.entities.Playlist
import com.aeswox.arcmusic.db.entities.PlaylistTrack
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val backupAdapter = moshi.adapter(ArcMusicBackup::class.java)

    suspend fun createBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupDao = database.backupDao()
            
            // 1. Gather Playlists
            val playlistsRaw = backupDao.getAllPlaylistsWithTracks()
            val playlistsBackup = playlistsRaw.map { raw ->
                PlaylistBackup(
                    id = raw.playlist.id,
                    name = raw.playlist.name,
                    dateCreated = raw.playlist.dateCreated,
                    description = raw.playlist.description,
                    tracks = raw.tracks.mapNotNull { pt ->
                        pt.track?.let { track ->
                            TrackFingerprint(
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                durationMs = track.durationMs,
                                positionInPlaylist = pt.playlistTrack.position
                            )
                        }
                    }
                )
            }

            // 2. Gather Play History
            val historyRaw = backupDao.getAllPlayHistoryWithTracks()
            val historyBackup = historyRaw.mapNotNull { raw ->
                raw.track?.let { track ->
                    PlayHistoryBackup(
                        fingerprint = TrackFingerprint(
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationMs = track.durationMs,
                            positionInPlaylist = -1
                        ),
                        playedAt = raw.playHistory.timestamp,
                        playDurationMs = raw.playHistory.playedMs,
                        completed = raw.playHistory.completed,
                        skipReason = raw.playHistory.skipReason
                    )
                }
            }

            // 3. Gather Search History
            val searchRaw = backupDao.getAllSearchHistory()
            val searchBackup = searchRaw.map {
                SearchHistoryBackup(query = it.query, timestamp = it.timestamp)
            }

            // 4. Settings
            val settingsMap = mutableMapOf<String, String>()
            settingsMap["hasCompletedOnboarding"] = settingsRepository.hasCompletedOnboarding.first().toString()
            settingsRepository.lastFmApiKey.first()?.let { settingsMap["lastFmApiKey"] = it }
            settingsRepository.fanartTvApiKey.first()?.let { settingsMap["fanartTvApiKey"] = it }
            settingsRepository.geminiApiKey.first()?.let { settingsMap["geminiApiKey"] = it }
            settingsMap["themeMode"] = settingsRepository.themeMode.first().name
            settingsMap["nowPlayingStyle"] = settingsRepository.nowPlayingStyle.first().name
            settingsMap["canvasEnabled"] = settingsRepository.canvasEnabled.first().toString()
            settingsMap["dynamicColorsEnabled"] = settingsRepository.dynamicColorsEnabled.first().toString()
            settingsMap["autoUpdateEnabled"] = settingsRepository.autoUpdateEnabled.first().toString()
            settingsMap["developerOptionsUnlocked"] = settingsRepository.developerOptionsUnlocked.first().toString()
            settingsMap["canvasCacheLimitMb"] = settingsRepository.canvasCacheLimitMb.first().toString()
            settingsMap["heroCardPlayingStateEnabled"] = settingsRepository.heroCardPlayingStateEnabled.first().toString()
            settingsMap["heroCardIncludeArtistsAndAlbums"] = settingsRepository.heroCardIncludeArtistsAndAlbums.first().toString()
            settingsMap["tintTransparency"] = settingsRepository.tintTransparency.first().toString()
            settingsMap["noiseFactor"] = settingsRepository.noiseFactor.first().toString()
            settingsMap["glowIntensity"] = settingsRepository.glowIntensity.first().toString()
            settingsMap["physicsMass"] = settingsRepository.physicsMass.first().toString()
            settingsMap["physicsStiffness"] = settingsRepository.physicsStiffness.first().toString()
            settingsMap["physicsDampingRatio"] = settingsRepository.physicsDampingRatio.first().toString()
            settingsMap["physicsAmplitude"] = settingsRepository.physicsAmplitude.first().toString()
            settingsMap["physicsGravity"] = settingsRepository.physicsGravity.first().toString()
            settingsMap["seekbarBaselineHeight"] = settingsRepository.seekbarBaselineHeight.first().toString()
            settingsMap["seekbarWaveMaxAmp"] = settingsRepository.seekbarWaveMaxAmp.first().toString()
            settingsMap["seekbarCycleLength"] = settingsRepository.seekbarCycleLength.first().toString()
            settingsMap["seekbarShadowOffset"] = settingsRepository.seekbarShadowOffset.first().toString()
            settingsMap["seekbarShadowOpacity"] = settingsRepository.seekbarShadowOpacity.first().toString()
            settingsMap["seekbarPrimaryOpacity"] = settingsRepository.seekbarPrimaryOpacity.first().toString()
            settingsMap["seekbarThumbRadius"] = settingsRepository.seekbarThumbRadius.first().toString()
            settingsMap["seekbarUnplayedStroke"] = settingsRepository.seekbarUnplayedStroke.first().toString()
            settingsMap["seekbarBloomDuration"] = settingsRepository.seekbarBloomDuration.first().toString()
            settingsMap["minSongDurationSec"] = settingsRepository.minSongDurationSec.first().toString()
            settingsMap["minTracksPerAlbum"] = settingsRepository.minTracksPerAlbum.first().toString()
            settingsMap["excludedFolders"] = settingsRepository.excludedFolders.first().joinToString("|")
            settingsMap["lightThemeForNowPlaying"] = settingsRepository.lightThemeForNowPlaying.first().toString()
            settingsMap["coilDiskCacheLimitMb"] = settingsRepository.coilDiskCacheLimitMb.first().toString()
            settingsMap["autoScanOnStartup"] = settingsRepository.autoScanOnStartup.first().toString()
            settingsMap["deferScanDuringPlayback"] = settingsRepository.deferScanDuringPlayback.first().toString()
            settingsMap["autoScanLrcFiles"] = settingsRepository.autoScanLrcFiles.first().toString()
            settingsMap["augmentMetadataFromTags"] = settingsRepository.augmentMetadataFromTags.first().toString()
            settingsMap["extractArtistsFromTitle"] = settingsRepository.extractArtistsFromTitle.first().toString()
            settingsMap["artistDelimiters"] = settingsRepository.artistDelimiters.first()
            settingsMap["minBitrateKbps"] = settingsRepository.minBitrateKbps.first().toString()
            settingsMap["autoplayEnabled"] = settingsRepository.autoplayEnabled.first().toString()
            settingsMap["skipSilenceEnabled"] = settingsRepository.skipSilenceEnabled.first().toString()
            settingsMap["resumeOnBluetoothEnabled"] = settingsRepository.resumeOnBluetoothEnabled.first().toString()
            settingsMap["audioDuckingEnabled"] = settingsRepository.audioDuckingEnabled.first().toString()
            settingsMap["appIconVariant"] = settingsRepository.appIconVariant.first().name

            val metadata = BackupMetadata(
                appVersionCode = BuildConfig.VERSION_CODE,
                appVersionName = BuildConfig.VERSION_NAME,
                backupTimestampMs = System.currentTimeMillis(),
                deviceModel = android.os.Build.MODEL
            )

            val backup = ArcMusicBackup(
                metadata = metadata,
                playlists = playlistsBackup,
                playHistory = historyBackup,
                searchHistory = searchBackup,
                settings = settingsMap
            )

            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    val json = backupAdapter.toJson(backup)
                    writer.write(json)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun parseBackup(uri: Uri): ArcMusicBackup? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { `is` ->
                InputStreamReader(`is`).use { reader ->
                    val json = reader.readText()
                    return@withContext backupAdapter.fromJson(json)
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun restoreBackup(
        backup: ArcMusicBackup,
        restoreSettings: Boolean,
        restorePlaylists: Boolean,
        restoreHistory: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupDao = database.backupDao()

            if (restorePlaylists && backup.playlists != null) {
                // Simplified restore for playlists
                val playlistDao = database.playlistDao()
                for (plBackup in backup.playlists) {
                    // Generate new ID to avoid conflicts, or use old ID if doing full replace
                    val newPlaylistId = UUID.randomUUID().toString()
                    val newPlaylist = Playlist(
                        id = newPlaylistId,
                        name = plBackup.name,
                        dateCreated = plBackup.dateCreated,
                        description = plBackup.description
                    )
                    playlistDao.insertPlaylist(newPlaylist)

                    val newTracks = mutableListOf<PlaylistTrack>()
                    for (trackFp in plBackup.tracks) {
                        // Reconciliation: Try to find local track by fingerprint
                        var localTrackId = backupDao.findTrackExact(trackFp.title, trackFp.artist, trackFp.album ?: "", trackFp.durationMs)
                        if (localTrackId == null) {
                            localTrackId = backupDao.findTrackFuzzy("%${trackFp.title}%", "%${trackFp.artist}%")
                        }
                        
                        if (localTrackId != null) {
                            newTracks.add(PlaylistTrack(
                                playlistId = newPlaylistId,
                                trackId = localTrackId,
                                position = trackFp.positionInPlaylist
                            ))
                        }
                    }
                    if (newTracks.isNotEmpty()) {
                        playlistDao.insertPlaylistTracks(newTracks)
                    }
                }
            }

            if (restoreHistory && backup.playHistory != null) {
                val historyDao = database.playHistoryDao()
                for (histBackup in backup.playHistory) {
                    val fp = histBackup.fingerprint
                    var localTrackId = backupDao.findTrackExact(fp.title, fp.artist, fp.album ?: "", fp.durationMs)
                    if (localTrackId == null) {
                        localTrackId = backupDao.findTrackFuzzy("%${fp.title}%", "%${fp.artist}%")
                    }
                    if (localTrackId != null) {
                        historyDao.insertPlayHistory(
                            PlayHistory(
                                trackId = localTrackId,
                                timestamp = histBackup.playedAt,
                                playedMs = histBackup.playDurationMs,
                                completed = histBackup.completed,
                                skipReason = histBackup.skipReason
                            )
                        )
                    }
                }
            }

            if (restoreHistory && backup.searchHistory != null) {
                val searchDao = database.searchHistoryDao()
                for (search in backup.searchHistory) {
                    searchDao.insertSearch(
                        com.aeswox.arcmusic.db.entities.SearchHistory(
                            query = search.query,
                            timestamp = search.timestamp
                        )
                    )
                }
            }

            if (restoreSettings && backup.settings != null) {
                val s = backup.settings
                
                s["hasCompletedOnboarding"]?.toBooleanStrictOrNull()?.let { settingsRepository.setHasCompletedOnboarding(it) }
                s["lastFmApiKey"]?.let { settingsRepository.setLastFmApiKey(it) }
                s["fanartTvApiKey"]?.let { settingsRepository.setFanartTvApiKey(it) }
                s["geminiApiKey"]?.let { settingsRepository.setGeminiApiKey(it) }
                s["themeMode"]?.let { 
                    try { settingsRepository.setThemeMode(com.aeswox.arcmusic.ThemeMode.valueOf(it)) } catch(e:Exception){} 
                }
                s["nowPlayingStyle"]?.let { 
                    try { settingsRepository.setNowPlayingStyle(com.aeswox.arcmusic.data.model.NowPlayingStyle.valueOf(it)) } catch(e:Exception){} 
                }
                s["canvasEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setCanvasEnabled(it) }
                s["dynamicColorsEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setDynamicColorsEnabled(it) }
                s["autoUpdateEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setAutoUpdateEnabled(it) }
                s["developerOptionsUnlocked"]?.toBooleanStrictOrNull()?.let { settingsRepository.setDeveloperOptionsUnlocked(it) }
                s["canvasCacheLimitMb"]?.toIntOrNull()?.let { settingsRepository.setCanvasCacheLimitMb(it) }
                s["heroCardPlayingStateEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setHeroCardPlayingStateEnabled(it) }
                s["heroCardIncludeArtistsAndAlbums"]?.toBooleanStrictOrNull()?.let { settingsRepository.setHeroCardIncludeArtistsAndAlbums(it) }
                s["tintTransparency"]?.toFloatOrNull()?.let { settingsRepository.setTintTransparency(it) }
                s["noiseFactor"]?.toFloatOrNull()?.let { settingsRepository.setNoiseFactor(it) }
                s["glowIntensity"]?.toFloatOrNull()?.let { settingsRepository.setGlowIntensity(it) }
                s["physicsMass"]?.toFloatOrNull()?.let { settingsRepository.setPhysicsMass(it) }
                s["physicsStiffness"]?.toFloatOrNull()?.let { settingsRepository.setPhysicsStiffness(it) }
                s["physicsDampingRatio"]?.toFloatOrNull()?.let { settingsRepository.setPhysicsDampingRatio(it) }
                s["physicsAmplitude"]?.toFloatOrNull()?.let { settingsRepository.setPhysicsAmplitude(it) }
                s["physicsGravity"]?.toFloatOrNull()?.let { settingsRepository.setPhysicsGravity(it) }
                s["seekbarBaselineHeight"]?.toFloatOrNull()?.let { settingsRepository.setSeekbarBaselineHeight(it) }
                s["seekbarWaveMaxAmp"]?.toFloatOrNull()?.let { settingsRepository.setSeekbarWaveMaxAmp(it) }
                s["seekbarCycleLength"]?.toFloatOrNull()?.let { settingsRepository.setSeekbarCycleLength(it) }
                s["seekbarShadowOffset"]?.toFloatOrNull()?.let { settingsRepository.setSeekbarShadowOffset(it) }
                s["seekbarShadowOpacity"]?.toFloatOrNull()?.let { settingsRepository.setSeekbarShadowOpacity(it) }
                s["seekbarPrimaryOpacity"]?.toFloatOrNull()?.let { settingsRepository.setSeekbarPrimaryOpacity(it) }
                s["seekbarThumbRadius"]?.toFloatOrNull()?.let { settingsRepository.setSeekbarThumbRadius(it) }
                s["seekbarUnplayedStroke"]?.toFloatOrNull()?.let { settingsRepository.setSeekbarUnplayedStroke(it) }
                s["seekbarBloomDuration"]?.toFloatOrNull()?.let { settingsRepository.setSeekbarBloomDuration(it) }
                s["minSongDurationSec"]?.toIntOrNull()?.let { settingsRepository.setMinSongDurationSec(it) }
                s["minTracksPerAlbum"]?.toIntOrNull()?.let { settingsRepository.setMinTracksPerAlbum(it) }
                s["excludedFolders"]?.let { settingsRepository.setExcludedFolders(it.split("|").filter{ f -> f.isNotBlank() }) }
                s["lightThemeForNowPlaying"]?.toBooleanStrictOrNull()?.let { settingsRepository.setLightThemeForNowPlaying(it) }
                s["coilDiskCacheLimitMb"]?.toIntOrNull()?.let { settingsRepository.setCoilDiskCacheLimitMb(it) }
                s["autoScanOnStartup"]?.toBooleanStrictOrNull()?.let { settingsRepository.setAutoScanOnStartup(it) }
                s["deferScanDuringPlayback"]?.toBooleanStrictOrNull()?.let { settingsRepository.setDeferScanDuringPlayback(it) }
                s["autoScanLrcFiles"]?.toBooleanStrictOrNull()?.let { settingsRepository.setAutoScanLrcFiles(it) }
                s["augmentMetadataFromTags"]?.toBooleanStrictOrNull()?.let { settingsRepository.setAugmentMetadataFromTags(it) }
                s["extractArtistsFromTitle"]?.toBooleanStrictOrNull()?.let { settingsRepository.setExtractArtistsFromTitle(it) }
                s["artistDelimiters"]?.let { settingsRepository.setArtistDelimiters(it) }
                s["minBitrateKbps"]?.toIntOrNull()?.let { settingsRepository.setMinBitrateKbps(it) }
                s["autoplayEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setAutoplayEnabled(it) }
                s["skipSilenceEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setSkipSilenceEnabled(it) }
                s["resumeOnBluetoothEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setResumeOnBluetoothEnabled(it) }
                s["audioDuckingEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setAudioDuckingEnabled(it) }
                s["appIconVariant"]?.let { 
                    try { settingsRepository.setAppIconVariant(com.aeswox.arcmusic.data.AppIconVariant.valueOf(it)) } catch(e:Exception){} 
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
