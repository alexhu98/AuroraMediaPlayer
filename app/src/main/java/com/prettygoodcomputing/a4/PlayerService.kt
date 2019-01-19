package com.prettygoodcomputing.a4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import java.net.URLDecoder

class PlayerService : MediaBrowserServiceCompat() {

    private val TAG = "PlayerService"

    private val PLAYER_SERVICE_NOTIFICATION_ID = 101
    private val REQUEST_LAUNCH_APP = 201
    private val CHANNEL_PLAYER_SERVICE_NAME = "A4"
    private val CHANNEL_PLAYER_SERVICE = "com.prettygoodcomputing.a4.player_service"
    private val MEDIA_ID_ROOT = "com.prettygoodcomputing.a4.PlayerService.Root"
    private val MEDIA_ID_EMPTY_ROOT = "com.prettygoodcomputing.a4.PlayerService.EmptyRoot"

    private val context by lazy { this }
    private val repository by lazy { App.getAppRepository() }
    private lateinit var mediaSession: MediaSessionCompat
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val playerController by lazy { PlayerController(context, "PS.PlayerController") }
    private var localBroadcastReceiver: BroadcastReceiver? = null
    private var mediaPlaybackState = PlaybackStateCompat.STATE_NONE
    private var foregroundServiceStarted = false
    private var currentContentUrl = ""
    private var currentContentTitle = ""

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        createMediaSession()
        createPlayer()
        registerLocalBroadcastReceiver()
    }

    override fun onDestroy() {
        releasePlayer()
        playerController.release()
        unregisterLocalBroadcastReceiver()
    }

    private fun allowBrowsing(clientPackageName: String, clientUid: Int): Boolean {
        return true
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
//        Toast.makeText(applicationContext, "MBS: onGetRoot $clientPackageName", Toast.LENGTH_LONG).show()

        // (Optional) Control the level of access for the specified package name.
        // You'll need to write your own logic to do this.
        return if (allowBrowsing(clientPackageName, clientUid)) {
            // Returns a root ID that clients can use with onLoadChildren() to retrieve
            // the content hierarchy.
            MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_ROOT, null)
        } else {
            // Clients can connect, but this BrowserRoot is an empty hierachy
            // so onLoadChildren returns nothing. This disables the ability to browse for content.
            MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_EMPTY_ROOT, null)
        }
    }

    override fun onLoadChildren(parentMediaId: String, result: Result<MutableList<MediaItem>>) {
        val mediaItems = arrayListOf<MediaBrowserCompat.MediaItem>()
        if (TextUtils.equals(MEDIA_ID_EMPTY_ROOT, parentMediaId)) {
        }
        else {
//            Toast.makeText(context, "PS: onLoadChildren", Toast.LENGTH_LONG).show()
            val fileItems = repository.getCurrentFileItems().value ?: listOf()
            fileItems.forEachIndexed { index, fileItem ->
                mediaItems.add(createMediaItem(fileItem, index + 1, fileItems.size))
            }
        }
        result.sendResult(mediaItems)
    }

    private fun createMetadata(fileItem: FileItem, trackNumber: Int, numberOfTracks: Int): MediaMetadataCompat {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, fileItem.name)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, fileItem.url)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, fileItem.url)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, fileItem.duration)
            .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber.toLong())
            .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, numberOfTracks.toLong())
            .build()
        return metadata
    }

    private fun createMediaItem(fileItem: FileItem, trackNumber: Int, numberOfTracks: Int): MediaBrowserCompat.MediaItem {
        val metadata = createMetadata(fileItem, trackNumber, numberOfTracks)
        return MediaBrowserCompat.MediaItem(metadata.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun localBroadcast(callbackName: String, broadcastIntent: Intent): Boolean {
        broadcastIntent.action = PlayerService.ACTION_MEDIA_SESSION_CALLBACK
        broadcastIntent.putExtra(PlayerService.PARAM_CALLBACK, callbackName)
        return LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val broadcastIntent = Intent()
            broadcastIntent.putExtra(PlayerService.PARAM_MEDIA_ID, mediaId)
            if (!localBroadcast("onPlayFromMediaId", broadcastIntent)) {
                playerController.stopPlayer()
                val intent = Intent(context, MainActivity::class.java)
                intent.action = Intent.ACTION_VIEW
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                intent.putExtra(PARAM_MEDIA_ID, mediaId)
                startActivity(intent);
            }
        }

        override fun onPlay() {
            if (!localBroadcast("onPlay", Intent())) {
                playerController.resumePlayer()
            }
        }

        override fun onPause() {
            if (!localBroadcast("onPause", Intent())) {
                playerController.pausePlayer()
            }
        }

        override fun onStop() {
            if (!localBroadcast("onStop", Intent())) {
                stopForegroundService()
            }
        }

        override fun onSkipToNext() {
            if (!localBroadcast("onSkipToNext", Intent())) {
                playerController.seekForward()
            }
        }

        override fun onSkipToPrevious() {
            if (!localBroadcast("onSkipToPrevious", Intent())) {
                playerController.seekBackward()
            }
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {}
    }

    private fun registerLocalBroadcastReceiver() {
        localBroadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    when (intent.action) {
                        PlayerService.ACTION_PLAYER_CALLBACK -> {
                            val callbackName = intent.extras?.getString(PlayerService.PARAM_CALLBACK) ?: ""
                            val mediaId = intent.extras?.getString(PlayerService.PARAM_MEDIA_ID) ?: ""
                            val playbackState = intent.extras?.getInt(PlayerService.PARAM_PLAYBACK_STATE, PlaybackStateCompat.STATE_NONE) ?: PlaybackStateCompat.STATE_NONE
                            val position = intent.extras?.getLong(PlayerService.PARAM_POSITION, 0L) ?: 0L

//                            Toast.makeText(context, "PS  $playbackState", Toast.LENGTH_LONG).show()
                            when (callbackName) {
                                "onStartPlayer" -> setPlayerInfo(mediaId)
                                "onPlaybackState" -> setMediaPlaybackState(playbackState, position)
                                "onPushToBackground" -> pushToBackground(mediaId)
                                "onStopBackgroundPlayer" -> playerController.stopPlayer()
                                "onStopBackgroundService" -> stopForegroundService()
                            }
                        }
                    }
                }
            }
        }
        localBroadcastReceiver?.let {
            LocalBroadcastManager.getInstance(this).registerReceiver(it, IntentFilter(ACTION_PLAYER_CALLBACK))
        }
    }

    private fun unregisterLocalBroadcastReceiver() {
        localBroadcastReceiver?.let {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
        }
        localBroadcastReceiver = null
    }

    private fun createMediaSession() {
        mediaSession = MediaSessionCompat(context, context.resources.getString(R.string.app_name))
        sessionToken = mediaSession.sessionToken
        mediaSession.setCallback(MediaSessionCallback())
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession.isActive = true
    }

    private fun setMediaPlaybackState(state: Int, position: Long = 0) {
        Logger.enter(TAG, "setMediaPlaybackState state = $state")
        if (foregroundServiceStarted) {
            mediaPlaybackState = state
            val notification = buildNotification(mediaPlaybackState, CHANNEL_PLAYER_SERVICE, position)
            notificationManager.notify(PLAYER_SERVICE_NOTIFICATION_ID, notification)

            val fileItem = repository.getFileItem(currentContentUrl)
            val fileItems = repository.getCurrentFileItems().value ?: listOf()
            val index = fileItems.indexOf(fileItem)
            val trackNumber = if (index >= 0) index + 1 else 1
            val numberOfTracks = if (index >= 0) fileItems.size else 1
            val metadata = createMetadata(fileItem, trackNumber, numberOfTracks)
            mediaSession.setMetadata(metadata)

            val commonActions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT

            val stateBuilder = PlaybackStateCompat.Builder()
            when (mediaPlaybackState) {
                PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_STOPPED -> {}
                PlaybackStateCompat.STATE_PLAYING -> stateBuilder.setActions(PlaybackStateCompat.ACTION_PAUSE or commonActions)
                PlaybackStateCompat.STATE_PAUSED -> stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY or commonActions)
            }
            val playbackSpeed = if (mediaPlaybackState == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0f
            stateBuilder.setState(mediaPlaybackState, position, playbackSpeed, SystemClock.elapsedRealtime())
            mediaSession.setPlaybackState(stateBuilder.build())
        }
        Logger.exit(TAG, "setMediaPlaybackState state = $state")
    }

    private fun urlDecodedName(url: String): String {
        var name: String = URLDecoder.decode(url, "UTF-8")
        val tokens = name.split("/")
        if (tokens.isNotEmpty()) {
            name = tokens[tokens.size - 1]
        }
        return name
    }

    private fun setPlayerInfo(url: String) {
        currentContentUrl = url
        currentContentTitle = urlDecodedName(url)
    }

    private fun pushToBackground(url: String) {
        setPlayerInfo(url)
        startPlayer(url)
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var channel = NotificationChannel(CHANNEL_PLAYER_SERVICE, CHANNEL_PLAYER_SERVICE_NAME, NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel);
    }

    private fun buildNotification(state: Int, channelID: String, position: Long = 0L): Notification {
        Logger.enter(TAG, "buildNotification state = $state")
        val builder = NotificationCompat.Builder(this, channelID)
        val intent = Intent(this, MainActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra(PARAM_MEDIA_ID, currentContentUrl)
        val pendingIntent = PendingIntent.getActivity(this, REQUEST_LAUNCH_APP, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        if (state == PlaybackStateCompat.STATE_NONE || state == PlaybackStateCompat.STATE_STOPPED) {
            val exitAction = NotificationCompat.Action(
                R.drawable.transparent, getString(R.string.media_exit),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))

            builder.setContentTitle("")
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(resources.getColor( R.color.color_primary_dark, theme))
                // Add a exit button
                .addAction(exitAction)
                .setShowWhen(false)
                .setUsesChronometer(false)
                // Take advantage of MediaStyle features
                .setStyle(MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
                )
        }
        else {
            val playOrPauseAction = if (state == PlaybackStateCompat.STATE_PLAYING) {
                NotificationCompat.Action(
                    R.drawable.ic_media_pause, getString(R.string.media_pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE))
            }
            else {
                NotificationCompat.Action(
                    R.drawable.ic_media_play, getString(R.string.media_play),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY))
            }
            val stopAction = NotificationCompat.Action(
                R.drawable.ic_media_stop, getString(R.string.media_stop),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))

            // Add the metadata for the currently playing track
            builder.setContentTitle(currentContentTitle)
                // Enable launching the player by clicking the notification
                .setContentIntent(pendingIntent)

                // Stop the service when the notification is swiped away
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))

                // Make the transport controls visible on the lockscreen
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                // Add an app icon and set its accent color
                // Be careful about the color
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(resources.getColor( R.color.color_primary_dark, theme))

                // Add a play or pause button
                .addAction(playOrPauseAction)

                // Add a stop button
                .addAction(stopAction)

                // Take advantage of MediaStyle features
                .setStyle(MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
                )
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                builder.setWhen(System.currentTimeMillis() - position)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
            }
        }
        Logger.exit(TAG, "buildNotification state = $state")
        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foregroundServiceStarted = true
        mediaPlaybackState = PlaybackStateCompat.STATE_NONE
        val notification = buildNotification(mediaPlaybackState, CHANNEL_PLAYER_SERVICE)
        startForeground(PLAYER_SERVICE_NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createPlayer() {
        setMediaPlaybackState(PlaybackStateCompat.STATE_STOPPED)
/*
                    playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(context, CHANNEL_PLAYER_SERVICE, R.string.app_name, PLAYER_SERVICE_NOTIFICATION_ID,
                object: PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun createCurrentContentIntent(player: Player?): PendingIntent? {
                        val intent = Intent(context, MainActivity::class.java)
                        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                    }

                    override fun getCurrentContentTitle(player: Player?): String {
                        val windowIndex = player?.currentWindowIndex ?: -1
                        val title = when (windowIndex) {
                            -1 -> "Player is null"
                            0, 2, 4, 6 -> "mpthreetest.mp3 " + windowIndex.toString()
                            else -> "crowd-cheering.mp3 " + windowIndex.toString()
                        }
                        return title
                    }

                    override fun getCurrentContentText(player: Player?): String? {
                        return null
                    }

                    override fun getCurrentLargeIcon(player: Player?, callback: PlayerNotificationManager.BitmapCallback?): Bitmap? {
                        return null
                    }
                })

            playerNotificationManager.setNotificationListener(object: PlayerNotificationManager.NotificationListener {
                override fun onNotificationStarted(notificationId: Int, notification: Notification?) {
                    startForeground(notificationId, notification)
                }

                override fun onNotificationCancelled(notificationId: Int) {
                    stopSelf()
                }
            })
            playerNotificationManager.setColor(R.color.colorPrimaryDark)
            playerNotificationManager.setSmallIcon(R.mipmap.ic_launcher)
*/
//            playerNotificationManager.setPlayer(player)

//            mediaSessionConnector = MediaSessionConnector(mediaSession)

//            mediaSessionConnector.setQueueNavigator(object: TimelineQueueNavigator(mediaSession) {
//
//                override fun getMediaDescription(player: Player?, windowIndex: Int): MediaDescriptionCompat {
//                    val title = when (windowIndex) {
//                        0, 2, 4, 6 -> "mpthreetest.mp3 " + windowIndex.toString()
//                        else -> "crowd-cheering.mp3 " + windowIndex.toString()
//                    }
//                    val mediaDescription = MediaDescriptionCompat.Builder()
//                        .setMediaId(title)
//                        .setTitle(title)
//                        .setDescription(title)
//                        .build()
//                    return mediaDescription
//
//                }
//            })
//            mediaSessionConnector.setPlayer(player, null)
//        }
    }

    private fun startPlayer(url: String) {
        val fileItem = repository.getFileItem((url))
        playerController.stopPlayer()
        playerController.release()
        playerController.startPlayer(fileItem)
    }

    private fun releasePlayer() {
        playerController.release()
    }

    private fun stopForegroundService() {
        Logger.enter(TAG, "stopForegroundService")
        foregroundServiceStarted = false
        playerController.stopPlayer()
        stopForeground(true)
        stopSelf()
        Logger.exit(TAG, "stopForegroundService")
    }

    companion object {
        const val ACTION_MEDIA_SESSION_CALLBACK = "com.prettygoodcomputing.a4.PlayerService.ACTION_MEDIA_SESSION_CALLBACK"
        const val ACTION_PLAYER_CALLBACK = "com.prettygoodcomputing.a4.PlayerService.ACTION_PLAYER_CALLBACK"
        const val PARAM_CALLBACK = "com.prettygoodcomputing.a4.PlayerService.PARAM_CALLBACK"
        const val PARAM_MEDIA_ID = "com.prettygoodcomputing.a4.PlayerService.PARAM_MEDIA_ID"
        const val PARAM_POSITION = "com.prettygoodcomputing.a4.PlayerService.PARAM_POSITION"
        const val PARAM_QUERY = "com.prettygoodcomputing.a4.PlayerService.PARAM_QUERY"
        const val PARAM_QUEUE_ID = "com.prettygoodcomputing.a4.PlayerService.PARAM_QUEUE_ID"
        const val PARAM_PLAYBACK_STATE = "com.prettygoodcomputing.a4.PlayerService.PARAM_PLAYBACK_STATE"
    }
}
