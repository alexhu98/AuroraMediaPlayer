package com.prettygoodcomputing.a4

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
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
    private val SEEK_FORWARD_OFFSET = 30 * 1000
    private val SEEK_BACKWARD_OFFSET = 25 * 1000
    private val SEEK_REPEAT_DELAY_SHORT = 1L
    private val SEEK_REPEAT_DELAY_LONG = 1000L
    private val NEAR_BEGINNING = 10 * 1000L
    private val NEAR_ENDING = 10 * 1000L
    private val IS_ENDING = 3 * 1000L
    private val MAX_SEEK_FORWARD_COUNT = 5
    private val MAX_SEEK_BACKWARD_COUNT = 2

    private val repository by lazy { App.getAppRepository() }
    private lateinit var currentFileItem: FileItem
    private val playerEventListener = PlayerEventListener()
    private val playerVideoListener = PlayerVideoListener()
    private lateinit var player: SimpleExoPlayer
    private var playerCreated = false

    private var handler = Handler()
    private var seekForwardCount = 0
    private var seekBackwardCount = 0
    private var seekReady = true
    private var seekRepeat = false
    private var seekRepeating = false

    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusLostTime = 0L
    private val audioNoisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private val audioNoisyReceiver = AudioNoisyReceiver()
    private var audioNoisyReceiverRegistered = false

    private val processPendingSeekAction = Runnable { processPendingSeek() }

    var interactive = false
    var videoWidth = 0
    var videoHeight = 0

    fun getPlayer(): SimpleExoPlayer {
        return createPlayer()
    }

    fun createPlayer(): SimpleExoPlayer {
        if (!playerCreated) {
            videoWidth = 0
            videoHeight = 0
            player = ExoPlayerFactory.newSimpleInstance(context, DefaultTrackSelector()).apply {
                addListener(playerEventListener)
                addVideoListener(playerVideoListener)
                repeatMode = Player.REPEAT_MODE_OFF
                setSeekParameters(SeekParameters.NEXT_SYNC)
            }
            playerCreated = true
        }
        return player
    }

    fun release() {
        unregisterAudioNoisyReceiver()
        if (playerCreated) {
            player.playWhenReady = false
            player.removeListener(playerEventListener)
            player.removeVideoListener(playerVideoListener)
            player.release()
        }
        playerCreated = false
    }

    fun startPlayer(fileItem: FileItem): Boolean {
        createPlayer()
        val canStart = requestAudioFocusGranted()
        if (canStart) {
            currentFileItem = fileItem

            val broadcastIntent = Intent()
            broadcastIntent.putExtra(PlayerService.PARAM_MEDIA_ID, currentFileItem.url)
            localBroadcast("onStartPlayer", broadcastIntent)

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
        return canStart
    }

    fun playNext() {

    }

    fun playPrevious() {
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
        localBroadcastPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    fun seekForward(offset: Int = SEEK_FORWARD_OFFSET) {
        Logger.enter(TAG, "seekForward()")
        resumePlayer()
        if (!seekRepeating) {
            interactive = true
            cancelSeekRepeat()
        }
        seekBackwardCount = 0
        if (!seekReady) {
            seekForwardCount = Math.min(MAX_SEEK_FORWARD_COUNT, seekForwardCount + 1)
            Logger.exit(TAG, "seekForward() seekForwardCount = $seekForwardCount")
        }
        else {
            val duration = getDuration()
            if (duration > 0) {
                val currentPosition = getCurrentPosition()
                var newPosition = currentPosition
                if (nearEnding(newPosition, duration)) {
                    // mark this file as finished
//                    val fileItem = DataStore.getFileItem(mUrl)
//                    fileItem.position = currentPosition
//                    fileItem.finished = true

                    // if already near the end, then skip to the next track
//                    if (seekRepeat && TimeProfile.nightTimeInteractiveRequired() && !interactive) {
//                        pause()
//                        mPlayerControlListener?.onPlayerStopped()
//                        Logger.v(TAG, "seekForward() finished because not interactive")
//                    }
//                    else {
//                        playNext()
//                        Logger.v(TAG, "seekForward() playNext")
//                    }
                    Logger.exit(TAG, "seekForward()")
                }
                else {
                    newPosition += offset
                    if (nearEnding(newPosition, duration)) {
                        newPosition = duration - NEAR_ENDING
                        Logger.v(TAG, "seekForward() near end, back up a little")
                    }
                    if (newPosition < 0) {
                        newPosition = 0
                    }
                    seekTo(newPosition)
                    Logger.exit(TAG, "seekForward() newPosition = $newPosition")
                }
            }
        }
    }

    fun seekForwardRepeat() {
        Logger.enter(TAG, "seekForwardRepeat()")
        cancelPendingSeek()
        seekForwardCount = 1
        seekRepeat = true
        seekRepeating = true
        seekForward()
        seekRepeating = false
        Logger.exit(TAG, "seekForwardRepeat()")
    }

    fun seekBackward() {
        Logger.enter(TAG, "seekBackward() seekReady = $seekReady, seekBackwardCount = $seekBackwardCount")
        resumePlayer()
        if (!seekRepeating) {
            interactive = true
            cancelSeekRepeat()
        }
        seekForwardCount = 0
        if (!seekReady) {
            seekBackwardCount = Math.min(MAX_SEEK_BACKWARD_COUNT, seekBackwardCount + 1)
            Logger.exit(TAG, "seekBackward() seekBackwardCount = $seekBackwardCount")
        }
        else {
            val currentPosition = getCurrentPosition()
            var newPosition = currentPosition - SEEK_BACKWARD_OFFSET
            if (newPosition < 0) {
                newPosition = 0
            }
            seekTo(newPosition)
            Logger.exit(TAG, "seekBackward() newPosition = $newPosition")
        }
    }

    fun seekBackwardRepeat() {
        Logger.enter(TAG, "seekBackwardRepeat()")
        seekBackwardCount = 1
        seekRepeat = true
        seekRepeating = true
        seekBackward()
        seekRepeating = false
        Logger.exit(TAG, "seekBackwardRepeat()")
    }

    fun isSeekRepeat(): Boolean {
        return seekRepeat
    }

    fun cancelSeekRepeat() {
        seekRepeat = false
    }

    private fun schedulePendingSeek() {
        Logger.enter(TAG, "schedulePendingSeek() seekReady = $seekReady, seekForwardCount = $seekForwardCount, seekBackwardCount = $seekBackwardCount")
        handler.removeCallbacks(processPendingSeekAction)
        handler.postDelayed(processPendingSeekAction, if (seekRepeat) SEEK_REPEAT_DELAY_LONG else SEEK_REPEAT_DELAY_SHORT)
        Logger.exit(TAG, "schedulePendingSeek()")
    }

    private fun processPendingSeek() {
        Logger.enter(TAG, "processPendingSeek() seekReady = $seekReady, mSeekForwardCount = $seekForwardCount, mSeekBackwardCount = $seekBackwardCount")
        seekReady = true
        seekRepeating = true
        if (seekForwardCount > 0) {
            if (!seekRepeat) {
                seekForwardCount--
            }
            seekForward()
        }
        else if (seekBackwardCount > 0) {
            if (!seekRepeat) {
                seekBackwardCount--
            }
            seekBackward()
        }
        seekRepeating = false
        Logger.exit(TAG, "processPendingSeek() seekReady = $seekReady, mSeekForwardCount = $seekForwardCount, mSeekBackwardCount = $seekBackwardCount")
    }

    fun cancelPendingSeek() {
        handler.removeCallbacks(processPendingSeekAction)
        seekForwardCount = 0
        seekBackwardCount = 0
        seekRepeat = false
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
        localBroadcastPlaybackState()
    }

    fun pausePlayer() {
        player.playWhenReady = false
        abandonAudioFocusRequest()
        localBroadcastPlaybackState()
    }

    fun stopPlayer() {
        currentFileItem.copy().apply {
            position = getCurrentPosition()
            repository.update(this)
        }
        cancelSeekRepeat()
        cancelPendingSeek()
        player.playWhenReady = false
        player.stop(false)
        abandonAudioFocusRequest()
        localBroadcastPlaybackState(PlaybackStateCompat.STATE_STOPPED)
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

    fun calculateSeekPosition(swipeDistance: Float, swipeVelocity: Float): Long {
        Logger.enter(TAG, "calculateSeekPosition() swipeDistance = $swipeDistance, swipeVelocity = $swipeVelocity")
        val fileItem = currentFileItem
        var position = fileItem.position
        val duration = fileItem.duration
        var direction = Math.signum(swipeDistance)
        val distance = Math.abs(swipeDistance)
        // 5 seconds every 120 pixels
        val secondsInPixels = 5.0f
        var pixelsDistance = 120.0f / 2f
        var velocityFactor = 1.0f
        if (swipeVelocity > 2000.0f || swipeVelocity < -2000.0f) {
            velocityFactor = swipeVelocity / 500.0f
        }
        else if (swipeVelocity > 500.0f || swipeVelocity < -500.0f) {
            velocityFactor = swipeVelocity / 1000.0f
        }
        val seconds = secondsInPixels * (distance / pixelsDistance) * Math.abs(velocityFactor)
        position += (direction * seconds * 1000f).toInt()
        if (position >= duration) {
            position = duration - NEAR_ENDING
        }
        if (position < 0) {
            position = 0
        }
        Logger.exit(TAG, "calculateSeekPosition() position = $position")
        return position
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
            when (playbackState) {
                Player.STATE_READY -> schedulePendingSeek()
            }
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
            videoWidth = width
            videoHeight = height
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

    private fun localBroadcastPlaybackState(playbackState: Int = PlaybackStateCompat.STATE_NONE): Boolean {
        val broadcastIntent = Intent()
        val state = when (playbackState){
            PlaybackStateCompat.STATE_NONE -> if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            else -> playbackState
        }
        broadcastIntent.putExtra(PlayerService.PARAM_PLAYBACK_STATE, state)
        broadcastIntent.putExtra(PlayerService.PARAM_POSITION, getCurrentPosition())
        return localBroadcast("onPlaybackState", broadcastIntent)
    }
}
