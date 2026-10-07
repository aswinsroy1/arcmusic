package com.aeswox.arcmusic

import com.aeswox.arcmusic.ui.animations.physicsBounceOverscroll
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.aeswox.arcmusic.db.entities.Playlist
import com.aeswox.arcmusic.db.entities.Track
import com.aeswox.arcmusic.ui.animations.jellyClick
import com.aeswox.arcmusic.ui.animations.jelly
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.aeswox.arcmusic.ui.components.ArcModalBottomSheet
import com.aeswox.arcmusic.ui.components.HugeIcons
import com.aeswox.arcmusic.ui.components.JellyButton
import com.aeswox.arcmusic.ui.components.JellyIconButton
import com.aeswox.arcmusic.ui.components.JellyFilledIconButton
import com.aeswox.arcmusic.ui.components.JellyFilledTonalIconButton
import com.aeswox.arcmusic.ui.components.JellyOutlinedIconButton
import com.aeswox.arcmusic.ui.components.MorphingMenu
import com.aeswox.arcmusic.ui.components.MorphingMenuItem
import com.aeswox.arcmusic.db.entities.getQualityBadgeResId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailsScreen(
    playlistId: String,
    onNavigateBack: () -> Unit,
    onNavigateToShare: (String, String) -> Unit = { _, _ -> },
    viewModel: MusicViewModel = hiltViewModel()
) {
    val playlist by viewModel.getPlaylist(playlistId).collectAsState(initial = null)
    val tracks by viewModel.getTracksForPlaylist(playlistId).collectAsState(initial = emptyList())
    val currentlyPlaying by viewModel.currentlyPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (playlist == null) {
        PlaylistDetailsSkeleton(onNavigateBack = onNavigateBack)
        return
    }

    val isMiniPlayerVisible by viewModel.isMiniPlayerVisible.collectAsState()
    val hasMiniPlayer = isMiniPlayerVisible && currentlyPlaying != null

    val bottomPadding by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (hasMiniPlayer) 130.dp else 48.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "playlistDetailsBottomPadding"
    )

    val totalDurationMs = tracks.sumOf { it.durationMs }
    val totalHours = totalDurationMs / (1000 * 60 * 60)
    val totalMinutes = (totalDurationMs % (1000 * 60 * 60)) / (1000 * 60)
    val durationText = if (totalHours > 0) "${totalHours}h ${totalMinutes}m" else "${totalMinutes}m"

    val firstTrackWithArt = tracks.firstOrNull { it.albumId != null }
    val coverUrl = playlist?.coverArtUri
        ?: firstTrackWithArt?.albumId?.let { "content://media/external/audio/albumart/$it" }
        ?: "https://lh3.googleusercontent.com/aida-public/AB6AXuDK2gSPmhFiKqcqPLlCJlIp7lxpTt2scS9SuOmzxmZKXa1UQIjSKITZh8tGxaLLsMWtK_rqugpIF6kWjdqifIFpbIHQ51KFkHHGCwprGn7T1jWwAFiUiOgft22mJtHc311emev_Y9qChhO44k-VwJC7dvX80Zs-JHFurqrp7BRfflgHO2uz-vspGyR9BoWhQUaXuELDgddlmK__JFlAjdrkjKUgyxH0SVRHhhE0iqWq7lQMTieDIl6s1Oh1frE5nhxruwt9dXwi3SRK" // Fallback

    val menuItems = remember(playlist, tracks) {
        val list = mutableListOf<MorphingMenuItem>()
        if (tracks.isNotEmpty() && playlist != null) {
            list.add(
                MorphingMenuItem(
                    text = "Play next",
                    icon = Icons.Default.QueueMusic,
                    onClick = {
                        viewModel.addSelectedItemsToQueue(listOf("playlist_${playlist!!.name}"), playNext = true)
                    }
                )
            )
            list.add(
                MorphingMenuItem(
                    text = "Add to queue",
                    icon = Icons.Default.PlaylistAdd,
                    onClick = {
                        viewModel.addSelectedItemsToQueue(listOf("playlist_${playlist!!.name}"), playNext = false)
                    }
                )
            )
        }
        list.add(
            MorphingMenuItem(
                text = "Edit playlist",
                icon = com.aeswox.arcmusic.ui.components.HugeIcons.Edit,
                onClick = { showEditDialog = true }
            )
        )
        list.add(
            MorphingMenuItem(
                text = "Share playlist",
                icon = Icons.Default.IosShare,
                onClick = { onNavigateToShare("playlist", playlistId) }
            )
        )
        list.add(
            MorphingMenuItem(
                text = "Delete playlist",
                icon = HugeIcons.Delete,
                isDestructive = true,
                onClick = { showDeleteConfirmDialog = true }
            )
        )
        list
    }

    if (playlist == null) {
        PlaylistDetailsSkeleton(onNavigateBack = onNavigateBack)
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .physicsBounceOverscroll()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 104.dp, bottom = bottomPadding)
            ) {
                item {
                    PlaylistDetailsInfo(
                        playlist = playlist,
                        coverUrl = coverUrl,
                        tracks = tracks,
                        durationText = durationText,
                        onPlay = { viewModel.setCurrentlyPlaying(tracks.firstOrNull(), tracks) },
                        onShuffle = { 
                            val shuffled = tracks.shuffled()
                            viewModel.setCurrentlyPlaying(shuffled.firstOrNull(), shuffled) 
                        }
                    )
                }
                item {
                    PlaylistTracksList(
                        tracks = tracks,
                        currentlyPlaying = currentlyPlaying,
                        isPlaying = isPlaying,
                        onTrackClick = { track -> viewModel.setCurrentlyPlaying(track, tracks) }
                    )
                }
            }
            
            PlaylistDetailsHeader(
                onNavigateBack = onNavigateBack, 
                menuItems = menuItems,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 24.dp, end = 24.dp)
            )
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${playlist?.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    viewModel.deletePlaylists(listOf(playlistId))
                    onNavigateBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    ArcModalBottomSheet(
        currentSheet = if (showEditDialog && playlist != null) playlist else null,
        onDismissRequest = { showEditDialog = false }
    ) { currentPlaylist ->
        EditPlaylistSheetContent(
            playlist = currentPlaylist,
            onDismiss = { showEditDialog = false },
            onSave = { newName, newDescription, newCoverUri ->
                viewModel.updatePlaylist(playlistId, newName, newDescription, newCoverUri) {
                    showEditDialog = false
                    if (newName != playlistId) {
                        onNavigateBack()
                    }
                }
            }
        )
    }
}

