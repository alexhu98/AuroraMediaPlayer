package com.prettygoodcomputing.a4

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.media.session.PlaybackStateCompat
import com.crashlytics.android.Crashlytics
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoListener

class PlayerController(val context: Context, val TAG: String)
    : AudioManager.OnAudioFocusChangeListener {

    private val LOWER_VOLUME = 0.3f
    private val NEAR_BEGINNING = 10 * 1000L
    private val NEAR_ENDING = 2 * 60 * 1000L

    private val repository by lazy { App.getAppRepository() }
    private lateinit var currentFileItem: FileItem
    private val playerEventListener = PlayerEventListener()
    private val player = ExoPlayerFactory.newSimpleInstance(context, DefaultTrackSelector()).apply {
        addListener(playerEventListener)
        repeatMode = Player.REPEAT_MODE_OFF
        setSeekParameters(SeekParameters.NEXT_SYNC)
    }

    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusLostTime = 0L
    private val audioNoisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private val audioNoisyReceiver = AudioNoisyReceiver()
    private var audioNoisyReceiverRegistered = false

    fun getPlayer(): SimpleExoPlayer {
        return player
    }

    fun release() {
        unregisterAudioNoisyReceiver()
        stopPlayer()
        player.removeListener(playerEventListener)
        player.release()
    }

    fun startPlayer(fileItem: FileItem): Boolean {
        val started = requestAudioFocusGranted()
        if (started) {
            currentFileItem = fileItem
            val mediaSource = buildMediaSource(currentFileItem.url)
            player.prepare(mediaSource, true, false)
            var startPosition = calculateStartPosition(currentFileItem)
            if (currentFileItem.finished || currentFileItem.error) {
                currentFileItem.copy().apply {
                    position = startPosition
                    error = false
                    finished = false
                    repository.update(this)
                }
            }
            seekTo(startPosition)
            resumePlayer()
            registerAudioNoisyReceiver()
        }
        return started
    }

    private fun buildMediaSource(url: String): MediaSource {
        Logger.enter(TAG, "buildMediaSource() starting url = $url")
        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, context.resources.getString(R.string.app_name)));
        val mediaFactory = ExtractorMediaSource.Factory(dataSourceFactory)
        val playlist = ConcatenatingMediaSource()
        val mediaSource = mediaFactory.createMediaSource(Uri.parse(url))
        playlist.addMediaSource(mediaSource)

//        val index = mFileList.indexOf(url)
//        if (index > 0) {
//            mFileList = mFileList.subList(index, mFileList.size).plus(mFileList.subList(0, index))
//        }
//        mSortedFileList = DataStore.sortFileList(mFileList, SelectedFolderOptions.OPTIONS_SORT_BY_NAME)
//        mFileList.forEach {
//            Logger.v(TAG, "buildMediaSource() url = $it")
//            var mediaSource: MediaSource = mediaFactory.createMediaSource(Uri.parse(it))
//            playlist.addMediaSource(mediaSource)
//        }
//        mCurrentWindowIndex = 0
        Logger.exit(TAG, "buildMediaSource() playlist.size = ${playlist.size}")
        return playlist
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    fun togglePausePlayer(): Boolean {
        if (player.playWhenReady) {
            pausePlayer()
        }
        else {
            resumePlayer()
        }
        return player.playWhenReady
    }

    fun resumePlayer() {
        player.playWhenReady = true

        val broadcastIntent = Intent()
        broadcastIntent.putExtra(PlayerService.PARAM_PLAYBACK_STATE, PlaybackStateCompat.STATE_PLAYING)
        broadcastIntent.putExtra(PlayerService.PARAM_POSITION, getCurrentPosition())
        localBroadcast("onPlaybackState", broadcastIntent)
    }

    fun pausePlayer() {
        player.playWhenReady = false
        abandonAudioFocusRequest()

        val broadcastIntent = Intent()
        broadcastIntent.putExtra(PlayerService.PARAM_PLAYBACK_STATE, PlaybackStateCompat.STATE_PAUSED)
        broadcastIntent.putExtra(PlayerService.PARAM_POSITION, getCurrentPosition())
        localBroadcast("onPlaybackState", broadcastIntent)
    }

    fun stopPlayer() {
        currentFileItem.copy().apply {
            position = getCurrentPosition()
            repository.update(this)
        }
        player.playWhenReady = false
//        player.stop()
        abandonAudioFocusRequest()

        val broadcastIntent = Intent()
        broadcastIntent.putExtra(PlayerService.PARAM_PLAYBACK_STATE, PlaybackStateCompat.STATE_STOPPED)
        broadcastIntent.putExtra(PlayerService.PARAM_POSITION, getCurrentPosition())
        localBroadcast("onPlaybackState", broadcastIntent)
    }

    fun getCurrentPosition(): Long {
        val position = player.currentPosition
        return if (position == C.TIME_UNSET) 0 else position
    }

    fun getDuration(): Long {
        val duration = player.duration
        return if (duration == C.TIME_UNSET) 0 else duration
    }

    fun lowerVolume() {
        player.volume = LOWER_VOLUME
    }

    fun isPlaying(): Boolean {
        return player.playbackState != Player.STATE_ENDED
                && player.playbackState != Player.STATE_IDLE
                && player.playWhenReady
    }

    fun isSeekRepeat(): Boolean {
        return false
    }

    fun nearBeginning(): Boolean {
        return nearBeginning(getCurrentPosition())
    }

    fun nearEnding(): Boolean {
        return nearEnding(getCurrentPosition(), getDuration())
    }

    fun nearBeginning(position: Long): Boolean {
        return position < NEAR_BEGINNING
    }

    fun nearEnding(position: Long, duration: Long): Boolean {
        return position > 0 && duration > 0 && position + NEAR_ENDING > duration
    }

    private fun calculateStartPosition(fileItem: FileItem): Long {
        val startPosition = if (fileItem.finished || nearBeginning(fileItem.position) || nearEnding(fileItem.position, fileItem.duration)) 0 else fileItem.position
        return startPosition
    }

    private fun requestAudioFocus(focusChangeListener: AudioManager.OnAudioFocusChangeListener, streamType: Int, audioFocusGain: Int): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioFocusRequest != null) {
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        else {
            audioFocusRequest = AudioFocusRequest.Builder(audioFocusGain)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setLegacyStreamType(streamType)
                        .build())
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            return audioManager.requestAudioFocus(audioFocusRequest)
        }
    }

    private fun requestAudioFocusGranted(): Boolean {
        return requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocusRequest() {
        Logger.enter(TAG, "abandonAudioFocusRequest()")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (TAG.startsWith("PS")) {
            Logger.v(TAG, "abandonAudioFocusRequest()")
        }
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
        Logger.exit(TAG, "abandonAudioFocusRequest()")
    }

    override fun onAudioFocusChange(focusChange: Int) {
        Logger.enter(TAG, "onAudioFocusChange() focusChange = $focusChange")
        val currentTime = SystemClock.elapsedRealtime()
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.volume = 1.0f
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                audioFocusLostTime = currentTime
                pausePlayer()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                audioFocusLostTime = currentTime
                pausePlayer()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying()) {
                    audioFocusLostTime = 0L
                    lowerVolume()
                }
            }
        }
