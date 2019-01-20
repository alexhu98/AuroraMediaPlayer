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
    private val PLAYER_ERROR_POSITION_ADJUSTMENT = 5 * 60 * 1000
    private val UPDATE_POSITION_INTERVAL = 1000L

    private val repository by lazy { App.getAppRepository() }
    private val playerEventListener = PlayerEventListener()
    private val playerVideoListener = PlayerVideoListener()
    private lateinit var player: SimpleExoPlayer
    private var playerCreated = false
    private var playerActive = false
    private val playlist = ConcatenatingMediaSource()
    private var fileList = listOf<String>()
    private var currentWindowIndex = 0
    private var playerControlListener: PlayerControlListener? = null

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
    private var lastVolume = 1

    private var handler = Handler()
    private val updatePositionAction = Runnable { updatePosition() }
    private val processPendingSeekAction = Runnable { processPendingSeek() }

    var pauseWhenZeroVolume = false
    var interactive = false
    var videoWidth = 0
    var videoHeight = 0

    fun setPlayerControlListener(listener: PlayerControlListener) {
        playerControlListener = listener
    }

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

    fun releasePlayer() {
        if (playerCreated) {
            player.playWhenReady = false
            player.removeListener(playerEventListener)
            player.removeVideoListener(playerVideoListener)
            player.release()
            playerCreated = false
        }
        unregisterAudioNoisyReceiver()
    }

    fun startPlayer(fileItem: FileItem): Boolean {
        createPlayer()
        val canStart = requestAudioFocusGranted()
        if (canStart) {
            playerActive = true
            setCurrentContent(fileItem)
            val mediaSource = buildMediaSource(repository.getCurrentContentUrl())
            player.prepare(mediaSource, true, false)
            var startPosition = calculateStartPosition(fileItem)
            if (fileItem.finished || fileItem.error) {
                updateFileItem(fileItem, position = startPosition, error = 0, finished = 0)
            }
            seekTo(startPosition)
            resumePlayer()
            registerAudioNoisyReceiver()
        }
        return canStart
    }

    private fun buildMediaSource(url: String): MediaSource {
        Logger.enter(TAG, "buildMediaSource() starting url = $url")
        currentWindowIndex = 0
        playlist.clear()
        fileList = repository.currentFileItems?.value?.let { it.map { it.url } } ?: listOf()
        val index = fileList.indexOf(url)
        if (index < 0) {
            fileList = listOf(url)
        }
        else if (index > 0) {
            fileList = fileList.subList(index, fileList.size).plus(fileList.subList(0, index))
        }
        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, context.resources.getString(R.string.app_name)));
        val mediaFactory = ExtractorMediaSource.Factory(dataSourceFactory)
        playlist.addMediaSources( fileList.map {mediaFactory.createMediaSource(Uri.parse(it)) })
        Logger.exit(TAG, "buildMediaSource() playlist.size = ${playlist.size}")
        return playlist
    }

    fun setCurrentContent(fileItem: FileItem) {
        repository.setCurrentFileName(fileItem.name)
        repository.setCurrentContentUrl(fileItem.url)
        videoWidth = 0
        videoHeight = 0
        localBroadcast("onStartPlayer", Intent())
    }

    private fun updateFileItem(fileItem: FileItem? = null, position: Long = C.TIME_UNSET, finished: Int = -1, error: Int = -1) {
        if (playerActive) {
            (fileItem ?: repository.getCurrentFileItem()).copy().apply {
                this.position = if (position == C.TIME_UNSET) getCurrentPosition() else position
                if (finished != -1) {
                    this.finished = finished == 1
                }
                if (error != -1) {
                    this.error = error == 1
                }
                repository.update(this)
            }
        }
    }

    fun seekTo(position: Long) {
        seekReady = false
        player.seekTo(position)
        localBroadcastPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    fun seekTo(windowIndex: Int, position: Long) {
        seekReady = false
        currentWindowIndex = windowIndex
        player.seekTo(windowIndex, position)
        localBroadcastPlaybackState(PlaybackStateCompat.STATE_PLAYING)
   }

    fun seekForward(offset: Int = SEEK_FORWARD_OFFSET) {
        if (playerActive) {
            resumePlayer()
            if (!seekRepeating) {
                interactive = true
                cancelSeekRepeat()
            }
            seekBackwardCount = 0
            if (!seekReady) {
                seekForwardCount = Math.min(MAX_SEEK_FORWARD_COUNT, seekForwardCount + 1)
            }
            else {
                val duration = getDuration()
                if (duration > 0) {
                    val currentPosition = getCurrentPosition()
                    var newPosition = currentPosition
                    if (nearEnding(newPosition, duration)) {
                        // mark this file as finished
                        updateFileItem(position = 0L, finished = 1)
                        // if already near the end, then skip to the next track
//                        if (seekRepeat && TimeProfile.nightTimeInteractiveRequired() && !interactive) {
//                            pausePlayer()
//                            playerControlListener?.onPlayerStopped()
//                            Logger.v(TAG, "seekForward() finished because not interactive")
//                        }
//                        else {
                            playNext()
                            Logger.v(TAG, "seekForward() playNext")
//                        }
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
                    }
                }
            }
        }
    }

    fun seekForwardRepeat() {
        Logger.enter(TAG, "seekForwardRepeat()")
        if (playerActive) {
            cancelPendingSeek()
            seekForwardCount = 1
            seekRepeat = true
            seekRepeating = true
            seekForward()
            seekRepeating = false
        }
        Logger.exit(TAG, "seekForwardRepeat()")
    }

    fun seekBackward() {
        if (playerActive) {
            resumePlayer()
            if (!seekRepeating) {
                interactive = true
                cancelSeekRepeat()
            }
            seekForwardCount = 0
            if (!seekReady) {
                seekBackwardCount = Math.min(MAX_SEEK_BACKWARD_COUNT, seekBackwardCount + 1)
            }
            else {
                val currentPosition = getCurrentPosition()
                var newPosition = currentPosition - SEEK_BACKWARD_OFFSET
                if (newPosition < 0) {
                    newPosition = 0
                }
                seekTo(newPosition)
            }
        }
    }

    fun seekBackwardRepeat() {
        Logger.enter(TAG, "seekBackwardRepeat()")
        if (playerActive) {
            seekBackwardCount = 1
            seekRepeat = true
            seekRepeating = true
            seekBackward()
            seekRepeating = false
        }
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

    private fun updatePosition() {
        handler.removeCallbacks(updatePositionAction)
        if (playerCreated) {
            val currentPosition = player.currentPosition
            if (currentPosition != C.TIME_UNSET) {
//                synchronizeAudio()
                val fileItem = repository.getCurrentFileItem()
                if (fileItem.duration > 0 && currentPosition + IS_ENDING > fileItem.duration) {
//                    if (TimeProfile.nightTimeInteractiveRequired() && !interactive) {
////                        Logger.v(TAG, "updatePosition() finished because not interactive")
//                        pausePlayer()
//                        playerControlListener?.onPlayerStopped()
//                    }
//                    else {
//                        Logger.v(TAG, "updatePosition() playNext")
                        if (!fileItem.finished) {
                            updateFileItem(fileItem, position = 0, finished = 1)
                            playNext()
                        }
                    }
//                }
            }
            if (pauseWhenZeroVolume) {
                val volume = getAudioManager().getStreamVolume(AudioManager.STREAM_MUSIC)
                if (volume == 0 && lastVolume != volume && isPlaying()) {
                    pausePlayer()
                }
                lastVolume = volume
            }

            // Cancel any pending updates and schedule a new one if necessary.
            handler.postDelayed(updatePositionAction, UPDATE_POSITION_INTERVAL)
        }
    }

    private fun processPendingSeek() {
        Logger.enter(TAG, "processPendingSeek() seekReady = $seekReady, seekForwardCount = $seekForwardCount, seekBackwardCount = $seekBackwardCount")
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
        Logger.exit(TAG, "processPendingSeek() seekReady = $seekReady, seekForwardCount = $seekForwardCount, seekBackwardCount = $seekBackwardCount")
    }

    fun cancelPendingSeek() {
        handler.removeCallbacks(processPendingSeekAction)
        seekForwardCount = 0
        seekBackwardCount = 0
        seekRepeat = false
    }

    fun playPrevious(markedAsFinished: Boolean = false) {
        Logger.enter(TAG, "playPrevious() mCurrentWindowIndex = $currentWindowIndex, mFileList.size = ${fileList.size}")
        var exitMessage = "playPrevious()"
        var done = false
        var count = 0
        var newWindowIndex = currentWindowIndex
        updateFileItem(finished = if (markedAsFinished) 1 else -1)
        while (!done) {
            newWindowIndex = if (newWindowIndex == 0) (fileList.size - 1) else (newWindowIndex - 1)
            if (++count == fileList.size) {
                pausePlayer()
                playerControlListener?.onPlayerStopped()
                done = true
                exitMessage = "playPrevious() finished because playlist ended"
            }
            else {
                val url = fileList[newWindowIndex]
                val fileItem = repository.getFileItem(url)
                if (fileItem.deleted) {
                    // skip deleted file
                }
                else {
                    val startPosition = calculateStartPosition(fileItem)
                    setCurrentContent(fileItem)
                    seekTo(newWindowIndex, startPosition)
                    done = true
                    exitMessage = "playPrevious() newWindowIndex = $newWindowIndex, startPosition = $startPosition, url = $url"
                }
            }
        }
        Logger.exit(TAG, exitMessage)
    }

    fun playNext(recreatePlayer: Boolean = false, markedAsFinished: Boolean = false) {
        Logger.enter(TAG, "playNext() recreatePlayer = $recreatePlayer, currentWindowIndex = $currentWindowIndex, fileList.size = ${fileList.size}")
        var exitMessage = "playNext()"
        var done = false
        var count = 0
        var newWindowIndex = currentWindowIndex
        updateFileItem(finished = if (markedAsFinished) 1 else -1)
        while (!done) {
            newWindowIndex = if (newWindowIndex >= fileList.size - 1) 0 else (newWindowIndex + 1)
            if (++count == fileList.size || fileList.isEmpty()) {
                pausePlayer()
                playerControlListener?.onPlayerStopped()
                done = true
                exitMessage = "playNext() finished because playlist ended"
            }
            else {
                val url = fileList[newWindowIndex]
                val fileItem = repository.getFileItem(url)
                if (fileItem.deleted || fileItem.finished) {
                    // skip deleted file or finished file
                }
                else {
                    if (recreatePlayer) {
                        Logger.v(TAG, "playNext() releasePlayer")
                        releasePlayer()
                        Logger.v(TAG, "playNext() createPlayer")
                        createPlayer()
                        Logger.v(TAG, "playNext() setUrl")
                        startPlayer(fileItem)
                        Logger.v(TAG, "playNext() preparePlayer")
                        Logger.v(TAG, "playNext() resume")
                        resumePlayer()
                        done = true
                        exitMessage = "playNext() recreatePlayer = $recreatePlayer, url = $url"
                    }
                    else {
                        val startPosition = calculateStartPosition(fileItem)
                        setCurrentContent(fileItem)
                        seekTo(newWindowIndex, startPosition)
                        done = true
                        exitMessage = "playNext() newWindowIndex = $newWindowIndex, startPosition = $startPosition, url = $url"
                    }
                }
            }
        }
        Logger.exit(TAG, exitMessage)
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
        if (playerActive) {
            player.playWhenReady = true
            localBroadcastPlaybackState()
        }
    }

    fun pausePlayer() {
        if (playerActive) {
            updateFileItem()
            player.playWhenReady = false
            abandonAudioFocusRequest()
            localBroadcastPlaybackState()
        }
    }

    fun stopPlayer() {
        if (playerActive) {
            updateFileItem()
            cancelSeekRepeat()
            cancelPendingSeek()
            player.playWhenReady = false
            player.stop(false)
            abandonAudioFocusRequest()
            localBroadcastPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        }
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
        val fileItem = repository.getCurrentFileItem()
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
        position += (direction * seconds * 1000f).toLong()
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

    private fun getAudioManager(): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private fun requestAudioFocusGranted(): Boolean {
        return requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocusRequest() {
        Logger.enter(TAG, "abandonAudioFocusRequest()")
        if (TAG.startsWith("PS")) {
            Logger.v(TAG, "abandonAudioFocusRequest()")
        }
        audioFocusRequest?.let {
            getAudioManager().abandonAudioFocusRequest(it)
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
            Logger.v(TAG, "onTracksChanged() currentWindowIndex = ${player.currentWindowIndex}, position = ${getCurrentPosition()}, duration = ${getDuration()}" )
            super.onTracksChanged(trackGroups, trackSelections)
        }

        override fun onPlayerError(error: ExoPlaybackException?) {
            super.onPlayerError(error)

            // advance the position to avoid getting stuck in the same place
            // when trying to play it again
            var newPosition = getCurrentPosition() + PLAYER_ERROR_POSITION_ADJUSTMENT
            when (nearEnding(newPosition, getDuration())) {
                true -> updateFileItem(error = 1, finished = 1)
                false -> updateFileItem(error = 1, position = newPosition)
            }

            playNext(recreatePlayer = true)
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            super.onLoadingChanged(isLoading)
        }

        override fun onPositionDiscontinuity(reason: Int) {
            Logger.v(TAG, "onPositionDiscontinuity() currentWindowIndex = ${player.currentWindowIndex}, reason = $reason, position = ${getCurrentPosition()}, duration = ${getDuration()}" )
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

    fun pushToBackground() {
        updateFileItem()
        val broadcastIntent = Intent()
        broadcastIntent.putExtra(PlayerService.PARAM_MEDIA_ID, repository.getCurrentContentUrl())
        localBroadcast("onPushToBackground", broadcastIntent)
    }

    fun stopBackgroundPlayer() {
        val broadcastIntent = Intent()
        localBroadcast("onStopBackgroundPlayer", broadcastIntent)
    }

    fun stopBackgroundService() {
        val broadcastIntent = Intent()
        localBroadcast("onStopBackgroundService", broadcastIntent)
    }

    interface PlayerControlListener {
//        fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int)
        fun onPlayerStopped()
//        fun onPlayerError(e: ExoPlaybackException?)
//        fun onPlayerPositionDiscontinuity()
//        fun onPlayerAudioFocusChanged(focusChange: Int)
//        fun onPlayerStatus(status: String)
    }
}