@Composable
fun EditPlaylistSheetContent(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, coverUri: String?) -> Unit
) {
    var name by remember(playlist) { mutableStateOf(playlist.name) }
    var description by remember(playlist) { mutableStateOf(playlist.description ?: "") }
    var coverUri by remember(playlist) { mutableStateOf(playlist.coverArtUri) }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                coverUri = uri.toString()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Edit Playlist",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            JellyIconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .jellyClick {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (coverUri != null) {
                    AsyncImage(
                        model = coverUri,
                        contentDescription = "Cover preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { coverUri = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove cover",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Choose Image",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                placeholder = { Text("Add an optional description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                JellyButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                JellyButton(
                    onClick = { onSave(name.trim(), description.trim().takeIf { it.isNotBlank() }, coverUri) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailsHeader(
    onNavigateBack: () -> Unit,
    menuItems: List<MorphingMenuItem> = emptyList(),
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        JellyIconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f))
        ) {
            Icon(
                imageVector = HugeIcons.ArrowLeft,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        if (menuItems.isNotEmpty()) {
            MorphingMenu(
                items = menuItems,
                buttonBackground = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f),
                tint = MaterialTheme.colorScheme.onSurface
            )
        } else {
            JellyIconButton(
                onClick = { },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f))
            ) {
                Icon(
                    imageVector = HugeIcons.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun PlaylistDetailsInfo(
    playlist: com.aeswox.arcmusic.db.entities.Playlist?,
    coverUrl: String,
    tracks: List<com.aeswox.arcmusic.db.entities.Track>,
    durationText: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    val subtitleText = "My Playlist"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        AsyncImage(
            model = coverUrl,
            contentDescription = "Playlist Cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(36.dp))
        )
        Spacer(modifier = Modifier.height(32.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = playlist?.name ?: "Unknown Playlist",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 46.sp,
                lineHeight = 52.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!playlist?.description.isNullOrBlank()) {
                Text(
                    text = playlist?.description ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${tracks.size} songs",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                JellyButton(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        imageVector = HugeIcons.Play,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                JellyButton(
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        imageVector = HugeIcons.Shuffle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shuffle",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PlaylistTracksList(
    tracks: List<com.aeswox.arcmusic.db.entities.Track>,
    currentlyPlaying: com.aeswox.arcmusic.db.entities.Track?,
    isPlaying: Boolean,
    onTrackClick: (com.aeswox.arcmusic.db.entities.Track) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var displayLimit by remember { androidx.compose.runtime.mutableIntStateOf(5) }

    androidx.compose.runtime.LaunchedEffect(expanded, tracks) {
        if (expanded) {
            var current = displayLimit
            while (current < tracks.size) {
                current = (current + 20).coerceAtMost(tracks.size)
                displayLimit = current
                kotlinx.coroutines.delay(16)
            }
        } else {
            displayLimit = 5
        }
    }

    val displayTracks = if (tracks.size <= 6) tracks else tracks.take(displayLimit)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
            .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "TRACKS",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 72.dp, end = 24.dp, bottom = 16.dp)
        )
        
        displayTracks.forEachIndexed { index, track ->
            val isCurrentTrack = currentlyPlaying?.id == track.id
            val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(track.durationMs)
            val secs = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(track.durationMs) % 60
            val durString = String.format("%d:%02d", mins, secs)
            
            PlaylistTrackItem(
                number = (index + 1).toString(),
                title = track.title,
                artist = track.artist ?: "Unknown",
                duration = durString,
                qualityBadgeResId = track.getQualityBadgeResId(),
                isPlaying = isCurrentTrack && isPlaying,
                onClick = { onTrackClick(track) }
            )
        }
        
        if (!expanded && tracks.size > 6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .jellyClick { expanded = true }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show more",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun PlaylistTrackItem(
    number: String,
    title: String,
    artist: String,
    duration: String,
    qualityBadgeResId: Int? = null,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .jellyClick { onClick() }
            .background(if (isPlaying) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (isPlaying) {
                // Playing animation placeholder
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(16.dp)) {
                    Box(modifier = Modifier.width(3.dp).height(8.dp).background(MaterialTheme.colorScheme.primary))
                    Box(modifier = Modifier.width(3.dp).height(14.dp).background(MaterialTheme.colorScheme.primary))
                    Box(modifier = Modifier.width(3.dp).height(10.dp).background(MaterialTheme.colorScheme.primary))
                }
            } else {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium),
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        if (qualityBadgeResId != null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = qualityBadgeResId),
                contentDescription = "Quality",
                modifier = Modifier.height(16.dp),
                contentScale = ContentScale.Fit,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = duration,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun PlaylistDetailsSkeleton(onNavigateBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconButton(
                        icon = HugeIcons.ArrowLeft,
                        contentDescription = "Back",
                        onClick = onNavigateBack,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIconButton(
                            icon = HugeIcons.MoreVert,
                            contentDescription = "More",
                            onClick = { },
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .shimmerLoading()
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmerLoading()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .shimmerLoading()
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .shimmerLoading()
                        )
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .shimmerLoading()
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "TRACKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp)
                    )
                }
            }
            items(5) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
                ) {
                    TrackListItemSkeleton(showCover = false, showTrackNumber = true)
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
                )
            }
        }
    }
}
