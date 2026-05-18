package com.example.mediaplaybackcontrol

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity(), MediaUtils.MediaUpdateListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvMetadata: TextView
    private lateinit var tvSource: TextView
    private lateinit var tvSessionInfo: TextView
    private lateinit var tvActions: TextView
    private lateinit var btnPlayPause: Button
    private lateinit var seekBar: SeekBar
    private lateinit var mediaUtils: MediaUtils

    private var isUserSeeking = false

    private val handler = Handler(Looper.getMainLooper())
    private val updatePositionRunnable = object : Runnable {
        override fun run() {
            mediaUtils.getActiveController()?.let { controller ->
                updatePlaybackStatus(controller.playbackState, controller)
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tv_status)
        tvMetadata = findViewById(R.id.tv_metadata)
        tvSource = findViewById(R.id.tv_source)
        tvSessionInfo = findViewById(R.id.tv_session_info)
        tvActions = findViewById(R.id.tv_actions)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        seekBar = findViewById(R.id.seekbar_progress)
        
        mediaUtils = MediaUtils.getInstance(this)

        setupButtons()
        setupSeekBar()

        findViewById<Button>(R.id.btn_permission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        mediaUtils.setListener(this)
        if (isNotificationServiceEnabled()) {
            mediaUtils.register(this)
        } else {
            tvStatus.text = "Status: Permission required"
            tvMetadata.text = "Please grant Notification Access in Settings"
        }
    }

    override fun onPause() {
        super.onPause()
        mediaUtils.setListener(null)
        mediaUtils.unregister()
        handler.removeCallbacks(updatePositionRunnable)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, MediaUtils.MediaControlService::class.java).flattenToString()
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabledListeners != null && enabledListeners.contains(expectedComponentName)
    }

    override fun onPlaybackStatusChanged(state: PlaybackState?, controller: MediaController?) {
        updatePlaybackStatus(state, controller)
        if (state?.state == PlaybackState.STATE_PLAYING) {
            handler.post(updatePositionRunnable)
        } else {
            handler.removeCallbacks(updatePositionRunnable)
        }
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        if (metadata != null) {
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "Unknown Album"
            tvMetadata.text = "$title\n$artist\n$album"
        } else {
            tvMetadata.text = "Metadata: None"
        }
    }

    override fun onSessionDataUpdated(controller: MediaController?) {
        tvSource.text = "App: ${controller?.packageName ?: "None"}"
        controller?.let {
            val info = StringBuilder()
            info.append("Session Token: ${it.sessionToken}\n")
            info.append("Rating Type: ${it.ratingType}\n")
            it.extras?.let { extras ->
                info.append("Extras Count: ${extras.size()}\n")
                for (key in extras.keySet()) {
                    info.append(" - $key: ${extras.get(key)}\n")
                }
            }
            tvSessionInfo.text = info.toString()
        } ?: run {
            tvSessionInfo.text = "Session Info: None"
        }
    }

    private fun updatePlaybackStatus(state: PlaybackState?, controller: MediaController?) {
        val statusText = when (state?.state) {
            PlaybackState.STATE_PLAYING -> {
                btnPlayPause.text = "Pause"
                "Playing"
            }
            PlaybackState.STATE_PAUSED -> {
                btnPlayPause.text = "Play"
                "Paused"
            }
            PlaybackState.STATE_STOPPED -> {
                btnPlayPause.text = "Play"
                "Stopped"
            }
            PlaybackState.STATE_BUFFERING -> "Buffering..."
            PlaybackState.STATE_ERROR -> "Error"
            else -> {
                btnPlayPause.text = "Play"
                "Unknown"
            }
        }
        
        val position = state?.position ?: 0L
        val duration = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val speed = state?.playbackSpeed ?: 1.0f
        
        val timeInfo = "${MediaUtils.formatTime(position)} / ${MediaUtils.formatTime(duration)}"
        tvStatus.text = "$statusText ($timeInfo)"
        
        if (!isUserSeeking && duration > 0) {
            seekBar.max = duration.toInt()
            seekBar.progress = position.toInt()
        }

        val actions = state?.actions ?: 0L
        val actionList = mutableListOf<String>()
        if (actions and PlaybackState.ACTION_PLAY != 0L) actionList.add("PLAY")
        if (actions and PlaybackState.ACTION_PAUSE != 0L) actionList.add("PAUSE")
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) actionList.add("NEXT")
        if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) actionList.add("PREV")
        if (actions and PlaybackState.ACTION_SEEK_TO != 0L) actionList.add("SEEK")
        tvActions.text = "Supported: ${actionList.joinToString(", ")}"
    }

    private fun setupButtons() {
        btnPlayPause.setOnClickListener { mediaUtils.playPause() }
        findViewById<Button>(R.id.btn_stop).setOnClickListener { mediaUtils.stop() }
        findViewById<Button>(R.id.btn_next).setOnClickListener { mediaUtils.next() }
        findViewById<Button>(R.id.btn_prev).setOnClickListener { mediaUtils.previous() }
        findViewById<Button>(R.id.btn_fast_forward).setOnClickListener { mediaUtils.fastForward() }
        findViewById<Button>(R.id.btn_rewind).setOnClickListener { mediaUtils.rewind() }
        findViewById<Button>(R.id.btn_vol_up).setOnClickListener { mediaUtils.volumeUp() }
        findViewById<Button>(R.id.btn_vol_down).setOnClickListener { mediaUtils.volumeDown() }
        findViewById<Button>(R.id.btn_mute).setOnClickListener { mediaUtils.toggleMute() }
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = mediaUtils.getActiveController()?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    tvStatus.text = "Seeking: ${MediaUtils.formatTime(progress.toLong())} / ${MediaUtils.formatTime(duration)}"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                seekBar?.let {
                    mediaUtils.seekTo(it.progress.toLong())
                }
            }
        })
    }
}
