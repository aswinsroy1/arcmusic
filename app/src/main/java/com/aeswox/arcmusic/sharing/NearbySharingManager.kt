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

    private val _discoveredEndpoints = MutableStateFlow<List<DiscoveredEndpoint>>(emptyList())
    val discoveredEndpoints: StateFlow<List<DiscoveredEndpoint>> = _discoveredEndpoints.asStateFlow()

    private val _connectionRequest = MutableStateFlow<ConnectionRequest?>(null)
    val connectionRequest: StateFlow<ConnectionRequest?> = _connectionRequest.asStateFlow()

    private var currentPayload: SharePayload? = null
    private var isInitiator = false

    // Store expected metadata by Payload ID (for receiving)
    private val expectedMetadata = mutableMapOf<Long, JSONObject>()
    // Store sent payloads to track when to delete temp zips
    private val sentFiles = mutableMapOf<Long, File>()

    @Volatile
    private var expectedNfcToken: String? = null
    
    private val discoveredNfcTokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    
    fun connectViaNfcToken(token: String) {
        expectedNfcToken = token
        
        // Check if we already discovered it before the tap happened
        val match = discoveredNfcTokens.entries.find { it.value == token }
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
        _connectionRequest.value = null
        expectedNfcToken = null
        updateTransferService(SharingState.IDLE)
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
                            
                            activeEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
                            activeEndpointId = null
                            _sharingState.value = SharingState.COMPLETED
                            updateTransferService(SharingState.IDLE)
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
                    updateTransferService(SharingState.IDLE)
                }
            }
        }
    }



    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (isInitiator) {
                acceptConnection(endpointId)
            } else {
                // Sender side logic: Check if the receiver passed back our exact token in their name
                val receiverToken = if (info.endpointName.contains("|")) info.endpointName.split("|").getOrNull(1) else null
                val currentToken = com.aeswox.arcmusic.sharing.NfcShareService.currentToken
                
                if (receiverToken != null && currentToken != null && receiverToken == currentToken) {
                    acceptConnection(endpointId) // Seamless tap-to-share!
                } else {
                    val cleanName = if (info.endpointName.contains("|")) info.endpointName.substringBefore("|") else info.endpointName
                    _connectionRequest.value = ConnectionRequest(endpointId, cleanName, info.authenticationToken)
                }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                _sharingState.value = SharingState.CONNECTED
                stopDiscovery()
                stopAdvertising()
                
                // If we have a payload to send, start preparing and sending it
                currentPayload?.let { payload ->
                    sendPayloadPackage(endpointId, payload)
                }
            } else {
                _sharingState.value = SharingState.ERROR
                updateTransferService(SharingState.ERROR)
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (_sharingState.value != SharingState.COMPLETED) {
                _sharingState.value = SharingState.IDLE
                _transferProgress.value = 0f
                updateTransferService(SharingState.IDLE)
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
                        val tracks = repository.getTracksForPlaylistById(payload.playlistId).first()
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
                            }
                            
                            val metadataPayload = Payload.fromBytes(m3uMetadata.toString().toByteArray(Charsets.UTF_8))
                            activePayloads.add(metadataPayload.id)
                            activePayloads.add(m3uPayload.id)
                            _sharingState.value = SharingState.TRANSFERRING
                            
                            connectionsClient.sendPayload(endpointId, metadataPayload)
                            connectionsClient.sendPayload(endpointId, m3uPayload)
                            
                            payloadQueue.addAll(tracks)
                        }
                    }
                }
                
                // Initialise unified progress counters now that we know the full batch size
                totalFileCount = payloadQueue.size
                completedFileCount = 0
                _totalTransferCount.value = totalFileCount
                _completedTransferCount.value = completedFileCount
                filePayloadIds.clear()

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

    fun startAdvertising(nfcToken: String? = null) {
        isInitiator = false
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        val nameToAdvertise = if (nfcToken != null) "$userName|$nfcToken" else userName
        connectionsClient.startAdvertising(
            nameToAdvertise, serviceId, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener {
            _sharingState.value = SharingState.ADVERTISING
        }.addOnFailureListener {
            _sharingState.value = SharingState.ERROR
            Log.e("NearbySharingManager", "Failed to start advertising", it)
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        if (_sharingState.value == SharingState.ADVERTISING) {
            _sharingState.value = SharingState.IDLE
        }
    }

    fun startDiscovery() {
        _discoveredEndpoints.value = emptyList()
        discoveredNfcTokens.clear()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(
            serviceId, endpointDiscoveryCallback, discoveryOptions
        ).addOnSuccessListener {
            _sharingState.value = SharingState.DISCOVERING
        }.addOnFailureListener {
            _sharingState.value = SharingState.ERROR
            Log.e("NearbySharingManager", "Failed to start discovery", it)
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        if (_sharingState.value == SharingState.DISCOVERING) {
            _sharingState.value = SharingState.IDLE
        }
    }

    fun requestConnection(endpointId: String) {
        isInitiator = true
        // If we are connecting via an NFC token, send it back in our name so the sender can auto-accept
        val nameToSend = if (expectedNfcToken != null) "$userName|$expectedNfcToken" else userName
        connectionsClient.requestConnection(nameToSend, endpointId, connectionLifecycleCallback)
            .addOnFailureListener {
                _sharingState.value = SharingState.ERROR
                Log.e("NearbySharingManager", "Failed to request connection", it)
            }
        expectedNfcToken = null
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
    }


}
