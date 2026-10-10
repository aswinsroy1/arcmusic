package com.aeswox.arcmusic

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.aeswox.arcmusic.db.entities.getQualityBadgeResId
import com.aeswox.arcmusic.ui.components.HugeIcons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.aeswox.arcmusic.ui.animations.physicsBounceOverscroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeswox.arcmusic.ui.animations.jellyClick
import com.aeswox.arcmusic.ui.animations.jelly
import com.aeswox.arcmusic.ui.components.*

@Composable
fun AlbumDetailsScreen(
    albumId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToShare: (String, String) -> Unit = { _, _ -> },
    viewModel: MusicViewModel = hiltViewModel()
) {
    val album by viewModel.getAlbumById(albumId).collectAsState(initial = null)
    val tracks by viewModel.getTracksByAlbum(albumId).collectAsState(initial = emptyList())
    val sortedTracks = remember(tracks) { tracks.sortedBy { it.trackNumber } }
    val moreAlbums by viewModel.getAlbumsByArtist(album?.artist ?: "").collectAsState(initial = emptyList())
    val filteredMoreAlbums = remember(moreAlbums, albumId) { moreAlbums.filter { it.id != albumId } }
    
    val currentlyPlaying by viewModel.currentlyPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val isMiniPlayerVisible by viewModel.isMiniPlayerVisible.collectAsState()
    val hasMiniPlayer = isMiniPlayerVisible && currentlyPlaying != null

    val immersiveMode by viewModel.immersiveModeEnabled.collectAsState()
    val rawImmersiveColor by rememberDominantColor(album?.artworkUri, Color(0xFF211F26))
    val immersiveColor = remember(rawImmersiveColor) { immersiveBackground(rawImmersiveColor) }
    val immersiveAccent by rememberVibrantColor(album?.artworkUri, MaterialTheme.colorScheme.secondary, immersiveColor)
    LaunchedEffect(immersiveMode, immersiveColor) {
        viewModel.setImmersiveScrimColor(if (immersiveMode) immersiveColor else null)
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val bottomPadding by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (hasMiniPlayer) 130.dp else 48.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "albumDetailsBottomPadding"
    )

    val menuItems = remember(album, sortedTracks) {
        val list = mutableListOf<MorphingMenuItem>()
        if (sortedTracks.isNotEmpty() && album != null) {
            list.add(
                MorphingMenuItem(
                    text = "Play next",
                    icon = Icons.Default.QueueMusic,
                    onClick = {
                        viewModel.addSelectedItemsToQueue(listOf("album_${album!!.id}"), playNext = true)
                    }
                )
            )
            list.add(
                MorphingMenuItem(
                    text = "Add to queue",
                    icon = Icons.Default.PlaylistAdd,
                    onClick = {
                        viewModel.addSelectedItemsToQueue(listOf("album_${album!!.id}"), playNext = false)
                    }
                )
            )
        }
        if (!album?.artist.isNullOrBlank()) {
            list.add(
                MorphingMenuItem(
                    text = "Go to artist",
                    icon = Icons.Default.Person,
                    onClick = { onNavigateToArtist(album!!.artist) }
                )
            )
        }
        if (album != null) {
            list.add(
                MorphingMenuItem(
                    text = "Share album",
                    icon = Icons.Default.IosShare,
                    onClick = { onNavigateToShare("album", album!!.id) }
                )
            )
        }
        list
    }

    if (album == null) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            AlbumDetailsSkeleton(onNavigateBack = onNavigateBack)
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (immersiveMode) Modifier.background(immersiveColor)
                    else Modifier.systemBarsPadding()
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .physicsBounceOverscroll()
                    .then(if (immersiveMode) Modifier else Modifier.padding(horizontal = 24.dp)),
                contentPadding = PaddingValues(
                    top = if (immersiveMode) 0.dp else 104.dp,
                    bottom = if (immersiveMode) bottomPadding + bottomInset else bottomPadding
                )
            ) {
                if (immersiveMode) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            AsyncImage(
                                model = album?.artworkUri,
                                contentDescription = "Album Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(immersiveColor.copy(alpha = 0f), immersiveColor)
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 28.dp)
                                    .padding(horizontal = 24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = album?.title ?: "Unknown Album",
                                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White,
                                    fontSize = 40.sp,
                                    lineHeight = 46.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    JellyButton(
                                        onClick = { viewModel.setCurrentlyPlaying(sortedTracks.firstOrNull(), sortedTracks) },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Icon(imageVector = HugeIcons.Play, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Play", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                    JellyButton(
                                        onClick = {
                                            val shuffled = sortedTracks.shuffled()
                                            viewModel.setCurrentlyPlaying(shuffled.firstOrNull(), shuffled)
                                        },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = immersiveAccent, contentColor = Color.White),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Icon(imageVector = HugeIcons.Shuffle, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Shuffle", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(52.dp))
                    }
                }
                item {
                    Box(modifier = if (immersiveMode) Modifier.padding(horizontal = 24.dp) else Modifier) {
                        AlbumDetailsInfo(
                            album = album,
                            tracks = sortedTracks,
                            showArtwork = !immersiveMode,
                            immersive = immersiveMode,
                            onPlay = { viewModel.setCurrentlyPlaying(sortedTracks.firstOrNull(), sortedTracks) },
                            onShuffle = { 
                                val shuffled = sortedTracks.shuffled()
                                viewModel.setCurrentlyPlaying(shuffled.firstOrNull(), shuffled) 
                            },
                            onNavigateToArtist = onNavigateToArtist
                        )
                    }
                }
                item {
                    Box(modifier = if (immersiveMode) Modifier.padding(horizontal = 24.dp) else Modifier) {
                        AlbumTracksList(
                            tracks = sortedTracks,
                            currentlyPlaying = currentlyPlaying,
                            isPlaying = isPlaying,
                            immersive = immersiveMode,
                            onTrackClick = { track -> viewModel.setCurrentlyPlaying(track, sortedTracks) }
                        )
                    }
                }
                if (filteredMoreAlbums.isNotEmpty()) {
                    item {
                        Box(modifier = if (immersiveMode) Modifier.padding(horizontal = 24.dp) else Modifier) {
                            Spacer(modifier = Modifier.height(32.dp))
                            MoreByArtistSection(
                                artistName = album?.artist ?: "",
                                albums = filteredMoreAlbums,
                                immersive = immersiveMode,
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToAlbum = onNavigateToAlbum
                            )
                        }
                    }
                }
            }
            
            AlbumDetailsHeader(
                onNavigateBack = onNavigateBack, 
                menuItems = menuItems,
                immersive = immersiveMode,
                accent = immersiveAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (immersiveMode) topInset + 16.dp else 48.dp, start = 24.dp, end = 24.dp)
            )
        }
    }
}

