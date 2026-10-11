package com.aeswox.arcmusic.sharing

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.aeswox.arcmusic.MusicViewModel // Needed for importM3uPlaylist if available from a static context, or we can broadcast it. But actually MusicViewModel is a ViewModel. We might need a different way to import.
import com.aeswox.arcmusic.db.MusicRepository
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import android.content.ContentValues
import android.provider.MediaStore
import android.content.Intent

enum class SharingState {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTED,
    TRANSFERRING,
    COMPLETED,
    ERROR
}

data class DiscoveredEndpoint(val id: String, val name: String)
data class ConnectionRequest(val endpointId: String, val endpointName: String, val authCode: String)

@Singleton
class NearbySharingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val importMediaUseCase: ImportMediaUseCase
) {
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val serviceId = "com.aeswox.arcmusic.SERVICE_ID"
    private val prefs = context.getSharedPreferences("nearby_sharing_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = prefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
        set(value) {
            prefs.edit().putString("device_name", value).apply()
        }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val processingJobs = mutableListOf<kotlinx.coroutines.Job>()

    private val _sharingState = MutableStateFlow(SharingState.IDLE)
    val sharingState: StateFlow<SharingState> = _sharingState.asStateFlow()

    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress.asStateFlow()
    
    private val _totalTransferCount = MutableStateFlow(0)
    val totalTransferCount: StateFlow<Int> = _totalTransferCount.asStateFlow()

    private val _completedTransferCount = MutableStateFlow(0)
    val completedTransferCount: StateFlow<Int> = _completedTransferCount.asStateFlow()
    
    private val _currentTransferTitle = MutableStateFlow<String?>(null)
    val currentTransferTitle: StateFlow<String?> = _currentTransferTitle.asStateFlow()
    
    private val _currentTransferArtworkB64 = MutableStateFlow<String?>(null)
    val currentTransferArtworkB64: StateFlow<String?> = _currentTransferArtworkB64.asStateFlow()

    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _incomingSenderName = MutableStateFlow<String?>(null)
    val incomingSenderName: StateFlow<String?> = _incomingSenderName.asStateFlow()

    private val _discoveredEndpoints = MutableStateFlow<List<DiscoveredEndpoint>>(emptyList())
    val discoveredEndpoints: StateFlow<List<DiscoveredEndpoint>> = _discoveredEndpoints.asStateFlow()

    private val _connectionRequest = MutableStateFlow<ConnectionRequest?>(null)
    val connectionRequest: StateFlow<ConnectionRequest?> = _connectionRequest.asStateFlow()

    private var currentPayload: SharePayload? = null
    private var isInitiator = false
    @Volatile private var passiveListeningRequested = false
    @Volatile private var passiveListeningShowsState = false
    @Volatile private var passiveAdvertising = false
    @Volatile private var passiveDiscovering = false
    @Volatile private var passiveSessionStarting = false
    @Volatile private var pendingEndpointId: String? = null
    private var passiveRetryJob: kotlinx.coroutines.Job? = null

    /** Keeps Nearby ready for NFC initiated connections while the app is in the foreground. */
    fun setPassiveListening(enabled: Boolean, showState: Boolean) {
        passiveListeningRequested = enabled
        passiveListeningShowsState = showState
        if (!enabled) {
            passiveRetryJob?.cancel()
            passiveRetryJob = null
            stopAdvertising()
            stopDiscovery()
            passiveAdvertising = false
            passiveDiscovering = false
            return
        }
        ensurePassiveListening()
    }

    private fun ensurePassiveListening() {
        if (!passiveListeningRequested || activeEndpointId != null ||
            _sharingState.value == SharingState.CONNECTED ||
            _sharingState.value == SharingState.TRANSFERRING ||
            // Always block re-entry on COMPLETED regardless of showState — without this, the passive
            // session restarts before the previous Nearby session finishes tearing down, causing a
            // silent ALREADY_ADVERTISING / ALREADY_DISCOVERING failure on the next tap.
            _sharingState.value == SharingState.COMPLETED) return
        if ((passiveAdvertising && passiveDiscovering) || passiveSessionStarting) return

        // Start both sides before waiting for callbacks; a failure in either side retries the pair.
        isInitiator = false
        passiveSessionStarting = true
        startAdvertising(updateState = false)
        startDiscovery(updateState = false)
        passiveRetryJob?.cancel()
        passiveRetryJob = coroutineScope.launch {
            delay(1_500)
            if (passiveListeningRequested && (!passiveAdvertising || !passiveDiscovering)) {
                passiveSessionStarting = false
                stopAdvertising()
                stopDiscovery()
                passiveAdvertising = false
                passiveDiscovering = false
                delay(500)
                ensurePassiveListening()
            } else {
                passiveSessionStarting = false
                if (passiveListeningShowsState && _sharingState.value == SharingState.IDLE) {
                    _sharingState.value = SharingState.DISCOVERING
                }
            }
        }
    }

    // Store expected metadata by Payload ID (for receiving)
    private val expectedMetadata = mutableMapOf<Long, JSONObject>()
    // Store sent payloads to track when to delete temp zips
    private val sentFiles = mutableMapOf<Long, File>()

    @Volatile
    private var expectedNfcToken: String? = null
    
    private val discoveredNfcTokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    
    fun connectViaNfcToken(token: String) {
        expectedNfcToken = token

        // Only match against endpoints that are currently live in the discovery list.
        // discoveredNfcTokens is intentionally not cleared on session restart (Fix 2), so it can
        // hold entries from a previous Nearby session whose endpoint IDs are no longer valid.
        // Cross-referencing with _discoveredEndpoints filters those stale entries out.
        val activeIds = _discoveredEndpoints.value.map { it.id }.toSet()
        val match = discoveredNfcTokens.entries.find { it.key in activeIds && it.value == token }
        if (match != null) {
            requestConnection(match.key)
        }
    }

    fun setPayload(payload: SharePayload) {
        currentPayload = payload
    }

    /** Call before opening ShareScreen to ensure a clean state for a new session. */
    fun reset() {
        activeEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
        activeEndpointId = null
        currentPayload = null
        payloadQueue.clear()
        activePayloads.clear()
        sentFiles.clear()
        expectedMetadata.clear()
        incomingFiles.clear()
        incomingUris.clear()
        totalFileCount = 0
        completedFileCount = 0
        filePayloadIds.clear()
        _sharingState.value = SharingState.IDLE
        _transferProgress.value = 0f
        _totalTransferCount.value = 0
        _completedTransferCount.value = 0
        _currentTransferTitle.value = null
        _currentTransferArtworkB64.value = null
        _isReceiving.value = false
        _incomingSenderName.value = null
        _connectionRequest.value = null
        expectedNfcToken = null
        updateTransferService(SharingState.IDLE)
        ensurePassiveListening()
    }

    private val payloadQueue = mutableListOf<com.aeswox.arcmusic.db.entities.Track>()
    @Volatile
    private var activeEndpointId: String? = null

    // Unified progress tracking across all files in a batch
    private var totalFileCount = 0
    private var completedFileCount = 0
    private val filePayloadIds = mutableSetOf<Long>() // only actual file payloads, not metadata bytes

    private fun processNextInQueue() {
        val endpointId = activeEndpointId ?: return
        if (payloadQueue.isEmpty()) {
            // All tracks sent — switch to COMPLETED and disconnect
            _sharingState.value = SharingState.COMPLETED
            updateTransferService(SharingState.IDLE)
            connectionsClient.disconnectFromEndpoint(endpointId)
            activeEndpointId = null
            currentPayload = null
            totalFileCount = 0
            completedFileCount = 0
            filePayloadIds.clear()
            ensurePassiveListening()
            return
        }
        val track = payloadQueue.removeAt(0)
        sendTrackFile(endpointId, track)
    }

    private fun updateTransferService(state: SharingState, progress: Float = 0f) {
        val intent = Intent(context, NearbyTransferService::class.java)
        if (state == SharingState.TRANSFERRING) {
            intent.putExtra("PROGRESS", (progress * 100).toInt())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            intent.action = "STOP"
            context.startService(intent)
        }
    }

    private val incomingFiles = mutableMapOf<Long, File>()
    private val incomingUris = mutableMapOf<Long, android.net.Uri>()
    private val activePayloads = mutableSetOf<Long>()

    private val actualPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // This callback only runs for payloads received from the remote endpoint.
            // The local device may still have initiated the Nearby connection (NFC tap flow).
            _isReceiving.value = true
            activePayloads.add(payload.id)
            _sharingState.value = SharingState.TRANSFERRING
            updateTransferService(SharingState.TRANSFERRING, 0f)
            
            if (payload.type == Payload.Type.BYTES) {
                val jsonStr = String(payload.asBytes()!!, Charsets.UTF_8)
                try {
                    val json = JSONObject(jsonStr)
                    if (json.has("payloadId")) {
                        expectedMetadata[json.getLong("payloadId")] = json
                        val type = json.optString("type")
                        if (json.has("totalCount")) {
                            totalFileCount = json.getInt("totalCount")
                            _totalTransferCount.value = totalFileCount
                        }
                        if (type == "track") {
                            _currentTransferTitle.value = json.optString("title")
                            if (json.has("thumbnailB64")) {
                                _currentTransferArtworkB64.value = json.optString("thumbnailB64")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (payload.type == Payload.Type.FILE) {
                filePayloadIds.add(payload.id)
                payload.asFile()?.asUri()?.let { uri ->
                    incomingUris[payload.id] = uri
                } ?: payload.asFile()?.asJavaFile()?.let { file ->
                    incomingFiles[payload.id] = file
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS) {
                _sharingState.value = SharingState.TRANSFERRING
                // Only update progress for actual file payloads (not tiny metadata bytes-payloads)
                if (update.payloadId in filePayloadIds && update.totalBytes > 0 && totalFileCount > 0) {
                    val fileProgress = update.bytesTransferred.toFloat() / update.totalBytes.toFloat()
                    val unified = (completedFileCount + fileProgress) / totalFileCount
                    _transferProgress.value = unified
                    updateTransferService(SharingState.TRANSFERRING, unified)
                }
            } else if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                activePayloads.remove(update.payloadId)
                if (update.payloadId in filePayloadIds) {
                    filePayloadIds.remove(update.payloadId)
                    completedFileCount++
                    _completedTransferCount.value = completedFileCount
                    if (totalFileCount > 0) {
                        val unified = completedFileCount.toFloat() / totalFileCount
                        _transferProgress.value = unified
                        updateTransferService(SharingState.TRANSFERRING, unified)
                    }
                }
                
                val receivedUri = incomingUris[update.payloadId]
                val receivedFile = incomingFiles[update.payloadId]
                
                if (receivedUri != null || receivedFile != null) {
                    val metadata = expectedMetadata[update.payloadId]
                    if (metadata != null) {
                        val job = coroutineScope.launch {
                            importMediaUseCase.processReceivedPayload(receivedUri, receivedFile, metadata)
                        }
                        processingJobs.add(job)
                        expectedMetadata.remove(update.payloadId)
                    }
                    incomingUris.remove(update.payloadId)
                    incomingFiles.remove(update.payloadId)
                }

                if (activePayloads.isEmpty()) {
                    if (payloadQueue.isNotEmpty()) {
                        processNextInQueue()
                    } else if (totalFileCount > 0 && completedFileCount < totalFileCount) {
                        // Receiver side: batch is incomplete, wait for the next payload
                    } else {
                        // All payloads processed — finalize and reset connection
                        coroutineScope.launch {
                            kotlinx.coroutines.joinAll(*processingJobs.toTypedArray())
                            processingJobs.clear()
                            importMediaUseCase.finalizeImport()
                            _sharingState.value = SharingState.COMPLETED
                            activeEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
                            activeEndpointId = null
                            updateTransferService(SharingState.IDLE)
                            ensurePassiveListening()
                        }
                    }
                }
                
                sentFiles[update.payloadId]?.let { file ->
                    if (file.exists() && file.name.endsWith(".zip")) {
                        file.delete()
                    }
                    sentFiles.remove(update.payloadId)
                }
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE || update.status == PayloadTransferUpdate.Status.CANCELED) {
                incomingUris.remove(update.payloadId)
                incomingFiles[update.payloadId]?.let { file ->
                    if (file.exists()) file.delete()
                }
                incomingFiles.remove(update.payloadId)
                
                sentFiles[update.payloadId]?.let { file ->
                    if (file.exists() && file.name.endsWith(".zip")) {
                        file.delete()
                    }
                }
                sentFiles.remove(update.payloadId)
                
                activePayloads.remove(update.payloadId)
                if (activePayloads.isEmpty()) {
                    _sharingState.value = SharingState.IDLE
                    _isReceiving.value = false
                    updateTransferService(SharingState.IDLE)
                    activeEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
                    activeEndpointId = null
                    ensurePassiveListening()
                }
            }
        }
    }



    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (isInitiator) {
                _incomingSenderName.value = info.endpointName.substringBefore("|")
                acceptConnection(endpointId)
            } else {
                // Sender side logic: Check if the receiver passed back our exact token in their name
                val receiverToken = if (info.endpointName.contains("|")) info.endpointName.split("|").getOrNull(1) else null
                val currentToken = com.aeswox.arcmusic.sharing.NfcShareService.currentToken
                val cleanName = if (info.endpointName.contains("|")) info.endpointName.substringBefore("|") else info.endpointName
                _incomingSenderName.value = cleanName

                if (receiverToken != null && currentToken != null && receiverToken == currentToken) {
                    acceptConnection(endpointId) // Seamless tap-to-share!
                } else {
                    _connectionRequest.value = ConnectionRequest(endpointId, cleanName, info.authenticationToken)
                }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingEndpointId = null
            if (result.status.isSuccess) {
                // Token has served its purpose — clear it now that we're connected.
                // Deliberately NOT cleared in requestConnection() so that a failed attempt
                // leaves expectedNfcToken intact for onEndpointFound to auto-retry.
                expectedNfcToken = null
                activeEndpointId = endpointId
                _sharingState.value = SharingState.CONNECTED
                passiveAdvertising = false
                passiveDiscovering = false
                stopDiscovery()
                stopAdvertising()

                // If we have a payload to send, start preparing and sending it
                currentPayload?.let { payload ->
                    sendPayloadPackage(endpointId, payload)
                }
            } else {
                activeEndpointId = null
                _sharingState.value = SharingState.ERROR
                updateTransferService(SharingState.ERROR)
                ensurePassiveListening()
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (pendingEndpointId == endpointId) pendingEndpointId = null
            isInitiator = false
            if (_sharingState.value != SharingState.COMPLETED) {
                _sharingState.value = SharingState.IDLE
                _transferProgress.value = 0f
                _isReceiving.value = false
                updateTransferService(SharingState.IDLE)
                activeEndpointId = null
                ensurePassiveListening()
            } else {
                // Transfer completed — leave state as COMPLETED so the UI completion card stays
                // visible, then resume passive listening after a short grace period.
                // ensurePassiveListening() would be blocked on COMPLETED immediately, so we
                // schedule it after clearing the state ourselves.
                coroutineScope.launch {
                    delay(3_000)
                    if (_sharingState.value == SharingState.COMPLETED) {
                        _sharingState.value = SharingState.IDLE
                    }
                    ensurePassiveListening()
                }
            }
        }
    }

    private fun sendPayloadPackage(endpointId: String, payload: SharePayload) {
        coroutineScope.launch {
            activeEndpointId = endpointId
            payloadQueue.clear()
            try {
                when (payload) {
                    is SharePayload.SingleTrack -> {
                        val track = repository.getTrackById(payload.trackId)
                        if (track != null) {
                            payloadQueue.add(track)
                        }
                    }
                    is SharePayload.MultipleTracks -> {
                        for (id in payload.trackIds) {
                            val track = repository.getTrackById(id)
                            if (track != null) {
                                payloadQueue.add(track)
                            }
                        }
                    }
                    is SharePayload.Artist -> {
                        val artist = repository.getArtistById(payload.artistId).first()
                        if (artist != null) {
                            val tracks = repository.getTracksByArtist(artist.name).first()
                            payloadQueue.addAll(tracks)
                        }
                    }
                    is SharePayload.Album -> {
                        val album = repository.getAlbumById(payload.albumId).first()
                        if (album != null) {
                            val tracks = repository.getTracksByAlbum(album.title).first()
                            payloadQueue.addAll(tracks)
                        }
                    }
                    is SharePayload.Playlist -> {
                        val tracks = repository.getTracksForPlaylist(payload.playlistId).first()
                        if (tracks.isNotEmpty()) {
                            val sb = StringBuilder()
                            sb.append("#EXTM3U\n")
                            sb.append("#PLAYLIST:${payload.playlistId}\n")
                            for (track in tracks) {
                                val durationSec = track.durationMs / 1000
                                sb.append("#EXTINF:${durationSec},${track.artist} - ${track.title}\n")
                                val file = File(track.filePath)
                                sb.append("${file.name}\n")
                            }
                            
                            val m3uFile = File(context.cacheDir, "playlist_${System.currentTimeMillis()}.m3u")
                            m3uFile.writeText(sb.toString())
                            val m3uPayload = Payload.fromFile(m3uFile)
                            
                            val m3uMetadata = JSONObject().apply {
                                put("type", "playlist_m3u")
                                put("payloadId", m3uPayload.id)
                                put("playlistName", payload.playlistId)
                                put("totalCount", tracks.size + 1)
                            }
                            
                            val metadataPayload = Payload.fromBytes(m3uMetadata.toString().toByteArray(Charsets.UTF_8))
                            activePayloads.add(metadataPayload.id)
                            activePayloads.add(m3uPayload.id)
                            _sharingState.value = SharingState.TRANSFERRING
                            
                            connectionsClient.sendPayload(endpointId, metadataPayload)
                            connectionsClient.sendPayload(endpointId, m3uPayload)
                            
                            payloadQueue.addAll(tracks)
                            
                            totalFileCount = tracks.size + 1
                            completedFileCount = 0
                            _totalTransferCount.value = totalFileCount
                            _completedTransferCount.value = completedFileCount
                            filePayloadIds.clear()
                            filePayloadIds.add(m3uPayload.id)
                        } else {
                            totalFileCount = 0
                            completedFileCount = 0
                            _totalTransferCount.value = totalFileCount
                            _completedTransferCount.value = completedFileCount
                            filePayloadIds.clear()
                        }
                    }
                }
                
                if (payload !is SharePayload.Playlist) {
                    // Initialise unified progress counters now that we know the full batch size
                    totalFileCount = payloadQueue.size
                    completedFileCount = 0
                    _totalTransferCount.value = totalFileCount
                    _completedTransferCount.value = completedFileCount
                    filePayloadIds.clear()
                }

                if (activePayloads.isEmpty() && payloadQueue.isNotEmpty()) {
                    processNextInQueue()
                }
            } catch (e: Exception) {
                Log.e("NearbySharingManager", "Failed to send payload", e)
            }
        }
    }

    private fun sendTrackFile(endpointId: String, track: com.aeswox.arcmusic.db.entities.Track) {
        val file = File(track.filePath)
        if (file.exists()) {
            val filePayload = Payload.fromFile(file)
            filePayloadIds.add(filePayload.id) // register so progress is tracked as a file payload
            
            var thumbnailB64: String? = null
            try {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(file.absolutePath)
                val picture = mmr.embeddedPicture
                if (picture != null) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size)
                    // Scale to a higher resolution (400x400) for crispness on completion cards
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 400, 400, true)
                    val out = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 65, out)
                    val bytes = out.toByteArray()
                    if (bytes.size < 24000) {
                        // Base64 overhead is ~33%. 24KB bytes -> ~32KB string.
                        // Nearby connections MAX_BYTES_DATA_SIZE limit is 32768.
                        thumbnailB64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    } else {
                        // Fallback to a smaller size if compression wasn't enough to fit the limit
                        out.reset()
                        val smaller = android.graphics.Bitmap.createScaledBitmap(bmp, 200, 200, true)
                        smaller.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, out)
                        thumbnailB64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    }
                }
                mmr.release()
            } catch (e: Exception) {
                // Ignore extraction failures
            }
            
            val metadata = JSONObject().apply {
                put("payloadId", filePayload.id)
                put("type", "track")
                put("title", track.title)
                put("ext", file.extension)
                put("filename", file.name)
                put("totalCount", totalFileCount)
                if (thumbnailB64 != null) {
                    put("thumbnailB64", thumbnailB64)
                }
            }
            val metadataPayload = Payload.fromBytes(metadata.toString().toByteArray(Charsets.UTF_8))
            activePayloads.add(metadataPayload.id)
            activePayloads.add(filePayload.id)
            _sharingState.value = SharingState.TRANSFERRING
            
            connectionsClient.sendPayload(endpointId, metadataPayload)
            connectionsClient.sendPayload(endpointId, filePayload)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val currentList = _discoveredEndpoints.value.toMutableList()
            var displayName = info.endpointName
            var token: String? = null
            if (displayName.contains("|")) {
                val parts = displayName.split("|")
                displayName = parts[0]
                token = parts.getOrNull(1)
            }
            
            if (token != null) {
                discoveredNfcTokens[endpointId] = token
            }
            
            currentList.add(DiscoveredEndpoint(endpointId, displayName))
            _discoveredEndpoints.value = currentList
            
            // Auto connect if this endpoint matches our scanned NFC token
            if (token != null && token == expectedNfcToken) {
                requestConnection(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            discoveredNfcTokens.remove(endpointId)
            val currentList = _discoveredEndpoints.value.toMutableList()
            currentList.removeAll { it.id == endpointId }
            _discoveredEndpoints.value = currentList
        }
    }

    fun startAdvertising(nfcToken: String? = null, updateState: Boolean = true) {
        isInitiator = false
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        val nameToAdvertise = if (nfcToken != null) "$userName|$nfcToken" else userName
        connectionsClient.startAdvertising(
            nameToAdvertise, serviceId, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener {
            // Mark passive slot as filled whether this is a tokenless passive ad OR a token-bearing
            // ShareScreen ad. Without this, ensurePassiveListening sees passiveAdvertising==false
            // and restarts advertising without the token, stripping it from the endpoint name and
            // breaking the auto-accept handshake on the receiver side.
            if (!updateState || nfcToken != null) passiveAdvertising = true
            if (!updateState && passiveDiscovering) passiveSessionStarting = false
            if (updateState && nfcToken == null) _sharingState.value = SharingState.ADVERTISING
        }.addOnFailureListener {
            if (!updateState || nfcToken != null) passiveAdvertising = false
            if (!updateState) passiveSessionStarting = false
            if (updateState && nfcToken == null) _sharingState.value = SharingState.ERROR
            Log.e("NearbySharingManager", "Failed to start advertising", it)
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        passiveAdvertising = false
        if (_sharingState.value == SharingState.ADVERTISING) {
            _sharingState.value = SharingState.IDLE
        }
    }

    fun startDiscovery(updateState: Boolean = true) {
        _discoveredEndpoints.value = emptyList()
        // Do NOT clear discoveredNfcTokens here — the cache must survive passive-session restarts
        // so that an NFC tap can immediately match a pre-discovered endpoint instead of waiting
        // for Nearby to rediscover it (which takes 10-20 s). Entries are evicted in onEndpointLost.
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(
            serviceId, endpointDiscoveryCallback, discoveryOptions
        ).addOnSuccessListener {
            if (!updateState) passiveDiscovering = true
            if (!updateState && passiveAdvertising) passiveSessionStarting = false
            if (updateState) _sharingState.value = SharingState.DISCOVERING
        }.addOnFailureListener {
            if (!updateState) passiveDiscovering = false
            if (!updateState) passiveSessionStarting = false
            if (updateState) _sharingState.value = SharingState.ERROR
            Log.e("NearbySharingManager", "Failed to start discovery", it)
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        passiveDiscovering = false
        if (_sharingState.value == SharingState.DISCOVERING) {
            _sharingState.value = SharingState.IDLE
        }
    }

    fun requestConnection(endpointId: String) {
        if (pendingEndpointId == endpointId || activeEndpointId != null ||
            _sharingState.value == SharingState.CONNECTED || _sharingState.value == SharingState.TRANSFERRING) return
        pendingEndpointId = endpointId
        // Stop discovery once the NFC token resolves to a peer; keep advertising for the peer's request.
        stopDiscovery()
        isInitiator = true
        // Send the NFC token back in our name so the sender can auto-accept without a dialog.
        val nameToSend = if (expectedNfcToken != null) "$userName|$expectedNfcToken" else userName
        connectionsClient.requestConnection(nameToSend, endpointId, connectionLifecycleCallback)
            .addOnFailureListener {
                pendingEndpointId = null
                _sharingState.value = SharingState.ERROR
                Log.e("NearbySharingManager", "Failed to request connection", it)
                ensurePassiveListening()
            }
        // Do NOT clear expectedNfcToken here. If this attempt fails, ensurePassiveListening will
        // restart discovery and onEndpointFound will auto-retry when the sender is re-discovered.
        // expectedNfcToken is cleared in onConnectionResult(success) instead.
    }

    fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, actualPayloadCallback)
        _connectionRequest.value = null
    }

    fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
        _connectionRequest.value = null
    }

    fun disconnect(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
        _sharingState.value = SharingState.IDLE
        _transferProgress.value = 0f
        updateTransferService(SharingState.IDLE)
        activeEndpointId = null
        ensurePassiveListening()
    }

    fun cancelTransfer() {
        val payloadIds = mutableSetOf<Long>()
        payloadIds.addAll(expectedMetadata.keys)
        payloadIds.addAll(sentFiles.keys)
        payloadIds.addAll(incomingFiles.keys)
        for (id in payloadIds) {
            connectionsClient.cancelPayload(id)
        }
        activeEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
        activeEndpointId = null
        
        _sharingState.value = SharingState.IDLE
        _transferProgress.value = 0f
        _currentTransferTitle.value = null
        updateTransferService(SharingState.IDLE)
        activeEndpointId = null
        ensurePassiveListening()
    }


}
