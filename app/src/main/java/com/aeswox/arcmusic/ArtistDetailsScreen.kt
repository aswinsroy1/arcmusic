package com.aeswox.arcmusic

import com.aeswox.arcmusic.ui.animations.physicsBounceOverscroll
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.aeswox.arcmusic.ui.components.FavoriteHeartIcon
import com.aeswox.arcmusic.ui.components.HugeIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.aeswox.arcmusic.db.entities.Artist
import com.aeswox.arcmusic.db.entities.Track
import com.aeswox.arcmusic.db.entities.Album
import com.aeswox.arcmusic.ui.animations.jellyClick
import com.aeswox.arcmusic.ui.animations.jelly
import com.aeswox.arcmusic.ui.components.*
import com.aeswox.arcmusic.db.entities.getQualityBadgeResId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailsScreen(
    artistId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToAllTracks: (String) -> Unit = {},
    onNavigateToAllAlbums: (String) -> Unit = {},
    onNavigateToShare: (String, String) -> Unit = { _, _ -> },
    viewModel: MusicViewModel = hiltViewModel()
) {
    val artist by viewModel.getArtistById(artistId).collectAsState(initial = null)
    val tracks by viewModel.getTracksByArtist(artistId).collectAsState(initial = emptyList())
    val albums by viewModel.getAlbumsByArtist(artistId).collectAsState(initial = emptyList())
    
    val isMiniPlayerVisible by viewModel.isMiniPlayerVisible.collectAsState()
    val currentlyPlaying by viewModel.currentlyPlaying.collectAsState()
    val hasMiniPlayer = isMiniPlayerVisible && currentlyPlaying != null

    val bottomPadding by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (hasMiniPlayer) 130.dp else 48.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "artistDetailsBottomPadding"
    )

    if (artist == null) {
        ArtistDetailsSkeleton(onNavigateBack = onNavigateBack)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val dir = File(context.filesDir, "artist_images")
                    if (!dir.exists()) dir.mkdirs()
                    val extension = context.contentResolver.getType(it)?.split("/")?.lastOrNull() ?: "jpg"
                    val localFile = File(dir, "${artistId}_custom.$extension")
                    val outputStream = FileOutputStream(localFile)
                    inputStream?.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    viewModel.updateArtistImage(artistId, Uri.fromFile(localFile).toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    var showInternetSearch by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            item {
                ArtistHeroSection(artist = artist, tracks = tracks, viewModel = viewModel)
            }
            item {
                ArtistAlbumsSection(albums = albums, onNavigateToAlbum = onNavigateToAlbum, onNavigateToAllAlbums = { onNavigateToAllAlbums(artistId) })
            }
            item {
                ArtistPopularSection(tracks = tracks, viewModel = viewModel, onNavigateToAllTracks = { onNavigateToAllTracks(artistId) })
            }
            item {
                ArtistAboutSection(artist = artist)
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
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
            MorphingMenu(
                items = listOf(
                    MorphingMenuItem(
                        text = "Refresh",
                        icon = Icons.Outlined.Refresh,
                        onClick = {
                            if (artist != null) {
                                viewModel.refetchArtistDetails(artistId, artist!!.name)
                            }
                        }
                    ),
                    MorphingMenuItem(
                        text = "Share",
                        icon = HugeIcons.Share,
                        onClick = { 
                            onNavigateToShare("artist", artistId)
                        }
                    ),
                    MorphingMenuItem(
                        text = "Change image (gallery)",
                        icon = Icons.Outlined.Image,
                        onClick = {
                            galleryLauncher.launch(arrayOf("image/*"))
                        }
                    ),
                    MorphingMenuItem(
                        text = "Change image (online)",
                        icon = Icons.Outlined.Public,
                        onClick = {
                            showInternetSearch = true
                        }
                    ),
                    MorphingMenuItem(
                        text = "Delete",
                        icon = HugeIcons.Delete,
                        isDestructive = true,
                        onClick = { }
                    )
                ),
                buttonBackground = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        

        
        if (showInternetSearch) {
            ArtistImageSearchBottomSheet(
                artistName = artist?.name ?: "",
                viewModel = viewModel,
                onDismiss = { showInternetSearch = false },
                onImageSelected = { imageUrl ->
                    viewModel.updateArtistImage(artistId, imageUrl)
                    showInternetSearch = false
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ArtistHeroSection(artist: Artist?, tracks: List<Track>, viewModel: MusicViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp, bottom = 32.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f/3f)
                    .clip(RoundedCornerShape(48.dp))
            ) {
                com.aeswox.arcmusic.ui.components.ArtistImage(
                    model = artist?.photoUri ?: "",
                    contentDescription = "Artist Image",
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                startY = 300f
                            )
                        )
                )
                
                if (artist != null) {
                    JellyIconButton(
                        onClick = { viewModel.toggleArtistFavorite(listOf(artist.id), !artist.isFavorite) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 24.dp, end = 24.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f))
                    ) {
                        FavoriteHeartIcon(
                            isFavorite = artist.isFavorite,
                            activeColor = Color(0xFFE53935),
                            inactiveColor = MaterialTheme.colorScheme.onSurface,
                            iconSize = 24.dp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = artist?.name ?: "Unknown Artist",
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 46.sp,
                    lineHeight = 52.sp,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val totalDurationMs = tracks.sumOf { it.durationMs }
            val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(totalDurationMs)
            val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(totalDurationMs) % 60
            val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                JellyButton(
                    onClick = { if (tracks.isNotEmpty()) viewModel.setCurrentlyPlaying(tracks.first(), tracks) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(imageVector = HugeIcons.Play, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                
                JellyButton(
                    onClick = { if (tracks.isNotEmpty()) viewModel.setCurrentlyPlaying(tracks.random(), tracks.shuffled()) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f), contentColor = MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(imageVector = HugeIcons.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shuffle", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ArtistPopularSection(tracks: List<Track>, viewModel: MusicViewModel, onNavigateToAllTracks: () -> Unit = {}) {
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

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        if (tracks.isNotEmpty()) {
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
                    val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(track.durationMs)
                    val secs = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(track.durationMs) % 60
                    val durString = String.format("%d:%02d", mins, secs)
                    
                    ArtistTrackItem(
                        number = index + 1, 
                        title = track.title, 
                        duration = durString,
                        qualityBadgeResId = track.getQualityBadgeResId(),
                        onClick = { viewModel.setCurrentlyPlaying(track, tracks) }
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
    }
}

@Composable
fun ArtistTrackItem(number: Int, title: String, duration: String, qualityBadgeResId: Int?, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .jellyClick { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
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
fun ArtistAlbumsSection(albums: List<Album>, onNavigateToAlbum: (String) -> Unit, onNavigateToAllAlbums: () -> Unit = {}) {
    if (albums.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
                .padding(vertical = 24.dp)
        ) {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Albums",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyRow(
modifier = Modifier.physicsBounceOverscroll(isHorizontal = true),

            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums.size) { index ->
                val album = albums[index]
                ArtistAlbumItem(
                    title = album.title,
                    year = "${album.trackCount} tracks",
                    imageUrl = album.artworkUri ?: "",
                    onClick = { onNavigateToAlbum(album.id) }
                )
            }
        }
    }
}
}

@Composable
fun ArtistAlbumItem(title: String, year: String, imageUrl: String, onClick: () -> Unit = {}) {
    Column(modifier = Modifier.width(140.dp).jellyClick { onClick() }) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
                .padding(8.dp)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = year,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun ArtistAboutSection(artist: Artist?) {
    var expanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    val bio = artist?.bioText ?: "No artist info available yet."
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 32.dp)
    ) {
        Text(
            text = "About",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
                .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                lineHeight = 24.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    if (!expanded && textLayoutResult.hasVisualOverflow) {
                        hasOverflow = true
                    }
                }
            )
            
            if (hasOverflow && !expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .jellyClick { expanded = true }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistImageSearchBottomSheet(
    artistName: String,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onImageSelected: (String) -> Unit
) {
    var images by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(artistName) {
        viewModel.searchArtistImagesOnInternet(artistName).collect { result ->
            images = result
        }
    }

    ArcModalBottomSheet(
        currentSheet = "IMAGE_SEARCH",
        onDismissRequest = onDismiss
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Select an Image",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val currentImages = images
            if (currentImages == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.physicsBounceOverscroll().heightIn(max = 400.dp)
                ) {
                    items(9) {
                        SkeletonImageGridItem()
                    }
                }
            } else if (currentImages.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No images found on Deezer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.physicsBounceOverscroll().heightIn(max = 400.dp)
                ) {
                    items(currentImages) { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .jellyClick { onImageSelected(imageUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistDetailsSkeleton(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 80.dp, bottom = 32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f/3f)
                            .clip(RoundedCornerShape(48.dp))
                            .shimmerLoading()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerLoading()
                    )
                }
            }
            
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .padding(horizontal = 24.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerLoading()
                )
                Spacer(modifier = Modifier.height(16.dp))
                repeat(3) {
                    TrackListItemSkeleton(showCover = false, showTrackNumber = true)
                }
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f))
            )
        }
    }
}
