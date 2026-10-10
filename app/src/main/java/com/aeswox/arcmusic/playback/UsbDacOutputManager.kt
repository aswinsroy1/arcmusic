package com.aeswox.arcmusic.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.audio.AudioSink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Routes Media3 audio to a connected USB output using Android's supported audio-device API. */
@Singleton
class UsbDacOutputManager @Inject constructor(
    @ApplicationContext context: Context
) {
    enum class State { DISABLED, WAITING_FOR_DEVICE, CONNECTED }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioSink: AudioSink? = null
    private var enabled = false
    private var registered = false

    private val _state = MutableStateFlow(State.DISABLED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = updateRoute()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = updateRoute()
    }

    fun register() {
        if (registered) return
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        registered = true
        updateRoute()
    }

    fun unregister() {
        if (!registered) return
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        registered = false
        audioSink?.setPreferredDevice(null)
        audioSink = null
        _state.value = State.DISABLED
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        updateRoute()
    }

    fun attachSink(sink: AudioSink) {
        audioSink = sink
        updateRoute()
    }

    fun detachSink() {
        audioSink?.setPreferredDevice(null)
        audioSink = null
        updateRoute()
    }

    private fun updateRoute() {
        val device = if (enabled) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
        } else {
            null
        }

        audioSink?.setPreferredDevice(device)
        _state.value = when {
            !enabled -> State.DISABLED
            device != null -> State.CONNECTED
            else -> State.WAITING_FOR_DEVICE
        }
    }
}