@Composable
fun AlbumDetailsHeader(
    onNavigateBack: () -> Unit,
    menuItems: List<MorphingMenuItem> = emptyList(),
    immersive: Boolean = false,
    accent: Color = Color.White,
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
                .background(if (immersive) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f))
        ) {
            Icon(
                imageVector = HugeIcons.ArrowLeft,
                contentDescription = "Back",
                tint = if (immersive) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
        if (menuItems.isNotEmpty()) {
            MorphingMenu(
                items = menuItems,
                buttonBackground = if (immersive) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f),
                tint = if (immersive) Color.White else MaterialTheme.colorScheme.onSurface,
                immersive = immersive,
                immersiveAccent = accent
            )
        } else {
            JellyIconButton(
                onClick = { },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (immersive) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f))
            ) {
                Icon(
                    imageVector = HugeIcons.MoreVert,
                    contentDescription = "More",
                    tint = if (immersive) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun AlbumDetailsInfo(
    album: com.aeswox.arcmusic.db.entities.Album?,
    tracks: List<com.aeswox.arcmusic.db.entities.Track>,
    showArtwork: Boolean = true,
    immersive: Boolean = false,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val totalDurationMs = tracks.sumOf { it.durationMs }
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(totalDurationMs)
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(totalDurationMs) % 60
    val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    val genreText = tracks.firstOrNull()?.genre?.takeIf { it.isNotBlank() } ?: "Unknown"
    val yearText = album?.year?.takeIf { it > 0 }?.toString() ?: ""
    val subtitleText = listOf(genreText, yearText).filter { it.isNotBlank() }.joinToString(" • ")

    if (immersive) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .jellyClick { onNavigateToArtist(album?.artist ?: "") }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = album?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
            if (subtitleText.isNotBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        if (showArtwork) {
            AsyncImage(
                model = album?.artworkUri,
                contentDescription = "Album Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(36.dp))
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = album?.title ?: "Unknown Album",
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
                    .jellyClick { onNavigateToArtist(album?.artist ?: "") }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = album?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
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
fun AlbumTracksList(
    tracks: List<com.aeswox.arcmusic.db.entities.Track>,
    currentlyPlaying: com.aeswox.arcmusic.db.entities.Track?,
    isPlaying: Boolean,
    immersive: Boolean = false,
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
            .background(immersiveCardColor(immersive))
            .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "TRACKS",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            color = immersiveMutedColor(immersive),
            modifier = Modifier.padding(start = 72.dp, end = 24.dp, bottom = 16.dp)
        )
        
        displayTracks.forEachIndexed { index, track ->
            val isCurrentTrack = currentlyPlaying?.id == track.id
            val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(track.durationMs)
            val secs = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(track.durationMs) % 60
            val durString = String.format("%d:%02d", mins, secs)
            
            AlbumTrackItem(
                number = (index + 1).toString(),
                title = track.title,
                duration = durString,
                qualityBadgeResId = track.getQualityBadgeResId(),
                isPlaying = isCurrentTrack && isPlaying,
                isExplicit = false,
                immersive = immersive,
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
                    color = immersiveMutedColor(immersive)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = immersiveMutedColor(immersive),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AlbumTrackItem(
    number: String,
    title: String,
    duration: String,
    qualityBadgeResId: Int? = null,
    isPlaying: Boolean = false,
    isExplicit: Boolean = false,
    immersive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .jellyClick { onClick() }
            .background(if (isPlaying) (if (immersive) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer) else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (isPlaying) {
                // Playing animation placeholder
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(16.dp)) {
                    Box(modifier = Modifier.width(3.dp).height(8.dp).background(if (immersive) Color.White else MaterialTheme.colorScheme.primary))
                    Box(modifier = Modifier.width(3.dp).height(14.dp).background(if (immersive) Color.White else MaterialTheme.colorScheme.primary))
                    Box(modifier = Modifier.width(3.dp).height(10.dp).background(if (immersive) Color.White else MaterialTheme.colorScheme.primary))
                }
            } else {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = immersiveMutedColor(immersive).copy(alpha = if (immersive) 1f else 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium),
                    color = if (isPlaying) (if (immersive) Color.White else MaterialTheme.colorScheme.primary) else immersiveTextColor(immersive),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (false /* isExplicit */) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "E",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        if (qualityBadgeResId != null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = qualityBadgeResId),
                contentDescription = "Quality",
                modifier = Modifier.height(16.dp),
                contentScale = ContentScale.Fit,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(immersiveMutedColor(immersive))
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = duration,
            style = MaterialTheme.typography.bodyMedium,
            color = immersiveMutedColor(immersive).copy(alpha = if (immersive) 1f else 0.6f)
        )
    }
}

@Composable
fun MoreByArtistSection(
    artistName: String,
    albums: List<com.aeswox.arcmusic.db.entities.Album>,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    immersive: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "More by $artistName",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = immersiveTextColor(immersive),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "See all",
                style = MaterialTheme.typography.bodyMedium,
                color = immersiveMutedColor(immersive),
                modifier = Modifier.jellyClick { onNavigateToArtist(artistName) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(
            modifier = Modifier.physicsBounceOverscroll(isHorizontal = true),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums) { album ->
                AlbumResultItem(
                    title = album.title, 
                    year = "${album.trackCount} tracks", 
                    imageUrl = album.artworkUri ?: "",
                    immersive = immersive,
                    onClick = { onNavigateToAlbum(album.id) }
                )
            }
        }
    }
}

@Composable
fun AlbumDetailsSkeleton(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 120.dp)
    ) {
        AlbumDetailsHeader(onNavigateBack = onNavigateBack)
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .shimmerLoading()
            )
            Spacer(modifier = Modifier.height(32.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerLoading()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerLoading()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerLoading()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .shimmerLoading()
                    )
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .shimmerLoading()
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .shimmerLoading()
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .shimmerLoading()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        repeat(5) {
            TrackListItemSkeleton(
                modifier = Modifier.padding(horizontal = 0.dp),
                showCover = false, 
                showTrackNumber = true
            )
        }
    }
}
