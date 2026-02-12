package com.example.mediaplaybackcontrol

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.util.Log

class MediaUtils private constructor(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSessionManager = context.applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var activeController: MediaController? = null
    private var listener: MediaUpdateListener? = null

    // Cached media state
    var currentPlaybackState: PlaybackState? = null
        private set
    var currentMetadata: MediaMetadata? = null
        private set

    interface MediaUpdateListener {
        fun onPlaybackStatusChanged(state: PlaybackState?, controller: MediaController?)
        fun onMetadataChanged(metadata: MediaMetadata?)
        fun onSessionDataUpdated(controller: MediaController?)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            currentPlaybackState = state
            listener?.onPlaybackStatusChanged(state, activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            currentMetadata = metadata
            listener?.onMetadataChanged(metadata)
        }

        override fun onSessionDestroyed() {
            updateActiveController(null)
        }

        override fun onExtrasChanged(extras: Bundle?) {
            listener?.onSessionDataUpdated(activeController)
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers?.firstOrNull())
    }

    fun setListener(listener: MediaUpdateListener?) {
        this.listener = listener
        if (listener != null) {
            listener.onPlaybackStatusChanged(currentPlaybackState, activeController)
            listener.onMetadataChanged(currentMetadata)
            listener.onSessionDataUpdated(activeController)
        }
    }

    fun register(context: Context) {
        try {
            val componentName = ComponentName(context, MediaControlService::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            updateActiveController(controllers.firstOrNull())
        } catch (e: SecurityException) {
            Log.e("MediaUtils", "Permission denied: ${e.message}")
            // This happens if Notification Access is not granted for this specific service
            updateActiveController(null)
        }
    }

    fun unregister() {
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            Log.e("MediaUtils", "Error unregistering: ${e.message}")
        }
        activeController?.unregisterCallback(controllerCallback)
    }

    private fun updateActiveController(controller: MediaController?) {
        activeController?.unregisterCallback(controllerCallback)
        activeController = controller
        activeController?.registerCallback(controllerCallback)
        
        currentPlaybackState = activeController?.playbackState
        currentMetadata = activeController?.metadata

        listener?.onPlaybackStatusChanged(currentPlaybackState, activeController)
        listener?.onMetadataChanged(currentMetadata)
        listener?.onSessionDataUpdated(activeController)
    }

    fun getActiveController(): MediaController? = activeController

    fun playPause() {
        activeController?.let {
            if (it.playbackState?.state == PlaybackState.STATE_PLAYING) {
                it.transportControls.pause()
            } else {
                it.transportControls.play()
            }
        }
    }

    fun stop() {
        activeController?.transportControls?.stop()
    }

    fun next() {
        activeController?.transportControls?.skipToNext()
    }

    fun previous() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun fastForward() {
        activeController?.let { controller ->
            val currentPos = controller.playbackState?.position ?: 0
            controller.transportControls.seekTo(currentPos + 10000)
        }
    }

    fun rewind() {
        activeController?.let { controller ->
            val currentPos = controller.playbackState?.position ?: 0
            controller.transportControls.seekTo((currentPos - 10000).coerceAtLeast(0))
        }
    }

    fun volumeUp() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    fun volumeDown() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    fun toggleMute() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
    }

    fun getCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun getMaxVolume(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    fun isMuted(): Boolean {
        return audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
    }

    fun setVolume(volume: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
    }

    class MediaControlService : NotificationListenerService()

    companion object {
        @Volatile
        private var instance: MediaUtils? = null

        fun getInstance(context: Context): MediaUtils {
            return instance ?: synchronized(this) {
                instance ?: MediaUtils(context).also { instance = it }
            }
        }

        fun getInstance(): MediaUtils {
            return instance ?: throw IllegalStateException("MediaUtils must be initialized with context first (e.g. in MainActivity)")
        }

        fun formatTime(ms: Long): String {
            if (ms < 0) return "00:00"
            val seconds = (ms / 1000) % 60
            val minutes = (ms / (1000 * 60)) % 60
            val hours = (ms / (1000 * 60 * 60))
            return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
            else String.format("%02d:%02d", minutes, seconds)
        }
    }
}
