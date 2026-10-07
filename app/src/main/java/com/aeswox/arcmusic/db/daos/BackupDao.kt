package com.aeswox.arcmusic.db.daos

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.aeswox.arcmusic.db.entities.PlayHistory
import com.aeswox.arcmusic.db.entities.Playlist
import com.aeswox.arcmusic.db.entities.PlaylistTrack
import com.aeswox.arcmusic.db.entities.SearchHistory
import com.aeswox.arcmusic.db.entities.Track

data class PlaylistWithTracksBackup(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId",
        entity = PlaylistTrack::class
    )
    val tracks: List<PlaylistTrackWithTrack>
)

data class PlaylistTrackWithTrack(
    @Embedded val playlistTrack: PlaylistTrack,
    @Relation(
        parentColumn = "trackId",
        entityColumn = "id"
    )
    val track: Track?
)

data class PlayHistoryWithTrack(
    @Embedded val playHistory: PlayHistory,
    @Relation(
        parentColumn = "trackId",
        entityColumn = "id"
    )
    val track: Track?
)

@Dao
interface BackupDao {
    @Transaction
    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsWithTracks(): List<PlaylistWithTracksBackup>

    @Transaction
    @Query("SELECT * FROM play_history")
    suspend fun getAllPlayHistoryWithTracks(): List<PlayHistoryWithTrack>

    @Query("SELECT * FROM search_history")
    suspend fun getAllSearchHistory(): List<SearchHistory>
    
    @Query("SELECT id FROM tracks WHERE title = :title AND artist = :artist AND album = :album AND durationMs = :durationMs LIMIT 1")
    suspend fun findTrackExact(title: String, artist: String, album: String, durationMs: Long): String?

    @Query("SELECT id FROM tracks WHERE title LIKE :title AND artist LIKE :artist LIMIT 1")
    suspend fun findTrackFuzzy(title: String, artist: String): String?
}