//        mPlayerControlListener?.onPlayerAudioFocusChanged(focusChange)
        Logger.exit(TAG, "onAudioFocusChange()")
    }

    private fun registerAudioNoisyReceiver() {
        Logger.enter(TAG, "registerAudioNoisyReceiver() audioNoisyReceiverRegistered = $audioNoisyReceiverRegistered")
        if (!audioNoisyReceiverRegistered) {
            audioNoisyReceiverRegistered = true
            context.registerReceiver(audioNoisyReceiver, audioNoisyIntentFilter)
        }
        Logger.exit(TAG, "registerAudioNoisyReceiver()")
    }

    private fun unregisterAudioNoisyReceiver() {
        val exceptionMessage = "$TAG unregisterAudioNoisyReceiver() audioNoisyReceiverRegistered = $audioNoisyReceiverRegistered"
        Logger.enter(TAG, "unregisterAudioNoisyReceiver() audioNoisyReceiverRegistered = $audioNoisyReceiverRegistered")
        try {
            if (audioNoisyReceiverRegistered) {
                audioNoisyReceiverRegistered = false
                context.unregisterReceiver(audioNoisyReceiver)
            }
        }
        catch (ex: IllegalArgumentException) {
//            Crashlytics.logException(ex)
            try {
                // let see if the exception is caused by the Activity or Service
                throw IllegalStateException(exceptionMessage, ex)
            }
            catch (e: IllegalStateException) {
                Crashlytics.logException(e)
            }
        }
        Logger.exit(TAG, "unregisterAudioNoisyReceiver()")
    }

    private inner class AudioNoisyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent?.action)) {
                Logger.v(TAG, "AudioNoisyReceiver.onReceive() ACTION_AUDIO_BECOMING_NOISY")
                pausePlayer()
            }
        }
    }

    private inner class PlayerEventListener: Player.DefaultEventListener() {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            super.onPlayerStateChanged(playWhenReady, playbackState)
//            when (playbackState) {
//                Player.STATE_ENDED -> stopPlayer()
//            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
            super.onPlaybackParametersChanged(playbackParameters)
        }

        override fun onSeekProcessed() {
            super.onSeekProcessed()
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
            super.onTracksChanged(trackGroups, trackSelections)
        }

        override fun onPlayerError(error: ExoPlaybackException?) {
            super.onPlayerError(error)
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            super.onLoadingChanged(isLoading)
        }

        override fun onPositionDiscontinuity(reason: Int) {
            super.onPositionDiscontinuity(reason)
        }

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
            super.onTimelineChanged(timeline, manifest, reason)
        }
    }

    private inner class PlayerVideoListener: VideoListener {
        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
        }

        override fun onRenderedFirstFrame() {
        }
    }

    private fun localBroadcast(callbackName: String, broadcastIntent: Intent): Boolean {
        broadcastIntent.action = PlayerService.ACTION_PLAYER_CALLBACK
        broadcastIntent.putExtra(PlayerService.PARAM_CALLBACK, callbackName)
        val broadcasted = LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
        return broadcasted
    }
}
